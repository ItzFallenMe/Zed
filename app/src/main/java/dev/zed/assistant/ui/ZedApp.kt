package dev.zed.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.ui.theme.ZedBackground
import dev.zed.assistant.ui.theme.ZedTheme

private enum class Screen { SETUP, HOME, SETTINGS }

@Composable
fun ZedApp(
    prefs: ZedPrefs,
    onRequestCapability: (ZedPermissions.Capability) -> Unit,
    onRequestProjection: () -> Unit,
    onRequestDeviceAdmin: () -> Unit
) {
    var screen by remember {
        mutableStateOf(if (prefs.onboardingDone) Screen.HOME else Screen.SETUP)
    }

    ZedTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZedBackground)
        ) {
            // Glass needs something behind it: soft monochrome light pools on black.
            LightPool(modifier = Modifier.offset(x = (-90).dp, y = (-40).dp), size = 340, alpha = 0.16f)
            LightPool(
                modifier = Modifier.offset(x = 180.dp, y = 380.dp),
                size = 400,
                alpha = 0.10f
            )
            LightPool(
                modifier = Modifier.offset(x = (-60).dp, y = 620.dp),
                size = 300,
                alpha = 0.07f
            )

            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (screen) {
                    Screen.SETUP -> SetupScreen(
                        onCapabilityClick = onRequestCapability,
                        onDeviceAdmin = onRequestDeviceAdmin,
                        onDone = {
                            prefs.onboardingDone = true
                            screen = Screen.HOME
                        }
                    )
                    Screen.HOME -> HomeScreen(
                        prefs = prefs,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenSetup = { screen = Screen.SETUP },
                        onRequestProjection = onRequestProjection
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

@Composable
private fun LightPool(modifier: Modifier, size: Int, alpha: Float) {
    Box(
        modifier = modifier
            .size(size.dp)
            .blur(90.dp)
            .background(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = alpha), Color.Transparent)
                ),
                shape = CircleShape
            )
    )
}

@Suppress("unused")
private val themeReference = MaterialTheme
