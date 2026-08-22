package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

object SteamWhistlePlayer {

    private var isPlaying = false

    /**
     * Synthesizes an authentic multi-tone vintage steam locomotive whistle sound
     * using PCM 16-bit audio synthesis (Chords + Steam Hiss Noise + Attack/Decay envelope).
     */
    suspend fun playSteamWhistle(durationSeconds: Double = 1.8) = withContext(Dispatchers.Default) {
        if (isPlaying) return@withContext
        isPlaying = true

        val sampleRate = 44100
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val generatedSound = ShortArray(totalSamples)

        // Frequencies for traditional steam train whistle chord (A4 + C#5 + E5 + Steam turbulence)
        val f1 = 440.0 // A4
        val f2 = 554.37 // C#5
        val f3 = 659.25 // E5
        val f4 = 330.0 // Low bass drone

        val attackSamples = (sampleRate * 0.18).toInt()
        val releaseSamples = (sampleRate * 0.35).toInt()
        val sustainSamples = totalSamples - attackSamples - releaseSamples

        for (i in 0 until totalSamples) {
            val time = i.toDouble() / sampleRate

            // Multi-frequency resonance
            val harmonic1 = sin(2.0 * Math.PI * f1 * time)
            val harmonic2 = 0.8 * sin(2.0 * Math.PI * f2 * time)
            val harmonic3 = 0.5 * sin(2.0 * Math.PI * f3 * time)
            val bass = 0.35 * sin(2.0 * Math.PI * f4 * time)

            // Steam hiss noise (White noise with soft low-pass)
            val steamNoise = (Random.nextDouble() * 2.0 - 1.0) * 0.18

            // Modulation / flutter (simulating pressurized steam airflow flutter)
            val flutter = 1.0 + 0.08 * sin(2.0 * Math.PI * 14.0 * time)

            val rawWave = (harmonic1 + harmonic2 + harmonic3 + bass + steamNoise) * flutter

            // Envelope calculation (Attack - Sustain - Decay)
            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i < attackSamples + sustainSamples -> 1.0
                else -> {
                    val decayStep = i - attackSamples - sustainSamples
                    (1.0 - (decayStep.toDouble() / releaseSamples)).coerceAtLeast(0.0)
                }
            }

            val sampleValue = (rawWave * envelope * 0.75 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            generatedSound[i] = sampleValue.toShort()
        }

        var audioTrack: AudioTrack? = null
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(generatedSound.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(generatedSound, 0, generatedSound.size)
            audioTrack.play()

            // Wait until sound finishes playing
            kotlinx.coroutines.delay((durationSeconds * 1000).toLong() + 200L)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            isPlaying = false
        }
    }
}
