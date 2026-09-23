package dev.deftu.pulse.jdk

import dev.deftu.pulse.CheckType
import dev.deftu.pulse.Pulse
import dev.deftu.pulse.PulseEngine

/**
 * Pass to [Pulse.serve] to start a standalone health endpoint on `com.sun.net.httpserver`.
 *
 * Mounts the combined report at [path], plus liveness/readiness subsets at [livenessPath] and
 * [readinessPath] - pass `null` for either to skip mounting it.
 *
 * ```kotlin
 * val server = pulse.serve(JdkHttpEngine(port = 6139))
 * ```
 */
public class JdkHttpEngine(
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
