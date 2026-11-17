package dev.zeppcamera.poc

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
class WatchMediaPreviewTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val fixtures=mutableListOf<Uri>();private var activity:MainActivity?=null
 private fun insert(video:Boolean=false):Uri{val uri=context.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"WatchPreviewTest-${System.nanoTime()}"+if(video)".mp4"else".jpg");put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4"else"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/WristSnap/"else"Pictures/WristSnap/");put(MediaStore.MediaColumns.IS_PENDING,1)})!!;fixtures+=uri;context.contentResolver.openOutputStream(uri)!!.use{out->if(video)ins.context.assets.open("deletion-fixture.mp4").use{it.copyTo(out)}else{val b=Bitmap.createBitmap(60,120,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);b.compress(Bitmap.CompressFormat.JPEG,95,out);b.recycle()}};context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);return uri}
 private fun bytes(path:String,body:JSONObject?=null):ByteArray{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.use{it.readBytes()}}finally{c.disconnect()}}
 private fun http(path:String,body:JSONObject?=null)=JSONObject(String(bytes(path,body)))
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
 private fun command(op:String,args:JSONObject=JSONObject()):JSONObject{val id="watch_preview_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));var result=JSONObject();await("$op did not finish"){result=http("/result/$id");result.has("ok")};assertTrue(result.toString(),result.optBoolean("ok"));return result}
 private fun image(result:JSONObject):Bitmap{val png=bytes(result.getString("mediaPath"));return BitmapFactory.decodeByteArray(png,0,png.size)?:error("No PNG decoded")}
 @Before fun start(){assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked);activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("Camera not ready"){http("/session").optBoolean("ok")&&http("/status").getJSONObject("state").optBoolean("ready")};command("CONFIG",JSONObject().put("mode","Photo").put("timer",0))}
 @After fun cleanup(){fixtures.forEach{uri->try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null);context.contentResolver.delete(uri,null,null)}catch(_:Exception){}};ins.runOnMainSync{activity?.finish()}}
 @Test fun portraitPreviewPreservesRatioAndDashboardThumbnailTracksTrash(){
  val uri=insert();val reply=command("REVIEW");val bitmap=image(reply);assertEquals(320,bitmap.width);assertEquals(200,bitmap.height)
  var minX=320;var maxX=-1;var minY=200;var maxY=-1
  for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){val c=bitmap.getPixel(x,y);if(Color.red(c)>180&&Color.green(c)<50){minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)}}
  assertEquals(.5f,(maxX-minX+1f)/(maxY-minY+1f),.03f);assertTrue(minX>=105&&maxX<=215);assertEquals(Color.BLACK,bitmap.getPixel(0,100));bitmap.recycle()
  await("Dashboard identity not refreshed"){http("/status").getJSONObject("state").optString("latestMediaKey")==uri.toString()}
  val thumbReply=command("THUMBNAIL");assertEquals(uri.toString(),thumbReply.getString("mediaKey"));val thumb=image(thumbReply);assertEquals(72,thumb.width);assertEquals(72,thumb.height);thumb.recycle()
  context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,1)},null,null)
  await("Deleted image identity remained on watch state"){http("/status").getJSONObject("state").optString("latestMediaKey")!=uri.toString()}
 }
 @Test fun actualVideoSlowAndTimelapseHaveFramesAndExactDeletionTokens(){
  for(kind in listOf("Video","Slow Motion","Timelapse")){
   command("CONFIG",JSONObject().put("mode",kind));await("Movie camera not ready"){val state=http("/status").getJSONObject("state");state.optBoolean("ready")&&!state.optBoolean("busy")};command("SHUTTER");Thread.sleep(if(kind=="Timelapse")6000 else 3000);command("STOP")
   val reply=command("REVIEW",JSONObject().put("kind",kind));assertEquals(kind,reply.getString("previewKind"));assertTrue(reply.has("reviewToken"));val bitmap=image(reply);assertTrue(bitmap.width in listOf(160,320));assertEquals(bitmap.width*5/8,bitmap.height);bitmap.recycle()}
  val video=insert(true);val reply=command("REVIEW",JSONObject().put("kind","Latest"));assertEquals("Video",reply.getString("previewKind"));assertEquals(video.toString(),reply.getString("mediaKey"));val bitmap=image(reply);assertTrue(Color.green(bitmap.getPixel(160,100))>150);bitmap.recycle()
  val thumbReply=command("THUMBNAIL");val thumb=image(thumbReply);assertEquals(72,thumb.width);assertTrue(Color.green(thumb.getPixel(36,36))>150);thumb.recycle()
 }
 @Test fun fullscreenReviewPreservesPortraitRatioOnWatchCanvas(){
  insert();val reply=command("REVIEW",JSONObject().put("fullScreen",true));val bitmap=image(reply);assertEquals(390,bitmap.width);assertEquals(450,bitmap.height)
  var minX=390;var maxX=-1;var minY=450;var maxY=-1
  for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){val c=bitmap.getPixel(x,y);if(Color.red(c)>180&&Color.green(c)<50){minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)}}
  assertEquals(.5f,(maxX-minX+1f)/(maxY-minY+1f),.02f);assertEquals(Color.BLACK,bitmap.getPixel(0,225));bitmap.recycle()
 }
}
