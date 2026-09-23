package dev.deftu.pulse

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class PulseTest {

    @Test
    fun `evaluate reports UP with no registered checks`() = runBlocking {
        val pulse = Pulse.create()
        val report = pulse.evaluate()
        assertEquals(HealthState.UP, report.status)
        assertTrue(report.checks.isEmpty())
    }

    @Test
    fun `evaluate reports DOWN if any registered check is down`() = runBlocking {
        val pulse = Pulse.create()
        pulse.register("database") { CheckResult.UP }
        pulse.register("gateway") { CheckResult.down("disconnected") }

        val report = pulse.evaluate()
        assertEquals(HealthState.DOWN, report.status)
        assertEquals(CheckResult.UP, report.checks["database"])
        assertEquals(CheckResult.down("disconnected"), report.checks["gateway"])
    }

    @Test
    fun `evaluate reports a throwing check as DOWN instead of propagating`() = runBlocking {
        val pulse = Pulse.create()
        pulse.register("flaky") { throw IllegalStateException("boom") }

        val report = pulse.evaluate()
        assertEquals(HealthState.DOWN, report.status)
        assertEquals(HealthState.DOWN, report.checks.getValue("flaky").status)
        assertEquals("boom", report.checks.getValue("flaky").message)
    }

    @Test
    fun `unregister removes a check from future reports`() = runBlocking {
        val pulse = Pulse.create()
        pulse.register("temp") { CheckResult.down("still starting") }
        pulse.unregister("temp")

        val report = pulse.evaluate()
        assertEquals(HealthState.UP, report.status)
        assertTrue(report.checks.isEmpty())
    }

    @Test
    fun `registerSettable flips status without re-registering a new check`() = runBlocking {
        val pulse = Pulse.create()
        val gateway = pulse.registerSettable("gateway")

        assertEquals(HealthState.DOWN, pulse.evaluate().status)

        gateway.up()
        assertEquals(HealthState.UP, pulse.evaluate().status)

        gateway.down("disconnected")
        val report = pulse.evaluate()
        assertEquals(HealthState.DOWN, report.status)
        assertEquals("disconnected", report.checks.getValue("gateway").message)
    }

    @Test
    fun `evaluate reports a check that exceeds its timeout as DOWN instead of hanging`() = runBlocking {
        val pulse = Pulse.create(defaultTimeout = 50.milliseconds)
        pulse.register("slow") {
            delay(500)
            CheckResult.UP
        }

        val report = pulse.evaluate()
        assertEquals(HealthState.DOWN, report.status)
        assertTrue(report.checks.getValue("slow").message.orEmpty().contains("timed out"))
    }

    @Test
    fun `evaluate filters checks by type, BOTH counting toward either subset`() = runBlocking {
        val pulse = Pulse.create()
        pulse.register("live-only", type = CheckType.LIVENESS) { CheckResult.UP }
        pulse.register("ready-only", type = CheckType.READINESS) { CheckResult.UP }
        pulse.register("both") { CheckResult.UP }

        assertEquals(setOf("live-only", "both"), pulse.evaluate(CheckType.LIVENESS).checks.keys)
        assertEquals(setOf("ready-only", "both"), pulse.evaluate(CheckType.READINESS).checks.keys)
        assertEquals(setOf("live-only", "ready-only", "both"), pulse.evaluate().checks.keys)
    }

    @Test
    fun `evaluate includes metadata set via setMetadata`() = runBlocking {
        val pulse = Pulse.create()
        pulse.setMetadata("version", "1.2.3")
        pulse.setMetadata(mapOf("commit" to "abc123"))

        assertEquals(mapOf("version" to "1.2.3", "commit" to "abc123"), pulse.evaluate().metadata)
    }

    @Test
    fun `evaluate has no metadata by default`() = runBlocking {
        assertTrue(Pulse.create().evaluate().metadata.isEmpty())
    }

    @Test
    fun `removeMetadata removes a previously set key`() = runBlocking {
        val pulse = Pulse.create()
        pulse.setMetadata("temp", "value")
        pulse.removeMetadata("temp")

        assertFalse(pulse.evaluate().metadata.containsKey("temp"))
    }

    @Test
    fun `evaluate fires transition listeners only when a check's status actually changes`() = runBlocking {
        val transitions = mutableListOf<Triple<String, HealthState?, HealthState>>()
        val pulse = Pulse.create()
        pulse.addTransitionListener { name, previousStatus, currentStatus ->
            transitions += Triple(name, previousStatus, currentStatus)
        }
        val gateway = pulse.registerSettable("gateway")

        pulse.evaluate() // null -> DOWN
        pulse.evaluate() // still DOWN, no transition

        gateway.up()
        pulse.evaluate() // DOWN -> UP

        assertEquals(
            listOf(
                Triple("gateway", null, HealthState.DOWN),
                Triple("gateway", HealthState.DOWN, HealthState.UP)
            ),
            transitions
        )
    }

    @Test
    fun `removeTransitionListener stops future notifications`() = runBlocking {
        var callCount = 0
        val listener = TransitionListener { _, _, _ -> callCount++ }
        val pulse = Pulse.create()
        pulse.addTransitionListener(listener)
        val gateway = pulse.registerSettable("gateway")

        pulse.evaluate() // fires: null -> DOWN
        pulse.removeTransitionListener(listener)

        gateway.up()
        pulse.evaluate() // would fire DOWN -> UP, but the listener was removed

        assertEquals(1, callCount)
    }

    @Test
    fun `a throwing transition listener does not break evaluate`() = runBlocking {
        val pulse = Pulse.create()
        pulse.addTransitionListener { _, _, _ -> throw IllegalStateException("boom") }
        pulse.registerSettable("gateway")

        assertEquals(HealthState.DOWN, pulse.evaluate().status)
    }

}
