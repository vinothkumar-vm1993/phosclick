package dev.zeppcamera.poc

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject

/** Contextual settings, with full-page value selection and live camera capabilities. */
class SettingsActivity:AppCompatActivity(){
    private var session:CameraSessionService?=null
    private var bound=false;private var visible=false
    private lateinit var root:FrameLayout
    private var current=JSONObject();private var signature=""
    private data class Picker(val title:String,val key:String,val options:List<Pair<String,Any>>)
    private var picker:Picker?=null
    private var pendingAudio:Boolean?=null
    private val micPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)setAudio(true)else{render(true);error("Allow microphone access to record audio.")}}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){
            if(!visible||!bound)return
            session=(binder as CameraSessionService.SessionBinder).session
            session?.uiOwner=this@SettingsActivity;session?.renderer?.detach()
            session?.listener={_,state->if(visible){current=state;render()}}
            pendingAudio?.let{pendingAudio=null;setAudio(it)}
            session?.publish()
        }
        override fun onServiceDisconnected(name:ComponentName){session=null;bound=false;current=JSONObject();render(true)}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);setOnApplyWindowInsetsListener{v,i->val s=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(s.left,s.top,s.right,s.bottom);i}}
        setContentView(root);onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){goBack()}});render(true)
    }
    override fun onStart(){super.onStart();visible=true;bound=bindService(Intent(this,CameraSessionService::class.java),connection,0)}
    override fun onStop(){visible=false;session?.let{s->if(s.uiOwner===this){s.uiOwner=null;s.listener=null;s.approval=null}};if(bound)try{unbindService(connection)}catch(_:Exception){};bound=false;session=null;super.onStop()}
    @Suppress("DEPRECATION") override fun finish(){super.finish();overridePendingTransition(R.anim.camera_enter,R.anim.gallery_exit)}
    private fun goBack(){if(picker!=null){picker=null;render(true,true)}else finish()}
    private fun list(key:String)=current.optJSONArray(key)?.let{a->(0 until a.length()).map{a.get(it)}}?:emptyList()
    private fun quality(value:String)=mapOf("HD" to "720p","FHD" to "1080p","UHD" to "4K")[value]?:value
    private fun valueLabel(key:String,value:Any)=when(key){"quality"->quality(value.toString());"photoSize"->PhotoResolutions.label(value.toString(),list("photoSizes").map{it.toString()});"shotCount"->"$value×";"timer"->if(value.toString()=="0")"Off"else"${value}s";"fps"->"$value fps";else->value.toString()}
    private fun openPicker(title:String,key:String,values:List<Any>){picker=Picker(title,key,values.map{valueLabel(key,it)to it});render(true,true)}
    private fun render(force:Boolean=false,animate:Boolean=false){
        if(!::root.isInitialized)return
        val mode=current.optString("mode","Photo");val busy=current.optBoolean("busy")||current.optBoolean("recording")
        val next=listOf(mode,current.optString("quality"),current.optInt("fps"),current.optInt("timer"),current.optInt("shotCount",1),current.optString("aspect"),current.optString("photoSize"),current.optBoolean("audioEnabled"),current.optBoolean("microphoneGranted"),current.optBoolean("paired"),busy,list("qualities"),list("frameRates"),list("photoSizes"),picker?.title).toString()
        if(!force&&signature==next)return;signature=next
        val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK)}
        page.addView(MaterialToolbar(this).apply{title=picker?.title?:"Settings";setTitleTextColor(Color.WHITE);setNavigationIcon(R.drawable.ic_action_back);navigationContentDescription=if(picker==null)"Back to camera"else"Back to settings";setNavigationOnClickListener{goBack()}},LinearLayout.LayoutParams(-1,dp(64)))
        val scroll=ScrollView(this).apply{isFillViewport=true};val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(28))};scroll.addView(content);page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val enabled=current.optBoolean("paired")&&!busy
        if(picker!=null){
            val p=picker!!
            p.options.forEach{(label,value)->
                val selected=if(p.key=="photoSize")valueLabel(p.key,current.opt(p.key)?:"Auto")==valueLabel(p.key,value)else current.opt(p.key)?.toString()==value.toString()
                row(content,label,"",if(selected)R.drawable.ic_action_check else 0,enabled,"Choose ${p.title}: $label"+if(selected)", selected"else""){
                    session?.configure(JSONObject().put(p.key,value)){reply->current=reply.optJSONObject("state")?:current;if(reply.optBoolean("ok")){picker=null;render(true,true)}else{render(true);error(reply.optString("error","Try another option."))}}
                }
            }
        }else{
            heading(content,mode)
            if(!current.optBoolean("paired"))row(content,"Open camera","Connect watch",R.drawable.ic_action_phone,true,"Return to camera to connect"){finish()}
            val photo=mode=="Photo"
            val key=if(photo)"photoSize"else"quality";val sizes=if(photo)PhotoResolutions.options(list("photoSizes").map{it.toString()}).map{it.value}else list("qualities")
            row(content,"Resolution",valueLabel(key,current.opt(key)?:"Auto"),R.drawable.ic_action_resolution,enabled&&sizes.size>1,"Resolution settings"){openPicker("Resolution",key,sizes)}
            if(mode=="Video"||mode=="Slow Motion"){
                val rates=list("frameRates")
                row(content,"Frame rate","${current.optInt("fps",30)} fps",R.drawable.ic_action_fps,enabled&&rates.size>1,"Frame rate settings"){openPicker("Frame rate","fps",rates)}
            }
            if(mode=="Timelapse")row(content,"Frame interval","1s",R.drawable.ic_action_timer,false,"Timelapse interval, fixed at one second",null)
            if(photo)row(content,"Shots","${current.optInt("shotCount",1)}×",R.drawable.ic_action_multishot,enabled,"Number of photos per shutter"){openPicker("Shots","shotCount",listOf(1,3,5))}
            row(content,"Timer",valueLabel("timer",current.optInt("timer")),R.drawable.ic_action_timer,enabled,"Timer settings"){openPicker("Timer","timer",listOf(0,3,10))}
            row(content,"Aspect ratio",if(photo)current.optString("aspect","4:3")else"16:9",R.drawable.ic_action_aspect,enabled&&photo,"Aspect ratio settings",if(photo){{openPicker("Aspect ratio","aspect",listOf("4:3","16:9"))}}else null)
            if(mode=="Video"){
                val toggle=MaterialSwitch(this).apply{text="Record audio";textSize=18f;setTextColor(Color.WHITE);isChecked=current.optBoolean("audioEnabled");isEnabled=enabled;contentDescription="Record audio using phone microphone";setPadding(dp(16),dp(8),dp(8),dp(8));setOnCheckedChangeListener{_,on->if(on&&ContextCompat.checkSelfPermission(this@SettingsActivity,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)micPermission.launch(Manifest.permission.RECORD_AUDIO)else setAudio(on)}}
                content.addView(toggle,LinearLayout.LayoutParams(-1,dp(72)))
            }
            heading(content,"App")
            row(content,"Save location","On phone",R.drawable.ic_action_gallery,false,"Media is saved on this phone",null)
            row(content,"Quick start","",R.drawable.ic_action_help,true,"Show quick start guide"){QuickStart.show(this){}}
            row(content,"Permissions","",R.drawable.ic_action_permissions,true,"Manage app permissions"){startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}
            content.addView(TextView(this).apply{text="WristSnap · ${BuildConfig.VERSION_NAME}";textSize=16f;setTextColor(0xffa1a9b4.toInt());gravity=Gravity.CENTER;setPadding(0,dp(24),0,0)},LinearLayout.LayoutParams(-1,-2))
        }
        root.removeAllViews();root.addView(page,FrameLayout.LayoutParams(-1,-1))
        if(animate){page.alpha=0f;page.translationY=dp(8).toFloat();page.animate().alpha(1f).translationY(0f).setDuration(160).start()}
    }
    private fun heading(parent:LinearLayout,label:String){parent.addView(TextView(this).apply{text=label;textSize=16f;setTextColor(0xffc0c7d2.toInt());setPadding(dp(12),dp(18),0,dp(12))},LinearLayout.LayoutParams(-1,-2))}
    private fun row(parent:LinearLayout,label:String,value:String,icon:Int,enabled:Boolean,description:String,action:(()->Unit)?){
        val card=MaterialCardView(this).apply{radius=dp(20).toFloat();setCardBackgroundColor(Color.BLACK);strokeColor=0xff24282e.toInt();strokeWidth=dp(1);contentDescription=description;isClickable=enabled&&action!=null;if(isClickable)setOnClickListener{action?.invoke()}}
        val line=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(72);setPadding(dp(16),dp(12),dp(16),dp(12))}
        line.addView(ImageView(this).apply{if(icon!=0)setImageResource(icon)else visibility=View.INVISIBLE;imageTintList=android.content.res.ColorStateList.valueOf(if(enabled)Color.WHITE else 0xffa1a9b4.toInt());importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(24),dp(24)).apply{marginEnd=dp(16)})
        line.addView(TextView(this).apply{text=label;textSize=18f;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,-2,1f))
        if(value.isNotEmpty())line.addView(TextView(this).apply{text=value;textSize=16f;maxWidth=dp(136);gravity=Gravity.END;setTextColor(0xffc0c7d2.toInt());setPadding(dp(12),0,0,0)},LinearLayout.LayoutParams(-2,-2))
        if(enabled&&action!=null&&picker==null)line.addView(ImageView(this).apply{setImageResource(R.drawable.ic_action_back);rotation=180f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(20),dp(20)).apply{marginStart=dp(8)})
        card.addView(line);parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
    }
    private fun setAudio(on:Boolean){
        val target=session?:run{pendingAudio=on;return}
        val failure=target.setAudioEnabled(on)
        if(failure!=null)error(failure)else{
            // This activity is visible; Android permits updating camera/microphone service types here.
            ContextCompat.startForegroundService(this,Intent(this,CameraSessionService::class.java))
            session?.publish()
        }
    }
    private fun error(message:String){if(visible)MaterialAlertDialogBuilder(this).setTitle("Can't apply setting").setMessage(message).setPositiveButton("OK",null).show()}
}
