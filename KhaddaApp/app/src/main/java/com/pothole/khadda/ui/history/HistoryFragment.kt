package com.pothole.khadda.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import java.io.FileWriter
import com.pothole.khadda.databinding.FragmentHistoryBinding
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by activityViewModels()
    private lateinit var adapter: PotholeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PotholeAdapter(
            onItemClick = { event ->
                showDetailDialog(event)
            },
            onStatusChangeClick = { event ->
                showStatusChangeDialog(event)
            },
            onLongClick = { event ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Pothole Event")
                    .setMessage("Are you sure you want to delete this specific pothole record and its image?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteEvent(event.eventId)
                        val imageFile = java.io.File(requireContext().filesDir, "pothole_${event.eventId}.jpg")
                        if (imageFile.exists()) {
                            imageFile.delete()
                        }
                        android.widget.Toast.makeText(requireContext(), "Event Deleted", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerViewPotholes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPotholes.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All Records")
                .setMessage("Are you sure you want to delete all recorded pothole events AND all raw telemetry logs?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearAll()
                    // Delete raw telemetry files
                    try {
                        val dir = java.io.File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "KhaddaLogs")
                        if (dir.exists()) {
                            dir.listFiles()?.forEach { it.delete() }
                        }
                        
                        // Also delete debug gallery images
                        val filesDir = requireContext().filesDir
                        filesDir.listFiles { file -> 
                            file.name.startsWith("pothole_") || file.name.startsWith("rejected_pothole_") 
                        }?.forEach { it.delete() }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Toast.makeText(requireContext(), "Records, Logs, & Gallery Images Cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnExport.setOnClickListener {
            val events = viewModel.allEvents.value ?: emptyList()
            if (events.isEmpty()) {
                Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val exportDir = File(requireContext().cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()
                val file = File(exportDir, "pothole_session_export.csv")
                val writer = FileWriter(file)
                
                writer.append("EventID,SessionID,Timestamp,Latitude,Longitude,Impact,Severity,Algorithm,Label,Status\n")
                for (event in events) {
                    val label = when (event.algorithm) {
                        "MANUAL_POTHOLE" -> "POTHOLE"
                        "MANUAL_SPEED_BREAKER" -> "SPEED_BREAKER"
                        else -> ""
                    }
                    val algo = if (label.isNotEmpty()) "MANUAL_TRIGGER" else event.algorithm
                    writer.append("${event.eventId},${event.sessionId},${event.timestamp},${event.latitude},${event.longitude},${event.zDiffValue},${event.severity.name},$algo,$label,${event.status.name}\n")
                }
                writer.flush()
                writer.close()
                
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Pothole Session Data")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export Session Data"))
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to export data", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.allEvents.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvTotalCount.text = "Detected Potholes (${list.size})"
            if (list.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.recyclerViewPotholes.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.recyclerViewPotholes.visibility = View.VISIBLE
            }
        }
    }

    private fun showStatusChangeDialog(event: PotholeEvent) {
        val statuses = arrayOf("REPORTED", "UNDER_REVIEW", "REPAIRED")
        val currentIndex = statuses.indexOf(event.status.name)

        AlertDialog.Builder(requireContext())
            .setTitle("Update Repair Status")
            .setSingleChoiceItems(statuses, currentIndex) { dialog, which ->
                val newStatus = RepairStatus.valueOf(statuses[which])
                viewModel.updateRepairStatus(event.eventId, newStatus)
                Toast.makeText(requireContext(), "Status updated to ${newStatus.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailDialog(event: PotholeEvent) {
        AlertDialog.Builder(requireContext())
            .setTitle("${event.severity} Pothole Event")
            .setMessage(
                "Event ID: ${event.eventId}\n" +
                "Session: ${event.sessionId}\n" +
                "Impact: ${event.zDiffValue} m/s²\n" +
                "Algorithm: ${event.algorithm}\n" +
                "GPS: ${event.latitude}, ${event.longitude}\n" +
                "Status: ${event.status}\n" +
                "Sync: ${event.syncStatus}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
