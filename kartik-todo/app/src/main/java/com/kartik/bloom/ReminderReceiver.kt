package com.kartik.bloom

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createReminderChannels(context)

        val taskId = intent.getLongExtra("taskId", System.currentTimeMillis())
        val title = intent.getStringExtra("title") ?: "Bloom task"
        val dueTime = intent.getStringExtra("dueTime") ?: "soon"
        val reminderMinutes = intent.getIntExtra("reminderMinutes", 0)
        val recurrence = runCatching {
            Recurrence.valueOf(intent.getStringExtra("recurrence") ?: "NONE")
        }.getOrDefault(Recurrence.NONE)
        val occurrenceDate = runCatching {
            LocalDate.parse(intent.getStringExtra("occurrenceDate"))
        }.getOrDefault(LocalDate.now())

        val prefs = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString("reminder_mode", "notification") ?: "notification"

        val timingText = when (reminderMinutes) {
            0 -> "It’s time for this task"
            15 -> "Starts in 15 minutes • $dueTime"
            30 -> "Starts in 30 minutes • $dueTime"
            60 -> "Starts in 1 hour • $dueTime"
            else -> "Coming up soon • $dueTime"
        }
        val body = if (recurrence == Recurrence.NONE) timingText
        else "$timingText • ${recurrence.label()}"

        val launchIntent = Intent(context, MainActivity::class.java)
        val launchPending = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (mode == "alarm") ensureAlarmChannel(context) else "bloom_reminders"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (mode == "alarm") android.R.drawable.ic_lock_idle_alarm else android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (mode == "alarm") NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (mode == "alarm") NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(launchPending)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)

        if (recurrence != Recurrence.NONE) {
            val current = loadTasks(context).firstOrNull { it.id == taskId }
            if (current != null && current.recurrence != Recurrence.NONE) {
                scheduleNextRecurringReminder(context, current, occurrenceDate)
            }
        }
    }
}
