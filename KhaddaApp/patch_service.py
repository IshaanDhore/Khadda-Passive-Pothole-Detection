import re

filepath = r"c:\Users\Neeraj Padghan\OneDrive\Desktop\Khadda\KhaddaApp\app\src\main\java\com\pothole\khadda\service\DetectionForegroundService.kt"

with open(filepath, 'r') as f:
    content = f.read()

# 1. Imports
imports = """import androidx.lifecycle.LifecycleService
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.util.Size
import java.util.ArrayDeque
import kotlin.math.abs
import java.io.File
import java.io.FileOutputStream"""
content = content.replace("import android.app.Service", "import android.app.Service\n" + imports)

# 2. Base Class
content = content.replace("class DetectionForegroundService : Service()", "class DetectionForegroundService : LifecycleService()")

# 3. New variables
new_vars = """    private var lastSpeed = 0f
    private var lastLocationTime = 0L
    private var currentGpsAccel = 0f
    
    private val frameBuffer = ArrayDeque<Pair<Long, Bitmap>>()
    private var lastFrameTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null"""

content = content.replace("    private var potholeCount = 0\n", "    private var potholeCount = 0\n\n" + new_vars + "\n")

# 4. Location update logic
old_loc = """        // Hardware GPS listener
        locationTracker.onLocationChangedListener = { loc ->
            onLocationUpdated?.invoke(loc.latitude, loc.longitude, loc.speed * 3.6f)
        }"""
new_loc = """        // Hardware GPS listener
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
        }"""
content = content.replace(old_loc, new_loc)

# 5. processSensorSample 
content = content.replace("val features = dataProcessor.extractFeatures(data)", "val features = dataProcessor.extractFeatures(data, currentGpsAccel)")

# 6. Save bitmap logic
save_logic = """
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
"""
content = content.replace("onPotholeDetected?.invoke(event)", "onPotholeDetected?.invoke(event)\n" + save_logic)

# 7. Start Camera
start_camera = """
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
"""
content = content.replace("    fun startDetection(useSimulation: Boolean = false): Boolean {", start_camera + "\n    fun startDetection(useSimulation: Boolean = false): Boolean {")

# 8. Start/Stop Camera calls
content = content.replace("locationTracker.startLocationUpdates()", "locationTracker.startLocationUpdates()\n            startCamera()")
content = content.replace("simulationManager.stopSimulation()", "simulationManager.stopSimulation()\n        cameraProvider?.unbindAll()")


with open(filepath, 'w') as f:
    f.write(content)

print("Patched DetectionForegroundService.kt successfully")
