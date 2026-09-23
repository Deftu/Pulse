package dev.deftu.pulse.nanohttpd

import dev.deftu.pulse.CheckType
import dev.deftu.pulse.HealthState
import dev.deftu.pulse.Pulse
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A running standalone health endpoint, backed by NanoHTTPD - a small (around 30 classes),
 * dependency-free HTTP server library that runs on very old JVMs and on Android. Use this instead
 * of `pulse-jdk-httpserver` when `com.sun.net.httpserver` can't be relied on at all: a stripped
 * `jlink` runtime image, Android, or a non-OpenJDK JVM.
 *
 * Obtained only via `pulse.serve(NanoHttpEngine(...))`. Keep the returned handle only if you need
 * [stop]; nothing else requires it.
 */
public class PulseServer internal constructor(
    pulse: Pulse,
    port: Int,
    mounts: List<Mount>
) {
    internal data class Mount(val path: String, val type: CheckType?)

    private val lock = ReentrantLock()
    private val server = InternalServer(pulse, port, mounts)

    public val isRunning: Boolean
        get() = server.isAlive

    // Idempotent so a stray extra call (or a restart after an intentional stop()) never leaks a
    // second listener - state changes belong on Pulse's checks, not on restarting this.
    public fun start(): PulseServer = lock.withLock {
        if (server.isAlive) return@withLock this
        server.start()
        this
    }

    public fun stop(): PulseServer = lock.withLock {
        server.stop()
        this
    }

    private class InternalServer(
        private val pulse: Pulse,
        port: Int,
        private val mounts: List<Mount>
    ) : NanoHTTPD(port) {
        // NanoHTTPD hands every request to one serve() call with no built-in routing, so paths are
        // matched exactly here - unlike com.sun.net.httpserver, an unmounted path 404s rather than
        // falling back to a shorter prefix.
        override fun serve(session: IHTTPSession): Response {
            if (session.method != Method.GET) {
                return newFixedLengthResponse(HttpStatus.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
            }

            val mount = mounts.firstOrNull { it.path == session.uri }
                ?: return newFixedLengthResponse(HttpStatus.NOT_FOUND, MIME_PLAINTEXT, "")

            val report = runBlocking { pulse.evaluate(mount.type) }
            val body = pulse.json.encodeToString(report)
            val status = if (report.status == HealthState.UP) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE

            return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        }
    }

    // Defined locally rather than relying on NanoHTTPD's built-in Response.Status enum, whose exact
    // set of members isn't guaranteed across versions of a dependency this module exists to stay
    // compatible with.
    private enum class HttpStatus(
        private val code: Int,
        private val description: String
    ) : NanoHTTPD.Response.IStatus {
        OK(200, "OK"),
        NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable");

        override fun getRequestStatus(): Int = code
        override fun getDescription(): String = "$code $description"
    }
}
