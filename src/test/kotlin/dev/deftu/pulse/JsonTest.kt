package dev.deftu.pulse

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JsonTest {

    private val json = Pulse.create().json

    @Test
    fun `default json serializes an UP report with no checks`() {
        val report = HealthReport(HealthState.UP, emptyMap())
        assertEquals("""{"status":"UP","checks":{}}""", json.encodeToString(report))
    }

    @Test
    fun `default json serializes checks with and without a message`() {
        val report = HealthReport(
            HealthState.DOWN,
            linkedMapOf(
                "database" to CheckResult.UP,
                "gateway" to CheckResult.down("disconnected")
            )
        )

        assertEquals(
            """{"status":"DOWN","checks":{"database":{"status":"UP"},"gateway":{"status":"DOWN","message":"disconnected"}}}""",
            json.encodeToString(report)
        )
    }

    @Test
    fun `default json escapes quotes and backslashes in a message`() {
        val report = HealthReport(
            HealthState.DOWN,
            mapOf("check" to CheckResult.down("""said "hi" \ bye"""))
        )

        assertEquals(
            """{"status":"DOWN","checks":{"check":{"status":"DOWN","message":"said \"hi\" \\ bye"}}}""",
            json.encodeToString(report)
        )
    }

    @Test
    fun `default json serializes metadata when present and omits it when empty`() {
        val withMetadata = HealthReport(HealthState.UP, emptyMap(), mapOf("version" to "1.2.3"))
        assertEquals(
            """{"status":"UP","checks":{},"metadata":{"version":"1.2.3"}}""",
            json.encodeToString(withMetadata)
        )

        val withoutMetadata = HealthReport(HealthState.UP, emptyMap())
        assertEquals("""{"status":"UP","checks":{}}""", json.encodeToString(withoutMetadata))
    }

    @Test
    fun `json is the same instance every call, reusable without constructing a new one`() {
        val pulse = Pulse.create()
        assertSame(pulse.json, pulse.json)
    }

}
