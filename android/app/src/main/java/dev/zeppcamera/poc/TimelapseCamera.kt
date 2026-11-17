package dev.zeppcamera.poc

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import java.io.File

/** Camera2 + MediaRecorder capture-rate timelapse: 1 sensor frame/second encoded at 30 fps. */
class TimelapseCamera(private val context:Context,private val renderer:CameraPreviewRenderer) {
    private val handler=Handler(Looper.getMainLooper())
    private var device:CameraDevice?=null
    private var session:CameraCaptureSession?=null
    private var recorder:MediaRecorder?=null
    private var preview:Surface?=null
    private var frameBusy=false
    private var generation=0
    var flashAvailable=false;private set
    private var torchEnabled=false
    private var targets:List<Surface> = emptyList()
    private var orientation=90
    private var width=1280;private var height=720
    var previewReady=false;private set
    var running=false;private set
    var minZoom=1f;private set
    var maxZoom=1f;private set
    var zoomRatio=1f;private set
    private var activeArray=android.graphics.Rect(0,0,1,1)
    private var afRegions=0;private var aeRegions=0;private var afAuto=false;private var defaultAf=CaptureRequest.CONTROL_AF_MODE_OFF
    private var distortionMode:Int?=null;private var metering:android.hardware.camera2.params.MeteringRectangle?=null
    private var focusSerial=0;private var focusStartFrame=Long.MAX_VALUE;private var focusReply:((Boolean)->Unit)?=null
    private var exposureReply:((String?)->Unit)?=null;private var desiredExposure=0
    var exposureIndex=0;private set
    var exposureMin=0;private set
    var exposureMax=0;private set
    var exposureStep=0f;private set
    val focusAvailable get()=afRegions>0&&afAuto||aeRegions>0
    val exposureAvailable get()=exposureMax>exposureMin&&exposureStep>0
    private val focusTimeout=Runnable{focusReply?.invoke(false);focusReply=null}
    private val clearFocus=Runnable{metering=null;focusReply?.invoke(false);focusReply=null;focusSerial++;setTorch(torchEnabled,false)}
    private val exposureTimeout=Runnable{exposureReply?.invoke("Exposure not ready");exposureReply=null}
    private val meteringCallback=object:CameraCaptureSession.CaptureCallback(){override fun onCaptureCompleted(s:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult){
        if(request.tag!=focusSerial)return
        result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)?.let{exposureIndex=it;if(it==desiredExposure){handler.removeCallbacks(exposureTimeout);exposureReply?.invoke(null);exposureReply=null}}
        if(request.get(CaptureRequest.CONTROL_AF_TRIGGER)==CaptureRequest.CONTROL_AF_TRIGGER_START)focusStartFrame=result.frameNumber
        val af=result.get(CaptureResult.CONTROL_AF_STATE)
        if(focusReply!=null&&(!(afAuto&&afRegions>0)||result.frameNumber>=focusStartFrame&&(af==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED||af==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED))){
            val success=!(afAuto&&afRegions>0)||af==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;handler.removeCallbacks(focusTimeout);val reply=focusReply;focusReply=null;reply?.invoke(success)
        }
    }}
    private fun controls(request:CaptureRequest.Builder){
        request.setTag(focusSerial);request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
        request.set(CaptureRequest.CONTROL_ZOOM_RATIO,zoomRatio);request.set(CaptureRequest.FLASH_MODE,if(torchEnabled)CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,desiredExposure)
        distortionMode?.let{request.set(CaptureRequest.DISTORTION_CORRECTION_MODE,it)}
        request.set(CaptureRequest.CONTROL_AF_MODE,if(metering!=null&&afAuto)CaptureRequest.CONTROL_AF_MODE_AUTO else defaultAf)
        metering?.let{region->if(afRegions>0)request.set(CaptureRequest.CONTROL_AF_REGIONS,arrayOf(region));if(aeRegions>0)request.set(CaptureRequest.CONTROL_AE_REGIONS,arrayOf(region))}
    }
    fun focusAt(displayX:Float,displayY:Float,displayDegrees:Int,front:Boolean,done:(Boolean)->Unit){
        if(!focusAvailable||device==null||session==null){done(false);return}
        try{
            val rotation=if(front)(orientation+displayDegrees)%360 else (orientation-displayDegrees+360)%360
            val raw=PreviewMetering.sensor(PreviewMetering.Point(displayX,displayY),rotation,front)
            val sensor=PreviewMetering.uncrop(raw.x,raw.y,activeArray.width().toFloat()/activeArray.height(),width.toFloat()/height)
            val rw=(activeArray.width()*.12f).toInt().coerceAtLeast(1);val rh=(activeArray.height()*.12f).toInt().coerceAtLeast(1)
            val left=(activeArray.left+sensor.x*activeArray.width()-rw/2).toInt().coerceIn(activeArray.left,activeArray.right-rw)
            val top=(activeArray.top+sensor.y*activeArray.height()-rh/2).toInt().coerceIn(activeArray.top,activeArray.bottom-rh)
            handler.removeCallbacks(clearFocus);handler.removeCallbacks(focusTimeout);focusReply?.invoke(false);focusSerial++;focusStartFrame=Long.MAX_VALUE
            metering=android.hardware.camera2.params.MeteringRectangle(android.graphics.Rect(left,top,left+rw,top+rh),1000);focusReply=done
            val error=setTorch(torchEnabled,false);if(error!=null){focusReply=null;done(false);return}
            if(afAuto&&afRegions>0){
                for(trigger in listOf(CaptureRequest.CONTROL_AF_TRIGGER_CANCEL,CaptureRequest.CONTROL_AF_TRIGGER_START)){
                    val request=device!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);preview?.let{request.addTarget(it)};controls(request);request.set(CaptureRequest.CONTROL_AF_TRIGGER,trigger);session!!.capture(request.build(),meteringCallback,handler)
                }
            }
            handler.postDelayed(focusTimeout,2500);handler.postDelayed(clearFocus,3000)
        }catch(_:Exception){focusReply=null;done(false)}
    }
    fun setExposure(index:Int,done:(String?)->Unit){
        if(!exposureAvailable||index !in exposureMin..exposureMax){done("Exposure unavailable");return}
        exposureReply?.invoke("Exposure superseded");desiredExposure=index;exposureReply=done
        val error=setTorch(torchEnabled,false);if(error!=null){exposureReply=null;done(error);return}
        handler.removeCallbacks(exposureTimeout);handler.postDelayed(exposureTimeout,1800)
    }
    @SuppressLint("MissingPermission")
    fun open(front:Boolean,quality:String,complete:(String?)->Unit) {
        var completed=false
        val done:(String?)->Unit={error->if(!completed){completed=true;complete(error)}}
        close();val g=generation
        try{
            val manager=context.getSystemService(CameraManager::class.java)
            val facing=if(front)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id=manager.cameraIdList.first{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==facing}
            val characteristics=manager.getCameraCharacteristics(id)
            val distortion=characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)?:intArrayOf()
            distortionMode=when{distortion.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_FAST)->CaptureRequest.DISTORTION_CORRECTION_MODE_FAST;distortion.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY)->CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY;else->null}
            activeArray=(if(distortionMode==null)characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)else characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))?:characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?:android.graphics.Rect(0,0,1,1)
            afRegions=characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)?:0;aeRegions=characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)?:0
            val af=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?:intArrayOf();afAuto=af.contains(CaptureRequest.CONTROL_AF_MODE_AUTO);defaultAf=when{af.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)->CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;af.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)->CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;afAuto->CaptureRequest.CONTROL_AF_MODE_AUTO;else->CaptureRequest.CONTROL_AF_MODE_OFF}
            val ev=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);exposureMin=ev?.lower?:0;exposureMax=ev?.upper?:0;exposureStep=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat()?:0f;desiredExposure=0;exposureIndex=0
            val range=characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            minZoom=range?.lower?:1f;maxZoom=range?.upper?:1f;zoomRatio=zoomRatio.coerceIn(minZoom,maxZoom)
            flashAvailable=manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true
            orientation=manager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION)?:90
            width=if(quality=="FHD")1920 else 1280;height=if(quality=="FHD")1080 else 720
            val sizes=manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(MediaRecorder::class.java)
            check(sizes.any{it.width==width&&it.height==height}){"Timelapse resolution unsupported; select HD"}
            renderer.orientation(orientation,front)
            renderer.input(width,height){surface->
            if(g!=generation){surface.release();return@input};preview=surface
            try{manager.openCamera(id,object:CameraDevice.StateCallback(){
                override fun onOpened(camera:CameraDevice){if(g!=generation){camera.close();return};device=camera;configure(listOf(preview!!),false,done)}
                override fun onDisconnected(camera:CameraDevice){camera.close();if(g==generation){device=null;done("Camera disconnected")}}
                override fun onError(camera:CameraDevice,error:Int){camera.close();if(g==generation){device=null;done("Camera2 error $error")}}
            },handler)}catch(e:Exception){done(e.message?:"Timelapse unavailable")}
            }
        }catch(e:Exception){done(e.message?:"Timelapse unavailable")}
    }
    private fun configure(surfaces:List<Surface>,record:Boolean,done:(String?)->Unit){
        val d=device?:return done("Camera not ready")
        targets=surfaces
        val g=generation
        try {d.createCaptureSession(surfaces,object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(s:CameraCaptureSession){
                if(g!=generation){s.close();return};session=s
                try{val request=d.createCaptureRequest(if(record)CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW)
                    (if(record)listOf(preview!!)else surfaces).forEach{request.addTarget(it)}
                    controls(request)
                    s.setRepeatingRequest(request.build(),object:CameraCaptureSession.CaptureCallback(){override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult){if(g==generation){previewReady=true;meteringCallback.onCaptureCompleted(session,request,result)}}},handler)
                    if(record){recorder!!.start();running=true;handler.removeCallbacks(framePump);handler.post(framePump)};done(null)
                }catch(e:Exception){done(e.message?:"Session failed")}
            }
            override fun onConfigureFailed(s:CameraCaptureSession){done("Timelapse session unsupported")}
        },handler)}catch(e:Exception){done(e.message?:"Camera2 session failed")}
    }
    fun start(file:File,done:(String?)->Unit){
        try{
            check(device!=null&&!running){"Timelapse camera not ready"}
            session?.close()
            recorder=MediaRecorder(context).apply{
                setVideoSource(MediaRecorder.VideoSource.SURFACE);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264);setVideoSize(width,height);setVideoFrameRate(30)
                setVideoEncodingBitRate(8_000_000);setCaptureRate(1.0);setOrientationHint(orientation)
                setOutputFile(file.absolutePath);prepare()
            }
            configure(listOf(preview!!,recorder!!.surface),true){error->
                if(error!=null){recorder?.release();recorder=null;session?.close();session=null;configure(listOf(preview!!),false){done(error)}}else done(null)
            }
        }catch(e:Exception){recorder?.release();recorder=null;done(e.message?:"Timelapse failed")}
    }
    private val framePump=object:Runnable{
        override fun run(){
            if(!running)return
            if(!frameBusy){
                try{val d=device?:return;val s=session?:return;val surface=recorder?.surface?:return
                    val request=d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply{
                        addTarget(surface)
                        preview?.let{addTarget(it)}
                        controls(this)
                    }
                    frameBusy=true
                    s.capture(request.build(),object:CameraCaptureSession.CaptureCallback(){
                        override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult){frameBusy=false;meteringCallback.onCaptureCompleted(session,request,result)}
                        override fun onCaptureFailed(session:CameraCaptureSession,request:CaptureRequest,failure:CaptureFailure){frameBusy=false}
                    },handler)
                }catch(_:Exception){frameBusy=false}
            }
            handler.postDelayed(this,1000)
        }
    }
    fun setZoom(value:Float):String?{
        if(!value.isFinite()||value !in minZoom..maxZoom)return "Zoom unavailable"
        val previous=zoomRatio;zoomRatio=value
        val error=setTorch(torchEnabled,requireFlash=false)
        if(error!=null)zoomRatio=previous
        return error
    }
    fun setTorch(enabled:Boolean,requireFlash:Boolean=true):String? {
        if(requireFlash&&!flashAvailable)return "Torch unavailable on this camera"
        return try{val d=device?:return "Camera not ready";val current=session?:return "Camera session not ready"
            val request=d.createCaptureRequest(if(running)CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW)
            preview?.let{request.addTarget(it)};controls(request);request.set(CaptureRequest.FLASH_MODE,if(enabled)CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            current.setRepeatingRequest(request.build(),meteringCallback,handler);torchEnabled=enabled;null
        }catch(e:Exception){e.message?:"Torch failed"}
    }
    fun stop():String? {
        handler.removeCallbacks(framePump);frameBusy=false
        var error:String?=null
        try{session?.stopRepeating();session?.abortCaptures();if(running)recorder?.stop()}catch(e:Exception){error=e.message?:"Recording too short"}
        running=false;recorder?.release();recorder=null;session?.close();session=null
        return error
    }
    fun close(){handler.removeCallbacks(focusTimeout);handler.removeCallbacks(clearFocus);handler.removeCallbacks(exposureTimeout);focusReply?.invoke(false);focusReply=null;exposureReply?.invoke("Camera closed");exposureReply=null;metering=null;focusSerial++;generation++;previewReady=false;torchEnabled=false;flashAvailable=false;targets=emptyList();stop();device?.close();device=null;preview?.release();preview=null}
}
