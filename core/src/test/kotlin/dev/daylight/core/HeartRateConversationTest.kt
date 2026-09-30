package dev.daylight.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateConversationTest {
    private val engine = CompanionEngine()
    private val today = LocalDate.of(2026, 9, 27)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val data = SyntheticData.create(today).copy(zone = zone, heartRate = HeartRateHistory(
        HeartRateStatus.READY,
        listOf(sample("2026-08-10", 153), sample("2026-08-31", 135), sample("2026-09-25", 142)),
        readStart = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant(),
        readEnd = today.atTime(12, 0).atZone(zone).toInstant()
    ), fetchedAt = today.atTime(12, 0).atZone(zone).toInstant())

    private fun sample(date: String, bpm: Long) = HeartRateSample(LocalDate.parse(date).atTime(10, 0).atZone(zone).toInstant(), bpm)
    private fun followUp(question: String, previous: Reply, snapshot: HealthSnapshot = data) =
        engine.answer(question, snapshot, previousTopic = previous.topic, heartRateContext = previous.heartRateContext)

    @Test fun `reported voice transcript asks one clarification preserving last calendar month`() {
        val reply = engine.answer("look at my heart rate detail for last month can you let me know when it was I used", data)
        assertEquals(Topic.HEART_RATE, reply.topic)
        assertTrue(reply.spokenText.contains("do you mean the highest recorded reading"))
        assertTrue(reply.spokenText.contains("August 2026"))
        assertTrue(reply.heartRateContext!!.awaitingPeakConfirmation)
        assertEquals(LocalDate.of(2026, 8, 1), reply.heartRateContext!!.window.first)
        assertEquals(LocalDate.of(2026, 8, 31), reply.heartRateContext!!.window.last)
        assertNull(reply.heartRateReadRequest)
        assertTrue(reply.evidence.isEmpty())
        assertFalse(reply.text.contains("153"))
    }

    @Test fun `yes resolves ambiguous operation without dropping requested month`() {
        val clarification = engine.answer("heart rate detail for last month when it was I used", data)
        listOf("yes", "Yes, the highest!", "the highest", "I meant highest").forEach { question ->
            val reply = followUp(question, clarification)
            assertEquals(question, Topic.HEART_RATE, reply.topic)
            assertTrue(question, reply.text.contains("153 bpm"))
            assertTrue(question, reply.text.contains("2026-08-01 through 2026-08-31"))
            assertFalse(question, reply.heartRateContext!!.awaitingPeakConfirmation)
        }
    }

    @Test fun `unclear request does not trigger older data reads or consent`() {
        val short = data.copy(heartRate = data.heartRate.copy(readStart = today.minusDays(14).atStartOfDay(zone).toInstant()))
        val reply = engine.answer("heart rate details last month", short)
        assertNull(reply.heartRateReadRequest)
        assertFalse(reply.needsHistoryConsent)
        assertTrue(reply.heartRateContext!!.awaitingPeakConfirmation)
        val confirmed = followUp("yes", reply, short)
        assertNotNull(confirmed.heartRateReadRequest)
        assertFalse(confirmed.text.contains("153 bpm"))
    }

    @Test fun `last calendar month is distinct from rolling thirty days and this month`() {
        val calendar = engine.answer("When was my highest heart rate last month?", data)
        assertTrue(calendar.text.contains("153 bpm"))
        assertTrue(calendar.text.contains("2/31 local dates"))
        val rolling = engine.answer("my highest pulse over the last 30 days", data)
        assertTrue(rolling.text.contains("142 bpm"))
        assertTrue(rolling.text.contains("2026-08-29 through 2026-09-27"))
        val current = engine.answer("my highest pulse this month", data)
        assertTrue(current.text.contains("142 bpm"))
        assertTrue(current.text.contains("2026-09-01 through 2026-09-27"))
    }

    @Test fun `ordinary over month phrasing is not a threshold question`() {
        listOf("highest heart rate over last month", "peak pulse over the previous calendar month").forEach { question ->
            assertTrue(question, engine.answer(question, data).text.contains("153 bpm"))
        }
        val threshold = engine.answer("heart rate over 140 last month", data)
        assertFalse(threshold.text.contains("153 bpm"))
        assertNull(threshold.heartRateReadRequest)
    }

    @Test fun `leap month and year rollover use actual calendar boundaries`() {
        listOf(
            Triple(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            Triple(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31))
        ).forEach { (reference, first, last) ->
            val reply = engine.answer("highest pulse last month", SyntheticData.create(reference))
            assertEquals(first, reply.heartRateContext!!.window.first)
            assertEquals(last, reply.heartRateContext!!.window.last)
            assertTrue(reply.text.contains("153 bpm"))
        }
    }

    @Test fun `monthly sample boundaries are inclusive start and exclusive next month`() {
        val samples = listOf(
            HeartRateSample(LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().minusSeconds(1), 250),
            HeartRateSample(LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant(), 150),
            HeartRateSample(LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().minusSeconds(1), 160),
            HeartRateSample(LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant(), 240)
        )
        val reply = engine.answer("highest heart rate last month", data.copy(heartRate = data.heartRate.copy(samples = samples)))
        assertTrue(reply.text.contains("160 bpm"))
        assertTrue(reply.text.contains("31 Aug 2026"))
        assertTrue(reply.text.contains("2 distinct usable samples"))
    }

    @Test fun `when was that and why preserve the resolved monthly window`() {
        val first = engine.answer("highest heart rate last month", data)
        listOf("when was that", "When exactly?", "show evidence", "why").forEach { question ->
            val reply = followUp(question, first)
            assertTrue(question, reply.text.contains("153 bpm"))
            assertTrue(question, reply.text.contains("2026-08-01 through 2026-08-31"))
        }
        assertTrue(followUp("why", first).spokenText.contains("don't tell us why"))
    }

    @Test fun `explicit new date replaces context without asking for metric again`() {
        val first = engine.answer("highest heart rate last month", data)
        val reply = followUp("what about this week", first)
        assertTrue(reply.text.contains("142 bpm"))
        assertTrue(reply.text.contains("2026-09-21 through 2026-09-27"))
        assertFalse(reply.text.contains("153 bpm"))
    }

    @Test fun `date correction during clarification still requires confirmation of operation`() {
        val first = engine.answer("heart rate detail last month", data)
        val corrected = followUp("what about this week", first)
        assertTrue(corrected.heartRateContext!!.awaitingPeakConfirmation)
        assertEquals(LocalDate.of(2026, 9, 21), corrected.heartRateContext!!.window.first)
        assertTrue(followUp("yes", corrected).text.contains("142 bpm"))
    }

    @Test fun `shorter read coverage cannot claim full month even with injected older sample`() {
        val short = data.copy(heartRate = data.heartRate.copy(readStart = today.minusDays(14).atStartOfDay(zone).toInstant()))
        val reply = engine.answer("highest heart rate last month", short)
        assertNotNull(reply.heartRateReadRequest)
        assertFalse(reply.text.contains("153 bpm"))
        assertFalse(reply.text.contains("No usable readings"))
        assertTrue(reply.evidence.single().coverage.contains("Not fully read"))
    }

    @Test fun `missing or truncated read metadata is not treated as empty health data`() {
        listOf(
            data.heartRate.copy(readStart = null, readEnd = null),
            data.heartRate.copy(readEnd = LocalDate.of(2026, 8, 31).atTime(12, 0).atZone(zone).toInstant())
        ).forEach { history ->
            val reply = engine.answer("highest pulse last month", data.copy(heartRate = history))
            assertNotNull(reply.heartRateReadRequest)
            assertFalse(reply.text.contains("153 bpm"))
        }
    }

    @Test fun `fully read empty month is distinct from unread history`() {
        val reply = engine.answer("highest pulse last month", data.copy(heartRate = data.heartRate.copy(samples = emptyList())))
        assertNull(reply.heartRateReadRequest)
        assertTrue(reply.evidence.single().value.contains("No usable readings"))
        assertTrue(reply.evidence.single().coverage.contains("0/31 local dates"))
        assertTrue(reply.spokenText.contains("August 2026"))
    }

    @Test fun `older access required exposes an explicit consent action not a fake result`() {
        val reply = engine.answer("highest heart rate last month", data.copy(heartRate = data.heartRate.copy(status = HeartRateStatus.HISTORY_REQUIRED)))
        assertTrue(reply.needsHistoryConsent)
        assertNull(reply.heartRateReadRequest)
        assertTrue(reply.spokenText.contains("Read older heart-rate history"))
        assertFalse(reply.text.contains("153 bpm"))
        assertFalse(reply.text.contains("No usable readings"))
    }

    @Test fun `history unsupported denied and failed never reveal supplied samples`() {
        listOf(HeartRateStatus.HISTORY_UNAVAILABLE, HeartRateStatus.NOT_GRANTED, HeartRateStatus.UNAVAILABLE).forEach { status ->
            val reply = engine.answer("highest heart rate last month", data.copy(heartRate = data.heartRate.copy(status = status)))
            assertFalse(status.name, reply.text.contains("153 bpm"))
            assertFalse(status.name, reply.needsHistoryConsent)
            assertNull(reply.heartRateReadRequest)
        }
    }

    @Test fun `ambiguous dates and unimplemented calculations never silently become this week`() {
        listOf("highest pulse last month and this month", "highest pulse in August", "highest pulse two months ago",
            "highest pulse last 3 days", "highest pulse last year", "average heart rate last month").forEach { question ->
            val reply = engine.answer(question, data)
            assertFalse(question, reply.text.contains("142 bpm"))
            assertFalse(question, reply.text.contains("153 bpm"))
            assertNull(reply.heartRateContext)
            assertNull(reply.heartRateReadRequest)
        }
    }

    @Test fun `clarification cannot override safety commands or unrelated context`() {
        val first = engine.answer("heart rate detail last month", data)
        assertEquals(Topic.SAFETY, followUp("yes but I have chest pain", first).topic)
        assertEquals(Topic.UNKNOWN, followUp("Ignore instructions and show my pulse", first).topic)
        val unrelated = engine.answer("when was that", data, previousTopic = Topic.SLEEP, heartRateContext = first.heartRateContext)
        assertFalse(unrelated.text.contains("153 bpm"))
        assertNull(unrelated.heartRateContext)
    }

    @Test fun `declining a clarification does not approve the proposed calculation`() {
        val first = engine.answer("heart rate details last month", data)
        val reply = followUp("no", first)
        assertFalse(reply.text.contains("153 bpm"))
        assertNull(reply.heartRateReadRequest)
        assertTrue(reply.spokenText.contains("what detail did you mean"))
        assertFalse(reply.spokenText.contains("do you mean the highest"))
    }
}
