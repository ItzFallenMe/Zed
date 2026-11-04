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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zed.assistant.ai.PermissionGateway
import com.zed.assistant.ui.MainScreen
import com.zed.assistant.ui.SettingsScreen
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
      ZedTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          ZedApp(viewModel)
        }
      }
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
private fun ZedApp(viewModel: MainViewModel) {
  var showSettings by rememberSaveable { mutableStateOf(false) }

  if (showSettings) {
    SettingsScreen(
      viewModel = viewModel,
      onBack = { showSettings = false }
    )
  } else {
    MainScreen(
      viewModel = viewModel,
      onSettingsClick = { showSettings = true }
    )
  }
}
