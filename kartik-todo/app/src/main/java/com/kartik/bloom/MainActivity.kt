package com.kartik.bloom

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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BloomApp() }
    }
}

private const val KEY_NAME = "user_name"
private const val KEY_DARK = "dark_mode"
private const val KEY_REMINDER_MODE = "reminder_mode"
private const val KEY_ALARM_URI = "alarm_uri"

private val Purple = Color(0xFF8D63DE)
private val Pink = Color(0xFFF6B6CF)
private val Ink = Color(0xFF2D2636)
private val Muted = Color(0xFF817985)
private val LightBg = Color(0xFFFFFAFC)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Pink,
    background = LightBg,
    surface = Color.White,
    onSurface = Ink,
    onBackground = Ink
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCAB0FF),
    secondary = Color(0xFFFFB5D0),
    background = Color(0xFF151218),
    surface = Color(0xFF211D25),
    onSurface = Color(0xFFF5EEF7),
    onBackground = Color(0xFFF5EEF7)
)

@Composable
fun BloomApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BLOOM_PREFS, Context.MODE_PRIVATE) }
    val tasks = remember { mutableStateListOf<BloomTask>() }

    var loaded by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(prefs.getString(KEY_NAME, "") ?: "") }
    var darkMode by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, false)) }
    var reminderMode by remember { mutableStateOf(prefs.getString(KEY_REMINDER_MODE, "notification") ?: "notification") }
    var alarmUri by remember { mutableStateOf(prefs.getString(KEY_ALARM_URI, "") ?: "") }
    var selectedTab by remember { mutableStateOf("Today") }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<BloomTask?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        createReminderChannels(context)
        val savedTasks = loadTasks(context)
        tasks.addAll(savedTasks)
        savedTasks.filter { !it.done || it.recurrence != Recurrence.NONE }
            .forEach { scheduleTaskReminder(context, it) }
        loaded = true
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    MaterialTheme(colorScheme = if (darkMode) DarkColors else LightColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (loaded) {
                HomeScreen(
                    userName = userName.ifBlank { "there" },
                    tasks = tasks,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onToggle = { task ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) {
                            cancelTaskReminder(context, task.id)
                            val updated = if (task.recurrence == Recurrence.NONE) {
                                task.copy(done = !task.done, lastCompleted = if (!task.done) LocalDate.now().toString() else "")
                            } else {
                                val today = LocalDate.now()
                                val due = runCatching { LocalDate.parse(task.due) }.getOrDefault(today)
                                val base = if (due.isBefore(today)) today else due
                                task.copy(
                                    due = nextOccurrence(base, task.recurrence).toString(),
                                    done = false,
                                    lastCompleted = today.toString()
                                )
                            }
                            tasks[index] = updated
                            saveTasks(context, tasks)
                            if (!updated.done || updated.recurrence != Recurrence.NONE) scheduleTaskReminder(context, updated)
                        }
                    },
                    onDelete = { task ->
                        cancelTaskReminder(context, task.id)
                        tasks.removeAll { it.id == task.id }
                        saveTasks(context, tasks)
                    },
                    onEdit = { task -> editingTask = task; showEditor = true },
                    onAdd = { editingTask = null; showEditor = true },
                    onSettings = { showSettings = true }
                )
            }

            if (userName.isBlank()) {
                FirstLaunchNameScreen { name ->
                    userName = name.trim()
                    prefs.edit().putString(KEY_NAME, userName).apply()
                }
            }

            if (showEditor) {
                TaskEditorSheet(
                    existing = editingTask,
                    onDismiss = { showEditor = false; editingTask = null },
                    onSave = { task ->
                        cancelTaskReminder(context, task.id)
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) tasks[index] = task else tasks.add(0, task)
                        saveTasks(context, tasks)
                        if (!task.done || task.recurrence != Recurrence.NONE) scheduleTaskReminder(context, task)
                        selectedTab = if (task.due == LocalDate.now().toString()) "Today" else "Upcoming"
                        showEditor = false
                        editingTask = null
                    }
                )
            }

            if (showSettings) {
                SettingsSheet(
                    userName = userName,
                    darkMode = darkMode,
                    reminderMode = reminderMode,
                    alarmUri = alarmUri,
                    onDismiss = { showSettings = false },
                    onNameChange = { userName = it; prefs.edit().putString(KEY_NAME, it).apply() },
                    onDarkModeChange = { darkMode = it; prefs.edit().putBoolean(KEY_DARK, it).apply() },
                    onReminderModeChange = { reminderMode = it; prefs.edit().putString(KEY_REMINDER_MODE, it).apply() },
                    onAlarmUriChange = {
                        alarmUri = it
                        prefs.edit().putString(KEY_ALARM_URI, it).apply()
                        ensureAlarmChannel(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun FirstLaunchNameScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFF7FB), Color(0xFFF6F0FF), Color(0xFFFFFBF7)))
        ).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(26.dp)) {
                Text("Welcome to Bloom 🌷", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text("A calmer place for your day. What should I call you?", color = Muted)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(18.dp))
                Button({ if (name.isNotBlank()) onContinue(name) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.isNotBlank(), shape = RoundedCornerShape(18.dp)) {
                    Text("Start planning", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    userName: String,
    tasks: List<BloomTask>,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onToggle: (BloomTask) -> Unit,
    onDelete: (BloomTask) -> Unit,
    onEdit: (BloomTask) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    val today = LocalDate.now()
    val visibleTasks = when (selectedTab) {
        "Today" -> tasks.filter {
            val due = runCatching { LocalDate.parse(it.due) }.getOrNull()
            due == today || (it.recurrence != Recurrence.NONE && due != null && due.isBefore(today))
        }
        "Upcoming" -> tasks.filter { runCatching { LocalDate.parse(it.due).isAfter(today) }.getOrDefault(false) }
        else -> tasks
    }.sortedWith(compareBy<BloomTask> { it.done }.thenBy { it.due }.thenBy { it.time })

    val todayRelevant = tasks.filter { it.due == today.toString() || it.lastCompleted == today.toString() }
    val completedToday = todayRelevant.count { it.done || it.lastCompleted == today.toString() }
    val progress = if (todayRelevant.isEmpty()) 0f else completedToday.toFloat() / todayRelevant.size

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.Add, "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Header(userName, onSettings) }
            item { ProgressCard(todayRelevant.size, completedToday, progress) }
            item { TabRow(selectedTab, onTabChange) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(when (selectedTab) { "Today" -> "Today’s plan"; "Upcoming" -> "Coming up"; else -> "All tasks" }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${visibleTasks.count { !it.done }} left", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            }
            if (visibleTasks.isEmpty()) item { EmptyState(onAdd) }
            else items(visibleTasks, key = { it.id }) { TaskCard(it, onToggle, onEdit, onDelete) }
        }
    }
}

@Composable
private fun Header(userName: String, onSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconButton(onSettings, Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$greeting,\n$userName ✨", fontSize = 31.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(LocalDate.now().format(formatter), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}

@Composable
private fun ProgressCard(total: Int, complete: Int, progress: Float) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    if (MaterialTheme.colorScheme.background == LightBg) listOf(Color(0xFFEFE4FF), Color(0xFFFFE8F1), Color(0xFFFFEEE5))
                    else listOf(Color(0xFF352849), Color(0xFF3B2731), Color(0xFF3A2B25))
                )
            ).padding(20.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Today’s rhythm", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (total == 0) "A fresh page awaits." else "$complete of $total tasks complete", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 14.sp)
                    }
                    Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .7f))
            }
        }
    }
}

@Composable
private fun TabRow(selected: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Today", "Upcoming", "All").forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onChange(tab) },
                label = { Text(tab, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White, containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
private fun TaskCard(task: BloomTask, onToggle: (BloomTask) -> Unit, onEdit: (BloomTask) -> Unit, onDelete: (BloomTask) -> Unit) {
    val accent = categoryColor(task.category)
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(90.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Checkbox(task.done, { onToggle(task) })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (task.done) .48f else 1f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Badge(task.category, accent)
                    if (task.recurrence != Recurrence.NONE) Badge(task.recurrence.label(), MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Spacer(Modifier.width(4.dp)); Text(dueLabel(task.due), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Spacer(Modifier.width(10.dp)); Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Spacer(Modifier.width(4.dp)); Text(formatTaskTime(task.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                Spacer(Modifier.height(5.dp))
                Text(reminderLabel(task.reminderMinutes), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton({ onEdit(task) }, Modifier.size(38.dp)) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                IconButton({ onDelete(task) }, Modifier.size(38.dp)) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f)) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing here yet 🌷", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text("Add one small thing and make the day lighter.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Spacer(Modifier.height(12.dp))
            TextButton(onAdd) { Text("Add a task") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(existing: BloomTask?, onDismiss: () -> Unit, onSave: (BloomTask) -> Unit) {
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Personal") }
    var due by remember(existing?.id) { mutableStateOf(runCatching { LocalDate.parse(existing?.due ?: "") }.getOrDefault(LocalDate.now())) }
    var time by remember(existing?.id) { mutableStateOf(runCatching { LocalTime.parse(existing?.time ?: "09:00") }.getOrDefault(LocalTime.of(9, 0))) }
    var reminder by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes ?: 0) }
    var recurrence by remember(existing?.id) { mutableStateOf(existing?.recurrence ?: Recurrence.NONE) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(if (existing == null) "New task" else "Edit task", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("What needs doing?") }, maxLines = 4, shape = RoundedCornerShape(18.dp)) }
            item {
                Text("Category", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Personal", "Work", "Health").forEach { FilterChip(category == it, { category = it }, { Text(it) }) } }
            }
            item {
                Text("Date & time", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({
                        DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d) }, due.year, due.monthValue - 1, due.dayOfMonth).show()
                    }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(due.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                    }
                    OutlinedButton({
                        TimePickerDialog(context, { _, h, min -> time = LocalTime.of(h, min) }, time.hour, time.minute, false).show()
                    }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(time.format(DateTimeFormatter.ofPattern("h:mm a")))
                    }
                }
            }
            item {
                Text("Repeat", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.entries.forEach { value ->
                        FilterChip(recurrence == value, { recurrence = value }, { Text(value.label(), fontSize = 12.sp) })
                    }
                }
                if (recurrence != Recurrence.NONE) {
                    Spacer(Modifier.height(7.dp))
                    Text(recurrenceHint(recurrence, due, time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
            }
            item {
                Text("Remind me", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderChip("On time", 0, reminder) { reminder = it }; ReminderChip("15 min early", 15, reminder) { reminder = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderChip("30 min early", 30, reminder) { reminder = it }; ReminderChip("1 hour early", 60, reminder) { reminder = it }
                    }
                }
            }
            item {
                Button({
                    if (title.isNotBlank()) onSave(
                        BloomTask(
                            id = existing?.id ?: System.currentTimeMillis(),
                            title = title.trim(), category = category, due = due.toString(),
                            time = time.format(DateTimeFormatter.ofPattern("HH:mm")), reminderMinutes = reminder,
                            recurrence = recurrence, done = existing?.done ?: false, lastCompleted = existing?.lastCompleted ?: ""
                        )
                    )
                }, Modifier.fillMaxWidth().height(52.dp), enabled = title.isNotBlank(), shape = RoundedCornerShape(18.dp)) {
                    Text(if (existing == null) "Add task" else "Save changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReminderChip(label: String, minutes: Int, selected: Int, onSelect: (Int) -> Unit) {
    FilterChip(selected == minutes, { onSelect(minutes) }, { Text(label, fontSize = 12.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    userName: String,
    darkMode: Boolean,
    reminderMode: String,
    alarmUri: String,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onReminderModeChange: (String) -> Unit,
    onAlarmUriChange: (String) -> Unit
) {
    val context = LocalContext.current
    var nameDraft by remember(userName) { mutableStateOf(userName) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) onAlarmUriChange(uri.toString())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold) }
            item {
                OutlinedTextField(nameDraft, { nameDraft = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(18.dp), trailingIcon = {
                    IconButton({ if (nameDraft.isNotBlank()) onNameChange(nameDraft.trim()) }) { Icon(Icons.Default.Check, "Save name") }
                })
            }
            item {
                SettingsCard("Appearance") {
                    SettingsChoice(Icons.Default.LightMode, "Light mode", selected = !darkMode) { onDarkModeChange(false) }
                    SettingsChoice(Icons.Default.DarkMode, "Dark mode", selected = darkMode) { onDarkModeChange(true) }
                }
            }
            item {
                SettingsCard("Reminder style") {
                    SettingsChoice(Icons.Default.Notifications, "Normal notification", "Standard task notification", reminderMode == "notification") { onReminderModeChange("notification") }
                    SettingsChoice(Icons.Default.Alarm, "Alarm", "Louder sound for important tasks", reminderMode == "alarm") { onReminderModeChange("alarm") }
                }
            }
            if (reminderMode == "alarm") item {
                SettingsCard("Alarm sound") {
                    SettingsChoice(Icons.Default.Alarm, "Built-in alarm tone", "Uses your phone’s default alarm sound", alarmUri.isBlank()) { onAlarmUriChange("") }
                    Row(Modifier.fillMaxWidth().clickable {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (alarmUri.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmUri))
                        }
                        ringtoneLauncher.launch(intent)
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("Choose from phone", fontWeight = FontWeight.SemiBold); Text(if (alarmUri.isBlank()) "Pick a ringtone or sound" else "Custom sound selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
                        RadioButton(alarmUri.isNotBlank(), null)
                    }
                }
            }
            item {
                OutlinedButton({ context.startActivity(Intent(context, AboutPrivacyActivity::class.java)) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text("About & Privacy")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(8.dp)); content() }
    }
}

@Composable
private fun SettingsChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
        RadioButton(selected, onClick)
    }
}

private fun categoryColor(category: String): Color = when (category) { "Work" -> Color(0xFF6F7FDB); "Health" -> Color(0xFF5CB896); else -> Color(0xFFD27EAA) }

private fun dueLabel(due: String): String {
    val date = runCatching { LocalDate.parse(due) }.getOrNull() ?: return due
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.isBefore(today) -> "Overdue • ${date.format(DateTimeFormatter.ofPattern("d MMM"))}"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

private fun reminderLabel(minutes: Int): String = when (minutes) { 0 -> "Reminder at task time"; 15 -> "Reminder 15 min before"; 30 -> "Reminder 30 min before"; 60 -> "Reminder 1 hour before"; else -> "Reminder set" }

private fun recurrenceHint(recurrence: Recurrence, due: LocalDate, time: LocalTime): String {
    val at = time.format(DateTimeFormatter.ofPattern("h:mm a"))
    return when (recurrence) {
        Recurrence.NONE -> "Runs once"
        Recurrence.DAILY -> "Repeats every day at $at"
        Recurrence.WEEKLY -> "Repeats every ${due.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} at $at"
        Recurrence.YEARLY -> "Repeats every ${due.format(DateTimeFormatter.ofPattern("d MMMM"))} at $at"
    }
}
