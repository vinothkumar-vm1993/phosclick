package dev.zeppcamera.poc
import org.junit.Test
import org.junit.Assert.*
class ImageFitTest {
 @Test fun imageAspectAndFullBoundsSurviveEveryPreviewSize(){for((w,h)in listOf(4000 to 3000,3000 to 4000,1920 to 1080,1080 to 1920,96 to 96))for((tw,th)in listOf(320 to 200,160 to 100,72 to 72)){val b=ImageFit.contain(w,h,tw,th);assertEquals(w.toFloat()/h,(b.right-b.left)/(b.bottom-b.top),.0001f);assertTrue(b.left>=0&&b.top>=0&&b.right<=tw+.001f&&b.bottom<=th+.001f);assertTrue(kotlin.math.abs(b.left+b.right-tw)<.001f&&kotlin.math.abs(b.top+b.bottom-th)<.001f)}}
 @Test fun portraitHasSideMarginsInsteadOfStretchOrCrop(){val b=ImageFit.contain(60,120,320,200);assertEquals(110f,b.left,0f);assertEquals(210f,b.right,0f);assertEquals(0f,b.top,0f);assertEquals(200f,b.bottom,0f)}
}
