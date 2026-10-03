package com.pothole.khadda.detector

import com.pothole.khadda.model.AlgorithmType
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel

/**
 * Result of algorithm evaluation on a sensor sample.
 */
data class DetectionResult(
    val isDetected: Boolean,
    val primaryAlgorithm: AlgorithmType,
    val triggeredAlgorithms: List<AlgorithmType>,
    val impactMagnitude: Double,
    val severity: SeverityLevel
)

/**
 * Core Pothole Detection Engine implementing the 4 algorithms from Mednis et al. (2011).
 * Matches UML Class Diagram: PotholeDetector.
 */
class PotholeDetector(
    var zDiffThreshold: Double = 1.96,    // 0.2g in m/s^2 (SRS REQ-6)
    var zThreshThreshold: Double = 3.92,  // 0.4g in m/s^2 (SRS REQ-4)
    var stdevThreshold: Double = 1.96,    // 0.2g in m/s^2 (SRS REQ-9)
    var gZeroThreshold: Double = 7.84,    // 0.8g in m/s^2 (SRS REQ-11)
    var isZDiffEnabled: Boolean = true,
    var isZThreshEnabled: Boolean = true,
    var isStdevEnabled: Boolean = true,
    var isGZeroEnabled: Boolean = true
) {

    /**
     * Z-DIFF Algorithm: Detects sudden rapid change between consecutive vertical readings.
     */
    fun isZDiffTriggered(zDiff: Double): Boolean {
        return isZDiffEnabled && zDiff >= zDiffThreshold
    }

    /**
     * Z-THRESH Algorithm: Detects vertical acceleration deviation from gravity (1g) exceeding threshold.
     */
    fun isZThreshTriggered(zThreshDiff: Double): Boolean {
        return isZThreshEnabled && zThreshDiff >= zThreshThreshold
    }

    /**
     * STDEV(Z) Algorithm: Detects elevated variance/standard deviation in vertical acceleration.
     */
    fun isStdevTriggered(stdevZ: Double): Boolean {
        return isStdevEnabled && stdevZ >= stdevThreshold
    }

    /**
     * G-ZERO Algorithm: Detects temporary free-fall condition when the wheel drops into a cavity.
     */
    fun isGZeroTriggered(accelMagnitude: Double): Boolean {
        return isGZeroEnabled && accelMagnitude <= gZeroThreshold
    }

    /**
     * Evaluates all enabled algorithms against the extracted features.
     */
    fun detect(features: FeatureSet): DetectionResult? {
        val triggered = mutableListOf<AlgorithmType>()

        if (isZDiffTriggered(features.zDiff)) {
            triggered.add(AlgorithmType.Z_DIFF)
        }
        if (isZThreshTriggered(features.zThreshDiff)) {
            triggered.add(AlgorithmType.Z_THRESH)
        }
        if (isStdevTriggered(features.stdevZ)) {
            triggered.add(AlgorithmType.STDEV_Z)
        }
        if (isGZeroTriggered(features.accelMagnitude)) {
            triggered.add(AlgorithmType.G_ZERO)
        }

        if (triggered.isEmpty()) {
            return null
        }

        // Determine highest-priority primary algorithm
        val primary = when {
            triggered.contains(AlgorithmType.Z_DIFF) -> AlgorithmType.Z_DIFF
            triggered.contains(AlgorithmType.Z_THRESH) -> AlgorithmType.Z_THRESH
            triggered.contains(AlgorithmType.G_ZERO) -> AlgorithmType.G_ZERO
            else -> AlgorithmType.STDEV_Z
        }

        // Determine severity using z-diff or z-thresh deviation
        val impact = maxOf(features.zDiff, features.zThreshDiff)
        val severity = PotholeEvent.calculateSeverity(impact)

        return DetectionResult(
            isDetected = true,
            primaryAlgorithm = primary,
            triggeredAlgorithms = triggered,
            impactMagnitude = impact,
            severity = severity
        )
    }

    /**
     * Helper to test if a raw zDiff value exceeds threshold.
     * Matches UML method signature: isPothole(zDiff: double): boolean.
     */
    fun isPothole(zDiff: Double): Boolean {
        return zDiff >= zDiffThreshold
    }
}
