package dev.zeppcamera.poc

import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Deletes only synthetic fixtures created here, with the real Android trash prompt. */
@RunWith(AndroidJUnit4::class)
class GallerySelectionTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val fixtures=mutableListOf<Uri>();private var gallery:CapturesActivity?=null
 private fun insert(video:Boolean):Uri{val uri=context.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"SelectionTest-${System.nanoTime()}"+if(video)".mp4"else".jpg");put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4"else"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/PhosClick/"else"Pictures/PhosClick/");put(MediaStore.MediaColumns.IS_PENDING,1)})!!;fixtures+=uri;context.contentResolver.openOutputStream(uri)!!.use{out->if(video)ins.context.assets.open("deletion-fixture.mp4").use{it.copyTo(out)}else{val b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);b.eraseColor(Color.CYAN);b.compress(Bitmap.CompressFormat.JPEG,95,out);b.recycle()}};context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);return uri}
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message+"; labels="+nodes().filterIsInstance<TextView>().map{it.text.toString()}+"; selected="+CapturesActivity::class.java.getDeclaredField("selected").apply{isAccessible=true}.get(gallery)+"; trace="+CapturesActivity::class.java.getDeclaredField("selectionTrace").apply{isAccessible=true}.get(gallery))}
 private fun nodes():List<View>{val out=mutableListOf<View>();ins.runOnMainSync{fun walk(v:View){out+=v;if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))};walk(gallery!!.window.decorView)};return out}
 private fun tap(v:View,long:Boolean=false){
  val uri=v.tag as? Uri;val xy=IntArray(2);var w=0;var h=0
  await("Touch target not attached/focused"){
   var ready=false
   ins.runOnMainSync{
    fun locate(root:View):View?{if(uri!=null&&root.tag==uri&&root.isAttachedToWindow)return root;if(root is android.view.ViewGroup)for(i in 0 until root.childCount){val found=locate(root.getChildAt(i));if(found!=null)return found};return null}
    val target=if(uri!=null)locate(gallery!!.window.decorView)else v
    if(target!=null&&target.isAttachedToWindow&&target.hasWindowFocus()){target.getLocationOnScreen(xy);w=target.width;h=target.height;ready=w>0&&h>0}
   };ready
  }
  val down=SystemClock.uptimeMillis();for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0]+w/2f,xy[1]+h/2f,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;ins.sendPointerSync(e);e.recycle();if(long&&action==MotionEvent.ACTION_DOWN)Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong()+300)};ins.waitForIdleSync()
 }
 private fun card(uri:Uri):View{val grid=nodes().filterIsInstance<RecyclerView>().first();val index=CaptureCatalog(context).all().indexOfFirst{it.uri==uri};assertTrue(index>=0);await("Library not loaded"){var count=0;ins.runOnMainSync{count=grid.adapter?.itemCount?:0};count>index};ins.runOnMainSync{grid.scrollToPosition(index)};var view:View?=null;await("Fixture card absent"){ins.runOnMainSync{view=grid.findViewHolderForAdapterPosition(index)?.itemView};view?.isShown==true&&view?.tag==uri&&!grid.isAnimating};return view!!}
 @Before fun unlocked(){assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
 @After fun cleanup(){fixtures.forEach{uri->try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null);context.contentResolver.delete(uri,null,null)}catch(_:Exception){}};ins.runOnMainSync{gallery?.finish()}}
 @Test fun longPressCanCancelThenTrashSingleVideoAndMixedMultipleSelection(){
  for(count in listOf(1,2)){
   val items=if(count==1)listOf(insert(true))else listOf(insert(false),insert(true))
   gallery=ins.startActivitySync(Intent(context,CapturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as CapturesActivity
   tap(card(items[0]),true);await("Long press did not select"){nodes().filterIsInstance<TextView>().any{it.text.toString()=="1 selected"}}
   ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("Back did not cancel selection"){nodes().filterIsInstance<TextView>().any{it.text.toString()=="Captures"}}
   assertTrue(items.all{CaptureCatalog(context).exists(it)})
   tap(card(items[0]),true);items.drop(1).forEach{tap(card(it))};await("Wrong selected count ($count)"){nodes().filterIsInstance<TextView>().any{it.text.toString()=="$count selected"}}
   tap(nodes().first{it.contentDescription?.toString()=="Delete selected"})
   var positive:AccessibilityNodeInfo?=null
   await("Android trash confirmation absent"){
    val root=ins.uiAutomation.rootInActiveWindow
    positive=root?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull{it.isClickable&&it.packageName?.toString()!="dev.zeppcamera.poc"}
    positive!=null
   }
   assertTrue(positive!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
   await("Trashed fixture remained in gallery"){items.none{CaptureCatalog(context).exists(it)}&&nodes().filterIsInstance<TextView>().any{it.text.toString()=="Captures"}}
   assertTrue(CaptureCatalog(context).all().none{it.uri in items});ins.runOnMainSync{gallery?.finish()};Thread.sleep(250)
  }
 }
}
