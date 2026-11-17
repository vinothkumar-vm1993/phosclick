package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class PreviewOrientationTest {
    @Test fun directCameraSurfaceIsNeverRotatedTwice(){
        for(sensor in listOf(0,90,180,270)){
            val p=PreviewOrientation.resolve(sensor,false,true);assertEquals(0,p.extraRotation)
        }
    }
    @Test fun frontSurfaceDoesNotReceiveDuplicateMirror(){
        assertFalse(PreviewOrientation.resolve(270,true,true).extraMirror)
        val copied=PreviewOrientation.resolve(270,true,false);assertEquals(270,copied.extraRotation);assertTrue(copied.extraMirror)
    }
    @Test fun uprightDimensionsStillUseSensorRotationForCorrectAspect(){
        assertEquals(9f/16f,PreviewOrientation.resolve(90,false,true).uprightAspect(1920,1080),.0001f)
        assertEquals(9f/16f,PreviewOrientation.resolve(270,true,true).uprightAspect(1920,1080),.0001f)
        assertEquals(16f/9f,PreviewOrientation.resolve(0,false,true).uprightAspect(1920,1080),.0001f)
    }
}
