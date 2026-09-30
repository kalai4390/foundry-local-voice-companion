package dev.daylight.core

import java.time.Duration
import java.time.Instant

data class HeartRateReadRange(val lookback: Instant, val start: Instant, val end: Instant, val requiresHistoryPermission: Boolean)

object HeartRateReadPolicy {
    fun range(window: HeartRateWindow, snapshot: HealthSnapshot, now: Instant): HeartRateReadRange {
        require(window.first <= window.last && window.last <= snapshot.today) { "Invalid heart-rate date window." }
        require(window.first >= snapshot.today.withDayOfMonth(1).minusMonths(1)) { "Heart-rate history is bounded to the previous calendar month and later." }
        val start = window.first.atStartOfDay(snapshot.zone).toInstant()
        val lookback = window.first.minusDays(1).atStartOfDay(snapshot.zone).toInstant()
        val end = minOf(window.last.plusDays(1).atStartOfDay(snapshot.zone).toInstant(), snapshot.fetchedAt, now)
        require(start < end) { "No elapsed time in the requested heart-rate window." }
        return HeartRateReadRange(lookback, start, end, lookback < now.minus(Duration.ofDays(30)))
    }
}
