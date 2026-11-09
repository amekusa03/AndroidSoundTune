package com.kusa.soundtune

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.kusa.soundtune.databinding.FragmentLevelMeterBinding
import com.kusa.soundtune.databinding.ItemSavedCurveBinding
import java.util.UUID

class LevelMeterFragment : Fragment() {

    private var _binding: FragmentLevelMeterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSweep()
        } else {
            binding.textViewSweepStatus.text = "スイープ測定にはマイク権限が必要です。"
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

        binding.toggleGroupSweepChannel.check(R.id.buttonSweepChannelCenter)

        binding.buttonStartSweep.setOnClickListener {
            checkPermissionAndStartSweep()
        }

        refreshGraphAndList()
    }

    private fun checkPermissionAndStartSweep() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSweep()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSweep() {
        val channel = when (binding.toggleGroupSweepChannel.checkedButtonId) {
            R.id.buttonSweepChannelLeft -> AudioChannel.LEFT
            R.id.buttonSweepChannelRight -> AudioChannel.RIGHT
            else -> AudioChannel.CENTER
        }

        binding.buttonStartSweep.isEnabled = false

        viewModel.sweepEngine.startSweepMeasurement(
            channel = channel,
            onStatus = { status ->
                _binding?.textViewSweepStatus?.text = status
            },
            onSuccess = { points ->
                if (_binding == null) return@startSweepMeasurement
                binding.buttonStartSweep.isEnabled = true
                binding.textViewSweepStatus.text = "✅ スイープ測定完了！グラフを保存して比較できます。"

                // Prompt user to save the curve
                showSaveCurveDialog(points)
            },
            onError = { error ->
                if (_binding == null) return@startSweepMeasurement
                binding.buttonStartSweep.isEnabled = true
                binding.textViewSweepStatus.text = "⚠️ $error"
            }
        )
    }

    private fun showSaveCurveDialog(points: List<SweepAcousticUtils.FreqResponsePoint>) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.save_curve_hint)
            setText("測定_${viewModel.sweepEngine.getSavedCurves().size + 1}")
            selectAll()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.save_curve_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim().ifEmpty {
                    "測定_${viewModel.sweepEngine.getSavedCurves().size + 1}"
                }
                val newCurve = NamedCurve(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    color = viewModel.sweepEngine.getNextColor(),
                    points = points,
                    isVisible = true
                )
                viewModel.sweepEngine.addCurve(newCurve)
                refreshGraphAndList()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Show temporarily without saving
                val tempCurve = NamedCurve(
                    id = "temp",
                    name = "今回の測定 (未保存)",
                    color = viewModel.sweepEngine.getNextColor(),
                    points = points,
                    isVisible = true
                )
                binding.frequencyGraphView.setCurves(viewModel.sweepEngine.getSavedCurves() + tempCurve)
            }
            .show()
    }

    private fun refreshGraphAndList() {
        val curves = viewModel.sweepEngine.getSavedCurves()
        binding.frequencyGraphView.setCurves(curves)

        binding.layoutSavedCurvesList.removeAllViews()

        if (curves.isEmpty()) {
            binding.textViewNoSavedCurves.visibility = View.VISIBLE
            binding.layoutSavedCurvesList.addView(binding.textViewNoSavedCurves)
        } else {
            binding.textViewNoSavedCurves.visibility = View.GONE

            curves.forEach { curve ->
                val itemBinding = ItemSavedCurveBinding.inflate(layoutInflater, binding.layoutSavedCurvesList, false)

                // Color circle indicator
                val shape = GradientDrawable().apply {
                    this.shape = GradientDrawable.OVAL
                    setColor(curve.color)
                }
                itemBinding.viewCurveColorIndicator.background = shape

                itemBinding.checkboxCurveVisible.text = curve.name
                itemBinding.checkboxCurveVisible.isChecked = curve.isVisible

                itemBinding.checkboxCurveVisible.setOnCheckedChangeListener { _, isChecked ->
                    curve.isVisible = isChecked
                    binding.frequencyGraphView.invalidate()
                }

                itemBinding.buttonDeleteCurve.setOnClickListener {
                    viewModel.sweepEngine.removeCurve(curve.id)
                    refreshGraphAndList()
                }

                binding.layoutSavedCurvesList.addView(itemBinding.root)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
