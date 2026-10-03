package com.pothole.khadda.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pothole.khadda.model.SeverityLevel

/**
 * Utility helper for audio tone and haptic vibration feedback upon pothole detection.
 */
class AlertHelper(private val context: Context) {

    var isSoundEnabled: Boolean = true
    var isVibrationEnabled: Boolean = true

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    fun triggerAlert(severity: SeverityLevel) {
        if (isSoundEnabled) {
            try {
                when (severity) {
                    SeverityLevel.HIGH -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 300)
                    SeverityLevel.MEDIUM -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    SeverityLevel.LOW -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                }
            } catch (e: Exception) {
                // Ignore audio errors
            }
        }

        if (isVibrationEnabled) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val duration = when (severity) {
                SeverityLevel.HIGH -> 400L
                SeverityLevel.MEDIUM -> 250L
                SeverityLevel.LOW -> 120L
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = when (severity) {
                        SeverityLevel.HIGH -> 255
                        SeverityLevel.MEDIUM -> 180
                        SeverityLevel.LOW -> 100
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
