package dev.deftu.pulse.jdk

import dev.deftu.pulse.CheckResult
import dev.deftu.pulse.CheckType
import dev.deftu.pulse.Pulse
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PulseServerTest {

    private var server: PulseServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun get(port: Int, path: String = "/health"): HttpResponse<String> {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `serve starts immediately, start is idempotent, and stop closes the listener`() {
        val port = freePort()
        val pulse = Pulse.create()
        val server = pulse.serve(JdkHttpEngine(port)).also { server = it }

        assertTrue(server.isRunning)
        server.start()
        assertTrue(server.isRunning)

        val response = get(port)
        assertEquals(200, response.statusCode())

        server.stop()
        assertFalse(server.isRunning)

        assertFailsToConnect(port)
    }

    @Test
    fun `responds 200 when UP and 503 when a check is DOWN`() {
        val port = freePort()
        val pulse = Pulse.create()
        val gateway = pulse.registerSettable("gateway")
        val server = pulse.serve(JdkHttpEngine(port)).also { server = it }

        assertEquals(503, get(port).statusCode())

        gateway.up()
        assertEquals(200, get(port).statusCode())
    }

    @Test
    fun `serves separate liveness and readiness endpoints alongside the combined one`() {
        val port = freePort()
        val pulse = Pulse.create()
        pulse.register("live-only", type = CheckType.LIVENESS) { CheckResult.down("nope") }
        pulse.register("ready-only", type = CheckType.READINESS) { CheckResult.UP }
        val server = pulse.serve(JdkHttpEngine(port)).also { server = it }

        assertEquals(503, get(port, "/health/live").statusCode())
        assertEquals(200, get(port, "/health/ready").statusCode())
        assertEquals(503, get(port, "/health").statusCode())
    }

    @Test
    fun `omitting a subset path falls back to the combined handler at the parent path`() {
        // com.sun.net.httpserver routes by longest-prefix match: with no dedicated context at
        // /health/live, a request there falls through to the /health context instead of 404ing.
        val port = freePort()
        val pulse = Pulse.create()
        pulse.register("live-only", type = CheckType.LIVENESS) { CheckResult.down("nope") }
        val server = pulse.serve(JdkHttpEngine(port, livenessPath = null)).also { server = it }

        assertEquals(get(port, "/health").statusCode(), get(port, "/health/live").statusCode())
        assertTrue(get(port, "/health/live").body().contains("live-only"))
        assertEquals(200, get(port, "/health/ready").statusCode())
    }

    private fun assertFailsToConnect(port: Int) {
        try {
            get(port)
            throw AssertionError("Expected connecting to a stopped server to fail")
        } catch (_: java.net.ConnectException) {
            // expected
        }
    }

}
