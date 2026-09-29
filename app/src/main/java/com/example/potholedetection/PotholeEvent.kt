package com.example.potholedetection

data class PotholeEvent(
    val eventId: String = "",
    val timestamp: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val algorithmTriggered: String = "",
    val severityValue: Float = 0.0f
)
