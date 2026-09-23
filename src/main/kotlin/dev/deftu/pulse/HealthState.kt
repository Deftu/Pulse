package dev.deftu.pulse

import kotlinx.serialization.Serializable

@Serializable
public enum class HealthState {
    UP,
    DOWN
}
