package com.kartik.bloom

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatTaskTime(time: String): String = runCatching {
    LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(time)

fun createReminderChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel("bloom_reminders", "Bloom reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Task reminders from Bloom"
        }
    )
    ensureAlarmChannel(context)
}

fun alarmChannelId(context: Context): String {
    val prefs = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
    val saved = prefs.getString("alarm_uri", "") ?: ""
    return "bloom_alarm_${saved.hashCode()}"
}

fun ensureAlarmChannel(context: Context): String {
    val channelId = alarmChannelId(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId

    val prefs = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
    val saved = prefs.getString("alarm_uri", "") ?: ""
    val soundUri: Uri = if (saved.isBlank()) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    } else Uri.parse(saved)

    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(channelId, "Bloom alarm reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Loud task reminders from Bloom"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(soundUri, attributes)
        }
    )
    return channelId
}

fun scheduleTaskReminder(context: Context, task: BloomTask, occurrenceDate: LocalDate? = null) {
    if (task.done && task.recurrence == Recurrence.NONE) return
    val originalDate = occurrenceDate ?: runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return
    val time = runCatching { LocalTime.parse(task.time) }.getOrNull() ?: return

    var date = originalDate
    var trigger = LocalDateTime.of(date, time).minusMinutes(task.reminderMinutes.toLong())
    val now = LocalDateTime.now()

    if (trigger <= now) {
        if (task.recurrence == Recurrence.NONE) return
        do {
            date = nextOccurrence(date, task.recurrence)
            trigger = LocalDateTime.of(date, time).minusMinutes(task.reminderMinutes.toLong())
        } while (trigger <= now)
    }

    val triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("taskId", task.id)
        putExtra("title", task.title)
        putExtra("dueTime", formatTaskTime(task.time))
        putExtra("reminderMinutes", task.reminderMinutes)
        putExtra("recurrence", task.recurrence.name)
        putExtra("occurrenceDate", date.toString())
    }
    val pending = PendingIntent.getBroadcast(
        context,
        task.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    } else {
        manager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
    }
}

fun scheduleNextRecurringReminder(context: Context, task: BloomTask, firedDate: LocalDate) {
    if (task.recurrence == Recurrence.NONE) return
    scheduleTaskReminder(context, task, nextOccurrence(firedDate, task.recurrence))
}

fun cancelTaskReminder(context: Context, taskId: Long) {
    val pending = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) ?: return
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    pending.cancel()
}
