package com.samruddhi.bloom

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra("title") ?: "Bloom task"
        val body = intent.getStringExtra("body") ?: "It’s time"
        val taskId = intent.getLongExtra("taskId", 0L)
        startAlarmSound()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF8D63DE),
                    background = Color(0xFFFFF8FC),
                    surface = Color.White,
                    onSurface = Color(0xFF2D2636)
                )
            ) {
                AlarmScreen(title, body) {
                    stopAlarm()
                    NotificationManagerCompat.from(this).cancel(taskId.hashCode())
                    finish()
                }
            }
        }
    }

    private fun startAlarmSound() {
        val prefs = getSharedPreferences("bloom_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("alarm_uri", "") ?: ""
        val uri = if (saved.isBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } else {
            Uri.parse(saved)
        }

        ringtone = runCatching { RingtoneManager.getRingtone(this, uri) }.getOrNull()
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
        ringtone?.play()
    }

    private fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(title: String, body: String, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF4E9FF), Color(0xFFFFEAF2), Color(0xFFFFF7EF))
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(64.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("Bloom alarm", fontSize = 15.sp, color = Color(0xFF817985))
                Spacer(Modifier.height(8.dp))
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(body, fontSize = 15.sp, color = Color(0xFF817985))
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Stop alarm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
