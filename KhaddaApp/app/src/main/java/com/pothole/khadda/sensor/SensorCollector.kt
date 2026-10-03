package com.pothole.khadda.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pothole.khadda.model.SensorData

/**
 * SensorCollector encapsulates Android SensorManager hardware access.
 * Matches UML Class Diagram: SensorManager, Accelerometer, Gyroscope.
 */
class SensorCollector(
    context: Context,
    var samplingRate: Int = 10000 // 10,000 µs (100Hz)
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    var isRecording: Boolean = false
        private set

    var onSensorDataListener: ((SensorData) -> Unit)? = null

    // Latest readings to synchronize
    private var lastAccelX = 0.0
    private var lastAccelY = 0.0
    private var lastAccelZ = 9.81
    private var lastGyroX = 0.0
    private var lastGyroY = 0.0
    private var lastGyroZ = 0.0
    private var lastGravX = 0.0
    private var lastGravY = 0.0
    private var lastGravZ = 9.81
    private var lastLinX = 0.0
    private var lastLinY = 0.0
    private var lastLinZ = 0.0

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasGyroscope: Boolean get() = gyroscope != null

    fun startSensors(): Boolean {
        if (accelerometer == null) return false

        val accelRegistered = sensorManager.registerListener(this, accelerometer, samplingRate)
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, samplingRate)
        }
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, samplingRate)
        }
        if (linAccelSensor != null) {
            sensorManager.registerListener(this, linAccelSensor, samplingRate)
        }

        isRecording = accelRegistered
        return isRecording
    }

    fun stopSensors() {
        if (isRecording) {
            sensorManager.unregisterListener(this)
            isRecording = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRecording) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values[0].toDouble()
                lastAccelY = event.values[1].toDouble()
                lastAccelZ = event.values[2].toDouble()

                val data = SensorData(
                    timestamp = System.currentTimeMillis(),
                    timestampNs = event.timestamp,
                    accelX = lastAccelX,
                    accelY = lastAccelY,
                    accelZ = lastAccelZ,
                    gyroX = lastGyroX,
                    gyroY = lastGyroY,
                    gyroZ = lastGyroZ,
                    gravX = lastGravX,
                    gravY = lastGravY,
                    gravZ = lastGravZ,
                    linX = lastLinX,
                    linY = lastLinY,
                    linZ = lastLinZ
                )
                onSensorDataListener?.invoke(data)
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0].toDouble()
                lastGyroY = event.values[1].toDouble()
                lastGyroZ = event.values[2].toDouble()
            }
            Sensor.TYPE_GRAVITY -> {
                lastGravX = event.values[0].toDouble()
                lastGravY = event.values[1].toDouble()
                lastGravZ = event.values[2].toDouble()
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                lastLinX = event.values[0].toDouble()
                lastLinY = event.values[1].toDouble()
                lastLinZ = event.values[2].toDouble()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for this application
    }
}
