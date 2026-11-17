package dev.zeppcamera.poc

/** Session-local, bounded selection handles; clients never choose arbitrary media URIs. */
class CaptureHistory(private val now:()->Long={System.nanoTime()/1_000_000}){
 data class Entry(val id:String,val target:String)
 private var issued=0L;private val targets=linkedMapOf<String,String>()
 fun replace(items:List<String>):List<Entry>{targets.clear();issued=now();return items.take(8).distinct().map{target->Entry(java.util.UUID.randomUUID().toString(),target).also{targets[it.id]=target}}}
 fun resolve(id:String):String?{val age=now()-issued;if(age !in 0..300_000){targets.clear();return null};return targets[id]}
}
