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
    val t10Ms: Double?,
    val t20Ms: Double?,
    val estimatedRt60Ms: Double?,
    val points: List<AcousticUtils.DecayPoint>,
    val evaluation: DecayEvaluation
)

class DecayMeasurementEngine(private val audioEngine: AudioEngine) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val STEADY_PLAY_MS = 600L
        const val POST_CUTOFF_MS = 500L
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

            mainHandler.post { onStatus("🔊 定常トーン再生中 (${frequency.toInt()}Hz)...") }

            // 1. Play tone for steady-state
            audioEngine.startTone(0, frequency, channel)

            // Read during playback (~600ms)
            val steadySamplesToCapture = (SAMPLE_RATE * 200) / 1000 // last 200ms for RMS
            val steadyBuffer = ShortArray(steadySamplesToCapture)
            val discardSamples = (SAMPLE_RATE * (STEADY_PLAY_MS - 200)) / 1000
            val tempDiscard = ShortArray(2048)

            var discarded = 0
            while (discarded < discardSamples) {
                val r = record.read(tempDiscard, 0, tempDiscard.size.coerceAtMost(discardSamples.toInt() - discarded))
                if (r <= 0) break
                discarded += r
            }

            var steadyRead = 0
            while (steadyRead < steadySamplesToCapture) {
                val r = record.read(steadyBuffer, steadyRead, steadySamplesToCapture - steadyRead)
                if (r <= 0) break
                steadyRead += r
            }

            val steadyRms = AcousticUtils.calculateRms(steadyBuffer, 0, steadyRead)
            val steadyDb = AcousticUtils.toDbFS(steadyRms)

            // 2. Abrupt cutoff
            audioEngine.stopTone(0)
            mainHandler.post { onStatus("🔇 音源遮断 → 残音（減衰）キャプチャ中...") }

            // 3. Capture post-cutoff audio for 500ms
            val postCutoffSamplesCount = (SAMPLE_RATE * POST_CUTOFF_MS) / 1000
            val postCutoffBuffer = ShortArray(postCutoffSamplesCount.toInt())
            var postRead = 0
            while (postRead < postCutoffSamplesCount) {
                val r = record.read(postCutoffBuffer, postRead, postCutoffSamplesCount.toInt() - postRead)
                if (r <= 0) break
                postRead += r
            }

            record.stop()
            record.release()

            if (steadyRms < 100.0) {
                mainHandler.post {
                    onError("テストトーンが小さすぎるか、マイクに届いていません。スピーカーの音量を上げてください。(定常音圧: ${String.format(java.util.Locale.US, "%.1f", steadyDb)} dBFS)")
                }
                return@Thread
            }

            // 4. Analyze decay
            val analysis = AcousticUtils.analyzeDecay(
                postCutoffBuffer,
                steadyStateRms = steadyRms,
                sampleRate = SAMPLE_RATE,
                stepMs = 10,
                windowMs = 20
            )

            val evaluation = when {
                analysis.t20Ms != null && analysis.t20Ms <= 130.0 -> DecayEvaluation.TIGHT
                analysis.t20Ms != null && analysis.t20Ms <= 220.0 -> DecayEvaluation.NORMAL
                else -> DecayEvaluation.LINGERING
            }

            val result = DecayResult(
                frequency = frequency,
                channel = channel,
                steadyDb = steadyDb,
                t10Ms = analysis.t10Ms,
                t20Ms = analysis.t20Ms,
                estimatedRt60Ms = analysis.estimatedRt60Ms,
                points = analysis.points,
                evaluation = evaluation
            )

            history.add(result)

            mainHandler.post {
                onComplete(result)
            }
        }.apply {
            name = "DecayMeasurementThread"
            start()
        }
    }
}
