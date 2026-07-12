package com.kusa.soundtune

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kusa.soundtune.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val audioEngine = AudioEngine()

    // Pairs of (editText, button) per slot, resolved after view is created
    private data class SlotViews(
        val editText: com.google.android.material.textfield.TextInputEditText,
        val button: android.widget.Button
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateOutputStatus()

        val slots = listOf(
            SlotViews(binding.editTextFreq0, binding.buttonTone0),
            SlotViews(binding.editTextFreq1, binding.buttonTone1),
            SlotViews(binding.editTextFreq2, binding.buttonTone2),
            SlotViews(binding.editTextFreq3, binding.buttonTone3),
            SlotViews(binding.editTextFreq4, binding.buttonTone4),
        )

        slots.forEachIndexed { id, slot ->
            slot.button.setOnClickListener {
                if (audioEngine.isTonePlaying(id)) {
                    audioEngine.stopTone(id)
                    slot.button.setText(R.string.play_sound)
                } else {
                    val freq = slot.editText.text.toString().toDoubleOrNull() ?: 440.0
                    audioEngine.startTone(id, freq)
                    slot.button.setText(R.string.stop_sound)
                }
            }
        }
    }

    private fun updateOutputStatus() {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val currentDevice = devices?.firstOrNull { it.isSink }

        val deviceName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            currentDevice?.productName?.toString()
        } else {
            null
        } ?: getDeviceTypeName(currentDevice?.type)

        binding.textViewOutputStatus.text = getString(R.string.output_status, deviceName)
    }

    private fun getDeviceTypeName(type: Int?): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            else -> "Unknown"
        }
    }

    override fun onPause() {
        super.onPause()
        audioEngine.stopAll()
        // Reset all button labels
        listOf(
            binding.buttonTone0,
            binding.buttonTone1,
            binding.buttonTone2,
            binding.buttonTone3,
            binding.buttonTone4,
        ).forEach { it.setText(R.string.play_sound) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioEngine.stopAll()
        _binding = null
    }
}
