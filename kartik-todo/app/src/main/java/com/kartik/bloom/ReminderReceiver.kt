package com.kynurelabs.bloom

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
        if (intent.action == ACTION_SNOOZE) {
            val taskId = intent.getLongExtra("taskId", -1L)
            val minutes = intent.getLongExtra("minutes", 10L)
            val task = loadTasks(context).firstOrNull { it.id == taskId } ?: return
            snoozeTask(context, task, minutes)
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            return
        }

        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createReminderChannels(context)

        val taskId = intent.getLongExtra("taskId", System.currentTimeMillis())
        val title = intent.getStringExtra("title") ?: "Bloom task"
        val dueTime = intent.getStringExtra("dueTime") ?: "soon"
        val reminderMinutes = intent.getIntExtra("reminderMinutes", 0)
        val notes = intent.getStringExtra("notes") ?: ""
        val recurrence = runCatching { Recurrence.valueOf(intent.getStringExtra("recurrence") ?: "NONE") }.getOrDefault(Recurrence.NONE)
        val occurrenceDate = runCatching { LocalDate.parse(intent.getStringExtra("occurrenceDate")) }.getOrDefault(LocalDate.now())
        val task = loadTasks(context).firstOrNull { it.id == taskId }
        val prefs = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString("reminder_mode", "notification") ?: "notification"

        val timingText = when (reminderMinutes) {
            0 -> "It’s time for this task"
            15 -> "Starts in 15 minutes • $dueTime"
            30 -> "Starts in 30 minutes • $dueTime"
            60 -> "Starts in 1 hour • $dueTime"
            else -> "Coming up • $dueTime"
        }
        val recurrenceText = task?.let { if (it.recurrence == Recurrence.NONE) "" else " • ${recurrenceLabel(it)}" } ?: ""
        val detailText = task?.let { " • ${it.priority.label} • ${it.estimatedMinutes}m" } ?: ""
        val body = timingText + recurrenceText + detailText + if (notes.isNotBlank()) "\n$notes" else ""

        val launch = PendingIntent.getActivity(context, taskId.hashCode(), Intent(context, MainActivity::class.java).putExtra("open_task", taskId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, if (mode == "alarm") ensureAlarmChannel(context) else "bloom_reminders")
            .setSmallIcon(if (mode == "alarm") android.R.drawable.ic_lock_idle_alarm else android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(timingText).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (mode == "alarm") NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (mode == "alarm") NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(launch)

        if (mode == "alarm") {
            builder.addAction(0, "10 min", snoozeIntent(context, taskId, 10, 1))
                .addAction(0, "30 min", snoozeIntent(context, taskId, 30, 2))
                .addAction(0, "1 hour", snoozeIntent(context, taskId, 60, 3))
        }
        NotificationManagerCompat.from(context).notify(taskId.hashCode(), builder.build())
        addHistory(context, HistoryEvent(taskId = taskId, title = title, action = "Reminder fired", detail = timingText))

        if (recurrence != Recurrence.NONE && task != null) scheduleNextRecurringReminder(context, task, occurrenceDate)
    }

    private fun snoozeIntent(context: Context, taskId: Long, minutes: Long, salt: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("taskId", taskId)
            putExtra("minutes", minutes)
        }
        return PendingIntent.getBroadcast(context, taskId.hashCode() xor (9000 + salt), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object { const val ACTION_SNOOZE = "com.kynurelabs.bloom.SNOOZE" }
}
