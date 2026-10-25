package dev.zed.assistant.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.MemoryStore
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.theme.ZedMuted
import dev.zed.assistant.voice.TtsVoice
import dev.zed.assistant.voice.VoiceCatalog
import dev.zed.assistant.voice.VoiceProfileStore
import dev.zed.assistant.ui.theme.ZedWhite

@Composable
fun SettingsScreen(
    prefs: ZedPrefs,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onRequestProjection: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val memory = remember { MemoryStore(context) }

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var groqKey by remember { mutableStateOf(prefs.groqKey) }
    var userName by remember { mutableStateOf(prefs.userName) }
    var model by remember { mutableStateOf(prefs.model) }
    var wakePhrase by remember { mutableStateOf(prefs.wakePhrase) }
    var customInstructions by remember { mutableStateOf(prefs.customInstructions) }
    var liveConversation by remember { mutableStateOf(prefs.liveVoice) }
    var cloudBrain by remember { mutableStateOf(prefs.cloudBrain) }
    var speakReplies by remember { mutableStateOf(prefs.speakReplies) }
    var bestVoice by remember { mutableStateOf(prefs.bestVoice) }
    var keepHistory by remember { mutableStateOf(prefs.keepHistory) }
    var sensitivity by remember { mutableStateOf(prefs.motionSensitivity.toFloat()) }
    var armed by remember { mutableStateOf(ZedState.antiTheftArmed.value) }
    var floatingOn by remember { mutableStateOf(prefs.floatingBubble) }
    var screenStream by remember { mutableStateOf(prefs.screenStream) }
    var voiceId by remember { mutableStateOf(prefs.voiceIdEnabled) }
    val voiceStore = remember { VoiceProfileStore(context) }
    var enrolled by remember { mutableStateOf(voiceStore.names()) }
    var memoryCount by remember { mutableStateOf(memory.all().size) }
    var ttsVoice by remember { mutableStateOf(prefs.ttsVoiceName) }
    var liveVoice by remember { mutableStateOf(prefs.liveVoiceName) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var showLiveVoicePicker by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<List<TtsVoice>>(emptyList()) }
    val logs by ZedState.logs.collectAsState()
    val vision by ZedState.screenVisionActive.collectAsState()

    LaunchedEffect(showVoicePicker) {
        if (showVoicePicker && voices.isEmpty()) {
            VoiceCatalog.load(context) { loaded -> voices = loaded }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", style = MaterialTheme.typography.labelLarge, color = ZedMuted)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSetup, contentPadding = PaddingValues(0.dp)) {
                Text("Access", style = MaterialTheme.typography.labelLarge, color = ZedMuted)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("Voice model")
        SectionCard {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    prefs.apiKey = it
                },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") },
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Get a free key",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZedWhite
                )
            }
            Text(
                text = "Stored in this app's private storage and used with the free Gemini tier. " +
                    "Without a key, every offline command still works.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
            DividerLine()
            OutlinedTextField(
                value = groqKey,
                onValueChange = {
                    groqKey = it
                    prefs.groqKey = it
                    prefs.keysConfigured = it.isNotBlank() && prefs.apiKey.isNotBlank()
                },
                label = { Text("Groq API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { uriHandler.openUri("https://console.groq.com/keys") },
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Get a free Groq key",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZedWhite
                )
            }
            DividerLine()
            OutlinedTextField(
                value = userName,
                onValueChange = {
                    userName = it
                    prefs.userName = it
                },
                label = { Text("Your name") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Zed uses this to greet you and to know whose voice it is.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
            DividerLine()
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    prefs.model = it
                },
                label = { Text("Live model id") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingRow(
                title = "Live conversation",
                subtitle = "Streaming audio with function calling",
                checked = liveConversation
            ) {
                liveConversation = it
                prefs.liveVoice = it
            }
            SettingRow(
                title = "Cloud reasoning",
                subtitle = "Off means never connect, fully offline",
                checked = cloudBrain
            ) {
                cloudBrain = it
                prefs.cloudBrain = it
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Speech output")
        SectionCard {
            SettingRow(
                title = "Best available voice",
                subtitle = "Google speech services, highest quality voice",
                checked = bestVoice
            ) {
                bestVoice = it
                prefs.bestVoice = it
                ZedCoreService.reload(context)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System voice",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ttsVoice.ifBlank { "Automatic (best available)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedMuted
                    )
                }
                TextButton(onClick = { showVoicePicker = true }) {
                    Text("Choose", style = MaterialTheme.typography.labelMedium, color = ZedWhite)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live voice",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$liveVoice · Gemini Live",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedMuted
                    )
                }
                TextButton(onClick = { showLiveVoicePicker = true }) {
                    Text("Choose", style = MaterialTheme.typography.labelMedium, color = ZedWhite)
                }
            }
            SettingRow(title = "Speak replies", checked = speakReplies) {
                speakReplies = it
                prefs.speakReplies = it
            }
            SettingRow(
                title = "Keep chat history",
                subtitle = "Stored locally only",
                checked = keepHistory
            ) {
                keepHistory = it
                prefs.keepHistory = it
                if (!it) ZedState.clearChat()
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Wake word & language")
        SectionCard {
            OutlinedTextField(
                value = wakePhrase,
                onValueChange = {
                    wakePhrase = it
                    prefs.wakePhrase = it
                },
                label = { Text("Wake phrase") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("हिंदी + English", prefs.language == "hi-IN") {
                    prefs.language = "hi-IN"
                    ZedCoreService.reload(context)
                }
                Pill("English", prefs.language == "en-IN") {
                    prefs.language = "en-IN"
                    ZedCoreService.reload(context)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Custom instructions")
        SectionCard {
            OutlinedTextField(
                value = customInstructions,
                onValueChange = {
                    customInstructions = it
                    prefs.customInstructions = it
                },
                label = { Text("How should Zed behave?") },
                placeholder = {
                    Text("Keep answers to one sentence. Call me Ved. Confirm before sending messages.")
                },
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Added to the system instruction of every live session. Takes effect on the " +
                    "next conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Screen vision")
        SectionCard {
            Text(
                text = if (vision) "Active. Zed can read, translate and act on this screen."
                else "Off. Screen capture needs your consent each time it starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingRow(
                title = "Hands-free sharing",
                subtitle = "Stream frames while you talk, no picking apps",
                checked = screenStream
            ) { value ->
                screenStream = value
                prefs.screenStream = value
            }
            Text(
                text = "Consent covers the whole session. From then on Zed sees whatever is on " +
                    "screen during a conversation, the way screen sharing works in Gemini.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequestProjection,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedWhite,
                        contentColor = Color.Black
                    )
                ) { Text("Start vision", style = MaterialTheme.typography.labelMedium) }
                Button(
                    onClick = { ZedCoreService.stopProjection(context) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = ZedWhite
                    )
                ) { Text("Stop", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Floating assistant")
        SectionCard {
            SettingRow(
                title = "Floating bubble",
                subtitle = "A tap-to-talk orb that floats above other apps",
                checked = floatingOn
            ) { value ->
                floatingOn = value
                prefs.floatingBubble = value
                if (value && !ZedPermissions.canDrawOverlays(context)) {
                    ZedPermissions.requestOverlayPermission(context)
                } else {
                    ZedCoreService.setBubble(context, value)
                }
            }
            Text(
                text = "Needs the \"display over other apps\" permission. The bubble shows live " +
                    "captions while Zed listens and speaks, and can be dragged anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Voice identity")
        SectionCard {
            SettingRow(
                title = "Recognise my voice",
                subtitle = "Zed greets the person who is actually talking",
                checked = voiceId
            ) { value ->
                voiceId = value
                prefs.voiceIdEnabled = value
            }
            if (enrolled.isEmpty()) {
                Text(
                    text = "No voices enrolled yet. Four seconds of speech is enough.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedMuted
                )
            } else {
                enrolled.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            voiceStore.remove(name)
                            enrolled = voiceStore.names()
                        }) {
                            Text("Forget", style = MaterialTheme.typography.labelMedium, color = ZedMuted)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ZedPermissions.hasMic(context)) {
                            ZedCoreService.enrollVoice(
                                context,
                                userName.ifBlank { prefs.userName }
                            )
                        } else {
                            onOpenSetup()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedWhite,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Teach my voice", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = "Stored on this phone only",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZedMuted,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Anti-theft guard")
        SectionCard {
            SettingRow(
                title = "Guard armed",
                subtitle = "Motion and charger detection with a loud siren",
                checked = armed
            ) { value ->
                armed = value
                prefs.antiTheftArmed = value
                if (value) AntiTheftService.arm(context) else AntiTheftService.disarm(context)
            }
            Text(
                text = "Motion sensitivity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = { prefs.motionSensitivity = sensitivity.toInt() },
                valueRange = 1f..3f,
                steps = 1,
                colors = SliderDefaults.colors(
                    thumbColor = ZedWhite,
                    activeTrackColor = ZedWhite,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                )
            )
            Text(
                text = when (sensitivity.toInt()) {
                    1 -> "Low: only real movement triggers it"
                    3 -> "High: sensitive to any nudge"
                    else -> "Balanced"
                },
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { AntiTheftService.requestTest(context) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = ZedWhite
                )
            ) { Text("Test siren", style = MaterialTheme.typography.labelMedium) }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Memory")
        SectionCard {
            Text(
                text = if (memoryCount == 0) "Nothing remembered yet. Say \"remember ...\"."
                else "$memoryCount remembered fact(s), stored locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ZedCoreService.sendCommand(context, "what do you remember") },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = ZedWhite
                    )
                ) { Text("Recall", style = MaterialTheme.typography.labelMedium) }
                Button(
                    onClick = {
                        memory.clear()
                        memoryCount = 0
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = ZedWhite
                    )
                ) { Text("Clear memory", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle("Diagnostics")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Assistant service",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { ZedCoreService.start(context) }) {
                    Text("Start", style = MaterialTheme.typography.labelMedium, color = ZedWhite)
                }
                TextButton(onClick = { ZedCoreService.stop(context) }) {
                    Text("Stop", style = MaterialTheme.typography.labelMedium, color = ZedMuted)
                }
            }
            TextButton(
                onClick = { ZedCoreService.sendCommand(context, "read notifications") },
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Read notifications aloud",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZedWhite
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Column {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No events yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedMuted
                        )
                    }
                    logs.takeLast(40).reversed().forEach { line ->
                        Text(
                            text = "${line.tag} · ${line.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedMuted
                        )
                    }
                }
            }
            TextButton(onClick = { ZedState.clearLogs() }) {
                Text("Clear log", style = MaterialTheme.typography.labelMedium, color = ZedMuted)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Zed · MIT licensed · wake word, OCR and translation run on device. " +
                "Gemini audio is only streamed while a conversation is open.",
            style = MaterialTheme.typography.bodySmall,
            color = ZedMuted
        )
        TextButton(
            onClick = { uriHandler.openUri("https://github.com/ItzFallenMe") },
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            Text(
                text = "Created by ItzFallenMe",
                style = MaterialTheme.typography.labelMedium,
                color = ZedWhite
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            confirmButton = {
                TextButton(onClick = { showVoicePicker = false }) { Text("Close") }
            },
            title = { Text("System voice") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        VoiceOption(
                            title = "Automatic",
                            subtitle = "Highest quality for the language",
                            selected = ttsVoice.isBlank()
                        ) {
                            ttsVoice = ""
                            prefs.ttsVoiceName = ""
                            ZedCoreService.reload(context)
                            showVoicePicker = false
                        }
                    }
                    items(voices) { voice ->
                        VoiceOption(
                            title = voice.name,
                            subtitle = buildString {
                                append(voice.locale)
                                append(" · ")
                                append(voice.qualityLabel())
                                if (voice.network) append(" · network")
                            },
                            selected = ttsVoice == voice.name
                        ) {
                            ttsVoice = voice.name
                            prefs.ttsVoiceName = voice.name
                            ZedCoreService.reload(context)
                            showVoicePicker = false
                        }
                    }
                }
            }
        )
    }

    if (showLiveVoicePicker) {
        AlertDialog(
            onDismissRequest = { showLiveVoicePicker = false },
            confirmButton = {
                TextButton(onClick = { showLiveVoicePicker = false }) { Text("Close") }
            },
            title = { Text("Live voice") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(LIVE_VOICES) { name ->
                        VoiceOption(
                            title = name,
                            subtitle = "Gemini Live prebuilt voice",
                            selected = liveVoice == name
                        ) {
                            liveVoice = name
                            prefs.liveVoiceName = name
                            ZedCoreService.reload(context)
                            showLiveVoicePicker = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun VoiceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ZedMuted
            )
        }
    }
}

private val LIVE_VOICES = listOf(
    "Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr"
)

private val CircleShape = RoundedCornerShape(50)
