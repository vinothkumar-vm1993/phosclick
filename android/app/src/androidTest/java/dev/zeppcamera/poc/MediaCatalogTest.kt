package dev.zeppcamera.poc

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCatalogTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun legacyCapturesRemainAvailableAfterWristSnapRename(){
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"RenameTest-${System.nanoTime()}.jpg");put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/ActionCamera/");put(MediaStore.MediaColumns.IS_PENDING,1)})!!
        try{
            resolver.openOutputStream(uri)!!.use{out->val image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);image.eraseColor(Color.CYAN);image.compress(Bitmap.CompressFormat.JPEG,90,out);image.recycle()}
            resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            val catalog=CaptureCatalog(context);assertTrue(catalog.exists(uri));assertTrue(catalog.all().any{it.uri==uri});assertEquals(uri,catalog.latest(true))
        }finally{resolver.delete(uri,null,null);CaptureCatalog(context).reconcile()}
    }
    @Test fun listedCaptureUrisResolveAcrossMediaCollections(){
        val catalog=CaptureCatalog(context);val missing=catalog.all().filterNot{catalog.exists(it.uri)}
        assertTrue("Unresolvable capture URIs: "+missing.map{it.name+" "+it.uri},missing.isEmpty())
    }
}
