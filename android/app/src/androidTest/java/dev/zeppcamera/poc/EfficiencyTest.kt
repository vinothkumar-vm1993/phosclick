package dev.zeppcamera.poc
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.util.LruCache
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class EfficiencyTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private var activity:MainActivity?=null;private var gallery:CapturesActivity?=null;private val fixtures=mutableSetOf<android.net.Uri>()
 private fun http(path:String,body:JSONObject?=null):JSONObject{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}}
 private fun state()=http("/status").getJSONObject("state")
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){ins.waitForIdleSync();try{if(test())return}catch(_:Exception){};Thread.sleep(60)};fail(message)}
 private fun post(op:String,args:JSONObject=JSONObject()):String{val id="efficiency_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));return id}
 private fun command(op:String,args:JSONObject=JSONObject()):JSONObject{val id=post(op,args);var r=JSONObject();await("$op failed"){r=http("/result/$id");r.has("ok")};assertTrue(r.toString(),r.optBoolean("ok"));return r}
 private fun field(name:String):Any?{var r:Any?=null;ins.runOnMainSync{r=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)};return r}
 private fun record(key:String,value:JSONObject){val p=java.io.File(context.filesDir,"efficiency-results.json");val r=runCatching{JSONObject(p.readText())}.getOrDefault(JSONObject());r.put(key,value);p.writeText(r.toString())}
 @Before fun start(){assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked);activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("Camera not ready"){http("/session").optBoolean("ok")&&state().optBoolean("ready")};command("CONFIG",JSONObject().put("mode","Photo").put("timer",0).put("shotCount",1))}
 @After fun cleanup(){ins.runOnMainSync{gallery?.finish();activity?.finish()};context.stopService(Intent(context,CameraSessionService::class.java));fixtures.forEach{context.contentResolver.delete(it,null,null)}}
 @Test fun repeatedStatusDoesNotResetZoomSliderOrRebuildControls(){
  command("ZOOM",JSONObject().put("ratio",2));val slider=field("zoomSlider")as Slider;await("Zoom UI not synchronized"){kotlin.math.abs(slider.value-2f)<.02f};var events=0;val before=field("uiRenderPasses")as Int
  val service=field("session")as CameraSessionService;ins.runOnMainSync{slider.addOnChangeListener{_,_,_->events++};repeat(30){service.publish()}}
  assertEquals("Unchanged state reset slider",0,events);val after=field("uiRenderPasses")as Int;assertEquals("Unchanged state rebuilt controls",before,after);assertEquals(2f,slider.value,.02f)
  record("status_dedup",JSONObject().put("repeated_publications",30).put("slider_change_events",events).put("control_rebuilds",after-before))
 }
 @Test fun timerLongPollUsesBoundedRequestsAndOneCapture(){
  command("CONFIG",JSONObject().put("timer",3));val id=post("SHUTTER");var r=JSONObject();var polls=0;val start=SystemClock.elapsedRealtime()
  while(!r.has("ok")&&polls<8){r=http("/result/$id?wait=1");polls++}
  assertTrue(r.toString(),r.optBoolean("ok"));assertEquals(1,r.getJSONObject("state").optInt("shotsTaken"));fixtures+=android.net.Uri.parse(r.getJSONObject("state").getString("latestMediaKey"));assertTrue("Result used excessive polling",polls<=4)
  record("long_poll",JSONObject().put("timer_seconds",3).put("requests",polls).put("elapsed_ms",SystemClock.elapsedRealtime()-start))
 }
 @Test fun unchangedGalleryReloadKeepsDecodedThumbnail(){
  val capture=command("SHUTTER");val uri=android.net.Uri.parse(capture.getJSONObject("state").getString("latestMediaKey"));fixtures+=uri;val item=CaptureCatalog(context).capture(uri)!!;val key="${uri}:${item.modified}"
  gallery=ins.startActivitySync(Intent(context,CapturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as CapturesActivity
  @Suppress("UNCHECKED_CAST") val cache=CapturesActivity::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(gallery)as LruCache<String,Bitmap>
  await("Gallery thumbnail did not load"){cache.get(key)!=null};val original=cache.get(key)
  ins.runOnMainSync{CapturesActivity::class.java.getDeclaredMethod("reload").apply{isAccessible=true}.invoke(gallery)};Thread.sleep(700);ins.waitForIdleSync();assertSame("Unchanged reload discarded decoded bitmap",original,cache.get(key));record("gallery_cache",JSONObject().put("same_bitmap_reused",true))
 }
 @Test fun disconnectReleasesSessionWakeLock(){
  val service=field("session")as CameraSessionService;var wake:android.os.PowerManager.WakeLock?=null
  ins.runOnMainSync{wake=CameraSessionService::class.java.getDeclaredField("wake").apply{isAccessible=true}.get(service)as? android.os.PowerManager.WakeLock};assertTrue("Active camera wake lock missing",wake?.isHeld==true)
  ins.runOnMainSync{service.disconnect()};await("Wake lock remained held after disconnect"){wake?.isHeld==false};record("wake_lock",JSONObject().put("released_after_disconnect",true))
 }
}
