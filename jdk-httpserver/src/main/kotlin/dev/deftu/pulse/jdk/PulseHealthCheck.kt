package dev.deftu.pulse.jdk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Checks an already-running [PulseServer] over loopback HTTP and reports the result as a process
 * exit code - for Docker's `HEALTHCHECK CMD` in images that don't ship curl/wget, since an app
 * running Pulse already needs a JVM anyway. This is a separate process from the one running
 * [PulseServer], so it has no access to live check state itself; it only asks the server that
 * does.
 *
 * ```dockerfile
 * HEALTHCHECK CMD ["java", "-cp", "app.jar", "dev.deftu.pulse.jdk.PulseHealthCheckKt", "6139"]
 * ```
 *
 * Any failure to connect, or a non-2xx status, is reported as unhealthy - a healthcheck that
 * cannot confirm the app is healthy has to assume it isn't.
 */
public fun checkPulseServer(
    port: Int,
    path: String = "/health",
    host: String = "localhost",
    timeout: Duration = Duration.ofSeconds(2)
): Boolean = try {
    val client = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    val request = HttpRequest.newBuilder(URI("http://$host:$port$path"))
        .timeout(timeout)
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.discarding())
    response.statusCode() in 200..299
} catch (_: Exception) {
    false
}

public fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 6139
    val path = args.getOrNull(1) ?: "/health"
    exitProcess(if (checkPulseServer(port, path)) 0 else 1)
}
