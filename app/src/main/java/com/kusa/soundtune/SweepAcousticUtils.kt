package com.kusa.soundtune

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object SweepAcousticUtils {

    const val START_FREQ = 20.0
    const val END_FREQ = 20000.0
    const val SWEEP_DURATION_SEC = 4.0
    const val LEAD_IN_CHIRP_DURATION_SEC = 0.08 // 80ms 1kHz sync chirp
    const val LEAD_IN_GAP_SEC = 0.15 // 150ms gap before sweep
    const val TOTAL_AUDIO_SEC = LEAD_IN_CHIRP_DURATION_SEC + LEAD_IN_GAP_SEC + SWEEP_DURATION_SEC

    /**
     * Generates a stereo log-sine sweep audio buffer with a lead-in sync chirp.
     */
    fun generateSweepSignal(
        channel: AudioChannel,
        sampleRate: Int = 44100
    ): ShortArray {
        val totalFrames = (sampleRate * TOTAL_AUDIO_SEC).toInt()
        val buffer = ShortArray(totalFrames * 2)

        val chirpFrames = (sampleRate * LEAD_IN_CHIRP_DURATION_SEC).toInt()
        val gapFrames = (sampleRate * LEAD_IN_GAP_SEC).toInt()
        val sweepStartFrame = chirpFrames + gapFrames
        val sweepFrames = (sampleRate * SWEEP_DURATION_SEC).toInt()

        // 1. Lead-in sync chirp (1.5 kHz Hann-windowed burst)
        for (i in 0 until chirpFrames) {
            val win = 0.5 * (1.0 - cos(2.0 * PI * i / chirpFrames))
            val wave = sin(2.0 * PI * 1500.0 * i / sampleRate)
            val sample = (wave * win * Short.MAX_VALUE).toInt().toShort()
            putStereoSample(buffer, i, sample, channel)
        }

        // 2. Gap (silence)
        for (i in chirpFrames until sweepStartFrame) {
            putStereoSample(buffer, i, 0, channel)
        }

        // 3. Logarithmic Sine Sweep from 20 Hz to 20,000 Hz
        val f1 = START_FREQ
        val f2 = END_FREQ
        val t = SWEEP_DURATION_SEC
        val w1 = 2.0 * PI * f1
        val w2 = 2.0 * PI * f2
        val k = (t * w1) / ln(w2 / w1)
        val l = ln(w2 / w1) / t

        val fadeFrames = (sampleRate * 0.02).toInt() // 20ms fade in/out

        for (i in 0 until sweepFrames) {
            val curTime = i.toDouble() / sampleRate
            // Phase of log sweep
            val phase = k * (exp(curTime * l) - 1.0)
            val wave = sin(phase)

            // Fade envelope to prevent clicking
            val env = when {
                i < fadeFrames -> (i.toDouble() / fadeFrames)
                i > sweepFrames - fadeFrames -> ((sweepFrames - i).toDouble() / fadeFrames)
                else -> 1.0
            }

            val sample = (wave * env * Short.MAX_VALUE).toInt().toShort()
            putStereoSample(buffer, sweepStartFrame + i, sample, channel)
        }

        return buffer
    }

    private fun putStereoSample(buffer: ShortArray, frameIndex: Int, sample: Short, channel: AudioChannel) {
        val idx = frameIndex * 2
        when (channel) {
            AudioChannel.LEFT -> {
                buffer[idx] = sample
                buffer[idx + 1] = 0
            }
            AudioChannel.RIGHT -> {
                buffer[idx] = 0
                buffer[idx + 1] = sample
            }
            AudioChannel.CENTER -> {
                buffer[idx] = sample
                buffer[idx + 1] = sample
            }
        }
    }

    /**
     * Standard 1/3 octave center frequencies (from 25 Hz to 20 kHz)
     */
    val STANDARD_FREQUENCIES = doubleArrayOf(
        25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0,
        315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0,
        3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )

    data class FreqResponsePoint(val freqHz: Double, val levelDb: Double)

    /**
     * Detects lead-in chirp onset, aligns with recorded sweep,
     * and extracts frequency response curve (dBFS across frequencies).
     * High sensitivity design: uses relative energy jump so quiet playback is reliably captured.
     */
    fun extractFrequencyResponse(
        recordedSamples: ShortArray,
        sampleRate: Int = 44100
    ): List<FreqResponsePoint>? {
        if (recordedSamples.size < (sampleRate * 2.0)) return null

        val chirpWindow = (sampleRate * LEAD_IN_CHIRP_DURATION_SEC).toInt()
        val searchLimit = min(recordedSamples.size - (sampleRate * 4), (sampleRate * 1.5).toInt())
        val step = (sampleRate * 0.005).toInt() // 5ms step

        // Measure initial ambient noise floor at 1.5kHz before sound arrives (~first 150ms)
        var noiseSum = 0.0
        var noiseCount = 0
        val noiseEnd = (sampleRate * 0.15).toInt().coerceAtMost(searchLimit)
        for (offset in 0 until noiseEnd step step) {
            val mag = AcousticUtils.goertzelMagnitude(recordedSamples, 1500.0, sampleRate, offset, chirpWindow)
            noiseSum += mag
            noiseCount++
        }
        val noiseFloor = if (noiseCount > 0) noiseSum / noiseCount else 1.0

        var maxChirpMag = 0.0
        var chirpOnsetIndex = -1

        for (offset in 0 until searchLimit step step) {
            val mag = AcousticUtils.goertzelMagnitude(
                recordedSamples,
                1500.0,
                sampleRate,
                offset,
                chirpWindow
            )
            if (mag > maxChirpMag) {
                maxChirpMag = mag
                chirpOnsetIndex = offset
            }
        }

        // Sensitive threshold: chirp must be 2.0x above ambient noise floor,
        // or have minimal absolute magnitude of 8.0 (lowered from 200.0 for quiet volumes)
        val isChirpDetected = chirpOnsetIndex != -1 && (maxChirpMag >= max(8.0, noiseFloor * 1.8))

        // Sweep starts at (chirpOnsetIndex + chirpDuration + gap)
        val sweepStartOffset = if (isChirpDetected) {
            chirpOnsetIndex + (sampleRate * (LEAD_IN_CHIRP_DURATION_SEC + LEAD_IN_GAP_SEC)).toInt()
        } else {
            // Fallback: estimate from signal energy onset if chirp was faint
            val onset = AcousticUtils.detectPulseOnsetIndex(
                recordedSamples,
                noiseFloorWindowSize = 1000,
                thresholdMultiplier = 1.8,
                minAbsoluteThreshold = 50
            ) ?: return null
            onset + (sampleRate * (LEAD_IN_GAP_SEC)).toInt()
        }

        val sweepLength = (sampleRate * SWEEP_DURATION_SEC).toInt()
        if (sweepStartOffset + sweepLength > recordedSamples.size) {
            return null
        }

        // 2. Measure level at each standard frequency along the sweep timeline
        val points = mutableListOf<FreqResponsePoint>()
        val f1 = START_FREQ
        val f2 = END_FREQ
        val t = SWEEP_DURATION_SEC
        val l = ln(f2 / f1) / t

        for (targetFreq in STANDARD_FREQUENCIES) {
            if (targetFreq < f1 || targetFreq > f2) continue

            // In log sweep, instantaneous frequency f(t) = f1 * exp(t * l)
            // So time t when freq is f(t): t = ln(targetFreq / f1) / l
            val timeSec = ln(targetFreq / f1) / l
            val frameCenter = sweepStartOffset + (timeSec * sampleRate).toInt()

            // Window size: longer for low frequencies, shorter for high frequencies
            val cycles = 3.0
            val minWindow = (sampleRate * 0.015).toInt() // 15ms min
            val windowSize = max(minWindow, (sampleRate * (cycles / targetFreq)).toInt())

            val offset = (frameCenter - windowSize / 2).coerceIn(0, recordedSamples.size - windowSize)
            val mag = AcousticUtils.goertzelMagnitude(recordedSamples, targetFreq, sampleRate, offset, windowSize)
            val db = AcousticUtils.toDbFS(mag)
            points.add(FreqResponsePoint(targetFreq, db))
        }

        return points
    }
}
