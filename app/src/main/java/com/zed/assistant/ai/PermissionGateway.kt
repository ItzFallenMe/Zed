package com.zed.assistant.ai

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
  private val lock = Any()

  @Volatile
  private var launcher: ((String) -> Unit)? = null

  private var pendingPermission: String? = null
  private var pendingContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

  fun attach(launcher: ((String) -> Unit)?) {
    this.launcher = launcher
  }

  fun onResult(permission: String, granted: Boolean) {
    Log.d(TAG, "Permission result: $permission granted=$granted")
    val continuation = synchronized(lock) {
      if (permission != pendingPermission) return
      pendingContinuation.also { clearPendingLocked() }
    }
    if (continuation?.isActive == true) continuation.resume(granted)
  }

  suspend fun ensure(context: Context, permission: String): Boolean {
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Permission already granted: $permission")
      return true
    }
    val activityLauncher = launcher
      ?: run {
        Log.e(TAG, "No Activity available to request $permission")
        return false
      }

    Log.d(TAG, "Requesting permission: $permission")
    return suspendCancellableCoroutine { continuation ->
      val accepted = synchronized(lock) {
        if (pendingContinuation?.isActive == true) {
          false
        } else {
          pendingPermission = permission
          pendingContinuation = continuation
          true
        }
      }
      if (!accepted) {
        Log.w(TAG, "Another permission request is already in flight; refusing $permission")
        continuation.resume(false)
        return@suspendCancellableCoroutine
      }

      continuation.invokeOnCancellation {
        synchronized(lock) {
          // A cancelled older request must never clear a newer request.
          if (pendingContinuation === continuation) clearPendingLocked()
        }
      }
      try {
        activityLauncher(permission)
      } catch (error: Exception) {
        Log.e(TAG, "Could not launch permission request for $permission", error)
        synchronized(lock) {
          if (pendingContinuation === continuation) clearPendingLocked()
        }
        if (continuation.isActive) continuation.resume(false)
      }
    }
  }

  private fun clearPendingLocked() {
    pendingPermission = null
    pendingContinuation = null
  }
}
