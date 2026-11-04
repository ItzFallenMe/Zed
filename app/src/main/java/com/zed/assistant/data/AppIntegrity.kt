package com.zed.assistant.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.zed.assistant.BuildConfig
import java.security.MessageDigest

/**
 * Optional signing-certificate pinning for creator master control.
 *
 * Set `ZED_SIGNING_CERT_SHA256` (or the matching Gradle property) to a
 * colon-free SHA-256 of the release certificate. When it is blank, the build
 * is not pinned and master control reports `signature_pinning_not_configured`
 * only in diagnostics, not as a hard failure.
 */
object AppIntegrity {

  private const val TAG = "ZedIntegrity"

  data class Status(
    val packageName: String,
    val actualSha256: String?,
    val expectedSha256: String?,
    val pinned: Boolean,
    val matches: Boolean
  )

  fun status(context: Context): Status {
    val expected = BuildConfig.EXPECTED_SIGNATURE_SHA256
      .replace(":", "")
      .replace(" ", "")
      .lowercase()
      .takeIf { it.isNotBlank() }
    val actual = signatureSha256(context)
    return Status(
      packageName = context.packageName,
      actualSha256 = actual,
      expectedSha256 = expected,
      pinned = expected != null,
      matches = expected == null || actual == expected
    )
  }

  /** True when the build is unpinned or the installed certificate matches the pin. */
  fun isTrusted(context: Context): Boolean = status(context).matches

  fun signatureSha256(context: Context): String? {
    return try {
      val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.packageManager.getPackageInfo(
          context.packageName,
          PackageManager.GET_SIGNING_CERTIFICATES
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
      }
      val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty()
      } else {
        @Suppress("DEPRECATION")
        info.signatures.orEmpty()
      }
      if (signatures.isEmpty()) return null
      val digest = MessageDigest.getInstance("SHA-256")
      signatures
        .map { it.toByteArray() }
        .sortedWith { left, right -> left.contentCompareTo(right) }
        .forEach(digest::update)
      digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    } catch (error: Exception) {
      Log.w(TAG, "Could not read the app signing certificate", error)
      null
    }
  }

  private fun ByteArray.contentCompareTo(other: ByteArray): Int {
    val limit = minOf(size, other.size)
    for (index in 0 until limit) {
      val comparison = this[index].compareTo(other[index])
      if (comparison != 0) return comparison
    }
    return size.compareTo(other.size)
  }
}
