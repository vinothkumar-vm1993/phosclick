package dev.zeppcamera.poc

/** Direct camera Surfaces already encode sensor rotation and mirroring in their texture matrix. */
data class PreviewOrientation(val extraRotation:Int,val contentRotation:Int,val extraMirror:Boolean){
    companion object{
        fun resolve(sensorToTarget:Int,mirror:Boolean,hasCameraTransform:Boolean):PreviewOrientation{
            val rotation=((sensorToTarget%360)+360)%360
            return PreviewOrientation(if(hasCameraTransform)0 else rotation,rotation,!hasCameraTransform&&mirror)
        }
    }
    fun uprightAspect(width:Int,height:Int):Float=if(contentRotation%180==0)width.toFloat()/height else height.toFloat()/width
}
