package com.pothole.khadda.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pothole.khadda.databinding.FragmentSettingsBinding
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.zDiffThreshold.observe(viewLifecycleOwner) { v ->
            binding.sliderZDiff.value = v.coerceIn(0.5f, 5.0f)
            binding.tvLabelZDiff.text = String.format(Locale.US, "Z-DIFF Threshold: %.2f m/s² (Ref: 0.2g)", v)
        }

        viewModel.zThreshThreshold.observe(viewLifecycleOwner) { v ->
            binding.sliderZThresh.value = v.coerceIn(1.0f, 8.0f)
            binding.tvLabelZThresh.text = String.format(Locale.US, "Z-THRESH Threshold: %.2f m/s² (Ref: 0.4g)", v)
        }

        viewModel.stdevThreshold.observe(viewLifecycleOwner) { v ->
            binding.sliderStdev.value = v.coerceIn(0.5f, 4.0f)
            binding.tvLabelStdev.text = String.format(Locale.US, "STDEV(Z) Threshold: %.2f m/s² (Ref: 0.2g)", v)
        }

        viewModel.gZeroThreshold.observe(viewLifecycleOwner) { v ->
            binding.sliderGZero.value = v.coerceIn(3.0f, 9.5f)
            binding.tvLabelGZero.text = String.format(Locale.US, "G-ZERO Threshold: %.2f m/s² (Ref: 0.8g)", v)
        }

        viewModel.soundEnabled.observe(viewLifecycleOwner) {
            binding.switchSound.isChecked = it
        }

        viewModel.vibrationEnabled.observe(viewLifecycleOwner) {
            binding.switchVibrate.isChecked = it
        }

        viewModel.demoMode.observe(viewLifecycleOwner) {
            binding.switchSettingsDemo.isChecked = it
        }

        binding.sliderZDiff.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.saveZDiff(value)
        }
        binding.sliderZThresh.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.saveZThresh(value)
        }
        binding.sliderStdev.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.saveStdev(value)
        }
        binding.sliderGZero.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.saveGZero(value)
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSound(isChecked)
        }
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setVibration(isChecked)
        }
        binding.switchSettingsDemo.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDemoMode(isChecked)
        }

        binding.btnResetDefaults.setOnClickListener {
            viewModel.resetDefaults()
            Toast.makeText(requireContext(), "Reset to Reference Defaults", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
