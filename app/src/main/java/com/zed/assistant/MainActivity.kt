package com.zed.assistant

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.zed.assistant.ai.PermissionGateway
import com.zed.assistant.ui.SettingsScreen
import com.zed.assistant.ui.ZedBottomDock
import com.zed.assistant.ui.ZedChatScreen
import com.zed.assistant.ui.ZedHomeScreen
import com.zed.assistant.ui.ZedTab
import com.zed.assistant.ui.ZedUpdatesScreen
import com.zed.assistant.ui.theme.ZedTheme
import com.zed.assistant.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()
  private var requestedPermission: String? = null

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val permission = requestedPermission
      requestedPermission = null
      if (permission != null) PermissionGateway.onResult(permission, granted)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    PermissionGateway.attach { permission ->
      // PermissionGateway.ensure is called from IO coroutines, but
      // ActivityResultLauncher.launch must run on the main thread.
      runOnUiThread {
        if (isFinishing || isDestroyed) {
          PermissionGateway.onResult(permission, false)
          return@runOnUiThread
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
          PermissionGateway.onResult(permission, true)
        } else {
          requestedPermission = permission
          try {
            permissionLauncher.launch(permission)
          } catch (error: Exception) {
            Log.e("Zed", "Could not request permission $permission", error)
            PermissionGateway.onResult(permission, false)
          }
        }
      }
    }

    setContent {
      ZedRoot(viewModel)
    }
  }

  // No onStop teardown: ZedSessionService keeps the microphone and Live session
  // alive while a device action (WhatsApp, dialer, contacts) is in the foreground.

  override fun onDestroy() {
    PermissionGateway.attach(null)
    if (isFinishing) viewModel.stopSession()
    super.onDestroy()
  }
}

@Composable
private fun ZedRoot(viewModel: MainViewModel) {
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  ZedTheme(mode = themeMode) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      ZedApp(viewModel)
    }
  }
}

@Composable
private fun ZedApp(viewModel: MainViewModel) {
  var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
  val selectedTab = ZedTab.values()[selectedTabIndex.coerceIn(0, ZedTab.values().lastIndex)]
  val state by viewModel.state.collectAsStateWithLifecycle()

  BackHandler(enabled = selectedTab != ZedTab.HOME) {
    selectedTabIndex = 0
  }

  Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    when (selectedTab) {
      ZedTab.HOME -> ZedHomeScreen(
        viewModel = viewModel,
        onChat = { selectedTabIndex = ZedTab.CHAT.ordinal },
        onSettings = { selectedTabIndex = ZedTab.SETTINGS.ordinal }
      )
      ZedTab.CHAT -> ZedChatScreen(
        viewModel = viewModel,
        onBack = { selectedTabIndex = ZedTab.HOME.ordinal }
      )
      ZedTab.SETTINGS -> SettingsScreen(
        viewModel = viewModel,
        onBack = { selectedTabIndex = ZedTab.HOME.ordinal }
      )
      ZedTab.UPDATES -> ZedUpdatesScreen()
    }

    ZedBottomDock(
      selectedTab = selectedTab,
      state = state,
      onTabSelected = { selectedTabIndex = it.ordinal },
      onMicClick = { viewModel.toggleSession() },
      onMicLongClick = { viewModel.interrupt() }
    )
  }
}
