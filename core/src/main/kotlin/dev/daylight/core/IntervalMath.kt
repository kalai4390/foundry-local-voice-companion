package dev.daylight.core

import java.time.Duration

object IntervalMath {
    fun totalMinutes(spans: List<TimeSpan>): Long {
        val ordered = spans.filter { span -> span.end.isAfter(span.start) }.sortedBy { span -> span.start }
        if (ordered.isEmpty()) return 0L

        var mergedStart = ordered.first().start
        var mergedEnd = ordered.first().end
        var duration = Duration.ZERO
        for (index in 1 until ordered.size) {
            val span = ordered[index]
            if (!span.start.isAfter(mergedEnd)) {
                if (span.end.isAfter(mergedEnd)) mergedEnd = span.end
            } else {
                duration = duration.plus(Duration.between(mergedStart, mergedEnd))
                mergedStart = span.start
                mergedEnd = span.end
            }
        }
        return duration.plus(Duration.between(mergedStart, mergedEnd)).toMinutes()
    }
}
