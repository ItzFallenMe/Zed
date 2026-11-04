package com.zed.assistant.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridges a coroutine request for a runtime permission to the Activity that is
 * allowed to show the system dialog. Only one request is outstanding at a time.
 */
object PermissionGateway {

  private const val TAG = "ZedPermissions"

  @Volatile
  private var launcher: ((String) -> Unit)? = null

  @Volatile
  private var pendingPermission: String? = null

  @Volatile
  private var pendingContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

  fun attach(launcher: ((String) -> Unit)?) {
    this.launcher = launcher
  }

  fun onResult(permission: String, granted: Boolean) {
    Log.d(TAG, "Permission result: $permission granted=$granted")
    if (permission == pendingPermission) {
      val continuation = pendingContinuation
      clearPending()
      if (continuation?.isActive == true) continuation.resume(granted)
    }
  }

  suspend fun ensure(context: Context, permission: String): Boolean {
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Permission already granted: $permission")
      return true
    }
    val activityLauncher = launcher
    if (activityLauncher == null) {
      Log.e(TAG, "No Activity available to request $permission")
      return false
    }
    if (pendingContinuation?.isActive == true) {
      Log.w(TAG, "Another permission request is already in flight; refusing $permission")
      return false
    }
    Log.d(TAG, "Requesting permission: $permission")
    return suspendCancellableCoroutine { continuation ->
      pendingPermission = permission
      pendingContinuation = continuation
      continuation.invokeOnCancellation { clearPending() }
      activityLauncher(permission)
    }
  }

  private fun clearPending() {
    pendingPermission = null
    pendingContinuation = null
  }
}
