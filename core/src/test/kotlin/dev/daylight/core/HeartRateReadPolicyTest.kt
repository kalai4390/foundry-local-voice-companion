package dev.daylight.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateReadPolicyTest {
    private val today = LocalDate.of(2026, 9, 27)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val snapshot = SyntheticData.create(today).copy(zone = zone, fetchedAt = now)

    @Test fun `last month requires optional history to conservatively cover full period`() {
        val range = HeartRateReadPolicy.range(HeartRateWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "last month"), snapshot, now)
        assertTrue(range.requiresHistoryPermission)
        assertEquals(Instant.parse("2026-07-31T18:30:00Z"), range.start)
        assertEquals(Instant.parse("2026-08-31T18:30:00Z"), range.end)
        assertEquals(Instant.parse("2026-07-30T18:30:00Z"), range.lookback)
    }

    @Test fun `recent week needs no additional history permission`() {
        val range = HeartRateReadPolicy.range(HeartRateWindow(today.minusDays(6), today, "week"), snapshot, now)
        assertFalse(range.requiresHistoryPermission)
        assertEquals(now, range.end)
    }

    @Test fun `read cutoff never passes original snapshot or current instant`() {
        val earlier = now.minusSeconds(3600)
        val range = HeartRateReadPolicy.range(HeartRateWindow(today.minusDays(6), today, "week"), snapshot, earlier)
        assertEquals(earlier, range.end)
        val later = HeartRateReadPolicy.range(HeartRateWindow(today.minusDays(6), today, "week"), snapshot, now.plusSeconds(3600))
        assertEquals(now, later.end)
    }

    @Test(expected = IllegalArgumentException::class) fun `arbitrarily old history is rejected`() {
        HeartRateReadPolicy.range(HeartRateWindow(today.minusYears(1), today, "year"), snapshot, now)
    }

    @Test(expected = IllegalArgumentException::class) fun `future windows are rejected`() {
        HeartRateReadPolicy.range(HeartRateWindow(today, today.plusDays(1), "future"), snapshot, now)
    }

    @Test(expected = IllegalArgumentException::class) fun `reversed windows are rejected`() {
        HeartRateReadPolicy.range(HeartRateWindow(today, today.minusDays(1), "reverse"), snapshot, now)
    }

    @Test fun `boundary overlap participates in the conservative older history gate`() {
        val first = today.minusDays(29)
        val range = HeartRateReadPolicy.range(HeartRateWindow(first, today, "30 dates"), snapshot, now)
        assertTrue(range.requiresHistoryPermission)
        assertEquals(first.minusDays(1).atStartOfDay(zone).toInstant(), range.lookback)
    }
}
