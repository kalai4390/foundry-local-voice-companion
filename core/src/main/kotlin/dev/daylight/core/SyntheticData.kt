package dev.daylight.core

import java.time.LocalDate
import java.time.ZoneId

object SyntheticData {
    fun create(today: LocalDate = LocalDate.now()): HealthSnapshot {
        val zone = ZoneId.systemDefault()
        return HealthSnapshot(
            today = today,
            days = listOf(
                DayRecord(today.minusDays(14), 3600, 435, 0),
                DayRecord(today.minusDays(13), 4100, 450, 20),
                DayRecord(today.minusDays(12)),
                DayRecord(today.minusDays(11), 3900, 440, 0),
                DayRecord(today.minusDays(10), 4400, 460, 15),
                DayRecord(today.minusDays(9), 0, 425, null),
                DayRecord(today.minusDays(8), 4200, 445, 25),
                DayRecord(today.minusDays(7), 6200, 400, 25),
                DayRecord(today.minusDays(6), 6800, 410, 0),
                DayRecord(today.minusDays(5), 7100, 390, 35),
                DayRecord(today.minusDays(4), null, 420, null),
                DayRecord(today.minusDays(3), 7400, null, 30),
                DayRecord(today.minusDays(2), 0, 405, 0),
                DayRecord(today.minusDays(1), 6900, 395, 40),
                DayRecord(today, 1300, null, 0)
            ),
            synthetic = true,
            source = "Synthetic demo",
            fetchedAt = today.atTime(12, 0).atZone(zone).toInstant(),
            latestRecordAt = today.atTime(11, 30).atZone(zone).toInstant(),
            zone = zone,
            notes = listOf(
                "Synthetic demo: fabricated fixture, not any real user's data.",
                "Synthetic demo: intentional missing records differ from explicitly recorded zero.",
                "Synthetic demo: today's partial data is outside both completed seven-day windows.",
                "Synthetic demo: sleep values describe session duration, not physiological time asleep.",
                "Synthetic demo: heart-rate samples are fabricated for peak-and-timestamp tests, not medical thresholds."
            ),
            heartRate = HeartRateHistory(HeartRateStatus.READY, listOf(
                HeartRateSample(today.minusDays(3).atTime(18, 20, 17).atZone(zone).toInstant(), 142),
                HeartRateSample(today.minusDays(2).atTime(10, 0).atZone(zone).toInstant(), 87),
                HeartRateSample(today.minusDays(1).atTime(17, 10).atZone(zone).toInstant(), 131),
                HeartRateSample(today.atTime(11, 0).atZone(zone).toInstant(), 104),
                HeartRateSample(today.withDayOfMonth(1).minusMonths(1).withDayOfMonth(10).atTime(16, 40).atZone(zone).toInstant(), 153)
            ), readStart = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant(),
                readEnd = today.atTime(12, 0).atZone(zone).toInstant())
        )
    }
}
