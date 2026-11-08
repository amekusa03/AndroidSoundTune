package com.kusa.soundtune

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper

enum class TargetSpeaker(val code: String, val displayName: String, val defaultChannel: AudioChannel) {
    FRONT_LEFT("FL", "Front Left (FL)", AudioChannel.LEFT),
    FRONT_RIGHT("FR", "Front Right (FR)", AudioChannel.RIGHT),
    REAR_LEFT("RL", "Rear Left (RL)", AudioChannel.LEFT),
    REAR_RIGHT("RR", "Rear Right (RR)", AudioChannel.RIGHT),
    SUBWOOFER("SW", "Subwoofer (SW)", AudioChannel.CENTER)
}

class DelayMeasurementEngine(private val audioEngine: AudioEngine) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val PRE_RECORD_SAMPLES = 4410 // 100ms baseline
        const val RECORD_WINDOW_SAMPLES = 22050 // 500ms max flight window
    }

    interface Listener {
        fun onMeasurementSuccess(
            speaker: TargetSpeaker,
            measuredTimeMs: Double,
            compensations: Map<TargetSpeaker, AcousticUtils.DelayCompensation>
        )
        fun onMeasurementFailed(speaker: TargetSpeaker, reason: String)
        fun onAllCleared()
    }

    private var listener: Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rawTimesMs = mutableMapOf<TargetSpeaker, Double>()

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun getCompensations(): Map<TargetSpeaker, AcousticUtils.DelayCompensation> {
        return AcousticUtils.calculateDelayCompensations(rawTimesMs)
    }

    fun clearAll() {
        rawTimesMs.clear()
        listener?.onAllCleared()
    }

    @SuppressLint("MissingPermission")
    fun measureSpeaker(speaker: TargetSpeaker) {
        Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize.coerceAtLeast(RECORD_WINDOW_SAMPLES * 2)

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
                    // Try next
                }
            }

            if (record == null) {
                mainHandler.post {
                    listener?.onMeasurementFailed(speaker, "Failed to initialize AudioRecord.")
                }
                return@Thread
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                record.release()
                mainHandler.post {
                    listener?.onMeasurementFailed(speaker, "Cannot start recording: ${e.message}")
                }
                return@Thread
            }

            // Buffer to hold total captured samples (baseline + window)
            val totalBuffer = ShortArray(PRE_RECORD_SAMPLES + RECORD_WINDOW_SAMPLES)
            var totalRead = 0

            // 1. Read pre-record baseline
            while (totalRead < PRE_RECORD_SAMPLES) {
                val read = record.read(totalBuffer, totalRead, PRE_RECORD_SAMPLES - totalRead)
                if (read <= 0) break
                totalRead += read
            }

            // 2. Trigger audio pulse on specified channel
            val pulseFreq = if (speaker == TargetSpeaker.SUBWOOFER) 120.0 else 1500.0
            audioEngine.playPulse(speaker.defaultChannel, frequency = pulseFreq, durationMs = 12)

            // 3. Continue recording during acoustic flight window
            val targetTotal = totalBuffer.size
            while (totalRead < targetTotal) {
                val read = record.read(totalBuffer, totalRead, targetTotal - totalRead)
                if (read <= 0) break
                totalRead += read
            }

            record.stop()
            record.release()

            // 4. Detect onset index relative to playback trigger
            val onsetIndex = AcousticUtils.detectPulseOnsetIndex(
                totalBuffer,
                noiseFloorWindowSize = PRE_RECORD_SAMPLES - 200,
                thresholdMultiplier = 3.2,
                minAbsoluteThreshold = 500
            )

            if (onsetIndex == null || onsetIndex < PRE_RECORD_SAMPLES) {
                mainHandler.post {
                    listener?.onMeasurementFailed(
                        speaker,
                        "Pulse not detected. Please ensure speaker volume is audible and mic is not blocked."
                    )
                }
            } else {
                val sampleOffset = onsetIndex - PRE_RECORD_SAMPLES
                val flightTimeMs = (sampleOffset.toDouble() / SAMPLE_RATE) * 1000.0

                rawTimesMs[speaker] = flightTimeMs
                val compensations = AcousticUtils.calculateDelayCompensations(rawTimesMs)

                mainHandler.post {
                    listener?.onMeasurementSuccess(speaker, flightTimeMs, compensations)
                }
            }
        }.apply {
            name = "DelayMeasureThread"
            start()
        }
    }
}
