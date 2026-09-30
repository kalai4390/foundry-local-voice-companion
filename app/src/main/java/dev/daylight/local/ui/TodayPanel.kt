package dev.daylight.local.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import dev.daylight.core.EvidenceFact
import dev.daylight.core.StatCard

internal class TodayPanel(private val ui: DaylightUi, private val actions: UiActions) {
    private val scroll = DaylightScroll(ui.context, ui)
    val view: View = scroll
    private val headline = ui.text("", 29f, medium = true, heading = true)
    private val context = ui.text("", 15f, DaylightColors.secondary)
    private val mode = ui.eyebrow("TODAY")
    private val cards = ui.column()
    private val metricViews = mutableListOf<DaylightMetricCard>()
    private val questionButtons = mutableListOf<Button>()
    private val coverage = ui.column()
    private var previousEvidence: List<EvidenceFact>? = null
    private val sourceHeading = ui.text("", 20f, medium = true, heading = true)
    private val sourceDescription = ui.text("", 14f, DaylightColors.secondary)
    private val connect = ui.button("Connect Samsung Health", primary = true) { actions.onConnect() }
    private val refresh = ui.button("Refresh permitted records") { actions.onRefresh() }
    private val healthData = ui.button("Open Health Connect data") { actions.onOpenHealthSettings() }
    private val demo = ui.button("Use synthetic demo") { actions.onDemo() }
    private val runtime = ui.text("", 15f, medium = true)
    private val routing = ui.text("", 14f, DaylightColors.secondary)

    init {
        val hero = ui.card().apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(DaylightColors.indigo, DaylightColors.surface)
            ).apply {
                cornerRadius = ui.dp(24).toFloat()
                setStroke(ui.dp(1), DaylightColors.line)
            }
        }
        val heroHeading = ui.row()
        heroHeading.addView(mode, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroHeading.addView(ui.icon(DaylightGlyph.SUN, DaylightColors.mint), LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)))
        ui.add(hero, heroHeading)
        ui.add(hero, headline, top = 16)
        ui.add(hero, context, top = 12)
        ui.add(hero, ui.text("A little context. Not a score.", 13f, DaylightColors.lavender), top = 18)
        ui.add(scroll.content, hero)

        val sourceCard = ui.card(DaylightColors.mintSurface)
        ui.add(sourceCard, ui.eyebrow("SOURCE & AVAILABILITY"))
        ui.add(sourceCard, sourceHeading, top = 10)
        ui.add(sourceCard, sourceDescription, top = 10)
        ui.add(sourceCard, connect, top = 18)
        val sourceActions = ui.flow()
        sourceActions.addView(refresh)
        sourceActions.addView(healthData)
        sourceActions.addView(demo)
        ui.add(sourceCard, sourceActions, top = 8)
        ui.add(scroll.content, sourceCard, top = 16)

        ui.add(scroll.content, ui.text("Your snapshot", 22f, medium = true, heading = true), top = 28)
        ui.add(scroll.content, ui.text(
            "Completed days, oldest first. Each chart has its own scale and a zero baseline.",
            13f, DaylightColors.secondary
        ), top = 8)
        ui.add(scroll.content, cards, top = 16)

        val ask = ui.card(DaylightColors.indigo)
        ui.add(ask, ui.eyebrow("A GOOD PLACE TO START", DaylightColors.lavender))
        ui.add(ask, ui.text("Ask your day.", 24f, medium = true, heading = true), top = 10)
        ui.add(ask, ui.text("Short questions. Answers with their source in view.", 14f, DaylightColors.secondary), top = 8)
        val questions = ui.flow()
        for (question in listOf("How was my week?", "Compare my sleep", "Compare my steps", "Show my workouts")) {
            val shortcut = ui.button(question) {
                actions.onTab(Tab.TALK)
                actions.onSend(question)
            }
            questionButtons.add(shortcut)
            questions.addView(shortcut)
        }
        ui.add(ask, questions, top = 16)
        ui.add(scroll.content, ask, top = 24)

        val coverageCard = ui.card()
        ui.add(coverageCard, ui.eyebrow("KNOW WHAT IS HERE", DaylightColors.blue))
        ui.add(coverageCard, ui.text("Source & coverage", 21f, medium = true, heading = true), top = 10)
        ui.add(coverageCard, ui.text(
            "Coverage describes the records available, not a complete picture of your day.",
            14f, DaylightColors.secondary
        ), top = 8)
        ui.add(coverageCard, coverage, top = 16)
        ui.add(coverageCard, ui.button("Open the evidence") { actions.onTab(Tab.EVIDENCE) }, top = 18)
        ui.add(scroll.content, coverageCard, top = 16)

        val runtimeCard = ui.card()
        ui.add(runtimeCard, ui.eyebrow("OPTIONAL LOCAL MODELS", DaylightColors.lavender))
        ui.add(runtimeCard, runtime, top = 10)
        ui.add(runtimeCard, routing, top = 8)
        ui.add(runtimeCard, ui.button("Models & privacy") { actions.onTab(Tab.PRIVACY) }, top = 14)
        ui.add(scroll.content, runtimeCard, top = 16)
    }

    fun render(state: ScreenState) {
        val idle = !state.busy && !state.recording
        mode.showText(if (state.demo) "TODAY / SYNTHETIC DEMO" else "TODAY / PERSONAL MODE")
        headline.showText(state.dashboard.headline)
        context.showText(state.dashboard.context)
        if (metricViews.size != state.dashboard.cards.size) {
            cards.removeAllViews()
            metricViews.clear()
            val accents = listOf(DaylightColors.lavender, DaylightColors.mint, DaylightColors.blue)
            state.dashboard.cards.forEachIndexed { index, _ ->
                val metric = DaylightMetricCard(ui, accents[index % accents.size])
                metricViews.add(metric)
                ui.add(cards, metric.view, top = if (index == 0) 0 else 12)
            }
        }
        state.dashboard.cards.forEachIndexed { index, card -> metricViews[index].bind(card) }
        questionButtons.forEach { it.isEnabled = idle }
        connect.isEnabled = idle
        refresh.showWhen(!state.demo)
        refresh.isEnabled = idle && !state.demo
        healthData.showWhen(!state.demo)
        healthData.isEnabled = idle && !state.demo
        demo.showText(if (state.demo) "Synthetic demo active" else "Use synthetic demo")
        demo.isEnabled = idle && !state.demo
        sourceHeading.showText(if (state.demo) "Explore without personal records." else "What the read actually returned.")
        sourceDescription.showText(if (state.demo) {
            "You are viewing synthetic records, not your health data. Samsung Health is optional and requires separate read-only consent through Health Connect."
        } else {
            "Samsung Health via Health Connect. ${state.dashboard.availability.detail} " +
                "No sync, permission change or background read is performed by this screen."
        })
        runtime.showText(state.runtimeStatus)
        routing.showText(if (state.modelsReady) {
            "Whisper transcribes speech. Normal answers use deterministic rules. The optional, separately confirmed Qwen recap only selects calculated fact cards."
        } else {
            "Typing and computed recaps work without models. These are application rules, not generated reasoning. Prepare optional models only when you choose."
        })
        if (previousEvidence != state.evidence) {
            previousEvidence = state.evidence.toList()
            coverage.removeAllViews()
            if (state.evidence.isEmpty()) {
                ui.add(coverage, ui.text("No source coverage has been supplied yet.", 14f, DaylightColors.secondary))
            } else {
                state.evidence.forEachIndexed { index, fact ->
                    if (index > 0) {
                        val separator = ui.divider()
                        coverage.addView(separator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                            topMargin = ui.dp(16)
                            bottomMargin = ui.dp(16)
                        })
                    }
                    ui.add(coverage, ui.text(fact.title, 15f, medium = true))
                    ui.add(coverage, ui.text(fact.coverage, 14f, DaylightColors.mint), top = 6)
                    ui.add(coverage, ui.text(fact.source, 13f, DaylightColors.secondary), top = 6)
                    ui.add(coverage, ui.text(fact.dates, 13f, DaylightColors.secondary), top = 4)
                }
            }
        }
    }
}

private class DaylightMetricCard(private val ui: DaylightUi, private val accent: Int) {
    val view = ui.card()
    private val label = ui.text("", 16f, medium = true, heading = true)
    private val value = ui.text("", 36f, medium = true)
    private val unit = ui.text("", 14f, DaylightColors.lavender)
    private val detail = ui.text("", 14f, DaylightColors.secondary)
    private val chart = DaylightTrendView(ui.context, accent)
    private val legend = ui.text("", 12f, DaylightColors.secondary)
    private var previousCard: StatCard? = null

    init {
        val headingRow = ui.row()
        val icon = ui.icon(DaylightGlyph.TREND, accent).apply {
            background = ui.rounded(DaylightColors.elevated, 12)
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
        }
        headingRow.addView(icon, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        headingRow.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.dp(12)
            gravity = Gravity.CENTER_VERTICAL
        })
        ui.add(view, headingRow)
        value.fontFeatureSettings = "tnum"
        ui.add(view, value, top = 16)
        ui.add(view, unit, top = 2)
        ui.add(view, detail, top = 8)
        ui.add(view, chart, top = 12)
        ui.add(view, legend, top = 4)
    }

    fun bind(card: StatCard) {
        if (previousCard == card) return
        previousCard = card.copy(series = card.series.toList())
        label.showText(card.label)
        val measure = Regex("^([-+0-9.,]+) (.+)$").matchEntire(card.value)
        value.showText(measure?.groupValues?.get(1) ?: card.value)
        value.textSize = if (measure == null) 23f else 36f
        unit.showText(measure?.groupValues?.get(2)?.replace("/", " / ") ?: "")
        unit.showWhen(measure != null)
        detail.showText(card.detail)
        chart.bind(card.label, card.series)
        legend.showText(if (card.series.any { it != null && !it.isFinite() }) {
            "Bar: value · Dash: zero · Outline: missing · ×: unavailable"
        } else {
            "Bar: value · Dash: zero · Dashed outline: missing"
        })
        legend.showWhen(card.series.isNotEmpty())
    }
}
