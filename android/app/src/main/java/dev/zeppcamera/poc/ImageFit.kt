package dev.zeppcamera.poc

object ImageFit {
 data class Box(val left:Float,val top:Float,val right:Float,val bottom:Float)
 fun contain(width:Int,height:Int,targetWidth:Int,targetHeight:Int):Box{
  require(width>0&&height>0&&targetWidth>0&&targetHeight>0)
  val scale=minOf(targetWidth.toFloat()/width,targetHeight.toFloat()/height)
  val w=(width*scale).coerceAtMost(targetWidth.toFloat());val h=(height*scale).coerceAtMost(targetHeight.toFloat())
  return Box((targetWidth-w)/2,(targetHeight-h)/2,(targetWidth+w)/2,(targetHeight+h)/2)
 }
}
