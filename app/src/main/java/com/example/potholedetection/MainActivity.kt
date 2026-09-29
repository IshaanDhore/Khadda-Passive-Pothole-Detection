package com.example.potholedetection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.potholedetection.theme.PotholeDetectionTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var detectedPotholes = mutableStateListOf<PotholeEvent>()

    private val potholeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "POTHOLE_EVENT") {
                val algorithm = intent.getStringExtra("algorithm") ?: ""
                val severity = intent.getFloatExtra("severity", 0f)
                val lat = intent.getDoubleExtra("lat", 0.0)
                val lng = intent.getDoubleExtra("lng", 0.0)
                
                detectedPotholes.add(
                    PotholeEvent(
                        timestamp = System.currentTimeMillis(),
                        latitude = lat,
                        longitude = lng,
                        algorithmTriggered = algorithm,
                        severityValue = severity
                    )
                )
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(potholeReceiver, IntentFilter("POTHOLE_EVENT"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(potholeReceiver, IntentFilter("POTHOLE_EVENT"))
        }

        setContent {
            PotholeDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServiceRunning = isServiceRunning,
                        potholes = detectedPotholes,
                        onToggleService = { toggleService() }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun toggleService() {
        val intent = Intent(this, SensorService::class.java)
        if (isServiceRunning) {
            stopService(intent)
            isServiceRunning = false
        } else {
            ContextCompat.startForegroundService(this, intent)
            isServiceRunning = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(potholeReceiver)
    }
}

@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    potholes: List<PotholeEvent>,
    onToggleService: () -> Unit
) {
    val defaultLocation = LatLng(37.7749, -122.4194)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }

    LaunchedEffect(potholes.size) {
        if (potholes.isNotEmpty()) {
            val last = potholes.last()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(last.latitude, last.longitude), 15f
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false)
            ) {
                potholes.forEach { event ->
                    Marker(
                        state = MarkerState(position = LatLng(event.latitude, event.longitude)),
                        title = "Pothole: ${event.algorithmTriggered}",
                        snippet = "Severity: ${event.severityValue}g"
                    )
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Potholes Detected: ${potholes.size}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onToggleService) {
                Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
            }
        }
    }
}
