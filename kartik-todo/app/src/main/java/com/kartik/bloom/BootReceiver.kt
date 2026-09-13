package com.kartiklabs.bloom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        createReminderChannels(context)
        loadTasks(context)
            .filter { !it.done || it.recurrence != Recurrence.NONE }
            .forEach { scheduleTaskReminder(context, it) }
    }
}
