package dev.zeppcamera.poc

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class ClosePrivacyTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext;private var activity:MainActivity?=null
 private fun http(path:String,body:JSONObject?=null):JSONObject{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=1000;c.readTimeout=3000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}}
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
 private fun command(op:String,args:JSONObject=JSONObject()){val id="privacy_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));await("$op failed"){http("/result/$id").optBoolean("ok")}}
 private fun start(){activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("No camera session"){http("/session").optBoolean("ok")&&http("/status").getJSONObject("state").optBoolean("ready")}}
 private fun cameraClosed():Boolean{
  val descriptor=ins.uiAutomation.executeShellCommand("dumpsys media.camera");val text=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use{it.readText()}
  // Only the active camera-client section is relevant; the historical event log contains old clients.
  val active=text.substringBefore("== Camera service events log")
  val microphone=ins.uiAutomation.executeShellCommand("cmd appops get dev.zeppcamera.poc RECORD_AUDIO");val micState=android.os.ParcelFileDescriptor.AutoCloseInputStream(microphone).bufferedReader().use{it.readText()}
  return !active.contains("dev.zeppcamera.poc")&&!micState.contains("running",ignoreCase=true)
 }
 private fun listenerClosed()=try{http("/health");false}catch(_:Exception){true}
 @Before fun unlocked(){assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
 @After fun cleanup(){ins.runOnMainSync{activity?.finish()};context.stopService(Intent(context,CameraSessionService::class.java))}
 @Test fun rootBackReleasesCameraAndFreshOpenCanReconnect(){start();ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("Back left the listener alive"){listenerClosed()};await("Back left camera hardware active"){cameraClosed()};start();command("CONFIG",JSONObject().put("mode","Photo").put("timer",0));command("SHUTTER")}
 @Test fun removingTaskStopsActiveRecordingAndReleasesCamera(){
  start();command("CONFIG",JSONObject().put("mode","Video").put("quality","HD").put("fps",30).put("timer",0));val before=CaptureCatalog(context).all().map{it.uri}.toSet();command("SHUTTER");Thread.sleep(1200)
  ins.runOnMainSync{activity!!.finishAndRemoveTask()};await("Removed task left listener alive"){listenerClosed()};await("Removed task left camera active"){cameraClosed()};await("Closing task failed to finalize recording"){CaptureCatalog(context).all().any{it.video&&it.uri !in before}}
 }
}
