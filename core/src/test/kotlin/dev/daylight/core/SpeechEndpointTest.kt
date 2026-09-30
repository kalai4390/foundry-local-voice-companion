package dev.daylight.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechEndpointTest {
    private fun frame(amplitude: Short = 0): ByteArray = ByteArray(3200).apply {
        for (offset in indices step 2) {
            this[offset] = (amplitude.toInt() and 255).toByte()
            this[offset + 1] = (amplitude.toInt() shr 8).toByte()
        }
    }

    @Test fun `silence alone times out without authorizing a transcription`() {
        val endpoint = SpeechEndpoint()
        repeat(69) { assertFalse(endpoint.accept(frame(), 3200)) }
        assertTrue(endpoint.accept(frame(), 3200))
        assertFalse(endpoint.hasVoiceEnergy)
    }

    @Test fun `brief click is not sufficient voice energy`() {
        val endpoint = SpeechEndpoint()
        endpoint.accept(frame(12000), 3200)
        repeat(65) { endpoint.accept(frame(), 3200) }
        assertFalse(endpoint.hasVoiceEnergy)
    }

    @Test fun `enough sound plus one point six seconds of silence finishes a turn`() {
        val endpoint = SpeechEndpoint()
        repeat(5) { assertFalse(endpoint.accept(frame(2000), 3200)) }
        assertTrue(endpoint.hasVoiceEnergy)
        repeat(15) { assertFalse(endpoint.accept(frame(), 3200)) }
        assertTrue(endpoint.accept(frame(), 3200))
    }

    @Test fun `resumed speech resets the trailing silence counter`() {
        val endpoint = SpeechEndpoint()
        repeat(4) { endpoint.accept(frame(-3000), 3200) }
        repeat(12) { assertFalse(endpoint.accept(frame(), 3200)) }
        endpoint.accept(frame(3000), 3200)
        repeat(15) { assertFalse(endpoint.accept(frame(), 3200)) }
        assertTrue(endpoint.accept(frame(), 3200))
    }

    @Test fun `continuous noise or speech still ends at twenty seconds`() {
        val endpoint = SpeechEndpoint()
        repeat(199) { assertFalse(endpoint.accept(frame(3000), 3200)) }
        assertTrue(endpoint.accept(frame(3000), 3200))
    }

    @Test fun `negative PCM samples have positive level and subthreshold noise is not voiced`() {
        val endpoint = SpeechEndpoint()
        repeat(4) { endpoint.accept(frame(100), 3200) }
        assertFalse(endpoint.hasVoiceEnergy)
        repeat(4) { endpoint.accept(frame(-3000), 3200) }
        assertTrue(endpoint.hasVoiceEnergy)
        assertTrue(endpoint.level > 0.08f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `partial PCM sample is rejected`() { SpeechEndpoint().accept(ByteArray(5), 5) }
}
