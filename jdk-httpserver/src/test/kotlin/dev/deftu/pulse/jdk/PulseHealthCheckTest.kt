package dev.deftu.pulse.jdk

import dev.deftu.pulse.Pulse
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PulseHealthCheckTest {

    private var server: PulseServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `returns true when the server responds UP`() {
        val port = freePort()
        val pulse = Pulse.create()
        server = pulse.serve(JdkHttpEngine(port))

        assertTrue(checkPulseServer(port))
    }

    @Test
    fun `returns false when a check is DOWN`() {
        val port = freePort()
        val pulse = Pulse.create()
        pulse.registerSettable("gateway")
        server = pulse.serve(JdkHttpEngine(port))

        assertFalse(checkPulseServer(port))
    }

    @Test
    fun `returns false when nothing is listening`() {
        assertFalse(checkPulseServer(freePort()))
    }

}
