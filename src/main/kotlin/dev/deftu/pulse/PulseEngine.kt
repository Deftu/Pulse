package dev.deftu.pulse

/**
 * A pluggable way to serve a [Pulse]'s checks, started via [Pulse.serve]. Lets an engine module
 * (e.g. `jdk-httpserver`) hand back its own handle type [H] without the root module ever knowing
 * that engine exists.
 */
public fun interface PulseEngine<out H> {
    public fun start(pulse: Pulse): H
}
