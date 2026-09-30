package dev.daylight.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionEngineTest {
    private val engine = CompanionEngine()

    @Test
    fun `evidence lists dated input values rather than only a window average`() {
        val current = LocalDate.of(2026, 9, 27)
        val facts = engine.evidence(SyntheticData.create(current))
        assertTrue(facts[0].basis.contains("2026-09-23 = missing"))
        assertTrue(facts[0].basis.contains("2026-09-25 = 0"))
        assertTrue(facts[0].basis.contains("2026-09-26 = 6900"))
        assertFalse(facts[0].basis.contains("2026-09-27 ="))
        assertTrue(facts[1].basis.contains("Recent daily values used (min)"))
    }
    private val today = LocalDate.of(2026, 9, 27)

    private fun snapshot(vararg days: DayRecord): HealthSnapshot = HealthSnapshot(
        today = today,
        days = days.toList(),
        synthetic = true,
        source = "Synthetic demo",
        fetchedAt = Instant.parse("2026-09-27T12:00:00Z"),
        zone = ZoneId.of("UTC")
    )

    private fun comparisonSnapshot(): HealthSnapshot = snapshot(
        DayRecord(today.minusDays(15), 999999, 999999, 999999),
        DayRecord(today.minusDays(14), 1000, 360, 0),
        DayRecord(today.minusDays(8), 3000, 480, 40),
        DayRecord(today.minusDays(7), 0, 0, 0),
        DayRecord(today.minusDays(6), 6000, 480, 30),
        DayRecord(today.minusDays(1), null, 420, null),
        DayRecord(today, 888888, 888888, 888888),
        DayRecord(today.plusDays(1), 777777, 777777, 777777)
    )

    @Test
    fun `means use only observed values including explicit zero`() {
        val dashboard = engine.dashboard(comparisonSnapshot())
        assertEquals(listOf("Steps", "Sleep sessions", "Recorded exercise"), dashboard.cards.map { card -> card.label })
        assertEquals("3000.0 steps/observed day", dashboard.cards[0].value)
        assertEquals("300.0 min/observed day", dashboard.cards[1].value)
        assertEquals("15.0 min/observed day", dashboard.cards[2].value)
        assertTrue(dashboard.cards[0].detail.contains("preceding: 2/7"))
        assertTrue(dashboard.cards[1].detail.contains("Recent: 3/7"))
        assertTrue(dashboard.cards[2].detail.contains("Recent: 2/7"))
    }

    @Test
    fun `completed windows exclude current old and future records`() {
        val facts = engine.evidence(comparisonSnapshot())
        assertEquals(3, facts.size)
        assertTrue(facts[0].value.contains("Recent: 3000.0"))
        assertTrue(facts[0].value.contains("Preceding: 2000.0"))
        assertTrue(facts[0].value.contains("difference: +1000.0"))
        assertTrue(facts[1].value.contains("Preceding: 420.0"))
        assertTrue(facts[2].value.contains("difference: -5.0"))
        facts.forEach { fact ->
            assertTrue(fact.dates.contains("2026-09-20 through 2026-09-26"))
            assertTrue(fact.dates.contains("2026-09-13 through 2026-09-19"))
            assertTrue(fact.dates.contains("today (2026-09-27) is excluded"))
            assertFalse(fact.value.contains("999999"))
            assertFalse(fact.value.contains("888888"))
            assertFalse(fact.value.contains("777777"))
        }
    }

    @Test
    fun `series is seven completed dates oldest first with null gaps`() {
        val cards = engine.dashboard(comparisonSnapshot()).cards
        assertEquals(listOf(0f, 6000f, null, null, null, null, null), cards[0].series)
        assertEquals(listOf(0f, 480f, null, null, null, null, 420f), cards[1].series)
        assertEquals(listOf(0f, 30f, null, null, null, null, null), cards[2].series)
    }

    @Test
    fun `missing and explicitly zero remain different for every metric`() {
        val recordedZero = snapshot(DayRecord(today.minusDays(1), 0, null, 0))
        val dashboard = engine.dashboard(recordedZero)
        assertEquals("0.0 steps/observed day", dashboard.cards[0].value)
        assertEquals("no observed records", dashboard.cards[1].value)
        assertEquals("0.0 min/observed day", dashboard.cards[2].value)
        assertEquals(0f, dashboard.cards[0].series.last())
        assertNull(dashboard.cards[1].series.last())
        assertTrue(dashboard.cards[0].detail.contains("Recent: 1/7"))
        assertTrue(dashboard.cards[1].detail.contains("Recent: 0/7"))
        assertFalse(dashboard.headline.contains("No records"))
        assertTrue(dashboard.context.contains("Missing records are not inactivity"))
    }

    @Test
    fun `empty snapshots show no invented numbers or trends`() {
        val dashboard = engine.dashboard(snapshot())
        assertEquals("No shared totals in this snapshot.", dashboard.headline)
        dashboard.cards.forEach { card ->
            assertEquals("no observed records", card.value)
            assertEquals(List<Float?>(7) { null }, card.series)
        }
        engine.evidence(snapshot()).forEach { fact ->
            assertTrue(fact.coverage.contains("Recent: 0/7"))
            assertTrue(fact.coverage.contains("preceding: 0/7"))
            assertTrue(fact.value.contains("change unavailable"))
            assertFalse(fact.value.contains("0.0"))
        }
    }

    @Test
    fun `empty baseline does not become zero or a percent change`() {
        val facts = engine.evidence(snapshot(DayRecord(today.minusDays(1), 4000, 420, 30)))
        facts.forEach { fact ->
            assertTrue(fact.value.contains("No preceding-week baseline; change unavailable"))
            assertTrue(fact.coverage.contains("Recent: 1/7"))
            assertTrue(fact.coverage.contains("preceding: 0/7"))
            assertFalse(fact.value.contains("Infinity"))
            assertFalse(fact.value.contains("NaN"))
            assertFalse(fact.value.contains("%"))
        }
    }

    @Test
    fun `baseline without recent data does not imply a decline`() {
        val snapshot = snapshot(DayRecord(today.minusDays(8), 1000, 400, 20))
        val facts = engine.evidence(snapshot)
        assertEquals("Only earlier history is available.", engine.dashboard(snapshot).headline)
        facts.forEach { fact ->
            assertTrue(fact.value.contains("Recent: no observed records"))
            assertTrue(fact.value.contains("change unavailable"))
            assertFalse(fact.value.contains("mean difference"))
        }
    }

    @Test
    fun `zero baseline supports an absolute recorded difference without division`() {
        val snapshot = snapshot(
            DayRecord(today.minusDays(8), 0),
            DayRecord(today.minusDays(1), 100)
        )
        val fact = engine.evidence(snapshot).first()
        assertTrue(fact.value.contains("Preceding: 0.0"))
        assertTrue(fact.value.contains("difference: +100.0"))
        assertFalse(fact.value.contains("%"))
    }

    @Test
    fun `today is a partial daily view rather than a weekly answer`() {
        val reply = engine.answer("How many steps today?", comparisonSnapshot())
        assertEquals(Topic.ACTIVITY, reply.topic)
        assertEquals(1, reply.evidence.size)
        assertEquals("888888 steps recorded", reply.evidence.single().value)
        assertTrue(reply.text.contains("Today (2026-09-27, UTC) is partial"))
        assertTrue(reply.evidence.single().coverage.contains("1/1 dates observed; partial day"))
        assertTrue(reply.evidence.single().dates.startsWith("2026-09-27"))
        assertFalse(reply.text.contains("Recent: 3000.0"))
        assertNull(reply.proposal)
    }

    @Test
    fun `a missing current day is not an explicit zero`() {
        val reply = engine.answer("Show my sleep today", snapshot(DayRecord(today, 0, null, 0)))
        assertEquals("no observed records", reply.evidence.single().value)
        assertTrue(reply.evidence.single().coverage.contains("0/1 dates observed"))
        assertTrue(reply.text.contains("not physiological time asleep"))
    }

    @Test
    fun `today and week views stay separately labelled`() {
        val reply = engine.answer("Compare today's steps with last week", comparisonSnapshot())
        assertEquals(2, reply.evidence.size)
        assertEquals("888888 steps recorded", reply.evidence[0].value)
        assertTrue(reply.evidence[1].value.contains("Recent: 3000.0"))
        assertTrue(reply.text.contains("not directly comparable"))
        assertTrue(reply.text.contains("7 completed local days each, not calendar weeks"))
    }

    @Test
    fun `yesterday is a dated total rather than a claim about individual sessions`() {
        val reply = engine.answer("How much recorded exercise yesterday?", snapshot(DayRecord(today.minusDays(1), 0, 420, 25)))
        assertEquals(Topic.WORKOUTS, reply.topic)
        assertEquals("25 min recorded", reply.evidence.single().value)
        assertTrue(reply.evidence.single().dates.startsWith("2026-09-26"))
        assertTrue(reply.evidence.single().basis.contains("not an individual-session view"))
    }

    @Test
    fun `all evidence supplies provenance basis coverage dates and numbers`() {
        engine.evidence(comparisonSnapshot()).forEach { fact ->
            assertEquals("Synthetic demo", fact.source)
            assertTrue(fact.basis.contains("Arithmetic means use observed daily values only"))
            assertTrue(fact.basis.contains("including explicit zero"))
            assertTrue(fact.basis.contains("does not prove complete capture"))
            assertTrue(fact.coverage.contains("/7 days observed"))
            assertTrue(fact.dates.contains("UTC"))
            assertTrue(fact.value.any { character -> character.isDigit() })
        }
        val sleep = engine.evidence(comparisonSnapshot())[1]
        assertTrue(sleep.basis.contains("Sleep-session duration, not physiological time asleep"))
    }

    @Test
    fun `snapshot local today defines windows rather than fetch instant or system clock`() {
        val original = comparisonSnapshot()
        val elsewhere = original.copy(
            zone = ZoneId.of("Pacific/Kiritimati"),
            fetchedAt = Instant.parse("2026-09-29T00:00:00Z")
        )
        assertEquals(engine.dashboard(original).cards, engine.dashboard(elsewhere).cards)
        assertEquals(engine.evidence(original).map { fact -> fact.value }, engine.evidence(elsewhere).map { fact -> fact.value })
        assertTrue(engine.evidence(elsewhere).first().dates.contains("Pacific/Kiritimati"))
    }

    @Test
    fun `negative and conflicting duplicate totals are excluded without double counting`() {
        val snapshot = snapshot(
            DayRecord(today.minusDays(7), -1, -30, 0),
            DayRecord(today.minusDays(6), 50, 360),
            DayRecord(today.minusDays(6), 50, 360),
            DayRecord(today.minusDays(5), 200, 420),
            DayRecord(today.minusDays(5), 400, 420)
        )
        val cards = engine.dashboard(snapshot).cards
        assertEquals("50.0 steps/observed day", cards[0].value)
        assertEquals(listOf(null, 50f, null, null, null, null, null), cards[0].series)
        assertTrue(cards[0].detail.contains("Recent: 1/7"))
        assertEquals("390.0 min/observed day", cards[1].value)
        assertEquals("0.0 min/observed day", cards[2].value)
        assertTrue(engine.evidence(snapshot).first().basis.contains("conflicting duplicate daily totals are excluded"))
    }

    @Test
    fun `large recorded totals do not overflow a long sum`() {
        val snapshot = snapshot(
            DayRecord(today.minusDays(2), Long.MAX_VALUE),
            DayRecord(today.minusDays(1), Long.MAX_VALUE)
        )
        val value = engine.dashboard(snapshot).cards.first().value
        assertFalse(value.startsWith("-"))
        assertFalse(value.contains("Infinity"))
        assertFalse(value.contains("NaN"))
    }

    @Test
    fun `suggestion context uses observed completed days and leaves calculation details in evidence`() {
        val reply = engine.answer("Suggest ideas for my sleep", comparisonSnapshot())
        assertEquals(Topic.SUGGEST, reply.topic)
        assertTrue(reply.text.contains("Record context only: Sleep sessions: 300.0 min/observed day (3/7 days observed)"))
        assertTrue(reply.text.contains("Last 7 completed local days; today excluded"))
        assertTrue(reply.text.contains("Synthetic demo"))
        assertTrue(reply.text.contains("Details in Evidence"))
        assertFalse(reply.text.contains("888888"))
        assertFalse(reply.text.contains("Arithmetic means"))
        assertEquals(listOf(engine.evidence(comparisonSnapshot())[1]), reply.evidence)
        assertTrue(reply.evidence.single().basis.contains("including explicit zero"))
        assertTrue(reply.evidence.single().basis.contains("not physiological time asleep"))
        assertNull(reply.proposal)
    }

    @Test
    fun `suggestions remain optional with missing records and never use the partial day as a baseline`() {
        val missing = engine.answer("Suggest a simple evening routine", snapshot())
        val partial = engine.answer(
            "Suggest a simple evening routine",
            snapshot(DayRecord(today, 999999, 999999, 999999))
        )
        assertEquals(missing, partial)
        assertEquals(Topic.SUGGEST, missing.topic)
        assertTrue(missing.text.contains("No observations"))
        assertTrue(missing.text.contains("0/7 days each"))
        assertTrue(missing.text.contains("optional local check-in"))
        assertFalse(missing.text.contains("0.0"))
        missing.evidence.forEach { fact ->
            assertTrue(fact.coverage.contains("Recent: 0/7"))
            assertTrue(fact.coverage.contains("preceding: 0/7"))
        }
        val recordedZero = engine.answer("Suggest a simple evening routine", snapshot(DayRecord(today.minusDays(1), 0)))
        assertTrue(recordedZero.text.contains("0.0 steps/observed day (1/7 days observed)"))
        assertFalse(recordedZero.text.contains("No observations"))
        assertNull(missing.proposal)
        assertNull(recordedZero.proposal)
    }

    @Test
    fun `ordinary answers stay concise with coverage visible and numerical bases in evidence`() {
        val snapshot = SyntheticData.create(today)
        listOf("Show my steps", "How was my sleep?", "Summarize my wellbeing records", "Suggest a simple evening routine").forEach { question ->
            val reply = engine.answer(question, snapshot)
            assertTrue(question, reply.text.length < 1000)
            assertTrue(question, reply.text.contains("6/7"))
            assertTrue(question, reply.text.contains("today excluded"))
            assertTrue(question, reply.text.contains("Details in Evidence"))
            assertFalse(question, reply.text.contains("Arithmetic means"))
            assertFalse(question, reply.text.contains("conflicting duplicate"))
            assertFalse(question, reply.text.contains("qualified health professional"))
            assertTrue(question, reply.evidence.all { fact -> fact.basis.contains("Arithmetic means") })
        }
    }
}
