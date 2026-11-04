package com.zed.assistant.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zed.assistant.R
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.model.AssistantState
import com.zed.assistant.ui.components.GlassCard
import com.zed.assistant.ui.components.GlassIconButton
import com.zed.assistant.ui.components.LiquidBackground
import com.zed.assistant.ui.components.StatusDot
import com.zed.assistant.ui.components.ZedWordmark
import com.zed.assistant.ui.theme.LocalZedPalette
import com.zed.assistant.viewmodel.MainViewModel
import java.util.Calendar

/**
 * Home screen: identity header, the live robot-head stage, session status,
 * transport card, and three quick actions.
 */
@Composable
fun ZedHomeScreen(
  viewModel: MainViewModel,
  onChat: () -> Unit,
  onSettings: () -> Unit
) {
  val context = LocalContext.current
  val palette = LocalZedPalette.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val status by viewModel.status.collectAsStateWithLifecycle()
  val level by viewModel.level.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val isActive by viewModel.isActive.collectAsStateWithLifecycle()
  val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
  val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
  var showLanguageInfo by rememberSaveable { mutableStateOf(false) }

  val prefs = remember {
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
  }
  val userName = prefs.getString(ZedEngine.KEY_USER_NAME, "").orEmpty()
  val model = prefs.getString(ZedEngine.KEY_MODEL, ZedEngine.DEFAULT_MODEL)
    .orEmpty()
    .ifBlank { ZedEngine.DEFAULT_MODEL }

  LiquidBackground {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 92.dp)
    ) {
      HomeHeader(
        userName = userName,
        onChat = onChat,
        onSettings = onSettings
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
      ) {
        OrbHalo(modifier = Modifier.fillMaxSize())
        ZedRobotAvatar(
          state = avatarState,
          onTap = { viewModel.toggleSession() },
          onLongPress = { viewModel.interrupt() },
          modifier = Modifier.fillMaxSize()
        )

        if (errorMessage != null) {
          LiveErrorOverlay(
            message = errorMessage.orEmpty(),
            onSettings = onSettings,
            onDismiss = viewModel::dismissError,
            modifier = Modifier
              .align(Alignment.Center)
              .padding(horizontal = 18.dp)
          )
        }
      }

      HomeStatus(
        state = state,
        status = status,
        isActive = isActive
      )

      Spacer(modifier = Modifier.height(14.dp))

      LiveLinkCard(
        state = state,
        model = model,
        turns = turns.size,
        onClick = {
          if (state == AssistantState.ERROR) onSettings else viewModel.toggleSession()
        }
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
      ) {
        QuickActionCard(
          icon = Icons.AutoMirrored.Rounded.VolumeUp,
          title = "Speaker",
          subtitle = "Tone test",
          modifier = Modifier.weight(1f),
          onClick = viewModel::testSpeaker
        )
        QuickActionCard(
          icon = Icons.Rounded.Language,
          title = "Language",
          subtitle = "Auto detect",
          modifier = Modifier.weight(1f),
          onClick = { showLanguageInfo = true }
        )
        QuickActionCard(
          icon = Icons.Rounded.ChatBubbleOutline,
          title = "Conversation",
          subtitle = if (turns.isEmpty()) "Type instead" else "${turns.size} turns",
          modifier = Modifier.weight(1f),
          onClick = onChat
        )
      }

      Text(
        text = "Zed sends microphone audio only while a session is active.",
        color = palette.textMuted,
        fontSize = 9.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp)
      )
    }
  }

  if (showLanguageInfo) {
    ZedInfoDialog(
      title = "Automatic language detection",
      message = "Zed follows the language you speak and can switch between English, Hindi, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and other Gemini-supported languages.",
      onDismiss = { showLanguageInfo = false }
    )
  }
}

@Composable
private fun HomeHeader(
  userName: String,
  onChat: () -> Unit,
  onSettings: () -> Unit
) {
  val palette = LocalZedPalette.current
  val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val greeting = when (hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Welcome back"
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    GlassIconButton(
      icon = Icons.Rounded.ChatBubbleOutline,
      contentDescription = "Open conversation",
      onClick = onChat,
      modifier = Modifier.size(42.dp)
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
          painter = painterResource(R.drawable.zed_applogo),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(27.dp)
            .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.size(7.dp))
        ZedWordmark(fontSize = 24, letterSpacing = 5)
      }
      Text(
        text = if (userName.isBlank()) greeting else "$greeting, $userName",
        color = palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp)
      )
    }
    GlassIconButton(
      icon = Icons.Rounded.Settings,
      contentDescription = "Settings",
      onClick = onSettings,
      modifier = Modifier.size(42.dp)
    )
  }
}

@Composable
private fun OrbHalo(modifier: Modifier = Modifier) {
  val palette = LocalZedPalette.current
  Canvas(modifier = modifier) {
    val radius = size.minDimension * 0.43f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(palette.accent.copy(alpha = 0.16f), Color.Transparent),
        center = center,
        radius = radius * 1.18f
      ),
      radius = radius * 1.18f,
      center = center
    )
    drawCircle(
      color = palette.accent.copy(alpha = 0.18f),
      radius = radius,
      center = center,
      style = Stroke(width = 1.dp.toPx())
    )
    drawCircle(
      color = palette.accentSecondary.copy(alpha = 0.10f),
      radius = radius * 1.12f,
      center = center,
      style = Stroke(width = 1.dp.toPx())
    )
  }
}

@Composable
private fun HomeStatus(
  state: AssistantState,
  status: String,
  isActive: Boolean
) {
  val palette = LocalZedPalette.current
  val color = statusColor(state)
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(palette.card.copy(alpha = 0.8f))
        .padding(horizontal = 16.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      StatusDot(color = color)
      Text(
        text = statusLabel(state, status),
        color = palette.textPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 8.dp)
      )
    }
    Text(
      text = if (isActive) {
        "Hold the avatar or mic to interrupt"
      } else {
        "Tap the avatar or mic to start"
      },
      color = palette.textMuted,
      fontSize = 10.sp,
      modifier = Modifier.padding(top = 6.dp)
    )
  }
}

@Composable
private fun LiveLinkCard(
  state: AssistantState,
  model: String,
  turns: Int,
  onClick: () -> Unit
) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(15.dp),
    onClick = onClick
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Brush.linearGradient(listOf(palette.accent, palette.accentSecondary))),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.Mic,
          contentDescription = null,
          tint = palette.specular,
          modifier = Modifier.size(23.dp)
        )
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 12.dp)
      ) {
        Text(
          text = "GEMINI LIVE UPLINK",
          color = palette.accent,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.2.sp
        )
        Text(
          text = model,
          color = palette.textPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 3.dp)
        )
        Text(
          text = when {
            state == AssistantState.ERROR -> "Tap to repair the connection in Settings"
            turns == 0 -> "Ready for your first conversation"
            else -> "$turns completed ${if (turns == 1) "turn" else "turns"} in this session"
          },
          color = palette.textMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = null,
        tint = palette.textMuted,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

@Composable
private fun QuickActionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = modifier.height(86.dp),
    shape = RoundedCornerShape(18.dp),
    contentPadding = PaddingValues(11.dp),
    onClick = onClick
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = palette.accent,
      modifier = Modifier.size(22.dp)
    )
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = title,
      color = palette.textPrimary,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = subtitle,
      color = palette.textMuted,
      fontSize = 9.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 1.dp)
    )
  }
}

@Composable
private fun LiveErrorOverlay(
  message: String,
  onSettings: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalZedPalette.current
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    contentPadding = PaddingValues(18.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.Settings,
        contentDescription = null,
        tint = palette.error,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = "CONNECTION NEEDS ATTENTION",
        color = palette.error,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier
          .weight(1f)
          .padding(start = 9.dp)
      )
      GlassIconButton(
        icon = Icons.Rounded.Close,
        contentDescription = "Dismiss error",
        onClick = onDismiss,
        modifier = Modifier.size(36.dp)
      )
    }
    Text(
      text = message,
      color = palette.textPrimary,
      fontSize = 13.sp,
      lineHeight = 19.sp,
      modifier = Modifier.padding(top = 10.dp)
    )
    TextButton(
      onClick = onSettings,
      contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
      modifier = Modifier.padding(top = 4.dp)
    ) {
      Text(
        text = "OPEN SETTINGS",
        color = palette.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
      )
    }
  }
}

@Composable
fun ZedInfoDialog(
  title: String,
  message: String,
  onDismiss: () -> Unit
) {
  val palette = LocalZedPalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = title,
        color = palette.textPrimary,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Text(
        text = message,
        color = palette.textSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp
      )
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("GOT IT", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
private fun statusColor(state: AssistantState): Color {
  val palette = LocalZedPalette.current
  return when (state) {
    AssistantState.LISTENING -> palette.success
    AssistantState.SPEAKING -> palette.accent
    AssistantState.CONNECTING -> palette.warning
    AssistantState.ERROR -> palette.error
    AssistantState.IDLE -> palette.textMuted
  }
}

private fun statusLabel(state: AssistantState, status: String): String = when (state) {
  AssistantState.IDLE -> "TAP ORB TO START"
  AssistantState.CONNECTING -> "CONNECTING…"
  AssistantState.LISTENING -> "MIC ON · LISTENING…"
  AssistantState.SPEAKING -> "ZED SPEAKING…"
  AssistantState.ERROR -> "CONNECTION ERROR"
}.ifBlank { status.uppercase() }
