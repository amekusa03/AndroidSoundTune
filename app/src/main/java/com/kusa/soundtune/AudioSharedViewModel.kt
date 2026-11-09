package com.kusa.soundtune

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AudioSharedViewModel : ViewModel() {

    val audioEngine = AudioEngine()
    val audioAnalyzer = AudioAnalyzer()
    val decayEngine = DecayMeasurementEngine(audioEngine)
    val sweepEngine = SweepMeasurementEngine()

    private val _outputDeviceName = MutableLiveData<String>("Detecting...")
    val outputDeviceName: LiveData<String> = _outputDeviceName

    fun setOutputDeviceName(name: String) {
        _outputDeviceName.postValue(name)
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopAll()
        audioAnalyzer.stopListening()
    }
}
