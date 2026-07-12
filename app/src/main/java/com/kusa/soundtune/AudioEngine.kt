package com.kusa.soundtune

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class AudioEngine {

    companion object {
        const val MAX_TONES = 5
    }

    private val audioTracks = arrayOfNulls<AudioTrack>(MAX_TONES)
    private val isPlaying = BooleanArray(MAX_TONES) { false }

    fun startTone(id: Int, frequency: Double) {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        stopTone(id)

        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTracks[id] = track
        isPlaying[id] = true
        track.play()

        Thread {
            val samples = ShortArray(minBufferSize)
            var angle = 0.0
            while (isPlaying[id]) {
                for (i in samples.indices) {
                    samples[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
                    angle += 2.0 * PI * frequency / sampleRate
                    if (angle > 2.0 * PI) angle -= 2.0 * PI
                }
                audioTracks[id]?.write(samples, 0, samples.size)
            }
        }.start()
    }

    fun stopTone(id: Int) {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        isPlaying[id] = false
        audioTracks[id]?.stop()
        audioTracks[id]?.release()
        audioTracks[id] = null
    }

    fun stopAll() {
        for (id in 0 until MAX_TONES) {
            stopTone(id)
        }
    }

    fun isTonePlaying(id: Int): Boolean = isPlaying[id]
}