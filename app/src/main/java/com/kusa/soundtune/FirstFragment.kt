package com.kusa.soundtune

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.kusa.soundtune.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioSharedViewModel by activityViewModels()

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

        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_generator)
                1 -> getString(R.string.tab_level)
                2 -> getString(R.string.tab_decay)
                3 -> getString(R.string.tab_delay)
                else -> ""
            }
        }.attach()
    }

    private fun updateOutputStatus() {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val currentDevice = devices?.firstOrNull { it.isSink }

        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            currentDevice?.productName?.toString()
        } else {
            null
        } ?: getDeviceTypeName(currentDevice?.type)

        viewModel.setOutputDeviceName(deviceName)
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

    override fun onResume() {
        super.onResume()
        updateOutputStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> GeneratorFragment()
                1 -> LevelMeterFragment()
                2 -> DecayMeasureFragment()
                3 -> DelayMeasureFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
