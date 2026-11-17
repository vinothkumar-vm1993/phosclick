package dev.zeppcamera.poc

object CameraPolicy {
    fun shots(value:Any):Int {require(value is Number&&value.toDouble() in listOf(1.0,3.0,5.0)){"Choose 1, 3 or 5 photos"};return value.toInt()}
    val modes=setOf("Photo","Video","Slow Motion","Timelapse")
    fun validate(mode:String,quality:String,fps:Int,timer:Int,aspect:String) {
        require(mode in modes){"Unknown camera mode"}
        require(quality in setOf("HD","FHD","UHD")){"Unsupported resolution"}
        require(fps in setOf(30,60,120,240)){"Unsupported frame rate"}
        require(timer in setOf(0,3,10)){"Unsupported timer"}
        require(aspect in setOf("4:3","16:9")){"Unsupported aspect ratio"}
        if(mode=="Timelapse") require(quality!="UHD"&&fps==30){"Timelapse supports HD/FHD, output 30 fps, capture 1 fps"}
        if(mode=="Slow Motion") require(fps>=120){"Select 120 or 240 fps for Slow Motion"}
        if(mode=="Video") require(fps<=60){"Use Slow Motion for high-speed capture"}
    }
}
