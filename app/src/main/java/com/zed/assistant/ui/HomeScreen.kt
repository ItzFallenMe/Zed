@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.model.AssistantState
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
        .statusBarsPadding()
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
            icon = Icons.AutoMirrored.Rounded.ScreenShare,
            title = "Device actions",
            subtitle = "Apps · calls · links",
            modifier = Modifier.weight(1f),
            onClick = onChat
          )
          QuickActionCard(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
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
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = PrimaryRed)
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
      Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Hint)
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
