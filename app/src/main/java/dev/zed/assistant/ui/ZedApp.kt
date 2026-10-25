package dev.zed.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ZedBackground,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
