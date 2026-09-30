package dev.daylight.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioAvailabilityTest {
    @Test fun `normal audio does not prevent a deliberate voice turn`() {
        assertNull(VoiceExperience.audioUnavailableMessage(VoiceAudioState.AVAILABLE))
    }

    @Test fun `a telephone call is distinguished from insufficient microphone energy`() {
        val message = requireNotNull(VoiceExperience.audioUnavailableMessage(VoiceAudioState.CALL))
        assertTrue(message.contains("A call"))
        assertTrue(message.contains("won't record or speak"))
        assertTrue(message.contains("typed questions still work"))
    }

    @Test fun `ringing does not start microphone capture`() {
        val message = requireNotNull(VoiceExperience.audioUnavailableMessage(VoiceAudioState.RINGING))
        assertTrue(message.contains("ringing"))
        assertTrue(message.contains("won't start the microphone"))
    }

    @Test fun `voice communication is not misidentified as a telephone call`() {
        val message = requireNotNull(VoiceExperience.audioUnavailableMessage(VoiceAudioState.COMMUNICATION))
        assertTrue(message.contains("voice or video session"))
        assertTrue(message.contains("won't record or speak"))
    }

    @Test fun `an unrecognized reserved audio mode fails closed`() {
        val message = requireNotNull(VoiceExperience.audioUnavailableMessage(VoiceAudioState.OTHER))
        assertTrue(message.contains("reserved"))
        assertTrue(message.contains("won't start recording or speaking"))
    }
}
