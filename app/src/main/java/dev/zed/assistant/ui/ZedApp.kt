package dev.zed.assistant.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.theme.ZedBackground
import dev.zed.assistant.ui.theme.ZedTheme

private enum class Screen { KEYS, SETUP, HOME, SETTINGS }

@Composable
fun ZedApp(
    prefs: ZedPrefs,
    onRequestCapability: (ZedPermissions.Capability) -> Unit,
    onRequestProjection: () -> Unit
) {
    val context = LocalContext.current
    var screen by remember {
        mutableStateOf(
            when {
                !prefs.keysConfigured -> Screen.KEYS
                !prefs.onboardingDone -> Screen.SETUP
                else -> Screen.HOME
            }
        )
    }

    ZedTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZedBackground)
        ) {
            // Glass needs light behind it: slow monochrome pools drifting on black.
            AmbientBackdrop()

            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(420),
                    label = "screens"
                ) { current ->
                    when (current) {
                        Screen.KEYS -> KeysScreen(
                            prefs = prefs,
                            onDone = {
                                screen = Screen.SETUP
                                if (ZedCoreService.isRunning) ZedCoreService.reload(context)
                            }
                        )
                        Screen.SETUP -> SetupScreen(
                            onCapabilityClick = onRequestCapability,
                            onRequestProjection = onRequestProjection,
                            onDone = {
                                prefs.onboardingDone = true
                                screen = Screen.HOME
                                if (ZedPermissions.hasMic(context)) {
                                    runCatching { ZedCoreService.start(context) }
                                }
                            }
                        )
                        Screen.HOME -> HomeScreen(
                            prefs = prefs,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onOpenSetup = { screen = Screen.SETUP },
                            onRequestProjection = onRequestProjection,
                            onRequestOverlay = {
                                ZedPermissions.requestOverlayPermission(context)
                            }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            prefs = prefs,
                            onBack = { screen = Screen.HOME },
                            onOpenSetup = { screen = Screen.SETUP },
                            onRequestProjection = onRequestProjection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientBackdrop() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    LightPool(
        modifier = Modifier.offset(x = (-100 + 40 * drift).dp, y = (-60 + 70 * drift).dp),
        size = 360,
        alpha = 0.15f
    )
    LightPool(
        modifier = Modifier.offset(x = (170 - 50 * drift).dp, y = (330 + 60 * drift).dp),
        size = 420,
        alpha = 0.09f
    )
    LightPool(
        modifier = Modifier.offset(x = (-70 + 30 * drift).dp, y = (600 - 40 * drift).dp),
        size = 300,
        alpha = 0.06f
    )
}

@Composable
private fun LightPool(modifier: Modifier, size: Int, alpha: Float) {
    Box(
        modifier = modifier
            .size(size.dp)
            .blur(96.dp)
            .background(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = alpha), Color.Transparent)
                ),
                shape = CircleShape
            )
    )
}
