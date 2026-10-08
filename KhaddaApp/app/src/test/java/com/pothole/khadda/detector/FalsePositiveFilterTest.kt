package com.pothole.khadda.detector

import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests validating False-Positive Filtering and De-duplication logic
 * as specified in SRS Section 4.2 and Assignment 3 DFD Process 4.0.
 */
class FalsePositiveFilterTest {

    private lateinit var filter: FalsePositiveFilter

    @Before
    fun setUp() {
        filter = FalsePositiveFilter(
            duplicateDistanceMeters = 15.0,
            duplicateTimeMillis = 3000L,
            maxBrakingThreshold = 4.5,
            maxTurningGyroThreshold = 1.2
        )
    }

    @Test
    fun testHardBraking_isFilteredOut() {
        // High horizontal deceleration (braking) without substantial vertical shock
        val brakingFeatures = FeatureSet(
            zDiff = 1.2,
            zThreshDiff = 1.0,
            stdevZ = 0.5,
            accelMagnitude = 11.0,
            horizontalAccel = 5.2, // > 4.5 m/s^2 braking
            gyroMagnitude = 0.1,
            meanZ = 9.81,
            roll = 0.0,
            pitch = 0.0,
            yaw = 0.0
        )

        val result = filter.validateTrigger(brakingFeatures, 18.5204, 73.8567, speedMps = 5.0f)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Hard braking"))
    }

    @Test
    fun testSharpTurn_isFilteredOut() {
        // High gyro angular velocity indicating sharp cornering
        val turnFeatures = FeatureSet(
            zDiff = 1.4,
            zThreshDiff = 1.2,
            stdevZ = 0.6,
            accelMagnitude = 10.5,
            horizontalAccel = 2.0,
            gyroMagnitude = 1.8, // > 1.2 rad/s turning
            meanZ = 9.81,
            roll = 0.0,
            pitch = 0.0,
            yaw = 0.4
        )

        val result = filter.validateTrigger(turnFeatures, 18.5204, 73.8567, speedMps = 5.0f)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Sharp turning"))
    }

    @Test
    fun testDuplicateEvent_isFilteredWithinThresholdWindow() {
        val now = 1000000L
        val event1 = PotholeEvent(
            eventId = "event-1",
            timestamp = now,
            latitude = 18.52040,
            longitude = 73.85670,
            zDiffValue = 4.0,
            severity = SeverityLevel.MEDIUM
        )
        filter.recordConfirmedEvent(event1)

        val genuineFeatures = FeatureSet(
            zDiff = 4.5,
            zThreshDiff = 4.0,
            stdevZ = 1.5,
            accelMagnitude = 13.0,
            horizontalAccel = 1.0,
            gyroMagnitude = 0.2,
            meanZ = 10.0,
            roll = 0.0,
            pitch = 0.0,
            yaw = 0.0
        )

        // Same location (within 5 meters) and 2000ms later (bypasses 1.5s cooldown, hits 3s duplicate window)
        val duplicateResult = filter.validateTrigger(
            genuineFeatures,
            18.52042,
            73.85671,
            speedMps = 5.0f,
            currentTime = now + 2000L
        )
        assertFalse(duplicateResult.isValid)
        assertTrue(duplicateResult.reason.contains("Duplicate"))

        // After 4000ms, duplicate suppression expires
        val nextResult = filter.validateTrigger(
            genuineFeatures,
            18.52042,
            73.85671,
            speedMps = 5.0f,
            currentTime = now + 4000L
        )
        assertTrue(nextResult.isValid)
    }

    @Test
    fun testGenuinePothole_passesValidation() {
        val features = FeatureSet(
            zDiff = 5.2,
            zThreshDiff = 5.0,
            stdevZ = 2.0,
            accelMagnitude = 14.0,
            horizontalAccel = 1.5,
            gyroMagnitude = 0.3,
            meanZ = 10.5,
            roll = 0.0,
            pitch = 0.0,
            yaw = 0.0
        )

        val result = filter.validateTrigger(features, 18.5204, 73.8567, speedMps = 5.0f)
        assertTrue(result.isValid)
        assertEquals("Confirmed Pothole", result.reason)
    }
}
