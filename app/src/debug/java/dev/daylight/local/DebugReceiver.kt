package dev.daylight.local

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command").orEmpty()
        val identifier = intent.getStringExtra("run_id").orEmpty()
        val screen = intent.getStringExtra("screen") ?: "TODAY"
        if (!BuildConfig.DEBUG || intent.action != "dev.daylight.local.RUN_SYNTHETIC_TEST" ||
            command !in setOf("smoke", "catalog", "provision", "routing", "audio", "debrief", "voice", "capture", "welcome") ||
            screen !in setOf("TODAY", "TALK", "EVIDENCE", "PRIVACY") ||
            !identifier.matches(Regex("[A-Za-z0-9_-]{1,100}"))) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "Invalid synthetic test request"
            return
        }
        DebugBridge.put(DebugRequest(command, identifier, screen, intent.getBooleanExtra("allow_download", false)))
        resultCode = Activity.RESULT_OK
        resultData = "queued:$identifier"
    }
}
