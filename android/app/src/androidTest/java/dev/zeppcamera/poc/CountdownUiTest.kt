package dev.zeppcamera.poc
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CountdownUiTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val fixtures=mutableSetOf<Uri>();private var activity:MainActivity?=null
 private fun http(path:String,body:JSONObject?=null):JSONObject{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}}
 private fun state()=http("/status").getJSONObject("state")
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+35000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(60)};fail(message)}
 private fun post(op:String,args:JSONObject=JSONObject()):String{val id="shots_history_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));return id}
 private fun result(id:String):JSONObject{var reply=JSONObject();await("Command did not complete"){reply=http("/result/$id");reply.has("ok")};return reply}
 private fun command(op:String,args:JSONObject=JSONObject())=result(post(op,args))
 private fun configure(count:Int,timer:Int=0){val r=command("CONFIG",JSONObject().put("mode","Photo").put("shotCount",count).put("timer",timer));assertTrue(r.toString(),r.optBoolean("ok"));await("Camera not ready"){state().optBoolean("ready")&&!state().optBoolean("busy")}}
 private fun uris()=CaptureCatalog(context).all().map{it.uri}.toSet()
 @Before fun start(){assertFalse("Unlock phone before tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked);activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("Camera not ready"){http("/session").optBoolean("ok")&&state().optBoolean("ready")};configure(1)}
 @After fun cleanup(){fixtures.forEach{uri->try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null);context.contentResolver.delete(uri,null,null)}catch(_:Exception){}};ins.runOnMainSync{activity?.finish()};context.stopService(Intent(context,CameraSessionService::class.java))}
 @Test fun phoneShowsThreeTwoOneAndReportsRelativeRemainingTime(){
  configure(1,3);val before=uris();val id=post("SHUTTER");val seen=mutableSetOf<Int>();val remaining=mutableListOf<Long>();var reply=JSONObject()
  await("Timed shutter did not finish"){
   var text="";ins.runOnMainSync{val title=MainActivity::class.java.getDeclaredField("title").apply{isAccessible=true}.get(activity)as android.widget.TextView;text=title.text.toString()};Regex(" · ([0-9]+)s$").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let{seen+=it}
   val s=state();if(s.optBoolean("countdown")){val ms=s.optLong("countdownRemainingMs");assertTrue(ms in 0..3000);remaining+=ms};reply=http("/result/$id");reply.has("ok")
  }
  fixtures+=uris()-before;assertTrue(reply.toString(),reply.optBoolean("ok"));assertTrue("Missing visible countdown digits: $seen",seen.containsAll(listOf(3,2,1)));assertTrue(remaining.isNotEmpty());assertTrue(remaining.zipWithNext().all{(a,b)->b<=a});assertEquals(0,state().optLong("countdownRemainingMs"))
 }
 @Test fun cancelClearsCountdownAndDoesNotTakeAPhoto(){
  configure(1,10);val before=uris();val id=post("SHUTTER");await("Countdown did not start"){state().optBoolean("countdown")};assertTrue(command("STOP").optBoolean("ok"));assertFalse(result(id).optBoolean("ok"));assertFalse(state().optBoolean("countdown"));assertEquals(0,state().optLong("countdownRemainingMs"));Thread.sleep(500);assertEquals(before,uris())
 }
}
