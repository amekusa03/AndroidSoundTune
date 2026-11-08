package com.kusa.soundtune

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper

class AudioAnalyzer {

    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE_SAMPLES = 4096
    }

    interface Listener {
        fun onAudioLevel(rmsDb: Double, targetFreqDb: Double?, peakAmp: Short)
        fun onError(message: String)
    }

    private var listener: Listener? = null
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var targetFrequency: Double? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    fun startListening(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            listener?.onError("Audio recording is not supported on this device.")
            return false
        }

        val bufferSize = minBufferSize.coerceAtLeast(BUFFER_SIZE_SAMPLES * 2)

        // Try UNPROCESSED (raw mic without AGC / noise suppression), fallback to VOICE_RECOGNITION then MIC
        val sources = mutableListOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sources.add(MediaRecorder.AudioSource.UNPROCESSED)
        }
        sources.add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        sources.add(MediaRecorder.AudioSource.MIC)

        var record: AudioRecord? = null
        for (source in sources) {
            try {
                val candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                // Try next source
            }
        }

        if (record == null) {
            listener?.onError("Failed to initialize AudioRecord.")
            return false
        }

        audioRecord = record
        isRecording = true

        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            audioRecord = null
            isRecording = false
            listener?.onError("Cannot start recording: ${e.message}")
            return false
        }

        recordingThread = Thread {
            val audioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)

            while (isRecording) {
                val readCount = record.read(audioBuffer, 0, audioBuffer.size)
                if (readCount > 0) {
                    val rms = AcousticUtils.calculateRms(audioBuffer, 0, readCount)
                    val rmsDb = AcousticUtils.toDbFS(rms)

                    var maxAmp: Short = 0
                    for (i in 0 until readCount) {
                        val abs = Math.abs(audioBuffer[i].toInt()).toShort()
                        if (abs > maxAmp) maxAmp = abs
                    }

                    val targetFreq = targetFrequency
                    val targetDb = if (targetFreq != null && targetFreq > 0) {
                        val mag = AcousticUtils.goertzelMagnitude(audioBuffer, targetFreq, SAMPLE_RATE, 0, readCount)
                        AcousticUtils.toDbFS(mag)
                    } else null

                    mainHandler.post {
                        listener?.onAudioLevel(rmsDb, targetDb, maxAmp)
                    }
                }
            }
        }.apply {
            name = "AudioAnalyzerThread"
            start()
        }

        return true
    }

    fun stopListening() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
        recordingThread = null
    }

    fun isListening(): Boolean = isRecording
}
