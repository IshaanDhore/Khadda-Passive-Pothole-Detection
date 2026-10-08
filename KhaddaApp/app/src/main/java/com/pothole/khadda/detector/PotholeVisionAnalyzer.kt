package com.pothole.khadda.detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hybrid Vision Analyzer for validating physical shock anomalies using Computer Vision.
 * 
 * If a pothole is physically detected (Z-DIFF > 5.0), this class scans the corresponding
 * camera frame to visually confirm the pothole's existence using a raw Interpreter.
 */
class PotholeVisionAnalyzer(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    init {
        try {
            // Attempt to load the trained YOLOv8 model from the assets folder.
            val modelBuffer = FileUtil.loadMappedFile(context, "pothole_model.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4) // Use 4 threads for faster CPU inference
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.d("VisionAnalyzer", "Successfully loaded pothole_model.tflite!")
        } catch (e: Exception) {
            Log.w("VisionAnalyzer", "pothole_model.tflite not found or invalid. Running in Mock Mode.")
            isModelLoaded = false
        }
    }

    /**
     * Scans the provided image for potholes by manually parsing the YOLOv8-Seg tensor.
     */
    fun validatePothole(bitmap: Bitmap): Boolean {
        Log.d("VisionAnalyzer", "=== STARTING VISION VALIDATION ===")
        if (!isModelLoaded || interpreter == null) {
            Log.w("VisionAnalyzer", "Model is not loaded! Falling back to hardware accelerometer.")
            // Graceful fallback: If the user hasn't trained and uploaded their TFLite model yet,
            // we default to trusting the accelerometer data (Legacy mode).
            return true 
        }

        try {
            // 1. Resize image to match the new HuggingFace YOLOv8 Segmentation expected input (640x640)
            Log.d("VisionAnalyzer", "Step 1: Resizing bitmap from ${bitmap.width}x${bitmap.height} to 640x640")
            val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
            
            // 2. Convert Bitmap to Float ByteBuffer [1, 640, 640, 3] normalized to 0.0 - 1.0
            Log.d("VisionAnalyzer", "Step 2: Allocating 640x640 ByteBuffer...")
            val inputBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4) // 4 bytes per float
            inputBuffer.order(ByteOrder.nativeOrder())
            
            val intValues = IntArray(640 * 640)
            resized.getPixels(intValues, 0, 640, 0, 0, 640, 640)
            
            for (pixel in intValues) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // Red
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // Green
                inputBuffer.putFloat((pixel and 0xFF) / 255.0f)          // Blue
            }

            // 3. Prepare YOLOv8-Seg dual output buffers.
            Log.d("VisionAnalyzer", "Step 3: Allocating dual-tensor output maps for Segmentation model...")
            // A 640x640 segmentation image outputs two tensors:
            // Tensor 0: [1, 37, 8400] (4 bbox + 1 confidence + 32 mask coefficients = 37 rows, 8400 anchors)
            // Tensor 1: [1, 32, 160, 160] (32 prototype masks at 160x160 resolution)
            val output0 = Array(1) { Array(37) { FloatArray(8400) } }
            val output1 = Array(1) { Array(32) { Array(160) { FloatArray(160) } } }
            
            val outputs: MutableMap<Int, Any> = HashMap()
            outputs[0] = output0
            outputs[1] = output1

            // 4. Run Inference (Multiple Inputs/Outputs)
            Log.d("VisionAnalyzer", "Step 4: Executing TFLite multiple-tensor Inference...")
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            Log.d("VisionAnalyzer", "Step 4: Inference Completed Successfully!")

            // 5. Check if any detected bounding boxes belong to the 'pothole' class
            // Raised threshold to 25% (0.25f) to prevent noise/blur from triggering false positives!
            Log.d("VisionAnalyzer", "Step 5: Scanning 8400 anchors for pothole confidences > 0.25...")
            var maxConfidence = 0.0f
            for (i in 0 until 8400) {
                val confidence = output0[0][4][i] // Index 4 is the class confidence score
                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                }
                if (confidence > 0.25f) {
                    Log.d("VisionAnalyzer", "✅ Pothole Visually Confirmed! Max Confidence found: $confidence")
                    return true
                }
            }
            
            // If the model ran but found no potholes > 25%, we reject the false positive!
            Log.d("VisionAnalyzer", "❌ False Positive Rejected: No pothole found in the image. Max confidence was only: $maxConfidence")
            return false

        } catch (e: Exception) {
            Log.e("VisionAnalyzer", "CRASH: Inference failed due to Tensor shape mismatch or Memory limit!", e)
            e.printStackTrace()
            return false // Safely reject on crash!
        }
    }
}
