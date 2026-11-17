package dev.zeppcamera.poc

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class SettingsPageTest {
    private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
    private val activities=mutableListOf<Activity>()
    private fun http(path:String,body:JSONObject?=null):JSONObject{
        val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)?:error("Approve watch first")
        val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token")
        if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
        return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}
    }
    private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){ins.waitForIdleSync();try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
    private fun state()=http("/status").getJSONObject("state")
    private fun configure(args:JSONObject){val id="settings_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op","CONFIG").put("args",args).put("expires",System.currentTimeMillis()+25000));await("Settings command failed"){http("/result/$id").optBoolean("ok")}}
    private fun find(activity:Activity,description:String):View?{var result:View?=null;ins.runOnMainSync{fun walk(v:View){if(v.contentDescription?.toString()==description)result=v;if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))};walk(activity.window.decorView)};return result}
    private fun tap(view:View){val xy=IntArray(2);var w=0;var h=0;ins.runOnMainSync{view.getLocationOnScreen(xy);w=view.width;h=view.height};val down=SystemClock.uptimeMillis();for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0]+w/2f,xy[1]+h/2f,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;ins.sendPointerSync(e);e.recycle()};Thread.sleep(250);ins.waitForIdleSync()}
    @Before fun unlocked(){assertFalse("Unlock phone for settings touch tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
    @After fun cleanup(){try{configure(JSONObject().put("mode","Photo").put("timer",0).put("shotCount",1).put("aspect","4:3"))}catch(_:Exception){};ins.runOnMainSync{activities.reversed().forEach{it.finish()}};context.stopService(Intent(context,CameraSessionService::class.java));ins.waitForIdleSync()}
    @Test fun settingsIsAnAmoledPageWithWorkingContextualOptions(){
        val camera=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;activities+=camera
        await("No camera session"){http("/session").optBoolean("ok")};await("Camera not ready"){state().optBoolean("ready")};configure(JSONObject().put("mode","Photo").put("timer",0))
        val monitor=ins.addMonitor(SettingsActivity::class.java.name,null,false);await("Settings button not ready"){find(camera,"Capture settings")?.isEnabled==true};tap(find(camera,"Capture settings")!!)
        val settings=ins.waitForMonitorWithTimeout(monitor,6000)as? SettingsActivity?:error("Settings did not open as an activity");activities+=settings;ins.removeMonitor(monitor)
        await("Settings rows not loaded"){find(settings,"Timer settings")?.isClickable==true}
        ins.runOnMainSync{val typed=android.util.TypedValue();assertTrue(settings.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface,typed,true));assertEquals(Color.BLACK,typed.data);assertTrue(settings.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh,typed,true));assertEquals(Color.BLACK,typed.data)}
        assertNull("Photo must not show a video FPS control",find(settings,"Frame rate settings"))
        await("Shots control missing"){find(settings,"Number of photos per shutter")?.isClickable==true};tap(find(settings,"Number of photos per shutter")!!);await("Shots picker missing"){find(settings,"Choose Shots: 3×")!=null};tap(find(settings,"Choose Shots: 3×")!!);await("Shot count not applied"){state().optInt("shotCount")==3&&find(settings,"Number of photos per shutter")!=null}
        tap(find(settings,"Timer settings")!!);await("Timer picker did not open"){find(settings,"Choose Timer: 3s")!=null};tap(find(settings,"Choose Timer: 3s")!!);await("Timer was not applied"){state().optInt("timer")==3&&find(settings,"Timer settings")!=null}
        configure(JSONObject().put("mode","Video").put("timer",0));await("Video-specific settings missing"){find(settings,"Frame rate settings")!=null&&find(settings,"Record audio using phone microphone")!=null}
        assertNull("Video must not show photo shot count",find(settings,"Number of photos per shutter"))
        ins.runOnMainSync{settings.finish()};await("Camera did not return"){find(camera,"Capture settings")?.isShown==true};assertTrue(state().optBoolean("paired"))
    }
}
