package dev.zeppcamera.poc

import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.Executors
import java.util.concurrent.Future

class CapturesActivity:AppCompatActivity(){
    private val workers=Executors.newFixedThreadPool(2)
    private val catalog by lazy{CaptureCatalog(applicationContext)}
    private val cache=object:LruCache<String,Bitmap>(8192){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount/1024}
    private var captures=listOf<SavedCapture>();private var filter=0
    private lateinit var empty:TextView;private lateinit var grid:RecyclerView;private lateinit var progress:CircularProgressIndicator
    private val adapter=CaptureAdapter()
    private lateinit var toolbar:MaterialToolbar
    private val selected=linkedSetOf<Uri>()
    private val selectionTrace=java.util.ArrayDeque<String>()
    private fun traceSelection(event:String){if(!BuildConfig.DEBUG)return;if(selectionTrace.size>=20)selectionTrace.removeFirst();selectionTrace.addLast("$event:${selected.size}:$touching")}
    private val changes=Handler(Looper.getMainLooper())
    private var touching=false
    private val refresh=object:Runnable{override fun run(){if(isDestroyed)return;if(touching){changes.postDelayed(this,120);return};reload()}}
    private val observer=object:ContentObserver(changes){override fun onChange(selfChange:Boolean){changes.removeCallbacks(refresh);changes.postDelayed(refresh,150)}}
    private val trash=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->if(result.resultCode==RESULT_OK)selected.clear();reload();selectionChanged(false)}
    private fun back(){traceSelection("back");if(selected.isNotEmpty()){selected.clear();selectionChanged()}else finish()}
    private fun toggle(uri:Uri){traceSelection("toggle");if(!selected.remove(uri)){if(selected.size>=2000)return;selected+=uri};selectionChanged()}
    private fun selectionChanged(redraw:Boolean=true){
        toolbar.title=if(selected.isEmpty())"Captures"else "${selected.size} selected"
        toolbar.navigationContentDescription=if(selected.isEmpty())"Back to camera"else"Cancel selection"
        toolbar.menu.findItem(1)?.isVisible=selected.isNotEmpty();toolbar.menu.findItem(2)?.isVisible=selected.isNotEmpty()
        if(redraw&&::grid.isInitialized)for(i in 0 until grid.childCount){(grid.getChildViewHolder(grid.getChildAt(i))as? Holder)?.let{renderSelection(it)}}
    }
    private fun renderSelection(h:Holder){
        val item=h.item?:return;val checked=selected.contains(item.uri)
        h.selection.visibility=if(checked)View.VISIBLE else View.GONE;h.card.strokeWidth=if(checked)dp(2)else 0;h.card.strokeColor=getColor(R.color.cobalt_primary);h.card.isChecked=checked
        h.card.contentDescription=(if(selected.isEmpty())"Open"else if(checked)"Deselect"else"Select")+" ${item.kind} capture ${h.number}"
    }
    private fun deleteSelection(){
        val uris=selected.filter{catalog.exists(it)}
        if(uris.isEmpty()){selected.clear();reload();selectionChanged();return}
        try{trash.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(contentResolver,uris,true).intentSender).build())}
        catch(_:Exception){MaterialAlertDialogBuilder(this).setTitle("Can't delete captures").setMessage("Try again or open them in your gallery app.").setPositiveButton("OK",null).show()}
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList("selection")?.forEach{selected+=Uri.parse(it)}
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){back()}})
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK);setOnApplyWindowInsetsListener{v,i->val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}}
        val bar=MaterialToolbar(this).apply{title="Captures";setTitleTextColor(Color.WHITE);setNavigationIcon(R.drawable.ic_action_back);navigationContentDescription="Back to camera";setNavigationOnClickListener{back()}}
        toolbar=bar
        bar.menu.add(0,2,0,"Select all").apply{setIcon(R.drawable.ic_action_check);setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)}
        bar.menu.add(0,1,1,"Delete selected").apply{setIcon(R.drawable.ic_action_delete);setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)}
        bar.setOnMenuItemClickListener{when(it.itemId){1->deleteSelection();2->{selected.clear();selected.addAll(adapter.items.take(2000).map{item->item.uri});selectionChanged()}};true}
        selectionChanged(false)
        root.addView(bar,LinearLayout.LayoutParams(-1,dp(64)))
        val chips=ChipGroup(this).apply{isSingleSelection=true;isSelectionRequired=true;setPadding(dp(16),0,dp(16),dp(8))}
        listOf("All","Photos","Videos").forEachIndexed{index,label->chips.addView(Chip(this).apply{id=View.generateViewId();text=label;isCheckable=true;isChecked=index==0;contentDescription="Filter $label";setOnCheckedChangeListener{_,checked->if(checked){filter=index;display()}}})}
        root.addView(chips,LinearLayout.LayoutParams(-1,-2))
        val body=FrameLayout(this)
        grid=RecyclerView(this).apply{layoutManager=GridLayoutManager(this@CapturesActivity,2);adapter=this@CapturesActivity.adapter;setPadding(dp(12),0,dp(12),dp(16));clipToPadding=false;contentDescription="Captured photos and videos";(itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations=false}
        body.addView(grid,FrameLayout.LayoutParams(-1,-1))
        empty=TextView(this).apply{visibility=View.GONE;text="Loading captures…";textSize=18f;setTextColor(0xffb6bfcd.toInt());gravity=Gravity.CENTER;setPadding(dp(24),0,dp(24),0)}
        body.addView(empty,FrameLayout.LayoutParams(-1,-1))
        progress=CircularProgressIndicator(this).apply{isIndeterminate=true}
        body.addView(progress,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER))
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    private var loadGeneration=0
    override fun onStart(){super.onStart();contentResolver.registerContentObserver(Uri.parse("content://media"),true,observer)}
    override fun onStop(){contentResolver.unregisterContentObserver(observer);changes.removeCallbacks(refresh);super.onStop()}
    override fun onSaveInstanceState(out:Bundle){out.putStringArrayList("selection",ArrayList(selected.map{it.toString()}));super.onSaveInstanceState(out)}
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean{
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)touching=true
        val handled=super.dispatchTouchEvent(event)
        if(event.actionMasked==android.view.MotionEvent.ACTION_UP||event.actionMasked==android.view.MotionEvent.ACTION_CANCEL)touching=false
        return handled
    }
    override fun onResume(){super.onResume();reload()}
    private fun reload(){
        val generation=++loadGeneration;if(captures.isEmpty()){progress.visibility=View.VISIBLE;empty.visibility=View.GONE}
        workers.execute{try{val items=catalog.all();runOnUiThread{if(touching){changes.removeCallbacks(refresh);changes.postDelayed(refresh,120);return@runOnUiThread};if(!isDestroyed&&generation==loadGeneration){val validKeys=items.map{"${it.uri}:${it.modified}"}.toSet();cache.snapshot().keys.filter{it !in validKeys}.forEach{cache.remove(it)};val changed=items!=captures;captures=items;selected.retainAll(items.map{it.uri}.toSet());traceSelection("reload");progress.visibility=View.GONE;if(changed||adapter.items.isEmpty())display();selectionChanged(false)}}}catch(e:Exception){runOnUiThread{if(!isDestroyed&&generation==loadGeneration){progress.visibility=View.GONE;empty.visibility=View.VISIBLE;empty.text="Unable to load captures";MaterialAlertDialogBuilder(this).setTitle("Can't load captures").setMessage("Return to the camera and try again.").setPositiveButton("OK",null).show()}}}}
    }
    private fun display(){
        if(!::grid.isInitialized)return
        adapter.items=captures.filter{filter==0||filter==1&&!it.video||filter==2&&it.video};adapter.notifyDataSetChanged()
        empty.text=if(filter==1)"No photos yet"else if(filter==2)"No videos yet"else "Your captures will appear here"
        empty.visibility=if(adapter.items.isEmpty())View.VISIBLE else View.GONE
    }
    private inner class Holder(val card:MaterialCardView,val image:ImageView,val label:TextView,val play:ImageView,val selection:ImageView):RecyclerView.ViewHolder(card){var job:Future<*>?=null;var cancellation:CancellationSignal?=null;var uri:String?=null;var item:SavedCapture?=null;var number=0}
    private inner class CaptureAdapter:RecyclerView.Adapter<Holder>(){
        var items=listOf<SavedCapture>()
        override fun getItemCount()=items.size
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
            val card=MaterialCardView(this@CapturesActivity).apply{radius=dp(20).toFloat();setCardBackgroundColor(Color.BLACK);strokeWidth=0;layoutParams=RecyclerView.LayoutParams(-1,dp(216)).apply{setMargins(dp(5),dp(5),dp(5),dp(5))}}
            val frame=FrameLayout(this@CapturesActivity);val image=ImageView(this@CapturesActivity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(R.drawable.ic_action_gallery)}
            frame.addView(image,FrameLayout.LayoutParams(-1,-1))
            val play=ImageView(this@CapturesActivity).apply{setImageResource(R.drawable.ic_action_play);setBackgroundColor(0x66000000)}
            frame.addView(play,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER))
            val selection=ImageView(this@CapturesActivity).apply{setImageResource(R.drawable.ic_action_check);setPadding(dp(10),dp(10),dp(10),dp(10));setBackgroundColor(0xcc000000.toInt());importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
            frame.addView(selection,FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP or Gravity.END))
            val label=TextView(this@CapturesActivity).apply{textSize=14f;setTextColor(Color.WHITE);setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(0xcc000000.toInt())}
            frame.addView(label,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));card.addView(frame,FrameLayout.LayoutParams(-1,-1));return Holder(card,image,label,play,selection)
        }
        override fun onBindViewHolder(h:Holder,position:Int){
            h.job?.cancel(true);h.cancellation?.cancel();val item=items[position];val key="${item.uri}:${item.modified}";h.uri=key
            h.card.tag=item.uri
            h.label.text=if(item.video)"${item.kind}  ·  ${MediaKinds.duration(item.duration)}"else "Photo";h.play.visibility=if(item.video)View.VISIBLE else View.GONE
            h.item=item;h.number=position+1;renderSelection(h)
            h.card.setOnLongClickListener{traceSelection("long");toggle(item.uri);true}
            h.card.setOnClickListener{traceSelection("click");if(selected.isNotEmpty())toggle(item.uri)else startActivity(Intent(this@CapturesActivity,MediaViewerActivity::class.java).putExtra("uri",item.uri.toString()).putExtra("kind",item.kind).putExtra("mime",item.mime))}
            val bitmap=cache.get(key);h.image.setImageBitmap(bitmap);if(bitmap==null){if(workers.isShutdown)return;h.image.setImageResource(R.drawable.ic_action_gallery);val cancel=CancellationSignal();h.cancellation=cancel;h.job=workers.submit{try{val result=catalog.thumbnail(item.uri,cancel);cache.put(key,result);runOnUiThread{if(!isDestroyed&&h.uri==key)h.image.setImageBitmap(result)}}catch(_:Exception){}}}
        }
        override fun onViewRecycled(h:Holder){h.job?.cancel(true);h.cancellation?.cancel();h.uri=null;super.onViewRecycled(h)}
    }
    @Suppress("DEPRECATION")
    override fun finish(){super.finish();overridePendingTransition(R.anim.camera_enter,R.anim.gallery_exit)}
    override fun onDestroy(){workers.shutdownNow();cache.evictAll();super.onDestroy()}
}
