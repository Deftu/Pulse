package dev.deftu.pulse

import kotlinx.serialization.Serializable

@Serializable
public data class CheckResult(
    public val status: HealthState,
    public val message: String? = null
) {
    public companion object {
        public val UP: CheckResult = CheckResult(HealthState.UP)

        public fun down(message: String? = null): CheckResult =
            CheckResult(HealthState.DOWN, message)
    }
}
