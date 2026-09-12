package com.kartik.bloom

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
                Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFAFC)) {
                    AboutPrivacyScreen()
                }
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
            Text("Version 1.1.0 • A calm planner for tasks and recurring reminders", color = Color(0xFF817985))
        }
        item {
            InfoCard("Privacy") {
                Text("Bloom is offline-first. Your name, tasks, recurrence settings, reminder times, completion state, theme and reminder preferences are stored locally on your device.")
            }
        }
        item {
            InfoCard("Data collection") {
                Text("Bloom 1.1.0 has no account system, advertising SDK, analytics SDK or third-party tracking SDK. Your task list is not sent to a Bloom server.")
            }
        }
        item {
            InfoCard("Notifications & reminders") {
                Text("Bloom requests notification permission only to show task reminders. One-time, daily, weekly and yearly reminders are scheduled locally using Android alarm APIs. Reminders are restored after a device reboot.")
            }
        }
        item {
            InfoCard("Alarm sounds") {
                Text("If you choose Alarm mode, Bloom can use your phone’s default alarm sound or a sound you select using Android’s system ringtone picker.")
            }
        }
        item {
            InfoCard("Delete your data") {
                Text("Delete individual tasks inside Bloom, or clear the app’s storage / uninstall Bloom to remove locally stored app data from the device.")
            }
        }
        item {
            Text("Built by Kartik • Bloom 1.1.0", modifier = Modifier.padding(top = 8.dp), color = Color(0xFF817985), fontSize = 13.sp)
        }
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
