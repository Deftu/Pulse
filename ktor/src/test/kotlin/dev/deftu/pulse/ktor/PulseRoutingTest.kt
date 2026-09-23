package dev.deftu.pulse.ktor

import dev.deftu.pulse.CheckResult
import dev.deftu.pulse.CheckType
import dev.deftu.pulse.Pulse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PulseRoutingTest {

    @Test
    fun `responds 200 with an UP body when there are no checks`() = testApplication {
        val pulse = Pulse.create()
        application {
            routing { pulse(pulse) }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        // Body includes an automatic "uptimeSeconds" entry alongside the checks, so this checks
        // shape rather than an exact string.
        val body = response.bodyAsText()
        assertTrue(body.contains(""""status":"UP""""))
        assertTrue(body.contains(""""checks":{}"""))
    }

    @Test
    fun `responds 503 when a registered check is DOWN`() = testApplication {
        val pulse = Pulse.create()
        pulse.register("database") { CheckResult.down("unreachable") }
        application {
            routing { pulse(pulse) }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `mounts at a custom path`() = testApplication {
        val pulse = Pulse.create()
        application {
            routing { pulse(pulse, path = "/status") }
        }

        assertEquals(HttpStatusCode.OK, client.get("/status").status)
    }

    @Test
    fun `mounts liveness and readiness subsets alongside the combined endpoint`() = testApplication {
        val pulse = Pulse.create()
        pulse.register("live-only", type = CheckType.LIVENESS) { CheckResult.down("nope") }
        pulse.register("ready-only", type = CheckType.READINESS) { CheckResult.UP }
        application {
            routing { pulse(pulse) }
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/live").status)
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health").status)
    }

    @Test
    fun `omitting a subset path does not mount it`() = testApplication {
        val pulse = Pulse.create()
        application {
            routing { pulse(pulse, livenessPath = null) }
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/health/live").status)
    }

}
