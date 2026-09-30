package dev.daylight.local

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            textSize = 17f
            setPadding(36, 64, 36, 48)
            text = """
                Ami • Privacy & data use

                Ami is a nonclinical, on-device reference application. It is not a medical device, diagnostic tool, or emergency service.

                Health Connect access is optional and read-only. After you select Connect Samsung Health and grant permissions, Ami reads Samsung Health step totals, sleep-session intervals, and exercise sessions for approximately the last 14 completed days and today. A separate optional Heart rate permission adds timestamped heart-rate samples for that period. Denying Heart rate leaves the existing steps, sleep and exercise features available. It does not request medical records, contacts, location, or background health access.

                Heart-rate questions can additionally request this calendar month, the previous calendar month, or the last 30 calendar days. Only the requested heart-rate range and one boundary day are queried; other metrics retain their existing windows. Beyond the app's conservative recent-30-day read boundary, a separate disclosure and Android history-permission prompt are required. Health Connect's default historical limit depends on the first permission-grant date; Ami does not know that date. Older-history access extends access to already permitted data types, but Ami uses it only for bounded heart-rate reads. Permission does not guarantee that Samsung Health has shared records. Only the requested dates, never health values or the raw transcript, survive the permission-screen transition to resume the answer.

                Records are used for local statistics and evidence-backed conversation. A missing record is not zero activity. Sleep-session duration is not necessarily time asleep. Heart-rate answers report the highest sampled beats per minute and its actual timestamp, not whether it was medically high. Calendar weeks start Monday; heart-rate answers state their dates, time zone, snapshot cutoff and sample coverage. An unread month is not treated as an empty month, and a shorter snapshot is never used to claim a full-month peak. Unclear requests ask for clarification before reading or calculating. No diagnosis, medical causation or continuous-monitoring claim is inferred.

                Health snapshots and conversations remain in memory and are cleared when the app leaves the foreground. Intentional check-ins are stored separately in encrypted app storage with Android backup disabled. Delete app memory removes Ami check-ins and conversation, not Samsung Health or Health Connect originals. Manage or revoke Health Connect permissions in Android Settings.

                Foundry Local runs speech transcription through the separately installed Microsoft service. Normal questions use deterministic routing and calculated answers, not language-model health reasoning. A separate experimental recap asks for confirmation before sending calculated weekly averages and observation counts to that same on-phone service. It does not include raw daily records, source metadata, or check-in text. The model can select two offered fact identifiers; all displayed wording, values and evidence are constructed by the app. Invalid output falls back to a labelled computed recap. Models require an initial download. SDK telemetry is disabled by this app. No cloud inference, advertising, analytics, or health-data export endpoint is configured. This is an application design boundary, not a claim that every process or tracker service on the phone is offline.

                Samsung Health and your wearable may have independent account and cloud-sync settings. Ami neither controls nor changes them.

                Microphone access occurs only after you press Record and grant Android permission. Capture is limited to a short utterance and the temporary recording is deleted after processing or cancellation. No background or always-listening recording occurs. Speech output uses only a selected installed voice that reports no network requirement; if unavailable, typed interaction remains available.

                Reminders are opt-in, one-shot, inexact Android alarms. Delivery may be delayed by Android and requires notification permission. Notifications contain no health statistics or check-in text. No action is scheduled from a model response without confirmation.

                Synthetic demo data and demo check-ins are separate from personal data. Debug evidence and captures are restricted to synthetic mode. Do not publish personal health screenshots or recordings.

                This local prototype has no account, backend, data broker, or support data-upload service. Before distributing a production app, replace this prototype notice with an appropriate reviewed privacy policy and complete platform declarations.
            """.trimIndent()
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
