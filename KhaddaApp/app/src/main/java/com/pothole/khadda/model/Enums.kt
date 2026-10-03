package com.pothole.khadda.model

enum class SeverityLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class SessionStatus {
    RUNNING,
    PAUSED,
    STOPPED
}

enum class RepairStatus {
    REPORTED,
    UNDER_REVIEW,
    REPAIRED
}

enum class AlgorithmType(val displayName: String) {
    Z_DIFF("Z-DIFF"),
    Z_THRESH("Z-THRESH"),
    STDEV_Z("STDEV(Z)"),
    G_ZERO("G-ZERO")
}
