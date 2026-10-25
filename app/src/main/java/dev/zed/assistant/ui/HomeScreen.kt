package dev.zed.assistant.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.service.ZedCoreService

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
    var bubbleOn by remember { mutableStateOf(prefs.floatingBubble) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your phone, hands free",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                label = if (wakeActive) "Wake on" else "Wake off",
                active = wakeActive
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        VoiceHero(
            status = when {
                armed -> "Anti-theft guard is watching"
                listening -> "I am listening"
                speaking -> "Speaking"
                thinking -> "Working on it"
                else -> "Standing by"
            },
            hint = "Say \"${prefs.wakePhrase}\" anytime",
            listening = listening,
            speaking = speaking,
            thinking = thinking,
            armed = armed,
            onListen = {
                if (speaking) ZedCoreService.stopSpeaking(context)
                else ZedCoreService.listen(context)
            }
        )

        if (partial.isNotBlank()) {
            Text(
                text = partial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "Ask me to change a setting, read the screen, send a WhatsApp " +
                            "message or scroll a feed. I can also remember things for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(messages) { message -> MessageBubble(message) }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item { QuickAction("Torch") { ZedCoreService.sendCommand(context, "torch on") } }
            item { QuickAction("Battery") { ZedCoreService.sendCommand(context, "battery") } }
            item { QuickAction("Read screen") { ZedCoreService.sendCommand(context, "read screen") } }
            item { QuickAction("Status") { ZedCoreService.sendCommand(context, "status report") } }
            item { QuickAction("WhatsApp") { ZedCoreService.sendCommand(context, "open whatsapp") } }
            item {
                QuickAction(if (bubbleOn) "Bubble on" else "Floating bubble") {
                    if (!ZedPermissions.canDrawOverlays(context)) {
                        prefs.floatingBubble = true
                        bubbleOn = true
                        ZedPermissions.requestOverlayPermission(context)
                    } else {
                        val enable = !bubbleOn
                        bubbleOn = enable
                        prefs.floatingBubble = enable
                        ZedCoreService.setBubble(context, enable)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SectionCard(modifier = Modifier.padding(bottom = 14.dp)) {
            SettingRow(
                title = "Wake word",
                subtitle = "Listen for \"${prefs.wakePhrase}\" on device",
                checked = wakeEnabled,
                onCheckedChange = { enabled ->
                    wakeEnabled = enabled
                    prefs.wakeEnabled = enabled
                    ZedCoreService.setWake(context, enabled)
                    if (enabled) onOpenSetup()
                }
            )
            DividerLine()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Pill(
                        label = if (vision) "Vision on" else "Screen vision",
                        selected = vision
                    ) {
                        if (vision) ZedCoreService.stopProjection(context)
                        else onRequestProjection()
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Pill(
                        label = if (armed) "Guard armed" else "Anti-theft",
                        selected = armed
                    ) {
                        if (armed) AntiTheftService.disarm(context)
                        else AntiTheftService.arm(context)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Pill(label = "Permissions", selected = false) { onOpenSetup() }
                }
            }
        }

        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun MessageBubble(message: ZedState.Message) {
    val isUser = message.role == ZedState.Role.USER
    if (message.role == ZedState.Role.SYSTEM) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "You" else "Zed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
