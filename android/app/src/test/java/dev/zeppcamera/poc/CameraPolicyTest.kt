package dev.zeppcamera.poc
import org.junit.Test
import org.junit.Assert.*
class CameraPolicyTest {
 @Test fun validModes(){CameraPolicy.validate("Photo","HD",30,10,"4:3");CameraPolicy.validate("Video","FHD",60,0,"16:9");CameraPolicy.validate("Slow Motion","HD",120,3,"4:3");CameraPolicy.validate("Timelapse","HD",30,0,"4:3")}
 @Test fun rejectFakeSlowMotion(){assertThrows(IllegalArgumentException::class.java){CameraPolicy.validate("Slow Motion","HD",30,0,"4:3")}}
 @Test fun rejectInvalidSettings(){for(a in listOf({CameraPolicy.validate("Video","8K",30,0,"4:3")},{CameraPolicy.validate("Video","HD",120,0,"4:3")},{CameraPolicy.validate("Photo","HD",30,99,"4:3")},{CameraPolicy.validate("Timelapse","UHD",30,0,"4:3")}))assertThrows(IllegalArgumentException::class.java,a)}
}
