package com.pothole.khadda.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pothole.khadda.databinding.FragmentReportBinding
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by activityViewModels()
    private var cachedEvents: List<PotholeEvent> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allEvents.observe(viewLifecycleOwner) { events ->
            cachedEvents = events
            viewModel.generateOverallReport(events)
        }

        viewModel.currentReport.observe(viewLifecycleOwner) { report ->
            if (report != null) {
                binding.tvReportHigh.text = report.highCount.toString()
                binding.tvReportMedium.text = report.mediumCount.toString()
                binding.tvReportLow.text = report.lowCount.toString()

                val zDiffCount = report.algorithmBreakdown["Z-DIFF"] ?: 0
                val zThreshCount = report.algorithmBreakdown["Z-THRESH"] ?: 0
                val stdevCount = report.algorithmBreakdown["STDEV(Z)"] ?: 0
                val gZeroCount = report.algorithmBreakdown["G-ZERO"] ?: 0

                binding.tvAlgoStats.text =
                    "• Z-DIFF: $zDiffCount detections\n" +
                    "• Z-THRESH: $zThreshCount detections\n" +
                    "• STDEV(Z): $stdevCount detections\n" +
                    "• G-ZERO: $gZeroCount detections"
            }
        }

        binding.btnExportCsv.setOnClickListener {
            val report = viewModel.currentReport.value
            if (report == null || cachedEvents.isEmpty()) {
                Toast.makeText(requireContext(), "No pothole data available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val csvFile = ReportExporter.exportToCsv(requireContext(), report, cachedEvents)
                ReportExporter.shareFile(requireContext(), csvFile, "text/csv")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExportJson.setOnClickListener {
            val report = viewModel.currentReport.value
            if (report == null || cachedEvents.isEmpty()) {
                Toast.makeText(requireContext(), "No pothole data available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val jsonFile = ReportExporter.exportToJson(requireContext(), report, cachedEvents)
                ReportExporter.shareFile(requireContext(), jsonFile, "application/json")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
