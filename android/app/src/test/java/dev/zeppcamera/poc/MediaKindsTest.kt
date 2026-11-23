package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class MediaKindsTest {
 @Test fun allCaptureKindsRetainTheirRealIdentity(){
  assertEquals("Photo",MediaKinds.classify("image/jpeg","Action.jpg",null))
  assertEquals("Video",MediaKinds.classify("video/mp4","Action-old.mp4",null))
  assertEquals("Slow Motion",MediaKinds.classify("video/mp4","Action.mp4","Slow Motion"))
  assertEquals("Timelapse",MediaKinds.classify("video/mp4","timelapse-123.mp4",null))
 }
 @Test fun oldUntaggedVideoIsNotFalselyLabeledSlowMotion(){assertEquals("Video",MediaKinds.classify("video/mp4","Action.mp4","invalid"));assertEquals("Slow Motion",MediaKinds.classify("video/mp4","Action-old.mp4",null,120f));assertEquals("Video",MediaKinds.classify("video/mp4","Action-old.mp4",null,30f));assertEquals("1:05",MediaKinds.duration(65000));assertEquals("0:00",MediaKinds.duration(-2000))}
    @org.junit.Test fun brandedMovieNamesKeepTheirCaptureKinds(){
        org.junit.Assert.assertEquals("Slow Motion",MediaKinds.classify("video/mp4","PhosClick-SlowMotion-1.mp4",null))
        org.junit.Assert.assertEquals("Timelapse",MediaKinds.classify("video/mp4","WristSnap-Timelapse-1.mp4",null))
        org.junit.Assert.assertEquals("Video",MediaKinds.classify("video/mp4","WristSnap-Video-1.mp4",null))
    }
}
