package com.kynurelabs.bloom

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val quickAdd = intent.getBooleanExtra("quick_add", false) || intent.action == "com.kynurelabs.bloom.QUICK_ADD"
        setContent { BloomApp(startQuickAdd = quickAdd) }
    }
}

private const val KEY_NAME = "user_name"
private const val KEY_DARK = "dark_mode"
private const val KEY_REMINDER_MODE = "reminder_mode"
private const val KEY_ALARM_URI = "alarm_uri"
private val Purple = Color(0xFF8D63DE)
private val Pink = Color(0xFFF6B6CF)
private val Ink = Color(0xFF2D2636)
private val LightBg = Color(0xFFFFFAFC)
private val LightColors = lightColorScheme(primary = Purple, secondary = Pink, background = LightBg, surface = Color.White, onSurface = Ink, onBackground = Ink)
private val DarkColors = darkColorScheme(primary = Color(0xFFCAB0FF), secondary = Color(0xFFFFB5D0), background = Color(0xFF151218), surface = Color(0xFF211D25), onSurface = Color(0xFFF5EEF7), onBackground = Color(0xFFF5EEF7))

enum class BloomSection { TODAY, CALENDAR, HISTORY, INSIGHTS }

@Composable
fun BloomApp(startQuickAdd: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE) }
    val tasks = remember { mutableStateListOf<BloomTask>() }
    var history by remember { mutableStateOf(emptyList<HistoryEvent>()) }
    var loaded by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(prefs.getString(KEY_NAME, "") ?: "") }
    var darkMode by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, false)) }
    var reminderMode by remember { mutableStateOf(prefs.getString(KEY_REMINDER_MODE, "notification") ?: "notification") }
    var alarmUri by remember { mutableStateOf(prefs.getString(KEY_ALARM_URI, "") ?: "") }
    var section by remember { mutableStateOf(BloomSection.TODAY) }
    var showEditor by remember { mutableStateOf(startQuickAdd) }
    var editingTask by remember { mutableStateOf<BloomTask?>(null) }
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var focusTaskId by remember { mutableStateOf<Long?>(null) }
    var focusRemaining by remember { mutableIntStateOf(0) }
    var focusRunning by remember { mutableStateOf(false) }
    var focusLogged by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        createReminderChannels(context)
        val saved = loadTasks(context)
        tasks.addAll(saved)
        history = loadHistory(context)
        saved.filter { !it.done || it.recurrence != Recurrence.NONE }.forEach { scheduleTaskReminder(context, it) }
        BloomWidget.updateAll(context)
        loaded = true
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(focusRunning, focusTaskId) {
        while (focusRunning && focusRemaining > 0) {
            delay(1000)
            focusRemaining--
        }
        if (focusRunning && focusRemaining <= 0) focusRunning = false
    }

    val focusTask = tasks.firstOrNull { it.id == focusTaskId }
    LaunchedEffect(focusRemaining, focusTaskId) {
        val task = focusTask
        if (task != null && focusRemaining == 0 && !focusLogged) {
            focusLogged = true
            addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Focus completed", detail = "${task.estimatedMinutes} min"))
            history = loadHistory(context)
        }
    }

    fun persist() {
        saveTasks(context, tasks)
        history = loadHistory(context)
    }

    fun updateTask(task: BloomTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(context, tasks)
        }
    }

    fun toggleTask(task: BloomTask) {
        val i = tasks.indexOfFirst { it.id == task.id }
        if (i < 0) return
        cancelTaskReminder(context, task.id)
        if (task.recurrence == Recurrence.NONE) {
            val complete = !task.done
            tasks[i] = task.copy(
                done = complete,
                lastCompleted = if (complete) LocalDate.now().toString() else "",
                subtasks = if (complete) task.subtasks.map { it.copy(done = true) } else task.subtasks
            )
            addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = if (complete) "Completed" else "Reopened"))
        } else {
            val today = LocalDate.now()
            val due = runCatching { LocalDate.parse(task.due) }.getOrDefault(today)
            val base = if (due.isBefore(today)) today else due
            val next = nextOccurrence(base, task)
            tasks[i] = task.copy(due = next.toString(), done = false, lastCompleted = today.toString(), subtasks = task.subtasks.map { it.copy(done = false) })
            addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Completed", detail = "Next: $next"))
            scheduleTaskReminder(context, tasks[i])
        }
        persist()
    }

    fun startFocus(task: BloomTask) {
        focusTaskId = task.id
        focusRemaining = task.estimatedMinutes.coerceAtLeast(5) * 60
        focusRunning = true
        focusLogged = false
        addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Focus started", detail = "${task.estimatedMinutes} min"))
        history = loadHistory(context)
    }

    MaterialTheme(colorScheme = if (darkMode) DarkColors else LightColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (loaded) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                Triple(BloomSection.TODAY, Icons.Default.Today, "Today"),
                                Triple(BloomSection.CALENDAR, Icons.Default.CalendarMonth, "Calendar"),
                                Triple(BloomSection.HISTORY, Icons.Default.History, "History"),
                                Triple(BloomSection.INSIGHTS, Icons.Default.BarChart, "Insights")
                            ).forEach { (item, icon, label) ->
                                NavigationBarItem(selected = section == item, onClick = { section = item }, icon = { Icon(icon, label) }, label = { Text(label) })
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { editingTask = null; editorDate = null; showEditor = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ) { Icon(Icons.Default.Add, "Add task") }
                    }
                ) { pad ->
                    when (section) {
                        BloomSection.TODAY -> TodayScreen(
                            Modifier.padding(pad), userName, tasks,
                            onSettings = { showSettings = true },
                            onToggle = { toggleTask(it) },
                            onEdit = { editingTask = it; editorDate = null; showEditor = true },
                            onDelete = { task ->
                                cancelTaskReminder(context, task.id)
                                tasks.removeAll { it.id == task.id }
                                addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Deleted"))
                                persist()
                            },
                            onTaskUpdate = { updateTask(it) },
                            onFocus = { startFocus(it) }
                        )
                        BloomSection.CALENDAR -> CalendarScreen(Modifier.padding(pad), tasks, onDateAdd = { editorDate = it; editingTask = null; showEditor = true }, onEdit = { editingTask = it; showEditor = true })
                        BloomSection.HISTORY -> HistoryScreen(Modifier.padding(pad), history)
                        BloomSection.INSIGHTS -> InsightsScreen(Modifier.padding(pad), tasks, history)
                    }
                }
            }

            if (userName.isBlank()) FirstLaunchNameScreen { userName = it.trim(); prefs.edit().putString(KEY_NAME, userName).apply() }

            if (showEditor) TaskEditorSheet(editingTask, editorDate, onDismiss = { showEditor = false; editingTask = null; editorDate = null }) { task ->
                cancelTaskReminder(context, task.id)
                val i = tasks.indexOfFirst { it.id == task.id }
                if (i >= 0) {
                    tasks[i] = task
                    addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Edited"))
                } else {
                    tasks.add(0, task)
                    addHistory(context, HistoryEvent(taskId = task.id, title = task.title, action = "Created"))
                }
                saveTasks(context, tasks)
                scheduleTaskReminder(context, task)
                history = loadHistory(context)
                showEditor = false; editingTask = null; editorDate = null
            }

            if (showSettings) SettingsSheet(userName, darkMode, reminderMode, alarmUri, onDismiss = { showSettings = false }, onNameChange = {
                userName = it; prefs.edit().putString(KEY_NAME, it).apply()
            }, onDarkModeChange = { darkMode = it; prefs.edit().putBoolean(KEY_DARK, it).apply() }, onReminderModeChange = {
                reminderMode = it; prefs.edit().putString(KEY_REMINDER_MODE, it).apply()
            }, onAlarmUriChange = { alarmUri = it; prefs.edit().putString(KEY_ALARM_URI, it).apply(); ensureAlarmChannel(context) })

            if (focusTask != null) FocusOverlay(
                task = focusTask,
                remainingSeconds = focusRemaining,
                running = focusRunning,
                onToggleRunning = { focusRunning = !focusRunning },
                onAddFive = { focusRemaining += 5 * 60 },
                onExit = { focusTaskId = null; focusRunning = false },
                onComplete = {
                    if (focusRemaining > 0) {
                        val spent = ((focusTask.estimatedMinutes * 60 - focusRemaining).coerceAtLeast(0) / 60).coerceAtLeast(1)
                        addHistory(context, HistoryEvent(taskId = focusTask.id, title = focusTask.title, action = "Focus completed", detail = "$spent min"))
                        history = loadHistory(context)
                    }
                    toggleTask(focusTask)
                    focusTaskId = null; focusRunning = false
                }
            )
        }
    }
}

@Composable
private fun FirstLaunchNameScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF7FB), Color(0xFFF6F0FF), Color(0xFFFFFBF7)))).padding(28.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(26.dp)) {
                Text("Welcome to Bloom 🌷", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("Your personal productivity system.", color = Color(0xFF817985), modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true)
                Button({ if (name.isNotBlank()) onContinue(name) }, Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), enabled = name.isNotBlank()) { Text("Start blooming") }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    name: String,
    tasks: List<BloomTask>,
    onSettings: () -> Unit,
    onToggle: (BloomTask) -> Unit,
    onEdit: (BloomTask) -> Unit,
    onDelete: (BloomTask) -> Unit,
    onTaskUpdate: (BloomTask) -> Unit,
    onFocus: (BloomTask) -> Unit
) {
    val today = LocalDate.now()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var status by remember { mutableStateOf("Open") }
    val todayOpenTasks = visibleTodayTasks(tasks, today)
    val filtered = todayOpenTasks.filter { t ->
        val matchesQuery = query.isBlank() || t.title.contains(query, true) || t.notes.contains(query, true) || t.subtasks.any { it.title.contains(query, true) }
        val matchesCategory = category == "All" || t.category == category
        val matchesStatus = when (status) {
            "Recurring" -> t.recurrence != Recurrence.NONE
            "Done", "Overdue" -> false
            else -> true
        }
        matchesQuery && matchesCategory && matchesStatus
    }.sortedWith(compareByDescending<BloomTask> { smartTaskScore(it, today) }.thenBy { it.time })
    val todayTasks = tasks.filter { it.due == today.toString() }
    val doneToday = todayTasks.count { it.done || it.lastCompleted == today.toString() }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                IconButton(onSettings, Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) { Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${greeting()},\n$name ✨", fontSize = 29.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = MaterialTheme.colorScheme.onSurface.copy(.55f))
                }
            }
        }
        item { ProgressCard(todayTasks.size, doneToday) }
        item { SmartPlanCard(tasks, onFocus) }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search tasks, notes or subtasks") }, singleLine = true, shape = RoundedCornerShape(18.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Open", "Overdue", "Recurring", "Done").forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s, maxLines = 1, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("All", "Personal", "Work", "Health").forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) } }
            }
        }
        item { Text(if (filtered.isEmpty()) "Nothing demanding your attention 🌷" else "${filtered.size} tasks", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(filtered, key = { it.id }) { TaskCard(it, onToggle, onEdit, onDelete, onTaskUpdate, onFocus) }
    }
}

@Composable
private fun SmartPlanCard(tasks: List<BloomTask>, onFocus: (BloomTask) -> Unit) {
    val today = LocalDate.now()
    val actionable = visibleTodayTasks(tasks, today)
    val plan = actionable.sortedByDescending { smartTaskScore(it, today) }.take(3)
    val minutes = actionable.sumOf { it.estimatedMinutes }
    val overload = minutes > 8 * 60
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(.55f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(" Smart Today", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (minutes > 0) Text(formatDuration(minutes), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (plan.isEmpty()) Text("Your queue is clear. Protect the space instead of filling it.", color = MaterialTheme.colorScheme.onSurface.copy(.65f))
            else {
                plan.forEachIndexed { index, task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${task.priority.label} · ${formatDuration(task.estimatedMinutes)} · ${dueLabel(task.due)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                        }
                        if (index == 0) FilledTonalButton(onClick = { onFocus(task) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Text("Focus") }
                    }
                }
                if (overload) Text("⚠️ You planned ${formatDuration(minutes)} for today. Consider moving low-priority work.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProgressCard(total: Int, done: Int) {
    val p = if (total == 0) 0f else done.toFloat() / total
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(.17f), MaterialTheme.colorScheme.secondary.copy(.25f)))).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Today’s rhythm", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (total == 0) "A fresh page awaits." else "$done of $total scheduled tasks complete", color = MaterialTheme.colorScheme.onSurface.copy(.6f)) }
                Text("${(p * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 15.dp).height(8.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun TaskCard(
    task: BloomTask,
    onToggle: (BloomTask) -> Unit,
    onEdit: (BloomTask) -> Unit,
    onDelete: (BloomTask) -> Unit,
    onTaskUpdate: (BloomTask) -> Unit,
    onFocus: (BloomTask) -> Unit
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    val subDone = task.subtasks.count { it.done }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(task.done, { onToggle(task) })
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PriorityPill(task.priority)
                        Spacer(Modifier.width(7.dp))
                        Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, textDecoration = if (task.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                    }
                    if (task.notes.isNotBlank()) Text(task.notes, maxLines = 2, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.58f), modifier = Modifier.padding(top = 5.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip({}, { Text(task.category, fontSize = 11.sp) })
                        Text("${dueLabel(task.due)} · ${formatTaskTime(task.time)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Timer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(formatDuration(task.estimatedMinutes), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        if (task.recurrence != Recurrence.NONE) {
                            Icon(Icons.Default.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(recurrenceLabel(task), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Column {
                    IconButton({ onFocus(task) }) { Icon(Icons.Default.PlayCircleOutline, "Focus", tint = MaterialTheme.colorScheme.primary) }
                    IconButton({ onEdit(task) }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
            if (task.subtasks.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Text("$subDone/${task.subtasks.size} subtasks")
                }
                if (expanded) {
                    task.subtasks.forEach { sub ->
                        Row(Modifier.fillMaxWidth().clickable {
                            val updated = task.copy(subtasks = task.subtasks.map { if (it.id == sub.id) it.copy(done = !it.done) else it })
                            onTaskUpdate(updated)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(sub.done, null)
                            Text(sub.title, fontSize = 13.sp, textDecoration = if (sub.done) TextDecoration.LineThrough else null)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ onDelete(task) }) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(17.dp)); Text("Delete") }
            }
        }
    }
}

@Composable
private fun PriorityPill(priority: Priority) {
    val alpha = when (priority) { Priority.CRITICAL -> .24f; Priority.HIGH -> .19f; Priority.MEDIUM -> .14f; Priority.LOW -> .10f }
    Text(priority.label.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha)).padding(horizontal = 7.dp, vertical = 3.dp))
}

@Composable
private fun CalendarScreen(modifier: Modifier, tasks: List<BloomTask>, onDateAdd: (LocalDate) -> Unit, onEdit: (BloomTask) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { listOf("M","T","W","T","F","S","S").forEach { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(.5f)) } }
        Spacer(Modifier.height(8.dp))
        val cells = List<LocalDate?>(offset) { null } + days
        LazyVerticalGrid(GridCells.Fixed(7), modifier = Modifier.height(290.dp), userScrollEnabled = false) {
            items(cells) { date ->
                if (date == null) Spacer(Modifier.size(40.dp)) else {
                    val count = tasks.count { it.due == date.toString() && !it.done }
                    Box(Modifier.padding(3.dp).aspectRatio(1f).clip(CircleShape).background(if (selected == date) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { selected = date }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${date.dayOfMonth}", color = if (selected == date) Color.White else MaterialTheme.colorScheme.onSurface); if (count > 0) Text("•", color = if (selected == date) Color.White else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton({ onDateAdd(selected) }) { Text("+ Add") } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            val dayTasks = tasks.filter { it.due == selected.toString() || it.lastCompleted == selected.toString() }.distinctBy { it.id }.sortedByDescending { smartTaskScore(it, selected) }
            if (dayTasks.isEmpty()) item { Text("No tasks. Protect this space or add something meaningful.", color = MaterialTheme.colorScheme.onSurface.copy(.55f), modifier = Modifier.padding(top = 16.dp)) }
            items(dayTasks) { t -> Card(Modifier.fillMaxWidth().clickable { onEdit(t) }, shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { PriorityPill(t.priority); Text(formatTaskTime(t.time), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp)); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(t.title); Text(formatDuration(t.estimatedMinutes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f)) } } } }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, history: List<HistoryEvent>) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Task history", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("Your actions, not just your unfinished list.", color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
        if (history.isEmpty()) item { Text("Your history will appear here.", modifier = Modifier.padding(top = 24.dp)) }
        items(history) { e ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(when (e.action) { "Completed", "Focus completed" -> Icons.Default.CheckCircle; "Snoozed" -> Icons.Default.Snooze; "Deleted" -> Icons.Default.DeleteOutline; "Focus started" -> Icons.Default.Timer; else -> Icons.Default.History }, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold); Text("${e.action}${if (e.detail.isNotBlank()) " · ${e.detail}" else ""}", fontSize = 12.sp); Text(historyTime(e.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f)) } } }
        }
    }
}

@Composable
private fun InsightsScreen(modifier: Modifier, tasks: List<BloomTask>, history: List<HistoryEvent>) {
    val now = LocalDate.now()
    val completedEvents = history.filter { it.action == "Completed" }
    val focusEvents = history.filter { it.action == "Focus completed" }
    val weekStart = now.minusDays(6)
    val weekCompleted = completedEvents.count { runCatching { LocalDateTime.parse(it.timestamp).toLocalDate() >= weekStart }.getOrDefault(false) }
    val weekFocusMinutes = focusEvents.filter { runCatching { LocalDateTime.parse(it.timestamp).toLocalDate() >= weekStart }.getOrDefault(false) }.sumOf { Regex("(\\d+)").find(it.detail)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0 }
    val totalOpen = tasks.count { !it.done }
    val overdue = tasks.count { !it.done && runCatching { LocalDate.parse(it.due).isBefore(now) }.getOrDefault(false) }
    val recurring = tasks.count { it.recurrence != Recurrence.NONE }
    val plannedToday = tasks.filter { !it.done && it.due == now.toString() }.sumOf { it.estimatedMinutes }
    val bestCategory = completedEvents.groupingBy { event -> tasks.firstOrNull { it.id == event.taskId }?.category ?: "General" }.eachCount().maxByOrNull { it.value }?.key ?: "—"
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("Use your data to plan better, not to judge yourself.", color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("7-day wins", "$weekCompleted", Modifier.weight(1f)); MetricCard("Focus", formatDuration(weekFocusMinutes), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Open", "$totalOpen", Modifier.weight(1f)); MetricCard("Overdue", "$overdue", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Recurring", "$recurring", Modifier.weight(1f)); MetricCard("Today load", formatDuration(plannedToday), Modifier.weight(1f)) } }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("Your productivity pulse", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Strongest category: $bestCategory", modifier = Modifier.padding(top = 8.dp)); Text(if (overdue == 0) "No overdue tasks. Your queue is under control." else "$overdue tasks are overdue. Reschedule them intentionally instead of carrying them forever.", color = MaterialTheme.colorScheme.onSurface.copy(.65f), modifier = Modifier.padding(top = 6.dp)) } } }
        item {
            val counts = (6 downTo 0).map { d -> val date = now.minusDays(d.toLong()); date to completedEvents.count { runCatching { LocalDateTime.parse(it.timestamp).toLocalDate() == date }.getOrDefault(false) } }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("Last 7 days", fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { counts.forEach { (date, count) -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(22.dp).height((16 + count * 12).coerceAtMost(100).dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(.75f))); Text(date.dayOfWeek.name.take(1), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)) } } } } }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text(value, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary); Text(label, color = MaterialTheme.colorScheme.onSurface.copy(.6f)) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(existing: BloomTask?, initialDate: LocalDate?, onDismiss: () -> Unit, onSave: (BloomTask) -> Unit) {
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Personal") }
    var due by remember(existing?.id, initialDate) { mutableStateOf(initialDate ?: runCatching { LocalDate.parse(existing?.due ?: "") }.getOrDefault(LocalDate.now())) }
    var time by remember(existing?.id) { mutableStateOf(runCatching { LocalTime.parse(existing?.time ?: "09:00") }.getOrDefault(LocalTime.of(9, 0))) }
    var reminder by remember(existing?.id) { mutableIntStateOf(existing?.reminderMinutes ?: 0) }
    var recurrence by remember(existing?.id) { mutableStateOf(existing?.recurrence ?: Recurrence.NONE) }
    var every by remember(existing?.id) { mutableIntStateOf(existing?.repeatEvery ?: 1) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.repeatUnit ?: "days") }
    var weekdays by remember(existing?.id) { mutableStateOf(existing?.weekdays ?: emptySet()) }
    var priority by remember(existing?.id) { mutableStateOf(existing?.priority ?: Priority.MEDIUM) }
    var duration by remember(existing?.id) { mutableIntStateOf(existing?.estimatedMinutes ?: 30) }
    var subtasksText by remember(existing?.id) { mutableStateOf(existing?.subtasks?.joinToString("\n") { it.title } ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(if (existing == null) "New task" else "Edit task", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("What needs doing?") }, maxLines = 3, shape = RoundedCornerShape(18.dp)) }
            item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes / details") }, placeholder = { Text("Context, links, instructions…") }, minLines = 2, maxLines = 5, shape = RoundedCornerShape(18.dp)) }
            item { OutlinedTextField(subtasksText, { subtasksText = it }, Modifier.fillMaxWidth(), label = { Text("Subtasks") }, placeholder = { Text("One step per line") }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(18.dp)) }
            item { Text("Priority", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Priority.entries.forEach { p -> FilterChip(priority == p, { priority = p }, { Text(p.label) }) } } }
            item { Text("Estimated duration", fontWeight = FontWeight.Bold); Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15,30,45).forEach { d -> FilterChip(duration == d, { duration = d }, { Text(formatDuration(d)) }) } }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(60,90,120).forEach { d -> FilterChip(duration == d, { duration = d }, { Text(formatDuration(d)) }) } } } }
            item { Text("Category", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Personal","Work","Health").forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) } } }
            item {
                Text("When", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d) }, due.year, due.monthValue - 1, due.dayOfMonth).show() }, Modifier.weight(1f)) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(5.dp)); Text(due.format(DateTimeFormatter.ofPattern("d MMM"))) }
                    OutlinedButton({ TimePickerDialog(context, { _, h, min -> time = LocalTime.of(h, min) }, time.hour, time.minute, false).show() }, Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(5.dp)); Text(time.format(DateTimeFormatter.ofPattern("h:mm a"))) }
                }
            }
            item { Text("Remind me", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "On time", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (v,l) -> FilterChip(reminder == v, { reminder = v }, { Text(l) }) } } }
            item {
                Text("Repeat", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(Recurrence.NONE, Recurrence.DAILY, Recurrence.WEEKLY).forEach { r -> FilterChip(recurrence == r, { recurrence = r }, { Text(r.label()) }) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(Recurrence.MONTHLY, Recurrence.YEARLY, Recurrence.CUSTOM).forEach { r -> FilterChip(recurrence == r, { recurrence = r }, { Text(r.label()) }) } }
                }
            }
            if (recurrence == Recurrence.CUSTOM) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom schedule", fontWeight = FontWeight.SemiBold)
                    Text("Specific weekdays", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("M","T","W","T","F","S","S").forEachIndexed { i, d -> val value = i + 1; FilterChip(weekdays.contains(value), { weekdays = if (weekdays.contains(value)) weekdays - value else weekdays + value }, { Text(d) }) } }
                    Text("Or repeat every", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (every > 1) every-- }) { Text("−") }; Text("$every", fontWeight = FontWeight.Bold); OutlinedButton({ every++ }) { Text("+") }
                        listOf("days","weeks","months").forEach { u -> FilterChip(unit == u, { unit = u; weekdays = emptySet() }, { Text(u.take(1).uppercase()) }) }
                    }
                }
            }
            item {
                Button({
                    if (title.isNotBlank()) {
                        val oldByTitle = existing?.subtasks?.associateBy { it.title } ?: emptyMap()
                        val parsedSubs = subtasksText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().mapIndexed { index, text ->
                            oldByTitle[text] ?: Subtask(id = System.currentTimeMillis() + index, title = text)
                        }
                        onSave(BloomTask(
                            id = existing?.id ?: System.currentTimeMillis(), title = title.trim(), category = category,
                            due = due.toString(), time = time.format(DateTimeFormatter.ofPattern("HH:mm")), reminderMinutes = reminder,
                            recurrence = recurrence, repeatEvery = every, repeatUnit = unit, weekdays = weekdays,
                            repeatEnd = existing?.repeatEnd ?: "", notes = notes.trim(), priority = priority,
                            estimatedMinutes = duration, subtasks = parsedSubs, done = existing?.done ?: false,
                            lastCompleted = existing?.lastCompleted ?: ""
                        ))
                    }
                }, Modifier.fillMaxWidth().height(52.dp), enabled = title.isNotBlank()) { Text(if (existing == null) "Add task" else "Save changes", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun FocusOverlay(
    task: BloomTask,
    remainingSeconds: Int,
    running: Boolean,
    onToggleRunning: () -> Unit,
    onAddFive: () -> Unit,
    onExit: () -> Unit,
    onComplete: () -> Unit
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(.18f), MaterialTheme.colorScheme.background))).padding(28.dp)) {
            IconButton(onExit, Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.Close, "Exit focus") }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("FOCUS MODE", letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(task.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                if (task.subtasks.any { !it.done }) Text("Next: ${task.subtasks.first { !it.done }.title}", color = MaterialTheme.colorScheme.onSurface.copy(.62f))
                Text(String.format(Locale.ENGLISH, "%02d:%02d", mins, secs), fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(if (running) "Stay with this one thing." else "Timer paused", color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onToggleRunning) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(if (running) "Pause" else "Resume") }
                    OutlinedButton(onAddFive) { Text("+5 min") }
                }
                Button(onComplete, Modifier.fillMaxWidth(.75f).height(52.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Complete task") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(userName: String, dark: Boolean, reminderMode: String, alarmUri: String, onDismiss: () -> Unit, onNameChange: (String) -> Unit, onDarkModeChange: (Boolean) -> Unit, onReminderModeChange: (String) -> Unit, onAlarmUriChange: (String) -> Unit) {
    var draft by remember(userName) { mutableStateOf(userName) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) onAlarmUriChange(uri.toString())
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold) }
            item { OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, trailingIcon = { IconButton({ if (draft.isNotBlank()) onNameChange(draft.trim()) }) { Icon(Icons.Default.Check, "Save") } }) }
            item { SettingsCard("Appearance") { SettingChoice(Icons.Default.LightMode, "Light", !dark) { onDarkModeChange(false) }; SettingChoice(Icons.Default.DarkMode, "Dark", dark) { onDarkModeChange(true) } } }
            item { SettingsCard("Reminder style") { SettingChoice(Icons.Default.Notifications, "Notification", reminderMode == "notification") { onReminderModeChange("notification") }; SettingChoice(Icons.Default.Alarm, "Alarm + snooze", reminderMode == "alarm") { onReminderModeChange("alarm") } } }
            if (reminderMode == "alarm") item { SettingsCard("Alarm sound") { SettingChoice(Icons.Default.Alarm, "Phone default", alarmUri.isBlank()) { onAlarmUriChange("") }; Row(Modifier.fillMaxWidth().clickable { launcher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply { putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION) }) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary); Text("Choose from phone", Modifier.padding(start = 12.dp).weight(1f)); RadioButton(alarmUri.isNotBlank(), null) } } }
            item { TextButton({ context.startActivity(Intent(context, AboutPrivacyActivity::class.java)) }) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(6.dp)); Text("About & Privacy") } }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() } }
}

@Composable
private fun SettingChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, Modifier.padding(start = 12.dp).weight(1f)); RadioButton(selected, click) }
}

private fun greeting(): String = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
private fun dueLabel(due: String): String { val d = runCatching { LocalDate.parse(due) }.getOrNull() ?: return due; return when (d) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"; else -> d.format(DateTimeFormatter.ofPattern("d MMM")) } }
private fun historyTime(value: String): String = runCatching { LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM · h:mm a", Locale.ENGLISH)) }.getOrDefault(value)
private fun formatDuration(minutes: Int): String = when { minutes <= 0 -> "0m"; minutes < 60 -> "${minutes}m"; minutes % 60 == 0 -> "${minutes / 60}h"; else -> "${minutes / 60}h ${minutes % 60}m" }
