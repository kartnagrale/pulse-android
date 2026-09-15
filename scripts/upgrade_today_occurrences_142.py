from pathlib import Path

main = Path('kartik-todo/app/src/main/java/com/kartik/bloom/MainActivity.kt')
store = Path('kartik-todo/app/src/main/java/com/kartik/bloom/TaskStore.kt')

m = main.read_text()
s = store.read_text()

old_toggle = '''        } else {
            val today = LocalDate.now()
            val due = runCatching { LocalDate.parse(task.due) }.getOrDefault(today)
            val base = if (due.isBefore(today)) today else due
            val next = nextOccurrence(base, task)
            tasks[i] = task.copy(due = next.toString(), done = false, lastCompleted = today.toString(), subtasks = task.subtasks.map { it.copy(done = false) })
            addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Completed", detail = "Next: $next"))
            scheduleTaskReminder(context, tasks[i])
        }'''
new_toggle = '''        } else {
            val today = LocalDate.now()
            val completedToday = isTaskCompletedOnDate(task, today)
            if (completedToday) {
                // Re-open only today's occurrence. The recurring rule itself remains intact.
                tasks[i] = task.copy(due = today.toString(), done = false, lastCompleted = "", subtasks = task.subtasks.map { it.copy(done = false) })
                addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Reopened", detail = "Occurrence: $today"))
                scheduleTaskReminder(context, tasks[i])
            } else {
                val next = nextOccurrence(today, task)
                tasks[i] = task.copy(due = next.toString(), done = false, lastCompleted = today.toString(), subtasks = task.subtasks.map { it.copy(done = false) })
                addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Completed", detail = "Occurrence: $today · Next: $next"))
                scheduleTaskReminder(context, tasks[i])
            }
        }'''
assert old_toggle in m, 'toggle block not found'
m = m.replace(old_toggle, new_toggle)

start = m.index('    val todayOpenTasks = visibleTodayTasks(tasks, today)')
end = m.index('\n\n    LazyColumn(', start)
new_today_logic = '''    val todayTasks = visibleTodayTasks(tasks, today)
    val now = LocalTime.now()
    val filtered = todayTasks.filter { t ->
        val matchesQuery = query.isBlank() || t.title.contains(query, true) || t.notes.contains(query, true) || t.subtasks.any { it.title.contains(query, true) }
        val matchesCategory = category == "All" || t.category == category
        val completed = isTaskCompletedOnDate(t, today)
        val taskTime = runCatching { LocalTime.parse(t.time) }.getOrDefault(LocalTime.MAX)
        val matchesStatus = when (status) {
            "Done" -> completed
            "Overdue" -> !completed && taskTime.isBefore(now)
            else -> !completed
        }
        matchesQuery && matchesCategory && matchesStatus
    }.sortedWith(compareByDescending<BloomTask> { smartTaskScore(it, today) }.thenBy { it.time })
    val doneToday = todayTasks.count { isTaskCompletedOnDate(it, today) }'''
m = m[:start] + new_today_logic + m[end:]

m = m.replace('listOf("Open", "Overdue", "Recurring", "Done")', 'listOf("Open", "Overdue", "Done")')
m = m.replace('items(filtered, key = { it.id }) { TaskCard(it, onToggle, onEdit, onDelete, onTaskUpdate, onFocus) }',
'''items(filtered, key = { it.id }) { task ->
            val displayTask = task.copy(done = isTaskCompletedOnDate(task, today))
            TaskCard(displayTask, onToggle, onEdit, onDelete, onTaskUpdate, onFocus)
        }''')

m = m.replace('val actionable = visibleTodayTasks(tasks, today)', 'val actionable = visibleTodayTasks(tasks, today).filter { !isTaskCompletedOnDate(it, today) }')

m = m.replace('val count = tasks.count { it.due == date.toString() && !it.done }', 'val count = visibleTodayTasks(tasks, date).count { !isTaskCompletedOnDate(it, date) }')
m = m.replace('val dayTasks = tasks.filter { it.due == selected.toString() || it.lastCompleted == selected.toString() }.distinctBy { it.id }.sortedByDescending { smartTaskScore(it, selected) }',
'val dayTasks = visibleTodayTasks(tasks, selected).distinctBy { it.id }.sortedByDescending { smartTaskScore(it, selected) }')

main.write_text(m)

old_visible = '''fun visibleTodayTasks(tasks: List<BloomTask>, today: LocalDate = LocalDate.now()): List<BloomTask> =
    tasks.filter { task -> !task.done && task.due == today.toString() }
'''
new_visible = '''fun isTaskCompletedOnDate(task: BloomTask, date: LocalDate): Boolean =
    if (task.recurrence == Recurrence.NONE) task.done && task.due == date.toString()
    else task.lastCompleted == date.toString()

/** True when the recurring rule produces an occurrence on [date]. */
fun isTaskScheduledForDate(task: BloomTask, date: LocalDate): Boolean {
    val anchor = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return false
    val end = runCatching { LocalDate.parse(task.repeatEnd) }.getOrNull()
    if (end != null && date.isAfter(end)) return false
    if (date.isBefore(anchor)) return false
    if (date == anchor) return true
    if (task.recurrence == Recurrence.NONE) return false

    if (task.recurrence == Recurrence.CUSTOM && task.weekdays.isNotEmpty()) {
        return task.weekdays.contains(date.dayOfWeek.value)
    }

    var occurrence = anchor
    repeat(5000) {
        val next = nextOccurrence(occurrence, task)
        if (next == occurrence) return false
        occurrence = next
        if (occurrence == date) return true
        if (occurrence.isAfter(date)) return false
    }
    return false
}

/**
 * Occurrences for a day, including a completed occurrence whose recurring template
 * has already advanced to its next due date.
 */
fun visibleTodayTasks(tasks: List<BloomTask>, today: LocalDate = LocalDate.now()): List<BloomTask> =
    tasks.filter { task -> isTaskScheduledForDate(task, today) || task.lastCompleted == today.toString() }
'''
assert old_visible in s, 'visibleTodayTasks block not found'
s = s.replace(old_visible, new_visible)
store.write_text(s)
