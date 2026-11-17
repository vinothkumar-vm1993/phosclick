package dev.zeppcamera.poc
import org.junit.Assert.*
import org.junit.Test
class PhotoResolutionsTest {
    @Test fun namesReplaceLongDimensionListsWithRealDistinctChoices(){
        val sizes=listOf("Auto","4032x3024","3840x2160","3264x2448","2560x1920","1920x1080","1600x1200","640x480")
        val choices=PhotoResolutions.options(sizes)
        assertEquals(listOf("Auto","High","Standard","Small"),choices.map{it.label});assertEquals(choices.size,choices.map{it.value}.distinct().size);assertTrue(choices.all{it.value in sizes});assertTrue(choices.none{it.label.contains('x')})
    }
    @Test fun limitedOrMalformedCapabilitiesNeverInventSizes(){
        assertEquals(listOf(PhotoResolutions.Option("Auto","Auto")),PhotoResolutions.options(listOf("bad","999999999999999999x88888888888888888")))
        val choices=PhotoResolutions.options(listOf("Auto","1920x1080"));assertEquals(2,choices.size)
    }
}
