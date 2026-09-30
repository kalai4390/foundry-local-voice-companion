package dev.daylight.core

import java.time.format.DateTimeFormatter
import java.util.Locale

object HeartRateAnalysis {
    private val medicalAssessment = Regex(
        "\\b(?:too high|too low|should|concerning|worried|worry|lower|raise|reduce|increase|" +
            "what does .{0,40}mean)\\b|^(?:is|was|are|were)\\b.{0,70}\\b(?:high|low|fast|slow|elevated)\\b"
    )
    private val detailTime = DateTimeFormatter.ofPattern("EEE, d MMM uuuu 'at' h:mm:ss a xxx", Locale.ENGLISH)
    private val spokenTime = DateTimeFormatter.ofPattern("EEEE, d MMMM 'at' h:mm a", Locale.ENGLISH)

    fun matches(question: String, previousTopic: Topic? = null, context: HeartRateContext? = null): Boolean =
        HeartRateQueries.mentionsHeartRate(question) || previousTopic == Topic.HEART_RATE && HeartRateQueries.matchesFollowUp(question, context)

    fun isPendingResponse(question: String, context: HeartRateContext?): Boolean = HeartRateQueries.isPendingResponse(question, context)

    fun asksForAssessment(question: String, previousTopic: Topic? = null): Boolean =
        matches(question, previousTopic) && medicalAssessment.containsMatchIn(question)

    fun hasUsableSamples(snapshot: HealthSnapshot): Boolean {
        if (snapshot.heartRate.status != HeartRateStatus.READY) return false
        val first = snapshot.today.minusDays(14).atStartOfDay(snapshot.zone).toInstant()
        val end = snapshot.today.plusDays(1).atStartOfDay(snapshot.zone).toInstant()
        return snapshot.heartRate.samples.filter { sample ->
            sample.time >= first && sample.time < end && sample.time <= snapshot.fetchedAt
        }.groupBy { sample -> sample.time }.values.any { entries ->
            entries.distinct().singleOrNull()?.beatsPerMinute in 1L..300L
        }
    }

    fun answer(question: String, snapshot: HealthSnapshot, context: HeartRateContext? = null): Reply {
        val prefix = if (snapshot.synthetic) "In this synthetic demo, not your own records: " else ""
        val resolution = HeartRateQueries.resolve(question, snapshot.today, context)
        val window = when (resolution) {
            is HeartRateResolution.Unsupported -> return Reply(Topic.HEART_RATE, "$prefix${resolution.message}")
            is HeartRateResolution.Clarify -> {
                val clarification = if (resolution.declined) {
                    "Okay, what detail did you mean for heart rate ${resolution.window.label}? " +
                        "Right now I can find the highest recorded reading and its time. I haven't calculated anything."
                } else {
                    "For heart rate ${resolution.window.label}, do you mean the highest recorded reading and when it happened? " +
                        "Say yes, the highest, or edit your question. I haven't calculated a result yet."
                }
                return Reply(Topic.HEART_RATE, "$prefix$clarification ${resolution.window.describe(snapshot)}",
                    spokenText = "$prefix$clarification", heartRateContext = HeartRateContext(resolution.window, true))
            }
            is HeartRateResolution.Peak -> resolution.window
        }
        val resolvedContext = HeartRateContext(window)
        val scope = window.describe(snapshot)
        val source = snapshot.source.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").take(120)
        if (snapshot.heartRate.status != HeartRateStatus.READY) {
            val missing = when (snapshot.heartRate.status) {
                HeartRateStatus.NOT_GRANTED -> "I don't have heart-rate permission yet. Choose Connect Samsung Health, " +
                    "then allow Heart rate. Steps and sleep access don't include those readings."
                HeartRateStatus.HISTORY_REQUIRED -> "I need optional older-history access to check the whole period ${window.label}. " +
                    "Tap Read older heart-rate history if you'd like to allow it. I haven't substituted recent readings."
                HeartRateStatus.HISTORY_UNAVAILABLE -> "I can't read that whole period with the history access available on this phone. " +
                    "You can still ask about a recent week. I haven't substituted recent readings for the requested month."
                else -> "The heart-rate read didn't complete, so I can't tell you the highest reading ${window.label}. " +
                    "Refresh to retry. I haven't used a partial result."
            }
            return Reply(Topic.HEART_RATE, "$prefix$missing $scope", listOf(
                EvidenceFact("Heart-rate access", "No heart-rate answer available", missing,
                    "Not read; not zero", source, scope)
            ), spokenText = "$prefix$missing", heartRateContext = resolvedContext,
                needsHistoryConsent = snapshot.heartRate.status == HeartRateStatus.HISTORY_REQUIRED)
        }
        val start = window.first.atStartOfDay(snapshot.zone).toInstant()
        val end = window.last.plusDays(1).atStartOfDay(snapshot.zone).toInstant()
        val requiredEnd = minOf(end, snapshot.fetchedAt)
        val readStart = snapshot.heartRate.readStart
        val readEnd = snapshot.heartRate.readEnd
        if (readStart == null || readEnd == null || readStart > start || readEnd < requiredEnd) {
            val missing = "I need to read the requested heart-rate period ${window.label} first. " +
                "The current snapshot doesn't cover all those dates; that doesn't mean there are no readings."
            return Reply(Topic.HEART_RATE, "$prefix$missing $scope", listOf(
                EvidenceFact("Heart-rate read window", "Requested period not loaded", "No peak calculated from a shorter window.",
                    "Not fully read; not zero", source, scope)
            ), spokenText = "$prefix$missing", heartRateContext = resolvedContext, heartRateReadRequest = window)
        }
        val supplied = snapshot.heartRate.samples.filter { sample ->
            sample.time >= start && sample.time < end && sample.time <= snapshot.fetchedAt
        }
        val grouped = supplied.groupBy { sample -> sample.time }
        val usable = grouped.values.mapNotNull { entries ->
            entries.distinct().singleOrNull()?.takeIf { sample -> sample.beatsPerMinute in 1L..300L }
        }.sortedBy { sample -> sample.time }
        val excluded = grouped.values.count { entries ->
            entries.distinct().singleOrNull()?.beatsPerMinute !in 1L..300L
        }
        val observedDays = usable.map { sample -> sample.time.atZone(snapshot.zone).toLocalDate() }.distinct().size
        val expectedDays = java.time.temporal.ChronoUnit.DAYS.between(window.first, window.last) + 1
        val coverage = "${usable.size} distinct usable samples on $observedDays/$expectedDays local dates. " +
            "An observed date does not prove continuous capture. " +
            "$excluded conflicting or out-of-range sample timestamps excluded."
        if (usable.isEmpty()) {
            val missing = "I don't have usable heart-rate readings ${window.label} in the Samsung Health data shared here. " +
                "Heart-rate permission is enabled. Check that Samsung Health shares Heart rate, then refresh here. " +
                "Missing readings don't mean your heart rate was low or normal."
            return Reply(Topic.HEART_RATE, "$prefix$missing $scope", listOf(
                EvidenceFact("Heart-rate samples", "No usable readings", "No values were inferred or substituted.", coverage, source, scope)
            ), spokenText = "$prefix$missing", heartRateContext = resolvedContext)
        }
        val maximum = usable.maxOf { sample -> sample.beatsPerMinute }
        val peaks = usable.filter { sample -> sample.beatsPerMinute == maximum }
        val firstPeak = peaks.first().time.atZone(snapshot.zone)
        val otherPeaks = if (peaks.size > 1) " That value appears at ${peaks.size} distinct sample times; this is the earliest." else ""
        val interpretation = "Here, high means highest recorded, not medically too high. " +
            "Samples are not continuous monitoring and cannot establish a cause."
        val fact = EvidenceFact(
            "Highest recorded heart rate",
            "$maximum bpm on ${firstPeak.format(detailTime)} (${snapshot.zone.id})",
            "Maximum of timestamped samples, not a record interval's start or end. " +
                "Exact duplicate samples count once; conflicting values at one timestamp are excluded. " +
                "The 1–300 bpm validation range matches the record schema, not a clinical normal range. " +
                "Tied maximum sample times (up to 5): " + peaks.take(5).joinToString("; ") { sample ->
                    sample.time.atZone(snapshot.zone).format(detailTime)
                } + ". Total ties: ${peaks.size}. $interpretation",
            coverage, source, scope
        )
        val spoken = "$prefix${if (snapshot.synthetic) "the" else "Your"} highest recorded heart rate ${window.label} " +
            "was $maximum beats per minute on ${firstPeak.format(spokenTime)}, in ${snapshot.zone.id}." +
            "$otherPeaks I have readings on $observedDays of $expectedDays days in that window. " +
            "That's the highest sample I can see, not a judgement that it was medically too high." +
            if (Regex("\\b(?:why|cause|caused|reason)\\b").containsMatchIn(question)) " These records don't tell us why it happened." else ""
        return Reply(
            Topic.HEART_RATE,
            "$prefix${fact.value}.$otherPeaks $scope\n$coverage\n$interpretation\n" +
                "Source: $source. Refreshed at ${snapshot.fetchedAt.atZone(snapshot.zone).format(detailTime)}.",
            listOf(fact), spokenText = spoken, heartRateContext = resolvedContext
        )
    }

    private fun HeartRateWindow.describe(snapshot: HealthSnapshot): String =
        "Window: $first through $last (${snapshot.zone.id}); calendar weeks start Monday. " +
            "Readings are limited to the snapshot at ${snapshot.fetchedAt.atZone(snapshot.zone).format(detailTime)}. " +
            if (last == snapshot.today) "Today is partial." else "Capture may be incomplete."
}
