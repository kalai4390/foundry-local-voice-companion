package dev.daylight.core

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalMathTest {
    private val origin = Instant.parse("2026-09-13T00:00:00Z")

    private fun span(startSeconds: Long, endSeconds: Long): TimeSpan = TimeSpan(
        origin.plusSeconds(startSeconds),
        origin.plusSeconds(endSeconds)
    )

    @Test
    fun `empty zero and reversed intervals contribute no duration`() {
        assertEquals(0L, IntervalMath.totalMinutes(emptyList()))
        assertEquals(0L, IntervalMath.totalMinutes(listOf(span(0, 0), span(120, 0))))
        assertEquals(1L, IntervalMath.totalMinutes(listOf(span(0, 60), span(200, 20))))
    }

    @Test
    fun `overlapping duplicate nested and adjacent intervals form one union`() {
        val spans = listOf(
            span(90, 180),
            span(0, 120),
            span(30, 60),
            span(0, 120),
            span(180, 240),
            span(300, 360)
        )
        assertEquals(5L, IntervalMath.totalMinutes(spans))
        assertEquals(5L, IntervalMath.totalMinutes(spans.reversed()))
    }

    @Test
    fun `nested intervals never shrink the merged end`() {
        assertEquals(6L, IntervalMath.totalMinutes(listOf(span(0, 300), span(60, 120), span(240, 360))))
    }

    @Test
    fun `positive disjoint durations are aggregated before flooring minutes`() {
        assertEquals(1L, IntervalMath.totalMinutes(listOf(span(0, 40), span(120, 160))))
        assertEquals(0L, IntervalMath.totalMinutes(listOf(span(0, 29), span(120, 150))))
        assertEquals(2L, IntervalMath.totalMinutes(listOf(span(0, 59), span(120, 179), span(240, 299))))
    }

    @Test
    fun `fractional seconds carry across disjoint intervals before flooring`() {
        val first = TimeSpan(origin, origin.plusSeconds(30).plusNanos(500_000_000))
        val second = TimeSpan(origin.plusSeconds(90), origin.plusSeconds(119).plusNanos(500_000_000))
        assertEquals(1L, IntervalMath.totalMinutes(listOf(first, second)))
        assertEquals(0L, IntervalMath.totalMinutes(listOf(first, second.copy(end = second.end.minusNanos(1)))))
    }

    @Test
    fun `large instant ranges do not use overflowing nanosecond arithmetic`() {
        assertEquals(
            Duration.between(Instant.MIN, Instant.MAX).toMinutes(),
            IntervalMath.totalMinutes(listOf(TimeSpan(Instant.MIN, Instant.MAX)))
        )
    }

    @Test
    fun `input order and interval list are not mutated`() {
        val spans = mutableListOf(span(300, 360), span(0, 60), span(30, 90))
        val original = spans.toList()
        assertEquals(2L, IntervalMath.totalMinutes(spans))
        assertEquals(original, spans)
    }
}
