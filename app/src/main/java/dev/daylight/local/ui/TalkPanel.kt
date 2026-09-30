package dev.daylight.local.ui

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.daylight.core.VoiceStage

internal class TalkPanel(private val ui: DaylightUi, private val actions: UiActions) {
    private val layout = ui.column()
    val view: View = layout
    private val scroll = DaylightScroll(ui.context, ui)
    private val messages = ui.column()
    private val route = ui.text("", 13f, DaylightColors.secondary)
    private val emptyConversation = ui.card(DaylightColors.indigo)
    private val questionButtons = mutableListOf<Button>()
    private val voiceHint = ui.text("", 12f, DaylightColors.secondary)
    private val speechHint = ui.text("", 13f, DaylightColors.secondary)
    private val voiceTitle = ui.text("A little space for you.", 22f, medium = true, heading = true)
    private val orb = VoiceOrb(ui.context)
    private var currentState: ScreenState? = null
    private var previousMessages: List<TalkMessage> = emptyList()
    private var followNextMessage = false
    private val question = EditText(ui.context)
    private val inputLabel = ui.eyebrow("YOUR QUESTION", DaylightColors.secondary)
    private val send = ui.button("Send", primary = true) { sendDraft() }
    private val record = ui.button("Talk to Ami", primary = true) { actions.onMicrophone() }
    private val stopVoice = ui.button("Stop voice", danger = true) { actions.onStopVoice() }
    private val speak = ui.button("Listen again") { actions.onSpeak() }
    private val voiceSettings = ui.button("Voice & pace") { actions.onVoiceSettings() }
    private val syntheticAudio = ui.button("Play a sample conversation") { actions.onSyntheticAudio() }
    private val debrief = ui.button("Recap my week") {
        followNextMessage = true
        actions.onDebrief()
    }
    private val checkIn = ui.button("Add a check-in") { actions.onSaveCheckIn() }
    private val reminder = ui.button("Set a reminder") { actions.onReminder() }
    private val historyAccess = ui.button("Read older heart-rate history", primary = true) { actions.onHistoryAccess() }
    private val latestMessage = ui.button("Jump to latest message ↓") {
        scroll.moveToEnd()
        hideLatestMessage()
    }

    fun clearDraft() {
        question.text?.clear()
        question.clearFocus()
        followNextMessage = false
    }

    fun frameSyntheticConversation(): Int {
        check(currentState?.demo == true) { "Only synthetic conversations may be framed for export." }
        scroll.scrollTo(0, if (currentState?.messages.isNullOrEmpty()) 0 else messages.top.coerceAtLeast(0))
        return scroll.scrollY
    }

    init {
        ui.add(scroll.content, ui.eyebrow("YOUR QUIET CORNER", DaylightColors.lavender))
        val voiceCard = ui.card(DaylightColors.indigo)
        val voiceRow = ui.row()
        voiceRow.addView(orb, LinearLayout.LayoutParams(ui.dp(90), ui.dp(90)))
        voiceRow.addView(voiceTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(10) })
        ui.add(voiceCard, voiceRow)
        ui.add(voiceCard, route, top = 8)
        ui.add(scroll.content, voiceCard, top = 14)

        ui.add(emptyConversation, ui.text(
            "Say hello, talk through your week, or ask for a wind-down idea. No scores to chase. No judgment. A wellbeing companion, not medical advice.",
            15f, DaylightColors.secondary
        ), top = 10)
        ui.add(scroll.content, emptyConversation, top = 14)

        val suggestions = ui.flow()
        for (prompt in listOf("Hello Ami", "My week", "When was my heartbeat highest this week?", "A wind-down idea")) {
            val shortcut = ui.button(prompt) {
                followNextMessage = true
                actions.onSend(prompt)
            }
            questionButtons.add(shortcut)
            suggestions.addView(shortcut)
        }
        ui.add(scroll.content, suggestions, top = 18)

        val personalActions = ui.flow()
        personalActions.addView(checkIn)
        personalActions.addView(reminder)
        personalActions.addView(debrief)
        ui.add(scroll.content, ui.eyebrow("ONLY WHEN YOU CHOOSE"), top = 24)
        ui.add(scroll.content, personalActions, top = 10)
        val voiceActions = ui.flow()
        voiceActions.addView(speak)
        voiceActions.addView(voiceSettings)
        voiceActions.addView(syntheticAudio)
        ui.add(scroll.content, voiceActions, top = 8)
        ui.add(scroll.content, speechHint, top = 10)
        ui.add(scroll.content, messages, top = 24)
        ui.add(scroll.content, historyAccess, top = 12)
        layout.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composerFrame = FrameLayout(ui.context)
        val composer = DaylightColumn(ui.context).apply {
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12))
        }
        latestMessage.showWhen(false)
        ui.add(composer, latestMessage, bottom = 8)
        val inputCard = ui.card().apply { setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)) }
        question.apply {
            id = View.generateViewId()
            hint = "Or type what's on your mind…"
            textSize = 16f
            setTextColor(DaylightColors.text)
            setHintTextColor(DaylightColors.muted)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(ui.dp(2), ui.dp(12), ui.dp(10), ui.dp(12))
            minimumHeight = ui.dp(48)
            minLines = 1
            maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(500))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false
            setSaveFromParentEnabled(false)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendDraft()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(value: Editable?) { updateSendAvailability() }
            })
        }
        inputLabel.labelFor = question.id
        ui.add(inputCard, inputLabel)
        val inputRow = ui.row().apply { gravity = Gravity.BOTTOM }
        inputRow.addView(question, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.dp(6)
        })
        ui.add(inputCard, inputRow, top = 4)
        ui.add(composer, inputCard)
        val recordingRow = ui.row()
        recordingRow.addView(record, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        recordingRow.addView(stopVoice, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ui.dp(8) })
        ui.add(composer, recordingRow, top = 8)
        ui.add(composer, voiceHint, top = 6)
        composerFrame.addView(composer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL
        ))
        ui.add(layout, composerFrame)
        scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (scroll.isNearEnd(ui.dp(80))) hideLatestMessage()
        }
    }

    fun render(state: ScreenState) {
        currentState = state
        val idle = !state.busy && !state.recording
        val speaking = state.voiceStage == VoiceStage.SPEAKING || state.voiceStage == VoiceStage.STARTING_SPEECH
        val voiceActive = state.voiceStage != VoiceStage.IDLE
        orb.render(state.voiceStage, state.voiceLevel)
        voiceTitle.showText(when (state.voiceStage) {
            VoiceStage.LISTENING -> "I'm listening."
            VoiceStage.TRANSCRIBING -> "Just a moment…"
            VoiceStage.PREPARING_REPLY -> "Let's take a look."
            VoiceStage.STARTING_SPEECH -> "Finding my voice…"
            VoiceStage.SPEAKING -> "A little perspective."
            VoiceStage.IDLE -> if (state.messages.isEmpty()) "A little space for you." else "Your pace. Your day."
        })
        route.showText(if (state.asrReady) {
            if (state.autoSpeak) "Tap Talk, speak naturally, and I'll reply aloud. A short pause finishes your turn. No always-on listening."
            else "Tap Talk to ask a question. Aloud replies are off; use Listen again when you want to hear one."
        } else {
            "You can type now. Prepare the optional models in Privacy to talk with me on this phone."
        })
        questionButtons.forEach { it.isEnabled = idle }
        debrief.isEnabled = idle
        debrief.showText(if (state.modelsReady) "Recap my week · experimental" else "Computed week recap")
        checkIn.isEnabled = idle
        reminder.isEnabled = idle
        historyAccess.showWhen(state.needsHistoryConsent && !state.demo)
        historyAccess.isEnabled = idle
        record.showText(if (state.recording) "Done" else if (speaking) "Interrupt & talk" else "Talk to Ami")
        record.contentDescription = if (state.recording) "Finish recording and get a reply" else if (speaking) "Interrupt speech and record a new question" else "Talk to Ami, microphone turns on after this tap"
        record.isEnabled = state.recording || ((idle || speaking) && state.asrReady)
        stopVoice.showWhen(voiceActive)
        stopVoice.showText(if (speaking) "Stop voice" else "Cancel turn")
        stopVoice.isEnabled = voiceActive
        voiceHint.showText(when {
            state.recording -> "Microphone on · pause to finish, or tap Done · 20 seconds maximum"
            speaking -> "Microphone off · interrupt any time"
            state.busy -> "One moment. You can still draft a question."
            state.asrReady -> "Microphone off · your next turn starts with a tap"
            else -> "Voice needs prepared models. You can still type."
        })
        voiceHint.setTextColor(if (state.recording) DaylightColors.rose else DaylightColors.secondary)
        speak.showWhen(state.speechReady)
        speak.isEnabled = idle && state.speechReady && state.messages.any { !it.user && it.text.isNotBlank() }
        voiceSettings.isEnabled = idle && state.speechReady
        syntheticAudio.showWhen(state.demo)
        syntheticAudio.isEnabled = idle && state.demo && state.asrReady
        speechHint.showText(buildString {
            append(if (state.speechReady) state.voiceLabel else "Reply speech is not ready.")
            if (state.demo) append(" Sample playback uses fictional records, not your microphone.")
            if (!state.asrReady) append(" Prepare optional models in Privacy.")
        })
        updateSendAvailability()
        updateMessages(state.messages)
    }

    fun setCompact(compact: Boolean) {
        inputLabel.showWhen(!compact)
        voiceHint.showWhen(!compact)
    }

    private fun sendDraft() {
        val state = currentState ?: return
        val draft = question.text?.toString()?.trim().orEmpty()
        if (draft.isEmpty() || draft.length > 500 || state.busy || state.recording) return
        followNextMessage = true
        question.text?.clear()
        actions.onSend(draft)
    }

    private fun updateSendAvailability() {
        val state = currentState
        send.isEnabled = state != null && !state.busy && !state.recording && !question.text.isNullOrBlank()
    }

    private fun hideLatestMessage() {
        latestMessage.showWhen(false)
    }

    private fun updateMessages(nextMessages: List<TalkMessage>) {
        if (nextMessages == previousMessages) return
        val shouldFollow = followNextMessage || previousMessages.isEmpty() || scroll.isNearEnd(ui.dp(96))
        val appendOnly = nextMessages.size >= previousMessages.size &&
            nextMessages.take(previousMessages.size) == previousMessages
        val firstNewIndex = if (appendOnly) previousMessages.size else 0
        if (!appendOnly) messages.removeAllViews()
        for (messageIndex in firstNewIndex until nextMessages.size) {
            ui.add(messages, messageBubble(nextMessages[messageIndex]), top = if (messageIndex == 0) 0 else 14)
        }
        val hasNewMessages = nextMessages.size > previousMessages.size
        previousMessages = nextMessages.toList()
        emptyConversation.showWhen(nextMessages.isEmpty())
        if (nextMessages.isEmpty()) {
            hideLatestMessage()
        } else if (shouldFollow) {
            scroll.moveToEnd()
            hideLatestMessage()
        } else if (hasNewMessages) {
            latestMessage.showWhen(true)
        }
        followNextMessage = false
    }

    private fun messageBubble(message: TalkMessage): View {
        val frame = FrameLayout(ui.context).apply {
            setPadding(if (message.user) ui.dp(26) else 0, 0, if (message.user) 0 else ui.dp(14), 0)
        }
        val bubble = ui.card(if (message.user) DaylightColors.lavender else DaylightColors.elevated)
        val foreground = if (message.user) DaylightColors.background else DaylightColors.text
        val secondary = if (message.user) DaylightColors.indigo else DaylightColors.secondary
        ui.add(bubble, ui.text(if (message.user) "You" else "Ami", 13f, secondary, medium = true))
        ui.add(bubble, ui.text(message.text, 16f, foreground).apply { setTextIsSelectable(true) }, top = 8)
        if (message.user && message.voiceTranscript) {
            ui.add(bubble, ui.button("Edit heard question") {
                if (currentState?.busy == false && currentState?.recording == false) {
                    question.setText(message.text)
                    question.setSelection(question.text.length)
                    question.requestFocus()
                }
            }, top = 8)
        }
        if (!message.user) {
            ui.add(bubble, ui.text(message.provenance.ifBlank { "Provenance not supplied" }, 11f, secondary), top = 12)
            if (message.details.isNotBlank() && message.details != message.text) {
                val detail = ui.text(message.details, 13f, secondary).apply { visibility = View.GONE; setTextIsSelectable(true) }
                val disclosure = ui.button("Calculation & coverage") {
                    detail.showWhen(detail.visibility != View.VISIBLE)
                }
                disclosure.contentDescription = "Show or hide the full calculation and coverage for this answer"
                ui.add(bubble, disclosure, top = 8)
                ui.add(bubble, detail, top = 8)
            }
        }
        frame.addView(bubble, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (message.user) Gravity.END else Gravity.START
        ))
        return frame
    }
}
