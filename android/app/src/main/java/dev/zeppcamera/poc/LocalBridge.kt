package dev.zeppcamera.poc

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.*

/** Phone-local server only. No outbound sockets, DNS, public listener or cloud endpoint. */
class LocalBridge(private val context:Context,
 private val execute:(JSONObject,(JSONObject)->Unit)->Unit,
 private val status:(String)->Unit,private val connected:(Boolean)->Unit,
 private val approve:((Boolean)->Unit)->Unit,private val manualDisconnect:()->Unit={},private val sessionId:()->String={""}){
 companion object{const val PORT=47835}
 private val main=Handler(Looper.getMainLooper())
 private val preferences=context.getSharedPreferences("zepp-local",Context.MODE_PRIVATE)
 private val gate=LocalSessionGate(preferences.getString("capability",null))
 @Volatile private var server:ServerSocket?=null
 private var pool:ThreadPoolExecutor?=null
 @Volatile private var seenAt=0L
 @Volatile private var notified=false
 private val sockets=java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
 private data class Job(val hash:String,val created:Long,val latch:CountDownLatch=CountDownLatch(1),@Volatile var result:ByteArray?=null)
 private val jobs=linkedMapOf<String,Job>()
 private data class Media(val id:String,val bytes:ByteArray,val expires:Long)
 @Volatile private var media:Media?=null
 private val diagnosticHistory=java.util.ArrayDeque<JSONObject>()
 fun isRunning()=server!=null
 private fun secret()=java.security.SecureRandom().let{r->ByteArray(24).also(r::nextBytes).joinToString(""){"%02x".format(it)}}
 fun start(){
  if(server!=null)return
  if(!gate.hasToken())gate.openWindow()
  val listener=ServerSocket()
  try{listener.reuseAddress=true;listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),PORT),8)}catch(e:Exception){listener.close();throw e}
  server=listener
  pool=ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,ArrayBlockingQueue(8))
  Thread({while(server===listener){try{val socket=listener.accept();if(!socket.inetAddress.isLoopbackAddress){socket.close();continue};socket.soTimeout=5000;sockets.add(socket);try{pool!!.execute{socket.use{try{handle(it)}catch(_:Exception){}finally{sockets.remove(it)}}}}catch(_:RejectedExecutionException){socket.close()}}catch(_:Exception){break}}},"ZeppLocalAccept").start()
  main.post{status("Ready • open Camera on watch")}
  main.post(lease)
 }
 private val lease=object:Runnable{
  override fun run(){
   if(server==null)return
   val idleMs=android.os.SystemClock.elapsedRealtime()-seenAt
   if(notified&&idleMs>=20000){
    val commandRunning=synchronized(jobs){jobs.values.any{it.result==null}}
    if(!commandRunning||idleMs>=45000){
     notified=false
     connected(false)
    }
   }
   main.postDelayed(this,2000)
  }
 }
 fun openWindow(){gate.openWindow();status("Open Camera on watch, then allow access here")}
 fun forget(){gate.forget();preferences.edit().clear().apply();notified=false;connected(false);media=null;status("Watch access removed")}
 fun storeImage(png:ByteArray):String{require(png.size<=262144);val m=Media(secret(),png,System.currentTimeMillis()+60000);media=m;return "/media/"+m.id}
 private fun handle(socket:Socket){
  val input=socket.getInputStream();val header=ByteArrayOutputStream();var matched=0
  while(matched<4){val b=input.read();if(b<0)return;header.write(b);if(header.size()>8192)return;val end=intArrayOf(13,10,13,10);matched=if(b==end[matched])matched+1 else if(b==13)1 else 0}
  val lines=header.toString("ISO-8859-1").split("\r\n");val start=lines[0].split(' ');if(start.size!=3)return
  val method=start[0];val path=start[1];val headers=mutableMapOf<String,String>()
  for(line in lines.drop(1)){val i=line.indexOf(':');if(i>0)headers[line.take(i).lowercase()]=line.substring(i+1).trim()}
  if(headers.containsKey("origin")){reply(socket,403,JSONObject().put("error","Browser requests not accepted"));return}
  if(headers.containsKey("transfer-encoding")){reply(socket,400,JSONObject().put("error","Content-Length required"));return}
  val length=headers["content-length"]?.toIntOrNull()?:0
  if(length !in 0..8192){reply(socket,413,JSONObject().put("error","Request too large"));return}
  val body=ByteArray(length);var read=0;while(read<length){val n=input.read(body,read,length-read);if(n<0)return;read+=n}
  if(path=="/health"&&method=="GET"){reply(socket,200,JSONObject().put("service","WristSnapLocal").put("v",1));return}
  if(method=="POST"&&path=="/enroll"){
   try{val ticket=gate.enroll();main.post{approve{yes->try{val token=gate.approve(ticket,yes);if(token!=null)preferences.edit().putString("capability",token).apply()}catch(_:Exception){status("Connection request expired")}}};reply(socket,202,JSONObject().put("ticket",ticket))}catch(e:Exception){reply(socket,409,JSONObject().put("error",e.message))};return
  }
  if(method=="GET"&&path.startsWith("/enroll/")){
   try{val (approved,token)=gate.status(path.removePrefix("/enroll/"));reply(socket,200,JSONObject().put("approved",approved).put("token",token))}catch(e:Exception){reply(socket,403,JSONObject().put("error",e.message))};return
  }
  val token=headers["authorization"]?.removePrefix("Bearer ")?:""
  if(!gate.authorized(token)){reply(socket,401,JSONObject().put("error","AUTH_REQUIRED"));return}
  if(path=="/session"||path=="/command"||path=="/status"){
  seenAt=android.os.SystemClock.elapsedRealtime()
  if(!notified){main.post{if(server!=null&&!notified){notified=true;connected(true);status("Watch connected through Zepp")}}}
  }
  if(path=="/diagnostics"){
   if(method=="POST"){
    val sample=JSONObject(body.toString(Charsets.UTF_8));val clean=JSONObject().put("receivedAt",System.currentTimeMillis())
    val allowed=listOf("event","watchVersion","sideVersion","audioAvailable","audioPending","audioIssue","audioVolume","audioState","hapticStarts","touchUps","vibrationScene","vibrationTypes","captureWidget","soundEnabled")
    for(key in allowed)if(sample.has(key)){val value=sample.get(key);if(value is Number||value is Boolean||value is String&&value.length<=250)clean.put(key,value)}
    synchronized(diagnosticHistory){
     if(diagnosticHistory.size>=40)diagnosticHistory.removeFirst();diagnosticHistory.addLast(clean)
     runCatching{java.io.File(context.filesDir,"watch-diagnostics.json").writeText(JSONObject().put("phoneVersion",BuildConfig.VERSION_NAME).put("samples",org.json.JSONArray(diagnosticHistory.toList())).toString())}
    }
    reply(socket,200,JSONObject().put("ok",true));return
   }
   if(method=="GET"){val list=synchronized(diagnosticHistory){diagnosticHistory.toList()};reply(socket,200,JSONObject().put("phoneVersion",BuildConfig.VERSION_NAME).put("samples",org.json.JSONArray(list)));return}
  }
  if(method=="GET"&&path=="/status"){
   val latch=CountDownLatch(1);var result:JSONObject?=null
   main.post{try{execute(JSONObject().put("op","STATUS")){r->result=r;latch.countDown()}}catch(_:Exception){latch.countDown()}}
   if(latch.await(3,TimeUnit.SECONDS)&&result!=null)reply(socket,200,result!!)else reply(socket,503,JSONObject().put("error","Camera status busy; retry"))
   return
  }
  if(method=="GET"&&path.startsWith("/result/")){val job=synchronized(jobs){jobs[path.removePrefix("/result/").substringBefore('?')]};if(job==null)reply(socket,404,JSONObject().put("error","Unknown command"))else{if(path.endsWith("?wait=1")&&job.result==null)job.latch.await(1500,TimeUnit.MILLISECONDS);if(job.result==null)reply(socket,202,JSONObject().put("pending",true))else raw(socket,200,"application/json",job.result!!)};return}
  if(method=="GET"&&path=="/session"){reply(socket,200,JSONObject().put("ok",true).put("version",BuildConfig.VERSION_NAME).put("sessionId",sessionId()));return}
  if(method=="POST"&&path=="/detach"){
   val detached=CountDownLatch(1)
   main.post{
    try{
     notified=false
     seenAt=0L
     connected(false)
    }finally{detached.countDown()}
   }
   if(detached.await(1,TimeUnit.SECONDS))reply(socket,200,JSONObject().put("ok",true))
   else reply(socket,503,JSONObject().put("error","Watch status busy"))
   return
  }
  if(method=="POST"&&path=="/close"){main.post{notified=false;connected(false);status("Disconnected");manualDisconnect()};reply(socket,200,JSONObject().put("ok",true));return}
  if(method=="GET"&&path.startsWith("/media/")){
   val m=media;if(m==null||m.expires<System.currentTimeMillis()||path!="/media/"+m.id){reply(socket,404,JSONObject().put("error","Image expired"));return};raw(socket,200,"image/png",m.bytes);return
  }
  if(method=="POST"&&path=="/command"){
   try{
    val c=JSONObject(body.toString(Charsets.UTF_8));val id=c.getString("id");require(c.optInt("v")==1&&id.matches(Regex("[A-Za-z0-9_-]{1,80}"))){"Invalid command"}
    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(body).joinToString(""){"%02x".format(it)}
    var fresh=false
    val job=synchronized(jobs){
     jobs.entries.removeAll{System.currentTimeMillis()-it.value.created>120000&&it.value.latch.count==0L}
     val old=jobs[id]
     if(old!=null){require(old.hash==hash){"Command ID conflict"};old}else{
      require(c.optLong("expires") in System.currentTimeMillis()..System.currentTimeMillis()+30000){"Command expired"}
      check(jobs.size<512){"Command history busy; wait a moment"};check(jobs.values.count{it.result==null}<16){"Camera command queue busy"};fresh=true;Job(hash,System.currentTimeMillis()).also{jobs[id]=it}
     }
    }
    if(fresh)main.post{execute(c){r->synchronized(job){if(job.result==null){r.put("id",id);job.result=r.toString().toByteArray();job.latch.countDown()}}}}
    reply(socket,202,JSONObject().put("id",id).put("accepted",true))
   }catch(e:Exception){reply(socket,400,JSONObject().put("error",e.message?:"Invalid command"))};return
  }
  reply(socket,404,JSONObject().put("error","Unknown local endpoint"))
 }
 private fun reply(s:Socket,code:Int,value:JSONObject)=raw(s,code,"application/json",value.toString().toByteArray())
 private fun raw(s:Socket,code:Int,type:String,body:ByteArray){val out=s.getOutputStream();out.write("HTTP/1.1 $code Result\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray());out.write(body);out.flush()}
 private fun abortPending(){synchronized(jobs){jobs.forEach{(id,job)->synchronized(job){if(job.result==null){job.result=JSONObject().put("id",id).put("ok",false).put("error","PHONE_INACTIVE: action interrupted; check Pixel before retrying capture").toString().toByteArray();job.latch.countDown()}}}}}
 fun stop(){abortPending();server?.close();server=null;main.removeCallbacks(lease);gate.closeWindow();synchronized(sockets){sockets.toList().forEach{try{it.close()}catch(_:Exception){}}};pool?.shutdownNow();pool=null;media=null;notified=false;connected(false)}
 fun close()=stop()
}
