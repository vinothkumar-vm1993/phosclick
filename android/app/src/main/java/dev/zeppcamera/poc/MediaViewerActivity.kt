package dev.zeppcamera.poc

import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MediaViewerActivity:AppCompatActivity(){
    private lateinit var uri:Uri;private var mime="image/jpeg";private var kind="Photo"
    private var player:ExoPlayer?=null;private var video:PlayerView?=null;private var position=0L;private var play=true;private var firstFrame=false
    private val worker=Executors.newSingleThreadExecutor()
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        uri=intent.getStringExtra("uri")?.let(Uri::parse)?:run{finish();return}
        if(uri.scheme!="content"||uri.authority!="media"){finish();return}
        mime=try{contentResolver.getType(uri)}catch(_:Exception){null}?:intent.getStringExtra("mime")?:"image/jpeg"
        if(!mime.startsWith("image/")&&!mime.startsWith("video/")){finish();return}
        kind=intent.getStringExtra("kind")?:if(mime.startsWith("video/"))"Video"else "Photo"
        position=savedInstanceState?.getLong("position")?:0;play=savedInstanceState?.getBoolean("play")?:true
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK);setOnApplyWindowInsetsListener{v,i->val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}}
        root.addView(MaterialToolbar(this).apply{title=kind;setTitleTextColor(Color.WHITE);setNavigationIcon(R.drawable.ic_action_back);navigationContentDescription="Back to captures";setNavigationOnClickListener{finish()}},LinearLayout.LayoutParams(-1,dp(64)))
        val frame=FrameLayout(this)
        if(mime.startsWith("video/")){
            video=PlayerView(this).apply{useController=true;controllerShowTimeoutMs=4000;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);setShowNextButton(false);setShowPreviousButton(false);contentDescription="Video preview. Loading"}
            frame.addView(video,FrameLayout.LayoutParams(-1,-1))
        }else{
            val image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Captured photo review"};frame.addView(image,FrameLayout.LayoutParams(-1,-1))
            val loading=TextView(this).apply{text="Loading photo…";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER};frame.addView(loading,FrameLayout.LayoutParams(-1,-1))
            worker.execute{try{val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,info,_->val scale=minOf(1f,1600f/maxOf(info.size.width,info.size.height));d.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1));d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE};runOnUiThread{if(!isDestroyed){image.setImageBitmap(bitmap);loading.visibility=View.GONE}else bitmap.recycle()}}catch(_:Exception){runOnUiThread{if(!isDestroyed){loading.text="Unable to open this photo";showError(false)}}}}
        }
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(MaterialButton(this).apply{text="Open in gallery";isAllCaps=false;contentDescription="Open capture in gallery";setOnClickListener{openGallery()}},LinearLayout.LayoutParams(-1,dp(56)).apply{setMargins(dp(20),dp(12),dp(20),dp(16))})
        setContentView(root)
    }
    private fun openGallery(){try{startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(_:Exception){MaterialAlertDialogBuilder(this).setTitle("No gallery app available").setMessage("Install a gallery app to open this capture externally.").setPositiveButton("OK",null).show()}}
    private fun showError(movie:Boolean){MaterialAlertDialogBuilder(this).setTitle(if(movie)"Can't play this video"else "Can't open this photo").setMessage("You can try opening it in your gallery app.").setPositiveButton("Open in gallery"){_,_->openGallery()}.setNegativeButton("Close",null).show()}
    private fun describe(){val p=player?:return;video?.contentDescription="Video preview. "+(when(p.playbackState){Player.STATE_BUFFERING->"Loading";Player.STATE_ENDED->"Finished";Player.STATE_READY->if(p.isPlaying)"Playing"else "Paused";else->"Preparing"})+if(firstFrame)". Frame rendered"else ""}
    override fun onStart(){super.onStart();worker.execute{if(!CaptureCatalog(applicationContext).exists(uri))runOnUiThread{if(!isDestroyed)finish()}};if(video!=null){firstFrame=false;player=ExoPlayer.Builder(this).build().also{p->video!!.player=p;p.addListener(object:Player.Listener{override fun onPlaybackStateChanged(state:Int){describe()};override fun onIsPlayingChanged(value:Boolean){describe()};override fun onRenderedFirstFrame(){firstFrame=true;describe()};override fun onPlayerError(error:PlaybackException){video?.contentDescription="Video preview. Playback error";if(!isFinishing)showError(true)}});p.setMediaItem(MediaItem.fromUri(uri));p.seekTo(position);p.prepare();p.playWhenReady=play}}}
    override fun onStop(){player?.let{position=it.currentPosition;play=it.playWhenReady;video?.player=null;it.release()};player=null;super.onStop()}
    override fun onSaveInstanceState(out:Bundle){out.putLong("position",player?.currentPosition?:position);out.putBoolean("play",player?.playWhenReady?:play);super.onSaveInstanceState(out)}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
