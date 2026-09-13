package com.kynurelabs.bloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AboutPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFAFC)) { AboutPrivacyScreen() }
            }
        }
    }
}

@Composable
private fun AboutPrivacyScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Bloom", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Version 1.4.1 • A focused productivity planner by Kynure Labs", color = Color(0xFF817985))
        }
        item { InfoCard("Privacy") { Text("Bloom is offline-first. Your name, tasks, subtasks, priorities, estimates, recurrence settings, reminder times, focus history, completion state, theme and reminder preferences are stored locally on your device.") } }
        item { InfoCard("Data collection") { Text("Bloom 1.4.1 has no account system, advertising SDK, analytics SDK or third-party tracking SDK. Your task list is not sent to a Kynure Labs server.") } }
        item { InfoCard("Notifications & reminders") { Text("Bloom requests notification permission only to show task reminders. Recurring reminders and snoozes are scheduled locally using Android alarm APIs and are restored after a device reboot.") } }
        item { InfoCard("Alarm sounds") { Text("If you choose Alarm mode, Bloom can use your phone’s default alarm sound or a sound you select using Android’s system ringtone picker.") } }
        item { InfoCard("Focus & statistics") { Text("Focus sessions and productivity statistics are calculated from locally stored task history. Bloom does not upload this productivity data.") } }
        item { InfoCard("Delete your data") { Text("Delete individual tasks inside Bloom, or clear the app’s storage / uninstall Bloom to remove locally stored app data from the device.") } }
        item { InfoCard("Support") { Text("KynureLabs.dev@gmail.com") } }
        item { Text("Built by Kynure Labs • com.kynurelabs.bloom", modifier = Modifier.padding(top = 8.dp), color = Color(0xFF817985), fontSize = 13.sp) }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            content()
        }
    }
}
