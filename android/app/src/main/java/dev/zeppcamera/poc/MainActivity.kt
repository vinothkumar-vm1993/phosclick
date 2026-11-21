package dev.zeppcamera.poc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity:AppCompatActivity() {
    private lateinit var zoomButton:com.google.android.material.button.MaterialButton
    private lateinit var zoomSlider:com.google.android.material.slider.Slider
    private var pinchStarts=0
    private var pinchSamples=0
    private var zoomInteracting=false
    private var uiZoom=1f
    private lateinit var focusOverlay:FocusExposureOverlay
    private var focusUiGeneration=0
    private var focusCameraGeneration=-1
    private var resourceLabel=""
    private var exposurePending:Int?=null
    private var exposurePosting=false
    private lateinit var preview:TextureView
    private lateinit var relayStatus:TextView
    private lateinit var title:TextView
    private lateinit var shutter:ImageButton
    private lateinit var flashButton:ImageButton
    private lateinit var switchButton:ImageButton
    private lateinit var remoteButton:ImageButton
    private lateinit var parameter:TextView
    private lateinit var lockedHint:TextView
    private lateinit var settingsButton:ImageButton
    private var paired=false
    private val modeButtons=linkedMapOf<String,Button>()
    private var flash="OFF"
    private var torch=false
    private lateinit var lastImage:ImageView
    private lateinit var mediaBadge:ImageView
    private var front=false
    private var mode="Photo";private var quality="HD";private var fps=30;private var timer=0;private var aspect="4:3"
    private var photoSize="Auto"
    private var configuring=false
    private var ready=false;private var saving=false;private var finalizing=false;private var foreground=false
    private val handler=Handler(Looper.getMainLooper())
    private var guideShowing=false
    private var permissionRequested=false
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(has(Manifest.permission.CAMERA))ensureSession()else show("Allow camera permission to connect")
    }
    private fun has(p:String)=ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED
    private var lastConnectionTint:Boolean?=null
    private var lastUiSignature:String?=null
    private var uiRenderPasses=0
    private fun show(s:String){
        val seconds=((System.currentTimeMillis()-cachedState.optLong("startedAt",System.currentTimeMillis()))/1000).coerceAtLeast(0)
        val titleText=if(cachedState.optInt("shotsTarget")>1)"Photo · ${cachedState.optInt("shotsTaken")}/${cachedState.optInt("shotsTarget")}"else if(isRecording())"$mode · %02d:%02d".format(seconds/60,seconds%60)else if(cachedState.optBoolean("countdown"))"$mode · ${cachedState.optInt("countdownSeconds")}s"else mode
        if(title.text.toString()!=titleText)title.text=titleText
        updateResourceWarning();updateZoomUI()
        val signature=listOf(mode,front,ready,paired,configuring,saving,finalizing,isRecording(),timer,aspect,fps,quality,flash,torch,cachedState.optBoolean("countdown"),cachedState.optString("photoSizeLabel"),cachedState.optBoolean("flashAvailable")).toString()
        if(signature==lastUiSignature)return
        lastUiSignature=signature;uiRenderPasses++
        title.textSize=if(isRecording())18f else 22f;title.setTextColor(if(isRecording())0xffff4c4c.toInt()else Color.WHITE);lockedHint.visibility=View.GONE
        val enabled=paired
        val canConfigure=paired&&ready&&!configuring&&!saving&&!finalizing&&!isRecording()
        shutter.isEnabled=(enabled&&ready&&!saving&&!finalizing&&!configuring)||isRecording()||cachedState.optBoolean("countdown");shutter.alpha=if(shutter.isEnabled)1f else .25f
        switchButton.isEnabled=canConfigure;switchButton.alpha=if(canConfigure)1f else .25f
        flashButton.isEnabled=enabled&&ready&&!configuring&&!saving&&!finalizing&&cachedState.optBoolean("flashAvailable");flashButton.alpha=if(flashButton.isEnabled)1f else .25f
        settingsButton.isEnabled=canConfigure;settingsButton.alpha=if(canConfigure)1f else .25f
        parameter.alpha=if(enabled)1f else .25f
        modeButtons.values.forEach{it.isEnabled=canConfigure;it.alpha=if(canConfigure)1f else .25f}
        preview.alpha=if(enabled)1f else .2f
        title.contentDescription="$mode, ${if(front)"Front" else "Rear"} camera"
        parameter.text=if(mode=="Photo")"${cachedState.optString("photoSizeLabel","Auto")}  ·  $aspect" else "${when(quality){"HD"->"720p";"FHD"->"1080p";else->"4K"}}  ·  $fps fps"
        if(timer>0)parameter.append("  ·  ${timer}s")
        shutter.setImageResource(if(isRecording())R.drawable.ic_action_stop else if(mode=="Photo")R.drawable.ic_action_shutter else R.drawable.ic_action_record)
        shutter.contentDescription=if(isRecording())"Stop recording" else if(cachedState.optBoolean("countdown"))"Cancel countdown" else "Capture"
        switchButton.contentDescription="Switch camera. ${if(front)"Front" else "Rear"} selected"
        val lit=if(mode=="Photo")flash!="OFF" else torch
        flashButton.setImageResource(if(mode=="Photo"&&flash=="AUTO")R.drawable.ic_action_flash_auto else if(lit)R.drawable.ic_action_flash else R.drawable.ic_action_flash_off)
        flashButton.contentDescription=if(mode=="Photo")"Flash $flash" else "Torch ${if(torch)"ON" else "OFF"}";flashButton.tooltipText=flashButton.contentDescription
        flashButton.imageTintList=android.content.res.ColorStateList.valueOf(if(lit)0xffffd65a.toInt() else Color.WHITE)
        modeButtons.forEach{(value,button)->button.setTextColor(if(value==mode)0xffffd65a.toInt() else 0xffa1a9b4.toInt())}
        updateResourceWarning()
    }
    private fun updateZoomUI(){
        val enabled=paired
        if(::zoomButton.isInitialized){
            val min=cachedState.optDouble("minZoom",1.0).toFloat();val max=cachedState.optDouble("maxZoom",1.0).toFloat()
            zoomButton.isEnabled=enabled&&ready&&max>min;zoomButton.alpha=if(zoomButton.isEnabled)1f else .3f
            if(!zoomInteracting&&!cachedState.optBoolean("zooming")){uiZoom=cachedState.optDouble("zoomRatio",1.0).toFloat();val label=zoomLabel(uiZoom);if(zoomButton.text.toString()!=label)zoomButton.text=label
                if(max>min){if(zoomSlider.valueFrom!=min||zoomSlider.valueTo!=max){zoomSlider.valueFrom=min;zoomSlider.valueTo=max};val desired=uiZoom.coerceIn(min,max);if(kotlin.math.abs(zoomSlider.value-desired)>.001f)zoomSlider.value=desired}}
            zoomSlider.isEnabled=zoomButton.isEnabled
        }
    }
    private fun updateResourceWarning(){
        val warning=if(paired)ResourceWarning.label(cachedState.optString("resourceWarning"))else""
        val text=when{
            cachedState.has("sessionActive")&&!cachedState.optBoolean("sessionActive")->"Disconnected"
            paired&&!cachedState.optBoolean("connectionLive")->"Watch disconnected"
            warning.isNotEmpty()->warning
            cachedState.optBoolean("connectionLive")->"Watch connected"
            else->"Waiting for watch"
        }
        if(relayStatus.text.toString()!=text)relayStatus.text=text
        if(resourceLabel!=warning){relayStatus.setTextColor(if(warning.isNotEmpty())0xffffcf69.toInt()else 0xffa1a9b4.toInt());relayStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(if(warning.isNotEmpty())R.drawable.ic_action_warning else 0,0,0,0);relayStatus.compoundDrawablePadding=dp(6);resourceLabel=warning}
    }
    private fun focusAt(x:Float,y:Float){
        val controller=session?:return
        if(!paired||!ready||configuring||preview.width<=0||preview.height<=0)return
        zoomSlider.visibility=View.GONE;val generation=++focusUiGeneration;focusCameraGeneration=cachedState.optInt("cameraGeneration")
        focusOverlay.showAt(x,y,cachedState.optInt("exposureMin"),cachedState.optInt("exposureMax"),cachedState.optInt("exposureIndex"),cachedState.optDouble("exposureStep",0.0).toFloat())
        controller.focusAt(preview.display,x/preview.width,y/preview.height,preview.width,preview.height){ok->if(foreground&&generation==focusUiGeneration)focusOverlay.focusResult(ok)}
    }
    private fun exposureTo(index:Int){
        exposurePending=index;if(exposurePosting)return;exposurePosting=true
        handler.postDelayed({exposurePosting=false;val wanted=exposurePending;exposurePending=null;if(foreground&&wanted!=null)session?.setExposure(wanted){}},40)
    }
    private fun isRecording()=cachedState.optBoolean("recording")
    private fun idle()=!isRecording()&&!cachedState.optBoolean("busy")
    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun round(color:Int,radius:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun icon(resource:Int,label:String,action:()->Unit):ImageButton=ImageButton(this).apply{
        setImageResource(resource);contentDescription=label;tooltipText=label;scaleType=ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(13),dp(13),dp(13),dp(13));background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33555555),round(0xff171a1e.toInt(),28),null)
        setOnClickListener{action()}
        setOnTouchListener{v,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->v.animate().scaleX(.94f).scaleY(.94f).setDuration(80).start();MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()};false}
    }
    private fun local(op:String,args:JSONObject=JSONObject()){command(JSONObject().put("op",op).put("args",args)){show(it.optString("error",it.optString("message")))}}
    private val catalog by lazy{CaptureCatalog(applicationContext)}
    private val mediaWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var displayedThumbnailKey:String?=null
    private var thumbnailGeneration=0L
    private val refreshMedia=Runnable{refreshThumbnail()}
    private val mediaObserver=object:android.database.ContentObserver(handler){override fun onChange(selfChange:Boolean){
        if(foreground){handler.removeCallbacks(refreshMedia);handler.postDelayed(refreshMedia,120)}
    }}
    private fun clearThumbnail(){
        thumbnailGeneration++;displayedThumbnailKey=null
        if(!::lastImage.isInitialized)return
        lastImage.setImageResource(R.drawable.ic_action_gallery);lastImage.setPadding(dp(12),dp(12),dp(12),dp(12));mediaBadge.visibility=View.GONE
    }
    private fun rememberCapture(uri:Uri,kind:String){catalog.record(uri,kind);refreshThumbnail()}
    private fun refreshThumbnail(){
        val generation=++thumbnailGeneration
        if(isDestroyed||mediaWorker.isShutdown)return
        try{mediaWorker.execute{
            try{
                val (uri,_)=catalog.reconcile()
                val capture=uri?.let{catalog.capture(it)}
                val key=capture?.let{"${it.uri}:${it.modified}"}
                if(key!=null&&key==displayedThumbnailKey)return@execute
                val bitmap=uri?.let{catalog.thumbnail(it)}
                val movie=capture?.video?:false
                // A concurrent delete/trash may invalidate a thumbnail that MediaStore cached.
                val valid=uri!=null&&catalog.exists(uri)
                runOnUiThread{if(!isDestroyed&&foreground&&generation==thumbnailGeneration){
                    if(valid&&bitmap!=null){displayedThumbnailKey=key;lastImage.setPadding(0,0,0,0);lastImage.setImageBitmap(bitmap);mediaBadge.visibility=if(movie)View.VISIBLE else View.GONE}else clearThumbnail()
                }else bitmap?.recycle()}
            }catch(_:Exception){runOnUiThread{if(foreground&&generation==thumbnailGeneration)clearThumbnail()}}
        }}catch(_:java.util.concurrent.RejectedExecutionException){}
    }
    private fun zoomLabel(value:Float)=String.format(java.util.Locale.US,"%.1f×",value)
    private fun setZoom(value:Float){
        val actual=session?.state()?:cachedState;val min=actual.optDouble("minZoom",1.0).toFloat();val max=actual.optDouble("maxZoom",1.0).toFloat();uiZoom=value.coerceIn(min,max);zoomButton.text=zoomLabel(uiZoom)
        session?.setZoomTarget(uiZoom)?.let{show(it)}
    }
    private fun reviewCaptures(){
        if(!paired){connectionDialog();return}
        if(!idle()){show("Stop recording or wait before opening captures");return}
        startActivity(android.content.Intent(this,CapturesActivity::class.java),android.app.ActivityOptions.makeCustomAnimation(this,R.anim.gallery_enter,R.anim.camera_exit).toBundle())
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){
            if(::zoomSlider.isInitialized&&zoomSlider.visibility==View.VISIBLE){zoomSlider.visibility=View.GONE;return}
            session?.disconnect()?:run{startService(android.content.Intent(this@MainActivity,CameraSessionService::class.java).setAction("STOP"))}
            releaseSessionUI();finish()
        }})
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK)}
        root.setOnApplyWindowInsetsListener{v,i->val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        val header=FrameLayout(this)
        remoteButton=icon(R.drawable.ic_action_link,"Watch connection"){connectionDialog()}
        header.addView(remoteButton,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.START or Gravity.CENTER_VERTICAL).apply{marginStart=dp(16)})
        title=TextView(this).apply{textSize=22f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD}
        header.addView(title,FrameLayout.LayoutParams(dp(210),-1,Gravity.CENTER))
        settingsButton=icon(R.drawable.ic_action_settings,"Capture settings"){openSettings()}
        header.addView(settingsButton,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.END or Gravity.CENTER_VERTICAL).apply{marginEnd=dp(16)})
        root.addView(header,LinearLayout.LayoutParams(-1,dp(68)))
        relayStatus=TextView(this).apply{text="Watch disconnected";textSize=13f;setTextColor(0xffa1a9b4.toInt());gravity=Gravity.CENTER}
        root.addView(relayStatus,LinearLayout.LayoutParams(-1,dp(25)))
        val frame=FrameLayout(this).apply{setBackgroundColor(0xff07090b.toInt())}
        preview=TextureView(this)
        frame.addView(preview,FrameLayout.LayoutParams(-1,-1))
        focusOverlay=FocusExposureOverlay(this).apply{onExposure={exposureTo(it)}};frame.addView(focusOverlay,FrameLayout.LayoutParams(-1,-1))
        lockedHint=TextView(this).apply{text="Waiting for watch";textSize=20f;setTextColor(0xffa1a9b4.toInt());gravity=Gravity.CENTER}
        frame.addView(lockedHint,FrameLayout.LayoutParams(-1,dp(80),Gravity.CENTER))
        parameter=TextView(this).apply{textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);background=round(0xaa101317.toInt(),20);contentDescription="Resolution, frame rate and timer"}
        frame.addView(parameter,FrameLayout.LayoutParams(dp(180),dp(44),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(14)})
        flashButton=icon(R.drawable.ic_action_flash_off,"Flash off"){local("FLASH")}.apply{setPadding(dp(10),dp(10),dp(10),dp(10))}
        frame.addView(flashButton,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.START).apply{topMargin=dp(12);marginStart=dp(12)})
        val zoomControls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(12),0,dp(12),dp(8))}
        zoomButton=com.google.android.material.button.MaterialButton(this).apply{text="1×";isAllCaps=false;textSize=16f;cornerRadius=dp(24);contentDescription="Zoom controls";setOnClickListener{focusOverlay.hideNow();android.transition.TransitionManager.beginDelayedTransition(frame,android.transition.AutoTransition().setDuration(160));zoomSlider.visibility=if(zoomSlider.visibility==View.VISIBLE)View.GONE else View.VISIBLE};setOnLongClickListener{setZoom(1f);true}}
        zoomSlider=com.google.android.material.slider.Slider(this).apply{valueFrom=1f;valueTo=2f;value=1f;visibility=View.GONE;contentDescription="Camera zoom";setLabelFormatter{zoomLabel(it)};addOnChangeListener{_,value,user->if(user)setZoom(value)};addOnSliderTouchListener(object:com.google.android.material.slider.Slider.OnSliderTouchListener{override fun onStartTrackingTouch(slider:com.google.android.material.slider.Slider){zoomInteracting=true};override fun onStopTrackingTouch(slider:com.google.android.material.slider.Slider){zoomInteracting=false}})}
        zoomControls.addView(zoomButton,LinearLayout.LayoutParams(dp(80),dp(48)));zoomControls.addView(zoomSlider,LinearLayout.LayoutParams(dp(256),dp(48)))
        frame.addView(zoomControls,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        val pinch=ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScaleBegin(detector:ScaleGestureDetector):Boolean{val actual=session?.state()?:return false;if(!actual.optBoolean("paired")||!actual.optBoolean("ready"))return false;focusOverlay.hideNow();pinchStarts++;zoomInteracting=true;uiZoom=actual.optDouble("zoomRatio",1.0).toFloat();return true};override fun onScale(detector:ScaleGestureDetector):Boolean{pinchSamples++;setZoom(uiZoom*detector.scaleFactor);return true};override fun onScaleEnd(detector:ScaleGestureDetector){zoomInteracting=false}})
        var multiplePointers=false
        val swipes=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(event:MotionEvent)=true
            override fun onSingleTapUp(event:MotionEvent):Boolean{if(multiplePointers)return false;focusAt(event.x,event.y);return true}
            override fun onFling(start:MotionEvent?,end:MotionEvent,velocityX:Float,velocityY:Float):Boolean{
                if(start==null||multiplePointers)return false
                val dx=end.x-start.x;val dy=end.y-start.y
                if(kotlin.math.abs(dx)<dp(64)||kotlin.math.abs(dx)<kotlin.math.abs(dy)*1.5f)return false
                val order=listOf("Photo","Video","Slow Motion","Timelapse");val current=modeTarget?:mode;val index=order.indexOf(current).coerceAtLeast(0);val next=(index+if(dx<0)1 else -1).coerceIn(0,order.lastIndex)
                if(next!=index){focusOverlay.hideNow();selectMode(order[next])};return true
            }
        })
        preview.setOnTouchListener{_,event->
            if(event.actionMasked==MotionEvent.ACTION_DOWN)multiplePointers=false
            if(event.pointerCount>1)multiplePointers=true
            pinch.onTouchEvent(event)
            if(!multiplePointers&&!pinch.isInProgress)swipes.onTouchEvent(event)
            true
        }
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        val modes=LinearLayout(this).apply{gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0)}
        listOf("Photo" to "Photo","Video" to "Video","Slow-mo" to "Slow Motion","Timelapse" to "Timelapse").forEach{(label,value)->
            val button=Button(this).apply{text=label;isAllCaps=false;textSize=16f;minWidth=0;minimumWidth=0;setPadding(0,0,0,0);setTextColor(Color.WHITE);background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33555555),round(Color.BLACK,24),null);contentDescription="Select $value";setOnClickListener{selectMode(value)}}
            modeButtons[value]=button;modes.addView(button,LinearLayout.LayoutParams(0,dp(52),1f))
        }
        root.addView(modes)
        val controls=FrameLayout(this)
        lastImage=ImageView(this).apply{setImageResource(R.drawable.ic_action_gallery);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Open captured photos and videos";background=round(0xff171a1e.toInt(),28);clipToOutline=true;setPadding(dp(12),dp(12),dp(12),dp(12));setOnClickListener{reviewCaptures()}}
        controls.addView(lastImage,FrameLayout.LayoutParams(dp(56),dp(56),Gravity.START or Gravity.CENTER_VERTICAL).apply{marginStart=dp(28)})
        mediaBadge=ImageView(this).apply{setImageResource(R.drawable.ic_action_play);setPadding(dp(15),dp(15),dp(15),dp(15));background=round(0x66000000,28);clipToOutline=true;visibility=View.GONE;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        controls.addView(mediaBadge,FrameLayout.LayoutParams(dp(56),dp(56),Gravity.START or Gravity.CENTER_VERTICAL).apply{marginStart=dp(28)})
        shutter=icon(R.drawable.ic_action_shutter,"Capture"){local(if(isRecording()||cachedState.optBoolean("countdown"))"STOP" else "SHUTTER")}.apply{scaleType=ImageView.ScaleType.FIT_CENTER;background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33888888),round(Color.BLACK,50),null);setPadding(0,0,0,0)}
        controls.addView(shutter,FrameLayout.LayoutParams(dp(96),dp(96),Gravity.CENTER))
        switchButton=icon(R.drawable.ic_action_switch,"Switch camera"){local("SWITCH")}
        controls.addView(switchButton,FrameLayout.LayoutParams(dp(56),dp(56),Gravity.END or Gravity.CENTER_VERTICAL).apply{marginEnd=dp(28)})
        root.addView(controls,LinearLayout.LayoutParams(-1,dp(116)))
        setContentView(root)
        refreshThumbnail()
        preview.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(t:android.graphics.SurfaceTexture,w:Int,h:Int){session?.renderer?.attach(t,w,h)}
            override fun onSurfaceTextureSizeChanged(t:android.graphics.SurfaceTexture,w:Int,h:Int){session?.renderer?.attach(t,w,h)}
            override fun onSurfaceTextureDestroyed(t:android.graphics.SurfaceTexture):Boolean{val renderer=session?.renderer;if(renderer!=null)renderer.detach{t.release()}else t.release();return false}
            override fun onSurfaceTextureUpdated(t:android.graphics.SurfaceTexture){}
        }
        show("Open WristSnap on your watch")
    }
    private var session:CameraSessionService?=null
    private var serviceBound=false
    private var cachedState=JSONObject()
    private var connectionHapticPlayed=false
    private val sessionConnection=object:android.content.ServiceConnection{
        override fun onServiceConnected(name:android.content.ComponentName,binder:IBinder){
            if(!foreground||!serviceBound)return
            session=(binder as CameraSessionService.SessionBinder).session
            session?.uiOwner=this@MainActivity
            session?.listener={message,state->if(foreground)applyState(message,state)}
            session?.approval={done->if(foreground)MaterialAlertDialogBuilder(this@MainActivity).setTitle("Allow watch?").setMessage("Control this camera from your watch.").setPositiveButton("Allow"){_,_->done(true)}.setNegativeButton("Deny"){_,_->done(false)}.setOnCancelListener{done(false)}.show()else done(false)}
            if(preview.isAvailable)session?.renderer?.attach(preview.surfaceTexture!!,preview.width,preview.height)
            session?.publish()
        }
        override fun onServiceDisconnected(name:android.content.ComponentName){session=null;serviceBound=false;paired=false;show("Open phone app to reconnect")}
    }
    private fun applyState(message:String,state:JSONObject){
        if(::focusOverlay.isInitialized&&focusCameraGeneration>=0&&state.optInt("cameraGeneration")!=focusCameraGeneration){focusUiGeneration++;focusOverlay.hideNow();focusCameraGeneration=-1}
        if(::focusOverlay.isInitialized)focusOverlay.updateExposure(state.optInt("exposureIndex"))
        cachedState=state;paired=state.optBoolean("paired");ready=state.optBoolean("ready");front=state.optBoolean("front")
        mode=state.optString("mode","Photo");quality=state.optString("quality","HD");fps=state.optInt("fps",30);timer=state.optInt("timer");aspect=state.optString("aspect","4:3");photoSize=state.optString("photoSize","Auto");flash=state.optString("flash","OFF");torch=state.optBoolean("torch")
        configuring=state.optBoolean("busy");saving=false;finalizing=false
        if(paired&&!connectionHapticPlayed&&session?.claimConnectionHaptic()==true){connectionHapticPlayed=true;remoteButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)}
        val live=state.optBoolean("connectionLive");if(lastConnectionTint!=live){lastConnectionTint=live;remoteButton.imageTintList=android.content.res.ColorStateList.valueOf(if(live)Color.WHITE else 0xffa1a9b4.toInt())}
        show(message)
        if(state.has("sessionActive")&&!state.optBoolean("sessionActive"))handler.post{releaseSessionUI()}
    }
    private fun ensureSession(){
        if(!foreground||serviceBound||!has(Manifest.permission.CAMERA))return
        try{val intent=android.content.Intent(this,CameraSessionService::class.java);ContextCompat.startForegroundService(this,intent);serviceBound=bindService(intent,sessionConnection,BIND_AUTO_CREATE)}catch(e:Exception){show("Open phone app to reconnect")}
    }
    private fun releaseSessionUI(){
        session?.let{current->if(current.uiOwner===this){current.listener=null;current.approval=null;current.uiOwner=null;current.renderer.detach()}}
        if(serviceBound){try{unbindService(sessionConnection)}catch(_:Exception){}};serviceBound=false;session=null
    }
    private fun state()=session?.state()?:cachedState
    private fun command(c:JSONObject,done:(JSONObject)->Unit){val current=session;if(current==null){show("Connect watch first");return};current.command(c){reply->applyState(reply.optString("message",reply.optString("error")),reply.optJSONObject("state")?:current.state());if(!reply.optBoolean("ok"))showCameraError(reply.optString("error","Camera unavailable"));done(reply)}}
    private var modeTarget:String?=null
    private var modeSwitching=false
    private fun selectMode(target:String){
        val controller=session?:return
        if(!paired||isRecording()||cachedState.optBoolean("countdown")||cachedState.optBoolean("busy")&&!modeSwitching)return
        modeTarget=target;if(modeSwitching)return;modeSwitching=true
        fun applyNext(){
            val wanted=modeTarget?:run{modeSwitching=false;return};modeTarget=null
            controller.configure(JSONObject().put("mode",wanted)){reply->
                if(!foreground||session!==controller){modeSwitching=false;modeTarget=null;return@configure}
                applyState(reply.optString("message",reply.optString("error")),reply.optJSONObject("state")?:controller.state())
                if(!reply.optBoolean("ok"))showCameraError(reply.optString("error","Mode unavailable"))
                if(modeTarget!=null)applyNext()else modeSwitching=false
            }
        }
        applyNext()
    }
    private var lastCameraError=""
    private fun showCameraError(message:String){if(!foreground||message==lastCameraError)return;lastCameraError=message;MaterialAlertDialogBuilder(this).setTitle("Camera").setMessage(message).setPositiveButton("OK"){_,_->lastCameraError=""}.show()}
    private fun configure(args:JSONObject,done:(JSONObject)->Unit){command(JSONObject().put("op","CONFIG").put("args",args),done)}
    private fun choices(name:String)=state().optJSONArray(name)?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList()
    private fun photoSizes()=choices("photoSizes")
    private fun qualities()=choices("qualities")
    private fun frameRates()=choices("frameRates").mapNotNull{it.toIntOrNull()}
    private fun connectionDialog(){
        ConnectionPanel.show(this,paired,{if(paired){session?.disconnect();releaseSessionUI();paired=false;show("Disconnected")}else{ensureSession();session?.bridge?.openWindow()}},{session?.bridge?.forget();session?.disconnect();releaseSessionUI();paired=false;show("Watch access removed")},{QuickStart.show(this){if(foreground)beginSession()}})
    }
    private fun openSettings(){startActivity(android.content.Intent(this,SettingsActivity::class.java),android.app.ActivityOptions.makeCustomAnimation(this,R.anim.gallery_enter,R.anim.camera_exit).toBundle())}
    override fun onStart(){super.onStart();foreground=true;contentResolver.registerContentObserver(Uri.parse("content://media"),true,mediaObserver)}
    override fun onStop(){focusUiGeneration++;focusOverlay.hideNow();exposurePending=null;contentResolver.unregisterContentObserver(mediaObserver);handler.removeCallbacks(refreshMedia);clearThumbnail();foreground=false;releaseSessionUI();super.onStop()}
    override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);if(foreground)ensureSession()}
    override fun onResume(){
        super.onResume();refreshThumbnail()
        if(!QuickStart.seen(this)){if(!guideShowing){guideShowing=true;QuickStart.show(this){guideShowing=false;if(foreground)beginSession()}}}else beginSession()
    }
    private fun beginSession(){
        if(has(Manifest.permission.CAMERA)){
            ensureSession()
            if(Build.VERSION.SDK_INT>=33&&!has(Manifest.permission.POST_NOTIFICATIONS)&&!permissionRequested){permissionRequested=true;permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))}
        }else if(!permissionRequested){permissionRequested=true;permissions.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS))}
    }
    override fun onDestroy(){mediaWorker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
