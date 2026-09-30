package dev.daylight.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal sealed class HeartRateResolution {
    data class Peak(val window: HeartRateWindow) : HeartRateResolution()
    data class Clarify(val window: HeartRateWindow, val declined: Boolean = false) : HeartRateResolution()
    data class Unsupported(val message: String) : HeartRateResolution()
}

internal object HeartRateQueries {
    private val names = Regex("\\b(?:heart[ -]?(?:rate|beat)s?|pulse|bpm)\\b")
    private val peak = Regex("\\b(?:highest|high|maximum|max|peak(?:ed)?|fastest)\\b")
    private val confirmation = Regex("^(?:yes(?: please)?|yeah|yep|correct|that's right|that is right|the highest|highest|yes the highest)$")
    private val rejection = Regex("^(?:no|no thanks|not that|that's not what i meant)$")
    private val detail = Regex("^(?:when exactly|when was that|when was it highest|tell me more|why|why was that|why did that happen|show evidence|evidence|how do you know|show the numbers)$")
    private val followUp = Regex(
        "^(?:(?:and|what about|how about) )?(?:the )?(?:today|yesterday|this (?:calendar )?(?:week|month)|" +
            "(?:last|previous|preceding) (?:calendar )?(?:week|month)|(?:last|past) (?:7|seven|14|fourteen|30|thirty) (?:calendar )?days|past month)$"
    )
    private val unsupportedOperation = Regex(
        "\\b(?:average|mean|median|lowest|low|minimum|resting|rest|asleep|sleep|sleeping|" +
            "exercise|workouts?|training|walking|running|compare|comparison|trend|" +
            "duration|how long|minutes?|hours?|seconds?|above|below|under|exceed(?:ed)?|" +
            "higher than|lower than|greater than|less than|at least|at most|" +
            "right now|live|current|hrv|variability|every|each|per day|" +
            "how many times|episodes?|spikes?|steps|morning|afternoon|evening|night)\\b|" +
            "\\bover\\b(?!\\s+(?:the )?(?:last|past|this|previous|today|yesterday))|" +
            "[<>≤≥=]|\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:bpm|beats)\\b"
    )
    private val otherTime = Regex(
        "\\b(?:tomorrow|tonight|next|since|before|after|ago|earlier|later|date|dates|" +
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "january|february|march|april|may|june|july|august|september|october|november|december|" +
            "days?|weeks?|months?|years?|weekends?|fortnight|completed)\\b|\\b[0-9]{4}\\b|\\d+[/:-]\\d+"
    )
    private val monthName = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)

    fun mentionsHeartRate(question: String): Boolean = names.containsMatchIn(question)

    fun matchesFollowUp(question: String, context: HeartRateContext?): Boolean =
        followUp.matches(question) || detail.matches(question) ||
            context != null && (confirmation.matches(question) || rejection.matches(question) ||
                Regex("^(?:i meant |the )?(?:highest|lowest|average|maximum|minimum)(?: one)?$").matches(question))

    fun isPendingResponse(question: String, context: HeartRateContext?): Boolean =
        context?.awaitingPeakConfirmation == true && (confirmation.matches(question) || rejection.matches(question))

    fun resolve(question: String, today: LocalDate, context: HeartRateContext?): HeartRateResolution {
        val clean = question.trim().trimEnd('?', '.', '!')
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        val candidates = listOf(
            Regex("\\btoday\\b") to HeartRateWindow(today, today, "today so far"),
            Regex("\\byesterday\\b") to HeartRateWindow(today.minusDays(1), today.minusDays(1), "yesterday"),
            Regex("\\bthis (?:calendar )?week\\b") to HeartRateWindow(monday, today, "this calendar week so far"),
            Regex("\\b(?:last|previous|preceding) (?:calendar )?week\\b") to
                HeartRateWindow(monday.minusWeeks(1), monday.minusDays(1), "last calendar week"),
            Regex("\\b(?:7|seven) (?:calendar )?days\\b|\\bpast week\\b") to
                HeartRateWindow(today.minusDays(6), today, "in the last 7 calendar days, including today so far"),
            Regex("\\b(?:14|fourteen) (?:calendar )?days\\b|\\bfortnight\\b") to
                HeartRateWindow(today.minusDays(13), today, "in the last 14 calendar days, including today so far"),
            Regex("\\b(?:30|thirty) (?:calendar )?days\\b|\\bpast month\\b") to
                HeartRateWindow(today.minusDays(29), today, "in the last 30 calendar days, including today so far"),
            Regex("\\bthis (?:calendar )?month\\b") to
                HeartRateWindow(monthStart, today, "in ${today.format(monthName)} so far"),
            Regex("\\b(?:last|previous|preceding) (?:calendar )?month\\b") to
                HeartRateWindow(monthStart.minusMonths(1), monthStart.minusDays(1), "in ${monthStart.minusMonths(1).format(monthName)}")
        )
        val requested = candidates.filter { (pattern, _) -> pattern.containsMatchIn(clean) }
        val remainder = requested.fold(clean) { remaining, (pattern, _) -> remaining.replace(pattern, " ") }
        if (requested.size > 1 || otherTime.containsMatchIn(remainder)) return HeartRateResolution.Unsupported(
            "I can find the highest recorded heart rate, but I need one clear period. " +
                "Try today, yesterday, this or last week, this or last month, or the last 7, 14 or 30 days. I haven't changed your requested dates."
        )
        if (unsupportedOperation.containsMatchIn(clean)) return HeartRateResolution.Unsupported(
            "I can find the highest recorded heart rate and its time, but not that calculation yet. " +
                "I won't replace an average, activity-specific reading or threshold question with a peak."
        )
        if (context != null && rejection.matches(clean)) return HeartRateResolution.Clarify(context.window, declined = true)
        if (requested.isEmpty() && context == null && !mentionsHeartRate(clean)) return HeartRateResolution.Unsupported(
            "Please include the time window again. I don't have the dates of the earlier heart-rate question in this session."
        )
        val window = requested.singleOrNull()?.second ?: context?.window ?: HeartRateWindow(monday, today, "this calendar week so far")
        val confirmed = peak.containsMatchIn(clean) || context != null && confirmation.matches(clean) ||
            context?.awaitingPeakConfirmation == false && (detail.matches(clean) || followUp.matches(clean)) ||
            context == null && followUp.matches(clean)
        return if (confirmed) HeartRateResolution.Peak(window) else HeartRateResolution.Clarify(window)
    }
}
