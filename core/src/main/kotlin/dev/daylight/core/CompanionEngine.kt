package dev.daylight.core

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class CompanionEngine {
    fun classify(question: String, previousTopic: Topic? = null, heartRateContext: HeartRateContext? = null): Topic {
        val query = prepareQuestion(question) ?: return Topic.UNKNOWN
        return route(query, previousTopic, heartRateContext).topic
    }

    fun answer(
        question: String,
        snapshot: HealthSnapshot,
        checkIns: List<CheckIn> = emptyList(),
        previousTopic: Topic? = null,
        modelTopic: Topic? = null,
        heartRateContext: HeartRateContext? = null
    ): Reply {
        val query = prepareQuestion(question) ?: return Reply(
            Topic.UNKNOWN,
            "Please ask a non-empty question of at most $MAX_QUESTION_LENGTH characters about " +
                "recorded steps, sleep sessions, exercise, heart-rate peaks, or local check-ins. Nothing was changed."
        )
        val activeHeartContext = heartRateContext.takeIf { previousTopic == Topic.HEART_RATE }
        val decision = route(query, previousTopic, activeHeartContext)
        val topic = advisoryTopic(decision, modelTopic)
        when (topic) {
            Topic.UNKNOWN -> return Reply(
                Topic.UNKNOWN,
                "Please clarify one request about recorded steps, sleep sessions, exercise, heart-rate peaks, or local check-ins. " +
                    "Time views are today, yesterday, or the two completed 7-day windows. Nothing was changed.",
                spokenText = "I'm not quite sure which part you mean. Try asking about your week, your sleep, or a wind-down idea. " +
                    "I can describe shared records, but I can't answer every kind of question."
            )
            Topic.CONVERSATION -> return Reply(topic, requireNotNull(SmallTalk.reply(query.commands)))
            Topic.SAFETY -> return safetyReply(query)
            Topic.HEART_RATE -> return HeartRateAnalysis.answer(query.text, snapshot, activeHeartContext)
            Topic.REMINDER -> return Reply(
                Topic.REMINDER,
                "Proposal only: one generic, one-shot routine notification at a time you choose in the app. " +
                    "Wording is fixed; Android may delay delivery. Nothing is scheduled by this reply. Confirm in the app first.",
                proposal = "One generic routine notification; choose a time and confirm",
                spokenText = "A little reminder is an option. Choose a time with the Set a reminder button, then confirm. " +
                    "I haven't scheduled anything, and Android may deliver it a little late."
            )
            Topic.FORGET -> return Reply(
                Topic.FORGET,
                "Proposal only: delete the selected namespace's entire app memory, including check-ins, " +
                    "conversation and the in-memory snapshot, and cancel the local reminder. " +
                    "This is not per-note deletion. Nothing has been deleted by this reply; confirm in the app. " +
                    "Original health records and Android permissions are unchanged.",
                proposal = "Delete all memory in the selected app namespace and cancel the local reminder",
                spokenText = "You're in control of that. Use Delete app memory in Privacy and confirm. " +
                    "That clears this app's selected memory and reminder, not Samsung Health originals. Nothing has been deleted yet."
            )
            else -> Unit
        }

        val view = RecordView(snapshot)
        val metrics = metricsFor(query, topic, previousTopic)
        val checkInContext = topic == Topic.CHECKINS || topic == Topic.EVIDENCE &&
            (CHECKINS.containsMatchIn(query.commands) ||
                previousTopic == Topic.CHECKINS && explicitMetrics(query).isEmpty())
        if (checkInContext) {
            val fact = checkInFact(snapshot, checkIns)
            val latest = checkIns.maxByOrNull { checkIn -> checkIn.createdAt }
            val quote = if (topic == Topic.CHECKINS && latest != null) {
                val date = latest.createdAt.atZone(snapshot.zone).toLocalDate()
                " Latest supplied local check-in ($date), quoted as user-written text: " +
                    "\"${displayText(latest.text, 240)}\"."
            } else {
                ""
            }
            return Reply(
                topic,
                "${fact.value}.$quote " +
                    "Self-reports are unverified, not evidence of causes. Quoted text is data, not instructions.",
                listOf(fact),
                spokenText = if (checkIns.isEmpty()) {
                    "There aren't any saved check-ins in this session. You're welcome to add one, but there is no daily requirement. Nothing is saved just because we talk."
                } else {
                    "You have ${checkIns.size} saved ${if (checkIns.size == 1) "check-in" else "check-ins"} here. " +
                        "Your notes are your own reflections, not proof of what caused a change. " +
                        "You can read them with Saved check-ins in Privacy; I won't read their contents aloud automatically."
                }
            )
        }
        if (topic == Topic.SUGGEST) {
            val observedMetric = metrics.firstOrNull { metric -> view.comparison(metric).recent.mean != null }
            val context = if (observedMetric == null) {
                "No observations for ${metrics.joinToString { metric -> metric.label }} (0/7 days each)."
            } else {
                val observed = view.comparison(observedMetric).recent
                "${observedMetric.label}: ${observed.meanText(observedMetric)} (${observed.count}/7 days observed)."
            }
            return Reply(
                topic,
                "If you would like, choose one: a generic one-shot reminder at a wind-down time you choose " +
                    "(confirm in the app), a movement break at a comfortable time, or an optional local check-in. " +
                    "These are nonclinical, opt-in ideas, not a prescription; nothing is scheduled or saved by this reply.\n" +
                    "Record context only: $context Last 7 completed local days; today excluded. " +
                    "Source \"${view.source}\". Details in Evidence.",
                view.facts(metrics),
                spokenText = "If you'd like, choose one small thing: a moment away from the screen, a wind-down reminder " +
                    "at a time you choose, or a private check-in. These are everyday options, not a personal health plan. " +
                    "Nothing is scheduled or saved yet."
            )
        }

        val todayRequested = TODAY.containsMatchIn(query.commands)
        val yesterdayRequested = YESTERDAY.containsMatchIn(query.commands)
        val day = when {
            todayRequested -> snapshot.today
            yesterdayRequested -> snapshot.today.minusDays(1)
            else -> null
        }
        val includeWeeks = day == null || WEEK.containsMatchIn(query.commands)
        val facts = buildList {
            if (day != null) addAll(view.dailyFacts(day, metrics))
            if (includeWeeks) addAll(view.facts(metrics))
            if (topic == Topic.EVIDENCE && explicitMetrics(query).isEmpty() && previousTopic !in
                setOf(Topic.SLEEP, Topic.ACTIVITY, Topic.WORKOUTS, Topic.CHECKINS)
            ) addAll(HeartRateAnalysis.answer("highest heart rate this week", snapshot).evidence)
        }
        val scope = buildList {
            add("Computed locally from source \"${view.source}\".")
            if (snapshot.synthetic && view.source != "Synthetic demo") add("Synthetic demo records.")
            if (day != null) {
                add(
                    if (todayRequested) {
                        "Today ($day, ${snapshot.zone.id}) is partial; excluded from week comparisons."
                    } else {
                        "Yesterday ($day, ${snapshot.zone.id}); capture may be incomplete."
                    }
                )
            }
            if (includeWeeks) add(view.compactWindowDescription)
            if (todayRequested && includeWeeks) {
                add("Today's partial total is not directly comparable to a completed-week mean.")
            }
        }.joinToString(" ")
        val summaries = buildList {
            if (day != null) {
                addAll(view.dailyFacts(day, metrics).map { fact -> "${fact.title}: ${fact.value}." })
            }
            if (includeWeeks) addAll(metrics.map { metric -> view.comparison(metric).summary(metric) })
        }.joinToString("\n")
        val why = if (CAUSE.containsMatchIn(query.text)) {
            " Records cannot establish causes."
        } else {
            ""
        }
        val sleepBasis = if (Metric.SLEEP in metrics) {
            " Sleep-session duration, not physiological time asleep."
        } else {
            ""
        }
        val standingBasis = if (ON_FEET.containsMatchIn(query.commands) && Metric.STEPS in metrics) {
            " Steps do not measure time on your feet."
        } else {
            ""
        }
        return Reply(
            topic,
            "$scope\n$summaries\nMissing records are not zero.$sleepBasis$standingBasis$why Details in Evidence.",
            facts,
            spokenText = view.spokenSummary(metrics, day, includeWeeks, topic == Topic.BRIEF, snapshot.synthetic) +
                if (why.isNotEmpty()) " These records can't explain why a change happened." else ""
        )
    }

    fun dashboard(snapshot: HealthSnapshot): Dashboard {
        val view = RecordView(snapshot)
        val availability = view.availability()
        return Dashboard(
            headline = availability.title,
            context = "${view.source} · ${snapshot.today.minusDays(7)} through ${snapshot.today.minusDays(1)}. " +
                "Last 7 completed local days; today excluded. Missing records are not inactivity. " +
                "Inspect Evidence for capture coverage and the preceding window.",
            cards = ALL_METRICS.map { metric ->
                val comparison = view.comparison(metric)
                StatCard(
                    label = metric.label,
                    value = comparison.recent.meanText(metric),
                    detail = "${comparison.coverage}. ${metric.basis} Compare both windows in Evidence.",
                    series = comparison.recent.values.map { value -> value?.toFloat() }
                )
            },
            availability = availability
        )
    }

    fun evidence(snapshot: HealthSnapshot, includeHeartRate: Boolean = false): List<EvidenceFact> =
        RecordView(snapshot).facts(ALL_METRICS) + if (includeHeartRate) {
            HeartRateAnalysis.answer("highest heart rate this week", snapshot).evidence
        } else emptyList()

    fun debriefCatalog(snapshot: HealthSnapshot): DebriefCatalog {
        val view = RecordView(snapshot)
        val cards = ALL_METRICS.filter { metric -> view.comparison(metric).recent.count > 0 }.map { metric ->
            DebriefCard(
                id = when (metric) {
                    Metric.STEPS -> DebriefFactId.STEPS
                    Metric.SLEEP -> DebriefFactId.SLEEP
                    Metric.EXERCISE -> DebriefFactId.EXERCISE
                },
                summary = view.comparison(metric).summary(metric),
                evidence = view.facts(listOf(metric)).single()
            )
        }
        return DebriefCatalog(
            cards,
            "Source: ${view.source}. ${view.compactWindowDescription}",
            view.availability()
        )
    }
}

private enum class Metric(val label: String, val topic: Topic, val unit: String, val basis: String) {
    STEPS("Steps", Topic.ACTIVITY, "steps", "Daily recorded step totals, not all activity."),
    SLEEP("Sleep sessions", Topic.SLEEP, "min", "Sleep-session duration, not physiological time asleep."),
    EXERCISE("Recorded exercise", Topic.WORKOUTS, "min", "Recorded exercise-session duration, not all movement.");

    fun read(record: DayRecord): Long? = when (this) {
        STEPS -> record.steps
        SLEEP -> record.sleepSessionMinutes
        EXERCISE -> record.exerciseMinutes
    }
}

private val ALL_METRICS = Metric.values().toList()

private data class Observations(val values: List<Long?>) {
    private val observed = values.filterNotNull()
    val count: Int = observed.size
    val mean: Double? = observed.takeIf { entries -> entries.isNotEmpty() }?.map { value -> value.toDouble() }?.average()

    fun meanText(metric: Metric): String = mean?.let { value -> "${number(value)} ${metric.unit}/observed day" }
        ?: "no observed records"
}

private data class Comparison(val recent: Observations, val preceding: Observations) {
    val coverage: String
        get() = "Recent: ${recent.count}/7 days observed; preceding: ${preceding.count}/7 days observed"

    fun summary(metric: Metric): String {
        val baseline = if (preceding.mean == null) {
            "no preceding-week baseline (0/7 days)"
        } else {
            "preceding ${preceding.meanText(metric)} (${preceding.count}/7 days)"
        }
        return "${metric.label}: ${recent.meanText(metric)} (${recent.count}/7 days); $baseline."
    }

    fun baselineText(metric: Metric): String {
        val recentMean = recent.mean
        val precedingMean = preceding.mean
        if (precedingMean == null) return "No preceding-week baseline; change unavailable"
        if (recentMean == null) return "Preceding: ${preceding.meanText(metric)}; change unavailable"
        val difference = recentMean - precedingMean
        val sign = if (difference > 0) "+" else ""
        return "Preceding: ${preceding.meanText(metric)}; recorded mean difference: " +
            "$sign${number(difference)} ${metric.unit}/observed day"
    }
}

private class RecordView(private val snapshot: HealthSnapshot) {
    val source: String = displayText(snapshot.source, 120).ifBlank { "Unspecified local source" }
    private val recentDates = List(7) { offset -> snapshot.today.minusDays(7L - offset) }
    private val precedingDates = List(7) { offset -> snapshot.today.minusDays(14L - offset) }
    private val records = snapshot.days.filter { record ->
        record.date >= precedingDates.first() && record.date <= snapshot.today
    }.groupBy { record -> record.date }
    private val comparisons = ALL_METRICS.associateWith { metric ->
        Comparison(
            Observations(recentDates.map { date -> value(date, metric) }),
            Observations(precedingDates.map { date -> value(date, metric) })
        )
    }
    val windowDescription: String
        get() = "Last 7 completed local days: ${recentDates.first()} through ${recentDates.last()}; " +
            "preceding 7: ${precedingDates.first()} through ${precedingDates.last()} (${snapshot.zone.id}). " +
            "These are rolling windows, not calendar weeks; today (${snapshot.today}) is excluded."
    val compactWindowDescription: String
        get() = "${recentDates.first()} through ${recentDates.last()} vs " +
            "${precedingDates.first()} through ${precedingDates.last()}: " +
            "7 completed local days each, not calendar weeks; today excluded."

    fun comparison(metric: Metric): Comparison = comparisons.getValue(metric)

    fun spokenSummary(metrics: List<Metric>, day: LocalDate?, includeWeeks: Boolean, brief: Boolean, synthetic: Boolean): String = buildString {
        if (synthetic) append("In this demo, not your own records: ")
        else append("From the records you've shared: ")
        if (day != null) {
            append(if (day == snapshot.today) "today so far, " else "yesterday, ")
            append(metrics.joinToString("; ") { metric ->
                val amount = value(day, metric)
                if (amount == null) "there's no usable ${metric.spokenLabel()} total"
                else "${metric.spokenLabel()} recorded ${spokenAmount(metric, amount.toDouble())}"
            })
            append(". These daily totals may be incomplete. ")
        }
        if (includeWeeks) {
            append("Looking at the last seven complete days, leaving today out. ")
            append(metrics.joinToString(" ") { metric ->
                val compared = comparison(metric)
                val recent = compared.recent
                val mean = recent.mean
                if (mean == null) {
                    "I don't have usable ${metric.spokenLabel()} records for that week."
                } else {
                    val comparisonText = if (brief) "" else compared.preceding.mean?.let { preceding ->
                        " The preceding week's average was ${spokenAmount(metric, preceding)}, with records on ${compared.preceding.count} of seven days."
                    } ?: " There isn't a preceding-week baseline to compare."
                    "${metric.spokenLabel().replaceFirstChar { letter -> letter.titlecase(Locale.ROOT) }} averaged " +
                        "${spokenAmount(metric, mean)} per recorded day, with records on ${recent.count} of seven days.$comparisonText"
                }
            })
            append(" Recorded days can be incomplete; missing records aren't zero. ")
        }
        if (Metric.SLEEP in metrics) append("Sleep sessions aren't a measure of time asleep. ")
        append("The full numbers and coverage are in Evidence.")
    }

    fun availability(): SnapshotAvailability {
        val recent = ALL_METRICS.any { metric -> comparison(metric).recent.count > 0 }
        val preceding = ALL_METRICS.any { metric -> comparison(metric).preceding.count > 0 }
        val current = ALL_METRICS.any { metric -> value(snapshot.today, metric) != null }
        val sourceTimestamp = snapshot.latestRecordAt?.let { latest ->
            latest >= precedingDates.first().atStartOfDay(snapshot.zone).toInstant() &&
                latest <= snapshot.fetchedAt && latest.atZone(snapshot.zone).toLocalDate() <= snapshot.today
        } == true
        val suppliedTotals = records.values.flatten().any { record ->
            ALL_METRICS.any { metric -> metric.read(record) != null }
        }
        val scope = "Selected source only; ${precedingDates.first()} through ${snapshot.today} (${snapshot.zone.id}). "
        return when {
            recent -> SnapshotAvailability(
                AvailabilityKind.RECENT, "Your week, in perspective.",
                scope + "At least one category has usable recent-week records. Check each category's coverage; a record does not prove complete capture."
            )
            current && preceding -> SnapshotAvailability(
                AvailabilityKind.TODAY_AND_PRECEDING, "Today and earlier history, with a gap.",
                scope + "Usable totals exist today and in the preceding week, but not in the last 7 completed days. Ask about today; weekly cards intentionally exclude it."
            )
            current -> SnapshotAvailability(
                AvailabilityKind.TODAY_ONLY, "Today's records are here. Weekly history is not.",
                scope + "Only today's partial totals are usable. Ask about today; the weekly cards deliberately exclude today. This is not a failed connection."
            )
            preceding -> SnapshotAvailability(
                AvailabilityKind.PRECEDING_ONLY, "Only earlier history is available.",
                scope + "Usable totals exist in the preceding week, but not in the recent week or today. Inspect Evidence for the older baseline and refresh after new records are shared."
            )
            HeartRateAnalysis.hasUsableSamples(snapshot) -> SnapshotAvailability(
                AvailabilityKind.HEART_RATE_ONLY, "Heart-rate readings are here.",
                scope + "Usable heart-rate samples exist, but steps, sleep and exercise totals are unavailable. " +
                    "Ask when your heartbeat was highest; heart-rate evidence uses its own explicit date window."
            )
            sourceTimestamp || suppliedTotals -> SnapshotAvailability(
                AvailabilityKind.SOURCE_WITHOUT_TOTALS, "Source records exist, but totals are unavailable.",
                scope + "A source timestamp or supplied record is present, but no unambiguous non-negative daily total is usable. This does not mean Health Connect is empty."
            )
            else -> SnapshotAvailability(
                AvailabilityKind.EMPTY, "No shared totals in this snapshot.",
                scope + "No usable steps, sleep-session or exercise totals were returned. Permission alone does not create shared records. Inspect Health Connect data; this result does not describe other sources or dates."
            )
        }
    }

    private fun value(date: LocalDate, metric: Metric): Long? = records[date].orEmpty()
        .mapNotNull { record -> metric.read(record)?.takeIf { amount -> amount >= 0 } }
        .distinct()
        .singleOrNull()

    fun facts(metrics: List<Metric>): List<EvidenceFact> = metrics.map { metric ->
        val comparison = comparison(metric)
        EvidenceFact(
            title = metric.label,
            value = "Recent: ${comparison.recent.meanText(metric)}; ${comparison.baselineText(metric)}",
            basis = "${metric.basis} Arithmetic means use observed daily values only, including explicit zero. " +
                "Negative values and conflicting duplicate daily totals are excluded, not added together. " +
                "An observed date does not prove complete capture.\n" +
                "Recent daily values used (${metric.unit}): " + dailyValues(recentDates, metric) + ".\n" +
                "Preceding daily values used (${metric.unit}): " + dailyValues(precedingDates, metric) + ".",
            coverage = "${comparison.coverage}. Missing is not zero or inactivity",
            source = source,
            dates = windowDescription
        )
    }

    private fun dailyValues(dates: List<LocalDate>, metric: Metric): String = dates.joinToString("; ") { date ->
        "$date = ${value(date, metric)?.toString() ?: "missing"}"
    }

    fun dailyFacts(date: LocalDate, metrics: List<Metric>): List<EvidenceFact> = metrics.map { metric ->
        val amount = value(date, metric)
        val partial = date == snapshot.today
        EvidenceFact(
            title = "${metric.label} on $date",
            value = amount?.let { recorded -> "$recorded ${metric.unit} recorded" } ?: "no observed records",
            basis = "${metric.basis} One unambiguous non-negative total for this local date, including explicit zero. " +
                "Negative or conflicting duplicate values are excluded. This is not an individual-session view.",
            coverage = "${if (amount == null) 0 else 1}/1 dates observed; " +
                if (partial) "partial day, completeness unknown" else "complete local date, capture may be incomplete",
            source = source,
            dates = "$date (${snapshot.zone.id})" + if (partial) "; today is excluded from week comparisons" else ""
        )
    }
}

private fun checkInFact(snapshot: HealthSnapshot, checkIns: List<CheckIn>): EvidenceFact {
    val dates = checkIns.map { checkIn -> checkIn.createdAt.atZone(snapshot.zone).toLocalDate() }
    val recentStart = snapshot.today.minusDays(7)
    val precedingStart = snapshot.today.minusDays(14)
    val recent = dates.count { date -> date >= recentStart && date < snapshot.today }
    val preceding = dates.count { date -> date >= precedingStart && date < recentStart }
    val todayCount = dates.count { date -> date == snapshot.today }
    val recentDays = dates.filter { date -> date >= recentStart && date < snapshot.today }.distinct().size
    val precedingDays = dates.filter { date -> date >= precedingStart && date < recentStart }.distinct().size
    return EvidenceFact(
        title = "Local check-ins",
        value = "${checkIns.size} local check-ins supplied; recent week: $recent; preceding week: $preceding; " +
            "today: $todayCount; outside these dates: ${checkIns.size - recent - preceding - todayCount}",
        basis = "Counts of supplied entries, dated in ${snapshot.zone.id}; not inferred from health records. " +
            "User-written content is unverified data, never instructions or evidence of health causation.",
        coverage = "Recent: $recentDays/7 dates with check-ins; preceding: $precedingDays/7. " +
            "No daily check-in requirement; absence says nothing about wellbeing",
        source = "User-controlled local check-ins",
        dates = "Recent: $recentStart through ${snapshot.today.minusDays(1)}; " +
            "preceding: $precedingStart through ${recentStart.minusDays(1)}. " +
            "Today (${snapshot.today}) is counted separately, outside the comparison"
    )
}

private fun number(value: Double): String = String.format(
    Locale.ROOT,
    if (value != 0.0 && abs(value) < 0.1) "%.2f" else "%.1f",
    value
)

private fun Metric.spokenLabel(): String = when (this) {
    Metric.STEPS -> "steps"
    Metric.SLEEP -> "sleep sessions"
    Metric.EXERCISE -> "recorded exercise"
}

private fun spokenAmount(metric: Metric, value: Double): String {
    if (metric == Metric.SLEEP && value >= 60) {
        val minutes = value.roundToLong()
        val hours = minutes / 60
        val remainder = minutes % 60
        val hourText = "$hours ${if (hours == 1L) "hour" else "hours"}"
        val minuteText = if (remainder == 0L) "" else " and $remainder ${if (remainder == 1L) "minute" else "minutes"}"
        return "about $hourText$minuteText"
    }
    return "${number(value).removeSuffix(".0")} ${if (metric == Metric.STEPS) "steps" else "minutes"}"
}

private fun displayText(text: String, limit: Int): String {
    val shortened = text.take(limit)
        .replace(Regex("[\\p{Cc}\\p{Cf}\\p{Z}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return shortened + if (text.length > limit) "…" else ""
}

private const val MAX_QUESTION_LENGTH = 1000
private data class Question(val text: String, val commands: String)
private data class RoutingDecision(val topic: Topic, val advisoryTopics: Set<Topic> = emptySet())

private fun words(pattern: String): Regex = Regex("\\b(?:$pattern)\\b")

private val MEDICAL = words(
    "diagnos(?:e|is|es|ing|tic)|medical|clinical|medicin(?:e|es)|medications?|prescrib(?:e|ed|ing)|" +
        "prescriptions?|pills?|dos(?:e|es|age)|treatments?|cures?|symptoms?|diseases?|disorders?|illness|sick|" +
        "diabet(?:es|ic)|cancer|asthma|infection|apn(?:ea|oea)|insomnia|depress(?:ed|ion)|anxiety|panic|" +
        "blood pressure|blood sugar|glucose|oxygen|heart attack|stroke|arrhythmia|tachycardia|bradycardia|palpitations|" +
        "pain|injur(?:y|ies|ed)|dizz(?:y|iness)|faint(?:ed|ing)?|pregnan(?:t|cy)|" +
        "healthy|unhealthy|abnormal|normal|safe|unsafe|risks?|risky|dangerous|doctor|physician|clinician|" +
        "overtraining|fatigue|tired|exhausted|" +
        "lose weight|weight loss|calorie target|readiness score|recovery score|" +
        "overdose|suicid(?:e|al)|self[ -]?harm|kill myself|hurt myself|want to die|" +
        "(?:don't|do not) want to live|(?:can't|cannot) breathe|shortness of breath|" +
        "(?:personal|daily|training|workout|exercise|step|sleep) (?:plan|target|goal)s?"
)
private val PERSONAL_TARGET = Regex(
    "\\bhow (?:many|much|long|hard|often)\\b.{0,80}\\b(?:should|must|need to)\\b" +
        ".{0,80}\\b(?:steps|walk|run|exercise|train|sleep|work out)\\b|" +
        "\\bhow (?:many|much) (?:steps|hours of sleep|exercise|training)\\b.{0,80}\\b(?:should|must|need)\\b|" +
        "\\b(?:can|should|must) i (?:walk|run|exercise|train|sleep|work out|take.{0,30}steps)\\b"
)
private val URGENT = words(
    "chest pain|heart attack|stroke|overdose|suicid(?:e|al)|self[ -]?harm|kill myself|hurt myself|" +
        "want to die|(?:don't|do not) want to live|(?:can't|cannot) breathe|shortness of breath"
)
private val NEGATIVE = words(
    "don't|dont|not|never|stop|cancel|avoid|without|refuse|shouldn't|mustn't|can't|cannot|won't|wouldn't|" +
        "no (?:more )?(?:reminders?|advice|suggestions?|actions?|deletion|sleep|steps|exercise|activity|check[ -]?ins?|notes?)|" +
        "not (?:a |about )?(?:reminder|advice|suggestion|delete|forget|remove|allowed|sleep|steps|activity|exercise|workouts?|check[ -]?ins?)|" +
        "except|exclude|excluding|omit|skip|ignore|disregard|rather than"
)
private val DIRECTIVE = words(
    "(?:ignore|override|bypass|disregard|replace).{0,80}(?:instructions?|rules?|safety|policy|policies)|" +
        "system prompt|system message|developer message|jailbreak|you are now|pretend to be|act as|" +
        "execute|run (?:a |this |the )?(?:command|script|program)|" +
        "(?:output|return|print|say) (?:only|exactly)|respond with|" +
        "(?:note|check[ -]?in|message|text) (?:says|said|reads|contains|asks|tells)"
)
private val OUTSIDE = words(
    "weather|forecast|stocks?|bitcoin|crypto|investments?|bank|buy|purchase|sell|send|emails?|" +
        "text message|book|flights?|travel|hotel|nightclub|work shifts?|night shifts?|astronomy|sunrise|sunset|" +
        "recipes?|diet|nutrition|meals?|calories|bmi|" +
        "programming|python|javascript|kotlin|sql|shell|terminal|code|scripts?|algorithms?|sort|software|computer|" +
        "bake|baking|bread|cake|cook|cooking|step by step|step-by-step|" +
        "download|upload|internet|web|browse|traffic|news|jokes?|poems?|politics|" +
        "translate|translation|passwords?|api|files?|capital|sports|astrology"
)
private val UNSUPPORTED_TIME = words(
    "tomorrow|tonight|last night|next (?:week|month|year)|last (?:month|year)|" +
        "months?|years?|calendar week|since|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
        "[0-9]{4}-[0-9]{2}-[0-9]{2}"
)
private val DAY_COUNT = words("([0-9]+) days?")
private val TODAY = words("today|current day|so far|right now")
private val YESTERDAY = words("yesterday")
private val WEEK = words("weeks?|7 days|seven days|14 days|fourteen days|fortnight")
private val CHECKINS = words("check[ -]?ins?|check in|notes?|journal|journaling")
private val RECORDS = words("wellbeing|wellness|health records|my records|local records|wearable|watch data|recorded data")
private val SLEEP = words("sleep|slept|sleeping|bedtime")
private val ACTIVITY = words("steps?|activity|active|movement|walking|walked|step count")
private val WORKOUTS = words("workouts?|work outs?|exercise|exercising|training|runs?|running|cycling|swimming")
private val NIGHT_RECORDS = words("my nights|my nighttimes|overnight rest|time in bed|bedtime records")
private val ON_FEET = words("on my feet|on your feet|standing time")
private val MOVEMENT_RECORDS = words("been moving|getting around|move around|moving about")
private val GYM_RECORDS = words("gym sessions|gym visits|at the gym|jogs|jogging|bike rides|cycle rides")
private val WELLBEING_ROUTINE = words("evening routine|bedtime routine|morning routine|daily routine|wind[ -]down|movement breaks?")
private val APP_MEMORY = words("app memory|daylight memory|local memory|demo memory|personal memory|all app data")
private val SELECTIVE_FORGET = words(
    "(?:only|just|latest|last|first|single|one|selected|this|that|these|those|specific|[0-9]+)" +
        "(?: [\\p{L}]+){0,3} (?:check[ -]?ins?|notes?|entries|entry)|" +
        "(?:check[ -]?ins?|notes?|entries|entry) (?:only|just|from|about|containing|called|named|that|before|after)|" +
        "check[ -]?in|note|entry"
)
private val CHOICE = words("or|but")
private val CONFLICTING_REQUESTS = words(
    "instead|alternatively|(?:and|then|also|but|or) (?:please )?" +
        "(?:show|tell|explain|compare|summari[sz]e|suggest|recommend|remind|forget|delete|clear|remove|schedule|set|add|save|create)"
)
private val EVIDENCE = words(
    "evidence|sources?|basis|coverage|sample counts?|how many days|how do you know|what is missing|which records are missing|missing records|" +
        "where did (?:that|this|those|these).{0,30}come from|what.{0,30}based on|show.{0,20}numbers|" +
        "why did you say (?:that|this)|why do you say (?:that|this)"
)
private val SUGGEST = words("suggest(?:ions?)?|ideas?|what should i do|what can i do|recommend(?:ations?)?|advice|tips?")
private val BRIEF = words("brief|briefing|summary|overview|summari[sz]e|my week|last week|this week|compare|comparison|what changed|how am i doing")
private val CAUSE = words("why|caus(?:e|es|ed|ation)|because|reasons?|affect|effects?|impact|improv(?:e|ed)|worse|better|correlat(?:e|ion)")
private val FOLLOWUP = Regex(
    "^(?:why|why is that|why was that|why did that happen|tell me more|more|explain that|explain|" +
        "what about that|what changed|compare them|compare|today|yesterday|and today|and yesterday|" +
        "what about today|what about yesterday|what about last week|what about the preceding week|and last week)$"
)
private val REMINDER_MENTION = words("remind|reminders?")
private val FORGET_MENTION = words("forget|delete|erase|remove|clear")
private const val REQUEST_PREFIX = "^(?:(?:please|can you|could you|would you|will you|i want you to|i would like you to) )*"
private val REMINDER_REQUEST = Regex(
    REQUEST_PREFIX + "(?:remind me|(?:set|create|schedule|add)(?: me)?(?: a| an)?(?: local)? reminder)\\b"
)
private val FORGET_REQUEST = Regex(REQUEST_PREFIX + "(?:forget|delete|erase|remove|clear)\\b")
private val PROTECTED_RECORDS = words("health connect|samsung|source|health records|sleep records|step records|exercise records")
private val WRITE_CHECKIN = Regex("\\b(?:write|save|store|remember|log|record|add|create|edit|update|change)\\b.{0,60}\\b(?:check[ -]?ins?|notes?|journal)\\b")
private val OTHER_ACTION = words(
    "schedule|set|create|add|save|store|remember|log|write|edit|update|enable|disable|install|uninstall|" +
        "export|share|notify|notifications?|alarms?|open|close|launch|reset|start|connect|disconnect|activate|toggle"
)
private val CHANGE_REQUEST = Regex(REQUEST_PREFIX + "(?:record|change)\\b")
private val INFORMATIONAL_START = Regex(
    "^(?:please )?(?:what|how|why|when|where|which|do|did|does|are|is|was|were|have|has|can|could|would|will|" +
        "show|tell|explain|compare|summari[sz]e|give|help|i|my|any|more|sleep|slept|steps?|activity|movement|" +
        "exercise|workouts?|check[ -]?ins?|notes?|evidence|sources?|basis|coverage|brief|briefing|summary|overview|" +
        "today|yesterday|and|let's|lets|a|suggest(?:ions?)?|ideas?|recommend(?:ations?)?|advice|tips?)\\b"
)
private val QUOTED = Regex("\"[^\"]*\"|`[^`]*`|(?<![\\p{L}\\p{N}])'[^']*'(?![\\p{L}\\p{N}])")
private val CONTEXT_TOPICS = setOf(Topic.BRIEF, Topic.SLEEP, Topic.ACTIVITY, Topic.WORKOUTS, Topic.CHECKINS, Topic.EVIDENCE, Topic.SUGGEST)

private fun prepareQuestion(question: String): Question? {
    if (question.length > MAX_QUESTION_LENGTH) return null
    val normalized = Normalizer.normalize(question, Normalizer.Form.NFKC)
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace(Regex("\\p{Cf}"), "")
        .replace(Regex("[\\p{Cc}\\p{Z}\\s]+"), " ")
        .trim()
        .lowercase(Locale.ROOT)
    if (normalized.isBlank() || normalized.length > MAX_QUESTION_LENGTH) return null
    val commands = normalized.replace(QUOTED, " ")
        .replace(Regex("[^\\p{L}\\p{N}'-]+"), " ")
        .trim()
    return Question(normalized, commands)
}

private fun route(query: Question, previousTopic: Topic?, heartRateContext: HeartRateContext? = null): RoutingDecision {
    if (MEDICAL.containsMatchIn(query.text) || PERSONAL_TARGET.containsMatchIn(query.text) ||
        HeartRateAnalysis.asksForAssessment(query.commands, previousTopic)
    ) {
        return RoutingDecision(Topic.SAFETY)
    }
    if (NEGATIVE.containsMatchIn(query.text) || DIRECTIVE.containsMatchIn(query.text) || OUTSIDE.containsMatchIn(query.text)) {
        return RoutingDecision(Topic.UNKNOWN)
    }
    if (SmallTalk.reply(query.commands) != null && !HeartRateAnalysis.isPendingResponse(query.commands, heartRateContext)) return RoutingDecision(Topic.CONVERSATION)
    if (HeartRateAnalysis.matches(query.commands, previousTopic, heartRateContext)) {
        return RoutingDecision(
            if (REMINDER_MENTION.containsMatchIn(query.commands) || FORGET_MENTION.containsMatchIn(query.commands) ||
                OTHER_ACTION.containsMatchIn(query.commands) || CHANGE_REQUEST.containsMatchIn(query.commands) ||
                CONFLICTING_REQUESTS.containsMatchIn(query.commands)
            ) Topic.UNKNOWN else Topic.HEART_RATE
        )
    }
    val metrics = explicitMetrics(query)
    val checkIns = CHECKINS.containsMatchIn(query.commands)
    val paraphrases = paraphraseTopics(query)
    val requestedTopics = metrics.map { metric -> metric.topic }.toSet() + paraphrases +
        if (checkIns) setOf(Topic.CHECKINS) else emptySet()
    if (CONFLICTING_REQUESTS.containsMatchIn(query.commands) ||
        requestedTopics.size > 1 && (checkIns || CHOICE.containsMatchIn(query.commands) || paraphrases.isNotEmpty())
    ) return RoutingDecision(Topic.UNKNOWN)
    val recordScope = metrics.isNotEmpty() || checkIns || RECORDS.containsMatchIn(query.commands) ||
        WELLBEING_ROUTINE.containsMatchIn(query.commands)
    val reminder = REMINDER_MENTION.containsMatchIn(query.commands)
    val forget = FORGET_MENTION.containsMatchIn(query.commands)
    if (reminder || forget) {
        val action = when {
            reminder && forget -> Topic.UNKNOWN
            reminder && recordScope && REMINDER_REQUEST.containsMatchIn(query.commands) -> Topic.REMINDER
            forget && (checkIns || APP_MEMORY.containsMatchIn(query.commands)) &&
                FORGET_REQUEST.containsMatchIn(query.commands) && !PROTECTED_RECORDS.containsMatchIn(query.commands) &&
                !SELECTIVE_FORGET.containsMatchIn(query.commands) -> Topic.FORGET
            else -> Topic.UNKNOWN
        }
        return RoutingDecision(action)
    }
    if (UNSUPPORTED_TIME.containsMatchIn(query.commands) ||
        DAY_COUNT.findAll(query.commands).any { match -> match.groupValues[1] !in setOf("7", "14") } ||
        TODAY.containsMatchIn(query.commands) && YESTERDAY.containsMatchIn(query.commands) ||
        WRITE_CHECKIN.containsMatchIn(query.commands) || OTHER_ACTION.containsMatchIn(query.commands) ||
        CHANGE_REQUEST.containsMatchIn(query.commands) || !INFORMATIONAL_START.containsMatchIn(query.commands)
    ) return RoutingDecision(Topic.UNKNOWN)
    val topic = when {
        EVIDENCE.containsMatchIn(query.commands) -> Topic.EVIDENCE
        SUGGEST.containsMatchIn(query.commands) -> {
            if (recordScope || previousTopic in CONTEXT_TOPICS) Topic.SUGGEST else Topic.UNKNOWN
        }
        checkIns -> Topic.CHECKINS
        metrics.size > 1 -> Topic.BRIEF
        metrics.size == 1 -> metrics.single().topic
        FOLLOWUP.matches(query.commands) && previousTopic in CONTEXT_TOPICS -> requireNotNull(previousTopic)
        BRIEF.containsMatchIn(query.commands) || RECORDS.containsMatchIn(query.commands) -> Topic.BRIEF
        else -> Topic.UNKNOWN
    }
    val advisoryTopics = when {
        topic == Topic.UNKNOWN && paraphrases.size == 1 && !SUGGEST.containsMatchIn(query.commands) -> paraphrases
        topic == Topic.BRIEF && metrics.isEmpty() && !BRIEF.containsMatchIn(query.commands) &&
            RECORDS.containsMatchIn(query.commands) -> ALL_METRICS.map { metric -> metric.topic }.toSet()
        else -> emptySet()
    }
    return RoutingDecision(topic, advisoryTopics)
}

private fun paraphraseTopics(query: Question): Set<Topic> = buildSet {
    if (NIGHT_RECORDS.containsMatchIn(query.commands)) add(Topic.SLEEP)
    if (ON_FEET.containsMatchIn(query.commands) || MOVEMENT_RECORDS.containsMatchIn(query.commands)) add(Topic.ACTIVITY)
    if (GYM_RECORDS.containsMatchIn(query.commands)) add(Topic.WORKOUTS)
}

private fun explicitMetrics(query: Question): List<Metric> = ALL_METRICS.filter { metric ->
    when (metric) {
        Metric.STEPS -> ACTIVITY.containsMatchIn(query.commands)
        Metric.SLEEP -> SLEEP.containsMatchIn(query.commands)
        Metric.EXERCISE -> WORKOUTS.containsMatchIn(query.commands)
    }
}

private fun metricsFor(query: Question, topic: Topic, previousTopic: Topic?): List<Metric> {
    ALL_METRICS.firstOrNull { metric -> metric.topic == topic }?.let { metric -> return listOf(metric) }
    val explicit = explicitMetrics(query)
    if (explicit.isNotEmpty()) return explicit
    if (topic == Topic.EVIDENCE || topic == Topic.SUGGEST) {
        ALL_METRICS.firstOrNull { metric -> metric.topic == previousTopic }?.let { metric -> return listOf(metric) }
    }
    return ALL_METRICS
}

private fun advisoryTopic(decision: RoutingDecision, modelTopic: Topic?): Topic {
    return if (modelTopic in decision.advisoryTopics) requireNotNull(modelTopic) else decision.topic
}

private fun safetyReply(query: Question): Reply {
    val urgency = if (URGENT.containsMatchIn(query.text)) {
        "I'm sorry you're dealing with this. If you may be in immediate danger, contact local emergency " +
            "services now; a trusted person can help you reach support. "
    } else {
        ""
    }
    return Reply(
        Topic.SAFETY,
        urgency + "I can't diagnose conditions, assess symptoms or emergencies, recommend treatment or medication, " +
            "or set personal sleep or exercise targets. Ask a qualified health professional about medical concerns; " +
            "I can summarize the available wellbeing records.",
        spokenText = urgency + "I can sit with the question, but these records can't tell us what's causing a symptom " +
            "or what treatment you need. Please ask a qualified health professional about personal medical concerns. " +
            "I can still help you understand what your shared records do and don't show."
    )
}
