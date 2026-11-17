package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class MeteringAndResourcesTest {
 @Test fun warningThresholdsAndPriority(){
  assertEquals("",ResourceWarning.evaluate(16,false,500*ResourceWarning.MIB))
  assertEquals("battery_low",ResourceWarning.evaluate(15,false,500*ResourceWarning.MIB))
  assertEquals("",ResourceWarning.evaluate(1,true,500*ResourceWarning.MIB))
  assertEquals("storage_low",ResourceWarning.evaluate(1,false,100*ResourceWarning.MIB))
  assertEquals("storage_critical",ResourceWarning.evaluate(1,false,99*ResourceWarning.MIB))
  assertEquals("",ResourceWarning.evaluate(-1,false,-1))
 }
 @Test fun cropMappingAndMirroring(){
  assertEquals(PreviewMetering.Point(.5f,.5f),PreviewMetering.uncrop(.5f,.5f,4f/3,9f/16))
  val edge=PreviewMetering.uncrop(0f,0f,2f,1f);assertEquals(.25f,edge.x,.0001f);assertEquals(0f,edge.y,.0001f)
  val p=PreviewMetering.Point(.2f,.3f)
  assertEquals(.3f,PreviewMetering.sensor(p,90,false).x,.0001f)
  assertEquals(.8f,PreviewMetering.sensor(p,90,false).y,.0001f)
  assertEquals(.8f,PreviewMetering.sensor(p,0,true).x,.0001f)
 }
 @Test(expected=IllegalArgumentException::class) fun invalidCoordinatesRejected(){PreviewMetering.uncrop(Float.NaN,0f,1f,1f)}
}
