package dev.zeppcamera.poc
import android.content.*
import android.graphics.*
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
class MultiShotHistoryTest {
 private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
 private val fixtures=mutableSetOf<Uri>();private var activity:MainActivity?=null
 private fun http(path:String,body:JSONObject?=null):JSONObject{val token=context.getSharedPreferences("zepp-local",0).getString("capability",null)!!;val c=URL("http://127.0.0.1:47835$path").openConnection()as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=5000;c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};return try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}}
 private fun state()=http("/status").getJSONObject("state")
 private fun await(message:String,test:()->Boolean){val end=SystemClock.uptimeMillis()+35000;while(SystemClock.uptimeMillis()<end){try{if(test())return}catch(_:Exception){};Thread.sleep(60)};fail(message)}
 private fun post(op:String,args:JSONObject=JSONObject()):String{val id="shots_history_${System.nanoTime()}";http("/command",JSONObject().put("v",1).put("id",id).put("op",op).put("args",args).put("expires",System.currentTimeMillis()+25000));return id}
 private fun result(id:String):JSONObject{var reply=JSONObject();await("Command did not complete"){reply=http("/result/$id");reply.has("ok")};return reply}
 private fun command(op:String,args:JSONObject=JSONObject())=result(post(op,args))
 private fun configure(count:Int,timer:Int=0){val r=command("CONFIG",JSONObject().put("mode","Photo").put("shotCount",count).put("timer",timer));assertTrue(r.toString(),r.optBoolean("ok"));await("Camera not ready"){state().optBoolean("ready")&&!state().optBoolean("busy")}}
 private fun uris()=CaptureCatalog(context).all().map{it.uri}.toSet()
 @Before fun start(){assertFalse("Unlock phone before tests",context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked);activity=ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MainActivity;await("Camera not ready"){http("/session").optBoolean("ok")&&state().optBoolean("ready")};configure(1)}
 @After fun cleanup(){fixtures.forEach{uri->try{context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,0)},null,null);context.contentResolver.delete(uri,null,null)}catch(_:Exception){}};ins.runOnMainSync{activity?.finish()};context.stopService(Intent(context,CameraSessionService::class.java))}
 @Test fun exactlyOneThreeAndFiveRealPhotos(){for(count in listOf(1,3,5)){configure(count);val before=uris();val reply=command("SHUTTER");val added=uris()-before;fixtures+=added;assertTrue(reply.toString(),reply.optBoolean("ok"));assertEquals(count,added.size);assertEquals(count,state().optInt("shotsTaken"));assertEquals(0,state().optInt("shotsTarget"));assertFalse(state().optBoolean("busy"))}}
 @Test fun timerRunsOnceForTheWholeSequence(){configure(3,3);val before=uris();val id=post("SHUTTER");var countdownEntries=0;var wasCountdown=false;var reply=JSONObject();await("Timed sequence did not finish"){val s=state();val active=s.optBoolean("countdown");if(active&&!wasCountdown)countdownEntries++;wasCountdown=active;reply=http("/result/$id");reply.has("ok")};val added=uris()-before;fixtures+=added;assertTrue(reply.toString(),reply.optBoolean("ok"));assertEquals(3,added.size);assertEquals(1,countdownEntries)}
 @Test fun busyRejectsDuplicatesAndStopCancelsRemainingShots(){configure(5);val before=uris();val id=post("SHUTTER");await("First photo not saved"){state().optInt("shotsTaken")>=1};assertFalse(command("SHUTTER").optBoolean("ok"));assertFalse(command("CONFIG",JSONObject().put("shotCount",1)).optBoolean("ok"));assertTrue(command("STOP").optBoolean("ok"));assertFalse(result(id).optBoolean("ok"));await("Capture did not become idle"){!state().optBoolean("busy")};Thread.sleep(1400);val after=uris()-before;fixtures+=after;assertTrue(after.size in 1..2);Thread.sleep(1400);assertEquals(after,uris()-before)}
 @Test fun rootBackStopsQueuedPhotosAndReleasesCamera(){configure(5);val before=uris();post("SHUTTER");await("First photo not saved"){state().optInt("shotsTaken")>=1};ins.runOnMainSync{activity!!.onBackPressedDispatcher.onBackPressed()};await("Camera service remained after close"){var active=false;ins.runOnMainSync{active=activity?.isFinishing==true};active};Thread.sleep(1800);val after=uris()-before;fixtures+=after;assertTrue(after.size in 1..2);Thread.sleep(1400);assertEquals(after,uris()-before);val response=ins.uiAutomation.executeShellCommand("dumpsys media.camera");val text=android.os.ParcelFileDescriptor.AutoCloseInputStream(response).bufferedReader().use{it.readText()};assertTrue("Camera still active",text.contains("Active Camera Clients:\n[]"))}
 private fun insert(kind:String):Uri{val video=kind!="Photo";val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"PhosClick-Test-${System.nanoTime()}"+if(video)".mp4"else".jpg");put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4"else"image/jpeg");put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/PhosClick"else"Pictures/PhosClick");put(MediaStore.MediaColumns.IS_PENDING,1)};val uri=context.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;fixtures+=uri;context.contentResolver.openOutputStream(uri)!!.use{out->if(video)ins.context.assets.open("deletion-fixture.mp4").use{it.copyTo(out)}else{val b=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);b.compress(Bitmap.CompressFormat.JPEG,90,out);b.recycle()}};context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);CaptureCatalog(context).record(uri,kind);return uri}
 @Test fun boundedHistorySelectsExactMediaAndRejectsDeletedOrUnknownHandles(){
  repeat(9){insert(listOf("Photo","Video","Slow Motion","Timelapse")[it%4])};val expected=CaptureCatalog(context).all(8);val history=command("HISTORY").getJSONArray("history");assertEquals(8,history.length());val first=history.getJSONObject(0).getString("id");val second=history.getJSONObject(1).getString("id");assertFalse(command("REVIEW",JSONObject().put("historyId",expected[0].uri.toString())).optBoolean("ok"));
  val review=command("REVIEW",JSONObject().put("historyId",second));assertTrue(review.toString(),review.optBoolean("ok"));assertEquals(expected[1].uri.toString(),review.optString("mediaKey"));assertEquals(expected[1].kind,review.optString("previewKind"));assertTrue(command("DELETE_REVIEW",JSONObject().put("reviewToken",review.getString("reviewToken"))).optBoolean("ok"));assertFalse(CaptureCatalog(context).exists(expected[1].uri));assertTrue(CaptureCatalog(context).exists(expected[0].uri));assertFalse(command("REVIEW",JSONObject().put("historyId",second)).optBoolean("ok"));
  context.contentResolver.update(expected[0].uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_TRASHED,1)},null,null);assertFalse(command("REVIEW",JSONObject().put("historyId",first)).optBoolean("ok"));val refreshed=command("HISTORY").getJSONArray("history");assertFalse(command("REVIEW",JSONObject().put("historyId",first)).optBoolean("ok"));assertTrue(refreshed.length()<=8)
 }
}
