package com.samruddhi.bloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AboutPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AboutPrivacyScreen(onClose = { finish() }) }
    }
}

@Composable
private fun AboutPrivacyScreen(onClose: () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF8D63DE),
            background = Color(0xFFFFFAFC),
            surface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("About Bloom", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bloom 1.0.0", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                Text(
                    "Bloom is a simple offline-first daily planner for tasks, schedules and reminders.",
                    fontSize = 16.sp
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Privacy", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Bloom does not require an account and does not upload your task list to a server.")
                        Spacer(Modifier.height(8.dp))
                        Text("Your name, tasks, theme preference, reminder mode and selected alarm sound are stored locally on your device.")
                        Spacer(Modifier.height(8.dp))
                        Text("Notification permission is used only to show task reminders. Alarm and wake-related behavior is used only for reminder features you configure.")
                        Spacer(Modifier.height(8.dp))
                        Text("Bloom contains no advertising SDK, analytics SDK or third-party tracking SDK in version 1.0.0.")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Data deletion", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Because data is stored locally, uninstalling Bloom or clearing its app storage removes Bloom's locally stored data from the device.")
                    }
                }

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Bloom")
                }
            }
        }
    }
}
