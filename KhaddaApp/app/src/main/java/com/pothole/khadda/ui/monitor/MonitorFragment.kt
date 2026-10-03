package com.pothole.khadda.ui.monitor

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pothole.khadda.R
import com.pothole.khadda.databinding.FragmentMonitorBinding
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel
import com.pothole.khadda.service.DetectionForegroundService
import java.util.Locale

class MonitorFragment : Fragment() {

    private var _binding: FragmentMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MonitorViewModel by activityViewModels()

    private var detectionService: DetectionForegroundService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DetectionForegroundService.LocalBinder
            detectionService = binder.getService()
            isBound = true

            // Attach callbacks
            detectionService?.onLiveSensorData = { data ->
                activity?.runOnUiThread {
                    binding.waveformView.addSample(
                        data.accelX.toFloat(),
                        data.accelY.toFloat(),
                        data.accelZ.toFloat()
                    )
                }
            }

            detectionService?.onPotholeDetected = { event ->
                activity?.runOnUiThread {
                    viewModel.onPotholeDetected(event)
                    showPotholeAlert(event)
                }
            }

            detectionService?.onLocationUpdated = { lat, lon, speed ->
                activity?.runOnUiThread {
                    viewModel.onLocationUpdated(lat, lon, speed)
                    binding.tvSpeed.text = String.format(Locale.US, "Speed: %.1f km/h", speed)
                    binding.tvGps.text = String.format(Locale.US, "GPS: %.4f, %.4f", lat, lon)
                }
            }

            updateUiState(detectionService?.isDetectionActive == true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            detectionService = null
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        
        if (fineLocationGranted && cameraGranted) {
            startServiceAndDetection()
        } else {
            Toast.makeText(requireContext(), "Location and Camera permissions are required for tracking and saving images", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind Service
        val intent = Intent(requireContext(), DetectionForegroundService::class.java)
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        binding.btnToggleMonitoring.setOnClickListener {
            val active = detectionService?.isDetectionActive ?: false
            if (active) {
                stopDetection()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.switchDemoMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDemoMode(isChecked)
            if (detectionService?.isDetectionActive == true) {
                // Restart with demo
                stopDetection()
                checkPermissionsAndStart()
            }
        }

        viewModel.sessionPotholeCount.observe(viewLifecycleOwner) { count ->
            binding.tvCount.text = count.toString()
        }

        viewModel.allEvents.observe(viewLifecycleOwner) { events ->
            if (detectionService?.isDetectionActive != true) {
                binding.tvCount.text = events.size.toString()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startServiceAndDetection()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startServiceAndDetection() {
        val intent = Intent(requireContext(), DetectionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }

        val isDemo = binding.switchDemoMode.isChecked
        val started = detectionService?.startDetection(useSimulation = isDemo) ?: false
        updateUiState(started)
        if (started) {
            binding.waveformView.clear()
            viewModel.resetSessionCounter()
            Toast.makeText(
                requireContext(),
                if (isDemo) "Simulation Demo Started" else "Real-Time Sensor Monitoring Started",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopDetection() {
        detectionService?.stopDetection()
        updateUiState(false)
        Toast.makeText(requireContext(), "Monitoring Stopped. Session Saved.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUiState(active: Boolean) {
        viewModel.setMonitoring(active)
        if (active) {
            binding.btnToggleMonitoring.text = getString(R.string.stop_monitoring)
            binding.btnToggleMonitoring.setBackgroundColor(Color.parseColor("#EF4444"))
            binding.tvStatus.text = getString(R.string.status_detecting)
            binding.tvStatus.setTextColor(Color.parseColor("#10B981"))
            binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.severity_low)
        } else {
            binding.btnToggleMonitoring.text = getString(R.string.start_monitoring)
            binding.btnToggleMonitoring.setBackgroundColor(Color.parseColor("#10B981"))
            binding.tvStatus.text = getString(R.string.status_standby)
            binding.tvStatus.setTextColor(Color.parseColor("#F8FAFC"))
            binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.on_surface_muted)
        }
    }

    private fun showPotholeAlert(event: PotholeEvent) {
        binding.tvAlertSeverity.text = event.severity.name
        binding.tvAlertDetail.text = String.format(
            Locale.US,
            "Impact: %.2f m/s²\nAlgo: %s",
            event.zDiffValue,
            event.algorithm
        )
        val color = when (event.severity) {
            SeverityLevel.HIGH -> Color.parseColor("#EF4444")
            SeverityLevel.MEDIUM -> Color.parseColor("#F59E0B")
            SeverityLevel.LOW -> Color.parseColor("#10B981")
        }
        binding.tvAlertSeverity.setTextColor(color)
        binding.cardLastAlert.setStrokeColor(color)
        binding.cardLastAlert.strokeWidth = 4
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
        }
        _binding = null
    }
}
