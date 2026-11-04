@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zed.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.model.AssistantState
import com.zed.assistant.ui.components.StatusDot
import com.zed.assistant.ui.theme.LocalLiquidGlassEnabled
import com.zed.assistant.ui.theme.LocalZedPalette

enum class ZedTab {
  HOME,
  CHAT,
  SETTINGS,
  UPDATES
}

@Composable
fun ZedBottomDock(
  selectedTab: ZedTab,
  state: AssistantState,
  onTabSelected: (ZedTab) -> Unit,
  onMicClick: () -> Unit,
  onMicLongClick: () -> Unit
) {
  val palette = LocalZedPalette.current
  val liquid = LocalLiquidGlassEnabled.current
  val dockShape = RoundedCornerShape(28.dp)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    if (liquid) {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(26.dp)
          .offset(y = (-58).dp)
      ) {
        val path = Path().apply {
          moveTo(size.width * 0.30f, size.height)
          quadraticTo(
            size.width * 0.50f,
            (-14).dp.toPx(),
            size.width * 0.70f,
            size.height
          )
        }
        drawPath(
          path = path,
          brush = Brush.horizontalGradient(
            listOf(
              palette.accent.copy(alpha = 0.05f),
              palette.accent.copy(alpha = 0.55f),
              palette.accentSecondary.copy(alpha = 0.45f),
              palette.accent.copy(alpha = 0.05f)
            )
          ),
          style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .shadow(if (liquid) 18.dp else 3.dp, dockShape)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(dockShape)
          .background(
            Brush.verticalGradient(
              if (liquid) listOf(palette.dockStart, palette.dockEnd)
              else listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
            )
          )
          .border(
            1.dp,
            if (liquid) palette.dockBorder.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.outlineVariant,
            dockShape
          )
      )
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        DockItem(
          label = "Home",
          icon = Icons.Rounded.Home,
          selected = selectedTab == ZedTab.HOME,
          onClick = { onTabSelected(ZedTab.HOME) }
        )
        DockItem(
          label = "Chat",
          icon = Icons.Rounded.ChatBubbleOutline,
          selected = selectedTab == ZedTab.CHAT,
          onClick = { onTabSelected(ZedTab.CHAT) }
        )

        Box(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(58.dp)
              .offset(y = (-13).dp)
              .shadow(14.dp, CircleShape)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  if (liquid) listOf(palette.accent, palette.accentSecondary)
                  else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                )
              )
              .combinedClickable(onClick = onMicClick, onLongClick = onMicLongClick)
              .semantics {
                contentDescription = if (state == AssistantState.IDLE || state == AssistantState.ERROR) {
                  "Start voice session"
                } else {
                  "Stop voice session. Long press to interrupt."
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (state == AssistantState.IDLE || state == AssistantState.ERROR) {
                Icons.Rounded.Mic
              } else {
                Icons.Rounded.Stop
              },
              contentDescription = null,
              tint = if (liquid) palette.specular else MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(28.dp)
            )
          }
        }

        DockItem(
          label = "Settings",
          icon = Icons.Rounded.Settings,
          selected = selectedTab == ZedTab.SETTINGS,
          onClick = { onTabSelected(ZedTab.SETTINGS) }
        )
        DockItem(
          label = "Updates",
          icon = Icons.Rounded.NotificationsNone,
          selected = selectedTab == ZedTab.UPDATES,
          onClick = { onTabSelected(ZedTab.UPDATES) }
        )
      }
    }
  }
}

@Composable
private fun RowScope.DockItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  showBadge: Boolean = false
) {
  val palette = LocalZedPalette.current
  Column(
    modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(18.dp))
      .combinedClickable(onClick = onClick, onLongClick = null)
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (selected) palette.accent else palette.textMuted,
        modifier = Modifier.size(21.dp)
      )
      if (showBadge) {
        StatusDot(
          color = palette.error,
          size = 6,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-1).dp)
        )
      }
    }
    Text(
      text = label,
      color = if (selected) palette.accent else palette.textMuted,
      fontSize = 9.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      modifier = Modifier.padding(top = 3.dp)
    )
  }
}
