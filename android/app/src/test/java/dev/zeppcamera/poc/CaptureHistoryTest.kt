package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class CaptureHistoryTest {
 @Test fun handlesAreBoundedOpaqueExpiringAndSuperseded(){var clock=100L;val h=CaptureHistory{clock};val list=h.replace((1..12).map{"private:$it"});assertEquals(8,list.size);assertEquals("private:1",h.resolve(list[0].id));assertNull(h.resolve("private:1"));val replacement=h.replace(listOf("new"));assertNull(h.resolve(list[0].id));assertEquals("new",h.resolve(replacement[0].id));clock+=300001;assertNull(h.resolve(replacement[0].id))}
 @Test fun fractionalAndUnboundedCountsRejected(){for(value in listOf<Any>(0,2,4,6,100,3.5,"3")){try{CameraPolicy.shots(value);fail("Accepted $value")}catch(_:IllegalArgumentException){}};assertEquals(3,CameraPolicy.shots(3));assertEquals(5,CameraPolicy.shots(5))}
}
