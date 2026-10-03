package com.pothole.khadda.ui.authority

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pothole.khadda.R
import com.pothole.khadda.databinding.FragmentAuthorityBinding
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus
import com.pothole.khadda.ui.history.PotholeAdapter

class AuthorityFragment : Fragment() {

    private var _binding: FragmentAuthorityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthorityViewModel by activityViewModels()
    private lateinit var adapter: PotholeAdapter
    private var allList: List<PotholeEvent> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PotholeAdapter(
            onItemClick = { event ->
                showStatusDialog(event)
            },
            onStatusChangeClick = { event ->
                showStatusDialog(event)
            }
        )

        binding.recyclerViewAuthority.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAuthority.adapter = adapter

        // Filter chips
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipStatusReported) -> viewModel.setFilter(RepairStatus.REPORTED)
                checkedIds.contains(R.id.chipStatusReview) -> viewModel.setFilter(RepairStatus.UNDER_REVIEW)
                checkedIds.contains(R.id.chipStatusRepaired) -> viewModel.setFilter(RepairStatus.REPAIRED)
                else -> viewModel.setFilter(null)
            }
        }

        viewModel.allEvents.observe(viewLifecycleOwner) { list ->
            allList = list
            filterAndDisplay()
        }

        viewModel.filterStatus.observe(viewLifecycleOwner) {
            filterAndDisplay()
        }
    }

    private fun filterAndDisplay() {
        val currentFilter = viewModel.filterStatus.value
        val displayList = if (currentFilter == null) {
            allList
        } else {
            allList.filter { it.status == currentFilter }
        }

        adapter.submitList(displayList)
        if (displayList.isEmpty()) {
            binding.tvEmptyAuthority.visibility = View.VISIBLE
            binding.recyclerViewAuthority.visibility = View.GONE
        } else {
            binding.tvEmptyAuthority.visibility = View.GONE
            binding.recyclerViewAuthority.visibility = View.VISIBLE
        }
    }

    private fun showStatusDialog(event: PotholeEvent) {
        val statuses = arrayOf("REPORTED", "UNDER_REVIEW", "REPAIRED")
        val currentIndex = statuses.indexOf(event.status.name)

        AlertDialog.Builder(requireContext())
            .setTitle("Authority Action: Update Repair Status")
            .setSingleChoiceItems(statuses, currentIndex) { dialog, which ->
                val newStatus = RepairStatus.valueOf(statuses[which])
                viewModel.updateStatus(event.eventId, newStatus)
                Toast.makeText(requireContext(), "Marked as ${newStatus.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
