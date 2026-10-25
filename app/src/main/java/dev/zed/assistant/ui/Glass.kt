package dev.zed.assistant.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.zed.assistant.ui.theme.ZedGlassBorder

/**
 * Frosted glass with real refraction.
 *
 * The screen content behind the panel is recorded once into a [GraphicsLayer]; this surface
 * samples that layer, blurs it with a render effect and tints it, then draws its children on
 * top. On Android 11 and older there is no render effect, so it degrades to the translucent
 * scrim the app used before instead of pretending.
 */
@Composable
fun rememberBackdropLayer(): GraphicsLayer = rememberGraphicsLayer()

@Composable
fun BackdropGlass(
    layer: GraphicsLayer,
    offset: IntOffset,
    shape: Shape,
    modifier: Modifier = Modifier,
    blur: Dp = 26.dp,
    tint: Float = 0.52f,
    content: @Composable BoxScope.() -> Unit
) {
    val radiusPx = with(LocalDensity.current) { blur.toPx() }
    Box(modifier = modifier.clip(shape)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radiusPx > 0f) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(radiusPx, radiusPx)
                        clip = true
                    }
            ) {
                translate(-offset.x.toFloat(), -offset.y.toFloat()) {
                    drawLayer(layer)
                }
            }
        }
        // Tint and sheen sit above the blurred sample, never inside the blur.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = tint))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(modifier = Modifier.border(1.dp, ZedGlassBorder, shape)) {
            content()
        }
    }
}

/** Offset of a composable inside its parent, for glass panels overlaying recorded content. */
@Composable
fun Modifier.trackOffset(onChange: (IntOffset) -> Unit): Modifier =
    this.onGloballyPositioned { onChange(it.positionInParent().round()) }
