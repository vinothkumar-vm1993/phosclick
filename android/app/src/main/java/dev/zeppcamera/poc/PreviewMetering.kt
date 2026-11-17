package dev.zeppcamera.poc

/** Inverse of the renderer's center crop; callers then convert display to sensor orientation. */
object PreviewMetering {
 data class Point(val x:Float,val y:Float)
 fun uncrop(x:Float,y:Float,imageAspect:Float,viewAspect:Float):Point{
  require(x.isFinite()&&y.isFinite()&&x in 0f..1f&&y in 0f..1f&&imageAspect.isFinite()&&viewAspect.isFinite()&&imageAspect>0&&viewAspect>0)
  return if(imageAspect>viewAspect)Point(.5f+(x-.5f)*viewAspect/imageAspect,y)else Point(x,.5f+(y-.5f)*imageAspect/viewAspect)
 }
 fun sensor(display:Point,rotation:Int,mirrored:Boolean):Point{
  val x=if(mirrored)1-display.x else display.x;val y=display.y
  return when(((rotation%360)+360)%360){0->Point(x,y);90->Point(y,1-x);180->Point(1-x,1-y);270->Point(1-y,x);else->error("Rotation must be a quarter turn")}
 }
}
