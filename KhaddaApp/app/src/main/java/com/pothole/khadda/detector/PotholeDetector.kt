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
    var zDiffThreshold: Double = 5.0,     // Increased for bike mount
    var zThreshThreshold: Double = 3.92,  
    var stdevThreshold: Double = 5.0,     // Increased for bike mount
    var gZeroThreshold: Double = 2.5,     // Reduced from 7.84 to ~0.25g
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

    private var gZeroCounter: Int = 0

    /**
     * G-ZERO Algorithm: Detects temporary free-fall condition when the wheel drops into a cavity.
     * Must hold for at least 3 consecutive samples to reject pure noise.
     */
    fun isGZeroTriggered(accelMagnitude: Double): Boolean {
        if (!isGZeroEnabled) return false
        if (accelMagnitude <= gZeroThreshold) {
            gZeroCounter++
        } else {
            gZeroCounter = 0
        }
        return gZeroCounter >= 3
    }

    /**
     * Evaluates all enabled algorithms against the extracted features.
     */
    fun getTriggeredAlgorithms(features: FeatureSet): List<AlgorithmType> {
        val triggered = mutableListOf<AlgorithmType>()
        if (isZDiffTriggered(features.zDiff)) triggered.add(AlgorithmType.Z_DIFF)
        if (isZThreshTriggered(features.zThreshDiff)) triggered.add(AlgorithmType.Z_THRESH)
        if (isStdevTriggered(features.stdevZ)) triggered.add(AlgorithmType.STDEV_Z)
        if (isGZeroTriggered(features.accelMagnitude)) triggered.add(AlgorithmType.G_ZERO)
        return triggered
    }

    fun buildResult(features: FeatureSet, triggered: List<AlgorithmType>): DetectionResult {
        val primary = when {
            triggered.contains(AlgorithmType.Z_DIFF) -> AlgorithmType.Z_DIFF
            triggered.contains(AlgorithmType.Z_THRESH) -> AlgorithmType.Z_THRESH
            triggered.contains(AlgorithmType.G_ZERO) -> AlgorithmType.G_ZERO
            else -> AlgorithmType.STDEV_Z
        }
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
