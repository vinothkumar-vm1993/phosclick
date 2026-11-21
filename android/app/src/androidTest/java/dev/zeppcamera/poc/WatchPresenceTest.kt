package dev.zeppcamera.poc

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class WatchPresenceTest {
    @Test
    fun detachClearsPresenceWithoutStoppingThePhoneSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val preferences = context.getSharedPreferences("zepp-local", Context.MODE_PRIVATE)
        val token = "c".repeat(64)
        val live = AtomicBoolean(false)
        val stopped = AtomicBoolean(false)
        preferences.edit().putString("capability", token).commit()
        val bridge = LocalBridge(
            context, { _, done -> done(JSONObject().put("ok", true)) }, {},
            { live.set(it) }, { it(false) }, { stopped.set(true) }
        )

        fun request(path: String, post: Boolean = false, authenticate: Boolean = true): Int {
            val connection = URL("http://127.0.0.1:${LocalBridge.PORT}$path")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 1500
            connection.readTimeout = 3000
            if (authenticate) connection.setRequestProperty("Authorization", "Bearer $token")
            return try {
                if (post) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(2)
                    connection.outputStream.use { it.write("{}".toByteArray()) }
                }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }

        try {
            instrumentation.runOnMainSync { bridge.start() }
            assertEquals(200, request("/session"))
            instrumentation.waitForIdleSync()
            assertTrue(live.get())
            assertEquals(401, request("/detach", post = true, authenticate = false))
            assertTrue(live.get())
            assertEquals(200, request("/detach", post = true))
            assertFalse(live.get())
            assertFalse(stopped.get())
            assertTrue(bridge.isRunning())

            assertEquals(200, request("/session"))
            instrumentation.waitForIdleSync()
            assertTrue(live.get())
        } finally {
            instrumentation.runOnMainSync { bridge.stop() }
            preferences.edit().clear().commit()
        }
    }
}
