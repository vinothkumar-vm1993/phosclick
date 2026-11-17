package dev.zeppcamera.poc

import android.graphics.Bitmap
import android.widget.ImageView
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Injects real touch events into the native preview/slider; no camera mock. */
@RunWith(AndroidJUnit4::class)
class ZoomGestureTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context=ins.targetContext
    private var activity:MainActivity?=null
    private fun http(path:String,body:JSONObject?=null):JSONObject{
        val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)?:error("Approve watch first")
        val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=4000;c.setRequestProperty("Authorization","Bearer $token")
        if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
        return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}
    }
    private fun state()=http("/status").getJSONObject("state")
    private fun await(message:String,condition:()->Boolean){val end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){ins.waitForIdleSync();try{if(condition())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
    private fun start(){activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("No session"){http("/session").getBoolean("ok")};await("No ready camera"){state().getBoolean("ready")};reset();await("Camera UI not ready for touch"){activity?.hasWindowFocus()==true&&view("zoomButton").isEnabled&&view("preview").width>100}}
    private fun reset(){val id="gesture_${SystemClock.uptimeMillis()}";http("/command",JSONObject().put("v",1).put("id",id).put("op","ZOOM").put("args",JSONObject().put("ratio",1.0)).put("expires",System.currentTimeMillis()+25000));await("Zoom reset failed"){http("/result/$id").optBoolean("ok")&&kotlin.math.abs(state().getDouble("zoomRatio")-1)<.03}}
    private fun view(name:String):View{var result:View?=null;ins.runOnMainSync{result=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)as View};return result!!}
    private fun bounds(v:View):IntArray{val b=IntArray(4);ins.runOnMainSync{v.getLocationOnScreen(b);b[2]=v.width;b[3]=v.height};return b}
    private fun event(down:Long,action:Int,points:List<Pair<Float,Float>>){
        val properties=Array(points.size){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
        val coordinates=Array(points.size){i->MotionEvent.PointerCoords().apply{x=points[i].first;y=points[i].second;pressure=1f;size=1f}}
        val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,points.size,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
        ins.sendPointerSync(e);e.recycle()
    }
    private fun tap(v:View){val b=bounds(v);val pt=listOf((b[0]+b[2]/2f)to(b[1]+b[3]/2f));val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,pt);event(down,MotionEvent.ACTION_UP,pt);ins.waitForIdleSync()}
    @Before fun requireUnlockedPhone(){assertFalse("Unlock the phone before touchscreen tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)}
    @After fun cleanup(){ins.runOnMainSync{activity?.finish()};context.stopService(Intent(context,CameraSessionService::class.java));ins.waitForIdleSync()}
    @Test fun pinchChangesActualCameraZoom(){
        start();val b=bounds(view("preview"));val x=b[0]+b[2]/2f;val y=b[1]+b[3]/2f;val down=SystemClock.uptimeMillis()
        event(down,MotionEvent.ACTION_DOWN,listOf((x-300)to y));event(down,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf((x-300)to y,(x+300)to y))
        for(i in 1..20){val radius=300+i*7.5f;event(down,MotionEvent.ACTION_MOVE,listOf((x-radius)to y,(x+radius)to y));Thread.sleep(16)}
        event(down,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf((x-450)to y,(x+450)to y));event(down,MotionEvent.ACTION_UP,listOf((x-450)to y))
        await("Pinch did not zoom the real camera; "+listOf("pinchStarts","pinchSamples").associateWith{MainActivity::class.java.getDeclaredField(it).apply{isAccessible=true}.get(activity)}+" bounds="+b.toList()){state().getDouble("zoomRatio")>1.3}
    }
    @Test fun sliderSwipeChangesActualCameraZoom(){
        start();tap(view("zoomButton"));Thread.sleep(240);await("Slider did not finish layout"){view("zoomSlider").width>100};val slider=view("zoomSlider");val b=bounds(slider);assertTrue(slider.visibility==View.VISIBLE);val y=b[1]+b[3]/2f;val down=SystemClock.uptimeMillis();val x=b[0]+b[2]*.15f
        event(down,MotionEvent.ACTION_DOWN,listOf(x to y))
        for(i in 1..20){event(down,MotionEvent.ACTION_MOVE,listOf((x+b[2]*.025f*i)to y));Thread.sleep(16)}
        event(down,MotionEvent.ACTION_UP,listOf((x+b[2]*.5f)to y));await("Slider did not zoom the real camera"){state().getDouble("zoomRatio")>1.3}
    }

    private fun swipePreview(left:Boolean){
        val b=bounds(view("preview"));val y=b[1]+b[3]/2f;val from=b[0]+b[2]*(if(left).8f else .2f);val to=b[0]+b[2]*(if(left).2f else .8f);val down=SystemClock.uptimeMillis()
        event(down,MotionEvent.ACTION_DOWN,listOf(from to y));for(i in 1..20){event(down,MotionEvent.ACTION_MOVE,listOf((from+(to-from)*i/20f)to y));Thread.sleep(16)};event(down,MotionEvent.ACTION_UP,listOf(to to y))
    }
    @Test fun previewSwipesChangeModesAndPinchDoesNotSwitchMode(){
        start();swipePreview(true);await("Left swipe did not choose Video"){state().getString("mode")=="Video"&&state().getBoolean("ready")};swipePreview(false);await("Right swipe did not return to Photo"){state().getString("mode")=="Photo"&&state().getBoolean("ready")}
    }
    @Test fun autoFlashAndOnHaveDifferentRenderedIcons(){
        start();assertFalse(state().optBoolean("front"));assertEquals("OFF",state().getString("flash"));tap(view("flashButton"));await("Auto flash not set"){state().getString("flash")=="AUTO"}
        fun pixels():Int{var hash=0;val v=view("flashButton")as ImageView;ins.runOnMainSync{val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);v.drawable.setBounds(0,0,96,96);v.drawable.draw(android.graphics.Canvas(bitmap));val data=IntArray(96*96);bitmap.getPixels(data,0,96,0,0,96,96);hash=data.contentHashCode();bitmap.recycle()};return hash}
        val auto=pixels();tap(view("flashButton"));await("On flash not set"){state().getString("flash")=="ON"};assertNotEquals("Auto and On icons must be visibly distinct",auto,pixels());tap(view("flashButton"))
    }

    @Test fun tapFocusAndContextualExposureChangeActualCamera(){
        start();val initial=state().optInt("focusAttempts");tap(view("preview"))
        await("Preview tap did not meter actual camera"){state().optInt("focusAttempts")>initial&&state().optString("focusStatus")!="pending"}
        val overlay=view("focusOverlay");assertEquals(View.VISIBLE,overlay.visibility)
        fun field(name:String):Float{var v=0f;ins.runOnMainSync{v=FocusExposureOverlay::class.java.getDeclaredField(name).apply{isAccessible=true}.getFloat(overlay)};return v}
        val b=bounds(overlay);val x=b[0]+field("barX");val y=b[1]+field("trackTop")+60*context.resources.displayMetrics.density
        if(state().optInt("exposureMax")>0){val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,listOf(x to y));for(i in 1..12){event(down,MotionEvent.ACTION_MOVE,listOf(x to (y-i*4*context.resources.displayMetrics.density)));Thread.sleep(20)};event(down,MotionEvent.ACTION_UP,listOf(x to (y-48*context.resources.displayMetrics.density)));await("Sun drag did not change actual exposure"){state().optInt("exposureIndex")>0}}
        await("Contextual controls did not disappear"){overlay.visibility==View.GONE}
    }

    private fun command(op:String,args:JSONObject=JSONObject()){
        val id="focus_${SystemClock.uptimeMillis()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));await("Command failed: $op"){http("/result/$id").optBoolean("ok")};await("Camera not ready"){state().optBoolean("ready")&&!state().optBoolean("busy")}
    }
    @Test fun focusAndExposureAvailableAcrossNativeCameraPaths(){
        start()
        for(mode in listOf("Photo","Video","Slow Motion","Timelapse")){
            command("CONFIG",JSONObject().put("mode",mode));if(mode=="Video")command("SHUTTER");val recordingStart=state().optLong("startedAt");val initial=state().optInt("focusAttempts");tap(view("preview"));await("Metering did not complete for $mode"){state().optInt("focusAttempts")>initial&&state().optString("focusStatus")!="pending"}
            val maximum=state().optInt("exposureMax")
            if(maximum>0){val latch=java.util.concurrent.CountDownLatch(1);var failure:String?="No reply";ins.runOnMainSync{val session=MainActivity::class.java.getDeclaredField("session").apply{isAccessible=true}.get(activity)as CameraSessionService;session.setExposure(1){failure=it;latch.countDown()}};assertTrue("Exposure timed out: $mode",latch.await(6,java.util.concurrent.TimeUnit.SECONDS));assertNull("Exposure failed: $mode",failure);assertEquals(1,state().optInt("exposureIndex"))}
            if(mode=="Video"){assertTrue(state().optBoolean("recording"));assertEquals(recordingStart,state().optLong("startedAt"));command("STOP")}
        }
        command("CONFIG",JSONObject().put("mode","Photo"));command("SWITCH");assertTrue(state().optBoolean("front"));val initial=state().optInt("focusAttempts");tap(view("preview"));await("Front metering did not finish"){state().optInt("focusAttempts")>initial&&state().optString("focusStatus")!="pending"};command("SWITCH")
    }
}
