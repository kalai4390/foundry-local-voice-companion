package dev.daylight.local.inference

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.daylight.core.OfflineVoice
import dev.daylight.core.SpeechStyle
import dev.daylight.core.VoiceExperience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSpeaker(context: Context) {
    private data class Utterance(val completion: CompletableDeferred<Unit>, val onStarted: () -> Unit)

    private val initialized = CompletableDeferred<Boolean>()
    private val requests = ConcurrentHashMap<String, Utterance>()
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioReadiness = AudioReadiness(context)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus: AudioFocusRequest? = null
    private var focusToken: String? = null
    private val engine = TextToSpeech(context.applicationContext) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }

    var voiceName: String? = null
        private set
    var style: SpeechStyle = SpeechStyle.WARM
        private set
    var voices: List<OfflineVoice> = emptyList()
        private set
    val engineName: String? get() = engine.defaultEngine

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null) handler.post { requests[utteranceId]?.onStarted?.invoke() }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) requests.remove(utteranceId)?.completion?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null) requests.remove(utteranceId)?.completion?.completeExceptionally(
                    IllegalStateException("This offline voice could not speak. Try another installed voice; the reply is still on screen.")
                )
            }

            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != null) requests.remove(utteranceId)?.completion?.cancel()
            }
        })
    }

    suspend fun prepare(preferredName: String? = voiceName, speechStyle: SpeechStyle = style): Boolean = mutex.withLock {
        if (!withTimeout(10000) { initialized.await() }) return@withLock false
        voices = VoiceExperience.available(engine.voices.orEmpty().map { voice ->
            OfflineVoice(voice.name, voice.locale.toLanguageTag(), voice.quality, voice.latency,
                voice.isNetworkConnectionRequired, !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
        }, Locale.getDefault())
        val selected = VoiceExperience.select(voices, preferredName, Locale.getDefault()) ?: return@withLock false
        configure(selected.name, speechStyle)
        true
    }

    fun configure(name: String, speechStyle: SpeechStyle) {
        check(requests.isEmpty()) { "Stop the current voice before changing it." }
        check(voices.any { voice -> voice.name == name }) { "This installed offline English voice is unavailable." }
        val voice = engine.voices.orEmpty().firstOrNull { candidate ->
            candidate.name == name && !candidate.isNetworkConnectionRequired && candidate.locale.language == "en" &&
                !candidate.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        } ?: error("The selected offline voice is no longer installed.")
        check(engine.setVoice(voice) == TextToSpeech.SUCCESS &&
            engine.setSpeechRate(speechStyle.rate) == TextToSpeech.SUCCESS &&
            engine.setPitch(speechStyle.pitch) == TextToSpeech.SUCCESS &&
            engine.setAudioAttributes(attributes) == TextToSpeech.SUCCESS) { "This voice could not use the selected pace." }
        voiceName = voice.name
        style = speechStyle
    }

    private fun verified(text: String) {
        val current = engine.voice
        check(voiceName != null && current != null && current.name == voiceName && !current.isNetworkConnectionRequired &&
            !current.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
            "An installed offline English voice is unavailable. Typed replies still work."
        }
        require(text.isNotBlank() && text.length <= TextToSpeech.getMaxSpeechInputLength()) { "This reply cannot be spoken by the local voice." }
    }

    suspend fun speak(text: String, onStarted: () -> Unit = {}) = mutex.withLock {
        audioReadiness.requireAvailable()
        verified(text)
        val focusIdentifier = UUID.randomUUID().toString()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (focusToken == focusIdentifier && change < AudioManager.AUDIOFOCUS_GAIN) {
                    stopWithFailure("Speech stopped because another app needs audio. Tap Listen again when you're ready.")
                }
            }, handler).build()
        focusToken = focusIdentifier
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusToken = null
            error("Audio is busy in another app. The reply is on screen; try Listen again later.")
        }
        focus = request
        val identifier = UUID.randomUUID().toString()
        val utterance = Utterance(CompletableDeferred(), onStarted)
        requests[identifier] = utterance
        try {
            check(engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, identifier) == TextToSpeech.SUCCESS) { "Local speech could not start." }
            withTimeout(120000) { utterance.completion.await() }
        } finally {
            requests.remove(identifier)
            if (!utterance.completion.isCompleted || utterance.completion.isCancelled) engine.stop()
            releaseFocus()
        }
    }

    suspend fun synthesizeToFile(text: String, file: File) = mutex.withLock {
        verified(text)
        val identifier = UUID.randomUUID().toString()
        val utterance = Utterance(CompletableDeferred()) {}
        requests[identifier] = utterance
        try {
            check(engine.synthesizeToFile(text, null, file, identifier) == TextToSpeech.SUCCESS) { "Local synthesis could not start." }
            withTimeout(30000) { utterance.completion.await() }
            check(file.isFile && file.length() > 44) { "Local synthesis produced no audio." }
        } finally {
            requests.remove(identifier)
            if (!utterance.completion.isCompleted || utterance.completion.isCancelled) engine.stop()
        }
    }

    private fun releaseFocus() {
        val previous = focus
        focus = null
        focusToken = null
        previous?.let { request -> audioManager.abandonAudioFocusRequest(request) }
    }

    private fun stopWithFailure(message: String) {
        requests.values.forEach { utterance -> utterance.completion.completeExceptionally(IllegalStateException(message)) }
        requests.clear()
        engine.stop()
        releaseFocus()
    }

    fun stop() {
        requests.values.forEach { utterance -> utterance.completion.cancel(CancellationException("Speech stopped.")) }
        requests.clear()
        engine.stop()
        releaseFocus()
    }

    fun close() { stop(); engine.shutdown() }
}
