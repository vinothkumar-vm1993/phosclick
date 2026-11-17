package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class CameraUiTextTest {
    @Test fun connectionNoticesNeverRepeatInCaptureStatus(){
        for(message in listOf("Watch unavailable","Watch disconnected","Watch connected through Zepp","Disconnected","Open WristSnap on your watch"))assertEquals("",CameraUiText.captureLine(message))
    }
    @Test fun captureProgressAndCameraErrorsRemainVisible(){
        for(message in listOf("Photo saved","Recording started","● REC  00:12","Camera disconnected","Zoom unavailable"))assertEquals(message,CameraUiText.captureLine(message))
    }
}
