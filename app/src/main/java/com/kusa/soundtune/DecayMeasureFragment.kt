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
import com.kusa.soundtune.databinding.FragmentDecayMeasureBinding
import java.util.Locale

class DecayMeasureFragment : Fragment() {

    private var _binding: FragmentDecayMeasureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startDecayMeasurement()
        } else {
            binding.textViewDecayStatus.text = "測定にはマイク権限が必要です。"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecayMeasureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toggleGroupDecayChannel.check(R.id.buttonDecayChannelCenter)

        setupChips()

        binding.buttonStartDecayMeasure.setOnClickListener {
            checkPermissionAndMeasure()
        }

        binding.buttonClearDecayHistory.setOnClickListener {
            viewModel.decayEngine.clearHistory()
            binding.textViewDecayHistory.text = "測定を行うと、周波数ごとの減衰時間リストがここに記録されます。"
        }

        updateHistorySummary()
    }

    private fun setupChips() {
        val chipMap = mapOf(
            binding.chipDecay40 to 40.0,
            binding.chipDecay50 to 50.0,
            binding.chipDecay63 to 63.0,
            binding.chipDecay80 to 80.0,
            binding.chipDecay125 to 125.0,
            binding.chipDecay250 to 250.0,
            binding.chipDecay1k to 1000.0
        )

        chipMap.forEach { (chip, freq) ->
            chip.setOnClickListener {
                binding.editTextDecayFreq.setText(freq.toInt().toString())
            }
        }
    }

    private fun checkPermissionAndMeasure() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startDecayMeasurement()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startDecayMeasurement() {
        val freq = binding.editTextDecayFreq.text.toString().toDoubleOrNull() ?: 80.0
        val channel = when (binding.toggleGroupDecayChannel.checkedButtonId) {
            R.id.buttonDecayChannelLeft -> AudioChannel.LEFT
            R.id.buttonDecayChannelRight -> AudioChannel.RIGHT
            else -> AudioChannel.CENTER
        }

        binding.buttonStartDecayMeasure.isEnabled = false

        viewModel.decayEngine.measureDecay(
            frequency = freq,
            channel = channel,
            onStatus = { status ->
                _binding?.textViewDecayStatus?.text = status
            },
            onComplete = { result ->
                if (_binding == null) return@measureDecay
                binding.buttonStartDecayMeasure.isEnabled = true
                binding.textViewDecayStatus.text = String.format(
                    Locale.US,
                    "測定完了: %dHz [%s] (定常音圧: %.1f dBFS)",
                    result.frequency.toInt(),
                    result.channel.label,
                    result.steadyDb
                )

                // Tail (residual echo beyond 500ms)
                binding.textViewTail.text = String.format(Locale.US, "+%.0f ms", result.tailMs)

                // Received duration vs emitted 500ms
                binding.textViewDurationCompare.text = String.format(
                    Locale.US,
                    "%.0f / %.0f ms",
                    result.receivedDurationMs,
                    result.emittedDurationMs
                )

                // Transmission latency (Bluetooth/OS delay)
                binding.textViewBtLatency.text = String.format(
                    Locale.US,
                    "伝送遅延 (BT/OS): %.0f ms (相殺済)",
                    result.transmissionDelayMs
                )

                // T20 decay tail
                if (result.t20TailMs != null) {
                    binding.textViewT20.text = String.format(Locale.US, "T20減衰: %.0f ms", result.t20TailMs)
                } else {
                    binding.textViewT20.text = "T20減衰: >250 ms"
                }

                binding.textViewDecayDiagnosis.text = result.evaluation.label
                binding.textViewDecayAdvice.text = getDetailedAdvice(result)

                // Visual timeline points (every 40ms up to 240ms after expected shutoff)
                val curveBuilder = StringBuilder("遮断後の減衰: ")
                val samplePoints = result.points.filter { it.timeMs.toInt() % 40 == 0 && it.timeMs in 0.0..240.0 }
                samplePoints.forEach { p ->
                    curveBuilder.append(String.format(Locale.US, "[+%.0fms: %.0fdB] ", p.timeMs, p.relativeDb))
                }
                binding.textViewDecayCurvePoints.text = curveBuilder.toString()

                updateHistorySummary()
            },
            onError = { error ->
                if (_binding == null) return@measureDecay
                binding.buttonStartDecayMeasure.isEnabled = true
                binding.textViewDecayStatus.text = "⚠️ $error"
            }
        )
    }

    private fun getDetailedAdvice(result: DecayResult): String {
        val freq = result.frequency.toInt()
        val tail = result.tailMs
        return when (result.evaluation) {
            DecayEvaluation.TIGHT -> {
                "キレのあるタイトなレスポンスです（残音尾引き: +${tail.toInt()}ms）。音の立ち下がりが素早く、余分な共振やこもりがありません。"
            }
            DecayEvaluation.NORMAL -> {
                "一般的な車内・室内の減衰特性です（残音尾引き: +${tail.toInt()}ms）。低音の量感と輪郭のバランスが取れています。"
            }
            DecayEvaluation.LINGERING -> {
                if (freq <= 80) {
                    "低域（${freq}Hz）の共振が長く残っています（残音尾引き: +${tail.toInt()}ms）。サブウーファーBOX・トランク・ドアの制振材追加や、イコライザーでの${freq}Hz微減衰が効果的です。"
                } else if (freq <= 250) {
                    "ミッドバス帯域（${freq}Hz）でドア鉄板または内張り（ドアトリム）が共振して音が濁っている可能性があります（残音尾引き: +${tail.toInt()}ms）。ドアのデッドニング施工を推奨します。"
                } else {
                    "車内・室内のガラス面や内装からの残響・一次反射が目立ちます（残音尾引き: +${tail.toInt()}ms）。吸音材の配置やスピーカーの指向性見直しを検討してください。"
                }
            }
        }
    }

    private fun updateHistorySummary() {
        val history = viewModel.decayEngine.getHistory()
        if (history.isEmpty()) return

        val builder = StringBuilder()
        history.forEach { item ->
            val t20Str = if (item.t20TailMs != null) String.format(Locale.US, "%.0f ms", item.t20TailMs) else ">250 ms"
            builder.append(
                String.format(
                    Locale.US,
                    "・%dHz [%s]: 残音 = +%.0f ms (実長 %.0fms / T20: %s) - %s\n",
                    item.frequency.toInt(),
                    item.channel.label,
                    item.tailMs,
                    item.receivedDurationMs,
                    t20Str,
                    item.evaluation.label
                )
            )
        }
        binding.textViewDecayHistory.text = builder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
