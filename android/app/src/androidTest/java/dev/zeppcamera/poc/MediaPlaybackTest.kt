package dev.zeppcamera.poc

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.widget.TextView
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Opens real CameraX/Camera2 captures, requiring a decoded frame, not just a viewer launch. */
@RunWith(AndroidJUnit4::class)
class MediaPlaybackTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val activities=mutableListOf<Activity>()
 private fun http(path:String,body:JSONObject?=null):JSONObject{
  val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)?:error("Approve watch first")
  val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token")
  if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
  return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}
 }
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
 private fun command(op:String,args:JSONObject=JSONObject()){
  val id="playback_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));await("$op failed"){http("/result/$id").optBoolean("ok")}
 }
 private fun nodes(a:Activity):List<View>{val out=mutableListOf<View>();ins.runOnMainSync{fun walk(v:View){out+=v;if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))};walk(a.window.decorView)};return out}
 private fun tap(v:View){val xy=IntArray(2);var w=0;var h=0;ins.runOnMainSync{v.getLocationOnScreen(xy);w=v.width;h=v.height};val down=SystemClock.uptimeMillis();for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0]+w/2f,xy[1]+h/2f,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;ins.sendPointerSync(e);e.recycle()}}
 @Before fun unlocked(){assertFalse("Unlock phone for playback tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
 @After fun cleanup(){ins.runOnMainSync{activities.reversed().forEach{it.finish()}};ins.waitForIdleSync()}
 @Test fun cameraThumbnailOpensRealPhotoAndAllThreeVideoKinds(){
  val camera=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;activities+=camera
  await("Camera not ready"){http("/session").optBoolean("ok")&&http("/status").getJSONObject("state").optBoolean("ready")};command("CONFIG",JSONObject().put("mode","Photo").put("timer",0));command("SHUTTER")
  // Generate owned movie fixtures here: another deletion test may have removed older fixtures.
  for(kind in listOf("Video","Slow Motion","Timelapse")){
   command("CONFIG",JSONObject().put("mode",kind));await("Movie camera not ready"){http("/status").getJSONObject("state").optBoolean("ready")&&!http("/status").getJSONObject("state").optBoolean("busy")};command("SHUTTER");Thread.sleep(if(kind=="Timelapse")6000 else 3000);command("STOP")
  }
  command("CONFIG",JSONObject().put("mode","Photo"));
  val monitor=ins.addMonitor(CapturesActivity::class.java.name,null,false)
  await("Gallery thumbnail not ready"){nodes(camera).any{it.contentDescription=="Open captured photos and videos"&&it.isShown}}
  tap(nodes(camera).first{it.contentDescription=="Open captured photos and videos"})
  val gallery=ins.waitForMonitorWithTimeout(monitor,7000)as? CapturesActivity?:error("Thumbnail did not open library");ins.removeMonitor(monitor);activities+=gallery
  val grid=nodes(gallery).filterIsInstance<RecyclerView>().first()
  for(kind in listOf("Photo","Video","Slow Motion","Timelapse")){
   val captures=CaptureCatalog(context).all();val index=captures.indexOfFirst{it.kind==kind};assertTrue("Create a real $kind test capture first",index>=0)
   await("Library reload not complete"){var count=0;ins.runOnMainSync{count=grid.adapter?.itemCount?:0};count==captures.size}
   ins.runOnMainSync{grid.scrollToPosition(index)}
   var card:View?=null;await("$kind card not visible"){ins.runOnMainSync{card=grid.findViewHolderForAdapterPosition(index)?.itemView};card?.isShown==true&&card?.tag==captures[index].uri&&!grid.isComputingLayout&&grid.itemAnimator?.isRunning!=true}
   Thread.sleep(250);val viewerMonitor=ins.addMonitor(MediaViewerActivity::class.java.name,null,false);tap(card!!)
   val viewer=ins.waitForMonitorWithTimeout(viewerMonitor,7000)as? MediaViewerActivity?:error("$kind viewer not opened");ins.removeMonitor(viewerMonitor);activities+=viewer;assertEquals("Touched card opened the wrong URI",captures[index].uri.toString(),viewer.intent.getStringExtra("uri"))
   if(kind=="Photo")await("Actual photo did not decode"){val views=nodes(viewer);views.any{it.contentDescription=="Captured photo review"&&it is ImageView&&it.drawable is BitmapDrawable}&&views.filterIsInstance<TextView>().none{it.isShown&&it.text.toString().contains("Loading photo")}}
   else await("$kind never rendered a decoded video frame"){nodes(viewer).any{it.contentDescription?.toString()?.contains("Frame rendered")==true}}
   assertTrue(nodes(viewer).any{it.contentDescription=="Open capture in gallery"})
   ins.runOnMainSync{viewer.finish()};Thread.sleep(300)
  }
 }
}
