package dev.zeppcamera.poc

/** Older untagged movies remain Video; never guess slow motion from playback FPS. */
object MediaKinds {
    internal fun captureName(name:String)=name.removePrefix("PhosClick-").removePrefix("WristSnap-")
    fun classify(mime:String,name:String,stored:String?,captureFps:Float?=null):String {
        if(mime.startsWith("image/"))return "Photo"
        if(stored in setOf("Video","Slow Motion","Timelapse"))return stored!!
        val captureName=captureName(name)
        return when{ captureName.startsWith("timelapse-",true)->"Timelapse";captureName.startsWith("SlowMotion-",true)->"Slow Motion";captureFps!=null&&captureFps>=100f->"Slow Motion";else->"Video" }
    }
    fun duration(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}
}
