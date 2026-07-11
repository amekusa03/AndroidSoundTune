package com.kusa.soundtune

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kusa.soundtune.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val audioEngine = AudioEngine()
    private var isPlaying = false

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

        binding.buttonPlay.setOnClickListener {
            togglePlayback()
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

    private fun togglePlayback() {
        if (isPlaying) {
            audioEngine.stop()
            binding.buttonPlay.setText(R.string.play_sound)
            binding.equalizerContainer.removeAllViews()
        } else {
            val freqText = binding.editTextFrequency.text.toString()
            val frequency = freqText.toDoubleOrNull() ?: 440.0
            audioEngine.start(frequency)
            binding.buttonPlay.setText(R.string.stop_sound)
            setupEqualizerUI()
        }
        isPlaying = !isPlaying
    }

    private fun setupEqualizerUI() {
        val eq = audioEngine.getEqualizer() ?: return
        val bands = eq.numberOfBands
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]

        binding.equalizerContainer.removeAllViews()

        for (i in 0 until bands.toInt()) {
            val band = i.toShort()
            val freq = eq.getCenterFreq(band) / 1000 // Convert to Hz

            val bandLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            val label = TextView(context).apply {
                text = getString(R.string.freq_hz_label, freq)
            }
            bandLayout.addView(label)

            val seekBar = SeekBar(context).apply {
                max = (maxLevel - minLevel).toInt()
                progress = eq.getBandLevel(band).toInt() - minLevel
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            audioEngine.setBandLevel(band, (progress + minLevel).toShort())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bandLayout.addView(seekBar)

            binding.equalizerContainer.addView(bandLayout)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            togglePlayback()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioEngine.stop()
        _binding = null
    }
}
