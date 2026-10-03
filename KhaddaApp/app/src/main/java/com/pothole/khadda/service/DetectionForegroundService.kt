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

    private var potholeCount = 0

    private var lastSpeed = 0f
    private var lastLocationTime = 0L
    private var currentGpsAccel = 0f
    
    private val frameBuffer = ArrayDeque<Pair<Long, Bitmap>>()
    private var lastFrameTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null

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
        onLiveSensorData?.invoke(data)

        // 1. Extract signal features and update rolling window
        val features = dataProcessor.extractFeatures(data, currentGpsAccel)

        // 2. Evaluate algorithms (Z-DIFF, Z-THRESH, STDEV(Z), G-ZERO)
        val result = detector.detect(features) ?: return

        // 3. Obtain location
        val (lat, lon) = if (isSimulationMode) {
            val loc = locationTracker.currentLocation
            Pair(loc?.latitude ?: 18.5204, loc?.longitude ?: 73.8567)
        } else {
            val loc = locationTracker.currentLocation
            Pair(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        }

        // 4. False-positive validation and duplicate suppression
        val validation = falsePositiveFilter.validateTrigger(features, lat, lon)
        if (!validation.isValid) return

        // 5. Confirmed Pothole Event creation
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
        val speedMps = lastSpeed
        var lookbackS = if (speedMps > 0.1f) 8f / speedMps else 2f
        lookbackS = lookbackS.coerceIn(0.3f, 2f) + 0.1f // add latency

        val eventTimeNs = data.timestampNs
        val targetTimeNs = eventTimeNs - (lookbackS * 1_000_000_000L).toLong()

        var bestFrame: Bitmap? = null
        synchronized(frameBuffer) {
            bestFrame = frameBuffer.minByOrNull { abs(it.first - targetTimeNs) }?.second
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


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 320))
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
                        if (frameBuffer.size > 15) {
                            frameBuffer.removeFirst()
                        }
                    }
                }
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
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
            true
        } else {
            locationTracker.startLocationUpdates()
            startCamera()
            sensorCollector.startSensors()
        }

        isDetectionActive = started
        return started
    }

    fun stopDetection(): DetectionSession? {
        if (!isDetectionActive) return currentSession

        isDetectionActive = false
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
