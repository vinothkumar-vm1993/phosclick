package dev.zeppcamera.poc

object ResourceWarning {
 const val MIB=1024L*1024L
 fun evaluate(batteryPercent:Int,charging:Boolean,availableBytes:Long):String=when{
  availableBytes>=0&&availableBytes<100*MIB->"storage_critical"
  availableBytes>=0&&availableBytes<500*MIB->"storage_low"
  batteryPercent in 0..15&&!charging->"battery_low"
  else->""
 }
 fun label(code:String)=when(code){"storage_critical"->"Storage almost full";"storage_low"->"Storage low";"battery_low"->"Phone battery low";else->""}
}
