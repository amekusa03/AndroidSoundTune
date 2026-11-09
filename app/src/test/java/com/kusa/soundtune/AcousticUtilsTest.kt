package com.kusa.soundtune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AcousticUtilsTest {

    @Test
    fun testRmsCalculationForSineWave() {
        val sampleRate = 44100
        val freq = 1000.0
        val amplitude = 10000.0
        val size = 4410 // 100 ms
        val buffer = ShortArray(size)

        for (i in 0 until size) {
            val angle = 2.0 * PI * freq * i / sampleRate
            buffer[i] = (amplitude * sin(angle)).toInt().toShort()
        }

        val rms = AcousticUtils.calculateRms(buffer)
        // Sine wave RMS = Amplitude / sqrt(2) ≈ 10000 / 1.4142 ≈ 7071
        assertEquals(7071.0, rms, 150.0)

        val db = AcousticUtils.toDbFS(rms)
        // 20 * log10(7071 / 32767) ≈ -13.3 dBFS
        assertEquals(-13.3, db, 0.5)
    }

    @Test
    fun testGoertzelMagnitudeRejectsOtherFrequencies() {
        val sampleRate = 44100
        val targetFreq = 1000.0
        val interferingFreq = 200.0
        val size = 4410
        val buffer = ShortArray(size)

        // Generate only interfering frequency (200 Hz)
        for (i in 0 until size) {
            val angle = 2.0 * PI * interferingFreq * i / sampleRate
            buffer[i] = (10000.0 * sin(angle)).toInt().toShort()
        }

        // Measure Goertzel at 1000 Hz when input is 200 Hz
        val magAtTarget = AcousticUtils.goertzelMagnitude(buffer, targetFreq, sampleRate)
        val dbAtTarget = AcousticUtils.toDbFS(magAtTarget)

        // Now generate target frequency (1000 Hz)
        for (i in 0 until size) {
            val angle = 2.0 * PI * targetFreq * i / sampleRate
            buffer[i] = (10000.0 * sin(angle)).toInt().toShort()
        }
        val magAtReal = AcousticUtils.goertzelMagnitude(buffer, targetFreq, sampleRate)
        val dbAtReal = AcousticUtils.toDbFS(magAtReal)

        // Target should be detected strongly, interfering should be rejected (> 40dB difference)
        assertTrue("Expected real target dB ($dbAtReal) to be much higher than rejected ($dbAtTarget)", dbAtReal - dbAtTarget > 40.0)
    }

    @Test
    fun testPulseOnsetDetection() {
        val size = 2000
        val buffer = ShortArray(size)

        // Baseline low noise
        for (i in 0 until 800) {
            buffer[i] = ((i % 10) * 10).toShort()
        }
        // Sudden pulse at index 800
        for (i in 800 until 1000) {
            buffer[i] = 15000.toShort()
        }

        val onset = AcousticUtils.detectPulseOnsetIndex(buffer, noiseFloorWindowSize = 400)
        assertNotNull(onset)
        assertEquals(800.0, onset!!.toDouble(), 35.0)
    }

    @Test
    fun testDelayCompensationCalculations() {
        // FL is closest (10 ms), FR is medium (12 ms), SW is farthest in trunk (15 ms)
        val rawTimes = mapOf(
            "FL" to 10.0,
            "FR" to 12.0,
            "SW" to 15.0
        )

        val compensations = AcousticUtils.calculateDelayCompensations(rawTimes)

        assertEquals(0.0, compensations["SW"]!!.delayCompensationMs, 0.01)
        assertEquals(0.0, compensations["SW"]!!.distanceDiffCm, 0.1)

        assertEquals(5.0, compensations["FL"]!!.delayCompensationMs, 0.01)
        // 5ms * 34.3 cm/ms = 171.5 cm
        assertEquals(171.5, compensations["FL"]!!.distanceDiffCm, 0.5)

        assertEquals(3.0, compensations["FR"]!!.delayCompensationMs, 0.01)
        assertEquals(102.9, compensations["FR"]!!.distanceDiffCm, 0.5)
    }

    @Test
    fun testDecayAnalysis() {
        val sampleRate = 44100
        val durationMs = 300
        val totalSamples = sampleRate * durationMs / 1000
        val buffer = ShortArray(totalSamples)

        // Exponential decay with tau = 50ms
        // A(t) = 10000 * exp(-t / 0.050)
        // At t = 57.5 ms, decay = -10 dB
        // At t = 115.1 ms, decay = -20 dB
        val steadyStateRms = 7071.0 // for 10000 amplitude sine wave

        for (i in 0 until totalSamples) {
            val tSeconds = i.toDouble() / sampleRate
            val envelope = 10000.0 * Math.exp(-tSeconds / 0.050)
            val wave = Math.sin(2.0 * Math.PI * 1000.0 * tSeconds)
            buffer[i] = (envelope * wave).toInt().toShort()
        }

        val result = AcousticUtils.analyzeDecay(buffer, steadyStateRms, sampleRate, stepMs = 10, windowMs = 20)

        assertNotNull(result.t10Ms)
        assertNotNull(result.t20Ms)

        // t10 expected around ~50-60ms, t20 around ~110-120ms
        assertEquals(60.0, result.t10Ms!!, 15.0)
        assertEquals(115.0, result.t20Ms!!, 15.0)
        assertEquals(result.t20Ms!! * 3.0, result.estimatedRt60Ms!!, 0.1)
    }

    @Test
    fun testAnalyzePulseDurationCancelsBluetoothDelay() {
        val sampleRate = 44100
        val totalDurationMs = 1400
        val totalSamples = sampleRate * totalDurationMs / 1000
        val buffer = ShortArray(totalSamples)

        // Simulate Bluetooth delay = 300ms
        // Emitted pulse duration = 500ms
        // Pulse starts arriving at 300ms, ends at 800ms
        // Tail decay adds ~80ms of decay after 800ms
        val btDelayMs = 300.0
        val pulseDurationMs = 500.0
        val tailTauSeconds = 0.040 // 40ms decay time constant

        for (i in 0 until totalSamples) {
            val tMs = (i.toDouble() / sampleRate) * 1000.0
            val wave = Math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)

            val amp = when {
                tMs < btDelayMs -> 0.0 // silence before BT arrives
                tMs < btDelayMs + pulseDurationMs -> 10000.0 // steady pulse
                else -> {
                    val tailTimeSec = (tMs - (btDelayMs + pulseDurationMs)) / 1000.0
                    10000.0 * Math.exp(-tailTimeSec / tailTauSeconds)
                }
            }
            buffer[i] = (amp * wave).toInt().toShort()
        }

        val result = AcousticUtils.analyzePulseDuration(
            samples = buffer,
            emittedDurationMs = pulseDurationMs,
            sampleRate = sampleRate,
            targetFreq = 1000.0,
            stepMs = 10,
            windowMs = 25
        )

        assertNotNull(result)
        // Transmission delay should accurately reflect the 300ms Bluetooth delay
        assertEquals(300.0, result!!.transmissionDelayMs, 20.0)

        // Emitted duration is 500ms
        assertEquals(500.0, result.emittedDurationMs, 0.1)

        // Received duration should be roughly 500ms + tail (around 520~550ms at half-power -6dB)
        assertTrue("Received duration should exceed emitted", result.receivedDurationMs >= 500.0)

        // Tail duration at -6dB should be around 25~45ms (ln(2)*40ms ≈ 27.7ms)
        assertEquals(30.0, result.tailMs, 20.0)
    }
}
