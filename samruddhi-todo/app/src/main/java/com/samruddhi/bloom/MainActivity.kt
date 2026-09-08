package com.samruddhi.bloom

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createReminderChannel(this)
        setContent { BloomApp() }
    }
}

data class BloomTask(
    val id: Long,
    val title: String,
    val category: String,
    val dueDate: String,
    val dueTime: String,
    val reminderMinutes: Int,
    val done: Boolean = false
)

private val BloomPurple = Color(0xFF8D6AD8)
private val BloomPink = Color(0xFFF3A6C3)
private val BloomInk = Color(0xFF2E2736)
private val BloomMuted = Color(0xFF7B7383)
private val BloomBg = Color(0xFFFFF9FC)

private val LightColors = lightColorScheme(
    primary = BloomPurple,
    onPrimary = Color.White,
    secondary = BloomPink,
    background = BloomBg,
    surface = Color.White,
    onSurface = BloomInk,
    onBackground = BloomInk
)

@Composable
fun BloomApp() {
    val context = LocalContext.current
    val tasks = remember { mutableStateListOf<BloomTask>() }
    var loaded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Today") }
    var editorTask by remember { mutableStateOf<BloomTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        tasks.addAll(loadTasks(context))
        loaded = true
    }

    MaterialTheme(colorScheme = LightColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (loaded) {
                HomeScreen(
                    tasks = tasks,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onToggle = { task ->
                        val i = tasks.indexOfFirst { it.id == task.id }
                        if (i >= 0) {
                            val updated = task.copy(done = !task.done)
                            tasks[i] = updated
                            if (updated.done) cancelReminder(context, updated.id) else scheduleReminder(context, updated)
                            saveTasks(context, tasks)
                        }
                    },
                    onEdit = { task -> editorTask = task; showEditor = true },
                    onDelete = { task ->
                        cancelReminder(context, task.id)
                        tasks.removeAll { it.id == task.id }
                        saveTasks(context, tasks)
                    },
                    onAdd = { editorTask = null; showEditor = true }
                )
            }

            if (showEditor) {
                TaskEditorSheet(
                    existing = editorTask,
                    onDismiss = { showEditor = false },
                    onSave = { edited ->
                        cancelReminder(context, edited.id)
                        val existingIndex = tasks.indexOfFirst { it.id == edited.id }
                        if (existingIndex >= 0) tasks[existingIndex] = edited else tasks.add(0, edited)
                        if (!edited.done) scheduleReminder(context, edited)
                        saveTasks(context, tasks)
                        selectedTab = if (edited.dueDate == LocalDate.now().toString()) "Today" else "Upcoming"
                        showEditor = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    tasks: List<BloomTask>,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onToggle: (BloomTask) -> Unit,
    onEdit: (BloomTask) -> Unit,
    onDelete: (BloomTask) -> Unit,
    onAdd: () -> Unit
) {
    val today = LocalDate.now()
    val visible = when (selectedTab) {
        "Today" -> tasks.filter { it.dueDate == today.toString() }
        "Upcoming" -> tasks.filter { safeDate(it.dueDate)?.isAfter(today) == true }
        else -> tasks
    }.sortedWith(compareBy<BloomTask> { it.done }.thenBy { it.dueDate }.thenBy { it.dueTime })

    val todayTasks = tasks.filter { it.dueDate == today.toString() }
    val completed = todayTasks.count { it.done }
    val progress = if (todayTasks.isEmpty()) 0f else completed.toFloat() / todayTasks.size

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = BloomPurple, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFFFF8FC), Color(0xFFFFFCF7), Color(0xFFF8F5FF)))
            ).padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Header() }
                item { ProgressCard(todayTasks.size, completed, progress) }
                item { TabRow(selectedTab, onTabChange) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedTab == "Today") "Today’s plan" else if (selectedTab == "Upcoming") "Coming up" else "All tasks", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BloomInk)
                        Text("${visible.count { !it.done }} left", fontSize = 13.sp, color = BloomMuted)
                    }
                }
                if (visible.isEmpty()) item { EmptyState(onAdd) }
                else items(visible, key = { it.id }) { task -> TaskCard(task, onToggle, onEdit, onDelete) }
            }
        }
    }
}

@Composable
private fun Header() {
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column {
            Text("$greeting, Samruddhi ✨", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = BloomInk)
            Spacer(Modifier.height(5.dp))
            Text(LocalDate.now().format(formatter), fontSize = 14.sp, color = BloomMuted)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BloomPurple.copy(.22f), BloomPink.copy(.32f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Spa, contentDescription = null, tint = BloomPurple)
        }
    }
}

@Composable
private fun ProgressCard(total: Int, complete: Int, progress: Float) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFEEE5FF), Color(0xFFFFE9F1), Color(0xFFFFEEE5)))).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Today’s rhythm", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BloomInk)
                    Text(if (total == 0) "A fresh page. Add your first task." else "$complete of $total tasks complete", color = BloomMuted, fontSize = 13.sp)
                }
                Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(.7f)), contentAlignment = Alignment.Center) {
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = BloomPurple)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = BloomPurple, trackColor = Color.White.copy(.7f))
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
                label = { Text(tab, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BloomPurple, selectedLabelColor = Color.White, containerColor = Color.White, labelColor = BloomMuted)
            )
        }
    }
}

@Composable
private fun TaskCard(task: BloomTask, onToggle: (BloomTask) -> Unit, onEdit: (BloomTask) -> Unit, onDelete: (BloomTask) -> Unit) {
    val accent = categoryColor(task.category)
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(62.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Checkbox(checked = task.done, onCheckedChange = { onToggle(task) })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).clickable { onEdit(task) }) {
                Text(task.title, fontWeight = FontWeight.SemiBold, color = if (task.done) BloomMuted else BloomInk, fontSize = 16.sp, textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(.13f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(task.category, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Event, null, tint = BloomMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(dueLabel(task.dueDate), color = BloomMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Schedule, null, tint = BloomMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(formatTime(task.dueTime), color = BloomMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, null, tint = BloomMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(reminderLabel(task.reminderMinutes), color = BloomMuted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = { onEdit(task) }) { Icon(Icons.Default.Edit, "Edit", tint = BloomPurple) }
            IconButton(onClick = { onDelete(task) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = BloomMuted) }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(.85f))) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Spa, null, tint = BloomPurple, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("Nothing here yet", fontWeight = FontWeight.Bold, color = BloomInk)
            Text("Make space for one small win.", color = BloomMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAdd) { Text("Add a task") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(existing: BloomTask?, onDismiss: () -> Unit, onSave: (BloomTask) -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Personal") }
    var dueDate by remember(existing?.id) { mutableStateOf(existing?.dueDate ?: LocalDate.now().toString()) }
    var dueTime by remember(existing?.id) { mutableStateOf(existing?.dueTime ?: "18:00") }
    var reminder by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes ?: 15) }
    val categories = listOf("Personal", "Work", "Study", "Health")
    val reminders = listOf(0, 15, 30, 60)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFFFFBFD)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(if (existing == null) "Plan something ✨" else "Edit task ✨", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = BloomInk)
                Text("Choose the day, exact time and when Bloom should remind you.", color = BloomMuted, fontSize = 13.sp)
            }
            item {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task") }, placeholder = { Text("What needs to happen?") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
            }
            item {
                Text("Category", fontWeight = FontWeight.SemiBold, color = BloomInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, fontSize = 12.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = categoryColor(c), selectedLabelColor = Color.White))
                    }
                }
            }
            item {
                Text("Date", fontWeight = FontWeight.SemiBold, color = BloomInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today" to LocalDate.now(), "Tomorrow" to LocalDate.now().plusDays(1), "+ 1 week" to LocalDate.now().plusWeeks(1)).forEach { (label, date) ->
                        FilterChip(selected = dueDate == date.toString(), onClick = { dueDate = date.toString() }, label = { Text(label) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Custom date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
            item {
                Text("Time", fontWeight = FontWeight.SemiBold, color = BloomInk)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("09:00", "13:00", "18:00", "21:00").forEach { t ->
                        FilterChip(selected = dueTime == t, onClick = { dueTime = t }, label = { Text(formatTime(t)) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dueTime, onValueChange = { dueTime = it }, label = { Text("Custom time (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
            item {
                Text("Remind me", fontWeight = FontWeight.SemiBold, color = BloomInk)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { value ->
                                FilterChip(selected = reminder == value, onClick = { reminder = value }, label = { Text(reminderOptionLabel(value)) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                val valid = title.isNotBlank() && safeDate(dueDate) != null && safeTime(dueTime) != null
                Button(
                    onClick = {
                        onSave(BloomTask(existing?.id ?: System.currentTimeMillis(), title.trim(), category, dueDate, dueTime, reminder, existing?.done ?: false))
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (existing == null) "Add task" else "Save changes", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun categoryColor(category: String): Color = when (category) {
    "Work" -> Color(0xFF6F7DD8)
    "Study" -> Color(0xFFCE7B9C)
    "Health" -> Color(0xFF63A68C)
    else -> Color(0xFF9B70D8)
}

private fun dueLabel(date: String): String {
    val d = safeDate(date) ?: return date
    val today = LocalDate.now()
    return when (d) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> d.format(DateTimeFormatter.ofPattern("d MMM")) }
}

private fun formatTime(time: String): String {
    val t = safeTime(time) ?: return time
    return t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "Reminder at task time"
    15 -> "15 min before"
    30 -> "30 min before"
    60 -> "1 hour before"
    else -> "$minutes min before"
}

private fun reminderOptionLabel(minutes: Int): String = when (minutes) {
    0 -> "On time"
    15 -> "15 min early"
    30 -> "30 min early"
    60 -> "1 hour early"
    else -> "$minutes min early"
}

private fun safeDate(value: String): LocalDate? = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { null }
private fun safeTime(value: String): LocalTime? = try { LocalTime.parse(value) } catch (_: DateTimeParseException) { null }

private fun saveTasks(context: Context, tasks: List<BloomTask>) {
    val array = JSONArray()
    tasks.forEach { t ->
        array.put(JSONObject().apply {
            put("id", t.id); put("title", t.title); put("category", t.category); put("dueDate", t.dueDate); put("dueTime", t.dueTime); put("reminderMinutes", t.reminderMinutes); put("done", t.done)
        })
    }
    context.getSharedPreferences("bloom_tasks", Context.MODE_PRIVATE).edit().putString("tasks", array.toString()).apply()
}

private fun loadTasks(context: Context): List<BloomTask> {
    val raw = context.getSharedPreferences("bloom_tasks", Context.MODE_PRIVATE).getString("tasks", null) ?: return emptyList()
    return try {
        val a = JSONArray(raw)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            BloomTask(
                id = o.getLong("id"),
                title = o.getString("title"),
                category = o.optString("category", "Personal"),
                dueDate = o.optString("dueDate", o.optString("due", LocalDate.now().toString())),
                dueTime = o.optString("dueTime", "18:00"),
                reminderMinutes = o.optInt("reminderMinutes", 15),
                done = o.optBoolean("done", false)
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun scheduleReminder(context: Context, task: BloomTask) {
    val date = safeDate(task.dueDate) ?: return
    val time = safeTime(task.dueTime) ?: return
    val trigger = LocalDateTime.of(date, time).minusMinutes(task.reminderMinutes.toLong())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (trigger <= System.currentTimeMillis()) return

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("taskId", task.id)
        putExtra("title", task.title)
        putExtra("dueTime", formatTime(task.dueTime))
        putExtra("reminderMinutes", task.reminderMinutes)
    }
    val pending = PendingIntent.getBroadcast(context, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}

private fun cancelReminder(context: Context, id: Long) {
    val pending = PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    if (pending != null) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }
}

fun createReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel("bloom_reminders", "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders for Bloom tasks"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
}
