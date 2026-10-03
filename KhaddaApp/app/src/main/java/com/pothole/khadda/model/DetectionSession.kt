package com.pothole.khadda.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * DetectionSession Entity representing a continuous driving & sensing session.
 * Matches UML Class Diagram: DetectionSession.
 */
@Entity(tableName = "detection_sessions")
data class DetectionSession(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long = 0L,
    var status: SessionStatus = SessionStatus.RUNNING,
    var totalEvents: Int = 0,
    var distanceMeters: Double = 0.0
) {
    fun getDuration(): Long {
        return if (endTime > startTime) {
            endTime - startTime
        } else {
            System.currentTimeMillis() - startTime
        }
    }
}
