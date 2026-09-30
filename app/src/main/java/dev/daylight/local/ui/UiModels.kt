package dev.daylight.local.ui

import dev.daylight.core.Dashboard
import dev.daylight.core.EvidenceFact
import dev.daylight.core.SpeechStyle
import dev.daylight.core.VoiceStage

enum class Tab { TODAY, TALK, EVIDENCE, PRIVACY }

data class TalkMessage(val user: Boolean, val text: String, val provenance: String = "", val details: String = "", val voiceTranscript: Boolean = false)

data class ScreenState(
    val tab: Tab,
    val demo: Boolean,
    val dashboard: Dashboard,
    val evidence: List<EvidenceFact>,
    val messages: List<TalkMessage>,
    val status: String = "Synthetic records. No access to your health data.",
    val runtimeStatus: String = "Models not prepared",
    val busy: Boolean = false,
    val recording: Boolean = false,
    val modelsReady: Boolean = false,
    val asrReady: Boolean = false,
    val speechReady: Boolean = false,
    val voiceStage: VoiceStage = VoiceStage.IDLE,
    val voiceLevel: Float = 0f,
    val voiceLabel: String = "Looking for an installed offline voice",
    val speechStyle: SpeechStyle = SpeechStyle.WARM,
    val autoSpeak: Boolean = true,
    val checkInCount: Int = 0,
    val reminder: String = "No reminder scheduled",
    val timing: String = "",
    val downloadProgress: Int? = null,
    val needsHistoryConsent: Boolean = false
)

data class UiActions(
    val onTab: (Tab) -> Unit,
    val onSend: (String) -> Unit,
    val onMicrophone: () -> Unit,
    val onSpeak: () -> Unit,
    val onStopVoice: () -> Unit,
    val onVoiceSettings: () -> Unit,
    val onConnect: () -> Unit,
    val onRefresh: () -> Unit,
    val onDemo: () -> Unit,
    val onPrepareModels: () -> Unit,
    val onSaveCheckIn: () -> Unit,
    val onShowCheckIns: () -> Unit,
    val onReminder: () -> Unit,
    val onCancelReminder: () -> Unit,
    val onForget: () -> Unit,
    val onOpenHealthSettings: () -> Unit,
    val onSyntheticAudio: () -> Unit,
    val onDebrief: () -> Unit,
    val onHistoryAccess: () -> Unit
)
