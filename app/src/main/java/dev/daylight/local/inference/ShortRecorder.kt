package dev.daylight.local.inference

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dev.daylight.core.SpeechEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class ShortRecorder(private val context: Context) {
    private val recording = AtomicBoolean(false)
    private val audioReadiness = AudioReadiness(context)

    fun stop() { recording.set(false) }

    fun unavailableReason(): String? = audioReadiness.unavailableReason()

    @SuppressLint("MissingPermission")
    suspend fun capture(onLevel: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        audioReadiness.requireAvailable()
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission was not granted."
        }
        check(recording.compareAndSet(false, true)) { "A recording is already active." }
        val target = File.createTempFile("utterance-", ".wav", context.cacheDir)
        var completed = false
        var device: AudioRecord? = null
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "16 kHz mono recording is unavailable on this device." }
            device = AudioRecord(
                MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400)
            )
            check(device.state == AudioRecord.STATE_INITIALIZED) { "The microphone could not initialize." }
            device.startRecording()
            val buffer = ByteArray(3200)
            val endpoint = SpeechEndpoint()
            var count = 0
            RandomAccessFile(target, "rw").use { file ->
                file.setLength(0)
                file.write(ByteArray(44))
                while (recording.get() && count < 16000 * 2 * SpeechEndpoint.MAX_SECONDS) {
                    currentCoroutineContext().ensureActive()
                    audioReadiness.requireAvailable()
                    val read = device.read(buffer, 0, minOf(buffer.size, 16000 * 2 * SpeechEndpoint.MAX_SECONDS - count))
                    check(read > 0) { "Microphone capture failed." }
                    file.write(buffer, 0, read)
                    count += read
                    val finished = endpoint.accept(buffer, read)
                    onLevel(endpoint.level)
                    if (finished) break
                }
                currentCoroutineContext().ensureActive()
                require(count >= 3200 && endpoint.hasVoiceEnergy) { "I didn't catch enough sound. Tap Talk and try a little closer to the microphone, or type instead." }
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                    .put("RIFF".toByteArray()).putInt(count + 36).put("WAVEfmt ".toByteArray())
                    .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
                    .putShort(2).putShort(16).put("data".toByteArray()).putInt(count).array()
                file.seek(0)
                file.write(header)
            }
            completed = true
            target
        } finally {
            recording.set(false)
            device?.let { current ->
                runCatching { current.stop() }
                current.release()
            }
            if (!completed) target.delete()
        }
    }
}
