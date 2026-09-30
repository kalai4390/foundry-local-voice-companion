package dev.daylight.local.inference

import android.content.Context
import android.media.AudioManager
import dev.daylight.core.VoiceAudioState
import dev.daylight.core.VoiceExperience

class AudioReadiness(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)

    fun unavailableReason(): String? = VoiceExperience.audioUnavailableMessage(when (manager.mode) {
        AudioManager.MODE_NORMAL -> VoiceAudioState.AVAILABLE
        AudioManager.MODE_RINGTONE -> VoiceAudioState.RINGING
        AudioManager.MODE_IN_CALL -> VoiceAudioState.CALL
        AudioManager.MODE_IN_COMMUNICATION -> VoiceAudioState.COMMUNICATION
        else -> VoiceAudioState.OTHER
    })

    fun requireAvailable() {
        unavailableReason()?.let { reason -> error(reason) }
    }
}
