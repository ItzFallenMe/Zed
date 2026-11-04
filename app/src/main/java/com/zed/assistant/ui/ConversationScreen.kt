package com.zed.assistant.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import com.zed.assistant.ui.components.GlassCard
import com.zed.assistant.ui.components.GlassChip
import com.zed.assistant.ui.components.GlassIconButton
import com.zed.assistant.ui.components.LiquidBackground
import com.zed.assistant.ui.theme.LocalZedPalette
import com.zed.assistant.viewmodel.MainViewModel

/**
 * Conversation screen: identity header, live turn bubbles, and a composer
 * shared by spoken and typed turns.
 */
@Composable
fun ZedChatScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit
) {
  val palette = LocalZedPalette.current
  val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val userText by viewModel.userText.collectAsStateWithLifecycle()
  val zedText by viewModel.zedText.collectAsStateWithLifecycle()
  var draft by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  fun send(message: String) {
    val trimmed = message.trim()
    if (trimmed.isEmpty()) return
    viewModel.sendText(trimmed)
    draft = ""
  }

  val lastUser = turns.lastOrNull { it.role == ChatMessage.Role.USER }?.text
  val lastAssistant = turns.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.text
  val liveUser = userText.takeIf { it.isNotBlank() && it != lastUser }
  val liveAssistant = zedText.takeIf { it.isNotBlank() && it != lastAssistant }
  val liveRows = buildList {
    liveUser?.let { add(ChatMessage(ChatMessage.Role.USER, it)) }
    liveAssistant?.let { add(ChatMessage(ChatMessage.Role.ASSISTANT, it)) }
  }
  val rows = remember(turns, liveRows) { turns + liveRows }

  LaunchedEffect(rows.size, rows.lastOrNull()?.text) {
    if (rows.isNotEmpty()) {
      listState.animateScrollToItem(rows.lastIndex)
    }
  }

  LiquidBackground {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 104.dp)
    ) {
      ChatHeader(
        state = state,
        onBack = onBack,
        onNew = viewModel::clearConversation
      )

      if (rows.isEmpty()) {
        EmptyChatState(
          onSuggestion = ::send,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          itemsIndexed(
            items = rows,
            key = { index, item -> "$index-${item.role}-${item.text.hashCode()}" }
          ) { _, turn ->
            ChatBubble(turn)
          }
        }
      }

      ChatComposer(
        draft = draft,
        onDraftChange = { draft = it },
        onSend = { send(draft) }
      )
    }
  }
}

@Composable
private fun ChatHeader(
  state: AssistantState,
  onBack: () -> Unit,
  onNew: () -> Unit
) {
  val palette = LocalZedPalette.current
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    GlassIconButton(
      icon = Icons.AutoMirrored.Rounded.ArrowBack,
      contentDescription = "Back to home",
      onClick = onBack
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "Conversation",
        color = palette.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
      ) {
        Text(
          text = "Zed · Gemini Live · ",
          color = palette.textMuted,
          fontSize = 11.sp
        )
        Text(
          text = stateLabel(state),
          color = stateColor(state),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
    GlassIconButton(
      icon = Icons.Rounded.Add,
      contentDescription = "Start a new conversation",
      onClick = onNew
    )
  }
}

@Composable
private fun EmptyChatState(
  onSuggestion: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalZedPalette.current
  Column(
    modifier = modifier.padding(horizontal = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(86.dp)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            listOf(
              palette.accent.copy(alpha = 0.18f),
              palette.accentSecondary.copy(alpha = 0.20f)
            )
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "Z",
        color = palette.accent,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold
      )
    }
    Text(
      text = "Your conversation starts here",
      color = palette.textPrimary,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 18.dp)
    )
    Text(
      text = "Use voice or type a message. Completed turns stay available for this session.",
      color = palette.textMuted,
      fontSize = 12.sp,
      textAlign = TextAlign.Center,
      lineHeight = 18.sp,
      modifier = Modifier.padding(top = 7.dp)
    )
    Row(
      modifier = Modifier.padding(top = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      GlassChip(
        label = "Open WhatsApp",
        modifier = Modifier.clickable { onSuggestion("Open WhatsApp") }
      )
      GlassChip(
        label = "What can you do?",
        modifier = Modifier.clickable { onSuggestion("What can you do?") }
      )
    }
  }
}

@Composable
private fun ChatBubble(turn: ChatMessage) {
  val palette = LocalZedPalette.current
  val isUser = turn.role == ChatMessage.Role.USER
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top
  ) {
    if (!isUser) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Z",
          color = palette.specular,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
    }

    Column(
      modifier = Modifier
        .fillMaxWidth(if (isUser) 0.84f else 0.88f)
        .clip(
          RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isUser) 20.dp else 6.dp,
            bottomEnd = if (isUser) 6.dp else 20.dp
          )
        )
        .background(
          if (isUser) {
            Brush.verticalGradient(
              listOf(palette.accent, palette.accent.copy(alpha = 0.88f))
            )
          } else {
            Brush.verticalGradient(listOf(palette.cardGlassStart, palette.cardGlassEnd))
          }
        )
        .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
      Text(
        text = if (isUser) "YOU" else "ZED",
        color = if (isUser) palette.specular.copy(alpha = 0.82f) else palette.accent,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
      )
      Text(
        text = turn.text,
        color = if (isUser) palette.specular else palette.textPrimary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 3.dp)
      )
    }
  }
}

@Composable
private fun ChatComposer(
  draft: String,
  onDraftChange: (String) -> Unit,
  onSend: () -> Unit
) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    contentPadding = PaddingValues(12.dp)
  ) {
    BasicTextField(
      value = draft,
      onValueChange = onDraftChange,
      textStyle = TextStyle(
        color = palette.textPrimary,
        fontSize = 15.sp,
        lineHeight = 21.sp
      ),
      cursorBrush = SolidColor(palette.accent),
      maxLines = 4,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { onSend() }),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 3.dp, vertical = 4.dp),
      decorationBox = { inner ->
        Box {
          if (draft.isBlank()) {
            Text(
              text = "Message Zed",
              color = palette.textMuted,
              fontSize = 15.sp
            )
          }
          inner()
        }
      }
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      GlassChip(
        label = "Zed",
        icon = Icons.Rounded.ChatBubbleOutline,
        contentColor = palette.accent
      )
      Spacer(modifier = Modifier.width(7.dp))
      GlassChip(
        label = "Live",
        icon = Icons.Rounded.Mic,
        contentColor = palette.success
      )
      Spacer(modifier = Modifier.weight(1f))
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(
            if (draft.isBlank()) {
              Brush.linearGradient(
                listOf(
                  palette.textMuted.copy(alpha = 0.35f),
                  palette.textMuted.copy(alpha = 0.35f)
                )
              )
            } else {
              Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))
            }
          )
          .clickable(enabled = draft.isNotBlank(), onClick = onSend),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Send,
          contentDescription = "Send message",
          tint = palette.specular,
          modifier = Modifier.size(21.dp)
        )
      }
    }
  }
}

private fun stateLabel(state: AssistantState): String = when (state) {
  AssistantState.IDLE -> "Ready"
  AssistantState.CONNECTING -> "Connecting"
  AssistantState.LISTENING -> "Listening"
  AssistantState.SPEAKING -> "Speaking"
  AssistantState.ERROR -> "Error"
}

private fun stateColor(state: AssistantState): androidx.compose.ui.graphics.Color = when (state) {
  AssistantState.IDLE -> androidx.compose.ui.graphics.Color(0xFF64748B)
  AssistantState.CONNECTING -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
  AssistantState.LISTENING -> androidx.compose.ui.graphics.Color(0xFF10B981)
  AssistantState.SPEAKING -> androidx.compose.ui.graphics.Color(0xFF2F6BFF)
  AssistantState.ERROR -> androidx.compose.ui.graphics.Color(0xFFEF4444)
}
