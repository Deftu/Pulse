package dev.deftu.pulse

/**
 * Registered via [Pulse.addTransitionListener]. Fires only when a check's status actually changes
 * between evaluations - including the first observation, where [previousStatus] is `null` - never
 * on a poll that finds no change.
 */
public fun interface TransitionListener {
    public fun onTransition(name: String, previousStatus: HealthState?, currentStatus: HealthState)
}
