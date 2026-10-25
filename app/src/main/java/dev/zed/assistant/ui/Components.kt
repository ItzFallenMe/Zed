package dev.zed.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zed.assistant.ui.theme.ZedGlassBorder
import dev.zed.assistant.ui.theme.ZedWhite
import kotlin.math.PI
import kotlin.math.sin

/** Frosted panel: translucent white fill, hairline highlight border. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (strong) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f),
                        if (strong) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.035f)
                    )
                )
            )
            .border(1.dp, ZedGlassBorder, RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

@Composable
fun StatusChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (active) Color.White.copy(alpha = 0.28f) else ZedGlassBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) ZedWhite else Color.White.copy(alpha = 0.35f))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) ZedWhite else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Monochrome audio bars with a slow organic wave. */
@Composable
fun Waveform(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = ZedWhite,
    barCount: Int = 27
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "phase"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (active) 1f else 0.18f,
        animationSpec = tween(420),
        label = "amplitude"
    )
    Canvas(modifier = modifier) {
        val slot = size.width / barCount
        val barWidth = slot * 0.38f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until barCount) {
            val t = phase + i / barCount.toFloat()
            val wave = (sin(t * 2f * PI).toFloat() + 1f) / 2f
            val shape = 0.28f + 0.72f * wave
            val height = (size.height * shape * amplitude).coerceAtLeast(barWidth)
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.55f * wave),
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = radius
            )
        }
    }
}

/** Hero glass panel: status, waveform and the primary talk action. */
@Composable
fun VoiceHero(
    status: String,
    hint: String,
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    armed: Boolean,
    onListen: () -> Unit
) {
    val active = listening || speaking || thinking
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            .border(
                1.dp,
                if (armed) Color.White.copy(alpha = 0.34f) else ZedGlassBorder,
                RoundedCornerShape(34.dp)
            )
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (armed) "Guard armed" else "Zed",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZedWhite
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when {
                        listening -> "LISTENING"
                        speaking -> "SPEAKING"
                        thinking -> "WORKING"
                        else -> "READY"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Waveform(
                active = active,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.titleSmall,
                color = ZedWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onListen,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZedWhite,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (speaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (speaking) "Stop" else if (listening) "Listening" else "Tap to talk",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun CapabilityRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        if (granted) {
            Text(
                text = "Ready",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        } else {
            TextButton(onClick = onGrant) {
                Text("Allow", style = MaterialTheme.typography.labelLarge, color = ZedWhite)
            }
        }
    }
}

@Composable
fun QuickAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, ZedGlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f))
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ZedWhite,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = ZedGlassBorder
            )
        )
    }
}

@Composable
fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = Color.White.copy(alpha = 0.07f)
    )
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.4f) else ZedGlassBorder,
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ZedWhite else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
