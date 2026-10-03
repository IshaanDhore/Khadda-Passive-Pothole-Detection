package com.pothole.khadda.model

import java.util.UUID

/**
 * DetectionReport model summarizing a session's pothole counts, severity distribution,
 * and duration for presentation, evaluation, and export.
 * Matches UML Class Diagram: DetectionReport.
 */
data class DetectionReport(
    val reportId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val totalEvents: Int,
    val lowCount: Int,
    val mediumCount: Int,
    val highCount: Int,
    val generatedOn: Long = System.currentTimeMillis(),
    val durationMillis: Long = 0L,
    val algorithmBreakdown: Map<String, Int> = emptyMap()
)
