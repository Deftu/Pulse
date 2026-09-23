package dev.deftu.pulse

public fun interface HealthCheck {
    public suspend fun check(): CheckResult
}
