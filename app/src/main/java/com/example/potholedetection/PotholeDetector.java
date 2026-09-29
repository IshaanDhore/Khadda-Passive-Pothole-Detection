package com.example.potholedetection;

import java.util.ArrayList;
import java.util.List;

public class PotholeDetector {
    public static final float Z_THRESH_LIMIT = 0.4f;
    public static final float Z_DIFF_LIMIT = 0.2f;
    public static final float G_ZERO_LIMIT = 0.8f;
    public static final int STDEV_WINDOW_SIZE = 20;
    public static final float STDEV_LIMIT = 0.2f;

    private float[] zWindowBuffer = new float[STDEV_WINDOW_SIZE];
    private int bufferPointer = 0;
    private int sampleCount = 0;
    private float previousZ = 0.0f;
    private boolean hasPreviousZ = false;

    public interface OnPotholeDetectedListener {
        void onDetected(long timestamp, double lat, double lng, String algorithm, float severity);
    }

    private OnPotholeDetectedListener onPotholeDetected;

    public void setOnPotholeDetectedListener(OnPotholeDetectedListener listener) {
        this.onPotholeDetected = listener;
    }

    public boolean checkZThresh(float z) {
        return Math.abs(z) >= Z_THRESH_LIMIT;
    }

    public boolean checkZDiff(float currentZ) {
        if (!hasPreviousZ) {
            previousZ = currentZ;
            hasPreviousZ = true;
            return false;
        }
        float diff = Math.abs(currentZ - previousZ);
        previousZ = currentZ;
        return diff >= Z_DIFF_LIMIT;
    }

    public boolean checkGZero(float x, float y, float z) {
        return Math.abs(x) < G_ZERO_LIMIT && Math.abs(y) < G_ZERO_LIMIT && Math.abs(z) < G_ZERO_LIMIT;
    }

    public boolean checkStdevZ(float currentZ) {
        zWindowBuffer[bufferPointer] = currentZ;
        bufferPointer = (bufferPointer + 1) % STDEV_WINDOW_SIZE;
        if (sampleCount < STDEV_WINDOW_SIZE) {
            sampleCount++;
        }

        if (sampleCount < STDEV_WINDOW_SIZE) {
            return false;
        }

        float sum = 0.0f;
        for (int i = 0; i < STDEV_WINDOW_SIZE; i++) {
            sum += zWindowBuffer[i];
        }
        float mean = sum / STDEV_WINDOW_SIZE;

        float sumSqDiff = 0.0f;
        for (int i = 0; i < STDEV_WINDOW_SIZE; i++) {
            float diff = zWindowBuffer[i] - mean;
            sumSqDiff += (diff * diff);
        }
        float variance = sumSqDiff / STDEV_WINDOW_SIZE;
        float stdev = (float) Math.sqrt(variance);

        return stdev >= STDEV_LIMIT;
    }

    public void processSample(long timestamp, float x, float y, float z, double lat, double lng) {
        boolean zThreshTriggered = checkZThresh(z);
        boolean zDiffTriggered = checkZDiff(z);
        boolean gZeroTriggered = checkGZero(x, y, z);
        boolean stdevTriggered = checkStdevZ(z);

        if (zThreshTriggered || zDiffTriggered || gZeroTriggered || stdevTriggered) {
            List<String> algorithms = new ArrayList<>();
            if (zThreshTriggered) algorithms.add("Z-THRESH");
            if (zDiffTriggered) algorithms.add("Z-DIFF");
            if (gZeroTriggered) algorithms.add("G-ZERO");
            if (stdevTriggered) algorithms.add("STDEV(Z)");
            
            String algorithmStr = String.join(",", algorithms);
            float severity = Math.abs(z);
            if (onPotholeDetected != null) {
                onPotholeDetected.onDetected(timestamp, lat, lng, algorithmStr, severity);
            }
        }
    }
}
