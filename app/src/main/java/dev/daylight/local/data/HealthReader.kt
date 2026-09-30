package dev.daylight.local.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.daylight.core.DayRecord
import dev.daylight.core.HealthSnapshot
import dev.daylight.core.HeartRateHistory
import dev.daylight.core.HeartRateSample
import dev.daylight.core.HeartRateStatus
import dev.daylight.core.HeartRateWindow
import dev.daylight.core.HeartRateReadPolicy
import dev.daylight.core.IntervalMath
import dev.daylight.core.TimeSpan
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class HealthReader(private val context: Context) {
    companion object {
        val basePermissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
        val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
        val historyPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        val permissions = basePermissions + heartRatePermission
        const val SAMSUNG_PACKAGE = "com.sec.android.app.shealth"
        @Volatile var readAttempts: Int = 0
            private set
    }

    private val origins = setOf(DataOrigin(SAMSUNG_PACKAGE))

    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(includeHeartRate: Boolean = false, includeHistory: Boolean = false): Boolean = available() &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            .containsAll((if (includeHeartRate) permissions else basePermissions) + if (includeHistory) setOf(historyPermission) else emptySet())

    fun supportsHistoryAccess(): Boolean = available() && HealthConnectClient.getOrCreate(context).features
        .getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun readHeartRateWindow(snapshot: HealthSnapshot, window: HeartRateWindow): HeartRateHistory {
        check(!snapshot.synthetic) { "Personal reads are never used to fill synthetic data." }
        readAttempts++
        if (!available()) return HeartRateHistory(HeartRateStatus.UNAVAILABLE)
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (heartRatePermission !in granted) return HeartRateHistory(HeartRateStatus.NOT_GRANTED)
        val range = HeartRateReadPolicy.range(window, snapshot, Instant.now())
        if (range.requiresHistoryPermission) {
            if (!supportsHistoryAccess()) return HeartRateHistory(HeartRateStatus.HISTORY_UNAVAILABLE)
            if (historyPermission !in granted) return HeartRateHistory(HeartRateStatus.HISTORY_REQUIRED)
        }
        return try {
            val history = readHeartRate(client, range.lookback, range.start, range.end)
            check(hasPermissions(includeHeartRate = true, includeHistory = range.requiresHistoryPermission)) {
                "Permissions changed during the heart-rate read. Discarding partial results."
            }
            history.copy(requiresHistoryPermission = range.requiresHistoryPermission)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            HeartRateHistory(HeartRateStatus.UNAVAILABLE)
        }
    }

    suspend fun read(): HealthSnapshot {
        readAttempts++
        check(available()) { "Health Connect is unavailable. Use synthetic demo mode or update Health Connect." }
        check(hasPermissions()) { "Read permission is missing or revoked. No personal records were loaded." }
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val start = today.minusDays(14).atStartOfDay(zone).toInstant()
        val lookback = today.minusDays(15).atStartOfDay(zone).toInstant()
        val heartRateGranted = heartRatePermission in client.permissionController.getGrantedPermissions()
        val heartRate = if (heartRateGranted) {
            try {
                readHeartRate(client, lookback, start, now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                HeartRateHistory(HeartRateStatus.UNAVAILABLE)
            }
        } else HeartRateHistory()
        val sleep = readAll<SleepSessionRecord>(client, lookback, now)
            .filter { it.endTime <= now && it.endTime >= start && it.endTime > it.startTime }
        val exercise = readAll<ExerciseSessionRecord>(client, lookback, now)
            .filter { it.endTime <= now && it.endTime > start && it.endTime > it.startTime }
        val lastStep = client.readRecords(
            ReadRecordsRequest(
                StepsRecord::class,
                TimeRangeFilter.between(start, now),
                dataOriginFilter = origins,
                ascendingOrder = false,
                pageSize = 1
            )
        ).records.firstOrNull()
        val days = (14L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                    dataOriginFilter = origins
                )
            )
            val sleepSpans = sleep.filter { it.endTime.atZone(zone).toLocalDate() == date }
                .map { TimeSpan(it.startTime, it.endTime) }
            val exerciseSpans = exercise.filter { it.startTime < dayEnd && it.endTime > dayStart }
                .map { TimeSpan(maxOf(it.startTime, dayStart), minOf(it.endTime, dayEnd)) }
            DayRecord(
                date = date,
                steps = aggregate[StepsRecord.COUNT_TOTAL],
                sleepSessionMinutes = sleepSpans.takeIf { it.isNotEmpty() }?.let(IntervalMath::totalMinutes),
                exerciseMinutes = exerciseSpans.takeIf { it.isNotEmpty() }?.let(IntervalMath::totalMinutes)
            )
        }
        check(hasPermissions(heartRateGranted)) { "Permissions changed during reading. Discarding the snapshot." }
        val latest = (sleep.map { it.endTime } + exercise.map { it.endTime } + listOfNotNull(lastStep?.endTime) +
            heartRate.samples.map { it.time })
            .filter { it <= now }.maxOrNull()
        return HealthSnapshot(
            today, days, false, "Samsung Health via Health Connect", now, latest, zone,
            listOf(
                "Only Samsung Health-origin records are read; its totals may include phone and watch data.",
                "Sleep sessions are assigned to their local wake-up date; duration is not necessarily time asleep.",
                "Overlapping exercise intervals are merged; absence of a workout record is not proof of inactivity.",
                "The current partial day is excluded from step, sleep and exercise week comparisons.",
                "Heart-rate peaks use sample timestamps and explicit calendar-date windows, including today when requested.",
                "Heart-rate access: ${heartRate.status}. Data freshness follows the last available record."
            ),
            heartRate
        )
    }

    private suspend fun readHeartRate(
        client: HealthConnectClient,
        lookback: Instant,
        start: Instant,
        end: Instant
    ): HeartRateHistory {
        val samples = mutableListOf<HeartRateSample>()
        var token: String? = null
        var recordCount = 0
        var sampleCount = 0L
        var pageCount = 0
        do {
            check(++pageCount <= 100) { "Heart-rate page limit exceeded; discarding partial results." }
            val response = client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    TimeRangeFilter.between(lookback, end),
                    dataOriginFilter = origins,
                    pageSize = 500,
                    pageToken = token
                )
            )
            recordCount += response.records.size
            check(recordCount <= 50000) { "Heart-rate record limit exceeded; discarding partial results." }
            response.records.forEach { record ->
                sampleCount += record.samples.size
                check(sampleCount <= 250000) { "Heart-rate sample limit exceeded; discarding partial results." }
                record.samples.forEach { sample ->
                    if (sample.time >= start && sample.time <= end &&
                        sample.time >= record.startTime && sample.time <= record.endTime
                    ) samples.add(HeartRateSample(sample.time, sample.beatsPerMinute))
                }
            }
            token = response.pageToken
        } while (token != null)
        return HeartRateHistory(HeartRateStatus.READY, samples, readStart = start, readEnd = end)
    }

    private suspend inline fun <reified RecordType : Record> readAll(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<RecordType> {
        val records = mutableListOf<RecordType>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    RecordType::class,
                    TimeRangeFilter.between(start, end),
                    dataOriginFilter = origins,
                    pageSize = 500,
                    pageToken = token
                )
            )
            records.addAll(response.records)
            check(records.size <= 50000) { "Too many records for this bounded prototype. No partial statistics will be shown." }
            token = response.pageToken
        } while (token != null)
        return records
    }
}
