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

        createReminderChannel(context)

        val taskId = intent.getLongExtra("taskId", System.currentTimeMillis())
        val title = intent.getStringExtra("title") ?: "Bloom task"
        val dueTime = intent.getStringExtra("dueTime") ?: "soon"
        val reminderMinutes = intent.getIntExtra("reminderMinutes", 0)

        val launchIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = when (reminderMinutes) {
            0 -> "It’s time: $title"
            15 -> "Coming up in 15 minutes • $dueTime"
            30 -> "Coming up in 30 minutes • $dueTime"
            60 -> "Coming up in 1 hour • $dueTime"
            else -> "Coming up soon • $dueTime"
        }

        val notification = NotificationCompat.Builder(context, "bloom_reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }
}
