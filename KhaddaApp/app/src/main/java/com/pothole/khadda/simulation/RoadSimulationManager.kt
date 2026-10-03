package com.pothole.khadda.simulation

import com.pothole.khadda.model.SensorData
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates synthetic sensor signals and simulated GPS track for testing,
 * classroom demonstrations, and emulator verification.
 */
class RoadSimulationManager {

    private var simulationJob: Job? = null
    var isSimulating: Boolean = false
        private set

    var onSimulatedSensorData: ((SensorData) -> Unit)? = null
    var onSimulatedLocation: ((latitude: Double, longitude: Double, speedKmh: Float) -> Unit)? = null

    // Base coordinate (e.g. Pune University / Campus area)
    private var simLat = 18.5204
    private var simLon = 73.8567
    private var simStep = 0

    fun startSimulation(scope: CoroutineScope) {
        if (isSimulating) return
        isSimulating = true
        simStep = 0

        simulationJob = scope.launch(Dispatchers.Default) {
            var sampleCount = 0
            while (isActive && isSimulating) {
                sampleCount++
                simStep++

                // Base engine / road surface vibration (sinusoidal + noise)
                val baseTime = sampleCount * 0.05
                val normalNoise = (Random.nextDouble() - 0.5) * 0.4
                var az = 9.81 + sin(baseTime * 5) * 0.3 + normalNoise
                var ax = (Random.nextDouble() - 0.5) * 0.3
                var ay = (Random.nextDouble() - 0.5) * 0.3
                var gx = (Random.nextDouble() - 0.5) * 0.05
                var gy = (Random.nextDouble() - 0.5) * 0.05
                var gz = (Random.nextDouble() - 0.5) * 0.05

                // Inject simulated potholes at specific intervals
                // Pothole 1 (Low): at sample 60
                // Pothole 2 (Medium): at sample 130
                // Pothole 3 (High): at sample 200
                if (sampleCount % 120 == 60) {
                    // Sudden dip then sharp impact
                    az = 9.81 - 4.5 // G-ZERO drop
                } else if (sampleCount % 120 == 61) {
                    az = 9.81 + 7.2 // Z-DIFF & Z-THRESH spike
                } else if (sampleCount % 120 == 62) {
                    az = 9.81 - 2.0
                }

                val data = SensorData(
                    timestamp = System.currentTimeMillis(),
                    accelX = ax,
                    accelY = ay,
                    accelZ = az,
                    gyroX = gx,
                    gyroY = gy,
                    gyroZ = gz
                )
                withContext(Dispatchers.Main) {
                    onSimulatedSensorData?.invoke(data)
                }

                // Update simulated GPS location every 20 samples (~1 second)
                if (sampleCount % 20 == 0) {
                    simLat += 0.00012 * (1.0 + sin(simStep * 0.1) * 0.2)
                    simLon += 0.00008 * (1.0 + Random.nextDouble() * 0.1)
                    val simulatedSpeed = 35f + Random.nextFloat() * 10f

                    withContext(Dispatchers.Main) {
                        onSimulatedLocation?.invoke(simLat, simLon, simulatedSpeed)
                    }
                }

                delay(30) // ~33 Hz sampling rate
            }
        }
    }

    fun stopSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
    }
}
