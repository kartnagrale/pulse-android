package com.kartik.bloom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

const val BLOOM_PREFS = "bloom_prefs"

enum class Recurrence {
    NONE, DAILY, WEEKLY, YEARLY;

    fun label(): String = when (this) {
        NONE -> "Once"
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        YEARLY -> "Yearly"
    }
}

data class BloomTask(
    val id: Long,
    val title: String,
    val category: String,
    val due: String,
    val time: String = "09:00",
    val reminderMinutes: Int = 0,
    val recurrence: Recurrence = Recurrence.NONE,
    val done: Boolean = false,
    val lastCompleted: String = ""
)

fun nextOccurrence(date: LocalDate, recurrence: Recurrence): LocalDate = when (recurrence) {
    Recurrence.DAILY -> date.plusDays(1)
    Recurrence.WEEKLY -> date.plusWeeks(1)
    Recurrence.YEARLY -> date.plusYears(1)
    Recurrence.NONE -> date
}

fun saveTasks(context: Context, tasks: List<BloomTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(
            JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("category", task.category)
                .put("due", task.due)
                .put("time", task.time)
                .put("reminderMinutes", task.reminderMinutes)
                .put("recurrence", task.recurrence.name)
                .put("done", task.done)
                .put("lastCompleted", task.lastCompleted)
        )
    }
    context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("tasks", array.toString())
        .apply()
}

fun loadTasks(context: Context): List<BloomTask> {
    val raw = context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE)
        .getString("tasks", null) ?: return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    BloomTask(
                        id = o.optLong("id", System.currentTimeMillis() + i),
                        title = o.optString("title", "Task"),
                        category = o.optString("category", "Personal"),
                        due = o.optString("due", LocalDate.now().toString()),
                        time = o.optString("time", "09:00"),
                        reminderMinutes = o.optInt("reminderMinutes", 0),
                        recurrence = runCatching {
                            Recurrence.valueOf(o.optString("recurrence", "NONE"))
                        }.getOrDefault(Recurrence.NONE),
                        done = o.optBoolean("done", false),
                        lastCompleted = o.optString("lastCompleted", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
