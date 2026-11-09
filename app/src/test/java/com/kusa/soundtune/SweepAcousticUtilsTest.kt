package com.kusa.soundtune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

class SweepAcousticUtilsTest {

    @Test
    fun testGenerateSweepSignal() {
        val buffer = SweepAcousticUtils.generateSweepSignal(AudioChannel.CENTER, 44100)
        assertTrue(buffer.isNotEmpty())
        assertEquals((44100 * SweepAcousticUtils.TOTAL_AUDIO_SEC).toInt() * 2, buffer.size)
    }

    @Test
    fun testExtractFrequencyResponseWithLowVolumeAndBluetoothDelay() {
        val sampleRate = 44100
        val totalSec = 6.0
        val totalSamples = (sampleRate * totalSec).toInt()
        val recorded = ShortArray(totalSamples)

        // Simulate Bluetooth delay = 350ms
        val btDelaySamples = (sampleRate * 0.35).toInt()

        // Generate synthetic sweep at low volume (amplitude = 800 out of 32767, ~ -32 dBFS)
        val lowAmp = 800.0

        val chirpFrames = (sampleRate * SweepAcousticUtils.LEAD_IN_CHIRP_DURATION_SEC).toInt()
        val gapFrames = (sampleRate * SweepAcousticUtils.LEAD_IN_GAP_SEC).toInt()
        val sweepStartFrame = chirpFrames + gapFrames
        val sweepFrames = (sampleRate * SweepAcousticUtils.SWEEP_DURATION_SEC).toInt()

        // Chirp
        for (i in 0 until chirpFrames) {
            val win = 0.5 * (1.0 - cos(2.0 * PI * i / chirpFrames))
            val wave = sin(2.0 * PI * 1500.0 * i / sampleRate)
            recorded[btDelaySamples + i] = (wave * win * lowAmp).toInt().toShort()
        }

        // Sweep
        val f1 = SweepAcousticUtils.START_FREQ
        val f2 = SweepAcousticUtils.END_FREQ
        val t = SweepAcousticUtils.SWEEP_DURATION_SEC
        val w1 = 2.0 * PI * f1
        val w2 = 2.0 * PI * f2
        val k = (t * w1) / ln(w2 / w1)
        val l = ln(w2 / w1) / t

        for (i in 0 until sweepFrames) {
            val curTime = i.toDouble() / sampleRate
            val phase = k * (exp(curTime * l) - 1.0)
            val wave = sin(phase)
            recorded[btDelaySamples + sweepStartFrame + i] = (wave * lowAmp).toInt().toShort()
        }

        val result = SweepAcousticUtils.extractFrequencyResponse(recorded, sampleRate)
        assertNotNull("Should detect low-volume sweep signal", result)
        assertTrue(result!!.isNotEmpty())

        // Mid-frequency (e.g. 1000Hz) should be around -32 dBFS ± 5dB
        val p1k = result.find { it.freqHz == 1000.0 }
        assertNotNull(p1k)
        assertEquals(-35.0, p1k!!.levelDb, 6.0)
    }
}
