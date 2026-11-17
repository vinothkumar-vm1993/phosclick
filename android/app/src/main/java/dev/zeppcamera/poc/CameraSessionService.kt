package dev.zeppcamera.poc

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Range
import android.util.Rational
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File


class CameraSessionService:android.app.Service(),androidx.lifecycle.LifecycleOwner {
    private val registry=androidx.lifecycle.LifecycleRegistry(this)
    override val lifecycle:androidx.lifecycle.Lifecycle get()=registry
    inner class SessionBinder:Binder(){val session get()=this@CameraSessionService}
    private val binder=SessionBinder()
    private val sessionId=java.util.UUID.randomUUID().toString()
    private var phoneHapticClaimed=false
    private var audioWanted=true
    fun setAudioEnabled(enabled:Boolean):String?{
        if(isRecording()||saving||finalizing)return "Stop recording first"
        if(enabled&&!has(Manifest.permission.RECORD_AUDIO))return "Allow microphone access first"
        audioWanted=enabled;getSharedPreferences("camera_options",MODE_PRIVATE).edit().putBoolean("record_audio",enabled).apply();show("Audio setting saved");return null
    }
    fun claimConnectionHaptic():Boolean{if(!paired||phoneHapticClaimed)return false;phoneHapticClaimed=true;return true}
    var uiOwner:Any?=null
    var listener:((String,JSONObject)->Unit)?=null
    var approval:(((Boolean)->Unit)->Unit)?=null
    var connectionLive=false;private set
    var paired=false;private set
    private var stopping=false
    private var connectionGeneration=0
    private var bindGeneration=0
    private var previewReadyAt=0L
    private var camera:Camera?=null
    private var flash="OFF";private var torch=false
    lateinit var bridge:LocalBridge;private set
    lateinit var renderer:CameraPreviewRenderer;private set
    private lateinit var lapse:TimelapseCamera
    private var provider:ProcessCameraProvider?=null
    private var imageCapture:ImageCapture?=null
    private var videoCapture:VideoCapture<Recorder>?=null
    private var recording:Recording?=null
    private var front=false
    private var mode="Photo";private var quality="HD";private var fps=30;private var timer=0;private var aspect="4:3";private var photoSize="Auto";private var shotCount=1
    private var shotsTaken=0;private var shotsTarget=0;private var photoInFlight=false;private var nextPhoto:Runnable?=null
    private val photoDeadline=Runnable{cancelPhotos("Photo sequence timed out; check gallery before retrying")}
    private val photoSizeCache=mutableMapOf<Boolean,List<String>>()
    private val qualityCache=mutableMapOf<Pair<Boolean,String>,List<String>>()
    private val rateCache=mutableMapOf<Triple<Boolean,String,String>,List<Int>>()
    private var configuring=false
    private var ready=false;private var saving=false;private var finalizing=false;private var foreground=true
    private var countdownSeconds=0
    private var countdown:CountDownTimer?=null
    private var countdownEndElapsed=0L
    private var startedAt=0L
    private var lastUri:Uri?=null
    private var lapseFile:File?=null
    private val captureHistory=CaptureHistory()
    private var replyCapture:((JSONObject)->Unit)?=null
    private var replyStop:((JSONObject)->Unit)?=null
    private val main by lazy{ContextCompat.getMainExecutor(this)}
    private val handler=Handler(Looper.getMainLooper())
    private val catalog by lazy{CaptureCatalog(this)}
    private val imageWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var latestMediaKey="";private var mediaGeneration=0
    private val refreshMedia=Runnable{
        val generation=++mediaGeneration
        try{imageWorker.execute{val key=try{catalog.latest()?.toString()?:""}catch(_:Exception){""};main.execute{if(!stopping&&generation==mediaGeneration){latestMediaKey=key;publish()}}}}catch(_:java.util.concurrent.RejectedExecutionException){}
    }
    private val mediaObserver=object:android.database.ContentObserver(handler){override fun onChange(selfChange:Boolean){handler.removeCallbacks(refreshMedia);handler.postDelayed(refreshMedia,150)}}
    private var batteryPercent=-1;private var batteryCharging=true;private var storageFreeBytes=-1L
    private val resourceWarning get()=ResourceWarning.evaluate(batteryPercent,batteryCharging,storageFreeBytes)
    private val resourcePoll=object:Runnable{override fun run(){
        if(stopping)return
        try{imageWorker.execute{
            val battery=getSystemService(android.os.BatteryManager::class.java)
            val percent=runCatching{battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}.getOrDefault(-1)
            val charging=runCatching{battery.isCharging}.getOrDefault(true)
            val free=runCatching{android.os.StatFs(android.os.Environment.getExternalStorageDirectory().absolutePath).availableBytes}.getOrDefault(-1L)
            main.execute{if(!stopping){val old=resourceWarning;batteryPercent=if(percent in 0..100)percent else -1;batteryCharging=charging;storageFreeBytes=free;if(old!=resourceWarning)publish()}}
        }}catch(_:java.util.concurrent.RejectedExecutionException){}
        handler.postDelayed(this,30000)
    }}
    private var focusStatus="idle";private var focusAttempts=0;private var focusX=.5f;private var focusY=.5f;private var focusGeneration=0
    private var exposureGeneration=0
    private fun exposureMin()=if(mode=="Timelapse")lapse.exposureMin else camera?.cameraInfo?.exposureState?.exposureCompensationRange?.lower?:0
    private fun exposureMax()=if(mode=="Timelapse")lapse.exposureMax else camera?.cameraInfo?.exposureState?.exposureCompensationRange?.upper?:0
    private fun exposureIndex()=if(mode=="Timelapse")lapse.exposureIndex else camera?.cameraInfo?.exposureState?.exposureCompensationIndex?:0
    private fun exposureStep()=if(mode=="Timelapse")lapse.exposureStep else camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat()?:0f
    fun setExposure(index:Int,done:(String?)->Unit){
        if(!paired||!ready||configuring||saving||finalizing||index !in exposureMin()..exposureMax()||exposureMax()<=exposureMin()){done("Exposure unavailable");return}
        val generation=++exposureGeneration
        if(mode=="Timelapse"){lapse.setExposure(index){error->if(generation==exposureGeneration){publish();done(error)}else done("Exposure superseded")};return}
        val c=camera?:return done("Camera not ready")
        val future=c.cameraControl.setExposureCompensationIndex(index)
        future.addListener({if(c!==camera||generation!=exposureGeneration){done("Exposure superseded");return@addListener};try{future.get();publish();done(null)}catch(_:Exception){done("Exposure unavailable")}},main)
    }
    fun focusAt(display:Display,x:Float,y:Float,viewWidth:Int,viewHeight:Int,done:(Boolean)->Unit){
        if(!paired||!ready||configuring||saving||finalizing||viewWidth<=0||viewHeight<=0||!x.isFinite()||!y.isFinite()||x !in 0f..1f||y !in 0f..1f){done(false);return}
        val point=PreviewMetering.uncrop(x,y,renderer.uprightInputAspect(),viewWidth.toFloat()/viewHeight)
        val generation=++focusGeneration;val binding=bindGeneration;focusAttempts++;focusX=point.x;focusY=point.y;focusStatus="pending";publish()
        fun finish(ok:Boolean){if(generation==focusGeneration&&binding==bindGeneration){focusStatus=if(ok)"set"else"not_locked";publish();done(ok)}else done(false)}
        if(mode=="Timelapse"){val degrees=when(display.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0};lapse.focusAt(point.x,point.y,degrees,front,::finish);return}
        val c=camera?:return finish(false)
        try{
            val factory=androidx.camera.core.DisplayOrientedMeteringPointFactory(display,c.cameraInfo,1f,1f)
            val action=FocusMeteringAction.Builder(factory.createPoint(point.x,point.y),FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).setAutoCancelDuration(3,java.util.concurrent.TimeUnit.SECONDS).build()
            if(!c.cameraInfo.isFocusMeteringSupported(action)){finish(false);return}
            val future=c.cameraControl.startFocusAndMetering(action)
            future.addListener({try{finish(future.get().isFocusSuccessful)}catch(_:Exception){finish(false)}},main)
        }catch(_:Exception){finish(false)}
    }
    private var wake:PowerManager.WakeLock?=null
    private var lastMessage="Waiting for watch"
    private val lapseClock=object:Runnable{override fun run(){if(lapse.running){val seconds=((System.currentTimeMillis()-startedAt)/1000).coerceAtLeast(0);show("● REC  %02d:%02d".format(seconds/60,seconds%60));handler.postDelayed(this,1000)}}}
    private fun has(p:String)=ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED
    private fun isRecording()=recording!=null||lapse.running
    private fun idle()=!isRecording()&&!saving&&!finalizing&&!configuring&&countdown==null
    private fun rememberCapture(uri:Uri,kind:String){catalog.record(uri,kind);mediaGeneration++;latestMediaKey=uri.toString()}
    private fun show(message:String){lastMessage=message;listener?.invoke(message,state());notifySession()}
    fun publish(){listener?.invoke(lastMessage,state())}
    override fun onBind(intent:android.content.Intent)=binder
    override fun onCreate(){
        super.onCreate();audioWanted=getSharedPreferences("camera_options",MODE_PRIVATE).getBoolean("record_audio",true);registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        contentResolver.registerContentObserver(Uri.parse("content://media"),true,mediaObserver);handler.post(refreshMedia);handler.post(resourcePoll)
        renderer=CameraPreviewRenderer(main){previewReadyAt=SystemClock.elapsedRealtime()}
        lapse=TimelapseCamera(this,renderer)
        bridge=LocalBridge(this,{c,done->command(c,done)},{message->show(message)},{live->
            connectionLive=live
            if(live&&!paired){paired=true;startCamera()}else if(!live&&paired)show("Watch unavailable")
        },{done->approval?.invoke(done)?:done(false)},{disconnect()},{sessionId})
    }
    override fun onStartCommand(intent:android.content.Intent?,flags:Int,startId:Int):Int{
        if(intent?.action=="STOP"){disconnect();return START_NOT_STICKY}
        if(!has(Manifest.permission.CAMERA)){stopSelf();return START_NOT_STICKY}
        try{
            val type=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or if(audioWanted&&has(Manifest.permission.RECORD_AUDIO))android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(47835,notification(),type)
            if(!registry.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START);registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)}
            startBridge()
        }catch(e:Exception){listener?.invoke("Open phone app to reconnect",JSONObject().put("ready",false));stopSelf()}
        return START_NOT_STICKY
    }
    private var retries=0
    private fun startBridge(){try{bridge.start();retries=0}catch(e:Exception){if(retries++<4)handler.postDelayed({if(!stopping)startBridge()},250L*retries)else{show("Connection unavailable");disconnect()}}}
    private var notificationChannelReady=false;private var notificationKey=""
    private fun notification():android.app.Notification{
        notificationKey="${isRecording()}:$connectionLive"
        val manager=getSystemService(android.app.NotificationManager::class.java)
        if(!notificationChannelReady){manager.createNotificationChannel(android.app.NotificationChannel("camera_session","Watch camera",android.app.NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)});notificationChannelReady=true}
        val open=android.app.PendingIntent.getActivity(this,1,android.content.Intent(this,MainActivity::class.java),android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=android.app.PendingIntent.getService(this,2,android.content.Intent(this,CameraSessionService::class.java).setAction("STOP"),android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        return android.app.Notification.Builder(this,"camera_session").setSmallIcon(R.drawable.ic_action_shutter).setContentTitle("WristSnap").setContentText(if(isRecording())"Recording · Watch control active"else if(connectionLive)"Watch connected"else "Watch control active").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).addAction(android.app.Notification.Action.Builder(null,"Disconnect",stop).build()).build()
    }
    private fun notifySession(){if(!stopping&&notificationKey!="${isRecording()}:$connectionLive")try{getSystemService(android.app.NotificationManager::class.java).notify(47835,notification())}catch(_:Exception){}}
    fun disconnect(){
        if(stopping)return;stopping=true;connectionLive=false;paired=false;if(zoomRunning)finishZoom("Disconnected");bridge.stop()
        countdown?.cancel();countdown=null;countdownSeconds=0;countdownEndElapsed=0;cancelPhotos("Capture stopped")
        fun finish(){provider?.unbindAll();lapse.close();ready=false;listener?.invoke("Disconnected",state());stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        val deadline=SystemClock.elapsedRealtime()+10000
        fun drain(){
            if(SystemClock.elapsedRealtime()>deadline){finish();return}
            if(saving||finalizing){handler.postDelayed({drain()},100);return}
            if(isRecording())stopCapture{finish()}else finish()
        }
        drain()
    }
    override fun onTaskRemoved(rootIntent:android.content.Intent?){disconnect();super.onTaskRemoved(rootIntent)}
    override fun onDestroy(){
        contentResolver.unregisterContentObserver(mediaObserver);handler.removeCallbacks(refreshMedia);imageWorker.shutdownNow()
        stopping=true;foreground=false;countdown?.cancel();countdownEndElapsed=0;cancelPhotos("Capture stopped");bridge.close();recording?.stop();recording=null;lapse.close();provider?.unbindAll();renderer.close();handler.removeCallbacksAndMessages(null);wake?.let{if(it.isHeld)it.release()};wake=null
        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY);listener=null;approval=null;uiOwner=null;super.onDestroy()
    }
    private var zoomTarget=1f
    private var zoomApplied=1f
    private var zoomRunning=false
    private var lastZoomPublish=0L
    private val zoomReplies=mutableListOf<(JSONObject)->Unit>()
    private fun zoomLimits():Pair<Float,Float> = if(mode=="Timelapse")lapse.minZoom to lapse.maxZoom else camera?.cameraInfo?.zoomState?.value?.let{it.minZoomRatio to it.maxZoomRatio}?: (1f to 1f)
    private fun zoomValue():Float=if(mode=="Timelapse")lapse.zoomRatio else camera?.cameraInfo?.zoomState?.value?.zoomRatio?:1f
    fun setZoomTarget(value:Float):String?{
        val (min,max)=zoomLimits()
        if(!paired||!ready||configuring||!value.isFinite()||value !in min..max)return "Zoom unavailable"
        zoomTarget=value
        if(!zoomRunning){zoomApplied=zoomValue();zoomRunning=true;handler.post(zoomPump)}
        return null
    }
    fun zoomTo(value:Float,done:(JSONObject)->Unit){
        val error=setZoomTarget(value)
        if(error!=null)done(result(false,error))else zoomReplies.add(done)
    }
    private fun finishZoom(error:String?=null){
        zoomRunning=false;handler.removeCallbacks(zoomPump)
        val replies=zoomReplies.toList();zoomReplies.clear();replies.forEach{it(result(error==null,error?:"Zoom applied"))}
        listener?.invoke(lastMessage,state())
    }
    private val zoomPump=object:Runnable{override fun run(){
        if(!zoomRunning||stopping)return
        if(!ready){finishZoom("Camera changing");return}
        val delta=zoomTarget-zoomApplied;val final=kotlin.math.abs(delta)<.012f
        zoomApplied=if(final)zoomTarget else zoomApplied+delta*.38f
        val wanted=zoomApplied
        if(mode=="Timelapse"){
            val error=lapse.setZoom(wanted);if(error!=null){finishZoom(error);return}
            if(final){finishZoom();return}
        }else{
            val future=camera?.cameraControl?.setZoomRatio(wanted)?:run{finishZoom("Camera unavailable");return}
            if(final){future.addListener({try{future.get();if(kotlin.math.abs(zoomTarget-wanted)<.001f)finishZoom()else handler.post(this)}catch(e:Exception){finishZoom("Zoom interrupted")}},main);return}
        }
        if(SystemClock.elapsedRealtime()-lastZoomPublish>80){lastZoomPublish=SystemClock.elapsedRealtime();listener?.invoke(lastMessage,state())}
        handler.postDelayed(this,16)
    }}
    private fun startCamera(){if(!paired||stopping)return;val f=ProcessCameraProvider.getInstance(this);f.addListener({if(stopping)return@addListener;try{provider=f.get();bind{if(it==null&&!stopping&&wake==null)wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WristSnap:Session").apply{acquire()};show(it?:"Ready")}}catch(e:Exception){show("Camera initialization failed: ${e.message}")}},main)}
    private fun selector()=if(front)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    private fun info()=try{selector().filter(provider?.availableCameraInfos?:emptyList()).firstOrNull()}catch(_:Exception){null}
    private fun q(name:String)=when(name){"UHD"->Quality.UHD;"FHD"->Quality.FHD;else->Quality.HD}
    private fun photoSizes():List<String>{
        if(!has(Manifest.permission.CAMERA))return listOf("Auto")
        photoSizeCache[front]?.let{return it}
        return try {val manager=getSystemService(android.hardware.camera2.CameraManager::class.java)
            val facing=if(front)0 else 1
            val id=manager.cameraIdList.first{manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)==facing}
            val sizes=manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(android.graphics.ImageFormat.JPEG)
            (listOf("Auto")+sizes.sortedByDescending{it.width.toLong()*it.height}.take(12).map{"${it.width}x${it.height}"}).also{photoSizeCache[front]=it}
        }catch(_:Exception){listOf("Auto")}
    }
    private fun qualities(targetMode:String=mode):List<String>{
        if(targetMode=="Timelapse")return listOf("HD","FHD")
        val i=info()?:return emptyList()
        val key=front to targetMode
        return qualityCache.getOrPut(key){
            val caps=if(targetMode=="Slow Motion")Recorder.getHighSpeedVideoCapabilities(i) else Recorder.getVideoCapabilities(i)
            val supported=caps?.getSupportedQualities(DynamicRange.SDR)?:emptyList()
            listOf("HD","FHD","UHD").filter{q(it) in supported}
        }
    }
    private fun frameRates(targetMode:String=mode,targetQuality:String=quality):List<Int>{
        if(targetMode=="Photo")return emptyList()
        if(targetMode=="Timelapse")return listOf(30)
        val i=info()?:return emptyList()
        return rateCache.getOrPut(Triple(front,targetMode,targetQuality)){
            try{
                val v=VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(q(targetQuality))).build())
                val session=if(targetMode=="Slow Motion")HighSpeedVideoSessionConfig.Builder(v).build() else SessionConfig.Builder(Preview.Builder().build(),v).build()
                i.getSupportedFrameRateRanges(session).filter{it.lower==it.upper}.map{it.upper}.filter{it in if(targetMode=="Slow Motion")listOf(120,240)else listOf(30,60)}.distinct().sorted()
            }catch(_:Exception){emptyList()}
        }
    }
    private fun previewReady()=if(mode=="Timelapse")lapse.previewReady else previewReadyAt>0L&&SystemClock.elapsedRealtime()-previewReadyAt>=350L&&previewReadyAt>0L
    private fun awaitPreview(generation:Int,done:(String?)->Unit){
        val until=SystemClock.elapsedRealtime()+6000L
        fun check(){
            if(generation!=bindGeneration)return
            if(!foreground||!paired){done("Camera no longer active");return}
            if(previewReady()){done(null);return}
            if(SystemClock.elapsedRealtime()>until){ready=false;done("Camera preview did not start. Reopen the camera app.");return}
            handler.postDelayed({check()},80)
        }
        check()
    }
    private fun bind(done:(String?)->Unit){
        if(zoomRunning)finishZoom("Camera changing")
        val generation=++bindGeneration;focusGeneration++;exposureGeneration++;focusStatus="idle"
        ready=false;previewReadyAt=0L
        if(!paired){done("Connect the watch first");return}
        val p=provider?:return done("Camera loading; retry Connect")
        p.unbindAll();camera=null;torch=false;lapse.close();imageCapture=null;videoCapture=null
        if(mode=="Timelapse"){
            fun open(){lapse.open(front,quality){error->ready=error==null;if(error!=null)done(error)else{val range=zoomLimits();zoomTarget=zoomTarget.coerceIn(range.first,range.second);lapse.setZoom(zoomTarget);awaitPreview(generation,done)}}}
            open()
            return
        }
        try{
            val view=Preview.Builder().build().also{it.setSurfaceProvider(renderer)}
            if(mode=="Photo"){
                val ib=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                if(photoSize!="Auto"){val parts=photoSize.split("x");ib.setResolutionSelector(androidx.camera.core.resolutionselector.ResolutionSelector.Builder().setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(android.util.Size(parts[0].toInt(),parts[1].toInt()),androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_NONE)).build())}
                imageCapture=ib.build().also{it.flashMode=when(flash){"ON"->ImageCapture.FLASH_MODE_ON;"AUTO"->ImageCapture.FLASH_MODE_AUTO;else->ImageCapture.FLASH_MODE_OFF};it.setCropAspectRatio(if(aspect=="4:3")Rational(3,4) else Rational(9,16))}
                camera=p.bindToLifecycle(this,selector(),view,imageCapture!!)
            }else{
                require(quality in qualities()){"Resolution unavailable on selected camera"}
                val vb=VideoCapture.Builder(Recorder.Builder().setQualitySelector(QualitySelector.from(q(quality))).build())
                if(mode=="Video")vb.setTargetFrameRate(Range(fps,fps))
                val v=vb.build();videoCapture=v
                if(mode=="Slow Motion"){
                    val b=HighSpeedVideoSessionConfig.Builder(v).setPreview(view).setSlowMotionEnabled(true)
                    val range=info()!!.getSupportedFrameRateRanges(b.build()).firstOrNull{it.lower==fps&&it.upper==fps}?:error("High-speed $fps fps unavailable; choose another rate or Video")
                    b.setFrameRateRange(range);camera=p.bindToLifecycle(this,selector(),b.build())
                }else{
                    camera=p.bindToLifecycle(this,selector(),view,v)
                }
            }
            val range=zoomLimits();zoomTarget=zoomTarget.coerceIn(range.first,range.second);camera?.cameraControl?.setZoomRatio(zoomTarget)
            ready=true;awaitPreview(generation,done)
        }catch(e:Exception){done(e.message?:"Camera binding failed")}
    }
    fun state():JSONObject=JSONObject().put("cameraGeneration",bindGeneration).put("focusStatus",focusStatus).put("focusAttempts",focusAttempts).put("focusX",focusX).put("focusY",focusY).put("exposureMin",exposureMin()).put("exposureMax",exposureMax()).put("exposureIndex",exposureIndex()).put("exposureStep",exposureStep()).put("resourceWarning",resourceWarning).put("batteryPercent",batteryPercent).put("batteryCharging",batteryCharging).put("storageFreeBytes",storageFreeBytes).put("latestMediaKey",latestMediaKey).put("audioEnabled",audioWanted&&has(Manifest.permission.RECORD_AUDIO)).put("microphoneGranted",has(Manifest.permission.RECORD_AUDIO)).put("sessionId",sessionId).put("zooming",zoomRunning).put("zoomRatio",zoomValue()).put("minZoom",zoomLimits().first).put("maxZoom",zoomLimits().second).put("countdownSeconds",countdownSeconds).put("countdown",countdown!=null).put("countdownRemainingMs",if(countdown!=null)(countdownEndElapsed-SystemClock.elapsedRealtime()).coerceIn(0L,10000L)else 0L).put("sessionActive",!stopping).put("busy",configuring||saving||finalizing||countdown!=null).put("paired",paired).put("connectionLive",connectionLive).put("mode",mode).put("quality",quality).put("fps",fps).put("timer",timer).put("shotCount",shotCount).put("shotsTaken",shotsTaken).put("shotsTarget",shotsTarget).put("aspect",if(mode=="Photo")aspect else "16:9")
        .put("flash",flash).put("torch",torch).put("flashAvailable",if(mode=="Timelapse")lapse.flashAvailable else info()?.hasFlashUnit()==true).put("photoSizeLabel",PhotoResolutions.label(photoSize,photoSizes())).put("photoSizeOptions",JSONArray(PhotoResolutions.options(photoSizes()).map{JSONObject().put("label",it.label).put("value",it.value)})).put("photoSize",photoSize).put("photoSizes",JSONArray(photoSizes())).put("front",front).put("recording",isRecording()).put("startedAt",startedAt).put("ready",ready&&previewReady()).put("previewReady",previewReady()).put("qualities",JSONArray(qualities())).put("frameRates",JSONArray(frameRates())).put("timelapseCaptureFps",1)
    private fun result(ok:Boolean,message:String)=JSONObject().put("ok",ok).put(if(ok)"message" else "error",message).put("state",state())
    fun configure(args:JSONObject,done:(JSONObject)->Unit){
        if(!paired){done(result(false,"WATCH_REQUIRED"));return}
        if(!idle()){done(result(false,"BUSY: stop recording or wait"));return}
        val old=listOf(mode,quality,fps.toString(),timer.toString(),aspect,photoSize,shotCount.toString())
        try{
            val m=args.optString("mode",mode)
            var f=args.optInt("fps",if(m!=mode){if(m=="Slow Motion")120 else 30}else fps)
            val available=qualities(m)
            val preferred=if(m!=mode&&quality !in available)available.firstOrNull()?:quality else quality
            val q=args.optString("quality",if(m=="Timelapse"&&preferred=="UHD")"HD" else preferred)
            if(m!="Photo"){
                val rates=frameRates(m,q)
                require(rates.isNotEmpty()){"$m unavailable on selected camera"}
                if(f !in rates&&!args.has("fps"))f=rates.first()
                require(f in rates){"$f fps unavailable at $q"}
            }
            val t=args.optInt("timer",timer);val a=if(m=="Photo")args.optString("aspect",aspect)else aspect
            if(m!="Photo"&&args.has("aspect"))require(args.getString("aspect")=="16:9"){"Video output is 16:9"}
            val size=args.optString("photoSize",photoSize);require(size in photoSizes()){"Photo size unavailable"}
            val shots=if(args.has("shotCount"))CameraPolicy.shots(args.get("shotCount"))else shotCount
            CameraPolicy.validate(m,q,f,t,a)
            if(m==mode&&q==quality&&f==fps&&t==timer&&a==aspect&&size==photoSize){shotCount=shots;show("Ready");done(result(true,"Settings applied"));return}
            configuring=true;shotCount=shots;photoSize=size;mode=m;quality=q;fps=f;timer=t;aspect=a;show("Applying settings…")
            bind{error->if(error==null){configuring=false;show("Ready");done(result(true,"Settings applied"))}else{
                mode=old[0];quality=old[1];fps=old[2].toInt();timer=old[3].toInt();aspect=old[4];photoSize=old[5];shotCount=old[6].toInt()
                bind{rollback->configuring=false;show(rollback?:"Previous settings restored");done(result(false,"$error. Previous settings restored."))}
            }}
        }catch(e:Exception){configuring=false;done(result(false,e.message?:"Invalid settings"))}
    }
    private fun toggleFlash(done:(JSONObject)->Unit){
        val available=if(mode=="Timelapse")lapse.flashAvailable else info()?.hasFlashUnit()==true
        if(!ready||!available){done(result(false,"Flash unavailable on selected camera"));return}
        if(mode=="Photo"){
            if(!idle()){done(result(false,"Wait for photo capture"));return}
            flash=when(flash){"OFF"->"AUTO";"AUTO"->"ON";else->"OFF"}
            imageCapture?.flashMode=when(flash){"ON"->ImageCapture.FLASH_MODE_ON;"AUTO"->ImageCapture.FLASH_MODE_AUTO;else->ImageCapture.FLASH_MODE_OFF}
            show("Flash $flash");done(result(true,"Flash $flash"))
        }else{
            val desired=!torch
            if(mode=="Timelapse"){
                val error=lapse.setTorch(desired)
                if(error==null){torch=desired;show("Torch ${if(torch)"on" else "off"}");done(result(true,"Torch applied"))}else done(result(false,error))
            }else{
                val f=camera?.cameraControl?.enableTorch(desired)?:return done(result(false,"Camera not ready"))
                f.addListener({try{f.get();torch=desired;show("Torch ${if(torch)"on" else "off"}");done(result(true,"Torch applied"))}catch(e:Exception){done(result(false,"Torch failed: ${e.message}"))}},main)
            }
        }
    }
    private val reviewDeletion=ReviewDeletionGuard()
    fun command(c:JSONObject,done:(JSONObject)->Unit){
        if(!paired&&c.optString("op")!="STOP"){done(result(false,"WATCH_REQUIRED: connect through Zepp first"));return}
        if(!foreground){done(result(false,"PHONE_INACTIVE: open the camera app"));return}
        try{when(c.getString("op")){
            "STATUS"->done(result(true,if(ready)"Camera ready" else "Camera loading / permission needed"))
            "CONFIG"->configure(c.optJSONObject("args")?:JSONObject(),done)
            "FLASH"->toggleFlash(done)
            "ZOOM"->zoomTo((c.optJSONObject("args")?.optDouble("ratio",Double.NaN)?:Double.NaN).toFloat(),done)
            "SWITCH"->{
                if(!idle())done(result(false,"BUSY: stop capture first"))else{
                    val oldFront=front;val oldQuality=quality;val oldFps=fps;val oldSize=photoSize;val oldFlash=flash
                    front=!front;zoomTarget=1f;flash="OFF";torch=false;photoSize="Auto"
                    try{
                        if(mode!="Photo"){
                            val options=qualities();require(options.isNotEmpty()){"$mode unavailable on this camera. Choose Photo or Video first."}
                            if(quality !in options)quality=options.first()
                            val rates=frameRates();require(rates.isNotEmpty()){"Frame rate unavailable on this camera"}
                            if(fps !in rates)fps=rates.first()
                        }
                        configuring=true
                        bind{e->if(e==null){configuring=false;done(result(true,"Camera switched"))}else{
                            front=oldFront;quality=oldQuality;fps=oldFps;photoSize=oldSize;flash=oldFlash
                            bind{configuring=false;done(result(false,e))}
                        }}
                    }catch(e:Exception){front=oldFront;quality=oldQuality;fps=oldFps;photoSize=oldSize;flash=oldFlash;done(result(false,e.message?:"Camera unavailable"))}
                }
            }
            "SHUTTER"->{if(!ready||!idle())done(result(false,"Camera busy or unavailable")) else {replyCapture=done
                if(timer==0)capture() else {countdownEndElapsed=SystemClock.elapsedRealtime()+timer*1000L;countdown=object:CountDownTimer(timer*1000L,1000){override fun onTick(ms:Long){countdownSeconds=((ms+999)/1000).toInt();show("Capture in ${countdownSeconds}s")};override fun onFinish(){countdown=null;countdownSeconds=0;countdownEndElapsed=0;capture()}}.start()}}}
            "STOP"->stopCapture(done)
            "DELETE_REVIEW"->{
                if(!idle()){done(result(false,"Stop capture first"));return}
                val target=reviewDeletion.consume(c.optJSONObject("args")?.optString("reviewToken","")?:"")
                if(target==null){done(result(false,"Open capture again before deleting"));return}
                val uri=android.net.Uri.parse(target)
                require(uri.scheme=="content"&&uri.authority=="media"&&contentResolver.getType(uri)?.let{it.startsWith("image/")||it.startsWith("video/")}==true){"Capture no longer available"}
                contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),null,null,null)?.use{cursor->require(cursor.moveToFirst()&&cursor.getString(0)==packageName){"Delete this capture in phone gallery"}}?:error("Capture no longer available")
                val count=contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,1)},"${MediaStore.MediaColumns.IS_TRASHED}=0",null)
                catalog.reconcile();lastUri=catalog.latest(true);latestMediaKey=catalog.latest()?.toString()?:"";mediaGeneration++
                done(result(count>0,if(count>0)"Capture moved to trash"else"Capture no longer available"))
            }
            "SNAPSHOT"->{
                if(saving||finalizing){done(result(false,"Wait for capture to finish"));return}
                renderer.snapshot{bitmap->if(bitmap==null)done(result(false,"Camera loading"))else sendBitmap(bitmap,done)}
            }
            "HISTORY"->{
                imageWorker.execute{try{val items=catalog.all(8);main.execute{
                    if(stopping){done(result(false,"Open phone app"));return@execute}
                    val handles=captureHistory.replace(items.map{it.uri.toString()})
                    done(result(true,"History ready").put("history",JSONArray(handles.map{entry->JSONObject().put("id",entry.id).put("kind",items.first{it.uri.toString()==entry.target}.kind)})))
                }}catch(_:Exception){main.execute{done(result(false,"Gallery unavailable; open phone gallery"))}}}
            }
            "REVIEW","THUMBNAIL"->{
                if(saving||finalizing){done(result(false,"Wait for capture to finish"));return}
                val thumbnail=c.getString("op")=="THUMBNAIL"
                val kind=if(thumbnail)"Latest"else c.optJSONObject("args")?.optString("kind","Photo")?:"Photo"
                require(kind in setOf("Latest","Photo","Video","Slow Motion","Timelapse")){"Unknown capture kind"}
                val historyId=c.optJSONObject("args")?.optString("historyId","")?:""
                val historyUri=if(historyId.isNotEmpty()){require(!thumbnail){"Invalid history request"};captureHistory.resolve(historyId)?.let(Uri::parse)?:error("Open gallery again")}else null
                imageWorker.execute{
                    try{
                        val item=if(historyUri!=null)catalog.capture(historyUri)else if(kind=="Latest")catalog.latest()?.let{catalog.capture(it)}else if(kind=="Photo")catalog.latest(true)?.let{catalog.capture(it)}else catalog.all().firstOrNull{it.kind==kind}
                        if(item==null){main.execute{done(result(false,"No capture yet"))};return@execute}
                        val bitmap=if(item.video){val retriever=android.media.MediaMetadataRetriever();try{retriever.setDataSource(this,item.uri);retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,320)}finally{retriever.release()}}
                        else ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,item.uri)){d,info,_->val scale=minOf(1f,640f/info.size.width,640f/info.size.height);d.setTargetSize(kotlin.math.round(info.size.width*scale).toInt().coerceAtLeast(1),kotlin.math.round(info.size.height*scale).toInt().coerceAtLeast(1));d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
                        main.execute{
                            if(stopping||bitmap==null){bitmap?.recycle();done(result(false,"Preview unavailable"));return@execute}
                            if(!catalog.exists(item.uri)){bitmap.recycle();done(result(false,"Capture no longer available"));return@execute}
                            sendBitmap(bitmap,{reply->
                                if(reply.optBoolean("ok")){
                                    reply.put("previewKind",item.kind).put("mediaKey",item.uri.toString())
                                    if(!thumbnail)reply.put("reviewToken",reviewDeletion.issue(item.uri.toString()))
                                };done(reply)
                            },thumbnail,!thumbnail&&c.optJSONObject("args")?.optBoolean("fullScreen",false)==true)
                        }
                    }catch(_:Exception){main.execute{done(result(false,"Preview unavailable; open phone gallery"))}}
                }
            }
            else->done(result(false,"Unsupported command"))
        }}catch(e:Exception){done(result(false,e.message?:"Camera error"))}
    }
    private fun sendBitmap(bitmap:Bitmap,done:(JSONObject)->Unit,thumbnail:Boolean=false,fullReview:Boolean=false){
        fun encode(width:Int,height:Int):ByteArray{
            val canvasBitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(canvasBitmap);canvas.drawColor(android.graphics.Color.BLACK)
            if(thumbnail){val clip=android.graphics.Path().apply{addRoundRect(android.graphics.RectF(0f,0f,width.toFloat(),height.toFloat()),width*.18f,height*.18f,android.graphics.Path.Direction.CW)};canvas.clipPath(clip)}
            val box=ImageFit.contain(bitmap.width,bitmap.height,width,height)
            canvas.drawBitmap(bitmap,null,android.graphics.RectF(box.left,box.top,box.right,box.bottom),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return ByteArrayOutputStream().apply{canvasBitmap.compress(Bitmap.CompressFormat.PNG,100,this);canvasBitmap.recycle()}.toByteArray()
        }
        val limit=if(fullReview)262144 else 65536
        var png=if(thumbnail)encode(72,72)else if(fullReview)encode(390,450)else encode(320,200)
        if(png.size>limit-4096)png=if(thumbnail)encode(72,72)else if(fullReview)encode(312,360)else encode(160,100)
        if(fullReview&&png.size>limit)png=encode(195,225)
        bitmap.recycle()
        if(png.size>limit){done(result(false,"Frame too large; try again"));return}
        done(result(true,"Image ready").put("mediaPath",bridge.storeImage(png)).put("bytes",png.size))
    }
    private fun capture(){
        if(!foreground||!ready){finishCapture(false,"Camera no longer active");return}
        if(mode=="Timelapse"){
            val f=File(cacheDir,"WristSnap-Timelapse-${System.currentTimeMillis()}.mp4");lapseFile=f
            saving=true
            lapse.start(f){e->saving=false;if(e==null){startedAt=System.currentTimeMillis();show("Timelapse recording • 1 fps → 30 fps");handler.removeCallbacks(lapseClock);handler.post(lapseClock);finishCapture(true,"Timelapse started")}else finishCapture(false,e)};return
        }
        if(mode!="Photo"){startVideo();return}
        shotsTaken=0;shotsTarget=shotCount;saving=true
        handler.postDelayed(photoDeadline,18000)
        takeNextPhoto()
    }
    private fun cancelPhotos(message:String){
        if(shotsTarget==0)return
        nextPhoto?.let{handler.removeCallbacks(it)};nextPhoto=null;handler.removeCallbacks(photoDeadline)
        val target=shotsTarget;shotsTarget=0;saving=photoInFlight
        finishCapture(false,"$message ($shotsTaken/$target saved)")
    }
    private fun takeNextPhoto(){
        nextPhoto=null
        if(stopping||!foreground||!ready){cancelPhotos("Capture stopped");return}
        if(shotsTarget==0)return
        photoInFlight=true;saving=true;show(if(shotsTarget>1)"${shotsTaken+1}/$shotsTarget"else"Capturing…")
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"WristSnap-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/WristSnap")}
        val output=ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build()
        fun failed(message:String){photoInFlight=false;saving=false;if(shotsTarget>0)cancelPhotos(message)else publish()}
        try{imageCapture!!.takePicture(output,main,object:ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(o:ImageCapture.OutputFileResults){
                photoInFlight=false;lastUri=o.savedUri;getSharedPreferences("media",MODE_PRIVATE).edit().putString("lastPhoto",lastUri?.toString()).apply();lastUri?.let{rememberCapture(it,"Photo")}
                if(shotsTarget==0){saving=false;publish();return}
                if(o.savedUri==null){failed("Photo save not confirmed");return}
                shotsTaken++
                if(stopping){cancelPhotos("Capture stopped");return}
                if(shotsTaken>=shotsTarget){val total=shotsTarget;shotsTarget=0;saving=false;handler.removeCallbacks(photoDeadline);finishCapture(true,if(total==1)"Photo saved"else"$total photos saved")}
                else{show("$shotsTaken/$shotsTarget");nextPhoto=Runnable{takeNextPhoto()}.also{handler.postDelayed(it,1000)}}
            }
            override fun onError(e:ImageCaptureException){failed(e.message?:"Photo failed")}
        })}catch(e:Exception){failed(e.message?:"Photo failed")}
    }
    private fun finishCapture(ok:Boolean,message:String){val r=replyCapture;replyCapture=null;show(message);r?.invoke(result(ok,message))}
    private fun startVideo(){
        val capturedMode=mode
        try{
            val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"WristSnap-${if(capturedMode=="Slow Motion")"SlowMotion"else "Video"}-${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/WristSnap")}
            val out=MediaStoreOutputOptions.Builder(contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
            var pending=videoCapture!!.output.prepareRecording(this,out)
            if(mode=="Video"&&audioWanted&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)pending=pending.withAudioEnabled()
            saving=true
            recording=pending.start(main){e->when(e){
                is VideoRecordEvent.Start->{saving=false;startedAt=System.currentTimeMillis();finishCapture(true,"Recording started")}
                is VideoRecordEvent.Status->{val s=((System.currentTimeMillis()-startedAt)/1000).coerceAtLeast(0);show("● REC  %02d:%02d".format(s/60,s%60))}
                is VideoRecordEvent.Finalize->{recording=null;saving=false;finalizing=false;val ok=!e.hasError();if(ok&&e.outputResults.outputUri!=Uri.EMPTY)rememberCapture(e.outputResults.outputUri,capturedMode);val msg=if(ok)"Video saved" else "Video failed: ${e.error}";finishCapture(ok,msg);replyStop?.invoke(result(ok,msg));replyStop=null;show(msg)}
            }}
        }catch(e:Exception){recording=null;saving=false;finishCapture(false,e.message?:"Video failed")}
    }
    private fun stopCapture(done:(JSONObject)->Unit){
        if(shotsTarget>0){cancelPhotos("Capture stopped");done(result(true,"Photo sequence stopped"));return}
        if(countdown!=null){countdown?.cancel();countdown=null;countdownSeconds=0;countdownEndElapsed=0;finishCapture(false,"Countdown canceled");done(result(true,"Countdown canceled"));return}
        if(lapse.running){
            val e=lapse.stop();val f=lapseFile;lapseFile=null
            if(e!=null||f==null){f?.delete();bind{};done(result(false,"Timelapse too short or failed: $e"));return}
            try{val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,f.name);put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/WristSnap");put(MediaStore.Video.Media.IS_PENDING,1)}
                val uri=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:error("MediaStore insert failed")
                try{contentResolver.openOutputStream(uri)!!.use{o->f.inputStream().use{it.copyTo(o)}};contentResolver.update(uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)}catch(t:Exception){contentResolver.delete(uri,null,null);throw t}
                rememberCapture(uri,"Timelapse");f.delete();bind{};done(result(true,"Timelapse saved • silent 30 fps MP4"))
            }catch(x:Exception){bind{};done(result(false,"Save failed: ${x.message}"))};return
        }
        if(recording!=null&&!finalizing){finalizing=true;replyStop=done;recording!!.stop();show("Saving video…")}else done(result(false,"No active recording"))
    }
}
