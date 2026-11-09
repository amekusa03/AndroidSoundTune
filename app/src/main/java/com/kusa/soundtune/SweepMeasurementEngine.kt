package com.kusa.soundtune

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper

class SweepMeasurementEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        val PALETTE_COLORS = intArrayOf(
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#E91E63"), // Pink
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#FFC107")  // Amber
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val savedCurves = mutableListOf<NamedCurve>()

    fun getSavedCurves(): List<NamedCurve> = savedCurves

    fun addCurve(curve: NamedCurve) {
        savedCurves.add(curve)
    }

    fun removeCurve(id: String) {
        savedCurves.removeAll { it.id == id }
    }

    fun toggleVisibility(id: String) {
        savedCurves.find { it.id == id }?.let {
            it.isVisible = !it.isVisible
        }
    }

    fun getNextColor(): Int {
        return PALETTE_COLORS[savedCurves.size % PALETTE_COLORS.size]
    }

    @SuppressLint("MissingPermission")
    fun startSweepMeasurement(
        channel: AudioChannel,
        onStatus: (String) -> Unit,
        onSuccess: (List<SweepAcousticUtils.FreqResponsePoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            val sweepSignal = SweepAcousticUtils.generateSweepSignal(channel, SAMPLE_RATE)

            // AudioTrack in STREAM or STATIC mode
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(sweepSignal.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(sweepSignal, 0, sweepSignal.size)

            // Setup AudioRecord: buffer to record extra 1.5 seconds for Bluetooth lag
            val totalRecordDurationSec = SweepAcousticUtils.TOTAL_AUDIO_SEC + 1.8
            val recordBufferSize = (SAMPLE_RATE * totalRecordDurationSec).toInt()
            val recordedSamples = ShortArray(recordBufferSize)

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
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
                        minBufferSize.coerceAtLeast(8192)
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
                track.release()
                mainHandler.post { onError("AudioRecord の初期化に失敗しました。") }
                return@Thread
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                record.release()
                track.release()
                mainHandler.post { onError("録音の開始に失敗しました: ${e.message}") }
                return@Thread
            }

            mainHandler.post { onStatus("🔊 スイープ音を再生中 (20Hz → 20kHz)...") }

            // Start playback
            track.play()

            // Record entire duration
            var totalRead = 0
            while (totalRead < recordedSamples.size) {
                val toRead = (recordedSamples.size - totalRead).coerceAtMost(4096)
                val r = record.read(recordedSamples, totalRead, toRead)
                if (r <= 0) break
                totalRead += r
            }

            record.stop()
            record.release()
            track.stop()
            track.release()

            mainHandler.post { onStatus("📊 録音データから周波数特性を解析中...") }

            val responseCurve = SweepAcousticUtils.extractFrequencyResponse(recordedSamples, SAMPLE_RATE)

            if (responseCurve == null || responseCurve.isEmpty()) {
                mainHandler.post {
                    onError("測定信号を検出できませんでした。スピーカーの音量を上げて、マイクが塞がれていないことを確認してください。")
                }
            } else {
                mainHandler.post {
                    onSuccess(responseCurve)
                }
            }
        }.apply {
            name = "SweepMeasurementThread"
            start()
        }
    }
}
