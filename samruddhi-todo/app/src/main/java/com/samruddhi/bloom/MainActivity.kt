package com.samruddhi.bloom

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
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

data class BloomTask(
    val id: Long,
    val title: String,
    val category: String,
    val due: String,
    val done: Boolean = false
)

private val BloomPurple = Color(0xFF8E68D8)
private val BloomPink = Color(0xFFF4A8C2)
private val BloomPeach = Color(0xFFFFD7C2)
private val BloomInk = Color(0xFF2D2636)
private val BloomMuted = Color(0xFF7C7484)
private val BloomBg = Color(0xFFFFF9FC)
private val BloomCard = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = BloomPurple,
    onPrimary = Color.White,
    secondary = BloomPink,
    background = BloomBg,
    surface = BloomCard,
    onSurface = BloomInk,
    onBackground = BloomInk
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCCB4FF),
    secondary = Color(0xFFFFB7D0),
    background = Color(0xFF17141B),
    surface = Color(0xFF211D26),
    onSurface = Color(0xFFF2ECF5),
    onBackground = Color(0xFFF2ECF5)
)

@Composable
fun BloomApp() {
    val context = LocalContext.current
    val tasks = remember { mutableStateListOf<BloomTask>() }
    var loaded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Today") }

    LaunchedEffect(Unit) {
        tasks.addAll(loadTasks(context))
        if (tasks.isEmpty()) {
            tasks.addAll(seedTasks())
            saveTasks(context, tasks)
        }
        loaded = true
    }

    MaterialTheme(colorScheme = LightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (loaded) {
                HomeScreen(
                    tasks = tasks,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onToggle = { task ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) {
                            tasks[index] = task.copy(done = !task.done)
                            saveTasks(context, tasks)
                        }
                    },
                    onDelete = { task ->
                        tasks.removeAll { it.id == task.id }
                        saveTasks(context, tasks)
                    },
                    onAdd = { showAdd = true }
                )
            }

            if (showAdd) {
                AddTaskSheet(
                    onDismiss = { showAdd = false },
                    onCreate = { title, category, due ->
                        tasks.add(
                            0,
                            BloomTask(
                                id = System.currentTimeMillis(),
                                title = title.trim(),
                                category = category,
                                due = due.toString()
                            )
                        )
                        saveTasks(context, tasks)
                        selectedTab = if (due == LocalDate.now()) "Today" else "Upcoming"
                        showAdd = false
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
    onDelete: (BloomTask) -> Unit,
    onAdd: () -> Unit
) {
    val today = LocalDate.now()
    val visibleTasks = when (selectedTab) {
        "Today" -> tasks.filter { it.due == today.toString() }
        "Upcoming" -> tasks.filter { LocalDate.parse(it.due).isAfter(today) }
        else -> tasks
    }.sortedWith(compareBy<BloomTask> { it.done }.thenBy { it.due })

    val todayTasks = tasks.filter { it.due == today.toString() }
    val completedToday = todayTasks.count { it.done }
    val progress = if (todayTasks.isEmpty()) 0f else completedToday.toFloat() / todayTasks.size

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = BloomPurple,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF8FC), Color(0xFFFFFCF7), Color(0xFFF8F5FF))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Header() }
                item { ProgressCard(todayTasks.size, completedToday, progress) }
                item { TabRow(selectedTab, onTabChange) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedTab) {
                                "Today" -> "Today’s little wins"
                                "Upcoming" -> "Coming up"
                                else -> "Everything"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BloomInk
                        )
                        Text(
                            text = "${visibleTasks.count { !it.done }} left",
                            fontSize = 13.sp,
                            color = BloomMuted
                        )
                    }
                }

                if (visibleTasks.isEmpty()) {
                    item { EmptyState(selectedTab, onAdd) }
                } else {
                    items(visibleTasks, key = { it.id }) { task ->
                        TaskCard(task, onToggle, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "$greeting, Samruddhi ✨",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BloomInk
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = LocalDate.now().format(formatter),
                fontSize = 14.sp,
                color = BloomMuted
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(BloomPurple.copy(alpha = .22f), BloomPink.copy(alpha = .32f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Spa, contentDescription = null, tint = BloomPurple)
        }
    }
}

@Composable
private fun ProgressCard(total: Int, complete: Int, progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFEEE5FF), Color(0xFFFFE9F1), Color(0xFFFFEEE5))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Today’s rhythm", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BloomInk)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (total == 0) "A fresh page. Add something lovely." else "$complete of $total tasks complete",
                            color = BloomMuted,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = BloomPurple)
                    }
                }
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = BloomPurple,
                    trackColor = Color.White.copy(alpha = .65f)
                )
                AnimatedVisibility(visible = total > 0 && complete == total) {
                    Text(
                        "Beautiful — today is all wrapped up 🌷",
                        modifier = Modifier.padding(top = 12.dp),
                        color = BloomPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TabRow(selected: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("Today", "Upcoming", "All").forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onChange(tab) },
                label = { Text(tab, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BloomPurple,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = BloomMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == tab,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun TaskCard(task: BloomTask, onToggle: (BloomTask) -> Unit, onDelete: (BloomTask) -> Unit) {
    val accent = categoryColor(task.category)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggle(task) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle(task) }
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.done) BloomMuted else BloomInk,
                    fontSize = 16.sp,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.copy(alpha = .13f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(task.category, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.Event, contentDescription = null, tint = BloomMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dueLabel(task.due), color = BloomMuted, fontSize = 11.sp)
                }
            }
            if (task.done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = BloomPurple, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onDelete(task) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BloomMuted.copy(alpha = .75f))
            }
        }
    }
}

@Composable
private fun EmptyState(tab: String, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .82f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(BloomPink.copy(alpha = .18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = BloomPink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (tab == "Today") "Nothing on your plate" else "Clear skies ahead",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = BloomInk
            )
            Spacer(Modifier.height(5.dp))
            Text("Make space for what matters.", color = BloomMuted, fontSize = 13.sp)
            TextButton(onClick = onAdd) { Text("Add a task") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String, LocalDate) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    var dueOption by remember { mutableStateOf("Today") }
    val categories = listOf("Personal", "Work", "Study", "Wellness", "Errands")
    val dueOptions = listOf("Today", "Tomorrow", "This week")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BloomBg,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .padding(bottom = 26.dp)
        ) {
            Text("A new little win", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = BloomInk)
            Text("Keep it simple and kind to future-you.", color = BloomMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What needs doing?") },
                placeholder = { Text("e.g. Finish the presentation") },
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )
            Spacer(Modifier.height(18.dp))
            Text("Category", fontWeight = FontWeight.Bold, color = BloomInk)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(3).forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = categoryColor(item).copy(alpha = .2f),
                                selectedLabelColor = categoryColor(item)
                            )
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.drop(3).forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = categoryColor(item).copy(alpha = .2f),
                                selectedLabelColor = categoryColor(item)
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("When", fontWeight = FontWeight.Bold, color = BloomInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dueOptions.forEach { item ->
                    AssistChip(
                        onClick = { dueOption = item },
                        label = { Text(item) },
                        leadingIcon = if (dueOption == item) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val due = when (dueOption) {
                        "Tomorrow" -> LocalDate.now().plusDays(1)
                        "This week" -> LocalDate.now().plusDays(3)
                        else -> LocalDate.now()
                    }
                    if (title.isNotBlank()) onCreate(title, category, due)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Add to my day", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun categoryColor(category: String): Color = when (category) {
    "Work" -> Color(0xFF6C8DEB)
    "Study" -> Color(0xFF9D73E7)
    "Wellness" -> Color(0xFF5EAF92)
    "Errands" -> Color(0xFFE6A24B)
    else -> Color(0xFFE985AD)
}

private fun dueLabel(due: String): String {
    val date = LocalDate.parse(due)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

private fun seedTasks(): List<BloomTask> {
    val today = LocalDate.now()
    return listOf(
        BloomTask(101, "Take a calm 10-minute walk", "Wellness", today.toString()),
        BloomTask(102, "Reply to that important message", "Personal", today.toString()),
        BloomTask(103, "Plan tomorrow’s top 3", "Work", today.plusDays(1).toString())
    )
}

private fun saveTasks(context: Context, tasks: List<BloomTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(
            JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("category", task.category)
                put("due", task.due)
                put("done", task.done)
            }
        )
    }
    context.getSharedPreferences("bloom_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("tasks", array.toString())
        .apply()
}

private fun loadTasks(context: Context): List<BloomTask> {
    val raw = context.getSharedPreferences("bloom_prefs", Context.MODE_PRIVATE)
        .getString("tasks", null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    BloomTask(
                        id = item.getLong("id"),
                        title = item.getString("title"),
                        category = item.getString("category"),
                        due = item.getString("due"),
                        done = item.optBoolean("done", false)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
