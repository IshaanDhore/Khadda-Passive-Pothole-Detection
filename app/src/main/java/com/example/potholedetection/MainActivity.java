package com.example.potholedetection;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private boolean isServiceRunning = false;
    private List<PotholeEvent> detectedPotholes = new ArrayList<>();
    
    private TextView potholeCountText;
    private Button toggleServiceButton;
    private GoogleMap googleMap;

    private final BroadcastReceiver potholeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "POTHOLE_EVENT".equals(intent.getAction())) {
                String algorithm = intent.getStringExtra("algorithm");
                float severity = intent.getFloatExtra("severity", 0f);
                double lat = intent.getDoubleExtra("lat", 0.0);
                double lng = intent.getDoubleExtra("lng", 0.0);
                
                PotholeEvent event = new PotholeEvent(
                        "", 
                        System.currentTimeMillis(), 
                        lat, 
                        lng, 
                        algorithm != null ? algorithm : "", 
                        severity
                );
                
                detectedPotholes.add(event);
                updateUI(event);
            }
        }
    };

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            // Handle permissions
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        potholeCountText = findViewById(R.id.potholeCountText);
        toggleServiceButton = findViewById(R.id.toggleServiceButton);

        toggleServiceButton.setOnClickListener(v -> toggleService());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        requestPermissions();

        IntentFilter filter = new IntentFilter("POTHOLE_EVENT");
        ContextCompat.registerReceiver(this, potholeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        LatLng defaultLocation = new LatLng(37.7749, -122.4194);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f));
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toArray(new String[0]));
        }
    }

    private void toggleService() {
        Intent intent = new Intent(this, SensorService.class);
        if (isServiceRunning) {
            stopService(intent);
            isServiceRunning = false;
            toggleServiceButton.setText("Start Monitoring");
        } else {
            ContextCompat.startForegroundService(this, intent);
            isServiceRunning = true;
            toggleServiceButton.setText("Stop Monitoring");
        }
    }

    private void updateUI(PotholeEvent event) {
        potholeCountText.setText("Potholes Detected: " + detectedPotholes.size());
        
        if (googleMap != null) {
            LatLng location = new LatLng(event.getLatitude(), event.getLongitude());
            googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Pothole: " + event.getAlgorithmTriggered())
                    .snippet("Severity: " + event.getSeverityValue() + "g"));
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(potholeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }
}
