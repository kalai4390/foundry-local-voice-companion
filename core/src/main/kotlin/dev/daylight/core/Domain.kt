package dev.daylight.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Topic { BRIEF, SLEEP, ACTIVITY, WORKOUTS, HEART_RATE, SUGGEST, CHECKINS, EVIDENCE, REMINDER, FORGET, SAFETY, UNKNOWN, CONVERSATION }

data class HeartRateSample(val time: Instant, val beatsPerMinute: Long)

enum class HeartRateStatus { NOT_GRANTED, READY, UNAVAILABLE, HISTORY_REQUIRED, HISTORY_UNAVAILABLE }

data class HeartRateWindow(val first: LocalDate, val last: LocalDate, val label: String)

data class HeartRateContext(val window: HeartRateWindow, val awaitingPeakConfirmation: Boolean = false)

data class HeartRateHistory(
    val status: HeartRateStatus = HeartRateStatus.NOT_GRANTED,
    val samples: List<HeartRateSample> = emptyList(),
    val readStart: Instant? = null,
    val readEnd: Instant? = null,
    val requiresHistoryPermission: Boolean = false
)

data class DayRecord(
    val date: LocalDate,
    val steps: Long? = null,
    val sleepSessionMinutes: Long? = null,
    val exerciseMinutes: Long? = null
)

data class HealthSnapshot(
    val today: LocalDate,
    val days: List<DayRecord>,
    val synthetic: Boolean,
    val source: String,
    val fetchedAt: Instant,
    val latestRecordAt: Instant? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val notes: List<String> = emptyList(),
    val heartRate: HeartRateHistory = HeartRateHistory()
)

data class CheckIn(val id: String, val text: String, val createdAt: Instant)

data class EvidenceFact(
    val title: String,
    val value: String,
    val basis: String,
    val coverage: String,
    val source: String,
    val dates: String
)

data class StatCard(
    val label: String,
    val value: String,
    val detail: String,
    val series: List<Float?> = emptyList()
)

enum class AvailabilityKind { RECENT, TODAY_ONLY, PRECEDING_ONLY, TODAY_AND_PRECEDING, HEART_RATE_ONLY, SOURCE_WITHOUT_TOTALS, EMPTY }

data class SnapshotAvailability(val kind: AvailabilityKind, val title: String, val detail: String)

data class Dashboard(
    val headline: String,
    val context: String,
    val cards: List<StatCard>,
    val availability: SnapshotAvailability
)

data class Reply(
    val topic: Topic,
    val text: String,
    val evidence: List<EvidenceFact> = emptyList(),
    val proposal: String? = null,
    val spokenText: String = text,
    val heartRateContext: HeartRateContext? = null,
    val heartRateReadRequest: HeartRateWindow? = null,
    val needsHistoryConsent: Boolean = false
)

data class TimeSpan(val start: Instant, val end: Instant)
