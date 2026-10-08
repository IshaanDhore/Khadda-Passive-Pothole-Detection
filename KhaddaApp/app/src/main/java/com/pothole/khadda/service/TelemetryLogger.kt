package com.pothole.khadda.service

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentLinkedQueue

class TelemetryLogger(private val context: Context) {

    private var sensorWriter: FileWriter? = null
    private var candidateWriter: FileWriter? = null
    private var gpsWriter: FileWriter? = null
    
    private val sensorQueue = ConcurrentLinkedQueue<String>()
    private val candidateQueue = ConcurrentLinkedQueue<String>()
    private val gpsQueue = ConcurrentLinkedQueue<String>()
    
    private var isLogging = false
    private var flushJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Keep track of pending labels
    private var pendingLabel: String = ""

    fun startLogging(sessionId: String, metadata: String) {
        try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "KhaddaLogs")
            if (!dir.exists()) dir.mkdirs()

            // Save metadata
            val metaFile = File(dir, "metadata_$sessionId.txt")
            metaFile.writeText(metadata)

            val sensorFile = File(dir, "sensor_log_$sessionId.csv")
            sensorWriter = FileWriter(sensorFile, true)
            sensorWriter?.append("timestampNs,vertAccel,smoothedVert,accelMag,zDiff,stdevZ,zThreshDiff,yaw,roll,pitch,speedMps,speedAgeMs,rawAccelX,rawAccelY,rawAccelZ,gravUnitX,gravUnitY,gravUnitZ,linAccelX,linAccelY,linAccelZ,gyroX,gyroY,gyroZ,label\n")

            val candidateFile = File(dir, "candidate_log_$sessionId.csv")
            candidateWriter = FileWriter(candidateFile, true)
            candidateWriter?.append("timestampNs,latitude,longitude,speed,yaw,zDiff,stdevZ,zThreshDiff,algZDiff,algZThresh,algStdev,algGZero,voteCount,rejectedReason\n")

            val gpsFile = File(dir, "gps_log_$sessionId.csv")
            gpsWriter = FileWriter(gpsFile, true)
            gpsWriter?.append("timestampNs,latitude,longitude,speed,accuracy,bearing\n")

            isLogging = true
            
            flushJob = scope.launch {
                while (isActive && isLogging) {
                    delay(1500) // Flush every 1.5 seconds
                    flushQueues()
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            isLogging = false
        }
    }

    fun markNextRowWithLabel(label: String) {
        pendingLabel = label
    }

    fun logSensor(
        timestampNs: Long,
        vertAccel: Double, smoothedVert: Double, accelMag: Double,
        zDiff: Double, stdevZ: Double, zThreshDiff: Double,
        yaw: Double, roll: Double, pitch: Double,
        speedMps: Float, speedAgeMs: Long,
        rawAccelX: Float, rawAccelY: Float, rawAccelZ: Float,
        gravX: Float, gravY: Float, gravZ: Float,
        linAccelX: Float, linAccelY: Float, linAccelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float
    ) {
        if (!isLogging) return
        val currentLabel = pendingLabel
        pendingLabel = "" // Consume label
        val row = "$timestampNs,$vertAccel,$smoothedVert,$accelMag,$zDiff,$stdevZ,$zThreshDiff,$yaw,$roll,$pitch,$speedMps,$speedAgeMs,$rawAccelX,$rawAccelY,$rawAccelZ,$gravX,$gravY,$gravZ,$linAccelX,$linAccelY,$linAccelZ,$gyroX,$gyroY,$gyroZ,$currentLabel\n"
        sensorQueue.offer(row)
    }

    fun logCandidate(
        timestampNs: Long, lat: Double, lon: Double, speed: Float, yaw: Double,
        zDiff: Double, stdevZ: Double, zThreshDiff: Double,
        algZDiff: Boolean, algZThresh: Boolean, algStdev: Boolean, algGZero: Boolean,
        voteCount: Int, rejectedReason: String
    ) {
        if (!isLogging) return
        val lats = if (lat == 0.0) "NaN" else lat.toString()
        val lons = if (lon == 0.0) "NaN" else lon.toString()
        candidateQueue.offer("$timestampNs,$lats,$lons,$speed,$yaw,$zDiff,$stdevZ,$zThreshDiff,$algZDiff,$algZThresh,$algStdev,$algGZero,$voteCount,\"$rejectedReason\"\n")
    }

    fun logGps(
        timestampNs: Long, lat: Double, lon: Double,
        speed: Float, accuracy: Float, bearing: Float
    ) {
        if (!isLogging) return
        val lats = if (lat == 0.0) "NaN" else lat.toString()
        val lons = if (lon == 0.0) "NaN" else lon.toString()
        gpsQueue.offer("$timestampNs,$lats,$lons,$speed,$accuracy,$bearing\n")
    }

    private fun flushQueues() {
        try {
            var sensorRows = 0
            while (sensorQueue.isNotEmpty() && sensorRows < 500) {
                sensorQueue.poll()?.let { sensorWriter?.append(it) }
                sensorRows++
            }
            if (sensorRows > 0) sensorWriter?.flush()

            var candidateRows = 0
            while (candidateQueue.isNotEmpty() && candidateRows < 50) {
                candidateQueue.poll()?.let { candidateWriter?.append(it) }
                candidateRows++
            }
            if (candidateRows > 0) candidateWriter?.flush()

            var gpsRows = 0
            while (gpsQueue.isNotEmpty() && gpsRows < 50) {
                gpsQueue.poll()?.let { gpsWriter?.append(it) }
                gpsRows++
            }
            if (gpsRows > 0) gpsWriter?.flush()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLogging() {
        isLogging = false
        flushJob?.cancel()
        scope.launch {
            // Final flush
            flushQueues()
            try {
                sensorWriter?.close()
                candidateWriter?.close()
                gpsWriter?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
