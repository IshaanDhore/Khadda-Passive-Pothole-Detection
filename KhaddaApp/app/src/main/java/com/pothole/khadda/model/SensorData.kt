package com.pothole.khadda.model

import kotlin.math.sqrt

/**
 * Sensor data model representing synchronized 3-axis accelerometer and gyroscope measurements.
 * Matches the UML Class Diagram: SensorData, Accelerometer, Gyroscope.
 */
data class SensorData(
    val timestamp: Long = System.currentTimeMillis(),
    val timestampNs: Long = 0,
    val accelX: Double = 0.0,
    val accelY: Double = 0.0,
    val accelZ: Double = 0.0,
    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0,
    val gravX: Double = 0.0,
    val gravY: Double = 0.0,
    val gravZ: Double = 0.0,
    val linX: Double = 0.0,
    val linY: Double = 0.0,
    val linZ: Double = 0.0
) {
    /**
     * Calculates the total acceleration magnitude: sqrt(ax^2 + ay^2 + az^2)
     * Used directly in the G-ZERO algorithm.
     */
    fun getAccelMagnitude(): Double {
        return sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
    }

    /**
     * Calculates the total gyroscope rotational velocity magnitude.
     */
    fun getGyroMagnitude(): Double {
        return sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
    }

    /**
     * Calculates horizontal acceleration magnitude: sqrt(ax^2 + ay^2)
     * Used for braking and turning false-positive filtering.
     */
    fun getHorizontalAccel(): Double {
        return sqrt(accelX * accelX + accelY * accelY)
    }
}
