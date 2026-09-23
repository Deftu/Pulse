package dev.deftu.pulse

/**
 * Which probe(s) a check counts toward. [BOTH] (the default) means the check shows up in the
 * combined report and in both the liveness and readiness subsets an engine may expose separately.
 */
public enum class CheckType {
    LIVENESS,
    READINESS,
    BOTH
}
