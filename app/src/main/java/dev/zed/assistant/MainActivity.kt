package dev.zed.assistant

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.security.ZedDeviceAdminReceiver
import dev.zed.assistant.service.ZedCoreService
import dev.zed.assistant.ui.ZedApp

class MainActivity : ComponentActivity() {

    private val prefs: ZedPrefs by lazy { ZedPrefs(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        ZedState.log("PERM", grants.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
        maybeStartCore()
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            try {
                ZedCoreService.startProjection(this, result.resultCode, data)
            } catch (t: Throwable) {
                ZedState.log("VISION", "Projection start failed: ${t.message}")
            }
        } else {
            ZedState.log("VISION", "Screen capture consent denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            ZedApp(
                prefs = prefs,
                onRequestCapability = ::requestCapability,
                onRequestProjection = ::requestProjection,
                onRequestDeviceAdmin = ::requestDeviceAdmin
            )
        }
    }

    override fun onResume() {
        super.onResume()
        maybeStartCore()
    }

    private fun maybeStartCore() {
        if (!prefs.onboardingDone) return
        if (!ZedPermissions.hasMic(this)) return
        if (ZedCoreService.isRunning) return
        runCatching { ZedCoreService.start(this) }
            .onFailure { ZedState.log("SVC", "Start failed: ${it.message}") }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ZedPermissions.hasNotificationPermission(this)) return
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun requestCapability(capability: ZedPermissions.Capability) {
        val permissions = when (capability) {
            ZedPermissions.Capability.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
            ZedPermissions.Capability.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyArray()
                }
            ZedPermissions.Capability.CALLS -> arrayOf(Manifest.permission.CALL_PHONE)
            ZedPermissions.Capability.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
            ZedPermissions.Capability.CAMERA -> arrayOf(Manifest.permission.CAMERA)
            else -> emptyArray()
        }
        if (permissions.isEmpty()) {
            ZedPermissions.openSettingsScreen(this, capability)
            return
        }
        permissionLauncher.launch(permissions)
    }

    private fun requestProjection() {
        if (!prefs.onboardingDone) prefs.onboardingDone = true
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        try {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (t: Throwable) {
            ZedState.log("VISION", "Screen capture unavailable: ${t.message}")
        }
    }

    private fun requestDeviceAdmin() {
        val component = ComponentName(this, ZedDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets Zed lock the screen when the anti-theft guard detects an intruder."
            )
        ZedPermissions.launchSafely(this, intent)
    }
}
