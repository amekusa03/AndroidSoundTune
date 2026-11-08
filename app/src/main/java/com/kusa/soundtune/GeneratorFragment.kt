package com.kusa.soundtune

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.kusa.soundtune.databinding.FragmentGeneratorBinding
import com.kusa.soundtune.databinding.LayoutToneSlotBinding

class GeneratorFragment : Fragment() {

    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.outputDeviceName.observe(viewLifecycleOwner) { name ->
            binding.textViewOutputStatus.text = getString(R.string.output_status, name)
        }

        val slotBindings = listOf(
            binding.slot0,
            binding.slot1,
            binding.slot2,
            binding.slot3,
            binding.slot4
        )

        val defaultFrequencies = listOf(50.0, 198.0, 789.0, 3100.0, 12500.0)

        slotBindings.forEachIndexed { id, slotBinding ->
            setupSlot(id, slotBinding, defaultFrequencies[id])
        }

        setupPresets(slotBindings)

        binding.buttonStopAll.setOnClickListener {
            viewModel.audioEngine.stopAll()
            slotBindings.forEach { it.buttonTone.setText(R.string.play_sound) }
        }
    }

    private fun setupSlot(id: Int, slotBinding: LayoutToneSlotBinding, defaultFreq: Double) {
        slotBinding.textViewSlotLabel.text = "Tone ${id + 1}"
        slotBinding.editTextFreq.setText(defaultFreq.toInt().toString())

        // Default channel: Center (L+R)
        slotBinding.toggleGroupChannel.check(R.id.buttonChannelCenter)

        slotBinding.toggleGroupChannel.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val channel = when (checkedId) {
                    R.id.buttonChannelLeft -> AudioChannel.LEFT
                    R.id.buttonChannelRight -> AudioChannel.RIGHT
                    else -> AudioChannel.CENTER
                }
                viewModel.audioEngine.setChannel(id, channel)
            }
        }

        slotBinding.buttonTone.setOnClickListener {
            if (viewModel.audioEngine.isTonePlaying(id)) {
                viewModel.audioEngine.stopTone(id)
                slotBinding.buttonTone.setText(R.string.play_sound)
            } else {
                val freq = slotBinding.editTextFreq.text.toString().toDoubleOrNull() ?: defaultFreq
                val channel = when (slotBinding.toggleGroupChannel.checkedButtonId) {
                    R.id.buttonChannelLeft -> AudioChannel.LEFT
                    R.id.buttonChannelRight -> AudioChannel.RIGHT
                    else -> AudioChannel.CENTER
                }
                viewModel.audioEngine.startTone(id, freq, channel)
                slotBinding.buttonTone.setText(R.string.stop_sound)
            }
        }
    }

    private fun setupPresets(slotBindings: List<LayoutToneSlotBinding>) {
        val presetMap = mapOf(
            binding.chipSw31 to 31.5,
            binding.chipSw50 to 50.0,
            binding.chipCross80 to 80.0,
            binding.chipMidBass160 to 160.0,
            binding.chipMid1k to 1000.0,
            binding.chipHigh4k to 4000.0,
            binding.chipAir10k to 10000.0
        )

        presetMap.forEach { (chip, freq) ->
            chip.setOnClickListener {
                // Populate Slot 0 with preset frequency for quick test
                slotBindings[0].editTextFreq.setText(if (freq % 1.0 == 0.0) freq.toInt().toString() else freq.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
