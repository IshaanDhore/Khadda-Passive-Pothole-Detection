package com.pothole.khadda.detector

import com.pothole.khadda.model.SensorData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Feature set extracted from sensor sample windows.
 */
data class FeatureSet(
    val zDiff: Double,
    val zThreshDiff: Double,
    val stdevZ: Double,
    val accelMagnitude: Double,
    val horizontalAccel: Double,
    val gyroMagnitude: Double,
    val meanZ: Double,
    val roll: Double,
    val pitch: Double,
    val yaw: Double,
    val vertAccel: Double
)

/**
 * DataProcessor performs noise filtering, rolling window calculations, and feature extraction.
 * Matches UML Class Diagram: DataProcessor.
 */
class DataProcessor(
    var filterWindow: Int = 20,
    var zDiffThreshold: Double = 1.96 // ~0.2g in m/s^2
) {
    private val zWindow = ArrayDeque<Double>(filterWindow + 5)
    
    // Smoothing buffers for vertical acceleration
    private val vertWindow = ArrayDeque<Double>(5)
    private val smoothedVertHistory = ArrayDeque<Double>(10)

    // Gyroscope tracking variables
    private var fwdAccum = floatArrayOf(0f, 1f, 0f) // initial guess

    private fun dot(a: FloatArray, b: FloatArray) = a[0]*b[0] + a[1]*b[1] + a[2]*b[2]
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0])
    private fun norm(a: FloatArray): FloatArray {
        val m = Math.sqrt(dot(a, a).toDouble()).toFloat()
        if (m == 0f) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(a[0]/m, a[1]/m, a[2]/m)
    }

    // calculateZDiff is now computed using smoothed history inline

    /**
     * Adds sample to rolling window and calculates standard deviation.
     */
    fun updateRollingWindow(zValue: Double): Double {
        zWindow.addLast(zValue)
        while (zWindow.size > filterWindow) {
            zWindow.removeFirst()
        }

        if (zWindow.size < 3) return 0.0

        val mean = zWindow.average()
        val sumSquaredDiffs = zWindow.fold(0.0) { acc, z ->
            acc + (z - mean) * (z - mean)
        }
        return sqrt(sumSquaredDiffs / zWindow.size)
    }

    fun extractFeatures(data: SensorData, gpsAccel: Float = 0f): FeatureSet {
        // 1. Calculate precise gravity up-vector
        val gravity = floatArrayOf(data.gravX.toFloat(), data.gravY.toFloat(), data.gravZ.toFloat())
        val up = norm(gravity)
        
        // 2. Project linear acceleration onto gravity to get pure vertical acceleration
        val lin = floatArrayOf(data.linX.toFloat(), data.linY.toFloat(), data.linZ.toFloat())
        val vertAccel = dot(lin, up).toDouble()

        // 3. Smooth vertical acceleration (5-sample moving average)
        vertWindow.addLast(vertAccel)
        if (vertWindow.size > 5) vertWindow.removeFirst()
        val smoothedVert = if (vertWindow.isNotEmpty()) vertWindow.average() else 0.0

        // 4. Compute Z-DIFF over a 100ms span (10 samples at 100Hz)
        smoothedVertHistory.addLast(smoothedVert)
        if (smoothedVertHistory.size > 10) smoothedVertHistory.removeFirst()
        val zDiff = if (smoothedVertHistory.size == 10) {
            abs(smoothedVert - smoothedVertHistory.first())
        } else 0.0

        // 5. Update other features using the projected vertical acceleration
        val stdev = updateRollingWindow(vertAccel)
        // Z-THRESH measures absolute distance from 0 (since gravity is removed)
        val zThreshDiff = abs(smoothedVert) 
        val accelMag = data.getAccelMagnitude()
        val horizAccel = data.getHorizontalAccel()
        val gyroMag = data.getGyroMagnitude()
        val meanZ = if (zWindow.isNotEmpty()) zWindow.average() else vertAccel

        // Compute advanced gyroscope orientation features
        val gyro = floatArrayOf(data.gyroX.toFloat(), data.gyroY.toFloat(), data.gyroZ.toFloat())
        val yaw = dot(gyro, up)

        if (Math.abs(gpsAccel) > 1.5f) {
            val dotLinUp = dot(lin, up)
            val h = floatArrayOf(
                lin[0] - dotLinUp * up[0],
                lin[1] - dotLinUp * up[1],
                lin[2] - dotLinUp * up[2]
            )
            val s = Math.signum(gpsAccel)
            for (i in 0..2) fwdAccum[i] += s * h[i]
        }
        val forward = norm(fwdAccum)
        val lateral = cross(up, forward)
        val roll = dot(gyro, forward)
        val pitch = dot(gyro, lateral)

        return FeatureSet(
            zDiff = zDiff,
            zThreshDiff = zThreshDiff,
            stdevZ = stdev,
            accelMagnitude = accelMag,
            horizontalAccel = horizAccel,
            gyroMagnitude = gyroMag,
            meanZ = meanZ,
            roll = roll.toDouble(),
            pitch = pitch.toDouble(),
            yaw = yaw.toDouble(),
            vertAccel = vertAccel
        )
    }

    fun reset() {
        zWindow.clear()
        vertWindow.clear()
        smoothedVertHistory.clear()
        fwdAccum = floatArrayOf(0f, 1f, 0f)
    }
}
