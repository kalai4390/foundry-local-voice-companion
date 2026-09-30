package dev.daylight.local

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Reminders(private val context: Context) {
    companion object {
        const val CHANNEL = "daylight_local_reminders"
        const val REQUEST = 4001
    }

    private val preferences = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun notificationsAllowed(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun pending(flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReminderReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(hour: Int, minute: Int): String {
        check(notificationsAllowed()) { "Notifications are disabled; no reminder was scheduled." }
        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.set(AlarmManager.RTC_WAKEUP, target.toInstant().toEpochMilli(), requireNotNull(pending()))
        preferences.edit().putLong("when", target.toInstant().toEpochMilli())
            .putString("label", target.format(DateTimeFormatter.ofPattern("EEE HH:mm"))).apply()
        return description()
    }

    fun description(): String {
        if (preferences.getLong("when", 0) <= System.currentTimeMillis() || pending(PendingIntent.FLAG_NO_CREATE) == null) {
            return "No reminder scheduled"
        }
        return "Routine reminder: ${preferences.getString("label", "")} • approximate delivery"
    }

    fun cancel() {
        pending(PendingIntent.FLAG_NO_CREATE)?.let { alarm ->
            context.getSystemService(AlarmManager::class.java).cancel(alarm)
            alarm.cancel()
        }
        preferences.edit().clear().apply()
        NotificationManagerCompat.from(context).cancel(REQUEST)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Reminders.CHANNEL, "Private routine reminders", NotificationManager.IMPORTANCE_DEFAULT))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(dev.daylight.local.R.drawable.ic_daylight)
            .setContentTitle("A moment for your routine")
            .setContentText("Open Ami when it suits you. No health details are shown here.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(Reminders.REQUEST, notification)
        context.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
