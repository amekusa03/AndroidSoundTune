package com.kusa.soundtune

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

enum class AudioChannel(val label: String) {
    LEFT("L"),
    CENTER("L+R"),
    RIGHT("R")
}

class AudioEngine {

    companion object {
        const val MAX_TONES = 5
        const val SAMPLE_RATE = 44100
    }

    private val audioTracks = arrayOfNulls<AudioTrack>(MAX_TONES)
    private val isPlaying = BooleanArray(MAX_TONES) { false }
    private val toneChannels = Array(MAX_TONES) { AudioChannel.CENTER }

    fun startTone(id: Int, frequency: Double, channel: AudioChannel = toneChannels[id]) {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        stopTone(id)

        toneChannels[id] = channel

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Ensure buffer size is at least a reasonable chunk
        val bufferSize = minBufferSize.coerceAtLeast(4096)

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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTracks[id] = track
        isPlaying[id] = true
        track.play()

        Thread {
            val samples = ShortArray(bufferSize / 2) // short count for stereo (L, R pairs)
            val frameCount = samples.size / 2
            var angle = 0.0
            val angleStep = 2.0 * PI * frequency / SAMPLE_RATE

            while (isPlaying[id]) {
                val currentChannel = toneChannels[id]
                for (i in 0 until frameCount) {
                    val sample = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
                    when (currentChannel) {
                        AudioChannel.LEFT -> {
                            samples[2 * i] = sample
                            samples[2 * i + 1] = 0
                        }
                        AudioChannel.RIGHT -> {
                            samples[2 * i] = 0
                            samples[2 * i + 1] = sample
                        }
                        AudioChannel.CENTER -> {
                            samples[2 * i] = sample
                            samples[2 * i + 1] = sample
                        }
                    }
                    angle += angleStep
                    if (angle > 2.0 * PI) angle -= 2.0 * PI
                }
                audioTracks[id]?.write(samples, 0, samples.size)
            }
        }.start()
    }

    fun setChannel(id: Int, channel: AudioChannel) {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        toneChannels[id] = channel
    }

    fun getChannel(id: Int): AudioChannel {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        return toneChannels[id]
    }

    fun stopTone(id: Int) {
        require(id in 0 until MAX_TONES) { "id must be 0..${MAX_TONES - 1}" }
        isPlaying[id] = false
        try {
            audioTracks[id]?.pause()
            audioTracks[id]?.flush()
            audioTracks[id]?.stop()
            audioTracks[id]?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTracks[id] = null
    }

    fun stopAll() {
        for (id in 0 until MAX_TONES) {
            stopTone(id)
        }
    }

    fun isTonePlaying(id: Int): Boolean = isPlaying[id]

    /**
     * Plays a single synchronized acoustic pulse (e.g., 5-10ms burst) for delay measurement.
     */
    fun playPulse(
        channel: AudioChannel,
        frequency: Double = 1500.0,
        durationMs: Int = 10,
        onPlaybackStarted: () -> Unit = {}
    ) {
        Thread {
            val totalFrames = (SAMPLE_RATE * durationMs / 1000)
            val samples = ShortArray(totalFrames * 2)

            for (i in 0 until totalFrames) {
                // Apply Hann window for smooth envelope and sharp correlation
                val window = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / totalFrames))
                val wave = sin(2.0 * PI * frequency * i / SAMPLE_RATE)
                val sample = (wave * window * Short.MAX_VALUE).toInt().toShort()

                when (channel) {
                    AudioChannel.LEFT -> {
                        samples[2 * i] = sample
                        samples[2 * i + 1] = 0
                    }
                    AudioChannel.RIGHT -> {
                        samples[2 * i] = 0
                        samples[2 * i + 1] = sample
                    }
                    AudioChannel.CENTER -> {
                        samples[2 * i] = sample
                        samples[2 * i + 1] = sample
                    }
                }
            }

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
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            onPlaybackStarted()
            track.play()

            // Wait until played then release
            Thread.sleep(durationMs.toLong() + 50L)
            track.stop()
            track.release()
        }.start()
    }
}
