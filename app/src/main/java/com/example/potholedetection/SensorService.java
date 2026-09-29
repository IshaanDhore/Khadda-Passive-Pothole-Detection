package com.example.potholedetection;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class SensorService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PotholeDetector potholeDetector;
    private DatabaseSynchronizer dbSynchronizer;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentLat = 0.0;
    private double currentLng = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        
        dbSynchronizer = new DatabaseSynchronizer();
        potholeDetector = new PotholeDetector();
        potholeDetector.setOnPotholeDetectedListener((timestamp, lat, lng, algorithm, severity) -> {
            dbSynchronizer.queueEvent(timestamp, currentLat, currentLng, algorithm, severity);
            
            Intent intent = new Intent("POTHOLE_EVENT");
            intent.putExtra("algorithm", algorithm);
            intent.putExtra("severity", severity);
            intent.putExtra("lat", currentLat);
            intent.putExtra("lng", currentLng);
            sendBroadcast(intent);
        });
        
        setupLocationTracking();
        startForegroundService();
    }

    @SuppressLint("MissingPermission")
    private void setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();
                
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    currentLat = locationResult.getLastLocation().getLatitude();
                    currentLng = locationResult.getLastLocation().getLongitude();
                }
            }
        };
        
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    private void startForegroundService() {
        String channelId = "PotholeServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Pothole Detection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Pothole Detection Active")
                .setContentText("Monitoring road surface...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            
            float xG = x / SensorManager.STANDARD_GRAVITY;
            float yG = y / SensorManager.STANDARD_GRAVITY;
            float zG = z / SensorManager.STANDARD_GRAVITY;
            
            potholeDetector.processSample(System.currentTimeMillis(), xG, yG, zG, currentLat, currentLng);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
