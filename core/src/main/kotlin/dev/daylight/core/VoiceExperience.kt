package dev.daylight.core

import java.util.Locale

enum class VoiceStage { IDLE, LISTENING, TRANSCRIBING, PREPARING_REPLY, STARTING_SPEECH, SPEAKING }

enum class VoiceOrigin { MICROPHONE_GESTURE, SAMPLE_PLAY_BUTTON, TYPED, DEBUG_FIXTURE }

enum class VoiceAudioState { AVAILABLE, RINGING, CALL, COMMUNICATION, OTHER }

enum class SpeechStyle(val label: String, val rate: Float, val pitch: Float) {
    WARM("Warm · easy pace", 0.92f, 0.98f),
    CLEAR("Clear · everyday pace", 1.0f, 1.0f),
    UNHURRIED("Unhurried · slower pace", 0.82f, 0.98f)
}

data class OfflineVoice(
    val name: String,
    val languageTag: String,
    val quality: Int,
    val latency: Int,
    val networkRequired: Boolean,
    val installed: Boolean
) {
    val displayLocale: String get() = Locale.forLanguageTag(languageTag).getDisplayName(Locale.ENGLISH)
}

object VoiceExperience {
    const val PREVIEW = "Hi, I'm Ami. We can take this at your pace. A little perspective on your day, " +
        "or just a gentle moment to pause. This is a voice preview, not your health data."

    fun audioUnavailableMessage(state: VoiceAudioState): String? = when (state) {
        VoiceAudioState.AVAILABLE -> null
        VoiceAudioState.RINGING -> "The phone is ringing. Ami will stay quiet and won't start the microphone. Try Talk or Listen again afterward; typed questions still work."
        VoiceAudioState.CALL -> "A call is using the phone's audio. Ami won't record or speak over it. After the call, tap Talk or Listen again; typed questions still work."
        VoiceAudioState.COMMUNICATION -> "Another voice or video session is using the phone's audio. Ami won't record or speak over it. Try Talk or Listen again afterward; typed questions still work."
        VoiceAudioState.OTHER -> "The phone's audio is reserved by another session. Ami won't start recording or speaking. Try again afterward; typed questions still work."
    }

    fun available(voices: List<OfflineVoice>, locale: Locale): List<OfflineVoice> = voices
        .filter { voice -> voice.installed && !voice.networkRequired && Locale.forLanguageTag(voice.languageTag).language == "en" }
        .sortedWith(compareByDescending<OfflineVoice> { voice -> voice.quality }
            .thenByDescending { voice -> Locale.forLanguageTag(voice.languageTag).country == locale.country }
            .thenBy { voice -> voice.latency }
            .thenBy { voice -> voice.name })

    fun select(voices: List<OfflineVoice>, preferredName: String?, locale: Locale): OfflineVoice? {
        val eligible = available(voices, locale)
        return eligible.firstOrNull { voice -> voice.name == preferredName } ?: eligible.firstOrNull()
    }

    fun shouldSpeakBack(origin: VoiceOrigin, enabled: Boolean, foreground: Boolean, speechReady: Boolean): Boolean =
        foreground && speechReady && (origin == VoiceOrigin.SAMPLE_PLAY_BUTTON || enabled && origin == VoiceOrigin.MICROPHONE_GESTURE)
}

internal object SmallTalk {
    private val greeting = Regex("(?:hi|hello|hey)(?: ami)?(?: how are you)?|good (?:morning|afternoon|evening)(?: ami)?")
    private val presence = Regex("(?:how are you|how's it going|are you there|can you hear me)(?: ami)?")
    private val thanks = Regex("(?:thanks|thank you)(?: ami| so much)?|that's helpful|that helps")
    private val unwind = Regex("(?:i(?:'ve| have) had|i had|it's been) a long day|today was a lot|" +
        "i (?:feel|am|'m) (?:overwhelmed|stressed)|i'm (?:overwhelmed|stressed)|i just want to unwind")
    private val positive = Regex("i (?:feel good|had a good day|am doing well)|i'm doing well")
    private val farewell = Regex("good ?night(?: ami)?|goodbye(?: ami)?|bye(?: ami)?")

    fun reply(commands: String): String? = when {
        greeting.matches(commands) -> "Hi, I'm Ami. Let's take a little pause. Would you like to look at your week, your sleep, or a wind-down idea?"
        presence.matches(commands) -> "I'm here and ready for a quiet catch-up. You can ask about the records you've shared, or we can keep it simple with a wind-down idea."
        thanks.matches(commands) -> "You're welcome. We can leave it there, or take a closer look at something in your week. Your pace."
        unwind.matches(commands) -> "That sounds like a lot. You don't have to turn this into another task. Would you prefer a gentle wind-down idea, or a simple look at your week?"
        positive.matches(commands) -> "That's lovely to hear. If you'd like to keep that thought, you can add a private check-in. Nothing is saved unless you choose to save it."
        farewell.matches(commands) -> "We can leave it there for now. Take your time. When you come back, you choose what to share."
        commands in setOf("who are you", "what can you do", "tell me about yourself") ->
            "I'm Ami, an on-device wellbeing companion. I can talk through shared steps, sleep sessions and workouts, or offer a simple wind-down idea. I'm not a clinician, and you stay in charge of every action."
        else -> null
    }
}
