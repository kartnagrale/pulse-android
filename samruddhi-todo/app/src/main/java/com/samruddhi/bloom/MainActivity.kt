package com.samruddhi.bloom

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BloomApp() }
    }
}

data class BloomTask(
    val id: Long,
    val title: String,
    val category: String,
    val due: String,
    val time: String = "09:00",
    val reminderMinutes: Int = 0,
    val done: Boolean = false
)

private const val PREFS = "bloom_prefs"
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
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
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
        tasks.addAll(loadTasks(context))
        loaded = true
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(colorScheme = if (darkMode) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (loaded) {
                HomeScreen(
                    userName = userName.ifBlank { "there" },
                    tasks = tasks,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onToggle = { task ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) {
                            val updated = task.copy(done = !task.done)
                            tasks[index] = updated
                            saveTasks(context, tasks)
                            if (updated.done) cancelTaskReminder(context, updated.id)
                            else scheduleTaskReminder(context, updated)
                        }
                    },
                    onDelete = { task ->
                        cancelTaskReminder(context, task.id)
                        tasks.removeAll { it.id == task.id }
                        saveTasks(context, tasks)
                    },
                    onEdit = { task ->
                        editingTask = task
                        showEditor = true
                    },
                    onAdd = {
                        editingTask = null
                        showEditor = true
                    },
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
                    onDismiss = {
                        showEditor = false
                        editingTask = null
                    },
                    onSave = { task ->
                        cancelTaskReminder(context, task.id)
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) tasks[index] = task else tasks.add(0, task)
                        saveTasks(context, tasks)
                        if (!task.done) scheduleTaskReminder(context, task)
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
                    onNameChange = { newName ->
                        userName = newName
                        prefs.edit().putString(KEY_NAME, newName).apply()
                    },
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        prefs.edit().putBoolean(KEY_DARK, enabled).apply()
                    },
                    onReminderModeChange = { mode ->
                        reminderMode = mode
                        prefs.edit().putString(KEY_REMINDER_MODE, mode).apply()
                    },
                    onAlarmUriChange = { uri ->
                        alarmUri = uri
                        prefs.edit().putString(KEY_ALARM_URI, uri).apply()
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF7FB), Color(0xFFF6F0FF), Color(0xFFFFFBF7))
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(26.dp)) {
                Text("Welcome to Bloom 🌷", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text("A calmer place for your day. What should I call you?", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { if (name.isNotBlank()) onContinue(name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
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
        "Today" -> tasks.filter { it.due == today.toString() }
        "Upcoming" -> tasks.filter { runCatching { LocalDate.parse(it.due).isAfter(today) }.getOrDefault(false) }
        else -> tasks
    }.sortedWith(compareBy<BloomTask> { it.done }.thenBy { it.due }.thenBy { it.time })

    val todayTasks = tasks.filter { it.due == today.toString() }
    val completedToday = todayTasks.count { it.done }
    val progress = if (todayTasks.isEmpty()) 0f else completedToday.toFloat() / todayTasks.size

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Header(userName, onSettings) }
            item { ProgressCard(todayTasks.size, completedToday, progress) }
            item { TabRow(selectedTab, onTabChange) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (selectedTab) {
                            "Today" -> "Today’s plan"
                            "Upcoming" -> "Coming up"
                            else -> "All tasks"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("${visibleTasks.count { !it.done }} left", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            }

            if (visibleTasks.isEmpty()) {
                item { EmptyState(onAdd) }
            } else {
                items(visibleTasks, key = { it.id }) { task ->
                    TaskCard(task, onToggle, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun Header(userName: String, onSettings: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$greeting,\n$userName ✨",
                fontSize = 31.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(LocalDate.now().format(formatter), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}

@Composable
private fun ProgressCard(total: Int, complete: Int, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        if (MaterialTheme.colorScheme.background == LightBg)
                            listOf(Color(0xFFEFE4FF), Color(0xFFFFE8F1), Color(0xFFFFEEE5))
                        else
                            listOf(Color(0xFF352849), Color(0xFF3B2731), Color(0xFF3A2B25))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Today’s rhythm", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (total == 0) "A fresh page awaits." else "$complete of $total tasks complete",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .7f)
                )
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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: BloomTask,
    onToggle: (BloomTask) -> Unit,
    onEdit: (BloomTask) -> Unit,
    onDelete: (BloomTask) -> Unit
) {
    val accent = categoryColor(task.category)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(5.dp).height(74.dp).clip(CircleShape).background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Checkbox(checked = task.done, onCheckedChange = { onToggle(task) })
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (task.done) .48f else 1f)
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(task.category, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(9.dp))
                    Icon(Icons.Default.Event, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Spacer(Modifier.width(3.dp))
                    Text(dueLabel(task.due), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Spacer(Modifier.width(9.dp))
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Spacer(Modifier.width(3.dp))
                    Text(formatTime(task.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    reminderLabel(task.reminderMinutes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onEdit(task) }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing here yet 🌷", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text("Add one small thing and make the day lighter.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAdd) { Text("Add a task") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    existing: BloomTask?,
    onDismiss: () -> Unit,
    onSave: (BloomTask) -> Unit
) {
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Personal") }
    var due by remember(existing?.id) { mutableStateOf(runCatching { LocalDate.parse(existing?.due ?: "") }.getOrDefault(LocalDate.now())) }
    var time by remember(existing?.id) { mutableStateOf(runCatching { LocalTime.parse(existing?.time ?: "09:00") }.getOrDefault(LocalTime.of(9, 0))) }
    var reminder by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes ?: 0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(if (existing == null) "New task" else "Edit task", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs doing?") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                Text("Category", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Personal", "Work", "Health").forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
            }
            item {
                Text("When", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> due = LocalDate.of(y, m + 1, d) },
                                due.year,
                                due.monthValue - 1,
                                due.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(due.format(DateTimeFormatter.ofPattern("d MMM")))
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min -> time = LocalTime.of(h, min) },
                                time.hour,
                                time.minute,
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(time.format(DateTimeFormatter.ofPattern("h:mm a")))
                    }
                }
            }
            item {
                Text("Remind me", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderChip("On time", 0, reminder) { reminder = it }
                        ReminderChip("15 min early", 15, reminder) { reminder = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderChip("30 min early", 30, reminder) { reminder = it }
                        ReminderChip("1 hour early", 60, reminder) { reminder = it }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                BloomTask(
                                    id = existing?.id ?: System.currentTimeMillis(),
                                    title = title.trim(),
                                    category = category,
                                    due = due.toString(),
                                    time = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    reminderMinutes = reminder,
                                    done = existing?.done ?: false
                                )
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (existing == null) "Add task" else "Save changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReminderChip(label: String, minutes: Int, selected: Int, onSelect: (Int) -> Unit) {
    FilterChip(
        selected = selected == minutes,
        onClick = { onSelect(minutes) },
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
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
    var nameDraft by remember(userName) { mutableStateOf(userName) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) onAlarmUriChange(uri.toString())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold) }
            item {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    trailingIcon = {
                        IconButton(onClick = { if (nameDraft.isNotBlank()) onNameChange(nameDraft.trim()) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save name")
                        }
                    }
                )
            }
            item {
                SettingsCard(title = "Appearance") {
                    SettingsChoice(
                        icon = Icons.Default.LightMode,
                        title = "Light mode",
                        selected = !darkMode,
                        onClick = { onDarkModeChange(false) }
                    )
                    SettingsChoice(
                        icon = Icons.Default.DarkMode,
                        title = "Dark mode",
                        selected = darkMode,
                        onClick = { onDarkModeChange(true) }
                    )
                }
            }
            item {
                SettingsCard(title = "Reminder style") {
                    SettingsChoice(
                        icon = Icons.Default.Notifications,
                        title = "Normal notification",
                        subtitle = "Quiet reminder like it works now",
                        selected = reminderMode == "notification",
                        onClick = { onReminderModeChange("notification") }
                    )
                    SettingsChoice(
                        icon = Icons.Default.Alarm,
                        title = "Alarm",
                        subtitle = "Louder full-screen reminder",
                        selected = reminderMode == "alarm",
                        onClick = { onReminderModeChange("alarm") }
                    )
                }
            }
            if (reminderMode == "alarm") {
                item {
                    SettingsCard(title = "Alarm sound") {
                        SettingsChoice(
                            icon = Icons.Default.Alarm,
                            title = "Built-in alarm tone",
                            subtitle = "Uses your phone’s default alarm sound",
                            selected = alarmUri.isBlank(),
                            onClick = { onAlarmUriChange("") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (alarmUri.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmUri))
                                }
                                ringtoneLauncher.launch(intent)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Choose from phone", fontWeight = FontWeight.SemiBold)
                                Text(if (alarmUri.isBlank()) "Pick any ringtone or sound" else "Custom sound selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                            }
                            RadioButton(selected = alarmUri.isNotBlank(), onClick = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun categoryColor(category: String): Color = when (category) {
    "Work" -> Color(0xFF6F7FDB)
    "Health" -> Color(0xFF5CB896)
    else -> Color(0xFFD27EAA)
}

private fun dueLabel(due: String): String {
    val date = runCatching { LocalDate.parse(due) }.getOrNull() ?: return due
    return when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

private fun formatTime(time: String): String = runCatching {
    LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(time)

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "Reminder at task time"
    15 -> "Reminder 15 min before"
    30 -> "Reminder 30 min before"
    60 -> "Reminder 1 hour before"
    else -> "Reminder set"
}

private fun saveTasks(context: Context, tasks: List<BloomTask>) {
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
                .put("done", task.done)
        )
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tasks", array.toString()).apply()
}

private fun loadTasks(context: Context): List<BloomTask> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tasks", null) ?: return emptyList()
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
                        done = o.optBoolean("done", false)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun createReminderChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("bloom_reminders", "Bloom reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders from Bloom"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel("bloom_alarms", "Bloom alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm-style task reminders"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
        )
    }
}

private fun scheduleTaskReminder(context: Context, task: BloomTask) {
    val date = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return
    val time = runCatching { LocalTime.parse(task.time) }.getOrNull() ?: return
    val triggerDateTime = LocalDateTime.of(date, time).minusMinutes(task.reminderMinutes.toLong())
    val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (triggerMillis <= System.currentTimeMillis()) return

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("taskId", task.id)
        putExtra("title", task.title)
        putExtra("dueTime", formatTime(task.time))
        putExtra("reminderMinutes", task.reminderMinutes)
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

private fun cancelTaskReminder(context: Context, taskId: Long) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) ?: return
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    pending.cancel()
}
