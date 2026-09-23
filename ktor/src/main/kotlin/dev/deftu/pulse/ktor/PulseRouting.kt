package dev.deftu.pulse.ktor

import dev.deftu.pulse.CheckType
import dev.deftu.pulse.HealthState
import dev.deftu.pulse.Pulse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Mounts [pulse] onto an existing Ktor server instead of opening a second port — for apps that
 * already run Ktor for something else and don't need `dev.deftu.pulse.jdk.PulseServer`'s
 * standalone listener as well.
 *
 * Mounts the combined report at [path], plus liveness/readiness subsets at [livenessPath] and
 * [readinessPath] — pass `null` for either to skip mounting it.
 */
public fun Route.pulse(
    pulse: Pulse,
    path: String = "/health",
    livenessPath: String? = "$path/live",
    readinessPath: String? = "$path/ready"
) {
    mountPulse(pulse, path, null)
    livenessPath?.let { mountPulse(pulse, it, CheckType.LIVENESS) }
    readinessPath?.let { mountPulse(pulse, it, CheckType.READINESS) }
}

private fun Route.mountPulse(pulse: Pulse, path: String, type: CheckType?) {
    get(path) {
        val report = pulse.evaluate(type)
        val status = if (report.status == HealthState.UP) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respondText(pulse.json.encodeToString(report), ContentType.Application.Json, status)
    }
}
