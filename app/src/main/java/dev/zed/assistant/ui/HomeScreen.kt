package dev.zed.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.theme.ZedGlassBorder
import dev.zed.assistant.ui.theme.ZedMuted
import dev.zed.assistant.ui.theme.ZedWhite
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    prefs: ZedPrefs,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onRequestProjection: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listening by ZedState.listening.collectAsState()
    val speaking by ZedState.speaking.collectAsState()
    val thinking by ZedState.thinking.collectAsState()
    val status by ZedState.status.collectAsState()
    val partial by ZedState.partialText.collectAsState()
    val messages by ZedState.messages.collectAsState()
    val wakeActive by ZedState.wakeActive.collectAsState()
    val armed by ZedState.antiTheftArmed.collectAsState()
    val vision by ZedState.screenVisionActive.collectAsState()
    val enrolling by ZedState.enrolling.collectAsState()
    val speaker by ZedState.speaker.collectAsState()

    var wakeEnabled by remember { mutableStateOf(prefs.wakeEnabled) }
    var draft by remember { mutableStateOf("") }
    var showEnroll by remember { mutableStateOf(false) }
    var enrollName by remember { mutableStateOf(prefs.userName) }
    val listState = rememberLazyListState()

    val sendMessage: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotBlank()) {
            ZedCoreService.sendCommand(context, text)
            draft = ""
        }
    }

    LaunchedEffect(messages.size, partial) {
        if (messages.isNotEmpty()) listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
    }

    val mood = when {
        enrolling -> AvatarMood.THINKING
        armed -> AvatarMood.GUARDED
        thinking -> AvatarMood.THINKING
        speaking -> AvatarMood.SPEAKING
        listening -> AvatarMood.LISTENING
        else -> AvatarMood.IDLE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // ------------------------------------------------------------ header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.animateContentSize()) {
                    ZedAvatar(mood = mood, size = 54)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Zed",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZedWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        StatusDot(active = wakeActive, speaking = speaking)
                    }
                    Text(
                        text = when {
                            enrolling -> "Learning your voice…"
                            listening -> if (speaker.isNotBlank()) "Listening · $speaker" else "Listening…"
                            speaking -> "Speaking"
                            thinking -> "Thinking"
                            armed -> "Guard is watching"
                            else -> if (wakeActive) "Say \"${prefs.wakePhrase}\"" else "Tap the mic"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedMuted
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = ZedMuted
                    )
                }
            }

            // ------------------------------------------------------- transcript
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyState(
                            userName = prefs.userName,
                            onSuggestion = { text ->
                                draft = text
                                keyboard?.show()
                            }
                        )
                    }
                }
                items(messages, key = { it.timestamp + it.text.hashCode() }) { message ->
                    MessageBubble(message)
                }
                if (partial.isNotBlank()) {
                    item {
                        Text(
                            text = partial,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZedWhite.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(14.dp)
                        )
                    }
                }
                if (thinking) item { TypingIndicator() }
            }

            // --------------------------------------------------------- quick bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { QuickAction("Torch") { ZedCoreService.sendCommand(context, "torch on") } }
                item { QuickAction("Battery") { ZedCoreService.sendCommand(context, "battery") } }
                item {
                    QuickAction(if (vision) "Screen on" else "See screen") {
                        if (vision) ZedCoreService.stopProjection(context) else onRequestProjection()
                    }
                }
                item {
                    QuickAction(if (armed) "Guard on" else "Guard") {
                        if (armed) AntiTheftService.disarm(context) else AntiTheftService.arm(context)
                    }
                }
                item { QuickAction(if (enrolling) "Learning…" else "My voice") { showEnroll = true } }
            }

            // ----------------------------------------------------------- composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, ZedGlassBorder, RoundedCornerShape(28.dp))
                    .padding(start = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (!ZedPermissions.hasMic(context)) onOpenSetup()
                        else if (speaking) ZedCoreService.stopSpeaking(context)
                        else ZedCoreService.listen(context)
                    }
                ) {
                    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
                        initialValue = 1f,
                        targetValue = if (listening) 1.12f else 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "pulse"
                    )
                    Icon(
                        imageVector = when {
                            speaking -> Icons.Rounded.Stop
                            listening -> Icons.Rounded.GraphicEq
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = "Talk to Zed",
                        tint = if (listening || speaking) Color.Black else ZedWhite,
                        modifier = Modifier
                            .size(38.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(if (listening || speaking) ZedWhite else Color.White.copy(alpha = 0.08f))
                            .padding(9.dp)
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text("Message Zed…", color = Color.White.copy(alpha = 0.35f))
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage(draft) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        cursorColor = ZedWhite,
                        focusedTextColor = ZedWhite,
                        unfocusedTextColor = ZedWhite,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                val sendEnabled = draft.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (sendEnabled) ZedWhite else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable(enabled = sendEnabled) { sendMessage(draft) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (sendEnabled) Color.Black else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Wake word + permissions row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingRow(
                    title = "Hands-free",
                    subtitle = "Listen for \"${prefs.wakePhrase}\"",
                    checked = wakeEnabled,
                    onCheckedChange = { enabled ->
                        wakeEnabled = enabled
                        prefs.wakeEnabled = enabled
                        ZedCoreService.setWake(context, enabled)
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenSetup) {
                    Text("Access", style = MaterialTheme.typography.labelMedium, color = ZedMuted)
                }
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.28f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
    }

    if (showEnroll) {
        AlertDialog(
            onDismissRequest = { showEnroll = false },
            containerColor = Color(0xFF111111),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Teach Zed your voice", color = ZedWhite) },
            text = {
                Column {
                    Text(
                        text = "Zed will listen for four seconds while you say anything " +
                            "natural. The voiceprint stays on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enrollName,
                        onValueChange = { enrollName = it },
                        label = { Text("Your name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = ZedWhite,
                            focusedTextColor = ZedWhite,
                            unfocusedTextColor = ZedWhite,
                            focusedLabelColor = ZedMuted,
                            unfocusedLabelColor = ZedMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEnroll = false
                        if (ZedPermissions.hasMic(context)) {
                            ZedCoreService.enrollVoice(context, enrollName)
                        } else {
                            onOpenSetup()
                        }
                    }
                ) {
                    Text("Start learning", color = ZedWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnroll = false }) {
                    Text("Cancel", color = ZedMuted)
                }
            }
        )
    }
}

@Composable
private fun EmptyState(userName: String, onSuggestion: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = if (userName.isBlank()) "I'm here." else "Hey $userName.",
            style = MaterialTheme.typography.headlineMedium,
            color = ZedWhite
        )
        Text(
            text = "Talk to me like a person. I can run the phone, read what's on screen, " +
                "message people and remember what matters.",
            style = MaterialTheme.typography.bodyMedium,
            color = ZedMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        listOf(
            "Turn the torch on",
            "What's on my screen?",
            "Message mom on WhatsApp that I'm late",
            "It's too loud in here"
        ).forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZedWhite.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean, speaking: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(
                when {
                    speaking -> ZedWhite
                    active -> ZedWhite.copy(alpha = alpha)
                    else -> Color.White.copy(alpha = 0.22f)
                }
            )
    )
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val height by transition.animateFloat(
                initialValue = 4f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    tween(420, delayMillis = index * 120, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = height.dp)
                    .clip(CircleShape)
                    .background(ZedWhite.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ZedState.Message) {
    val isUser = message.role == ZedState.Role.USER
    if (message.role == ZedState.Role.SYSTEM) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.labelSmall,
            color = ZedMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        return
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(dampingRatio = 0.8f)
        ),
        exit = fadeOut() + slideOutVertically { it / 4 }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Z", style = MaterialTheme.typography.labelSmall, color = ZedWhite)
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = if (isUser) 22.dp else 6.dp,
                            bottomEnd = if (isUser) 6.dp else 22.dp
                        )
                    )
                    .background(
                        if (isUser) ZedWhite else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        1.dp,
                        if (isUser) Color.Transparent else ZedGlassBorder,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.Black else ZedWhite
                )
            }
        }
    }
}
