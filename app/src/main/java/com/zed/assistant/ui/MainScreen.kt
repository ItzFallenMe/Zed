package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val status by viewModel.status.collectAsStateWithLifecycle()
  val level by viewModel.level.collectAsStateWithLifecycle()
  val userText by viewModel.userText.collectAsStateWithLifecycle()
  val zedText by viewModel.zedText.collectAsStateWithLifecycle()
  val actionNote by viewModel.actionNote.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val isActive by viewModel.isActive.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val hasApiKey = remember { viewModel.hasApiKey() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Background)
      .systemBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
      Header(state = state, onSettingsClick = onSettingsClick)

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        OrbAnimationView(state = state, level = level)
        Spacer(modifier = Modifier.height(4.dp))
        WaveformView(
          level = level,
          active = state == AssistantState.LISTENING || state == AssistantState.SPEAKING
        )
        Spacer(modifier = Modifier.height(16.dp))
        StateChip(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = status,
          color = PrimaryText,
          fontSize = 19.sp,
          fontWeight = FontWeight.Medium,
          textAlign = TextAlign.Center,
          letterSpacing = 0.3.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = hintFor(state),
          color = Hint,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )

        if (!hasApiKey && state == AssistantState.IDLE) {
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "Add your Gemini API key in Settings to get started.",
            color = Warning,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
        }

        if (userText.isNotBlank() || zedText.isNotBlank()) {
          Spacer(modifier = Modifier.height(18.dp))
          TranscriptCard(userText = userText, zedText = zedText)
        }
      }

      actionNote?.let { note ->
        Text(
          text = note,
          color = Success,
          fontSize = 11.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
        )
      }

      errorMessage?.let { message ->
        ErrorCard(
          message = message,
          onSettingsClick = onSettingsClick,
          onDismiss = { viewModel.dismissError() }
        )
        Spacer(modifier = Modifier.height(8.dp))
      }

      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Box(contentAlignment = Alignment.Center) {
          Box(
            modifier = Modifier
              .size(100.dp)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  listOf(PrimaryRed.copy(alpha = 0.22f), Color.Transparent)
                )
              )
          )
          Box(
            modifier = Modifier
              .size(78.dp)
              .clip(CircleShape)
              .background(
                Brush.verticalGradient(
                  listOf(if (isActive) Purple else PrimaryRed, PrimaryRed)
                )
              )
              .then(
                Modifier.combinedClickable(
                  onClick = { viewModel.toggleSession() },
                  onLongClick = { viewModel.interrupt() }
                )
              )
              .semantics {
                contentDescription = if (isActive) "Stop Zed" else "Start Zed"
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.Mic,
              contentDescription = null,
              tint = PrimaryText,
              modifier = Modifier.size(32.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = if (isActive) "LONG PRESS TO INTERRUPT" else "APPS · WHATSAPP · DIALER · CONTACTS",
        color = Hint,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@Composable
private fun Header(state: AssistantState, onSettingsClick: () -> Unit) {
  val statusColor = when (state) {
    AssistantState.LISTENING -> Success
    AssistantState.SPEAKING -> Purple
    AssistantState.CONNECTING -> Warning
    AssistantState.ERROR -> PrimaryRed
    AssistantState.IDLE -> Hint
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column {
      Text(
        text = "ZED",
        color = PrimaryRed,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 5.sp
      )
      Text(
        text = "VOICE COMPANION",
        color = Hint,
        fontSize = 9.sp,
        letterSpacing = 2.sp
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(statusColor)
      )
      Spacer(modifier = Modifier.size(6.dp))
      Text(
        text = stateName(state),
        color = Hint,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp
      )
      IconButton(onClick = onSettingsClick) {
        Icon(
          imageVector = Icons.Rounded.Settings,
          contentDescription = "Settings",
          tint = PrimaryText
        )
      }
    }
  }
}

@Composable
private fun StateChip(state: AssistantState) {
  Surface(
    color = Cards,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, Border)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(
            when (state) {
              AssistantState.LISTENING -> Success
              AssistantState.SPEAKING -> Purple
              AssistantState.CONNECTING -> Warning
              AssistantState.ERROR -> PrimaryRed
              AssistantState.IDLE -> Hint
            }
          )
      )
      Spacer(modifier = Modifier.size(7.dp))
      Text(
        text = stateName(state),
        color = PrimaryText,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp
      )
    }
  }
}

@Composable
private fun TranscriptCard(userText: String, zedText: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(Cards.copy(alpha = 0.92f))
      .border(1.dp, Border, RoundedCornerShape(18.dp))
      .padding(14.dp)
  ) {
    if (userText.isNotBlank()) {
      Text(
        text = "YOU",
        color = Hint,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
      )
      Text(
        text = userText,
        color = PrimaryText.copy(alpha = 0.75f),
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (userText.isNotBlank() && zedText.isNotBlank()) {
      Spacer(modifier = Modifier.height(8.dp))
    }
    if (zedText.isNotBlank()) {
      Text(
        text = "ZED",
        color = Purple,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
      )
      Text(
        text = zedText,
        color = PrimaryText,
        fontSize = 13.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun ErrorCard(
  message: String,
  onSettingsClick: () -> Unit,
  onDismiss: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFF1B0A0E))
      .border(1.dp, PrimaryRed.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Text(
      text = "SOMETHING WENT WRONG",
      color = PrimaryRed,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.4.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = message, color = PrimaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      TextButton(
        onClick = onSettingsClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
      ) {
        Text("Settings", color = PrimaryRed, fontSize = 11.sp)
      }
      TextButton(
        onClick = onDismiss,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
      ) {
        Text("Dismiss", color = Hint, fontSize = 11.sp)
      }
    }
  }
}

private fun stateName(state: AssistantState): String = when (state) {
  AssistantState.IDLE -> "READY"
  AssistantState.CONNECTING -> "CONNECTING"
  AssistantState.LISTENING -> "LISTENING"
  AssistantState.SPEAKING -> "SPEAKING"
  AssistantState.ERROR -> "ERROR"
}

private fun hintFor(state: AssistantState): String = when (state) {
  AssistantState.IDLE -> "Tap the mic and say hi — any language works."
  AssistantState.CONNECTING -> "Warming up Gemini Live…"
  AssistantState.LISTENING -> "Speak naturally. Hindi, English, Hinglish — your call."
  AssistantState.SPEAKING -> "Hold the mic to cut me off."
  AssistantState.ERROR -> "Fix the issue, then tap the mic to try again."
}
