package com.example.potholedetection

import kotlin.math.abs
import kotlin.math.sqrt

class PotholeDetector {
    companion object {
        const val Z_THRESH_LIMIT = 0.4f
        const val Z_DIFF_LIMIT = 0.2f
        const val G_ZERO_LIMIT = 0.8f
        const val STDEV_WINDOW_SIZE = 20
        const val STDEV_LIMIT = 0.2f
    }

    private val zWindowBuffer = FloatArray(STDEV_WINDOW_SIZE)
    private var bufferPointer = 0
    private var sampleCount = 0
    private var previousZ = 0.0f
    private var hasPreviousZ = false

    var onPotholeDetected: ((timestamp: Long, lat: Double, lng: Double, algorithm: String, severity: Float) -> Unit)? = null

    fun checkZThresh(z: Float): Boolean {
        return abs(z) >= Z_THRESH_LIMIT
    }

    fun checkZDiff(currentZ: Float): Boolean {
        if (!hasPreviousZ) {
            previousZ = currentZ
            hasPreviousZ = true
            return false
        }
        val diff = abs(currentZ - previousZ)
        previousZ = currentZ
        return diff >= Z_DIFF_LIMIT
    }

    fun checkGZero(x: Float, y: Float, z: Float): Boolean {
        return abs(x) < G_ZERO_LIMIT && abs(y) < G_ZERO_LIMIT && abs(z) < G_ZERO_LIMIT
    }

    fun checkStdevZ(currentZ: Float): Boolean {
        zWindowBuffer[bufferPointer] = currentZ
        bufferPointer = (bufferPointer + 1) % STDEV_WINDOW_SIZE
        if (sampleCount < STDEV_WINDOW_SIZE) {
            sampleCount++
        }

        if (sampleCount < STDEV_WINDOW_SIZE) {
            return false
        }

        var sum = 0.0f
        for (i in 0 until STDEV_WINDOW_SIZE) {
            sum += zWindowBuffer[i]
        }
        val mean = sum / STDEV_WINDOW_SIZE

        var sumSqDiff = 0.0f
        for (i in 0 until STDEV_WINDOW_SIZE) {
            val diff = zWindowBuffer[i] - mean
            sumSqDiff += (diff * diff)
        }
        val variance = sumSqDiff / STDEV_WINDOW_SIZE
        val stdev = sqrt(variance.toDouble()).toFloat()

        return stdev >= STDEV_LIMIT
    }

    fun processSample(timestamp: Long, x: Float, y: Float, z: Float, lat: Double = 0.0, lng: Double = 0.0) {
        val zThreshTriggered = checkZThresh(z)
        val zDiffTriggered = checkZDiff(z)
        val gZeroTriggered = checkGZero(x, y, z)
        val stdevTriggered = checkStdevZ(z)

        if (zThreshTriggered || zDiffTriggered || gZeroTriggered || stdevTriggered) {
            val algorithms = mutableListOf<String>()
            if (zThreshTriggered) algorithms.add("Z-THRESH")
            if (zDiffTriggered) algorithms.add("Z-DIFF")
            if (gZeroTriggered) algorithms.add("G-ZERO")
            if (stdevTriggered) algorithms.add("STDEV(Z)")
            
            val severity = abs(z)
            onPotholeDetected?.invoke(timestamp, lat, lng, algorithms.joinToString(","), severity)
        }
    }
}
