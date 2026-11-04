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
import androidx.compose.material.icons.rounded.Shield
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
    tag = "2.0 · CREATOR PROTOCOL",
    title = "Recognition that stays on the phone",
    details = "Say “I am your creator” or “activate master control” and Z.E.D. opens a local code challenge. The default first-run code is 160123; change it after unlocking. Codes are hashed with PBKDF2 and sealed with the Android Keystore, with a one-minute lockout after three misses.",
    icon = Icons.Rounded.Lock
  ),
  ZedUpdate(
    tag = "2.0 · MASTER CONTROL",
    title = "The creator gets the keys",
    details = "Verified creator access unlocks system navigation, lock screen, wake-word and overlay switches, theme control, and memory and conversation controls. The code and the verification never pass through Gemini, and the assistant is told never to ask for it in chat.",
    icon = Icons.Rounded.Shield
  ),
  ZedUpdate(
    tag = "2.0 · PRESENCE",
    title = "A robot head that knows what it is doing",
    details = "The primary orb is now the robot-head avatar. It shifts between idle, thinking, listening, speaking, searching, working, happy, and error to match Z.E.D.'s actual state, on the home stage and in the floating overlay.",
    icon = Icons.Rounded.AutoAwesome
  ),
  ZedUpdate(
    tag = "2.0 · VOICE",
    title = "Eight voices, one personality",
    details = "Choose Zed Prime, E.V. Mode, Spark, Gravel, Sentinel, Velvet, Anchor, or Current. Each preset pairs a different Gemini native voice with a matching delivery direction, so the writing and the sound change together.",
    icon = Icons.Rounded.Mic
  ),
  ZedUpdate(
    tag = "2.0 · PERFORMANCE",
    title = "Material 3 first, glass when you want it",
    details = "Solid Material 3 surfaces are the default, with no ambient glow fields or translucent layers. The liquid-glass look is an appearance toggle for devices that can afford it, and the black Obsidian palette remains the default.",
    icon = Icons.Rounded.Palette
  ),
  ZedUpdate(
    tag = "HANDS-FREE",
    title = "Wake word, owner voice, and memory",
    details = "The offline Vosk wake word, optional speaker-profile enrolment, floating robot overlay, permissions centre, and memory vault are all available in Settings. No audio leaves the device for wake-word detection or speaker vectors.",
    icon = Icons.Rounded.Mic
  ),
  ZedUpdate(
    tag = "CRAFT",
    title = "Small details that make it feel finished",
    details = "A branded launcher icon, persisted conversation history, speaker diagnostic through the real AudioTrack path, automatic language switching, and truthful device-action results including dialer fallback and WhatsApp prefill.",
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
            text = "Z.E.D. 2.0",
            color = palette.textPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp)
          )
          Text(
            text = "Zeta Evolved Device Intelligence. Built by Vedant, made to feel like a partner.",
            color = palette.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 5.dp)
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            GlassChip(
              label = "CREATOR READY",
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
          text = "Created by Vedant · ItzFallenMe · MIT licensed",
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
      text = "Z.E.D. capabilities and release notes",
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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
