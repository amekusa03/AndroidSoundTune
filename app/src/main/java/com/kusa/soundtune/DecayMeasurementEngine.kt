package com.kusa.soundtune

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper

enum class DecayEvaluation(val label: String, val description: String) {
    TIGHT("✅ キレ良好 (Tight)", "余分な共振や残音がなく、素早く減衰しています。"),
    NORMAL("ℹ️ 標準 (Standard)", "一般的な車内・室内の減衰特性です。"),
    LINGERING("⚠️ 残音・共振大 (Boomy)", "音が鳴り止んだ後も共振が長く残っています。デッドニング（制振・吸音）やEQ調整を推奨します。")
}

data class DecayResult(
    val frequency: Double,
    val channel: AudioChannel,
    val steadyDb: Double,
    val emittedDurationMs: Double,
    val receivedDurationMs: Double,
    val tailMs: Double,
    val t10TailMs: Double?,
    val t20TailMs: Double?,
    val transmissionDelayMs: Double, // The measured Bluetooth/OS latency
    val points: List<AcousticUtils.DecayPoint>,
    val evaluation: DecayEvaluation
)

class DecayMeasurementEngine(private val audioEngine: AudioEngine) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val EMITTED_PULSE_MS = 500L
        // Capture window needs to cover: Bluetooth delay (up to ~600ms) + pulse (500ms) + tail decay (~500ms) = ~1600ms
        const val TOTAL_CAPTURE_MS = 1600L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val history = mutableListOf<DecayResult>()

    fun getHistory(): List<DecayResult> = history

    fun clearHistory() {
        history.clear()
    }

    @SuppressLint("MissingPermission")
    fun measureDecay(
        frequency: Double,
        channel: AudioChannel = AudioChannel.CENTER,
        onStatus: (String) -> Unit,
        onComplete: (DecayResult) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize.coerceAtLeast(8192)

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
                mainHandler.post { onError("AudioRecord の初期化に失敗しました。") }
                return@Thread
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                record.release()
                mainHandler.post { onError("録音の開始に失敗しました: ${e.message}") }
                return@Thread
            }

            mainHandler.post { onStatus("🔊 500ms 基準パルス発振中 (${frequency.toInt()}Hz)...") }

            // 1. Play precise 500ms tone pulse
            audioEngine.startTone(0, frequency, channel)

            // Start capture in parallel
            val totalSamplesCount = (SAMPLE_RATE * TOTAL_CAPTURE_MS / 1000).toInt()
            val capturedBuffer = ShortArray(totalSamplesCount)
            var totalRead = 0

            val toneStopThread = Thread {
                Thread.sleep(EMITTED_PULSE_MS)
                audioEngine.stopTone(0)
                mainHandler.post { onStatus("🔇 パルス遮断 → 受音波形＆尾引き（残音）解析中...") }
            }
            toneStopThread.start()

            // Read continuous audio stream until full window is captured
            while (totalRead < totalSamplesCount) {
                val toRead = (totalSamplesCount - totalRead).coerceAtMost(2048)
                val r = record.read(capturedBuffer, totalRead, toRead)
                if (r <= 0) break
                totalRead += r
            }

            record.stop()
            record.release()
            toneStopThread.join()

            // 2. Analyze pulse duration (length comparison)
            val result = AcousticUtils.analyzePulseDuration(
                samples = capturedBuffer,
                emittedDurationMs = EMITTED_PULSE_MS.toDouble(),
                sampleRate = SAMPLE_RATE,
                targetFreq = frequency,
                stepMs = 10,
                windowMs = 25
            )

            if (result == null) {
                mainHandler.post {
                    onError("テストトーンが小さすぎるか、マイクに届いていません。スピーカーの音量を上げて再度お試しください。")
                }
                return@Thread
            }

            val steadyDb = AcousticUtils.toDbFS(result.peakSteadyRms)

            // Evaluation based on tail length beyond the emitted 500ms
            // Tight: tail <= 40ms, Normal: tail <= 90ms, Lingering: tail > 90ms
            val evaluation = when {
                result.tailMs <= 45.0 -> DecayEvaluation.TIGHT
                result.tailMs <= 100.0 -> DecayEvaluation.NORMAL
                else -> DecayEvaluation.LINGERING
            }

            val decayResult = DecayResult(
                frequency = frequency,
                channel = channel,
                steadyDb = steadyDb,
                emittedDurationMs = result.emittedDurationMs,
                receivedDurationMs = result.receivedDurationMs,
                tailMs = result.tailMs,
                t10TailMs = result.t10TailMs,
                t20TailMs = result.t20TailMs,
                transmissionDelayMs = result.transmissionDelayMs,
                points = result.points,
                evaluation = evaluation
            )

            history.add(decayResult)

            mainHandler.post {
                onComplete(decayResult)
            }
        }.apply {
            name = "DecayMeasurementThread"
            start()
        }
    }
}
