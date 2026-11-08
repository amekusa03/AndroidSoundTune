package com.kusa.soundtune

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.kusa.soundtune.databinding.FragmentLevelMeterBinding
import java.util.Locale
import kotlin.math.max

class LevelMeterFragment : Fragment() {

    private var _binding: FragmentLevelMeterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

    private val testSlotId = 0 // Use slot 0 for test tone in this tab
    private var frontDb: Double? = null
    private var swDb: Double? = null
    private var bothDb: Double? = null
    private var lastTargetDb: Double? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.cardPermission.visibility = View.GONE
            startMic()
        } else {
            binding.cardPermission.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLevelMeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPermissionCard()
        setupToneControls()
        setupMeterControls()
        setupPhaseEvaluation()
    }

    private fun setupPermissionCard() {
        binding.buttonGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        updatePermissionVisibility()
    }

    private fun updatePermissionVisibility() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        binding.cardPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun setupToneControls() {
        binding.toggleGroupMeterChannel.check(R.id.buttonMeterChannelCenter)

        binding.buttonPlayReferenceTone.setOnClickListener {
            if (viewModel.audioEngine.isTonePlaying(testSlotId)) {
                viewModel.audioEngine.stopTone(testSlotId)
                binding.buttonPlayReferenceTone.setText(R.string.play_sound)
            } else {
                val freq = binding.editTextMeterFreq.text.toString().toDoubleOrNull() ?: 80.0
                val channel = when (binding.toggleGroupMeterChannel.checkedButtonId) {
                    R.id.buttonMeterChannelLeft -> AudioChannel.LEFT
                    R.id.buttonMeterChannelRight -> AudioChannel.RIGHT
                    else -> AudioChannel.CENTER
                }
                viewModel.audioAnalyzer.targetFrequency = freq
                viewModel.audioEngine.startTone(testSlotId, freq, channel)
                binding.buttonPlayReferenceTone.setText(R.string.stop_sound)
            }
        }

        binding.buttonQuick80.setOnClickListener {
            binding.editTextMeterFreq.setText("80")
            viewModel.audioAnalyzer.targetFrequency = 80.0
        }

        binding.buttonQuick1k.setOnClickListener {
            binding.editTextMeterFreq.setText("1000")
            viewModel.audioAnalyzer.targetFrequency = 1000.0
        }
    }

    private fun setupMeterControls() {
        binding.buttonToggleMic.setOnClickListener {
            if (viewModel.audioAnalyzer.isListening()) {
                stopMic()
            } else {
                checkPermissionAndStartMic()
            }
        }

        viewModel.audioAnalyzer.setListener(object : AudioAnalyzer.Listener {
            override fun onAudioLevel(rmsDb: Double, targetFreqDb: Double?, peakAmp: Short) {
                if (_binding == null) return

                lastTargetDb = targetFreqDb ?: rmsDb

                // Overall dB
                binding.textViewTotalDb.text = String.format(Locale.US, "%.1f dBFS", rmsDb)

                // Map -100dB..0dB to progress 0..100
                val progress = (rmsDb + 100.0).toInt().coerceIn(0, 100)
                binding.progressBarLevel.progress = progress

                // Target Freq dB
                val targetText = if (targetFreqDb != null) {
                    val freq = viewModel.audioAnalyzer.targetFrequency?.toInt() ?: 80
                    String.format(Locale.US, "対象周波数 (%dHz): %.1f dBFS", freq, targetFreqDb)
                } else {
                    "対象周波数音圧: -- dBFS"
                }
                binding.textViewTargetDb.text = targetText
                binding.textViewPeakAmp.text = "Peak: $peakAmp"
            }

            override fun onError(message: String) {
                if (_binding == null) return
                binding.textViewTotalDb.text = "Error"
                binding.buttonToggleMic.setText(R.string.start_mic)
            }
        })
    }

    private fun checkPermissionAndStartMic() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startMic()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startMic() {
        val freq = binding.editTextMeterFreq.text.toString().toDoubleOrNull() ?: 80.0
        viewModel.audioAnalyzer.targetFrequency = freq
        val success = viewModel.audioAnalyzer.startListening()
        if (success) {
            binding.buttonToggleMic.setText(R.string.stop_mic)
        }
    }

    private fun stopMic() {
        viewModel.audioAnalyzer.stopListening()
        binding.buttonToggleMic.setText(R.string.start_mic)
        binding.textViewTotalDb.text = "-- dBFS"
        binding.progressBarLevel.progress = 0
    }

    private fun setupPhaseEvaluation() {
        binding.buttonPhaseStep1.setOnClickListener {
            val db = lastTargetDb ?: return@setOnClickListener
            frontDb = db
            binding.textViewPhaseRecord1.text = String.format(Locale.US, "Front: %.1f dB", db)
            evaluatePhase()
        }

        binding.buttonPhaseStep2.setOnClickListener {
            val db = lastTargetDb ?: return@setOnClickListener
            swDb = db
            binding.textViewPhaseRecord2.text = String.format(Locale.US, "SW: %.1f dB", db)
            evaluatePhase()
        }

        binding.buttonPhaseStep3.setOnClickListener {
            val db = lastTargetDb ?: return@setOnClickListener
            bothDb = db
            binding.textViewPhaseRecord3.text = String.format(Locale.US, "Both: %.1f dB", db)
            evaluatePhase()
        }

        binding.buttonPhaseReset.setOnClickListener {
            frontDb = null
            swDb = null
            bothDb = null
            binding.textViewPhaseRecord1.text = "Front: --"
            binding.textViewPhaseRecord2.text = "SW: --"
            binding.textViewPhaseRecord3.text = "Both: --"
            binding.textViewPhaseDiagnosis.text = "各ステップを順番に押して音圧を記録してください。"
        }
    }

    private fun evaluatePhase() {
        val f = frontDb
        val s = swDb
        val b = bothDb

        if (f != null && s != null && b != null) {
            val maxSingle = max(f, s)
            val diff = b - maxSingle
            if (diff >= 1.0) {
                binding.textViewPhaseDiagnosis.text = String.format(
                    Locale.US,
                    "✅ 同相 (In-Phase): 低音が正しく加算されています (+%.1f dB)。位相は揃っています！",
                    diff
                )
            } else if (diff <= -1.0) {
                binding.textViewPhaseDiagnosis.text = String.format(
                    Locale.US,
                    "⚠️ 逆相の可能性 (Phase Cancellation): 両方鳴らすと低音が減衰しています (%.1f dB)。DSPまたはウーファーの位相設定(Phase)を0°/180°反転してください。",
                    diff
                )
            } else {
                binding.textViewPhaseDiagnosis.text = String.format(
                    Locale.US,
                    "ℹ️ 同相と逆相の中間 (差分: %.1f dB)。タイムアライメント（ディレイ）の微調整でさらに低域の量感が向上する可能性があります。",
                    diff
                )
            }
        } else {
            val missing = mutableListOf<String>()
            if (f == null) missing.add("Front")
            if (s == null) missing.add("SW")
            if (b == null) missing.add("Both")
            binding.textViewPhaseDiagnosis.text = "残り測定項目: ${missing.joinToString(", ")}"
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionVisibility()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.audioAnalyzer.setListener(null)
        viewModel.audioAnalyzer.stopListening()
        viewModel.audioEngine.stopTone(testSlotId)
        _binding = null
    }
}
