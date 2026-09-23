# Pulse

A healthcheck/readiness server for Kotlin apps that doesn't drag in a whole web framework to
answer `GET /health`.

## Why

A common pattern for a Kotlin bot or backend is to embed a full Ktor server, core plus the Netty
engine, just to answer one static `GET /health` route, then tear that server down and rebuild it
on every connection event. That lifecycle is racy. Restarting a listener from concurrent event
handlers can leave it briefly unreachable or in a torn state, which is exactly what an
orchestrator's healthcheck exists to catch, not cause.

Pulse avoids both problems, and does a bit more than a static Docker or Kubernetes probe strictly
needs.

- `Pulse` is the one object an app constructs. Registering checks, setting metadata, listening for
  transitions, reading the shared JSON codec, and starting an engine all happen through it. There's
  nothing else to construct yourself.
- The server starts once and stays up for the process's whole life. Represent a state change, such
  as a gateway disconnecting or a database going unreachable, by flipping a check's result. Don't
  restart the listener to do it.

## Modules

- `dev.deftu:pulse`: the check registry (`Pulse`), check types, the report model
  (`kotlinx-serialization-json`), and the `PulseEngine` extension point.
- `dev.deftu:pulse-jdk-httpserver`: the default standalone engine (`JdkHttpEngine`/`PulseServer`),
  built on `com.sun.net.httpserver`, already part of the JDK. The only added dependency is
  `kotlinx-coroutines-core`, which a Kord- or JDA-based Discord bot, or a Ktor server, already has
  on its classpath. This lives in its own module because `com.sun.net.httpserver` isn't part of
  the formal Java SE spec and is missing from stripped `jlink` images and from Android; an app
  that can't rely on it just skips this module. It also ships `checkPulseServer`/
  `PulseHealthCheckKt`, a loopback HTTP CLI check for Docker's `HEALTHCHECK CMD` in images without
  curl.
- `dev.deftu:pulse-nanohttpd`: an alternative standalone engine (`NanoHttpEngine`/`PulseServer`),
  built on NanoHTTPD, a small (around 30 classes), dependency-free HTTP server library that runs
  on very old JVMs and on Android. Use this instead of `pulse-jdk-httpserver` when
  `com.sun.net.httpserver` can't be relied on at all: a stripped `jlink` runtime image, Android, or
  a non-OpenJDK JVM. Unlike `pulse-jdk-httpserver`, an unmounted subset path 404s outright instead
  of falling back to the combined handler, since this module does its own exact-match routing
  rather than relying on the underlying server's path matching.
- `dev.deftu:pulse-ktor`: mounts a `Pulse` onto an existing Ktor `Route`, for apps that already run
  Ktor and don't want to open a second port.

## Usage

### Standalone (JDK server)

```kotlin
val pulse = Pulse.create()
val gateway = pulse.registerSettable("discord-gateway")

val server = pulse.serve(JdkHttpEngine(port = 6139))

kord.on<ReadyEvent> { gateway.up() }
kord.on<ResumedEvent> { gateway.up() }
kord.on<DisconnectEvent> { gateway.down("disconnected") }

// server keeps running until the process exits, with no start()/stop() per event.
// Keep the returned handle only if you need server.stop() later, e.g. a shutdown hook.
```

This mounts three endpoints by default: the combined report at `/health`, and liveness/readiness
subsets at `/health/live` and `/health/ready` (see [Liveness vs. readiness](#liveness-vs-readiness)
below). Pass `livenessPath`/`readinessPath = null` to skip either.

`GET /health` returns:

```json
{"status":"UP","checks":{"discord-gateway":{"status":"UP"}}}
```
with a `200` when every relevant check is `UP`, `503` otherwise.

`pulse-nanohttpd`'s `NanoHttpEngine` is a drop-in replacement with the same constructor shape, for
environments that can't rely on `com.sun.net.httpserver`:

```kotlin
val server = pulse.serve(NanoHttpEngine(port = 6139))
```

### Mounted on an existing Ktor server

```kotlin
val pulse = Pulse.create()

embeddedServer(Netty, port = 8080) {
    routing {
        pulse(pulse) // /health, /health/live, /health/ready, same paths as JdkHttpEngine
    }
}.start(wait = true)
```

### Docker `HEALTHCHECK` without curl

`pulse-jdk-httpserver` also ships a tiny loopback HTTP CLI check, for base images that don't have
curl or wget. The app already needs a JVM, so this needs nothing extra:

```dockerfile
HEALTHCHECK CMD ["java", "-cp", "app.jar", "dev.deftu.pulse.jdk.PulseHealthCheckKt", "6139"]
```

It's a separate process from the one running `PulseServer`, so it can't read check state directly.
It asks the running server over HTTP instead, and turns the response into an exit code, `0` or
`1`. Call `checkPulseServer(port, path)` directly if you want the same check from Kotlin rather
than the command line.

### Checks

```kotlin
// A function-backed check, re-evaluated on every request, with a per-check timeout
pulse.register("database", timeout = 2.seconds) {
    if (dataSource.isConnected()) CheckResult.UP else CheckResult.down("no connection")
}

// A settable check for event-driven state (see above). Call set() from wherever the state
// changes; evaluate() just reads the current value.
val ready = pulse.registerSettable("ready", initial = CheckResult.down("starting up"))
ready.up()
```

Pulse reports a check that throws, or one that runs past its timeout, as `DOWN` instead of hanging
or failing the whole report. The timeout defaults to 5 seconds (`Pulse.create()`'s
`defaultTimeout`); override it per check with the `timeout` parameter shown above.

`CheckResult.status` is a `HealthState` (`UP`/`DOWN`); `HealthReport.status` is the same, rolled up
from every relevant check.

### Liveness vs. readiness

Kubernetes treats these differently. A failing liveness check gets the pod restarted; a failing
readiness check just pulls it out of load balancing. Tag a check with `type` if it should only
count toward one of them. The default, `CheckType.BOTH`, counts toward the combined report and
both subsets:

```kotlin
pulse.register("database", type = CheckType.READINESS) { ... }   // just stop routing traffic
```

`pulse.evaluate(CheckType.LIVENESS)` / `evaluate(CheckType.READINESS)` return only the matching
subset; `evaluate()` (no argument) returns everything, which is what `/health` serves.

#### A bot's primary connection is a liveness check, not a readiness check

Readiness assumes something else, such as a load balancer or a Kubernetes Service, decides whether
to route work to an instance, and can simply stop routing while that instance recovers. That model
doesn't fit a Discord bot, because nothing sits in front of it deciding whether to send it traffic.
If a bot's gateway connection goes down, the bot isn't temporarily unavailable for new work; it
isn't doing its job at all. Tagging that check `READINESS` leaves a fully broken process running
forever, since nothing ever restarts it. Tag it `LIVENESS` instead (or leave it `BOTH`, the
default), so a dead gateway actually gets the pod restarted:

```kotlin
val gateway = pulse.registerSettable("discord-gateway", type = CheckType.LIVENESS)
```

As a rule of thumb, losing a dependency that stops the app from doing its one job is a liveness
failure; losing one that only stops part of the job, while the app is otherwise fine, is a
readiness failure. A message-queue consumer's connection to its queue, or a webhook receiver's
connection to whatever it forwards to, count as liveness for the same reason. An optional cache,
or a secondary datastore the app can run without, counts as readiness.

### Report metadata

Metadata is entirely up to you. Pulse doesn't add anything of its own, such as uptime or version;
that's the app's concern, not the library's.

```kotlin
val pulse = Pulse.create()
pulse.setMetadata("version", BuildInfo.VERSION)
pulse.setMetadata(mapOf("commit" to BuildInfo.COMMIT, "environment" to "production"))
```

Pulse omits `metadata` from the JSON entirely when nothing's been set. `removeMetadata(key)` undoes
a `setMetadata` call.

### Reacting to state changes

```kotlin
val pulse = Pulse.create()
pulse.addTransitionListener { name, previousStatus, currentStatus ->
    logger.info("check '$name' went from $previousStatus to $currentStatus")
}
```

The listener fires only when a check's status actually changes between evaluations, including the
first observation, where `previousStatus` is `null`. It never fires on a poll that finds no
change. Pulse swallows an exception from a listener, so a broken listener can't take down health
reporting either. `removeTransitionListener` undoes `addTransitionListener`.

### Reusing the JSON codec

`pulse.json` is the same `kotlinx.serialization.json.Json` instance every engine uses to encode a
`HealthReport`. Reach for it instead of building your own if you need to serialize a report
yourself, for logging for example. Pass a custom-configured `Json` to `Pulse.create(json = ...)`
to change the format everywhere at once.

### Adding another engine

An engine is anything implementing `PulseEngine<H>`:

```kotlin
public fun interface PulseEngine<out H> {
    public fun start(pulse: Pulse): H
}
```

`JdkHttpEngine` is the only one Pulse ships (see `pulse-jdk-httpserver`); write your own the same
way and pass it to `pulse.serve(...)`.

## License

LGPL-3.0. See [LICENSE](LICENSE).
