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

    data class PulseDurationResult(
        val emittedDurationMs: Double,
        val receivedDurationMs: Double,
        val tailMs: Double,
        val t10TailMs: Double?,
        val t20TailMs: Double?,
        val transmissionDelayMs: Double, // The Bluetooth/system latency (onset time)
        val peakSteadyRms: Double,
        val points: List<DecayPoint>
    )

    /**
     * Analyzes recorded audio containing a tone pulse of known emitted length.
     * Cancels out transmission delays (such as Bluetooth ~300ms) and normalizes volume
     * by comparing the duration of the received acoustic sound against the emitted duration.
     */
    fun analyzePulseDuration(
        samples: ShortArray,
        emittedDurationMs: Double,
        sampleRate: Int = 44100,
        targetFreq: Double? = null,
        stepMs: Int = 10,
        windowMs: Int = 25
    ): PulseDurationResult? {
        if (samples.isEmpty()) return null

        val stepSamples = (sampleRate * stepMs) / 1000
        val windowSamples = (sampleRate * windowMs) / 1000

        val timePoints = mutableListOf<Double>()
        val rmsLevels = mutableListOf<Double>()

        var offset = 0
        var currentMs = 0.0

        while (offset + windowSamples <= samples.size) {
            val level = if (targetFreq != null && targetFreq > 0.0) {
                goertzelMagnitude(samples, targetFreq, sampleRate, offset, windowSamples)
            } else {
                calculateRms(samples, offset, windowSamples)
            }
            timePoints.add(currentMs)
            rmsLevels.add(level)

            offset += stepSamples
            currentMs += stepMs
        }

        if (rmsLevels.isEmpty()) return null

        // Find steady-state level (e.g. 95th percentile or max)
        val sortedLevels = rmsLevels.sorted()
        val peakLevel = sortedLevels[(sortedLevels.size * 0.95).toInt()]
        if (peakLevel < 50.0) {
            // Signal too weak / no pulse found
            return null
        }

        // Relative threshold for 50% power (-6dB / amplitude half-power point)
        val halfPowerThreshold = peakLevel * 0.50
        val t10Threshold = peakLevel * 0.3162 // -10dB
        val t20Threshold = peakLevel * 0.1000 // -20dB

        // 1. Detect ON point (rising edge crossing -6dB threshold)
        var onIndex = -1
        for (i in 0 until rmsLevels.size) {
            if (rmsLevels[i] >= halfPowerThreshold) {
                onIndex = i
                break
            }
        }
        if (onIndex == -1) return null

        // 2. Detect OFF point (falling edge crossing -6dB threshold AFTER peak)
        // Ensure we search after reaching at least near-peak
        var maxIndex = onIndex
        for (i in onIndex until rmsLevels.size) {
            if (rmsLevels[i] > rmsLevels[maxIndex]) {
                maxIndex = i
            }
        }

        var offIndex = -1
        for (i in maxIndex until rmsLevels.size) {
            if (rmsLevels[i] < halfPowerThreshold) {
                offIndex = i
                break
            }
        }
        if (offIndex == -1) offIndex = rmsLevels.size - 1

        val onTimeMs = timePoints[onIndex]
        val offTimeMs = timePoints[offIndex]
        val receivedDurationMs = max(0.0, offTimeMs - onTimeMs)
        val tailMs = max(0.0, receivedDurationMs - emittedDurationMs)

        // 3. Find -10dB and -20dB tail decay from expected shutoff point
        val expectedShutoffTimeMs = onTimeMs + emittedDurationMs
        var t10TailMs: Double? = null
        var t20TailMs: Double? = null

        for (i in maxIndex until rmsLevels.size) {
            val t = timePoints[i]
            if (t >= expectedShutoffTimeMs) {
                val delayFromShutoff = t - expectedShutoffTimeMs
                if (t10TailMs == null && rmsLevels[i] <= t10Threshold) {
                    t10TailMs = max(0.0, delayFromShutoff)
                }
                if (t20TailMs == null && rmsLevels[i] <= t20Threshold) {
                    t20TailMs = max(0.0, delayFromShutoff)
                }
            }
        }

        // Relative dB points aligned with shutoff (t=0 at shutoff)
        val relativePoints = mutableListOf<DecayPoint>()
        for (i in 0 until rmsLevels.size) {
            val t = timePoints[i] - expectedShutoffTimeMs
            if (t >= -50.0 && t <= 350.0) { // from 50ms before shutoff to 350ms after
                val relDb = if (rmsLevels[i] > 0.001) {
                    20.0 * log10(rmsLevels[i] / peakLevel)
                } else {
                    -60.0
                }
                relativePoints.add(DecayPoint(t, kotlin.math.min(0.0, relDb)))
            }
        }

        return PulseDurationResult(
            emittedDurationMs = emittedDurationMs,
            receivedDurationMs = receivedDurationMs,
            tailMs = tailMs,
            t10TailMs = t10TailMs,
            t20TailMs = t20TailMs,
            transmissionDelayMs = onTimeMs,
            peakSteadyRms = peakLevel,
            points = relativePoints
        )
    }

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
