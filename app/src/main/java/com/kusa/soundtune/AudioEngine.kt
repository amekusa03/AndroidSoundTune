package com.kusa.soundtune

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import kotlin.math.PI
import kotlin.math.sin

class AudioEngine {

    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var isPlaying = false

    fun start(frequency: Double) {
        if (isPlaying) stop()

        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
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

        val sessionId = audioTrack?.audioSessionId ?: 0
        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
        }

        isPlaying = true
        audioTrack?.play()

        Thread {
            val samples = ShortArray(minBufferSize)
            var angle = 0.0
            while (isPlaying) {
                for (i in samples.indices) {
                    samples[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
                    angle += 2.0 * PI * frequency / sampleRate
                    if (angle > 2.0 * PI) angle -= 2.0 * PI
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        equalizer?.release()
        equalizer = null
    }

    fun getEqualizer(): Equalizer? = equalizer

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }
}