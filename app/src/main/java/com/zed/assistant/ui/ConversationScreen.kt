package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.model.ChatMessage
import com.zed.assistant.ui.theme.Background
import com.zed.assistant.ui.theme.Border
import com.zed.assistant.ui.theme.Cards
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.PrimaryText
import com.zed.assistant.ui.theme.Purple
import com.zed.assistant.viewmodel.MainViewModel

@Composable
fun ZedChatScreen(viewModel: MainViewModel) {
  val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  var draft by rememberSaveable { mutableStateOf("") }

  fun sendDraft() {
    val message = draft.trim()
    if (message.isNotEmpty()) {
      viewModel.sendText(message)
      draft = ""
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Background)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 106.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Conversation", color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
          text = "Zed · Gemini Live · ${state.name.lowercase().replaceFirstChar(Char::uppercase)}",
          color = Hint,
          fontSize = 12.sp,
          modifier = Modifier.padding(top = 3.dp)
        )
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
          keyboardActions = KeyboardActions(onSend = { sendDraft() })
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
          Surface(color = Background, shape = RoundedCornerShape(50)) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = Purple,
                modifier = Modifier.size(15.dp)
              )
              Text("Zed", color = PrimaryText, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
            }
          }
          Spacer(modifier = Modifier.weight(1f))
          IconButton(
            onClick = { sendDraft() },
            enabled = draft.isNotBlank(),
            modifier = Modifier.size(42.dp).clip(CircleShape).background(PrimaryRed)
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message", tint = PrimaryText)
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
      Icon(
        Icons.Rounded.ChatBubbleOutline,
        contentDescription = null,
        tint = PrimaryRed,
        modifier = Modifier.size(34.dp)
      )
    }
    Text(
      "Your conversation starts here",
      color = PrimaryText,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(top = 16.dp)
    )
    Text(
      "Use voice or type a message. Completed turns stay available for this session.",
      color = Hint,
      fontSize = 12.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp)
    )
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
        Text(
          if (isUser) "YOU" else "ZED",
          color = if (isUser) PrimaryRed else Purple,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.4.sp
        )
        Text(turn.text, color = PrimaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}
