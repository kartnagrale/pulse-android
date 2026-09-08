package com.kartik.pulse

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartik.pulse.ui.theme.PulseTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PulseTheme { PulseApp(applicationContext) } }
    }
}

enum class Screen { HOME, FOCUS, HISTORY }
enum class Energy(val label: String, val icon: String) { LOW("Low", "◔"), OKAY("Okay", "◑"), HIGH("High", "●") }
data class Sprint(val title: String, val subtitle: String, val minutes: Int)
data class HistoryItem(val title: String, val minutes: Int, val timestamp: Long)

class PulseStore(context: Context) {
    private val prefs = context.getSharedPreferences("pulse", Context.MODE_PRIVATE)
    fun save(item: HistoryItem) {
        val entries = prefs.getStringSet("history", emptySet())!!.toMutableSet()
        entries += "${item.timestamp}|${item.minutes}|${item.title.replace("|", " ")}"
        prefs.edit().putStringSet("history", entries).apply()
    }
    fun history(): List<HistoryItem> = prefs.getStringSet("history", emptySet())!!.mapNotNull {
        val p = it.split("|", limit = 3)
        if (p.size == 3) HistoryItem(p[2], p[1].toIntOrNull() ?: 0, p[0].toLongOrNull() ?: 0L) else null
    }.sortedByDescending { it.timestamp }
    fun streak(): Int {
        val days = history().map { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.timestamp)) }.toSet()
        var streak = 0
        val cal = Calendar.getInstance()
        repeat(365) {
            val key = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            if (key in days) streak++ else if (it > 0 || streak > 0) return streak
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }
}

@Composable fun PulseApp(context: Context) {
    val store = remember { PulseStore(context) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var activeSprint by remember { mutableStateOf<Sprint?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val history = remember(refresh) { store.history() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (screen != Screen.FOCUS) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(selected = screen == Screen.HOME, onClick = { screen = Screen.HOME }, icon = { Icon(Icons.Rounded.Bolt, null) }, label = { Text("Today") })
            NavigationBarItem(selected = screen == Screen.HISTORY, onClick = { screen = Screen.HISTORY }, icon = { Icon(Icons.Rounded.Insights, null) }, label = { Text("Progress") })
        }
    }) { padding ->
        AnimatedContent(targetState = screen, label = "screen") { target ->
            when (target) {
                Screen.HOME -> HomeScreen(Modifier.padding(padding), store.streak(), history.sumOf { it.minutes }) { activeSprint = it; screen = Screen.FOCUS }
                Screen.FOCUS -> FocusScreen(activeSprint ?: Sprint("One clear move", "Put everything else down and finish the smallest useful version.", 10), { screen = Screen.HOME }) {
                    store.save(HistoryItem(it.title, it.minutes, System.currentTimeMillis())); refresh++; screen = Screen.HISTORY
                }
                Screen.HISTORY -> HistoryScreen(Modifier.padding(padding), history, store.streak())
            }
        }
    }
}

@Composable fun HomeScreen(modifier: Modifier = Modifier, streak: Int, totalMinutes: Int, onStart: (Sprint) -> Unit) {
    var energy by remember { mutableStateOf(Energy.OKAY) }
    var thought by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Sprint>>(emptyList()) }
    val greeting = remember { when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(greeting, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text("Find your next move.", fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp) }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.Bolt, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Streak", if (streak == 1) "1 day" else "$streak days", Icons.Rounded.LocalFireDepartment)
            StatCard(Modifier.weight(1f), "Focused", "$totalMinutes min", Icons.Rounded.Timer)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("How much fuel do you have?", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Energy.entries.forEach { e -> FilterChip(selected = energy == e, onClick = { energy = e }, label = { Text("${e.icon}  ${e.label}") }) } }
                OutlinedTextField(value = thought, onValueChange = { thought = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, shape = RoundedCornerShape(18.dp), label = { Text("What's taking up space in your head?") }, placeholder = { Text("Ship a feature, study, reply to emails, clean up…") })
                Button(onClick = { suggestions = makeSprints(thought, energy) }, enabled = thought.trim().length >= 3, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Give me one clear move")
                }
            }
        }
        AnimatedVisibility(suggestions.isNotEmpty()) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Pick the one that feels easiest to start", fontWeight = FontWeight.Bold, fontSize = 19.sp); suggestions.forEachIndexed { index, sprint -> SprintCard(sprint, index == 0) { onStart(sprint) } } } }
        if (suggestions.isEmpty()) Text("Pulse doesn't build a perfect plan. It gives you a small move you can actually begin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

fun makeSprints(input: String, energy: Energy): List<Sprint> {
    val clean = input.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val base = when (energy) { Energy.LOW -> 7; Energy.OKAY -> 15; Energy.HIGH -> 25 }
    val lower = input.lowercase()
    val first = when {
        listOf("study", "learn", "read", "course", "exam").any { it in lower } -> Sprint("Open it and learn one chunk", "No notes system. No perfect setup. Read or practice the first meaningful section of: $clean", base)
        listOf("code", "bug", "feature", "project", "build").any { it in lower } -> Sprint("Make one visible change", "Open the project and produce the smallest working change related to: $clean", base)
        listOf("email", "reply", "message", "call").any { it in lower } -> Sprint("Close one communication loop", "Choose the most important person involved in '$clean' and send the useful reply now.", minOf(base, 15))
        listOf("clean", "room", "desk", "organize").any { it in lower } -> Sprint("Clear one surface completely", "Ignore the whole space. Pick one visible area connected to '$clean' and finish only that.", minOf(base, 15))
        else -> Sprint("Create the ugly first version", "Spend $base minutes making a concrete first pass at: $clean", base)
    }
    return listOf(first, Sprint("Shrink it until it's easy", "Do only the first 2-minute action that makes '$clean' easier to continue later.", 2), Sprint("Deep push", "Put the phone away, remove one distraction, and work only on '$clean' until the timer ends.", maxOf(15, base)))
}

@Composable fun StatCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column { Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } } }
}

@Composable fun SprintCard(sprint: Sprint, recommended: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (recommended) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(sprint.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp); Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.background.copy(alpha = .5f)) { Text("${sprint.minutes} min", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 12.sp) } }
            Text(sprint.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) { if (recommended) Text("BEST FIRST MOVE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(20.dp)) }
        }
    }
}

@Composable fun FocusScreen(sprint: Sprint, onClose: () -> Unit, onComplete: (Sprint) -> Unit) {
    val total = sprint.minutes * 60
    var remaining by remember(sprint) { mutableIntStateOf(total) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(running, remaining) { if (running && remaining > 0) { delay(1000); remaining-- } }
    LaunchedEffect(remaining) { if (remaining == 0) running = false }
    val progress = if (total == 0) 1f else 1f - remaining.toFloat() / total
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }; Spacer(Modifier.weight(1f)); Text("FOCUS MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.weight(1f)); Spacer(Modifier.size(48.dp)) }
        Spacer(Modifier.height(26.dp)); Text(sprint.title, fontWeight = FontWeight.Bold, fontSize = 27.sp, textAlign = TextAlign.Center, lineHeight = 31.sp); Spacer(Modifier.height(12.dp)); Text(sprint.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp); Spacer(Modifier.weight(.7f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) { CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 14.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60), fontSize = 46.sp, fontWeight = FontWeight.Bold); Text(if (running) "Stay with this" else if (remaining == 0) "Nice. Close the loop." else "Paused", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = { running = !running }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) { Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (running) "Pause" else "Resume") }; Button(onClick = { onComplete(sprint) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(7.dp)); Text("Done") } }
        Spacer(Modifier.height(18.dp)); Text("You can finish early. The goal is progress, not obeying a timer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable fun HistoryScreen(modifier: Modifier, history: List<HistoryItem>, streak: Int) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(4.dp)); Text("Progress", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Proof that you started beats a perfect productivity system.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column { Text(if (streak == 1) "1 day" else "$streak day streak", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Keep the chain lightweight.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (history.isEmpty()) Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Timeline, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Text("Nothing here yet", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Complete your first focus sprint and it will show up here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else { Text("Recent wins", fontWeight = FontWeight.Bold, fontSize = 19.sp); history.take(30).forEach { item -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(item.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${item.minutes}m", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium) } } }
    }
}
