package dev.zed.assistant.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.theme.ZedCyan
import dev.zed.assistant.ui.theme.ZedGold
import dev.zed.assistant.ui.theme.ZedMuted
import dev.zed.assistant.ui.theme.ZedRed

@Composable
fun HomeScreen(
    prefs: ZedPrefs,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onRequestProjection: () -> Unit
) {
    val context = LocalContext.current
    val listening by ZedState.listening.collectAsState()
    val speaking by ZedState.speaking.collectAsState()
    val thinking by ZedState.thinking.collectAsState()
    val status by ZedState.status.collectAsState()
    val partial by ZedState.partialText.collectAsState()
    val messages by ZedState.messages.collectAsState()
    val wakeActive by ZedState.wakeActive.collectAsState()
    val armed by ZedState.antiTheftArmed.collectAsState()
    val vision by ZedState.screenVisionActive.collectAsState()

    var wakeEnabled by remember { mutableStateOf(prefs.wakeEnabled) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZED",
                    color = ZedCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                )
                Text(
                    text = if (prefs.jarvisMode) "Just A Rather Very Intelligent System"
                    else "Your on-device assistant",
                    color = ZedMuted,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
            StatusChip(
                label = if (wakeActive) "WAKE ARMED" else "WAKE OFF",
                active = wakeActive
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = ZedMuted)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ReactorOrb(
                listening = listening,
                speaking = speaking,
                thinking = thinking,
                armed = armed,
                label = when {
                    armed -> "guard live"
                    speaking -> "speaking"
                    listening -> "listening"
                    thinking -> "working"
                    else -> "standby"
                },
                onClick = {
                    if (speaking) ZedCoreService.stopSpeaking(context)
                    else ZedCoreService.listen(context)
                }
            )
        }

        Text(
            text = status,
            color = ZedMuted,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (partial.isNotBlank()) {
            Text(
                text = "\"$partial\"",
                color = ZedCyan,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Say \"${prefs.wakePhrase}\" or tap the core.",
                            color = ZedMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Torch, Wi-Fi, Bluetooth, volume, brightness, alarms, apps, " +
                                "screen reading, translation and WhatsApp are all wired in.",
                            color = ZedMuted.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
                        )
                    }
                }
            }
            items(messages) { message -> MessageBubble(message) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip("Torch") { ZedCoreService.sendCommand(context, "torch on") }
            QuickActionChip("Read screen") { ZedCoreService.sendCommand(context, "read screen") }
            QuickActionChip("Battery") { ZedCoreService.sendCommand(context, "battery") }
            QuickActionChip("WhatsApp") { ZedCoreService.sendCommand(context, "open whatsapp") }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wake word",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Say \"${prefs.wakePhrase}\"",
                    color = ZedMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = wakeEnabled,
                onCheckedChange = { enabled ->
                    wakeEnabled = enabled
                    prefs.wakeEnabled = enabled
                    ZedCoreService.setWake(context, enabled)
                    if (enabled && !ZedPermissions.isAccessibilityEnabled(context)) onOpenSetup()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ZedCyan,
                    checkedTrackColor = ZedCyan.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (vision) ZedCoreService.stopProjection(context) else onRequestProjection() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vision) ZedCyan.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = if (vision) "Vision ON" else "Screen vision",
                    color = if (vision) ZedCyan else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    if (armed) AntiTheftService.disarm(context) else AntiTheftService.arm(context)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (armed) ZedRed.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = if (armed) "Guard ARMED" else "Anti-theft",
                    color = if (armed) ZedRed else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    if (speaking) ZedCoreService.stopSpeaking(context)
                    else ZedCoreService.listen(context)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (listening) ZedGold else ZedCyan,
                    contentColor = Color(0xFF04121A)
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (speaking) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = if (listening) "LISTENING" else "LISTEN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MessageBubble(message: ZedState.Message) {
    val isUser = message.role == ZedState.Role.USER
    if (message.role == ZedState.Role.SYSTEM) {
        Text(
            text = message.text,
            color = ZedMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isUser) ZedCyan.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = if (isUser) "YOU" else "ZED",
                color = if (isUser) ZedCyan else ZedGold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
    }
}
