package dev.daylight.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDataTest {
    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun `fixture has fourteen completed local dates and one partial current date`() {
        val snapshot = SyntheticData.create(today)
        assertEquals(today, snapshot.today)
        assertEquals(List(15) { offset -> today.minusDays(14L - offset) }, snapshot.days.map { day -> day.date })
        assertEquals(14, snapshot.days.count { day -> day.date < today })
        assertEquals(1, snapshot.days.count { day -> day.date == today })
        assertTrue(snapshot.days.none { day -> day.date > today })
        assertEquals(today, snapshot.fetchedAt.atZone(snapshot.zone).toLocalDate())
        assertEquals(12, snapshot.fetchedAt.atZone(snapshot.zone).hour)
        assertTrue(requireNotNull(snapshot.latestRecordAt) <= snapshot.fetchedAt)
        assertEquals(1300L, snapshot.days.last().steps)
    }

    @Test
    fun `fixture is reproducible and visibly synthetic throughout`() {
        val snapshot = SyntheticData.create(today)
        assertEquals(snapshot, SyntheticData.create(today))
        assertTrue(snapshot.synthetic)
        assertEquals("Synthetic demo", snapshot.source)
        assertTrue(snapshot.notes.isNotEmpty())
        assertTrue(snapshot.notes.all { note -> note.startsWith("Synthetic demo:") })
        assertTrue(snapshot.notes.any { note -> note.contains("not any real user's data") })
        assertTrue(CompanionEngine().evidence(snapshot).all { fact -> fact.source == "Synthetic demo" })
        assertTrue(CompanionEngine().dashboard(snapshot).context.contains("Synthetic demo"))
    }

    @Test
    fun `fixture includes per metric gaps and explicit recorded zeros`() {
        val completed = SyntheticData.create(today).days.filter { day -> day.date < today }
        assertTrue(completed.any { day -> day.steps == null })
        assertTrue(completed.any { day -> day.sleepSessionMinutes == null })
        assertTrue(completed.any { day -> day.exerciseMinutes == null })
        assertTrue(completed.any { day -> day.steps == 0L })
        assertTrue(completed.any { day -> day.exerciseMinutes == 0L })
        assertTrue(completed.any { day -> day.steps == null && day.sleepSessionMinutes == null && day.exerciseMinutes == null })
    }

    @Test
    fun `recent and preceding patterns are distinct with incomplete coverage`() {
        val snapshot = SyntheticData.create(today)
        val preceding = snapshot.days.filter { day -> day.date >= today.minusDays(14) && day.date < today.minusDays(7) }
        val recent = snapshot.days.filter { day -> day.date >= today.minusDays(7) && day.date < today }
        assertTrue(recent.mapNotNull { day -> day.steps }.average() > preceding.mapNotNull { day -> day.steps }.average())
        assertTrue(recent.mapNotNull { day -> day.sleepSessionMinutes }.average() < preceding.mapNotNull { day -> day.sleepSessionMinutes }.average())
        assertTrue(recent.mapNotNull { day -> day.exerciseMinutes }.average() > preceding.mapNotNull { day -> day.exerciseMinutes }.average())
        CompanionEngine().evidence(snapshot).forEach { fact ->
            assertFalse(fact.coverage.contains("7/7"))
            assertTrue(fact.value.contains("recorded mean difference"))
        }
    }

    @Test
    fun `dates follow the supplied day across year and leap day boundaries`() {
        listOf(LocalDate.of(2027, 1, 3), LocalDate.of(2024, 3, 3)).forEach { anchor ->
            val snapshot = SyntheticData.create(anchor)
            assertEquals(anchor.minusDays(14), snapshot.days.first().date)
            assertEquals(anchor, snapshot.days.last().date)
            assertEquals(15, snapshot.days.map { day -> day.date }.distinct().size)
        }
    }
}
