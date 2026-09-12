package com.kartik.bloom

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalTime

class BloomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BloomWidget::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val today = LocalDate.now().toString()
            val tasks = loadTasks(context).filter { !it.done && it.due <= today }
                .sortedWith(compareBy<BloomTask> { it.due }.thenBy { it.time })
            val todayTasks = loadTasks(context).filter { it.due == today }
            val done = todayTasks.count { it.done || it.lastCompleted == today }
            val views = RemoteViews(context.packageName, R.layout.widget_bloom)
            views.setTextViewText(R.id.widget_title, "Bloom · Today")
            views.setTextViewText(R.id.widget_progress, if (todayTasks.isEmpty()) "Fresh day" else "$done/${todayTasks.size} complete")
            val next = tasks.firstOrNull()
            views.setTextViewText(R.id.widget_task, next?.let { "${formatTaskTime(it.time)}  ${it.title}" } ?: "All clear ✨")

            val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val add = PendingIntent.getActivity(context, id + 1000, Intent(context, MainActivity::class.java).putExtra("quick_add", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_add, add)
            manager.updateAppWidget(id, views)
        }
    }
}
