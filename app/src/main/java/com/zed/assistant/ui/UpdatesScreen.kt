package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.ui.theme.Background
import com.zed.assistant.ui.theme.Border
import com.zed.assistant.ui.theme.Cards
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.PrimaryText

private data class ZedUpdate(val title: String, val details: String)

@Composable
fun ZedUpdatesScreen() {
  val updates = listOf(
    ZedUpdate("Gemini Live", "Native voice and typed conversation share one low-latency session."),
    ZedUpdate("Safe device actions", "Open allowlisted apps, WhatsApp, safe links, and calls with honest permission results."),
    ZedUpdate("Assistant navigation", "Home, Conversation, Voice, Settings, and Updates are available from the floating dock."),
    ZedUpdate("Automatic languages", "Speak naturally in your preferred language or switch languages mid-conversation."),
    ZedUpdate("Audio diagnostics", "The speaker test uses the same AudioTrack path as Gemini voice output."),
    ZedUpdate("Privacy", "Your API key stays in private app preferences and audio is sent only during an active session.")
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(Background)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentPadding = PaddingValues(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 112.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text("Updates", color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
      Text(
        "Zed capabilities and release notes",
        color = Hint,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
    items(updates, key = ZedUpdate::title) { update ->
      Card(
        colors = CardDefaults.cardColors(containerColor = Cards.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            update.title.uppercase(),
            color = PrimaryRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
          )
          Text(
            update.details,
            color = PrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp)
          )
        }
      }
    }
  }
}
