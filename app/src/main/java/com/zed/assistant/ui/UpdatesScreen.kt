package com.zed.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.ui.components.GlassCard
import com.zed.assistant.ui.components.GlassChip
import com.zed.assistant.ui.components.LiquidBackground
import com.zed.assistant.ui.components.SectionLabel
import com.zed.assistant.ui.theme.LocalZedPalette

private data class ZedUpdate(
  val tag: String,
  val title: String,
  val details: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val updates = listOf(
  ZedUpdate(
    tag = "1.1 · INTERFACE",
    title = "Liquid glass, rebuilt around the orb",
    details = "The Part 7-inspired shell is now native Compose: Pearl, Obsidian, Ruby, and Sunset palettes, a floating curved dock, and a live Three.js particle orb that reacts to Zed's voice state.",
    icon = Icons.Rounded.Palette
  ),
  ZedUpdate(
    tag = "CONVERSATION",
    title = "One thread for voice and typing",
    details = "Spoken and typed turns share the same Gemini Live session. Completed turns stay in the conversation screen, and the composer shows when Zed is listening, thinking, or speaking.",
    icon = Icons.Rounded.Mic
  ),
  ZedUpdate(
    tag = "SAFETY",
    title = "Device actions with honest results",
    details = "Open allowlisted apps, WhatsApp, and safe links, or start a call. Zed validates arguments, never guesses an ambiguous contact, and reports dialer fallback instead of claiming a call connected.",
    icon = Icons.Rounded.Phone
  ),
  ZedUpdate(
    tag = "PRIVACY",
    title = "Your key stays on your device",
    details = "The Gemini API key is stored in private app preferences. Microphone audio is streamed only while a session is active, and the foreground service is visible for the duration of the session.",
    icon = Icons.Rounded.Lock
  ),
  ZedUpdate(
    tag = "CRAFT",
    title = "Small details that make it feel finished",
    details = "Haptic-free status transitions, accessible tap and long-press controls, a speaker diagnostic that uses the same AudioTrack as Gemini, and automatic language switching without a picker.",
    icon = Icons.Rounded.AutoAwesome
  )
)

@Composable
fun ZedUpdatesScreen() {
  val palette = LocalZedPalette.current
  LiquidBackground {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(),
      contentPadding = PaddingValues(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        RowHeader()
      }

      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp),
          contentPadding = PaddingValues(20.dp)
        ) {
          Box(
            modifier = Modifier
              .size(46.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(palette.accent, palette.accentSecondary, palette.accentTertiary)
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.NewReleases,
              contentDescription = null,
              tint = palette.specular,
              modifier = Modifier.size(23.dp)
            )
          }
          Text(
            text = "Zed 1.1 · Liquid Glass",
            color = palette.textPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp)
          )
          Text(
            text = "A calmer, faster assistant shell with the orb at the centre.",
            color = palette.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 5.dp)
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            GlassChip(
              label = "NATIVE ANDROID",
              contentColor = palette.accent,
              modifier = Modifier.padding(top = 13.dp)
            )
            GlassChip(
              label = "OPEN SOURCE",
              contentColor = palette.success,
              modifier = Modifier.padding(start = 7.dp, top = 13.dp)
            )
          }
        }
      }

      item { SectionLabel("WHAT'S NEW") }

      items(updates, key = ZedUpdate::title) { update ->
        UpdateCard(update)
      }

      item {
        Text(
          text = "Crafted by ItzFallenMe · MIT licensed",
          color = palette.textMuted,
          fontSize = 11.sp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        )
      }
    }
  }
}

@Composable
private fun RowHeader() {
  val palette = LocalZedPalette.current
  Column {
    Text(
      text = "Updates",
      color = palette.textPrimary,
      fontSize = 26.sp,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Zed capabilities and release notes",
      color = palette.textMuted,
      fontSize = 13.sp,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@Composable
private fun UpdateCard(update: ZedUpdate) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    contentPadding = PaddingValues(16.dp)
  ) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(palette.accentSoft),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = update.icon,
          contentDescription = null,
          tint = palette.accent,
          modifier = Modifier.size(19.dp)
        )
      }
      Text(
        text = update.tag,
        color = palette.accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 11.dp)
      )
    }
    Text(
      text = update.title,
      color = palette.textPrimary,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(top = 12.dp)
    )
    Text(
      text = update.details,
      color = palette.textSecondary,
      fontSize = 13.sp,
      lineHeight = 20.sp,
      modifier = Modifier.padding(top = 5.dp)
    )
  }
}
