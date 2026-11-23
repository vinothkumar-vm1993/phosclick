package dev.zeppcamera.poc

/** Link status belongs only in the header; camera operations keep the lower status line. */
object CameraUiText {
    fun isConnection(message:String):Boolean {
        val s=message.trim().lowercase()
        return s in setOf("watch unavailable","ready for watch","watch disconnected","watch connected","watch connected through zepp","disconnected","watch access removed","watch control active","waiting for watch","waiting for your watch…","open phone app to reconnect","connect watch first","connect your watch first","connection unavailable") || s.startsWith("open phosclick on your watch") || s.startsWith("ready • open camera on watch") || s.startsWith("open camera on watch")
    }
    fun captureLine(message:String)=if(isConnection(message))""else message
}
