package dev.daylight.local.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import dev.daylight.core.EvidenceFact

internal class EvidencePanel(private val ui: DaylightUi, private val actions: UiActions) {
    private val scroll = DaylightScroll(ui.context, ui)
    val view: View = scroll
    private val mode = ui.eyebrow("EVIDENCE", DaylightColors.blue)
    private val facts = ui.column()
    private var previousFacts: List<EvidenceFact>? = null
    private val empty = ui.card()
    private val connect = ui.button("Connect Samsung Health", primary = true) { actions.onConnect() }
    private val demo = ui.button("Use synthetic demo") { actions.onDemo() }

    init {
        ui.add(scroll.content, mode)
        ui.add(scroll.content, ui.text("Evidence, not guesswork.", 29f, medium = true, heading = true), top = 10)
        ui.add(scroll.content, ui.text(
            "The facts supplied to Ami. Values, basis, coverage, sources and dates are shown as provided, without adding an interpretation.",
            15f, DaylightColors.secondary
        ), top = 12)
        val note = ui.card(DaylightColors.indigo)
        ui.add(note, ui.text("Missing is not zero.", 17f, DaylightColors.lavender, medium = true))
        ui.add(note, ui.text(
            "A gap means no supplied record. Sleep-session duration is not the same as time physiologically asleep.",
            14f, DaylightColors.secondary
        ), top = 8)
        ui.add(scroll.content, note, top = 22)
        ui.add(scroll.content, facts, top = 18)
        ui.add(empty, ui.text("No evidence supplied yet.", 21f, medium = true, heading = true))
        ui.add(empty, ui.text(
            "You can explore with synthetic records or choose separate read-only consent for personal records. No values are inferred from an empty result.",
            15f, DaylightColors.secondary
        ), top = 10)
        ui.add(empty, connect, top = 18)
        ui.add(empty, demo, top = 8)
        ui.add(scroll.content, empty, top = 12)
    }

    fun render(state: ScreenState) {
        mode.showText(if (state.demo) "EVIDENCE / SYNTHETIC DEMO" else "EVIDENCE / PERSONAL MODE")
        connect.isEnabled = !state.busy && !state.recording
        demo.isEnabled = !state.busy && !state.recording && !state.demo
        demo.showText(if (state.demo) "Synthetic demo active" else "Use synthetic demo")
        empty.showWhen(state.evidence.isEmpty())
        if (previousFacts == state.evidence) return
        previousFacts = state.evidence.toList()
        facts.removeAllViews()
        state.evidence.forEachIndexed { index, fact ->
            val card = ui.card()
            ui.add(card, ui.eyebrow("SUPPLIED FACT ${index + 1}", DaylightColors.blue))
            ui.add(card, ui.text(fact.title, 19f, medium = true, heading = true).apply { setTextIsSelectable(true) }, top = 10)
            ui.add(card, ui.text(fact.value, 28f, DaylightColors.mint, medium = true).apply {
                fontFeatureSettings = "tnum"
                setTextIsSelectable(true)
            }, top = 12)
            field(card, "BASIS", fact.basis)
            field(card, "COVERAGE", fact.coverage)
            field(card, "SOURCE", fact.source)
            field(card, "DATES", fact.dates)
            ui.add(facts, card, top = if (index == 0) 0 else 16)
        }
    }

    private fun field(card: LinearLayout, label: String, value: String) {
        ui.add(card, ui.eyebrow(label, DaylightColors.secondary), top = 18)
        ui.add(card, ui.text(value, 15f).apply { setTextIsSelectable(true) }, top = 6)
    }
}

internal class PrivacyPanel(private val ui: DaylightUi, private val actions: UiActions) {
    private val scroll = DaylightScroll(ui.context, ui)
    val view: View = scroll
    private val modelFlag = ui.eyebrow("OPTIONAL LOCAL MODELS", DaylightColors.lavender)
    private val runtime = ui.text("", 17f, medium = true)
    private val speech = ui.text("", 13f, DaylightColors.secondary)
    private val prepare = ui.button("Prepare models", primary = true) { actions.onPrepareModels() }
    private val progressSection = ui.column()
    private val progressLabel = ui.text("", 13f, DaylightColors.lavender)
    private val download = ProgressBar(ui.context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        isIndeterminate = false
        progressTintList = ColorStateList.valueOf(DaylightColors.lavender)
        progressBackgroundTintList = ColorStateList.valueOf(DaylightColors.line)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val timingSection = ui.column()
    private val timing = ui.text("", 13f, DaylightColors.secondary)
    private val memoryCount = ui.text("", 35f, DaylightColors.mint, medium = true)
    private val memoryMode = ui.text("", 14f, DaylightColors.secondary)
    private val manageMemory = ui.button("Manage check-ins") { actions.onShowCheckIns() }
    private val forgetMemory = ui.button("Forget app memory…", danger = true) { actions.onForget() }
    private val reminderStatus = ui.text("", 16f, medium = true)
    private val schedule = ui.button("Set a reminder") { actions.onReminder() }
    private val cancel = ui.button("Cancel reminder", danger = true) { actions.onCancelReminder() }
    private val connect = ui.button("Connect Samsung Health") { actions.onConnect() }
    private val healthSettings = ui.button("Open Health Connect data") { actions.onOpenHealthSettings() }
    private val demo = ui.button("Switch to synthetic demo") { actions.onDemo() }
    private val sourceMode = ui.text("", 14f, DaylightColors.secondary)

    init {
        ui.add(scroll.content, ui.eyebrow("PRIVACY & CONTROL"))
        ui.add(scroll.content, ui.text("You set the boundaries.", 29f, medium = true, heading = true), top = 10)
        ui.add(scroll.content, ui.text(
            "Optional connections. Deliberate actions. A clear account of what Ami does—and what it does not.",
            15f, DaylightColors.secondary
        ), top = 12)

        val consent = ui.card(DaylightColors.mintSurface)
        ui.add(consent, ui.eyebrow("01 / SEPARATE, READ-ONLY CONSENT"))
        ui.add(consent, ui.text("Your health records are optional.", 21f, medium = true, heading = true), top = 10)
        ui.add(consent, ui.text(
            "Personal records require a separate disclosure and read-only Health Connect permission. Heart rate has its own optional permission; steps and sleep access do not grant it. Opening Ami does not automatically read health records or start a recording.",
            15f, DaylightColors.secondary
        ), top = 10)
        ui.add(consent, sourceMode, top = 12)
        ui.add(consent, connect, top = 16)
        ui.add(consent, healthSettings, top = 8)
        ui.add(consent, demo, top = 8)
        ui.add(scroll.content, consent, top = 24)

        val models = ui.card(DaylightColors.indigo)
        ui.add(models, modelFlag)
        ui.add(models, ui.text("Local models. Your decision.", 21f, medium = true, heading = true), top = 10)
        ui.add(models, runtime, top = 16)
        ui.add(models, ui.text(
            "Typed questions always use deterministic routing and calculated answers. Whisper provides optional speech input. A separately confirmed experimental Qwen recap selects calculated fact cards; it cannot write advice or alter values. Setup may download models; there is no cloud inference fallback.",
            15f, DaylightColors.secondary
        ), top = 10)
        ui.add(progressSection, progressLabel)
        progressSection.addView(download, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(8)).apply {
            topMargin = ui.dp(10)
        })
        ui.add(models, progressSection, top = 16)
        ui.add(models, prepare, top = 16)
        ui.add(models, speech, top = 10)
        ui.add(timingSection, ui.eyebrow("RUNTIME REPORT", DaylightColors.secondary))
        ui.add(timingSection, timing, top = 6)
        ui.add(models, timingSection, top = 14)
        ui.add(scroll.content, models, top = 16)

        val memory = ui.card()
        ui.add(memory, ui.eyebrow("02 / APP-ONLY MEMORY"))
        ui.add(memory, ui.text("Keep only what you choose.", 21f, medium = true, heading = true), top = 10)
        ui.add(memory, memoryCount, top = 16)
        ui.add(memory, memoryMode, top = 6)
        ui.add(memory, ui.text(
            "Intentional check-ins belong to Ami’s app-only memory. Demo and personal memory are separate. Managing or forgetting this memory does not delete your Samsung Health or Health Connect records.",
            15f, DaylightColors.secondary
        ), top = 12)
        val memoryActions = ui.flow()
        memoryActions.addView(manageMemory)
        memoryActions.addView(forgetMemory)
        ui.add(memory, memoryActions, top = 16)
        ui.add(scroll.content, memory, top = 16)

        val reminders = ui.card()
        ui.add(reminders, ui.eyebrow("03 / INTENTIONAL REMINDERS", DaylightColors.blue))
        ui.add(reminders, reminderStatus, top = 12)
        ui.add(reminders, ui.text(
            "A reminder is optional and scheduling requires your confirmation. You can cancel Ami’s reminder here.",
            14f, DaylightColors.secondary
        ), top = 10)
        val reminderActions = ui.flow()
        reminderActions.addView(schedule)
        reminderActions.addView(cancel)
        ui.add(reminders, reminderActions, top = 16)
        ui.add(scroll.content, reminders, top = 16)

        disclosure(
            "04 / VOICE IS A GESTURE",
            "Your voice. Your pace.",
            "Tap Talk to start the microphone after permission. A short pause or Done finishes the turn; Cancel discards it. Recording is capped at 20 seconds. Replies speak back automatically unless you turn that off in Voice & pace. Typed questions stay quiet. Listen again is explicit playback; Play a sample conversation uses fictional records and no microphone. Leaving the foreground stops voice. Only installed English voices marked offline are offered; there is no cloud voice fallback."
        )
        disclosure(
            "05 / THE UPSTREAM BOUNDARY",
            "Your tracker has its own settings.",
            "Samsung Health or another upstream tracker may cloud-sync separately. Ami does not control those services or settings. Local inference does not mean all network traffic has been ruled out; initial model downloads and upstream sync can use the network."
        )
        val prototype = ui.card()
        ui.add(prototype, ui.eyebrow("NONCLINICAL PROTOTYPE", DaylightColors.amber))
        ui.add(prototype, ui.text("Perspective, not a diagnosis.", 20f, medium = true, heading = true), top = 10)
        ui.add(prototype, ui.text(
            "Ami is a wellness prototype, not a medical device or a source of clinical advice. Missing records are not zero. Recorded sleep-session duration is not a measurement of time physiologically asleep.",
            15f, DaylightColors.secondary
        ), top = 10)
        ui.add(scroll.content, prototype, top = 16)
    }

    fun render(state: ScreenState) {
        val idle = !state.busy && !state.recording
        modelFlag.showText(if (state.modelsReady) "LOCAL MODELS / PREPARED" else "LOCAL MODELS / OPTIONAL")
        runtime.showText(state.runtimeStatus)
        speech.showText(if (state.speechReady) "Reply speech is ready." else "Reply speech is not ready. Typing remains available.")
        prepare.showText(if (state.modelsReady) "Models prepared" else "Prepare models")
        prepare.isEnabled = idle && !state.modelsReady
        val percentage = state.downloadProgress
        progressSection.showWhen(percentage != null)
        if (percentage != null) {
            if (percentage in 0..100) {
                download.isIndeterminate = false
                download.progress = percentage
                progressLabel.showText("Download progress · $percentage%")
            } else {
                download.isIndeterminate = true
                progressLabel.showText("Download progress unavailable")
            }
        }
        timingSection.showWhen(state.timing.isNotBlank())
        timing.showText(state.timing)
        memoryCount.showText(state.checkInCount.toString())
        memoryCount.contentDescription = "${state.checkInCount} saved check-ins"
        memoryMode.showText(if (state.demo) "Saved check-ins · synthetic demo memory" else "Saved check-ins · personal memory")
        reminderStatus.showText(state.reminder)
        sourceMode.showText(if (state.demo) {
            "Current mode: DEMO. All displayed health records are synthetic."
        } else {
            "Current mode: PERSONAL. Permission and source coverage determine which records are available."
        })
        listOf(manageMemory, forgetMemory, schedule, cancel, connect, healthSettings).forEach { it.isEnabled = idle }
        demo.isEnabled = idle && !state.demo
        demo.showText(if (state.demo) "Synthetic demo active" else "Switch to synthetic demo")
    }

    private fun disclosure(label: String, heading: String, body: String) {
        val card = ui.card()
        ui.add(card, ui.eyebrow(label, DaylightColors.lavender))
        ui.add(card, ui.text(heading, 21f, medium = true, heading = true), top = 10)
        ui.add(card, ui.text(body, 15f, DaylightColors.secondary), top = 10)
        ui.add(scroll.content, card, top = 16)
    }
}
