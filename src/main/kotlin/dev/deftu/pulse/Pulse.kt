package dev.deftu.pulse

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class Pulse private constructor(
    public val json: Json,
    private val defaultTimeout: Duration
) {
    private data class RegisteredCheck(
        val check: HealthCheck,
        val type: CheckType,
        val timeout: Duration
    )

    private val checks = ConcurrentHashMap<String, RegisteredCheck>()
    private val lastStatuses = ConcurrentHashMap<String, HealthState>()
    private val metadata = ConcurrentHashMap<String, String>()
    private val transitionListeners = CopyOnWriteArrayList<TransitionListener>()

    public fun register(
        name: String,
        type: CheckType = CheckType.BOTH,
        timeout: Duration = defaultTimeout,
        check: HealthCheck
    ): Pulse = apply {
        checks[name] = RegisteredCheck(check, type, timeout)
    }

    public fun registerSettable(
        name: String,
        initial: CheckResult = CheckResult.down("not ready yet"),
        type: CheckType = CheckType.BOTH,
        timeout: Duration = defaultTimeout
    ): SettableHealthCheck {
        val check = SettableHealthCheck(initial)
        register(name, type, timeout, check)
        return check
    }

    public fun unregister(name: String): Pulse = apply {
        checks.remove(name)
        lastStatuses.remove(name)
    }

    public fun setMetadata(key: String, value: String): Pulse = apply {
        metadata[key] = value
    }

    public fun setMetadata(entries: Map<String, String>): Pulse = apply {
        metadata.putAll(entries)
    }

    public fun removeMetadata(key: String): Pulse = apply {
        metadata.remove(key)
    }

    public fun addTransitionListener(listener: TransitionListener): Pulse = apply {
        transitionListeners += listener
    }

    public fun removeTransitionListener(listener: TransitionListener): Pulse = apply {
        transitionListeners -= listener
    }

    public fun <H> serve(engine: PulseEngine<H>): H = engine.start(this)

    // A check that throws, or hangs past its timeout, must not fail the whole report - one broken
    // or stuck dependency shouldn't make the process look totally dead to an orchestrator that
    // would otherwise still route to it.
    public suspend fun evaluate(type: CheckType? = null): HealthReport = coroutineScope {
        val relevant = if (type == null) {
            checks
        } else {
            checks.filterValues { it.type == type || it.type == CheckType.BOTH }
        }

        val results = relevant.map { (name, registered) ->
            async {
                name to runCatching {
                    withTimeout(registered.timeout) { registered.check.check() }
                }.getOrElse { error ->
                    val message = if (error is TimeoutCancellationException) {
                        "timed out after ${registered.timeout}"
                    } else {
                        error.message ?: error::class.simpleName
                    }
                    CheckResult.down(message)
                }
            }
        }.associate { it.await() }

        results.forEach { (name, result) ->
            val previous = lastStatuses.put(name, result.status)
            if (previous != result.status) {
                transitionListeners.forEach { listener ->
                    runCatching { listener.onTransition(name, previous, result.status) }
                }
            }
        }

        val overall = if (results.values.all { it.status == HealthState.UP }) HealthState.UP else HealthState.DOWN
        HealthReport(overall, results, metadata.toMap())
    }

    public companion object {
        public fun create(
            json: Json = defaultJson(),
            defaultTimeout: Duration = 5.seconds
        ): Pulse = Pulse(json, defaultTimeout)

        private fun defaultJson(): Json = Json {
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
