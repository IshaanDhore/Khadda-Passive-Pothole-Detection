package com.pothole.khadda.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import androidx.lifecycle.LifecycleService
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.util.Size
import java.util.ArrayDeque
import kotlin.math.abs
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pothole.khadda.KhaddaApplication
import com.pothole.khadda.detector.*
import com.pothole.khadda.location.LocationTracker
import com.pothole.khadda.model.*
import com.pothole.khadda.sensor.SensorCollector
import com.pothole.khadda.simulation.RoadSimulationManager
import com.pothole.khadda.ui.MainActivity
import com.pothole.khadda.utils.AlertHelper
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Foreground Service that keeps accelerometer and GPS sensing active continuously
 * even when the phone screen is locked or the user navigates to other apps.
 */
class DetectionForegroundService : LifecycleService() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var sensorCollector: SensorCollector
    private lateinit var locationTracker: LocationTracker
    private lateinit var dataProcessor: DataProcessor
    private lateinit var detector: PotholeDetector
    private lateinit var falsePositiveFilter: FalsePositiveFilter
    private lateinit var visionAnalyzer: PotholeVisionAnalyzer
    private lateinit var alertHelper: AlertHelper
    private lateinit var simulationManager: RoadSimulationManager

    var currentSession: DetectionSession? = null
        private set

    var isDetectionActive: Boolean = false
        private set

    var isSimulationMode: Boolean = false

    // Callbacks for UI subscribers
    var onLiveSensorData: ((SensorData) -> Unit)? = null
    var onPotholeDetected: ((PotholeEvent) -> Unit)? = null
    var onLocationUpdated: ((Double, Double, Float) -> Unit)? = null

    private lateinit var telemetryLogger: TelemetryLogger
    
    private var potholeCount = 0

    private var lastSpeed = 0f
    private var lastLocationTime = 0L
    private var currentGpsAccel = 0f
    
    private val frameBuffer = ArrayDeque<Pair<Long, Bitmap>>()
    private var lastFrameTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: androidx.camera.core.CameraControl? = null
    
    var isTorchEnabled = true

    inner class LocalBinder : Binder() {
        fun getService(): DetectionForegroundService = this@DetectionForegroundService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sensorCollector = SensorCollector(this)
        locationTracker = LocationTracker(this)
        dataProcessor = DataProcessor()
        detector = PotholeDetector()
        falsePositiveFilter = FalsePositiveFilter()
        alertHelper = AlertHelper(this)
        simulationManager = RoadSimulationManager()
        telemetryLogger = TelemetryLogger(this)
        visionAnalyzer = PotholeVisionAnalyzer(this)

        setupListeners()
    }

    private fun setupListeners() {
        // Hardware sensor listener
        sensorCollector.onSensorDataListener = { data ->
            if (isDetectionActive && !isSimulationMode) {
                processSensorSample(data)
            }
        }

        // Hardware GPS listener
        locationTracker.onLocationChangedListener = { loc ->
            val now = System.currentTimeMillis()
            if (lastLocationTime != 0L) {
                val dt = (now - lastLocationTime) / 1000f
                if (dt > 0f) {
                    currentGpsAccel = (loc.speed - lastSpeed) / dt
                }
            }
            lastSpeed = loc.speed
            lastLocationTime = now
            
            // Log GPS fixes separately
            val elapsedNanos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                loc.elapsedRealtimeNanos
            } else {
                android.os.SystemClock.elapsedRealtimeNanos()
            }
            telemetryLogger.logGps(
                elapsedNanos, loc.latitude, loc.longitude, loc.speed, loc.accuracy, loc.bearing
            )

            onLocationUpdated?.invoke(loc.latitude, loc.longitude, loc.speed * 3.6f)
        }

        // Simulation listeners
        simulationManager.onSimulatedSensorData = { data ->
            if (isDetectionActive && isSimulationMode) {
                processSensorSample(data)
            }
        }

        simulationManager.onSimulatedLocation = { lat, lon, speed ->
            onLocationUpdated?.invoke(lat, lon, speed)
        }
    }

    private fun processSensorSample(data: SensorData) {
        val timeSinceStart = System.currentTimeMillis() - (currentSession?.startTime ?: 0L)
        if (timeSinceStart < 1000L) return // Drop first second to let gravity stabilize
        
        onLiveSensorData?.invoke(data)

        // 1. Extract signal features and update rolling window
        val features = dataProcessor.extractFeatures(data, currentGpsAccel)

        // Log continuous telemetry
        val speedAgeMs = System.currentTimeMillis() - lastLocationTime
        telemetryLogger.logSensor(
            data.timestampNs, features.vertAccel, features.meanZ, features.accelMagnitude,
            features.zDiff, features.stdevZ, features.zThreshDiff,
            features.yaw, features.roll, features.pitch, lastSpeed, speedAgeMs,
            data.accelX.toFloat(), data.accelY.toFloat(), data.accelZ.toFloat(),
            data.gravX.toFloat(), data.gravY.toFloat(), data.gravZ.toFloat(),
            data.linX.toFloat(), data.linY.toFloat(), data.linZ.toFloat(),
            data.gyroX.toFloat(), data.gyroY.toFloat(), data.gyroZ.toFloat()
        )

        // 1.5 Obtain location early for logging
        val (lat, lon) = if (isSimulationMode) {
            val loc = locationTracker.currentLocation
            Pair(loc?.latitude ?: 18.5204, loc?.longitude ?: 73.8567)
        } else {
            val loc = locationTracker.currentLocation
            Pair(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        }

        // 2. Evaluate algorithms
        val triggered = detector.getTriggeredAlgorithms(features)
        val isCandidate = triggered.isNotEmpty() || features.zDiff >= (detector.zDiffThreshold / 2.0)

        if (isCandidate) {
            var rejectedReason = ""
            var isValid = true

            // Require 2 votes
            if (triggered.size < 2) {
                isValid = false
                rejectedReason = "Failed 2-vote rule (Got ${triggered.size})"
            } else {
                // False-positive validation
                val validation = falsePositiveFilter.validateTrigger(features, lat, lon, lastSpeed)
                if (!validation.isValid) {
                    isValid = false
                    rejectedReason = validation.reason
                } else {
                    // Passed accelerometer false-positive filter. Now run Hybrid Vision AI!
                    // Extract ALL frames from the last 3 seconds
                    val framesToAnalyze = mutableListOf<Bitmap>()
                    val eventTimeNs = data.timestampNs
                    val threeSecondsNs = 3_000_000_000L
                    
                    synchronized(frameBuffer) {
                        // Grab frames that are within 3 seconds prior to the event
                        for (frame in frameBuffer) {
                            val ageNs = eventTimeNs - frame.first
                            if (ageNs in 0..threeSecondsNs) {
                                framesToAnalyze.add(frame.second)
                            }
                        }
                    }

                    var isVisuallyConfirmed = false
                    
                    if (framesToAnalyze.isNotEmpty()) {
                        // Only analyze the 3 most recent frames to prevent motion-blur false positives and memory spikes
                        val framesToProcess = framesToAnalyze.reversed().take(3)
                        for ((index, frame) in framesToProcess.withIndex()) {
                            android.util.Log.d("HybridSystem", "Analyzing frame ${index + 1}/${framesToProcess.size} for visual confirmation...")
                            if (visionAnalyzer.validatePothole(frame)) {
                                isVisuallyConfirmed = true
                                break // Stop analyzing once we find a pothole!
                            }
                        }
                    } else {
                        android.util.Log.w("HybridSystem", "Frame buffer was empty! Skipping vision validation.")
                    }

                    if (!isVisuallyConfirmed) {
                        isValid = false
                        rejectedReason = "Filtered: Vision YOLO Rejected (No pothole found in the 3 most recent photos)"
                        
                        // Save the most recent rejected frame for debugging purposes
                        val rejectedFrame = framesToAnalyze.lastOrNull() ?: frameBuffer.lastOrNull()?.second
                        rejectedFrame?.let {
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    val file = File(applicationContext.filesDir, "rejected_pothole_${System.currentTimeMillis()}.jpg")
                                    val out = FileOutputStream(file)
                                    it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    out.flush()
                                    out.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }

            // val hasValidFix = (speedAgeMs < 2000L) && (locationTracker.accuracy < 20f) && (lat != 0.0)
            // if (!hasValidFix && isValid) {
            //     isValid = false
            //     rejectedReason = "Filtered: No recent GPS fix (Age: $speedAgeMs, Acc: ${locationTracker.accuracy})"
            // }

            telemetryLogger.logCandidate(
                data.timestampNs, lat, lon, lastSpeed, features.yaw,
                features.zDiff, features.stdevZ, features.zThreshDiff,
                triggered.contains(AlgorithmType.Z_DIFF),
                triggered.contains(AlgorithmType.Z_THRESH),
                triggered.contains(AlgorithmType.STDEV_Z),
                triggered.contains(AlgorithmType.G_ZERO),
                triggered.size, rejectedReason
            )

            if (!isValid) return
        } else {
            return
        }
        
        val result = detector.buildResult(features, triggered)

        // 3. Confirmed Pothole Event creation
        potholeCount++
        val session = currentSession
        val event = PotholeEvent(
            eventId = UUID.randomUUID().toString(),
            sessionId = session?.sessionId ?: "",
            timestamp = System.currentTimeMillis(),
            latitude = lat,
            longitude = lon,
            zDiffValue = result.impactMagnitude,
            severity = result.severity,
            algorithm = result.primaryAlgorithm.displayName,
            status = RepairStatus.REPORTED,
            syncStatus = "QUEUED"
        )

        falsePositiveFilter.recordConfirmedEvent(event)
        alertHelper.triggerAlert(event.severity)
        updateNotification("Potholes Detected: $potholeCount (Last: ${event.severity} at ${event.algorithm})")

        // 6. Persist to Room SQLite Database
        serviceScope.launch {
            val repo = (application as KhaddaApplication).repository
            repo.insertEvent(event)
            session?.let {
                it.totalEvents = potholeCount
                repo.updateSession(it)
            }
        }

        onPotholeDetected?.invoke(event)

        // 7. Save camera frame closest to eventTime - lookback
        val speedMps2 = lastSpeed
        var lookbackS2 = if (speedMps2 > 0.1f) 8f / speedMps2 else 2f
        lookbackS2 = lookbackS2.coerceIn(0.3f, 2f) + 0.1f // add latency

        val eventTimeNs = data.timestampNs
        val targetTimeNs2 = eventTimeNs - (lookbackS2 * 1_000_000_000L).toLong()

        var finalBestFrame: Bitmap? = null
        synchronized(frameBuffer) {
            finalBestFrame = frameBuffer.minByOrNull { abs(it.first - targetTimeNs2) }?.second
        }

        finalBestFrame?.let {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val file = File(applicationContext.filesDir, "pothole_${event.eventId}.jpg")
                    val out = FileOutputStream(file)
                    it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastFrameTime >= 200) {
                    lastFrameTime = now
                    val bitmap = imageProxy.toBitmap()
                    val timestampNs = imageProxy.imageInfo.timestamp
                    
                    synchronized(frameBuffer) {
                        frameBuffer.addLast(Pair(timestampNs, bitmap))
                        // Keep 25 frames (about 5 seconds of history at 5 FPS)
                        if (frameBuffer.size > 25) {
                            frameBuffer.removeFirst()
                        }
                    }
                }
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                val camera = cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
                cameraControl = camera?.cameraControl
                cameraControl?.enableTorch(isTorchEnabled)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun toggleTorch(enable: Boolean) {
        isTorchEnabled = enable
        cameraControl?.enableTorch(enable)
    }

    fun triggerManualPothole(eventType: String = "MANUAL_TRIGGER", forceAiResult: Boolean? = null) {
        telemetryLogger.markNextRowWithLabel(eventType)
        
        val lat = locationTracker.currentLocation?.latitude ?: 0.0
        val lon = locationTracker.currentLocation?.longitude ?: 0.0
        val session = currentSession

        val framesToAnalyze = mutableListOf<Bitmap>()
        val eventTimeNs = android.os.SystemClock.elapsedRealtimeNanos()
        val threeSecondsNs = 3_000_000_000L
        
        synchronized(frameBuffer) {
            for (frame in frameBuffer) {
                val ageNs = eventTimeNs - frame.first
                if (ageNs in 0..threeSecondsNs) {
                    framesToAnalyze.add(frame.second)
                }
            }
        }

        var aiConfirmed = forceAiResult ?: false
        if (forceAiResult == null && framesToAnalyze.isNotEmpty()) {
            for ((index, frame) in framesToAnalyze.reversed().withIndex()) {
                android.util.Log.d("HybridSystem", "[Manual Test] Analyzing frame ${index + 1}/${framesToAnalyze.size} for visual confirmation...")
                if (visionAnalyzer.validatePothole(frame)) {
                    aiConfirmed = true
                    break
                }
            }
        }

        val eventIdStr = UUID.randomUUID().toString()

        if (aiConfirmed) {
            android.util.Log.d("HybridSystem", "✅ [Manual Test] AI SUCCESSFULLY DETECTED POTHOLE! (Or Forced)")
        } else {
            android.util.Log.e("HybridSystem", "❌ [Manual Test] AI FAILED TO DETECT POTHOLE! (Or Forced Reject)")
            
            // Save the rejected frame silently and abort!
            var bestFrame: Bitmap? = null
            synchronized(frameBuffer) { bestFrame = frameBuffer.lastOrNull()?.second }
            bestFrame?.let {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val file = File(applicationContext.filesDir, "rejected_pothole_$eventIdStr.jpg")
                        val out = FileOutputStream(file)
                        it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.flush()
                        out.close()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            return // Abort! Don't create an event
        }

        potholeCount++
        val event = PotholeEvent(
            eventId = eventIdStr,
            sessionId = session?.sessionId ?: "",
            timestamp = System.currentTimeMillis(),
            latitude = lat,
            longitude = lon,
            zDiffValue = 9.99, // Fake high impact for manual trigger
            severity = SeverityLevel.HIGH,
            algorithm = "MANUAL (AI Confirmed)",
            status = RepairStatus.REPORTED,
            syncStatus = "QUEUED"
        )

        falsePositiveFilter.recordConfirmedEvent(event)
        alertHelper.triggerAlert(event.severity)
        updateNotification("Potholes Detected: $potholeCount (Last: MANUAL)")

        serviceScope.launch {
            val repo = (application as KhaddaApplication).repository
            repo.insertEvent(event)
            session?.let {
                it.totalEvents = potholeCount
                repo.updateSession(it)
            }
        }
        onPotholeDetected?.invoke(event)

        // Save the accepted frame
        var bestFrame: Bitmap? = null
        synchronized(frameBuffer) {
            bestFrame = frameBuffer.lastOrNull()?.second
        }
        bestFrame?.let {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val file = File(applicationContext.filesDir, "pothole_${event.eventId}.jpg")
                    val out = FileOutputStream(file)
                    it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun startDetection(useSimulation: Boolean = false): Boolean {
        if (isDetectionActive) return true

        isSimulationMode = useSimulation
        potholeCount = 0
        dataProcessor.reset()
        falsePositiveFilter.reset()

        val newSession = DetectionSession(
            sessionId = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis(),
            status = SessionStatus.RUNNING
        )
        currentSession = newSession

        serviceScope.launch {
            (application as KhaddaApplication).repository.insertSession(newSession)
        }

        startForeground(NOTIFICATION_ID, buildNotification("Pothole Detection Active - Monitoring Road..."))

        val started = if (isSimulationMode) {
            simulationManager.startSimulation(serviceScope)
            telemetryLogger.startLogging(newSession.sessionId, "Simulation: true")
            true
        } else {
            locationTracker.startLocationUpdates()
            startCamera()
            val success = sensorCollector.startSensors()
            if (success) {
                val meta = """
                    Simulation: false
                    Vehicle: Bike
                    Mount: Handlebar
                    Phone: ${android.os.Build.MODEL}
                    Android: ${android.os.Build.VERSION.RELEASE}
                    StartTime: ${java.util.Date()}
                    Thresholds: ZDiff=${detector.zDiffThreshold}, Stdev=${detector.stdevThreshold}, ZThresh=${detector.zThreshThreshold}, GZero=${detector.gZeroThreshold}
                """.trimIndent()
                telemetryLogger.startLogging(newSession.sessionId, meta)
            }
            success
        }

        isDetectionActive = started
        return started
    }

    fun stopDetection(): DetectionSession? {
        if (!isDetectionActive) return currentSession

        isDetectionActive = false
        telemetryLogger.stopLogging()
        sensorCollector.stopSensors()
        locationTracker.stopLocationUpdates()
        simulationManager.stopSimulation()
        cameraProvider?.unbindAll()

        val session = currentSession
        if (session != null) {
            session.endTime = System.currentTimeMillis()
            session.status = SessionStatus.STOPPED
            session.totalEvents = potholeCount

            serviceScope.launch {
                (application as KhaddaApplication).repository.closeSession(session.sessionId, potholeCount)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return session
    }

    fun getDetector(): PotholeDetector = detector
    fun getAlertHelper(): AlertHelper = alertHelper

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pothole Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notification for real-time pothole sensing"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Khadda Pothole Detection")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
        alertHelper.release()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "khadda_detection_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
