package com.kartiklabs.bloom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

const val BLOOM_PREFS = "bloom_prefs"

enum class Recurrence {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM;

    fun label(): String = when (this) {
        NONE -> "Once"
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        CUSTOM -> "Custom"
    }
}

enum class Priority(val label: String, val weight: Int) {
    CRITICAL("Critical", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1)
}

data class Subtask(
    val id: Long,
    val title: String,
    val done: Boolean = false
)

data class BloomTask(
    val id: Long,
    val title: String,
    val category: String,
    val due: String,
    val time: String = "09:00",
    val reminderMinutes: Int = 0,
    val recurrence: Recurrence = Recurrence.NONE,
    val repeatEvery: Int = 1,
    val repeatUnit: String = "days",
    val weekdays: Set<Int> = emptySet(),
    val repeatEnd: String = "",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val estimatedMinutes: Int = 30,
    val subtasks: List<Subtask> = emptyList(),
    val done: Boolean = false,
    val lastCompleted: String = ""
)

data class HistoryEvent(
    val id: Long = System.currentTimeMillis(),
    val taskId: Long,
    val title: String,
    val action: String,
    val timestamp: String = LocalDateTime.now().toString(),
    val detail: String = ""
)

fun recurrenceLabel(task: BloomTask): String = when (task.recurrence) {
    Recurrence.NONE -> "Once"
    Recurrence.DAILY -> "Every day"
    Recurrence.WEEKLY -> "Every week"
    Recurrence.MONTHLY -> "Every month"
    Recurrence.YEARLY -> "Every year"
    Recurrence.CUSTOM -> {
        if (task.weekdays.isNotEmpty()) {
            val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            task.weekdays.sorted().joinToString(" · ") { labels[(it - 1).coerceIn(0, 6)] }
        } else "Every ${task.repeatEvery.coerceAtLeast(1)} ${task.repeatUnit}"
    }
}

fun nextOccurrence(date: LocalDate, task: BloomTask): LocalDate {
    val next = when (task.recurrence) {
        Recurrence.NONE -> date
        Recurrence.DAILY -> date.plusDays(1)
        Recurrence.WEEKLY -> date.plusWeeks(1)
        Recurrence.MONTHLY -> date.plusMonths(1)
        Recurrence.YEARLY -> date.plusYears(1)
        Recurrence.CUSTOM -> {
            if (task.weekdays.isNotEmpty()) {
                var candidate = date.plusDays(1)
                while (!task.weekdays.contains(candidate.dayOfWeek.value)) candidate = candidate.plusDays(1)
                candidate
            } else when (task.repeatUnit) {
                "weeks" -> date.plusWeeks(task.repeatEvery.coerceAtLeast(1).toLong())
                "months" -> date.plusMonths(task.repeatEvery.coerceAtLeast(1).toLong())
                "years" -> date.plusYears(task.repeatEvery.coerceAtLeast(1).toLong())
                else -> date.plusDays(task.repeatEvery.coerceAtLeast(1).toLong())
            }
        }
    }
    val end = runCatching { LocalDate.parse(task.repeatEnd) }.getOrNull()
    return if (end != null && next.isAfter(end)) date else next
}

fun smartTaskScore(task: BloomTask, today: LocalDate = LocalDate.now()): Int {
    val due = runCatching { LocalDate.parse(task.due) }.getOrDefault(today)
    val urgency = when {
        due.isBefore(today) -> 500
        due == today -> 300
        due == today.plusDays(1) -> 100
        else -> 0
    }
    val shorterBonus = (120 - task.estimatedMinutes.coerceIn(5, 120)) / 5
    return task.priority.weight * 1000 + urgency + shorterBonus
}

fun saveTasks(context: Context, tasks: List<BloomTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        val subtaskArray = JSONArray()
        task.subtasks.forEach { sub ->
            subtaskArray.put(JSONObject().put("id", sub.id).put("title", sub.title).put("done", sub.done))
        }
        array.put(
            JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("category", task.category)
                .put("due", task.due)
                .put("time", task.time)
                .put("reminderMinutes", task.reminderMinutes)
                .put("recurrence", task.recurrence.name)
                .put("repeatEvery", task.repeatEvery)
                .put("repeatUnit", task.repeatUnit)
                .put("repeatEnd", task.repeatEnd)
                .put("notes", task.notes)
                .put("priority", task.priority.name)
                .put("estimatedMinutes", task.estimatedMinutes)
                .put("subtasks", subtaskArray)
                .put("done", task.done)
                .put("lastCompleted", task.lastCompleted)
                .put("weekdays", JSONArray(task.weekdays.toList()))
        )
    }
    context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .edit().putString("tasks", array.toString()).apply()
    BloomWidget.updateAll(context)
}

fun loadTasks(context: Context): List<BloomTask> {
    val raw = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .getString("tasks", null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val days = mutableSetOf<Int>()
                o.optJSONArray("weekdays")?.let { a -> for (j in 0 until a.length()) days += a.optInt(j) }
                val subtasks = mutableListOf<Subtask>()
                o.optJSONArray("subtasks")?.let { a ->
                    for (j in 0 until a.length()) {
                        val s = a.getJSONObject(j)
                        subtasks += Subtask(
                            id = s.optLong("id", System.currentTimeMillis() + j),
                            title = s.optString("title", "Step"),
                            done = s.optBoolean("done", false)
                        )
                    }
                }
                add(
                    BloomTask(
                        id = o.optLong("id", System.currentTimeMillis() + i),
                        title = o.optString("title", "Task"),
                        category = o.optString("category", "Personal"),
                        due = o.optString("due", LocalDate.now().toString()),
                        time = o.optString("time", "09:00"),
                        reminderMinutes = o.optInt("reminderMinutes", 0),
                        recurrence = runCatching { Recurrence.valueOf(o.optString("recurrence", "NONE")) }.getOrDefault(Recurrence.NONE),
                        repeatEvery = o.optInt("repeatEvery", 1),
                        repeatUnit = o.optString("repeatUnit", "days"),
                        weekdays = days,
                        repeatEnd = o.optString("repeatEnd", ""),
                        notes = o.optString("notes", ""),
                        priority = runCatching { Priority.valueOf(o.optString("priority", "MEDIUM")) }.getOrDefault(Priority.MEDIUM),
                        estimatedMinutes = o.optInt("estimatedMinutes", 30).coerceAtLeast(5),
                        subtasks = subtasks,
                        done = o.optBoolean("done", false),
                        lastCompleted = o.optString("lastCompleted", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun addHistory(context: Context, event: HistoryEvent) {
    val events = loadHistory(context).toMutableList()
    events.add(0, event)
    saveHistory(context, events.take(500))
}

fun saveHistory(context: Context, events: List<HistoryEvent>) {
    val array = JSONArray()
    events.forEach { e ->
        array.put(JSONObject().put("id", e.id).put("taskId", e.taskId).put("title", e.title)
            .put("action", e.action).put("timestamp", e.timestamp).put("detail", e.detail))
    }
    context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .edit().putString("history", array.toString()).apply()
}

fun loadHistory(context: Context): List<HistoryEvent> {
    val raw = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .getString("history", null) ?: return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(HistoryEvent(o.optLong("id"), o.optLong("taskId"), o.optString("title"), o.optString("action"), o.optString("timestamp"), o.optString("detail")))
            }
        }
    }.getOrDefault(emptyList())
}
