package dev.deftu.pulse.jdk

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import dev.deftu.pulse.CheckType
import dev.deftu.pulse.HealthState
import dev.deftu.pulse.Pulse
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A running standalone health endpoint, backed by the JDK's built-in [HttpServer]
 * (`com.sun.net.httpserver`, shipped in every mainstream OpenJDK-family distribution, but not
 * part of the formal Java SE spec and absent from stripped-down `jlink` images or Android). No
 * Ktor, no Netty, no extra dependency beyond coroutines.
 *
 * Obtained only via `pulse.serve(JdkHttpEngine(...))`. Keep the returned handle only if you need
 * [stop]; nothing else requires it.
 */
public class PulseServer internal constructor(
    private val pulse: Pulse,
    private val port: Int,
    private val mounts: List<Mount>
) {
    private val lock = ReentrantLock()

    @Volatile
    private var server: HttpServer? = null

    public val isRunning: Boolean
        get() = server != null

    // Idempotent so a stray extra call (or a restart after an intentional stop()) never leaks a
    // second listener - state changes belong on Pulse's checks, not on restarting this.
    public fun start(): PulseServer = lock.withLock {
        if (server != null) return@withLock this

        val httpServer = HttpServer.create(InetSocketAddress(port), 0)
        mounts.forEach { mount ->
            httpServer.createContext(mount.path, PulseHandler(pulse, mount.type))
        }
        httpServer.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "pulse-http").apply { isDaemon = true }
        }
        httpServer.start()
        server = httpServer
        this
    }

    public fun stop(gracePeriodSeconds: Int = 0): PulseServer = lock.withLock {
        server?.stop(gracePeriodSeconds)
        server = null
        this
    }

    internal data class Mount(val path: String, val type: CheckType?)

    private class PulseHandler(private val pulse: Pulse, private val type: CheckType?) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.use { exchange ->
                if (exchange.requestMethod != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    return
                }

                val report = runBlocking { pulse.evaluate(type) }
                val body = pulse.json.encodeToString(report).toByteArray(Charsets.UTF_8)
                val status = if (report.status == HealthState.UP) 200 else 503

                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.write(body)
            }
        }
    }
}
