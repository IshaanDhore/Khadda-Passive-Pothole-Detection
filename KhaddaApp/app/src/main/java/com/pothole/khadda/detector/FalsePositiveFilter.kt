package com.pothole.khadda.detector

import com.pothole.khadda.model.PotholeEvent
import kotlin.math.*

/**
 * FalsePositiveFilter evaluates raw algorithm triggers and filters out:
 * 1. Hard braking maneuvers (high longitudinal deceleration without vertical impact).
 * 2. Sharp turns and cornering (high angular velocity / lateral g-force).
 * 3. Duplicate triggers of the same pothole within distance/time thresholds.
 * 4. Speed breakers (smooth biphasic oscillations).
 *
 * Implements SRS Section 4.2 and Assignment 3 DFD process 4.0 (Check duplicate).
 */
class FalsePositiveFilter(
    var duplicateDistanceMeters: Double = 15.0,
    var duplicateTimeMillis: Long = 3000L,
    var maxBrakingThreshold: Double = 4.5,      // m/s^2
    var maxTurningGyroThreshold: Double = 1.2    // rad/s
) {
    private var lastConfirmedEvent: PotholeEvent? = null

    /**
     * Validates if a raw trigger is a genuine pothole event or a false positive.
     * Returns true if the trigger passes all validation filters.
     */
    fun validateTrigger(
        features: FeatureSet,
        latitude: Double,
        longitude: Double,
        speedMps: Float,
        currentTime: Long = System.currentTimeMillis()
    ): ValidationResult {
        // 0. Speed gate (ignore below 10km/h ~ 2.77 m/s)
//        if (speedMps < 2.77f) {
//            return ValidationResult(isValid = false, reason = "Filtered: Speed below 10km/h")
//        }

        // 1. Check for hard braking without vertical impact
        if (features.horizontalAccel > maxBrakingThreshold && features.zDiff < 2.5) {
            return ValidationResult(isValid = false, reason = "Filtered: Hard braking / acceleration")
        }

        // 2. Check for sharp turning using computed yaw rate
        if (abs(features.yaw) > 0.3) {
            return ValidationResult(isValid = false, reason = "Filtered: Sharp turning / cornering (|yaw| > 0.3)")
        }
        
        // 2b. Legacy check for overall gyro magnitude just in case
        if (features.gyroMagnitude > maxTurningGyroThreshold && features.zDiff < 2.5) {
            return ValidationResult(isValid = false, reason = "Filtered: High gyro magnitude")
        }

        // 3. Check for cooldown (1.5 seconds purely on time) and duplicate window
        val last = lastConfirmedEvent
        if (last != null) {
            val timeDiff = currentTime - last.timestamp
            // Strict 1.5s cooldown regardless of distance
            if (timeDiff < 1500L) {
                return ValidationResult(isValid = false, reason = "Filtered: Cooldown period active")
            }
            // Original distance check within larger duplicate window
            if (timeDiff in 0 until duplicateTimeMillis) {
                val distance = calculateDistanceMeters(
                    latitude, longitude,
                    last.latitude, last.longitude
                )
                if (distance <= duplicateDistanceMeters) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Filtered: Duplicate event (${distance.toInt()}m, ${timeDiff}ms)"
                    )
                }
            }
        }

        return ValidationResult(isValid = true, reason = "Confirmed Pothole")
    }

    fun recordConfirmedEvent(event: PotholeEvent) {
        lastConfirmedEvent = event
    }

    fun reset() {
        lastConfirmedEvent = null
    }

    /**
     * Haversine formula to calculate great-circle distance between two GPS coordinates in meters.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0) return Double.MAX_VALUE
        if (lat2 == 0.0 && lon2 == 0.0) return Double.MAX_VALUE

        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val reason: String
)
