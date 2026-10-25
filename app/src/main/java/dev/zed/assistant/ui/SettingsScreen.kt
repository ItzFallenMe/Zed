package dev.zed.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.theme.ZedCyan
import dev.zed.assistant.ui.theme.ZedGold
import dev.zed.assistant.ui.theme.ZedMuted

@Composable
fun SettingsScreen(
    prefs: ZedPrefs,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onRequestProjection: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var wakePhrase by remember { mutableStateOf(prefs.wakePhrase) }
    var liveVoice by remember { mutableStateOf(prefs.liveVoice) }
    var cloudBrain by remember { mutableStateOf(prefs.cloudBrain) }
    var jarvisMode by remember { mutableStateOf(prefs.jarvisMode) }
    var speakReplies by remember { mutableStateOf(prefs.speakReplies) }
    var keepHistory by remember { mutableStateOf(prefs.keepHistory) }
    var sensitivity by remember { mutableStateOf(prefs.motionSensitivity.toFloat()) }
    var armed by remember { mutableStateOf(ZedState.antiTheftArmed.value) }
    val logs by ZedState.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = ZedMuted) }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SYSTEMS",
                color = ZedCyan,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSetup) { Text("Permissions", color = ZedMuted) }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle("Gemini live voice")
        GlassCard {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    prefs.apiKey = it
                },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    prefs.model = it
                },
                label = { Text("Live model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The key is stored only in this app's private storage. Without it, Zed " +
                    "still runs every offline command through the on-device parser.",
                color = ZedMuted,
                fontSize = 11.sp
            )
            ToggleRow("Live conversation", liveVoice) {
                liveVoice = it
                prefs.liveVoice = it
            }
            ToggleRow("Allow cloud reasoning", cloudBrain) {
                cloudBrain = it
                prefs.cloudBrain = it
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Wake word & persona")
        GlassCard {
            OutlinedTextField(
                value = wakePhrase,
                onValueChange = {
                    wakePhrase = it
                    prefs.wakePhrase = it
                },
                label = { Text("Wake phrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Language", color = ZedMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("हिंदी + English", prefs.language == "hi-IN") {
                    prefs.language = "hi-IN"
                    ZedCoreService.reload(context)
                }
                LanguageChip("English", prefs.language == "en-IN") {
                    prefs.language = "en-IN"
                    ZedCoreService.reload(context)
                }
            }
            ToggleRow("JARVIS persona", jarvisMode) {
                jarvisMode = it
                prefs.jarvisMode = it
            }
            ToggleRow("Speak replies", speakReplies) {
                speakReplies = it
                prefs.speakReplies = it
            }
            ToggleRow("Keep local chat history", keepHistory) {
                keepHistory = it
                prefs.keepHistory = it
                if (!it) ZedState.clearChat()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Screen vision")
        GlassCard {
            Text(
                text = "Screen vision uses Android's screen-capture consent and on-device OCR. " +
                    "It is only active while you keep it on.",
                color = ZedMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequestProjection,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyan,
                        contentColor = Color(0xFF04121A)
                    )
                ) { Text("Start vision", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { ZedCoreService.stopProjection(context) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Stop", fontSize = 12.sp) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Anti-theft guard")
        GlassCard {
            ToggleRow("Guard armed", armed) { value ->
                armed = value
                prefs.antiTheftArmed = value
                if (value) AntiTheftService.arm(context) else AntiTheftService.disarm(context)
            }
            Text(
                text = "Motion sensitivity",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = { prefs.motionSensitivity = sensitivity.toInt() },
                valueRange = 1f..3f,
                steps = 1
            )
            Text(
                text = when (sensitivity.toInt()) {
                    1 -> "Low: only real movement triggers it"
                    3 -> "High: sensitive to any nudge"
                    else -> "Balanced"
                },
                color = ZedMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { AntiTheftService.requestTest(context) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Test siren", fontSize = 12.sp) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Diagnostics")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Service",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { ZedCoreService.start(context) }) {
                    Text("Start", color = ZedCyan, fontSize = 12.sp)
                }
                TextButton(onClick = { ZedCoreService.stop(context) }) {
                    Text("Stop", color = ZedGold, fontSize = 12.sp)
                }
            }
            Button(
                onClick = {
                    ZedCoreService.sendCommand(
                        context,
                        "read notifications"
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Speak notifications", fontSize = 12.sp) }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(12.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                if (logs.isEmpty()) {
                    Text("No events yet.", color = ZedMuted, fontSize = 11.sp)
                }
                logs.takeLast(40).reversed().forEach { line ->
                    Text(
                        text = "[${line.tag}] ${line.text}",
                        color = ZedMuted,
                        fontSize = 10.sp
                    )
                }
            }
            TextButton(onClick = { ZedState.clearLogs() }) {
                Text("Clear log", color = ZedMuted, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Zed · open source · all recognition and OCR runs on device. " +
                "Gemini Live sends audio to Google only while a conversation is open.",
            color = ZedMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZedCyan,
                checkedTrackColor = ZedCyan.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ZedCyan.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) ZedCyan else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontSize = 11.sp)
    }
}
