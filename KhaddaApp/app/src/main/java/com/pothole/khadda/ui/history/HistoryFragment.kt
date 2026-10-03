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
            }
        )

        binding.recyclerViewPotholes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPotholes.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All Records")
                .setMessage("Are you sure you want to delete all recorded pothole events and sessions?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearAll()
                    Toast.makeText(requireContext(), "Records Cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
