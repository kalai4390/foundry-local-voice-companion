package dev.daylight.local.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.daylight.core.CheckIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PrivateMemory(private val context: Context, private val testOnly: Boolean = false) {
    private fun alias(demo: Boolean): String {
        require(!testOnly || demo) { "Synthetic tests cannot select personal memory." }
        return if (testOnly) "daylight_synthetic_test" else if (demo) "daylight_demo_v1" else "daylight_personal_v1"
    }
    private fun store(demo: Boolean) = AtomicFile(File(context.noBackupFilesDir, "${alias(demo)}.enc"))
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun read(demo: Boolean): List<CheckIn> {
        val file = store(demo)
        if (!file.baseFile.exists()) return emptyList()
        val bytes = file.readFully()
        require(bytes.size in 31..100000 && bytes[0].toInt() == 1) { "Unsupported memory format." }
        val ivSize = bytes[1].toInt()
        require(ivSize == 12 && bytes.size > 2 + ivSize + 16) { "Invalid encrypted memory." }
        val entry = keyStore().getEntry(alias(demo), null) as? KeyStore.SecretKeyEntry
            ?: error("The local encryption key is unavailable; memory was not replaced.")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(128, bytes.copyOfRange(2, 2 + ivSize)))
        }
        val payload = cipher.doFinal(bytes.copyOfRange(2 + ivSize, bytes.size)).toString(Charsets.UTF_8)
        val array = JSONArray(payload)
        require(array.length() <= 50) { "Memory limit exceeded." }
        return (0 until array.length()).map { position ->
            val item = array.getJSONObject(position)
            CheckIn(item.getString("id"), item.getString("text"), Instant.parse(item.getString("createdAt")))
        }
    }

    fun add(demo: Boolean, text: String): List<CheckIn> {
        require(text.trim().length in 1..500) { "Use between 1 and 500 characters for a check-in." }
        val existing = read(demo)
        require(existing.size < 50) { "Local memory holds at most 50 check-ins. Explicitly clear app memory before adding more; no old note was discarded." }
        val items = existing + CheckIn(UUID.randomUUID().toString(), text.trim(), Instant.now())
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("id", item.id).put("text", item.text).put("createdAt", item.createdAt.toString()))
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(demo)) }
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val output = byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + encrypted
        val file = store(demo)
        val stream = file.startWrite()
        try {
            stream.write(output)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        return items
    }

    fun clear(demo: Boolean) {
        store(demo).delete()
        keyStore().deleteEntry(alias(demo))
    }

    private fun key(demo: Boolean): SecretKey {
        val existing = keyStore().getEntry(alias(demo), null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias(demo), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
}
