package dev.zeppcamera.poc

import java.security.MessageDigest
import java.util.UUID

/** One bounded, expiring reference to the exact reviewed image; never means "latest". */
class ReviewDeletionGuard(private val now:()->Long=System::currentTimeMillis){
 private var token="";private var uri="";private var expires=0L
 fun issue(target:String):String{uri=target;token=UUID.randomUUID().toString();expires=now()+300000;return token}
 fun consume(candidate:String):String?{
  if(candidate.isEmpty()||now()>expires||!MessageDigest.isEqual(candidate.toByteArray(),token.toByteArray()))return null
  val target=uri;token="";uri="";expires=0;return target.takeIf{it.isNotEmpty()}
 }
}
