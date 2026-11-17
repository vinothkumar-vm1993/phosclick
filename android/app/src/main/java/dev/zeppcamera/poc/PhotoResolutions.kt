package dev.zeppcamera.poc

/** Small, named choices backed by real JPEG sizes; dimensions stay inside the camera protocol. */
object PhotoResolutions {
    data class Option(val label:String,val value:String)
    private fun pixels(value:String):Long?=try{val p=value.split('x');if(p.size!=2)null else Math.multiplyExact(p[0].toLong(),p[1].toLong()).takeIf{it>0}}catch(_:Exception){null}
    fun options(supported:List<String>):List<Option>{
        val sizes=supported.mapNotNull{v->pixels(v)?.let{v to it}}.distinctBy{it.first}.sortedByDescending{it.second}
        val result=mutableListOf(Option("Auto","Auto"))
        val high=sizes.firstOrNull()?:return result
        result+=Option("High",high.first)
        val standard=sizes.firstOrNull{it.second<=high.second*.7}?:sizes.last()
        if(standard.first!=high.first)result+=Option("Standard",standard.first)
        val small=sizes.firstOrNull{it.second<=2_100_000}?:sizes.last()
        if(result.none{it.value==small.first})result+=Option("Small",small.first)
        return result
    }
    fun label(value:String,supported:List<String>):String{
        val choices=options(supported);choices.firstOrNull{it.value==value}?.let{return it.label}
        val area=pixels(value)?:return "Auto"
        return choices.filter{it.value!="Auto"}.minByOrNull{kotlin.math.abs((pixels(it.value)?:area)-area)}?.label?:"Auto"
    }
}
