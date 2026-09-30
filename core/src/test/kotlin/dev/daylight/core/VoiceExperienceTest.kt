package dev.daylight.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceExperienceTest {
    private fun voice(name: String, tag: String = "en-US", quality: Int = 400, latency: Int = 200,
        network: Boolean = false, installed: Boolean = true) = OfflineVoice(name, tag, quality, latency, network, installed)

    @Test fun `cloud and not-installed voices never become fallback candidates`() {
        val voices = listOf(voice("cloud", quality = 500, network = true), voice("missing", quality = 500, installed = false), voice("local"))
        assertEquals(listOf("local"), VoiceExperience.available(voices, Locale.US).map { option -> option.name })
        assertEquals("local", VoiceExperience.select(voices, "cloud", Locale.US)?.name)
        assertEquals("local", VoiceExperience.select(voices, "missing", Locale.US)?.name)
    }

    @Test fun `non-English-only engine is reported unavailable without substitution`() {
        assertNull(VoiceExperience.select(listOf(voice("french", "fr-FR")), null, Locale.US))
        assertNull(VoiceExperience.select(emptyList(), null, Locale.US))
    }

    @Test fun `selection uses quality then locale then latency and stable name`() {
        val voices = listOf(voice("us", "en-US"), voice("slow-in", "en-IN", latency = 400), voice("b-in", "en-IN"), voice("a-in", "en-IN"))
        assertEquals("a-in", VoiceExperience.select(voices.reversed(), null, Locale.forLanguageTag("en-IN"))?.name)
        assertEquals("us", VoiceExperience.select(voices, null, Locale.US)?.name)
        assertEquals("higher", VoiceExperience.select(voices + voice("higher", quality = 500), null, Locale.forLanguageTag("en-IN"))?.name)
    }

    @Test fun `explicit installed preference wins over automatic metadata ranking`() {
        val voices = listOf(voice("chosen", quality = 300), voice("other", quality = 500))
        assertEquals("chosen", VoiceExperience.select(voices, "chosen", Locale.US)?.name)
        assertEquals("other", VoiceExperience.select(voices, "removed", Locale.US)?.name)
    }

    @Test fun `only deliberate microphone and audible sample gestures autoplay`() {
        assertTrue(VoiceExperience.shouldSpeakBack(VoiceOrigin.MICROPHONE_GESTURE, true, true, true))
        assertFalse(VoiceExperience.shouldSpeakBack(VoiceOrigin.MICROPHONE_GESTURE, false, true, true))
        assertTrue(VoiceExperience.shouldSpeakBack(VoiceOrigin.SAMPLE_PLAY_BUTTON, false, true, true))
        assertFalse(VoiceExperience.shouldSpeakBack(VoiceOrigin.TYPED, true, true, true))
        assertFalse(VoiceExperience.shouldSpeakBack(VoiceOrigin.DEBUG_FIXTURE, true, true, true))
    }

    @Test fun `background and unavailable voice prevent all autoplay`() {
        VoiceOrigin.values().forEach { origin ->
            assertFalse(VoiceExperience.shouldSpeakBack(origin, true, false, true))
            assertFalse(VoiceExperience.shouldSpeakBack(origin, true, true, false))
        }
    }

    @Test fun `pace presets stay near normal pitch and include a slower option`() {
        assertTrue(SpeechStyle.WARM.rate < SpeechStyle.CLEAR.rate)
        assertTrue(SpeechStyle.UNHURRIED.rate < SpeechStyle.WARM.rate)
        assertTrue(SpeechStyle.values().all { style -> style.rate in 0.8f..1.0f && style.pitch in 0.95f..1.05f })
        assertTrue(VoiceExperience.PREVIEW.contains("not your health data"))
    }
}
