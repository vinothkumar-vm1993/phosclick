package dev.zeppcamera.poc

/** Hidden capability credentials; no user-entered app key. Loopback enrollment still needs
 * a foreground connection window and explicit approval on the phone. It is not caller attestation.
 */
class LocalSessionGate(initialToken:String?=null,private val now:()->Long=System::currentTimeMillis,
 private val random:()->String={java.security.SecureRandom().let{r->ByteArray(32).also(r::nextBytes).joinToString(""){"%02x".format(it)}}}) {
 private var token=initialToken
 private var window=0L
 private var ticket:String?=null
 private var expiry=0L
 private var allowed=false
 private val starts=java.util.ArrayDeque<Long>()
 @Synchronized fun hasToken()=token!=null
 @Synchronized fun openWindow(){window=now()+120000}
 @Synchronized fun enroll():String {
  check(now()<window){"Open WristSnap on phone and tap Connect"}
  check(ticket==null||now()>expiry){"Another approval is pending; wait or reopen Connect"}
  while(starts.isNotEmpty()&&now()-starts.first>300000)starts.removeFirst()
  check(starts.size<3){"Too many connection requests; wait five minutes"}
  starts.add(now());ticket=random();expiry=now()+90000;allowed=false;return ticket!!
 }
 @Synchronized fun approve(value:String,yes:Boolean):String? {
  check(ticket==value&&now()<expiry){"Connection request expired"}
  if(!yes){ticket=null;return null}
  token=random();allowed=true;expiry=now()+120000;window=0;return token
 }
 @Synchronized fun status(value:String):Pair<Boolean,String?> {
  check(ticket!=null&&equal(value,ticket!!)&&now()<expiry){"Connection request expired"}
  return allowed to if(allowed)token else null
 }
 @Synchronized fun authorized(value:String)=token!=null&&equal(value,token!!)
 @Synchronized fun forget(){token=null;ticket=null;window=0;allowed=false}
 @Synchronized fun closeWindow(){ticket=null;window=0;allowed=false}
 private fun equal(a:String,b:String)=java.security.MessageDigest.isEqual(a.toByteArray(),b.toByteArray())
}
