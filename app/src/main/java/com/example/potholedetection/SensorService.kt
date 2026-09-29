package com.example.potholedetection

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var potholeDetector: PotholeDetector
    private lateinit var dbSynchronizer: DatabaseSynchronizer

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        dbSynchronizer = DatabaseSynchronizer()
        potholeDetector = PotholeDetector().apply {
            onPotholeDetected = { timestamp, _, _, algorithm, severity ->
                // Use the cached location
                dbSynchronizer.queueEvent(timestamp, currentLat, currentLng, algorithm, severity)
                
                // Broadcast for UI updates
                val intent = Intent("POTHOLE_EVENT")
                intent.putExtra("algorithm", algorithm)
                intent.putExtra("severity", severity)
                intent.putExtra("lat", currentLat)
                intent.putExtra("lng", currentLng)
                sendBroadcast(intent)
            }
        }
        
        setupLocationTracking()
        startForegroundService()
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun startForegroundService() {
        val channelId = "PotholeServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pothole Detection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pothole Detection Active")
            .setContentText("Monitoring road surface...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Convert to g-force. Standard gravity is 9.80665 m/s^2
            val xG = x / SensorManager.STANDARD_GRAVITY
            val yG = y / SensorManager.STANDARD_GRAVITY
            val zG = z / SensorManager.STANDARD_GRAVITY
            
            potholeDetector.processSample(System.currentTimeMillis(), xG, yG, zG, currentLat, currentLng)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
