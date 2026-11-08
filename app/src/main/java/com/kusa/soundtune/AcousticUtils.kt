package com.kusa.soundtune

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

object AcousticUtils {

    const val SPEED_OF_SOUND_MPS = 343.0 // Speed of sound at ~20°C in m/s

    /**
     * Calculates the Root-Mean-Square (RMS) amplitude of a PCM 16-bit buffer.
     */
    fun calculateRms(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Double {
        if (length <= 0) return 0.0
        var sumSquares = 0.0
        val end = offset + length
        for (i in offset until end) {
            val v = samples[i].toDouble()
            sumSquares += v * v
        }
        return sqrt(sumSquares / length)
    }

    /**
     * Converts an RMS amplitude to decibels relative to full scale (dBFS).
     * Reference full scale for 16-bit signed PCM is 32767.0.
     */
    fun toDbFS(rms: Double, maxVal: Double = 32767.0): Double {
        if (rms <= 0.00001) return -100.0
        val db = 20.0 * log10(rms / maxVal)
        return max(-100.0, db)
    }

    /**
     * Calculates the magnitude of a specific frequency using the Goertzel algorithm.
     * This acts as an efficient, sharp single-frequency bandpass filter to measure
     * test tone level while rejecting ambient noise.
     */
    fun goertzelMagnitude(
        samples: ShortArray,
        targetFreq: Double,
        sampleRate: Int = 44100,
        offset: Int = 0,
        length: Int = samples.size
    ): Double {
        if (length <= 0 || targetFreq <= 0.0) return 0.0

        val k = ((0.5 + (length * targetFreq / sampleRate))).toInt()
        val omega = (2.0 * PI * k) / length
        val coeff = 2.0 * cos(omega)

        var sPrev = 0.0
        var sPrev2 = 0.0
        val end = offset + length

        for (i in offset until end) {
            val sample = samples[i].toDouble()
            val s = sample + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }

        val power = sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2
        if (power <= 0.0) return 0.0

        // Normalization for DFT bin magnitude to equivalent sine RMS
        val magnitude = sqrt(power) / (length / 2.0)
        return magnitude
    }

    /**
     * Detects the onset sample index of an acoustic pulse in a recorded buffer.
     * Uses noise-floor tracking and energy threshold jump detection.
     */
    fun detectPulseOnsetIndex(
        samples: ShortArray,
        noiseFloorWindowSize: Int = 500,
        thresholdMultiplier: Double = 3.5,
        minAbsoluteThreshold: Short = 800
    ): Int? {
        if (samples.size < noiseFloorWindowSize * 2) return null

        // Calculate baseline noise floor from the beginning
        var noiseSum = 0.0
        for (i in 0 until noiseFloorWindowSize) {
            val absVal = Math.abs(samples[i].toInt()).toDouble()
            noiseSum += absVal
        }
        val avgNoise = noiseSum / noiseFloorWindowSize
        val threshold = max(minAbsoluteThreshold.toDouble(), avgNoise * thresholdMultiplier)

        // Find where amplitude jumps over threshold
        val checkWindow = 32
        for (i in noiseFloorWindowSize until (samples.size - checkWindow)) {
            var windowSum = 0.0
            for (w in 0 until checkWindow) {
                windowSum += Math.abs(samples[i + w].toInt())
            }
            val windowAvg = windowSum / checkWindow
            if (windowAvg > threshold) {
                return i
            }
        }
        return null
    }

    /**
     * Converts travel time (milliseconds) to physical distance (centimeters).
     */
    fun timeMsToDistanceCm(timeMs: Double): Double {
        return (timeMs / 1000.0) * SPEED_OF_SOUND_MPS * 100.0
    }

    data class DelayCompensation(
        val rawTimeMs: Double,
        val delayCompensationMs: Double,
        val distanceDiffCm: Double
    )

    /**
     * Given raw arrival times (in ms) for different speakers,
     * computes the recommended DSP delay compensation relative to the farthest speaker.
     */
    fun <T> calculateDelayCompensations(rawTimesMs: Map<T, Double>): Map<T, DelayCompensation> {
        if (rawTimesMs.isEmpty()) return emptyMap()

        val maxTime = rawTimesMs.values.maxOrNull() ?: return emptyMap()

        return rawTimesMs.mapValues { (_, time) ->
            val compMs = max(0.0, maxTime - time)
            val distCm = (compMs / 1000.0) * SPEED_OF_SOUND_MPS * 100.0
            DelayCompensation(
                rawTimeMs = time,
                delayCompensationMs = compMs,
                distanceDiffCm = distCm
            )
        }
    }

    data class DecayPoint(val timeMs: Double, val relativeDb: Double)

    data class DecayAnalysisResult(
        val steadyStateRms: Double,
        val t10Ms: Double?,
        val t20Ms: Double?,
        val estimatedRt60Ms: Double?,
        val points: List<DecayPoint>
    )

    /**
     * Analyzes decay of audio after cutoff.
     * @param postCutoffSamples PCM 16-bit audio samples immediately following sound cutoff.
     * @param steadyStateRms Baseline RMS measured during the steady-state tone playback.
     */
    fun analyzeDecay(
        postCutoffSamples: ShortArray,
        steadyStateRms: Double,
        sampleRate: Int = 44100,
        stepMs: Int = 10,
        windowMs: Int = 20
    ): DecayAnalysisResult {
        if (postCutoffSamples.isEmpty() || steadyStateRms <= 0.0) {
            return DecayAnalysisResult(steadyStateRms, null, null, null, emptyList())
        }

        val stepSamples = (sampleRate * stepMs) / 1000
        val windowSamples = (sampleRate * windowMs) / 1000
        val points = mutableListOf<DecayPoint>()

        var t10Ms: Double? = null
        var t20Ms: Double? = null

        var offset = 0
        var currentMs = 0.0

        while (offset + windowSamples <= postCutoffSamples.size) {
            val rms = calculateRms(postCutoffSamples, offset, windowSamples)
            val relDb = if (rms > 0.0001) {
                20.0 * log10(rms / steadyStateRms)
            } else {
                -60.0
            }
            val clampedDb = kotlin.math.min(0.0, relDb)
            points.add(DecayPoint(currentMs, clampedDb))

            if (t10Ms == null && clampedDb <= -10.0) {
                t10Ms = currentMs
            }
            if (t20Ms == null && clampedDb <= -20.0) {
                t20Ms = currentMs
            }

            offset += stepSamples
            currentMs += stepMs
        }

        val estimatedRt60 = if (t20Ms != null) {
            t20Ms * 3.0
        } else if (t10Ms != null) {
            t10Ms * 6.0
        } else null

        return DecayAnalysisResult(
            steadyStateRms = steadyStateRms,
            t10Ms = t10Ms,
            t20Ms = t20Ms,
            estimatedRt60Ms = estimatedRt60,
            points = points
        )
    }
}
