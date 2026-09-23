package dev.deftu.pulse.nanohttpd

import dev.deftu.pulse.CheckType
import dev.deftu.pulse.Pulse
import dev.deftu.pulse.PulseEngine

/**
 * Pass to [Pulse.serve] to start a standalone health endpoint on NanoHTTPD, for environments that
 * can't rely on `com.sun.net.httpserver` (see `pulse-jdk-httpserver`) being present at all.
 *
 * Mounts the combined report at [path], plus liveness/readiness subsets at [livenessPath] and
 * [readinessPath] - pass `null` for either to skip mounting it.
 *
 * ```kotlin
 * val server = pulse.serve(NanoHttpEngine(port = 6139))
 * ```
 */
public class NanoHttpEngine(
    private val port: Int = 6139,
    private val path: String = "/health",
    private val livenessPath: String? = "$path/live",
    private val readinessPath: String? = "$path/ready"
) : PulseEngine<PulseServer> {
    override fun start(pulse: Pulse): PulseServer {
        val mounts = buildList {
            add(PulseServer.Mount(path, null))
            livenessPath?.let { add(PulseServer.Mount(it, CheckType.LIVENESS)) }
            readinessPath?.let { add(PulseServer.Mount(it, CheckType.READINESS)) }
        }

        return PulseServer(pulse, port, mounts).start()
    }
}
