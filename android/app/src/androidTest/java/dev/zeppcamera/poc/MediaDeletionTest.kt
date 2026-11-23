package dev.zeppcamera.poc

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real MediaStore + activity lifecycle tests; deletes only synthetic files created here. */
@RunWith(AndroidJUnit4::class)
class MediaDeletionTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context=ins.targetContext
    private val catalog=CaptureCatalog(context)
    private val owned=mutableListOf<Uri>()
    private val activities=mutableListOf<Activity>()
    private fun insert(video:Boolean=false,color:Int=Color.RED):Uri{
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"DeletionTest-${UUID.randomUUID()}"+if(video)".mp4"else".jpg");put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4"else"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/PhosClick/"else"Pictures/PhosClick/");put(MediaStore.MediaColumns.IS_PENDING,1)}
        val uri=context.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;owned+=uri
        context.contentResolver.openOutputStream(uri)!!.use{out->if(video)ins.context.assets.open("deletion-fixture.mp4").use{it.copyTo(out)}else{val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);bitmap.compress(Bitmap.CompressFormat.JPEG,95,out);bitmap.recycle()}}
        context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
        catalog.record(uri,if(video)"Video"else"Photo");return uri
    }
    private fun launch():MainActivity= (ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity).also{activities+=it}
    private fun center(activity:MainActivity):Int?{
        var color:Int?=null
        ins.runOnMainSync{val f=MainActivity::class.java.getDeclaredField("lastImage").apply{isAccessible=true};val image=f.get(activity)as ImageView;val bitmap=(image.drawable as? BitmapDrawable)?.bitmap;color=bitmap?.getPixel(bitmap.width/2,bitmap.height/2)}
        return color
    }
    private fun matches(c:Int?,target:Int)=c!=null&&kotlin.math.abs(Color.red(c)-Color.red(target))<40&&kotlin.math.abs(Color.green(c)-Color.green(target))<40&&kotlin.math.abs(Color.blue(c)-Color.blue(target))<40
    private fun await(message:String,condition:()->Boolean){val end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){ins.waitForIdleSync();if(condition())return;Thread.sleep(100)};fail(message)}
    @Before fun requireUnlockedPhone(){assertFalse("Unlock the phone before touchscreen tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
    @After fun cleanup(){
        try{owned.forEach{uri->
            // Android may report access denied when an already deleted URI is deleted twice.
            try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null)}catch(_:SecurityException){if(catalog.exists(uri))throw AssertionError("Cannot clean up test fixture")}
            if(catalog.exists(uri))context.contentResolver.delete(uri,null,null)
        }}finally{ins.runOnMainSync{activities.reversed().forEach{it.finish()}};ins.waitForIdleSync();catalog.reconcile()}
    }
    @Test fun deletingPhotoInvalidatesVisibleThumbnailAndPersistedReference(){
        val previous=insert(color=Color.CYAN);val uri=insert();val a=launch();await("Photo thumbnail never loaded"){matches(center(a),Color.RED)}
        context.contentResolver.delete(uri,null,null)
        await("Deleted photo thumbnail remained visible"){matches(center(a),Color.CYAN)&&catalog.lastUri()==previous}
        assertFalse(catalog.exists(uri));assertFalse(catalog.all().any{it.uri==uri})
    }
    @Test fun deletingVideoInvalidatesVisibleThumbnailAndFallsBack(){
        val uri=insert(video=true);val a=launch();await("Video thumbnail never loaded"){matches(center(a),Color.GREEN)}
        context.contentResolver.delete(uri,null,null)
        await("Deleted video thumbnail remained visible"){!matches(center(a),Color.GREEN)&&catalog.lastUri()!=uri}
        assertFalse(catalog.exists(uri));assertFalse(catalog.all().any{it.uri==uri})
    }
    @Test fun galleryTrashAndRestoreAreReflectedWithoutRestart(){
        val uri=insert(color=Color.MAGENTA);val a=launch();await("Photo missing"){matches(center(a),Color.MAGENTA)}
        context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,1)},null,null)
        await("Trashed photo remained visible"){!matches(center(a),Color.MAGENTA)&&!catalog.exists(uri)}
        assertFalse(catalog.all().any{it.uri==uri})
        context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null)
        await("Restored photo did not return"){matches(center(a),Color.MAGENTA)}
    }
    @Test fun returningFromGalleryRevalidatesDeletionWhileCameraWasStopped(){
        val uri=insert(color=Color.BLUE);val a=launch();await("Photo missing"){matches(center(a),Color.BLUE)}
        val gallery=ins.startActivitySync(Intent(context,CapturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities+=gallery
        context.contentResolver.delete(uri,null,null);ins.runOnMainSync{gallery.finish()}
        await("Stale thumbnail returned on resume"){!matches(center(a),Color.BLUE)&&catalog.lastUri()!=uri}
    }
}
