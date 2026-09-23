package dev.deftu.pulse

import java.util.concurrent.atomic.AtomicReference

/**
 * A [HealthCheck] whose result is flipped by the app itself instead of being computed on demand.
 *
 * Intended to replace the anti-pattern of tearing down and recreating the whole HTTP listener to
 * represent a state change (e.g. a Discord gateway connecting/disconnecting). Keep the listener
 * running for the process's whole life and call [up]/[down] as that state changes instead.
 */
public class SettableHealthCheck(
    initial: CheckResult = CheckResult.down("not ready yet")
) : HealthCheck {
    private val current = AtomicReference(initial)

    public fun set(result: CheckResult) {
        current.set(result)
    }

    public fun up(message: String? = null) {
        set(CheckResult(HealthState.UP, message))
    }

    public fun down(message: String? = null) {
        set(CheckResult(HealthState.DOWN, message))
    }

    override suspend fun check(): CheckResult = current.get()
}
