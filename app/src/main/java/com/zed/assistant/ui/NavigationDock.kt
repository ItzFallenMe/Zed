@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zed.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.model.AssistantState
import com.zed.assistant.ui.theme.Border
import com.zed.assistant.ui.theme.Cards
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.PrimaryText
import com.zed.assistant.ui.theme.Purple

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
        verticalAlignment = Alignment.CenterVertically
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
            .semantics {
              contentDescription = if (state == AssistantState.IDLE) "Start voice" else "Stop voice"
            },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (state == AssistantState.IDLE) Icons.Rounded.Mic else Icons.Rounded.Stop,
            contentDescription = null,
            tint = PrimaryText,
            modifier = Modifier.size(28.dp)
          )
        }
        DockItem("Settings", Icons.Rounded.Settings, selectedTab == ZedTab.SETTINGS) {
          onTabSelected(ZedTab.SETTINGS)
        }
        DockItem("Updates", Icons.Rounded.NotificationsNone, selectedTab == ZedTab.UPDATES) {
          onTabSelected(ZedTab.UPDATES)
        }
      }
    }
  }
}

@Composable
private fun RowScope.DockItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(18.dp))
      .combinedClickable(onClick = onClick, onLongClick = null)
      .padding(vertical = 7.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(icon, contentDescription = label, tint = if (selected) PrimaryText else Hint, modifier = Modifier.size(21.dp))
    Text(
      label,
      color = if (selected) PrimaryText else Hint,
      fontSize = 9.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.padding(top = 3.dp)
    )
  }
}
