package com.samruddhi.bloom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
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

            val savedSound = prefs.getString("alarm_uri", "") ?: ""
            val soundUri: Uri = if (savedSound.isBlank()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                Uri.parse(savedSound)
            }
            val channelId = ensureAlarmChannel(context, soundUri)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
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

    private fun ensureAlarmChannel(context: Context, soundUri: Uri): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "bloom_alarms"

        val channelId = "bloom_alarm_${soundUri.toString().hashCode()}"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                channelId,
                "Bloom alarm reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible alarm-style task reminders"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }
}
