package dev.daylight.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotAvailabilityTest {
    private val engine = CompanionEngine()
    private val today = LocalDate.of(2026, 9, 27)
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun snapshot(vararg records: DayRecord): HealthSnapshot = HealthSnapshot(
        today, records.toList(), false, "Samsung Health via Health Connect", now, zone = ZoneId.of("UTC")
    )

    @Test
    fun `null day placeholders do not prove records are shared`() {
        val empty = snapshot(*(0L..14L).map { offset -> DayRecord(today.minusDays(offset)) }.toTypedArray())
        val availability = engine.dashboard(empty).availability
        assertEquals(AvailabilityKind.EMPTY, availability.kind)
        assertTrue(availability.detail.contains("2026-09-13 through 2026-09-27"))
        assertTrue(availability.detail.contains("does not describe other sources or dates"))
    }

    @Test
    fun `today alone is distinguished from an empty source`() {
        val dashboard = engine.dashboard(snapshot(DayRecord(today, steps = 1800)))
        assertEquals(AvailabilityKind.TODAY_ONLY, dashboard.availability.kind)
        assertTrue(dashboard.availability.detail.contains("Ask about today"))
        assertTrue(dashboard.cards.all { card -> card.value == "no observed records" })
    }

    @Test
    fun `explicit zero today is an observation not missing`() {
        val availability = engine.dashboard(snapshot(DayRecord(today, exerciseMinutes = 0))).availability
        assertEquals(AvailabilityKind.TODAY_ONLY, availability.kind)
    }

    @Test
    fun `preceding only history is distinguished from missing data`() {
        val availability = engine.dashboard(snapshot(DayRecord(today.minusDays(14), sleepSessionMinutes = 420))).availability
        assertEquals(AvailabilityKind.PRECEDING_ONLY, availability.kind)
    }

    @Test
    fun `today and preceding records do not fill the recent week gap`() {
        val availability = engine.dashboard(snapshot(
            DayRecord(today, steps = 600), DayRecord(today.minusDays(8), sleepSessionMinutes = 480)
        )).availability
        assertEquals(AvailabilityKind.TODAY_AND_PRECEDING, availability.kind)
    }

    @Test
    fun `one recent zero is sufficient to report recent data without claiming completeness`() {
        val availability = engine.dashboard(snapshot(DayRecord(today.minusDays(7), steps = 0))).availability
        assertEquals(AvailabilityKind.RECENT, availability.kind)
        assertTrue(availability.detail.contains("does not prove complete capture"))
    }

    @Test
    fun `outside window and future values do not make an empty snapshot available`() {
        val availability = engine.dashboard(snapshot(
            DayRecord(today.minusDays(15), 900), DayRecord(today.plusDays(1), 1800)
        )).availability
        assertEquals(AvailabilityKind.EMPTY, availability.kind)
    }

    @Test
    fun `conflicting duplicate totals report unusable source rather than zero or empty store`() {
        val dashboard = engine.dashboard(snapshot(
            DayRecord(today.minusDays(1), 500), DayRecord(today.minusDays(1), 900)
        ))
        assertEquals(AvailabilityKind.SOURCE_WITHOUT_TOTALS, dashboard.availability.kind)
        assertEquals("no observed records", dashboard.cards.first().value)
    }

    @Test
    fun `negative source values are not counted as valid observations`() {
        val availability = engine.dashboard(snapshot(DayRecord(today, -10, -20, -30))).availability
        assertEquals(AvailabilityKind.SOURCE_WITHOUT_TOTALS, availability.kind)
    }

    @Test
    fun `identical duplicates still give one usable observation`() {
        val record = DayRecord(today.minusDays(1), 1200)
        val dashboard = engine.dashboard(snapshot(record, record))
        assertEquals(AvailabilityKind.RECENT, dashboard.availability.kind)
        assertTrue(dashboard.cards.first().detail.contains("Recent: 1/7"))
    }

    @Test
    fun `valid source timestamp with unavailable aggregates is not called an empty store`() {
        val availability = engine.dashboard(snapshot().copy(latestRecordAt = now.minusSeconds(60))).availability
        assertEquals(AvailabilityKind.SOURCE_WITHOUT_TOTALS, availability.kind)
        assertTrue(availability.detail.contains("does not mean Health Connect is empty"))
    }

    @Test
    fun `future and out of window timestamps cannot establish source availability`() {
        for (timestamp in listOf(now.plusSeconds(1), today.minusDays(15).atStartOfDay(ZoneId.of("UTC")).toInstant())) {
            val availability = engine.dashboard(snapshot().copy(latestRecordAt = timestamp)).availability
            assertEquals(AvailabilityKind.EMPTY, availability.kind)
        }
    }

    @Test
    fun `each observed category uses the same validity rules as evidence`() {
        val mixed = snapshot(
            DayRecord(today.minusDays(1), 500, 0, null),
            DayRecord(today.minusDays(1), 900, 0, null)
        )
        val dashboard = engine.dashboard(mixed)
        assertEquals(AvailabilityKind.RECENT, dashboard.availability.kind)
        assertEquals("no observed records", dashboard.cards[0].value)
        assertEquals("0.0 min/observed day", dashboard.cards[1].value)
        assertFalse(dashboard.context.contains("complete capture"))
    }
}
