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
    manager.createNotificationChannel(NotificationChannel("bloom_reminders", "Bloom reminders", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Task reminders from Bloom"
    })
    ensureAlarmChannel(context)
}

fun alarmChannelId(context: Context): String {
    val saved = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE).getString("alarm_uri", "") ?: ""
    return "bloom_alarm_${saved.hashCode()}"
}

fun ensureAlarmChannel(context: Context): String {
    val channelId = alarmChannelId(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
    val prefs = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
    val saved = prefs.getString("alarm_uri", "") ?: ""
    val soundUri: Uri = if (saved.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(saved)
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(channelId, "Bloom alarm reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Loud task reminders from Bloom"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(soundUri, attributes)
        })
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
            val next = nextOccurrence(date, task)
            if (next == date) return
            date = next
            trigger = LocalDateTime.of(date, time).minusMinutes(task.reminderMinutes.toLong())
        } while (trigger <= now)
    }
    val end = runCatching { LocalDate.parse(task.repeatEnd) }.getOrNull()
    if (end != null && date.isAfter(end)) return
    scheduleAt(context, task, trigger, date, task.id.hashCode())
}

private fun scheduleAt(context: Context, task: BloomTask, whenDateTime: LocalDateTime, occurrenceDate: LocalDate, requestCode: Int) {
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("taskId", task.id); putExtra("title", task.title); putExtra("dueTime", formatTaskTime(task.time))
        putExtra("reminderMinutes", task.reminderMinutes); putExtra("recurrence", task.recurrence.name)
        putExtra("occurrenceDate", occurrenceDate.toString()); putExtra("notes", task.notes)
    }
    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val millis = whenDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    } else manager.setExact(AlarmManager.RTC_WAKEUP, millis, pending)
}

fun snoozeTask(context: Context, task: BloomTask, minutes: Long) {
    scheduleAt(context, task, LocalDateTime.now().plusMinutes(minutes), LocalDate.now(), task.id.hashCode() xor minutes.toInt())
    addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Snoozed", detail = "$minutes minutes"))
}

fun scheduleNextRecurringReminder(context: Context, task: BloomTask, firedDate: LocalDate) {
    if (task.recurrence == Recurrence.NONE) return
    val next = nextOccurrence(firedDate, task)
    if (next != firedDate) scheduleTaskReminder(context, task, next)
}

fun cancelTaskReminder(context: Context, taskId: Long) {
    val pending = PendingIntent.getBroadcast(context, taskId.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    pending.cancel()
}
