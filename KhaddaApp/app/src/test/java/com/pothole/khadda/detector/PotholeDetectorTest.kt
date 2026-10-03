package com.pothole.khadda.detector

import com.pothole.khadda.model.AlgorithmType
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests validating the 4 detection algorithms defined in Mednis et al. (2011)
 * and the project Software Requirements Specification (SRS REQ-3 to REQ-11).
 */
class PotholeDetectorTest {

    private lateinit var detector: PotholeDetector
    private lateinit var processor: DataProcessor

    @Before
    fun setUp() {
        detector = PotholeDetector(
            zDiffThreshold = 1.96,    // 0.2g
            zThreshThreshold = 3.92,  // 0.4g
            stdevThreshold = 1.96,    // 0.2g
            gZeroThreshold = 7.84     // 0.8g
        )
        processor = DataProcessor(filterWindow = 20)
    }

    @Test
    fun testZDiffAlgorithm_triggersWhenDifferenceExceedsThreshold() {
        // Reference threshold is 0.2g ~ 1.96 m/s^2
        assertTrue(detector.isZDiffTriggered(2.5))
        assertFalse(detector.isZDiffTriggered(1.0))
        assertTrue(detector.isPothole(2.1))
    }

    @Test
    fun testZThreshAlgorithm_triggersWhenDeviationExceedsThreshold() {
        // Reference threshold is 0.4g ~ 3.92 m/s^2
        assertTrue(detector.isZThreshTriggered(4.5))
        assertFalse(detector.isZThreshTriggered(2.0))
    }

    @Test
    fun testStdevZAlgorithm_triggersWhenRollingStdDevExceedsThreshold() {
        // Reference threshold is 0.2g ~ 1.96 m/s^2
        assertTrue(detector.isStdevTriggered(2.2))
        assertFalse(detector.isStdevTriggered(0.8))
    }

    @Test
    fun testGZeroAlgorithm_triggersWhenTotalMagnitudeBelowThreshold() {
        // Reference threshold is 0.8g ~ 7.84 m/s^2 (drop / free fall into pothole)
        assertTrue(detector.isGZeroTriggered(5.5))
        assertFalse(detector.isGZeroTriggered(9.81))
    }

    @Test
    fun testFullFeatureDetection_returnsCorrectPrimaryAlgorithmAndSeverity() {
        val features = FeatureSet(
            zDiff = 6.5,
            zThreshDiff = 6.2,
            stdevZ = 2.4,
            accelMagnitude = 14.5,
            horizontalAccel = 1.0,
            gyroMagnitude = 0.2,
            meanZ = 12.0
        )

        val result = detector.detect(features)
        assertNotNull(result)
        assertTrue(result!!.isDetected)
        assertEquals(AlgorithmType.Z_DIFF, result.primaryAlgorithm)
        assertEquals(SeverityLevel.HIGH, result.severity)
    }

    @Test
    fun testSeverityClassification() {
        assertEquals(SeverityLevel.HIGH, PotholeEvent.calculateSeverity(7.5))
        assertEquals(SeverityLevel.MEDIUM, PotholeEvent.calculateSeverity(4.2))
        assertEquals(SeverityLevel.LOW, PotholeEvent.calculateSeverity(1.8))
    }
}
