package com.pothole.khadda.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * PotholeEvent Entity representing a confirmed road surface anomaly.
 * Matches UML Class Diagram: PotholeEvent.
 */
@Entity(tableName = "pothole_events")
data class PotholeEvent(
    @PrimaryKey
    val eventId: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val zDiffValue: Double = 0.0,
    var severity: SeverityLevel = SeverityLevel.MEDIUM,
    val algorithm: String = AlgorithmType.Z_DIFF.displayName,
    var status: RepairStatus = RepairStatus.REPORTED,
    var syncStatus: String = "QUEUED",
    var address: String = ""
) {
    companion object {
        /**
         * Calculates severity level based on impact magnitude (z-diff value in m/s^2 or g).
         * LOW: Mild road roughness / minor pit
         * MEDIUM: Standard pothole / noticeable bump
         * HIGH: Deep or hazardous crater / severe shock
         */
        fun calculateSeverity(impactMagnitude: Double): SeverityLevel {
            return when {
                impactMagnitude >= 9.0 -> SeverityLevel.HIGH
                impactMagnitude >= 6.5 -> SeverityLevel.MEDIUM
                else -> SeverityLevel.LOW
            }
        }
    }
}
