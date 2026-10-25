package dev.zed.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zed.assistant.ui.theme.ZedCyan
import dev.zed.assistant.ui.theme.ZedGold
import dev.zed.assistant.ui.theme.ZedMuted
import dev.zed.assistant.ui.theme.ZedRed

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = ZedMuted,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun ReactorOrb(
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    armed: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "reactor")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val active = listening || speaking || thinking
    val accent = when {
        armed -> ZedRed
        speaking -> ZedGold
        listening -> ZedCyan
        thinking -> Color(0xFF9B7BFF)
        else -> ZedCyan
    }
    val scale = if (active) pulse else 1f

    Box(
        modifier = modifier
            .size(230.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = radius * 0.86f,
                center = center
            )
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = accent.copy(alpha = 0.28f),
                radius = radius * 0.62f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            rotate(rotation, center) {
                val arcSize = Size(radius * 1.7f, radius * 1.7f)
                val topLeft = Offset(center.x - arcSize.width / 2f, center.y - arcSize.height / 2f)
                drawArc(
                    color = accent,
                    startAngle = 0f,
                    sweepAngle = 62f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent.copy(alpha = 0.65f),
                    startAngle = 170f,
                    sweepAngle = 34f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent.copy(alpha = 0.4f),
                    startAngle = 250f,
                    sweepAngle = 18f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, accent.copy(alpha = 0.75f), accent.copy(alpha = 0.15f)),
                    center = center,
                    radius = radius * 0.42f
                ),
                radius = radius * 0.42f,
                center = center
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (armed) "ARMED" else "ZED",
                color = Color(0xFF04121A),
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                letterSpacing = 4.sp
            )
            Text(
                text = label,
                color = Color(0xFF04222C),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    active: Boolean,
    accent: Color = ZedCyan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (active) accent else ZedMuted, RoundedCornerShape(50))
        )
        Text(
            text = label,
            color = if (active) accent else ZedMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = description,
                color = ZedMuted,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        if (granted) {
            Text(
                text = "READY",
                color = ZedCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        } else {
            TextButton(onClick = onGrant) {
                Text("GRANT", color = ZedGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickActionChip(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    )
}
