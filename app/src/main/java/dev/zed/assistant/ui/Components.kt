package dev.zed.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zed.assistant.ui.theme.ZedGlassBorder
import dev.zed.assistant.ui.theme.ZedWhite
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shared building blocks, built from Material 3 primitives (Card, ListItem, chips, filled
 * buttons) and tinted with the monochrome glass palette. Material supplies the shapes,
 * elevation, state layers and touch targets; the theme supplies the look.
 */

/** Frosted panel: a Material 3 card with a translucent container and a hairline border. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (strong) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

/** Status pill. Material's AssistChip gives it the right height and touch target. */
@Composable
fun StatusChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        },
        modifier = modifier,
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (active) {
                Color.White.copy(alpha = 0.16f)
            } else {
                Color.White.copy(alpha = 0.06f)
            },
            labelColor = if (active) ZedWhite else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = if (active) Color.White.copy(alpha = 0.28f) else ZedGlassBorder
        ),
        leadingIcon = {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = if (active) ZedWhite else Color.White.copy(alpha = 0.35f))
            }
        }
    )
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

/** Hero panel: status, avatar, waveform and the primary talk action. */
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            if (armed) Color.White.copy(alpha = 0.34f) else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (armed) "Guard armed" else "Zed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            ZedAvatar(
                mood = when {
                    armed -> AvatarMood.GUARDED
                    thinking -> AvatarMood.THINKING
                    speaking -> AvatarMood.SPEAKING
                    listening -> AvatarMood.LISTENING
                    else -> AvatarMood.IDLE
                },
                size = 124
            )
            Spacer(modifier = Modifier.height(14.dp))
            Waveform(
                active = active,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(18.dp))
            FilledTonalButton(
                onClick = onListen,
                shape = CircleShape
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

/** Permission row. A ListItem keeps Material's spacing and semantics. */
@Composable
fun CapabilityRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (granted) {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            } else {
                TextButton(onClick = onGrant) {
                    Text("Allow", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** Quick action chip on the home glass bar. */
@Composable
fun QuickAction(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.White.copy(alpha = 0.07f),
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = ZedGlassBorder
        ),
        leadingIcon = {
            Canvas(modifier = Modifier.size(5.dp)) {
                drawCircle(color = Color.White.copy(alpha = 0.55f))
            }
        }
    )
}

/** Settings toggle row, on a Material 3 ListItem. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = subtitle?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Selectable pill, as a Material 3 FilterChip. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.06f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/** Small filled icon button used by the composer. */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    contentComposable: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContentColor = Color.White.copy(alpha = 0.4f)
        ),
        content = contentComposable
    )
}
