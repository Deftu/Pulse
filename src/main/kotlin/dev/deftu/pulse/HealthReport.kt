package dev.deftu.pulse

import kotlinx.serialization.Serializable

@Serializable
public data class HealthReport(
    public val status: HealthState,
    public val checks: Map<String, CheckResult>,
    public val metadata: Map<String, String> = emptyMap()
)
