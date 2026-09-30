package dev.daylight.local.ui

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Spinner
import dev.daylight.core.OfflineVoice
import dev.daylight.core.SpeechStyle

data class VoicePreferences(val name: String, val style: SpeechStyle, val autoSpeak: Boolean)

object VoiceSettingsDialog {
    fun create(
        context: Context,
        voices: List<OfflineVoice>,
        selected: VoicePreferences,
        onApply: (VoicePreferences) -> Unit,
        onPreview: (VoicePreferences) -> Unit,
        onStop: () -> Unit
    ): AlertDialog {
        val ui = DaylightUi(context)
        val column = ui.column().apply { setPadding(ui.dp(22), ui.dp(12), ui.dp(22), ui.dp(10)) }
        ui.add(column, ui.text("Find a voice that feels right. These are installed English voices marked offline by your phone.", 15f, DaylightColors.secondary))
        val labels = voices.mapIndexed { index, voice -> "${voice.displayLocale} · ${index + 1}" }
        val voicePicker = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(voices.indexOfFirst { voice -> voice.name == selected.name }.coerceAtLeast(0))
            contentDescription = "Installed offline voice"
            minimumHeight = ui.dp(56)
        }
        ui.add(column, ui.eyebrow("VOICE"), top = 20)
        ui.add(column, voicePicker, top = 6)
        val metadata = ui.text("", 12f, DaylightColors.secondary)
        voicePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, identifier: Long) {
                metadata.showText("Voice ID: ${voices[position].name}. Voice names and quality scores do not guarantee how natural it sounds; use Preview.")
            }
        }
        ui.add(column, metadata, top = 6)
        val styles = SpeechStyle.values().toList()
        val stylePicker = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, styles.map { style -> style.label })
            setSelection(styles.indexOf(selected.style))
            contentDescription = "Speaking pace"
            minimumHeight = ui.dp(56)
        }
        ui.add(column, ui.eyebrow("PACE"), top = 18)
        ui.add(column, stylePicker, top = 6)
        val automatic = CheckBox(context).apply {
            text = "Reply aloud after I speak"
            isChecked = selected.autoSpeak
            minimumHeight = ui.dp(56)
            setTextColor(DaylightColors.text)
        }
        ui.add(column, automatic, top = 12)
        ui.add(column, ui.text("No always-on listening. Typed questions stay quiet. Preview reads a sample sentence, never your health records.", 13f, DaylightColors.secondary), top = 6)
        fun draft() = VoicePreferences(voices[voicePicker.selectedItemPosition].name, styles[stylePicker.selectedItemPosition], automatic.isChecked)
        val playback = ui.flow()
        playback.addView(ui.button("Preview voice") { onPreview(draft()) })
        playback.addView(ui.button("Stop preview", danger = true) { onStop() })
        ui.add(column, playback, top = 16)
        return AlertDialog.Builder(context).setTitle("Make this voice yours")
            .setView(ScrollView(context).apply { addView(column) })
            .setNegativeButton("Close") { _, _ -> onStop() }
            .setPositiveButton("Use this voice") { _, _ -> onApply(draft()) }
            .create()
    }
}
