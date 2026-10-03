package com.pothole.khadda.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Custom real-time oscilloscope waveform view that visualizes continuous
 * triaxial accelerometer (X: Red, Y: Green, Z: Cyan) and gyroscope signals.
 */
class LiveWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxPoints = 120
    private val xPoints = FloatArray(maxPoints)
    private val yPoints = FloatArray(maxPoints)
    private val zPoints = FloatArray(maxPoints)
    private var pointCount = 0

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#223344")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#445566")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val paintX = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252") // Coral Red
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val paintY = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#69F0AE") // Mint Green
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val paintZ = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF") // Cyan Blue
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val pathX = Path()
    private val pathY = Path()
    private val pathZ = Path()

    fun addSample(ax: Float, ay: Float, az: Float) {
        if (pointCount < maxPoints) {
            xPoints[pointCount] = ax
            yPoints[pointCount] = ay
            zPoints[pointCount] = az
            pointCount++
        } else {
            // Shift left
            System.arraycopy(xPoints, 1, xPoints, 0, maxPoints - 1)
            System.arraycopy(yPoints, 1, yPoints, 0, maxPoints - 1)
            System.arraycopy(zPoints, 1, zPoints, 0, maxPoints - 1)
            xPoints[maxPoints - 1] = ax
            yPoints[maxPoints - 1] = ay
            zPoints[maxPoints - 1] = az
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        // Draw background grid
        canvas.drawColor(Color.parseColor("#0F172A")) // Dark Navy Slate
        canvas.drawLine(0f, centerY, w, centerY, centerLinePaint)
        canvas.drawLine(0f, centerY - h / 4f, w, centerY - h / 4f, gridPaint)
        canvas.drawLine(0f, centerY + h / 4f, w, centerY + h / 4f, gridPaint)

        if (pointCount < 2) return

        val stepX = w / (maxPoints - 1)
        val scaleY = h / 30f // Scaling for +/- 15 m/s^2

        pathX.reset()
        pathY.reset()
        pathZ.reset()

        for (i in 0 until pointCount) {
            val px = i * stepX
            val pyX = centerY - (xPoints[i] * scaleY)
            val pyY = centerY - (yPoints[i] * scaleY)
            val pyZ = centerY - ((zPoints[i] - 9.81f) * scaleY) // Center Z around 9.81 m/s^2

            if (i == 0) {
                pathX.moveTo(px, pyX)
                pathY.moveTo(px, pyY)
                pathZ.moveTo(px, pyZ)
            } else {
                pathX.lineTo(px, pyX)
                pathY.lineTo(px, pyY)
                pathZ.lineTo(px, pyZ)
            }
        }

        canvas.drawPath(pathX, paintX)
        canvas.drawPath(pathY, paintY)
        canvas.drawPath(pathZ, paintZ)
    }

    fun clear() {
        pointCount = 0
        postInvalidate()
    }
}
