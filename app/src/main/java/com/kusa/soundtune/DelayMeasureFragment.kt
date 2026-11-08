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
import com.kusa.soundtune.databinding.FragmentDelayMeasureBinding
import com.kusa.soundtune.databinding.LayoutSpeakerDelayRowBinding
import java.util.Locale

class DelayMeasureFragment : Fragment() {

    private var _binding: FragmentDelayMeasureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

    private var pendingSpeakerToMeasure: TargetSpeaker? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingSpeakerToMeasure?.let { startMeasure(it) }
        } else {
            binding.textViewDelayStatus.text = "測定にはマイク権限が必要です。"
        }
        pendingSpeakerToMeasure = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDelayMeasureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpeakerRow(binding.rowSpeakerFL, TargetSpeaker.FRONT_LEFT)
        setupSpeakerRow(binding.rowSpeakerFR, TargetSpeaker.FRONT_RIGHT)
        setupSpeakerRow(binding.rowSpeakerRL, TargetSpeaker.REAR_LEFT)
        setupSpeakerRow(binding.rowSpeakerRR, TargetSpeaker.REAR_RIGHT)
        setupSpeakerRow(binding.rowSpeakerSW, TargetSpeaker.SUBWOOFER)

        binding.buttonResetDelays.setOnClickListener {
            viewModel.delayEngine.clearAll()
        }

        viewModel.delayEngine.setListener(object : DelayMeasurementEngine.Listener {
            override fun onMeasurementSuccess(
                speaker: TargetSpeaker,
                measuredTimeMs: Double,
                compensations: Map<TargetSpeaker, AcousticUtils.DelayCompensation>
            ) {
                if (_binding == null) return
                binding.textViewDelayStatus.text = "${speaker.displayName} の測定完了 (${String.format(Locale.US, "%.1f ms", measuredTimeMs)})"
                updateAllRows(compensations)
                updateSummary(compensations)
            }

            override fun onMeasurementFailed(speaker: TargetSpeaker, reason: String) {
                if (_binding == null) return
                binding.textViewDelayStatus.text = "⚠️ ${speaker.code} 測定失敗: $reason"
            }

            override fun onAllCleared() {
                if (_binding == null) return
                val rows = listOf(
                    binding.rowSpeakerFL,
                    binding.rowSpeakerFR,
                    binding.rowSpeakerRL,
                    binding.rowSpeakerRR,
                    binding.rowSpeakerSW
                )
                rows.forEach { row ->
                    row.textViewFlightTime.text = "到達時間: 未測定"
                    row.textViewCompDelay.text = "補正: --"
                }
                binding.textViewDelayStatus.text = "測定データをリセットしました。"
                binding.textViewDspSummary.text = "各スピーカーを測定すると、最も遠いスピーカーに合わせたDSPディレイ設定値が表示されます。"
            }
        })
    }

    private fun setupSpeakerRow(rowBinding: LayoutSpeakerDelayRowBinding, speaker: TargetSpeaker) {
        rowBinding.textViewSpeakerName.text = speaker.displayName
        rowBinding.textViewSpeakerChannel.text = "CH: ${speaker.defaultChannel.label}"

        rowBinding.buttonMeasureSpeaker.setOnClickListener {
            checkPermissionAndMeasure(speaker)
        }
    }

    private fun checkPermissionAndMeasure(speaker: TargetSpeaker) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startMeasure(speaker)
        } else {
            pendingSpeakerToMeasure = speaker
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startMeasure(speaker: TargetSpeaker) {
        binding.textViewDelayStatus.text = "🔊 ${speaker.displayName} を測定中… パルス音を発振します"
        viewModel.delayEngine.measureSpeaker(speaker)
    }

    private fun updateAllRows(compensations: Map<TargetSpeaker, AcousticUtils.DelayCompensation>) {
        val speakerRowMap = mapOf(
            TargetSpeaker.FRONT_LEFT to binding.rowSpeakerFL,
            TargetSpeaker.FRONT_RIGHT to binding.rowSpeakerFR,
            TargetSpeaker.REAR_LEFT to binding.rowSpeakerRL,
            TargetSpeaker.REAR_RIGHT to binding.rowSpeakerRR,
            TargetSpeaker.SUBWOOFER to binding.rowSpeakerSW
        )

        speakerRowMap.forEach { (speaker, row) ->
            val comp = compensations[speaker]
            if (comp != null) {
                row.textViewFlightTime.text = String.format(Locale.US, "到達時間: %.1f ms", comp.rawTimeMs)
                if (comp.delayCompensationMs <= 0.05) {
                    row.textViewCompDelay.text = "基準 (0.0 ms / 最遠)"
                } else {
                    row.textViewCompDelay.text = String.format(
                        Locale.US,
                        "遅延: +%.2f ms (%.1f cm)",
                        comp.delayCompensationMs,
                        comp.distanceDiffCm
                    )
                }
            }
        }
    }

    private fun updateSummary(compensations: Map<TargetSpeaker, AcousticUtils.DelayCompensation>) {
        if (compensations.isEmpty()) return

        val builder = StringBuilder()
        builder.append("【DSP入力用推奨ディレイ値】\n")
        TargetSpeaker.values().forEach { speaker ->
            val comp = compensations[speaker]
            if (comp != null) {
                if (comp.delayCompensationMs <= 0.05) {
                    builder.append("・${speaker.code}: 0.00 ms (0.0 cm) ※基準スピーカー\n")
                } else {
                    builder.append(
                        String.format(
                            Locale.US,
                            "・%s: +%.2f ms (%.1f cm)\n",
                            speaker.code,
                            comp.delayCompensationMs,
                            comp.distanceDiffCm
                        )
                    )
                }
            } else {
                builder.append("・${speaker.code}: 未測定\n")
            }
        }
        builder.append("\n※最も遠いスピーカーに合わせて手前のスピーカーを遅延させることで、音が耳に同時に到達します。")
        binding.textViewDspSummary.text = builder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.delayEngine.setListener(null)
        _binding = null
    }
}
