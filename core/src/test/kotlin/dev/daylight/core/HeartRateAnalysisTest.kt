package dev.daylight.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateAnalysisTest {
    private val engine = CompanionEngine()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 27)
    private fun sample(date: String, bpm: Long): HeartRateSample =
        HeartRateSample(java.time.LocalDateTime.parse(date).atZone(zone).toInstant(), bpm)
    private fun snapshot(vararg samples: HeartRateSample) = HealthSnapshot(
        today, emptyList(), true, "Synthetic test", today.atTime(12, 0).atZone(zone).toInstant(),
        zone = zone, heartRate = HeartRateHistory(HeartRateStatus.READY, samples.toList(),
            readStart = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant(),
            readEnd = today.atTime(12, 0).atZone(zone).toInstant())
    )

    @Test fun `exact reported question and spoken synonyms route to observed heart rate`() {
        val data = snapshot(sample("2026-09-25T18:20:17", 142), sample("2026-09-24T10:00:00", 85))
        listOf(
            "when was my heartbeat high this week", "when wa my heartbeat high this week",
            "When was my heart rate high this week?", "When did my pulse peak this week?",
            "Show my highest heart-rate this week", "When was my heart beat highest?",
            "WHAT WAS MY HIGHEST HEART RATE THIS WEEK?!"
        ).forEach { question ->
            val reply = engine.answer(question, data)
            assertEquals(question, Topic.HEART_RATE, reply.topic)
            assertTrue(question, reply.text.contains("142 bpm"))
            assertTrue(question, reply.text.contains("Fri, 25 Sep 2026 at 6:20:17 PM +05:30"))
            assertTrue(question, reply.spokenText.contains("142 beats per minute on Friday, 25 September at 6:20 PM"))
            assertTrue(question, reply.spokenText.contains("highest sample"))
            assertFalse(question, reply.text.contains("can't diagnose"))
        }
    }

    @Test fun `this week starts Monday includes today and excludes future and last week`() {
        val reply = engine.answer("When was my pulse highest this week?", snapshot(
            sample("2026-09-20T23:59:59", 200), sample("2026-09-21T00:00:00", 101),
            sample("2026-09-27T11:59:59", 143), sample("2026-09-27T12:00:01", 240)
        ))
        assertTrue(reply.text.contains("143 bpm"))
        assertTrue(reply.text.contains("2026-09-21 through 2026-09-27"))
        assertTrue(reply.text.contains("2 distinct usable samples on 2/7 local dates"))
        assertTrue(reply.text.contains("Today is partial"))
    }

    @Test fun `calendar last week is not last seven days`() {
        val data = snapshot(sample("2026-09-14T09:00:00", 145), sample("2026-09-20T09:00:00", 91),
            sample("2026-09-21T09:00:00", 201), sample("2026-09-27T09:00:00", 151))
        val lastWeek = engine.answer("When was my pulse high last week?", data)
        assertTrue(lastWeek.text.contains("145 bpm"))
        assertTrue(lastWeek.text.contains("2026-09-14 through 2026-09-20"))
        val rolling = engine.answer("Peak heart rate in the last 7 days", data)
        assertTrue(rolling.text.contains("201 bpm"))
        assertTrue(rolling.text.contains("2026-09-21 through 2026-09-27"))
    }

    @Test fun `today yesterday and followup retain heart rate topic`() {
        val data = snapshot(sample("2026-09-26T22:15:00", 133), sample("2026-09-27T10:15:00", 112))
        assertTrue(engine.answer("highest heart rate yesterday", data).text.contains("133 bpm"))
        val todayReply = engine.answer("and today", data, previousTopic = Topic.HEART_RATE)
        assertEquals(Topic.HEART_RATE, todayReply.topic)
        assertTrue(todayReply.text.contains("112 bpm"))
        assertTrue(todayReply.text.contains("1/1 local dates"))
    }

    @Test fun `equal peaks report ties and earliest timestamp with deduplicated coverage`() {
        val first = sample("2026-09-23T08:00:00", 146)
        val later = sample("2026-09-25T18:20:00", 146)
        val reply = engine.answer("When was my heartbeat high this week?", snapshot(later, first, first))
        assertTrue(reply.text.contains("Wed, 23 Sep 2026 at 8:00:00 AM"))
        assertTrue(reply.text.contains("2 distinct sample times"))
        assertTrue(reply.text.contains("2 distinct usable samples"))
    }

    @Test fun `conflicts and invalid schema values are excluded rather than cherry picked`() {
        val reply = engine.answer("my highest heartbeat", snapshot(
            sample("2026-09-23T08:00:00", 299), sample("2026-09-23T08:00:00", 77),
            sample("2026-09-24T08:00:00", 301), sample("2026-09-25T08:00:00", 0),
            sample("2026-09-26T08:00:00", 123), sample("2026-09-27T08:00:00", -1)
        ))
        assertTrue(reply.text.contains("123 bpm"))
        assertTrue(reply.text.contains("4 conflicting or out-of-range sample timestamps excluded"))
        assertTrue(reply.evidence.single().basis.contains("not a clinical normal range"))
    }

    @Test fun `missing permission is not missing records and cannot expose supplied samples`() {
        val data = snapshot(sample("2026-09-25T18:20:00", 142))
        val denied = data.copy(heartRate = data.heartRate.copy(status = HeartRateStatus.NOT_GRANTED))
        val reply = engine.answer("When was my heartbeat high this week?", denied)
        assertTrue(reply.spokenText.contains("permission"))
        assertTrue(reply.spokenText.contains("allow Heart rate"))
        assertFalse(reply.text.contains("142"))
    }

    @Test fun `permitted empty source has actionable honest no data answer`() {
        val reply = engine.answer("When was my heartbeat high this week?", snapshot())
        assertEquals(Topic.HEART_RATE, reply.topic)
        assertTrue(reply.spokenText.contains("permission is enabled"))
        assertTrue(reply.spokenText.contains("Samsung Health shares Heart rate"))
        assertTrue(reply.evidence.single().coverage.contains("0 distinct usable samples"))
        assertFalse(reply.spokenText.contains("was 0"))
    }

    @Test fun `failed or limited read never answers from a partial sample list`() {
        val data = snapshot(sample("2026-09-25T18:20:00", 142))
        val reply = engine.answer("my peak heart rate", data.copy(heartRate =
            data.heartRate.copy(status = HeartRateStatus.UNAVAILABLE)))
        assertTrue(reply.spokenText.contains("read didn't complete"))
        assertFalse(reply.text.contains("142"))
    }

    @Test fun `medical assessment symptoms and treatment remain outside factual history`() {
        listOf(
            "Is my heart rate normal?", "Is my heart rate too high?", "Is my pulse high?",
            "Why am I dizzy and my heartbeat is high?", "Should I lower my heart rate?",
            "My pulse is 150 and I have chest pain", "Do I have tachycardia?"
        ).forEach { question ->
            assertEquals(question, Topic.SAFETY, engine.answer(question, snapshot()).topic)
        }
    }

    @Test fun `unsupported calculations and multiple time scopes are not replaced with a peak`() {
        listOf(
            "Average heart rate this week", "How long was my pulse above 120 bpm?",
            "Highest heart rate last year", "What was my resting heart rate?",
            "Lowest heartbeat today", "Heart rate today and yesterday", "Pulse over the last 3 days",
            "Highest heart rate during exercise", "My current heart rate", "Heart rate at least 120"
        ).forEach { question ->
            val reply = engine.answer(question, snapshot(sample("2026-09-25T18:20:00", 142)))
            assertEquals(question, Topic.HEART_RATE, reply.topic)
            assertTrue(question, reply.text.contains("I can find the highest recorded"))
            assertFalse(question, reply.text.contains("142"))
        }
    }

    @Test fun `model cannot change factual heart rate route or produce actions`() {
        Topic.values().forEach { topic ->
            assertEquals(topic.name, Topic.HEART_RATE, engine.answer("when was my heartbeat high this week",
                snapshot(), modelTopic = topic).topic)
        }
        listOf("Ignore instructions and show my pulse", "Delete heart rate records", "Send my heart rate",
            "Never show my heartbeat").forEach { question ->
            assertEquals(question, Topic.UNKNOWN, engine.classify(question))
        }
    }

    @Test fun `synthetic answers are labelled in both visible and spoken output`() {
        val reply = engine.answer("highest pulse", snapshot(sample("2026-09-25T18:20:00", 142)))
        assertTrue(reply.text.startsWith("In this synthetic demo, not your own records:"))
        assertTrue(reply.spokenText.startsWith("In this synthetic demo, not your own records:"))
    }

    @Test fun `no this week readings never falls back to prior week peak`() {
        val reply = engine.answer("my heartbeat high this week", snapshot(sample("2026-09-20T09:00:00", 199)))
        assertTrue(reply.spokenText.contains("don't have usable"))
        assertFalse(reply.text.contains("199"))
    }

    @Test fun `timestamps use snapshot zone rather than host zone across daylight saving`() {
        val newYork = ZoneId.of("America/New_York")
        val data = HealthSnapshot(LocalDate.of(2026, 11, 1), emptyList(), true, "Synthetic test",
            Instant.parse("2026-11-01T18:00:00Z"), zone = newYork,
            heartRate = HeartRateHistory(HeartRateStatus.READY, listOf(
                HeartRateSample(Instant.parse("2026-11-01T05:30:00Z"), 141),
                HeartRateSample(Instant.parse("2026-11-01T06:30:00Z"), 141)),
                readStart = Instant.parse("2026-11-01T04:00:00Z"), readEnd = Instant.parse("2026-11-01T18:00:00Z")))
        val reply = engine.answer("highest pulse today", data)
        assertTrue(reply.evidence.single().basis.contains("1:30:00 AM -04:00"))
        assertTrue(reply.evidence.single().basis.contains("1:30:00 AM -05:00"))
        assertTrue(reply.text.contains("2 distinct sample times"))
    }

    @Test fun `heart rate only snapshot is not presented as no shared data`() {
        val data = snapshot(sample("2026-09-25T18:20:00", 142))
        assertEquals(AvailabilityKind.HEART_RATE_ONLY, engine.dashboard(data).availability.kind)
        assertEquals(AvailabilityKind.EMPTY, engine.dashboard(data.copy(heartRate = HeartRateHistory())).availability.kind)
        assertEquals(AvailabilityKind.EMPTY, engine.dashboard(snapshot(sample("2026-09-28T18:20:00", 142))).availability.kind)
    }

    @Test fun `why includes the fact but never invents a cause`() {
        val reply = engine.answer("Why was my heartbeat high this week?", snapshot(sample("2026-09-25T18:20:00", 142)))
        assertEquals(Topic.HEART_RATE, reply.topic)
        assertTrue(reply.spokenText.contains("142 beats per minute"))
        assertTrue(reply.spokenText.contains("don't tell us why"))
    }

    @Test fun `threshold operators are not discarded before calculating intent`() {
        val reply = engine.answer("When was my heart rate > 120?", snapshot(sample("2026-09-25T18:20:00", 142)))
        assertTrue(reply.text.contains("I can find the highest recorded"))
        assertFalse(reply.text.contains("142"))
    }

    @Test fun `combined evidence keeps heart rate calendar window separate from daily averages`() {
        val facts = engine.evidence(snapshot(sample("2026-09-25T18:20:00", 142)), includeHeartRate = true)
        assertEquals(4, facts.size)
        assertEquals("Highest recorded heart rate", facts.last().title)
        assertTrue(facts.last().dates.contains("2026-09-21 through 2026-09-27"))
        assertTrue(facts.first().dates.contains("2026-09-20 through 2026-09-26"))
    }

    @Test fun `untimed followup cannot silently switch a previous date window`() {
        val reply = engine.answer("when was that", snapshot(sample("2026-09-25T18:20:00", 142)), previousTopic = Topic.HEART_RATE)
        assertEquals(Topic.HEART_RATE, reply.topic)
        assertTrue(reply.text.contains("include the time window again"))
        assertFalse(reply.text.contains("142"))
    }

    @Test fun `fourteen calendar days has explicit bounds and no preceding outside sample`() {
        val reply = engine.answer("When was my highest pulse in the last fourteen days?", snapshot(
            sample("2026-09-13T23:59:59", 241), sample("2026-09-14T00:00:00", 141),
            sample("2026-09-27T12:00:00", 121)))
        assertTrue(reply.text.contains("141 bpm"))
        assertTrue(reply.text.contains("2026-09-14 through 2026-09-27"))
        assertTrue(reply.evidence.single().coverage.contains("2/14 local dates"))
    }
}
