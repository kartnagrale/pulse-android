package com.samruddhi.bloom

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createReminderChannels(context)

        val taskId = intent.getLongExtra("taskId", System.currentTimeMillis())
        val title = intent.getStringExtra("title") ?: "Bloom task"
        val dueTime = intent.getStringExtra("dueTime") ?: "soon"
        val reminderMinutes = intent.getIntExtra("reminderMinutes", 0)
        val prefs = context.getSharedPreferences("bloom_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("reminder_mode", "notification") ?: "notification"

        val body = when (reminderMinutes) {
            0 -> "It’s time for this task"
            15 -> "Starts in 15 minutes • $dueTime"
            30 -> "Starts in 30 minutes • $dueTime"
            60 -> "Starts in 1 hour • $dueTime"
            else -> "Coming up soon • $dueTime"
        }

        val launchMain = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (mode == "alarm") {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("taskId", taskId)
                putExtra("title", title)
                putExtra("body", body)
            }
            val alarmScreen = PendingIntent.getActivity(
                context,
                (taskId xor 0x5A5A).hashCode(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "bloom_alarms")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(alarmScreen)
                .build()

            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        } else {
            val notification = NotificationCompat.Builder(context, "bloom_reminders")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(launchMain)
                .build()

            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }
    }
}
