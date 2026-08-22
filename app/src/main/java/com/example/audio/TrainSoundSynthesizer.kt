package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class TrainSoundType(val id: String, val titleAr: String, val descriptionAr: String, val iconEmoji: String) {
    STEAM_WHISTLE("steam", "صافرة قطار البخار القديم", "صافرة فحم كلاسيكية مع تدفق البخار المتعدد النغمات", "🚂"),
    ELECTRIC_HORN("electric", "بوق قطار الضواحي الكهربائي", "بوق قطار السكك الحديدية الحديث ثنائي التردد", "🚆"),
    STATION_CHIME("station_chime", "رنين إعلان المحطة", "نغمة الأجراس الرباعية الكلاسيكية لقدوم القطار", "🔔"),
    DIESEL_HORN("diesel", "بوق قطار الديزل القوي", "نغمة منخفضة وقوية لقطارات المسافات الطويلة", "🚋")
}

object TrainSoundSynthesizer {

    private var isPlaying = false

    suspend fun playSound(soundType: TrainSoundType) = withContext(Dispatchers.Default) {
        if (isPlaying) return@withContext
        isPlaying = true

        try {
            when (soundType) {
                TrainSoundType.STEAM_WHISTLE -> playSteamWhistle()
                TrainSoundType.ELECTRIC_HORN -> playElectricHorn()
                TrainSoundType.STATION_CHIME -> playStationChime()
                TrainSoundType.DIESEL_HORN -> playDieselHorn()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isPlaying = false
        }
    }

    private suspend fun playSteamWhistle() {
        val sampleRate = 44100
        val duration = 1.8
        val totalSamples = (sampleRate * duration).toInt()
        val sound = ShortArray(totalSamples)

        val f1 = 440.0 // A4
        val f2 = 554.37 // C#5
        val f3 = 659.25 // E5
        val f4 = 330.0 // Low drone

        val attackSamples = (sampleRate * 0.18).toInt()
        val releaseSamples = (sampleRate * 0.35).toInt()
        val sustainSamples = totalSamples - attackSamples - releaseSamples

        for (i in 0 until totalSamples) {
            val time = i.toDouble() / sampleRate
            val harmonic1 = sin(2.0 * Math.PI * f1 * time)
            val harmonic2 = 0.8 * sin(2.0 * Math.PI * f2 * time)
            val harmonic3 = 0.5 * sin(2.0 * Math.PI * f3 * time)
            val bass = 0.35 * sin(2.0 * Math.PI * f4 * time)
            val steamNoise = (Random.nextDouble() * 2.0 - 1.0) * 0.18
            val flutter = 1.0 + 0.08 * sin(2.0 * Math.PI * 14.0 * time)

            val rawWave = (harmonic1 + harmonic2 + harmonic3 + bass + steamNoise) * flutter

            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i < attackSamples + sustainSamples -> 1.0
                else -> {
                    val decayStep = i - attackSamples - sustainSamples
                    (1.0 - (decayStep.toDouble() / releaseSamples)).coerceAtLeast(0.0)
                }
            }

            val sampleValue = (rawWave * envelope * 0.75 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            sound[i] = sampleValue.toShort()
        }

        playPcmTrack(sound, sampleRate)
    }

    private suspend fun playElectricHorn() {
        val sampleRate = 44100
        val duration = 1.5
        val totalSamples = (sampleRate * duration).toInt()
        val sound = ShortArray(totalSamples)

        // Dual Tone Modern High Speed Train Horn (311Hz + 370Hz + 470Hz)
        val f1 = 311.13 // Eb4
        val f2 = 370.0 // F#4
        val f3 = 466.16 // Bb4

        val attackSamples = (sampleRate * 0.05).toInt()
        val releaseSamples = (sampleRate * 0.25).toInt()
        val sustainSamples = totalSamples - attackSamples - releaseSamples

        for (i in 0 until totalSamples) {
            val time = i.toDouble() / sampleRate
            val tone = sin(2.0 * Math.PI * f1 * time) + 0.9 * sin(2.0 * Math.PI * f2 * time) + 0.7 * sin(2.0 * Math.PI * f3 * time)

            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i < attackSamples + sustainSamples -> 1.0
                else -> {
                    val decay = i - attackSamples - sustainSamples
                    (1.0 - (decay.toDouble() / releaseSamples)).coerceAtLeast(0.0)
                }
            }

            sound[i] = (tone * envelope * 0.65 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playPcmTrack(sound, sampleRate)
    }

    private suspend fun playStationChime() {
        val sampleRate = 44100
        // 4 note chime (C5 - G4 - A4 - F4) like European/Algerian train stations
        val notes = listOf(523.25, 392.00, 440.00, 349.23)
        val noteDuration = 0.38
        val noteSamples = (sampleRate * noteDuration).toInt()
        val totalSamples = noteSamples * notes.size
        val sound = ShortArray(totalSamples)

        notes.forEachIndexed { noteIdx, freq ->
            val offset = noteIdx * noteSamples
            for (i in 0 until noteSamples) {
                val time = i.toDouble() / sampleRate
                // Bell chime with rich harmonics and exponential decay
                val wave = sin(2.0 * Math.PI * freq * time) + 0.4 * sin(4.0 * Math.PI * freq * time) + 0.2 * sin(6.0 * Math.PI * freq * time)
                val decay = exp(-4.5 * time)
                val sampleValue = (wave * decay * 0.8 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                sound[offset + i] = sampleValue.toShort()
            }
        }

        playPcmTrack(sound, sampleRate)
    }

    private suspend fun playDieselHorn() {
        val sampleRate = 44100
        val duration = 1.6
        val totalSamples = (sampleRate * duration).toInt()
        val sound = ShortArray(totalSamples)

        val f1 = 220.0 // Low A3
        val f2 = 277.18 // C#4
        val f3 = 329.63 // E4

        for (i in 0 until totalSamples) {
            val time = i.toDouble() / sampleRate
            val tone = sin(2.0 * Math.PI * f1 * time) + 0.85 * sin(2.0 * Math.PI * f2 * time) + 0.65 * sin(2.0 * Math.PI * f3 * time)
            val envelope = when {
                i < (sampleRate * 0.08) -> i.toDouble() / (sampleRate * 0.08)
                i < (totalSamples - sampleRate * 0.3) -> 1.0
                else -> {
                    val d = i - (totalSamples - sampleRate * 0.3)
                    (1.0 - (d.toDouble() / (sampleRate * 0.3))).coerceAtLeast(0.0)
                }
            }
            sound[i] = (tone * envelope * 0.7 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playPcmTrack(sound, sampleRate)
    }

    private suspend fun playPcmTrack(sound: ShortArray, sampleRate: Int) = withContext(Dispatchers.Default) {
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
                .setBufferSizeInBytes(sound.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(sound, 0, sound.size)
            audioTrack.play()

            val durMs = (sound.size.toDouble() / sampleRate * 1000).toLong()
            kotlinx.coroutines.delay(durMs + 150L)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
        }
    }
}
