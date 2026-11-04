@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import com.zed.assistant.ui.theme.Background
import com.zed.assistant.ui.theme.Border
import com.zed.assistant.ui.theme.Cards
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.PrimaryText
import com.zed.assistant.ui.theme.Purple
import com.zed.assistant.ui.theme.Success
import com.zed.assistant.ui.theme.Warning
import com.zed.assistant.viewmodel.MainViewModel

enum class ZedTab {
  HOME,
  CHAT,
  SETTINGS,
  UPDATES
}

@Composable
fun ZedHomeScreen(
  viewModel: MainViewModel,
  onChat: () -> Unit,
  onSettings: () -> Unit
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val status by viewModel.status.collectAsStateWithLifecycle()
  val level by viewModel.level.collectAsStateWithLifecycle()
  val userText by viewModel.userText.collectAsStateWithLifecycle()
  val zedText by viewModel.zedText.collectAsStateWithLifecycle()
  val actionNote by viewModel.actionNote.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val isActive by viewModel.isActive.collectAsStateWithLifecycle()
  val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
  var showLanguageInfo by rememberSaveable { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Background)
      .semantics { contentDescription = "Zed home" }
  ) {
    AmbientGlow(modifier = Modifier.align(Alignment.TopEnd).offset(x = 110.dp, y = (-100).dp), color = Purple)
    AmbientGlow(modifier = Modifier.align(Alignment.CenterStart).offset(x = (-130).dp, y = 50.dp), color = PrimaryRed)
    AmbientGlow(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 120.dp, y = 110.dp), color = Warning)

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(),
      contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 118.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        HomeHeader(state = state, onChat = onChat, onSettings = onSettings)
      }

      item {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier
              .size(250.dp)
              .combinedClickable(
                onClick = { viewModel.toggleSession() },
                onLongClick = { viewModel.interrupt() }
              )
              .semantics { contentDescription = "Tap or hold the Zed orb" },
            contentAlignment = Alignment.Center
          ) {
            OrbAnimationView(state = state, level = level)
          }
          WaveformView(
            level = level,
            active = state == AssistantState.LISTENING || state == AssistantState.SPEAKING
          )
          Spacer(modifier = Modifier.height(8.dp))
          StatusPill(state = state, status = status)
          Text(
            text = if (isActive) "Hold the orb or center button to interrupt" else "Tap the orb or center button to start",
            color = Hint,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp)
          )
        }
      }

      if (errorMessage != null) {
        item {
          ErrorCard(
            message = errorMessage.orEmpty(),
            onSettings = onSettings,
            onDismiss = viewModel::dismissError
          )
        }
      }

      if (userText.isNotBlank() || zedText.isNotBlank()) {
        item { TranscriptCard(userText = userText, zedText = zedText) }
      }

      item {
        LiveLinkCard(
          state = state,
          hasHistory = turns.isNotEmpty(),
          onClick = if (state == AssistantState.ERROR) onSettings else { { viewModel.toggleSession() } }
        )
      }

      item {
        Text(
          text = "QUICK ACTIONS",
          color = Hint,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.8.sp
        )
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          QuickActionCard(
            icon = Icons.Rounded.ScreenShare,
            title = "Device actions",
            subtitle = "Apps · calls · links",
            modifier = Modifier.weight(1f),
            onClick = onChat
          )
          QuickActionCard(
            icon = Icons.Rounded.VolumeUp,
            title = "Speaker",
            subtitle = "Run a tone test",
            modifier = Modifier.weight(1f),
            onClick = { viewModel.testSpeaker() }
          )
          QuickActionCard(
            icon = Icons.Rounded.Language,
            title = "Language",
            subtitle = "Auto detect",
            modifier = Modifier.weight(1f),
            onClick = { showLanguageInfo = true }
          )
        }
      }

      item {
        if (actionNote != null) {
          Text(
            text = actionNote.orEmpty(),
            color = Success,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        } else if (turns.isNotEmpty()) {
          TextButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Text("Open conversation history", color = PrimaryRed)
            Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = PrimaryRed)
          }
        }
      }
    }
  }

  if (showLanguageInfo) {
    InfoDialog(
      title = "Automatic language detection",
      message = "Zed follows the language you speak and can switch between English, Hindi, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and other Gemini-supported languages.",
      onDismiss = { showLanguageInfo = false }
    )
  }
}

@Composable
private fun HomeHeader(state: AssistantState, onChat: () -> Unit, onSettings: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    IconButton(onClick = onChat) {
      Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Chat", tint = PrimaryText)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
      Text("ZED", color = PrimaryRed, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
      Text("PERSONAL AI COMPANION", color = Hint, fontSize = 8.sp, letterSpacing = 1.7.sp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(statusColor(state))
      )
      IconButton(onClick = onSettings) {
        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = PrimaryText)
      }
    }
  }
}

@Composable
private fun StatusPill(state: AssistantState, status: String) {
  Surface(
    color = Cards.copy(alpha = 0.92f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, statusColor(state).copy(alpha = 0.55f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor(state)))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = status.uppercase(),
        color = PrimaryText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp
      )
    }
  }
}

@Composable
private fun LiveLinkCard(state: AssistantState, hasHistory: Boolean, onClick: () -> Unit) {
  val isReady = state != AssistantState.ERROR
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = Cards.copy(alpha = 0.94f)),
    border = BorderStroke(1.dp, if (isReady) Border else PrimaryRed.copy(alpha = 0.55f)),
    shape = RoundedCornerShape(18.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(13.dp))
          .background(Brush.linearGradient(listOf(PrimaryRed, Purple))),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = PrimaryText)
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text("GEMINI LIVE LINK", color = PrimaryRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(
          if (isReady) "Voice, typed chat, and safe device actions" else "Open Settings to repair the connection",
          color = PrimaryText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 3.dp)
        )
        Text(
          if (hasHistory) "Conversation history is ready" else "No completed turns yet",
          color = Hint,
          fontSize = 11.sp,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
      Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Hint)
    }
  }
}

@Composable
private fun QuickActionCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    onClick = onClick,
    modifier = modifier.height(92.dp),
    colors = CardDefaults.cardColors(containerColor = Cards.copy(alpha = 0.9f)),
    border = BorderStroke(1.dp, Border),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Icon(icon, contentDescription = null, tint = Purple, modifier = Modifier.size(20.dp))
      Column {
        Text(title, color = PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Hint, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
fun ZedChatScreen(viewModel: MainViewModel) {
  val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  var draft by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Background)
      .navigationBarsPadding()
      .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 106.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Conversation", color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Zed · Gemini Live · ${stateName(state)}", color = Hint, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
      }
      IconButton(onClick = viewModel::clearConversation) {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear conversation", tint = Hint)
      }
    }
    Spacer(modifier = Modifier.height(10.dp))

    if (turns.isEmpty()) {
      EmptyChatState(modifier = Modifier.weight(1f).fillMaxWidth())
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(turns, key = { "${it.role}-${it.text.hashCode()}-${it.text.length}" }) { turn ->
          ChatBubble(turn)
        }
      }
    }

    Surface(
      color = Cards,
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, Border)
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          placeholder = { Text("Message Zed", color = Hint) },
          modifier = Modifier.fillMaxWidth(),
          minLines = 1,
          maxLines = 4,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (draft.isNotBlank()) {
              viewModel.sendText(draft)
              draft = ""
            }
          })
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          Surface(color = Background, shape = RoundedCornerShape(50)) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = Purple, modifier = Modifier.size(15.dp))
              Text("Zed", color = PrimaryText, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
            }
          }
          Spacer(modifier = Modifier.weight(1f))
          IconButton(
            onClick = {
              if (draft.isNotBlank()) {
                viewModel.sendText(draft)
                draft = ""
              }
            },
            modifier = Modifier.size(42.dp).clip(CircleShape).background(PrimaryRed)
          ) {
            Icon(Icons.Rounded.Send, contentDescription = "Send message", tint = PrimaryText)
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyChatState(modifier: Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier.size(76.dp).clip(CircleShape).background(PrimaryRed.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(34.dp))
    }
    Text("Your conversation starts here", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp))
    Text("Use voice or type a message. Zed keeps completed turns in this session.", color = Hint, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
  }
}

@Composable
private fun ChatBubble(turn: ChatMessage) {
  val isUser = turn.role == ChatMessage.Role.USER
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    Surface(
      color = if (isUser) PrimaryRed.copy(alpha = 0.18f) else Cards,
      shape = RoundedCornerShape(18.dp),
      border = BorderStroke(1.dp, if (isUser) PrimaryRed.copy(alpha = 0.35f) else Border),
      modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.9f)
    ) {
      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
        Text(if (isUser) "YOU" else "ZED", color = if (isUser) PrimaryRed else Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(turn.text, color = PrimaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}

@Composable
fun ZedUpdatesScreen() {
  val notices = listOf(
    "Gemini Live" to "Native voice and typed conversation share one low-latency session.",
    "Safe device actions" to "Open allowlisted apps, WhatsApp, safe links, and calls with honest permission results.",
    "Part 7 navigation" to "Home, Conversation, Voice, Settings, and Updates are available from the floating dock.",
    "Automatic languages" to "Speak naturally in your preferred language or switch languages mid-conversation.",
    "Audio diagnostics" to "The speaker test uses the same AudioTrack path as Gemini voice output.",
    "Privacy" to "Your API key stays in private app preferences and audio is sent only during an active session."
  )

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(Background).navigationBarsPadding(),
    contentPadding = PaddingValues(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 112.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text("Updates", color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
      Text("Zed capabilities and release notes", color = Hint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
    items(notices) { (title, details) ->
      Card(
        colors = CardDefaults.cardColors(containerColor = Cards.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(title.uppercase(), color = PrimaryRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
          Text(details, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
        }
      }
    }
  }
}

@Composable
fun ZedBottomDock(
  selectedTab: ZedTab,
  state: AssistantState,
  onTabSelected: (ZedTab) -> Unit,
  onMicClick: () -> Unit,
  onMicLongClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(74.dp)) {
      drawArc(
        color = PrimaryRed.copy(alpha = 0.5f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.32f, -18.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width * 0.36f, 54.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
      )
    }
    Surface(
      color = Cards.copy(alpha = 0.97f),
      shape = RoundedCornerShape(28.dp),
      border = BorderStroke(1.dp, Border),
      shadowElevation = 12.dp
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        DockItem("Home", Icons.Rounded.Home, selectedTab == ZedTab.HOME) { onTabSelected(ZedTab.HOME) }
        DockItem("Chat", Icons.Rounded.ChatBubbleOutline, selectedTab == ZedTab.CHAT) { onTabSelected(ZedTab.CHAT) }
        Box(
          modifier = Modifier
            .weight(1f)
            .size(64.dp)
            .offset(y = (-13).dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(PrimaryRed, Purple)))
            .combinedClickable(onClick = onMicClick, onLongClick = onMicLongClick)
            .semantics { contentDescription = if (state == AssistantState.IDLE) "Start voice" else "Stop voice" },
          contentAlignment = Alignment.Center
        ) {
          Icon(if (state == AssistantState.IDLE) Icons.Rounded.Mic else Icons.Rounded.Stop, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(28.dp))
        }
        DockItem("Settings", Icons.Rounded.Settings, selectedTab == ZedTab.SETTINGS) { onTabSelected(ZedTab.SETTINGS) }
        DockItem("Updates", Icons.Rounded.NotificationsNone, selectedTab == ZedTab.UPDATES) { onTabSelected(ZedTab.UPDATES) }
      }
    }
  }
}

@Composable
private fun RowScope.DockItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
  Column(
    modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onClick, onLongClick = null).padding(vertical = 7.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(icon, contentDescription = label, tint = if (selected) PrimaryText else Hint, modifier = Modifier.size(21.dp))
    Text(label, color = if (selected) PrimaryText else Hint, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
  }
}

@Composable
private fun ErrorCard(message: String, onSettings: () -> Unit, onDismiss: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B0A0E)).border(1.dp, PrimaryRed.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(14.dp)
  ) {
    Text("CONNECTION NEEDS ATTENTION", color = PrimaryRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
    Text(message, color = PrimaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    Row {
      TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("Settings", color = PrimaryRed, fontSize = 11.sp) }
      TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("Dismiss", color = Hint, fontSize = 11.sp) }
    }
  }
}

@Composable
private fun TranscriptCard(userText: String, zedText: String) {
  Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Cards.copy(alpha = 0.9f)).border(1.dp, Border, RoundedCornerShape(18.dp)).padding(14.dp)) {
    if (userText.isNotBlank()) {
      Text("YOU", color = Hint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
      Text(userText, color = PrimaryText.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (userText.isNotBlank() && zedText.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
    if (zedText.isNotBlank()) {
      Text("ZED", color = Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
      Text(zedText, color = PrimaryText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun AmbientGlow(modifier: Modifier, color: Color) {
  Box(modifier = modifier.size(270.dp).clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent))))
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, color = PrimaryText) },
    text = { Text(message, color = Hint) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = PrimaryRed) } },
    containerColor = Cards
  )
}

private fun statusColor(state: AssistantState): Color = when (state) {
  AssistantState.LISTENING -> Success
  AssistantState.SPEAKING -> Purple
  AssistantState.CONNECTING -> Warning
  AssistantState.ERROR -> PrimaryRed
  AssistantState.IDLE -> Hint
}

private fun stateName(state: AssistantState): String = when (state) {
  AssistantState.IDLE -> "READY"
  AssistantState.CONNECTING -> "CONNECTING"
  AssistantState.LISTENING -> "LISTENING"
  AssistantState.SPEAKING -> "SPEAKING"
  AssistantState.ERROR -> "ERROR"
}
