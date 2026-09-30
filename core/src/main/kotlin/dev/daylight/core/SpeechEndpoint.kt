package dev.daylight.core

import kotlin.math.sqrt

class SpeechEndpoint {
    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 20
        const val SILENCE_SECONDS = 1.6
    }

    private var totalSamples = 0
    private var voicedSamples = 0
    private var quietSamples = 0
    var level: Float = 0f
        private set
    val hasVoiceEnergy: Boolean get() = voicedSamples >= SAMPLE_RATE * 0.3

    fun accept(pcm: ByteArray, size: Int): Boolean {
        require(size in 2..pcm.size && size % 2 == 0)
        var squares = 0.0
        for (offset in 0 until size step 2) {
            val sample = ((pcm[offset].toInt() and 255) or (pcm[offset + 1].toInt() shl 8)).toShort().toDouble() / 32768.0
            squares += sample * sample
        }
        val samples = size / 2
        level = sqrt(squares / samples).toFloat()
        totalSamples += samples
        if (level >= 0.012f) {
            voicedSamples += samples
            quietSamples = 0
        } else if (hasVoiceEnergy) quietSamples += samples
        return hasVoiceEnergy && quietSamples >= SAMPLE_RATE * SILENCE_SECONDS ||
            !hasVoiceEnergy && totalSamples >= SAMPLE_RATE * 7 || totalSamples >= SAMPLE_RATE * MAX_SECONDS
    }
}
