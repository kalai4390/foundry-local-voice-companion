package dev.daylight.local

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import dev.daylight.core.CheckIn
import dev.daylight.core.CompanionEngine
import dev.daylight.core.DebriefOrganizer
import dev.daylight.core.DebriefResult
import dev.daylight.core.DebriefStatus
import dev.daylight.core.EvidenceFact
import dev.daylight.core.HeartRateStatus
import dev.daylight.core.HeartRateContext
import dev.daylight.core.HeartRateWindow
import dev.daylight.core.Reply
import dev.daylight.core.SyntheticData
import dev.daylight.core.Topic
import dev.daylight.core.SpeechStyle
import dev.daylight.core.VoiceExperience
import dev.daylight.core.VoiceOrigin
import dev.daylight.core.VoiceStage
import dev.daylight.local.data.HealthReader
import dev.daylight.local.data.PrivateMemory
import dev.daylight.local.inference.FoundryRuntime
import dev.daylight.local.inference.DebriefInference
import dev.daylight.local.inference.LocalSpeaker
import dev.daylight.local.inference.ShortRecorder
import dev.daylight.local.ui.DaylightScreen
import dev.daylight.local.ui.ScreenState
import dev.daylight.local.ui.Tab
import dev.daylight.local.ui.TalkMessage
import dev.daylight.local.ui.UiActions
import dev.daylight.local.ui.VoicePreferences
import dev.daylight.local.ui.VoiceSettingsDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val engine = CompanionEngine()
    internal var snapshot = SyntheticData.create()
    internal var checkIns = emptyList<CheckIn>()
    internal lateinit var runtime: FoundryRuntime
    internal lateinit var speaker: LocalSpeaker
    internal lateinit var screen: DaylightScreen
    internal lateinit var state: ScreenState
    internal var work: Job? = null
    private lateinit var health: HealthReader
    private lateinit var memory: PrivateMemory
    private lateinit var recorder: ShortRecorder
    private lateinit var reminders: Reminders
    private var previousTopic: Topic? = null
    private var heartRateContext: HeartRateContext? = null
    private var lastReply: Reply? = null
    private var pendingHealthConsent = false
    private var pendingHistoryWindow: HeartRateWindow? = null
    private var pendingMicrophoneConsent = false
    private var pendingReminderConsent = false
    private var foreground = false
    private var resumed = false
    private var workGeneration = 0L
    private val voicePreferences by lazy { getSharedPreferences("voice-preferences", MODE_PRIVATE) }
    private val visibleDialogs = mutableSetOf<Dialog>()

    private val healthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val requested = pendingHealthConsent
        pendingHealthConsent = false
        if (requested && granted.containsAll(HealthReader.basePermissions)) loadHealth()
        else if (::state.isInitialized) update { copy(status = "Health access was not granted. Synthetic mode remains available.") }
    }
    private val historyPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val requested = pendingHistoryWindow
        pendingHistoryWindow = null
        if (requested == null) {
            if (::state.isInitialized) update { copy(needsHistoryConsent = false,
                status = "The history permission screen closed, but the earlier date request isn't in memory. Reconnect and ask again. No older records were read.") }
        } else if (HealthReader.historyPermission in granted) loadHealth(requested)
        else if (::state.isInitialized) update { copy(needsHistoryConsent = false,
            status = "Older-history access was not granted. No older records were read. Reconnect for recent records whenever you choose.") }
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingMicrophoneConsent
        pendingMicrophoneConsent = false
        if (granted && requested && foreground) recordVoice()
        else if (::state.isInitialized) update { copy(status = "No microphone access. You can type any supported question.") }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingReminderConsent
        pendingReminderConsent = false
        if (granted && requested) chooseReminderTime()
        else if (::state.isInitialized) update { copy(status = "Notifications were not allowed. Nothing was scheduled.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        health = HealthReader(this)
        memory = PrivateMemory(this)
        recorder = ShortRecorder(this)
        reminders = Reminders(this)
        runtime = FoundryRuntime(this)
        speaker = LocalSpeaker(this)
        val savedStyle = SpeechStyle.values().firstOrNull { style -> style.name == voicePreferences.getString("style", null) } ?: SpeechStyle.WARM
        state = ScreenState(Tab.TALK, true, engine.dashboard(snapshot), currentEvidence(), emptyList(),
            speechStyle = savedStyle, autoSpeak = voicePreferences.getBoolean("auto-speak", true))
        screen = DaylightScreen(this, UiActions(
            onTab = { tab -> update { copy(tab = tab) } },
            onSend = ::ask,
            onMicrophone = ::microphone,
            onSpeak = ::replayReply,
            onStopVoice = ::stopVoice,
            onVoiceSettings = ::showVoiceSettings,
            onConnect = ::connectHealth,
            onRefresh = { if (state.demo) resetDemo() else loadHealth() },
            onDemo = ::resetDemo,
            onPrepareModels = ::confirmModels,
            onSaveCheckIn = ::saveCheckIn,
            onShowCheckIns = ::showCheckIns,
            onReminder = ::confirmReminderPermission,
            onCancelReminder = { reminders.cancel(); update { copy(reminder = reminders.description(), status = "Reminder cancelled.") } },
            onForget = ::confirmForget,
            onOpenHealthSettings = { openHealthSettings() },
            onSyntheticAudio = { runSyntheticVoice() },
            onDebrief = ::confirmDebrief,
            onHistoryAccess = ::requestHistoryAccess
        ))
        setContentView(screen.root)
        ViewCompat.setOnApplyWindowInsetsListener(screen.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        update { copy(reminder = reminders.description()) }
        scope.launch {
            runCatching { speaker.prepare(voicePreferences.getString("voice", null), savedStyle) }
                .onSuccess { available -> updateSpeechState(available) }
                .onFailure { update { copy(voiceLabel = "No installed offline voice ready. You can still type.") } }
        }
        scope.launch {
            runCatching { runtime.states() }.onSuccess { cached ->
                if (foreground) update { copy(modelsReady = cached.all { model -> model.cached },
                    asrReady = cached.any { model -> model.name == FoundryRuntime.AUDIO && model.cached },
                    runtimeStatus = if (cached.all { model -> model.cached }) "Whisper Tiny · optional Qwen recap · CPU" else "Prepare optional models in Privacy") }
            }
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { memory.read(true) } }.onSuccess { items ->
                if (state.demo) { checkIns = items; update { copy(checkInCount = items.size) } }
            }.onFailure { showFailure(it) }
        }
    }

    override fun onStart() { super.onStart(); foreground = true }

    override fun onResume() {
        super.onResume()
        resumed = true
        startDebugWhenReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startDebugWhenReady()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) startDebugWhenReady()
    }

    private fun startDebugWhenReady() {
        if (BuildConfig.DEBUG && foreground && resumed && hasWindowFocus()) {
            DebugBridge.take()?.let { request -> DebugRunner(this).launch(request) }
        }
    }

    override fun onPause() {
        resumed = false
        if (::state.isInitialized && state.voiceStage != VoiceStage.IDLE) stopVoice()
        super.onPause()
    }

    override fun onStop() {
        foreground = false
        recorder.stop()
        speaker.stop()
        work?.cancel()
        workGeneration++
        visibleDialogs.toList().forEach { it.dismiss() }
        if (::state.isInitialized) {
            screen.clearSessionDraft()
            snapshot = SyntheticData.create()
            checkIns = emptyList()
            previousTopic = null
            heartRateContext = null
            lastReply = null
            update {
                copy(demo = true, messages = emptyList(), checkInCount = 0, recording = false, busy = false, voiceStage = VoiceStage.IDLE, voiceLevel = 0f, needsHistoryConsent = false,
                    dashboard = engine.dashboard(snapshot), evidence = currentEvidence(),
                    status = "Private session cleared on leaving the app. Reconnect explicitly to load personal records.")
            }
        }
        scope.launch { runtime.unload() }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        work?.cancel()
        recorder.stop()
        speaker.close()
        CoroutineScope(Dispatchers.IO).launch { runtime.close() }
        scope.cancel()
        super.onDestroy()
    }

    internal fun update(change: ScreenState.() -> ScreenState) {
        val next = state.change()
        if (state.demo != next.demo) screen.clearSessionDraft()
        state = next
        if (state.demo) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        screen.render(state)
    }

    internal fun resetDemo() {
        work?.cancel()
        workGeneration++
        recorder.stop()
        speaker.stop()
        visibleDialogs.toList().forEach { it.dismiss() }
        screen.clearSessionDraft()
        snapshot = SyntheticData.create()
        checkIns = emptyList()
        previousTopic = null
        heartRateContext = null
        pendingHistoryWindow = null
        lastReply = null
        update {
            copy(demo = true, busy = false, recording = false, voiceStage = VoiceStage.IDLE, voiceLevel = 0f, messages = emptyList(), checkInCount = 0, needsHistoryConsent = false,
                dashboard = engine.dashboard(snapshot), evidence = currentEvidence(),
                status = "Synthetic demo records only. No access to your health data.")
        }
    }

    private fun launchWork(status: String, block: suspend () -> Unit) {
        if (state.busy) return
        val generation = ++workGeneration
        update { copy(busy = true, status = status) }
        work = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                if (foreground && generation == workGeneration) update { copy(status = "Stopped. Take your time; tap Talk or type when you're ready.") }
                throw cancelled
            }
            catch (failure: Exception) { showFailure(failure) }
            finally {
                if (generation == workGeneration) update { copy(busy = false, recording = false, voiceStage = VoiceStage.IDLE, voiceLevel = 0f, downloadProgress = null) }
            }
        }
    }

    private fun showOwned(dialog: Dialog) {
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        visibleDialogs.add(dialog)
        dialog.setOnDismissListener { visibleDialogs.remove(dialog) }
        dialog.show()
    }

    private fun AlertDialog.Builder.present() { showOwned(create()) }

    private fun showFailure(failure: Throwable) {
        if (failure is CancellationException) return
        val safeMessage = when (failure) {
            is SecurityException -> "Permission was denied or revoked. No automatic permission change was attempted."
            is IllegalStateException, is IllegalArgumentException -> failure.message ?: "This operation could not complete."
            else -> "Local operation failed (${failure.javaClass.simpleName}). No cloud fallback was used. Try again explicitly."
        }
        update { copy(status = safeMessage.take(350)) }
    }

    private fun ask(question: String) {
        if (state.busy) return
        if (question.isBlank() || question.length > 500) {
            update { copy(status = "Use a non-empty question of at most 500 characters. Nothing was processed.") }
            return
        }
        launchWork("Reading your question locally…") { answer(question) }
    }

    internal fun currentEvidence(facts: List<EvidenceFact> = engine.evidence(snapshot, includeHeartRate = true)): List<EvidenceFact> {
        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z")
        val readAt = snapshot.fetchedAt.atZone(snapshot.zone).format(formatter)
        val newest = snapshot.latestRecordAt?.atZone(snapshot.zone)?.format(formatter) ?: "No available source record"
        return facts + EvidenceFact(
            title = "Source & freshness",
            value = "Newest available record/sample time: $newest",
            basis = "${if (snapshot.synthetic) "Synthetic fixture timestamp" else "Snapshot read at"}: $readAt. A record timestamp is not a live-sync guarantee. " + snapshot.notes.joinToString(" "),
            coverage = "Only categories with available, permitted records. Refresh is explicit; missing records stay missing.",
            source = snapshot.source,
            dates = "Local reference date ${snapshot.today}; time zone ${snapshot.zone.id}"
        )
    }

    internal suspend fun answer(question: String, audioTiming: String = "", voiceTranscript: Boolean = false): Reply {
        require(question.isNotBlank() && question.length <= 500) { "Use a short question of at most 500 characters; no transcript was silently truncated." }
        if (!state.demo && !health.hasPermissions(snapshot.heartRate.status == HeartRateStatus.READY, snapshot.heartRate.requiresHistoryPermission)) {
            resetDemo()
            error("Health permissions changed. Personal context was cleared; reconnect explicitly.")
        }
        var reply = engine.answer(question, snapshot, checkIns, previousTopic, heartRateContext = heartRateContext)
        val requested = reply.heartRateReadRequest
        if (!state.demo && requested != null) {
            update { copy(status = "Reading the requested heart-rate dates locally…") }
            val history = withContext(Dispatchers.IO) { health.readHeartRateWindow(snapshot, requested) }
            currentCoroutineContext().ensureActive()
            check(foreground) { "Return to Ami before reading personal records." }
            val scoped = snapshot.copy(heartRate = history)
            reply = engine.answer(question, scoped, checkIns, previousTopic, heartRateContext = heartRateContext)
            if (history.status == HeartRateStatus.READY) snapshot = scoped
        }
        val provenance = when {
            reply.heartRateContext?.awaitingPeakConfirmation == true -> "Clarification · no language model used"
            reply.needsHistoryConsent -> "History access needed · no language model used"
            reply.topic == Topic.CONVERSATION -> "App-authored conversation · no language model used"
            else -> "Calculated locally · no language model used"
        }
        if (reply.topic != Topic.CONVERSATION) {
            previousTopic = reply.topic
            heartRateContext = reply.heartRateContext
        }
        lastReply = reply
        update {
            copy(tab = Tab.TALK, messages = (messages + TalkMessage(true, question, voiceTranscript = voiceTranscript) + TalkMessage(false, reply.spokenText, provenance, reply.text)).takeLast(30),
                needsHistoryConsent = !demo && reply.needsHistoryConsent,
                evidence = currentEvidence(reply.evidence.ifEmpty { engine.evidence(snapshot, includeHeartRate = true) }), timing = audioTiming,
                status = if (reply.proposal != null) "This is a proposal only. Use the action button and confirm." else "Answer ready. Inspect Evidence for the calculation and coverage.")
        }
        return reply
    }

    private fun confirmDebrief() {
        if (state.busy) return
        if (!state.modelsReady || !engine.debriefCatalog(snapshot).canOrganize) {
            launchWork("Preparing a computed recap…") { createDebrief() }
            return
        }
        AlertDialog.Builder(this).setTitle("Try an experimental local recap?")
            .setMessage("Ami will send calculated weekly averages and observation counts to the Foundry Local companion on this phone. The model can select and order two available fact cards, not write health advice or change numbers. Raw daily records and check-ins are not included. This experimental selection is not medical prioritization or a validated personal recommendation. Rejected output falls back to a labelled computed recap. No cloud inference or action is allowed.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Organize on this phone") { _, _ ->
                launchWork("Organizing calculated fact cards locally…") { createDebrief(allowModel = true) }
            }.present()
    }

    internal suspend fun createDebrief(allowModel: Boolean = false): Pair<DebriefResult, DebriefInference?> {
        if (!state.demo && !health.hasPermissions(snapshot.heartRate.status == HeartRateStatus.READY)) {
            resetDemo()
            error("Health permissions changed. Personal context was cleared; reconnect explicitly.")
        }
        val catalog = engine.debriefCatalog(snapshot)
        var inference: DebriefInference? = null
        var unavailable = false
        if (allowModel && state.modelsReady && catalog.canOrganize) {
            try { inference = runtime.organize(catalog) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable = true }
        }
        if (!state.demo && !health.hasPermissions(snapshot.heartRate.status == HeartRateStatus.READY)) {
            resetDemo()
            error("Health permissions changed during processing. Personal context was cleared.")
        }
        val result = DebriefOrganizer.resolve(catalog, inference?.raw)
        val provenance = when (result.status) {
            DebriefStatus.MODEL_ORGANIZED -> "Experimental Foundry Local card selection • all text calculated by code"
            DebriefStatus.MODEL_OUTPUT_REJECTED -> "Computed fallback • model output rejected"
            else -> if (unavailable) "Computed fallback • local model unavailable" else "Computed recap • no language model used"
        }
        previousTopic = result.reply.topic
        heartRateContext = null
        lastReply = result.reply
        update {
            copy(tab = Tab.TALK, needsHistoryConsent = false, messages = (messages + TalkMessage(true, "Give me a short recorded-week recap.") +
                TalkMessage(false, result.reply.text, provenance)).takeLast(30), evidence = currentEvidence(),
                timing = inference?.let { "Card selection ${it.inferenceMs} ms + load ${it.loadMs} ms" }.orEmpty(),
                status = "$provenance. Inspect Evidence for all categories; nothing was saved or scheduled.")
        }
        return result to inference
    }

    private fun connectHealth() {
        if (state.busy) return
        AlertDialog.Builder(this).setTitle("Read Samsung Health on this phone?")
            .setMessage("Ami initially reads Samsung Health-origin steps, sleep sessions and exercise for the recent two weeks and today. You can additionally allow Heart rate to answer when your highest reading was recorded, using its actual sample time. Clear heart-rate questions can request this month, last month, or the last 30 calendar days; dates beyond the app's conservative recent-history boundary require a separate disclosure and Android history-permission decision. Heart-rate permission is optional and separate from steps or sleep; denying it does not disable those existing features. This is recorded history, not medical assessment or live monitoring. No background access, write access, cloud inference or automatic export. Records remain in this foreground session; intentional check-ins are stored separately. Revoke access in Health Connect. Publication captures use synthetic records.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue to permissions") { _, _ ->
                if (!health.available()) {
                    update { copy(status = "Health Connect is unavailable on this device. Use the synthetic demo.") }
                } else {
                    pendingHealthConsent = true
                    healthPermissions.launch(HealthReader.permissions)
                }
            }.present()
    }

    private fun requestHistoryAccess() {
        if (state.busy || state.demo || !state.needsHistoryConsent) return
        val requested = lastReply?.heartRateContext?.window ?: return
        if (!health.supportsHistoryAccess()) {
            update { copy(needsHistoryConsent = false, status = "Older-history permission isn't available on this phone. No older records were read.") }
            return
        }
        AlertDialog.Builder(this).setTitle("Read older heart-rate history?")
            .setMessage("To answer for ${requested.first} through ${requested.last}, Ami needs to query beyond its conservative recent-30-day boundary. Android's actual default limit depends on when access was first granted; this app doesn't know that date. Optional history permission extends access to older records of already permitted types. Ami will use it only for Samsung Health-origin heart-rate samples in the requested period, with one extra boundary day for overlapping records. Steps, sleep and exercise remain limited to the existing two-week window. No write or background access. Older access doesn't guarantee Samsung Health shared any readings. Android may clear this private chat while you leave for permissions; only these requested dates are held briefly to resume the calculation. Nothing is exported.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue to Android") { _, _ ->
                pendingHistoryWindow = requested
                historyPermissions.launch(setOf(HealthReader.historyPermission))
            }.present()
    }

    private fun loadHealth(resumeHeartRate: HeartRateWindow? = null) {
        launchWork("Reading permissioned Samsung Health records locally…") {
            val loaded = withContext(Dispatchers.IO) { health.read() }
            if (!foreground) return@launchWork
            val notes = withContext(Dispatchers.IO) { memory.read(false) }
            snapshot = loaded
            checkIns = notes
            previousTopic = null
            heartRateContext = null
            lastReply = null
            val dashboard = engine.dashboard(snapshot)
            update {
                copy(demo = false, tab = Tab.TODAY, messages = emptyList(), dashboard = dashboard, needsHistoryConsent = false,
                    evidence = currentEvidence(), checkInCount = checkIns.size,
                    status = "Read completed. ${dashboard.availability.title} No health values written to diagnostic logs.")
            }
            if (resumeHeartRate != null) {
                previousTopic = Topic.HEART_RATE
                heartRateContext = HeartRateContext(resumeHeartRate)
                answer("When was it highest?")
            }
        }
    }

    private fun openHealthSettings() {
        runCatching { startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this)) }
            .onFailure { update { copy(status = "Health Connect could not open its data screen on this device. No settings were changed.") } }
    }

    private fun confirmModels() {
        if (state.busy) return
        AlertDialog.Builder(this).setTitle("Prepare on-device models")
            .setMessage("Whisper Tiny transcribes speech. Qwen2.5 0.5B can optionally organize calculated fact cards; this experimental selection is not a validated recommendation. Qwen is no longer used for normal question routing because its routing test failed. The catalog advertises 822 MB + 131 MB. Downloads require validated unmetered Wi-Fi and this unlocked foreground app. Existing cached packages are reused. No health data is needed for setup. Models are not fine-tuned on you; no cloud inference fallback is configured.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Check cache / prepare") { _, _ -> prepareModels() }.present()
    }

    internal fun prepareModels(onFinished: (() -> Unit)? = null) {
        launchWork("Checking exact model package handles…") {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                runtime.prepare(this) { message, percent -> scope.launch { update { copy(status = message, downloadProgress = percent) } } }
                val cached = runtime.states()
                update { copy(modelsReady = cached.all { it.cached }, asrReady = cached.any { model -> model.name == FoundryRuntime.AUDIO && model.cached }, runtimeStatus = "Whisper Tiny • optional Qwen recap • CPU", status = "Model packages cached. Normal answers use deterministic rules; the optional recap is experimental. No health records accessed.") }
                onFinished?.invoke()
            } finally { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    private fun microphone() {
        if (state.recording) { recorder.stop(); update { copy(status = "Finishing the short recording…") }; return }
        if (state.voiceStage == VoiceStage.SPEAKING || state.voiceStage == VoiceStage.STARTING_SPEECH) {
            val previous = work
            speaker.stop()
            scope.launch {
                previous?.cancelAndJoin()
                if (foreground && resumed) microphone()
            }
            return
        }
        if (state.busy) return
        recorder.unavailableReason()?.let { reason -> update { copy(status = reason) }; return }
        if (!state.asrReady) { update { copy(status = "Prepare the local Whisper model before recording. Typed questions already work.") }; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMicrophoneConsent = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else recordVoice()
    }

    private fun recordVoice() {
        if (!foreground) return
        launchWork("I'm listening. A short pause finishes your turn; tap Done sooner. Maximum 20 seconds.") {
            speaker.stop()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            update { copy(recording = true, tab = Tab.TALK, voiceStage = VoiceStage.LISTENING) }
            var audio: File? = null
            try {
                audio = recorder.capture { level -> scope.launch {
                    if (state.recording) update { copy(voiceLevel = level) }
                } }
                update { copy(recording = false, voiceLevel = 0f, voiceStage = VoiceStage.TRANSCRIBING, status = "Just a moment. Turning your voice into words on this phone…") }
                val transcription = runtime.transcribe(audio)
                audio.delete()
                audio = null
                require(transcription.text.isNotBlank()) { "No speech was recognized. Try again or type your question." }
                update { copy(voiceStage = VoiceStage.PREPARING_REPLY) }
                val reply = answer(transcription.text, "ASR ${transcription.inferenceMs} ms + load ${transcription.loadMs} ms", voiceTranscript = true)
                if (VoiceExperience.shouldSpeakBack(VoiceOrigin.MICROPHONE_GESTURE, state.autoSpeak, foreground && resumed, state.speechReady)) {
                    speakText(reply.spokenText)
                }
            } finally { audio?.delete(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    private fun runSyntheticVoice() {
        if (!state.demo) { update { copy(status = "Switch to synthetic demo mode before running the public audio fixture.") }; return }
        if (!state.asrReady) { update { copy(status = "Prepare the local Whisper model before the synthetic-audio demonstration.") }; return }
        launchWork("Transcribing a labelled synthetic voice clip, not the microphone…") {
            update { copy(voiceStage = VoiceStage.TRANSCRIBING) }
            val audio = File(cacheDir, "synthetic-question.wav")
            try {
                assets.open("weekly-question.wav").use { input -> audio.outputStream().use { input.copyTo(it) } }
                val result = runtime.transcribe(audio)
                val reply = answer(result.text, "Synthetic-file ASR ${result.inferenceMs} ms + load ${result.loadMs} ms")
                if (VoiceExperience.shouldSpeakBack(VoiceOrigin.SAMPLE_PLAY_BUTTON, state.autoSpeak, foreground && resumed, state.speechReady)) {
                    speakText(reply.spokenText)
                }
            } finally { audio.delete() }
        }
    }

    private fun updateSpeechState(available: Boolean = true) {
        val voice = speaker.voices.firstOrNull { candidate -> candidate.name == speaker.voiceName }
        update { copy(speechReady = available, speechStyle = speaker.style,
            voiceLabel = voice?.let { selected -> "${selected.displayLocale} · ${speaker.style.label} · offline voice" }
                ?: "No installed offline English voice. Typed replies still work.") }
    }

    private suspend fun speakText(text: String) {
        currentCoroutineContext().ensureActive()
        check(foreground && resumed) { "Return to Ami to hear the reply." }
        update { copy(voiceStage = VoiceStage.STARTING_SPEECH, status = "Getting my voice ready…") }
        speaker.speak(text) {
            if (foreground && resumed && state.voiceStage == VoiceStage.STARTING_SPEECH) {
                update { copy(voiceStage = VoiceStage.SPEAKING, status = "Speaking. Tap Talk to interrupt, or Stop voice to keep things quiet.") }
            }
        }
        update { copy(voiceStage = VoiceStage.IDLE, status = "Your turn, whenever you're ready. Tap Talk; the microphone is off until then.") }
    }

    private fun replayReply() {
        val reply = lastReply ?: return
        if (!state.speechReady || state.busy) return
        launchWork("Preparing the last reply…") { speakText(reply.spokenText) }
    }

    private fun stopVoice() {
        if (state.voiceStage == VoiceStage.IDLE) return
        recorder.stop()
        speaker.stop()
        work?.cancel()
        update { copy(status = "Stopped. Nothing is listening. Tap Talk when you're ready.") }
    }

    private fun showVoiceSettings() {
        if (state.busy) return
        val name = speaker.voiceName
        if (!state.speechReady || name == null || speaker.voices.isEmpty()) {
            update { copy(status = "No installed offline English voice is ready. No cloud voice will be substituted.") }
            return
        }
        showOwned(VoiceSettingsDialog.create(this, speaker.voices, VoicePreferences(name, speaker.style, state.autoSpeak),
            onApply = { selected ->
                val previous = work
                stopVoice()
                scope.launch {
                    previous?.cancelAndJoin()
                    runCatching {
                        speaker.configure(selected.name, selected.style)
                        voicePreferences.edit().putString("voice", selected.name).putString("style", selected.style.name)
                            .putBoolean("auto-speak", selected.autoSpeak).apply()
                        updateSpeechState()
                        update { copy(autoSpeak = selected.autoSpeak, status = "Voice saved. Only your voice choice and pace are stored, never a recording.") }
                    }.onFailure { showFailure(it) }
                }
            },
            onPreview = { selected ->
                if (!state.busy) launchWork("Previewing a sample sentence, not your records…") {
                    val originalName = speaker.voiceName
                    val originalStyle = speaker.style
                    try {
                        speaker.configure(selected.name, selected.style)
                        speakText(VoiceExperience.PREVIEW)
                    } finally {
                        speaker.stop()
                        if (originalName != null) speaker.configure(originalName, originalStyle)
                    }
                }
            }, onStop = ::stopVoice))
    }

    private fun saveCheckIn() {
        val editor = EditText(this).apply {
            hint = "One thought or routine preference (max 500 characters)"
            maxLines = 5
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        AlertDialog.Builder(this).setTitle(if (state.demo) "Save a demo check-in" else "Save a private check-in")
            .setMessage("Stored encrypted on this phone. This text is context, not permission to perform actions. Use fictional notes in demo mode.")
            .setView(editor).setNegativeButton("Cancel", null).setPositiveButton("Save locally") { _, _ ->
                val demo = state.demo
                launchWork("Saving your intentional check-in locally…") {
                    val saved = withContext(Dispatchers.IO) { memory.add(demo, editor.text.toString()) }
                    if (state.demo == demo) { checkIns = saved; update { copy(checkInCount = saved.size, status = "Check-in saved locally. Nothing was sent to a training service.") } }
                }
            }.present()
    }

    private fun showCheckIns() {
        val demo = state.demo
        launchWork("Opening local memory…") {
            checkIns = withContext(Dispatchers.IO) { memory.read(demo) }
            update { copy(checkInCount = checkIns.size) }
            AlertDialog.Builder(this@MainActivity).setTitle(if (demo) "Demo check-ins" else "Private check-ins")
                .setMessage(checkIns.joinToString("\n\n") { "${it.createdAt}\n${it.text}" }.ifEmpty { "No intentional check-ins saved." })
                .setPositiveButton("Close", null).present()
        }
    }

    private fun confirmForget() {
        val demo = state.demo
        AlertDialog.Builder(this).setTitle("Delete Ami's ${if (demo) "demo" else "personal"} memory?")
            .setMessage("Deletes check-ins, conversation and the in-memory snapshot, and cancels the local reminder. This does not delete Samsung Health or Health Connect originals and does not revoke Android permissions.")
            .setNegativeButton("Keep", null).setPositiveButton("Delete app memory") { _, _ ->
                launchWork("Deleting only Ami app memory…") {
                    withContext(Dispatchers.IO) { memory.clear(demo) }
                    reminders.cancel()
                    screen.clearSessionDraft()
                    checkIns = emptyList()
                    previousTopic = null
                    heartRateContext = null
                    lastReply = null
                    snapshot = SyntheticData.create()
                    update { copy(demo = true, messages = emptyList(), checkInCount = 0, dashboard = engine.dashboard(snapshot), evidence = currentEvidence(), reminder = reminders.description(), status = "Selected app memory deleted. Original tracker records were not changed.") }
                }
            }.present()
    }

    private fun confirmReminderPermission() {
        if (state.busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminderConsent = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else chooseReminderTime()
    }

    private fun chooseReminderTime() {
        TimePickerDialog(this, { _, hour, minute ->
            AlertDialog.Builder(this).setTitle("Confirm routine reminder")
                .setMessage("Schedule one reminder around %02d:%02d? Android may delay delivery. It contains no health data and never repeats automatically.".format(hour, minute))
                .setNegativeButton("Cancel", null).setPositiveButton("Schedule") { _, _ ->
                    runCatching { reminders.schedule(hour, minute) }
                        .onSuccess { label -> update { copy(reminder = label, status = "One local reminder requested with your confirmation.") } }
                        .onFailure { showFailure(it) }
                }.present()
        }, 21, 0, true).also(::showOwned)
    }
}
