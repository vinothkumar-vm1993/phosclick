package dev.zeppcamera.poc

import android.content.*
import android.graphics.Bitmap
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
class ReviewDeletionTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val fixtures=mutableListOf<Uri>();private var activity:MainActivity?=null
 private fun insert(video:Boolean=false,kind:String="Video"):Uri{
  val uri=context.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"ReviewDeleteTest-${System.nanoTime()}"+if(video)".mp4"else".jpg");put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4"else"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/PhosClick/"else"Pictures/PhosClick/");put(MediaStore.MediaColumns.IS_PENDING,1)})!!;fixtures+=uri
  context.contentResolver.openOutputStream(uri)!!.use{out->if(video)ins.context.assets.open("deletion-fixture.mp4").use{it.copyTo(out)}else{val b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);b.compress(Bitmap.CompressFormat.JPEG,95,out);b.recycle()}}
  context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);CaptureCatalog(context).record(uri,if(video)kind else"Photo");return uri
 }
 private fun http(path:String,body:JSONObject?=null):JSONObject{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}}
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(80)};fail(message)}
 private fun command(op:String,args:JSONObject=JSONObject()):JSONObject{val id="review_delete_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));var result=JSONObject();await("$op did not finish"){result=http("/result/$id");result.has("ok")};return result}
 @Before fun start(){assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked);activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("Camera not ready"){http("/session").optBoolean("ok")&&http("/status").getJSONObject("state").optBoolean("ready")};assertTrue(command("CONFIG",JSONObject().put("mode","Photo").put("timer",0)).optBoolean("ok"))}
 @After fun cleanup(){fixtures.forEach{uri->try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null);context.contentResolver.delete(uri,null,null)}catch(_:Exception){}};ins.runOnMainSync{activity?.finish()}}
 @Test fun deletionTargetsExactReviewedFixtureAndRejectsWrongOrReusedTokens(){
  val reviewed=insert();val reply=command("REVIEW");assertTrue(reply.toString(),reply.optBoolean("ok"));val token=reply.getString("reviewToken");val newer=insert();val catalog=CaptureCatalog(context)
  assertFalse(command("DELETE_REVIEW",JSONObject().put("reviewToken","wrong")).optBoolean("ok"));assertTrue(catalog.exists(reviewed));assertTrue(catalog.exists(newer))
  val deleted=command("DELETE_REVIEW",JSONObject().put("reviewToken",token));assertTrue(deleted.toString(),deleted.optBoolean("ok"));assertFalse(catalog.exists(reviewed));assertTrue("A newer unreviewed photo must never be deleted",catalog.exists(newer));assertFalse(command("DELETE_REVIEW",JSONObject().put("reviewToken",token)).optBoolean("ok"))
 }
 @Test fun exactReviewedVideoCanBeTrashedForAllThreeVideoKinds(){
  for(kind in listOf("Video","Slow Motion","Timelapse")){
   val uri=insert(true,kind);val reply=command("REVIEW",JSONObject().put("kind","Latest"));assertTrue(reply.toString(),reply.optBoolean("ok"));assertEquals(kind,reply.getString("previewKind"));assertEquals(uri.toString(),reply.getString("mediaKey"))
   val deleted=command("DELETE_REVIEW",JSONObject().put("reviewToken",reply.getString("reviewToken")));assertTrue(deleted.toString(),deleted.optBoolean("ok"));assertFalse(CaptureCatalog(context).exists(uri))
  }
 }

}
