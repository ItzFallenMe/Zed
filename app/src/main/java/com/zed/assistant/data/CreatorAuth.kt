package com.zed.assistant.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local creator authentication.
 *
 * The code is never sent to Gemini and never stored in plaintext. A custom code
 * is hashed with PBKDF2-HMAC-SHA256 and the hash is encrypted with the Android
 * Keystore via [SecureStore]. The shipped 160123 default remains usable until
 * the creator changes it, and the UI recommends changing it after first use.
 *
 * Three wrong attempts trigger a persisted one-minute lockout, which also
 * survives an app restart.
 */
object CreatorAuth {

  private const val TAG = "ZedCreatorAuth"
  private const val PREFS = "zed_creator"
  private const val KEY_UNLOCKED = "unlocked"
  private const val KEY_CODE_HASH = "code_hash"
  private const val KEY_FAILURES = "failures"
  private const val KEY_LOCK_UNTIL = "lock_until"

  private const val ITERATIONS = 120_000
  private const val KEY_BITS = 256
  private const val SALT_BYTES = 16
  private const val MAX_FAILURES = 3
  private const val LOCKOUT_MS = 60_000L
  private const val HASH_VERSION = "v1"

  sealed interface Result {
    data object Success : Result
    data object InvalidFormat : Result
    data class Failure(val attemptsLeft: Int, val lockedSeconds: Long = 0L) : Result
  }

  fun isUnlocked(context: Context): Boolean =
    prefs(context).getBoolean(KEY_UNLOCKED, false)

  fun hasCustomCode(context: Context): Boolean =
    prefs(context).getString(KEY_CODE_HASH, null)?.isNotBlank() == true

  fun lock(context: Context) {
    prefs(context).edit { putBoolean(KEY_UNLOCKED, false) }
  }

  fun verify(context: Context, rawCode: String): Result {
    val code = CreatorGate.normalizeCodeInput(rawCode)
      ?: return Result.InvalidFormat
    if (!CreatorGate.isValidCodeFormat(code)) return Result.InvalidFormat

    val store = prefs(context)
    synchronized(store) {
      val now = System.currentTimeMillis()
      var failures = store.getInt(KEY_FAILURES, 0)
      var lockUntil = store.getLong(KEY_LOCK_UNTIL, 0L)

      if (lockUntil > 0L && now >= lockUntil) {
        failures = 0
        lockUntil = 0L
        store.edit {
          putInt(KEY_FAILURES, 0)
          putLong(KEY_LOCK_UNTIL, 0L)
        }
      }
      if (lockUntil > now) {
        return Result.Failure(0, (lockUntil - now + 999L) / 1000L)
      }

      if (matches(context, code)) {
        store.edit {
          putBoolean(KEY_UNLOCKED, true)
          putInt(KEY_FAILURES, 0)
          putLong(KEY_LOCK_UNTIL, 0L)
        }
        return Result.Success
      }

      failures += 1
      if (failures >= MAX_FAILURES) {
        lockUntil = now + LOCKOUT_MS
        store.edit {
          putInt(KEY_FAILURES, 0)
          putLong(KEY_LOCK_UNTIL, lockUntil)
        }
        return Result.Failure(0, LOCKOUT_MS / 1000L)
      }

      store.edit { putInt(KEY_FAILURES, failures) }
      return Result.Failure(MAX_FAILURES - failures)
    }
  }

  /** Stores a new numeric code. Returns false if the Keystore is unavailable. */
  fun setCode(context: Context, rawCode: String): Boolean {
    val code = CreatorGate.normalizeCodeInput(rawCode) ?: return false
    if (!CreatorGate.isValidCodeFormat(code)) return false

    val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    val hash = derive(code, salt, ITERATIONS)
    val payload = listOf(
      HASH_VERSION,
      ITERATIONS.toString(),
      Base64.encodeToString(salt, Base64.NO_WRAP),
      Base64.encodeToString(hash, Base64.NO_WRAP)
    ).joinToString(":")

    val encrypted = SecureStore.encrypt(payload)
    if (encrypted == null) {
      Log.e(TAG, "Could not encrypt the creator code hash")
      return false
    }
    prefs(context).edit {
      putString(KEY_CODE_HASH, encrypted)
      putBoolean(KEY_UNLOCKED, true)
      putInt(KEY_FAILURES, 0)
      putLong(KEY_LOCK_UNTIL, 0L)
    }
    return true
  }

  private fun matches(context: Context, code: String): Boolean {
    val stored = prefs(context).getString(KEY_CODE_HASH, null)
    if (stored.isNullOrBlank()) {
      return constantTimeEquals(code, CreatorGate.DEFAULT_CODE)
    }

    val payload = SecureStore.decrypt(stored) ?: return false
    val parts = payload.split(":")
    if (parts.size != 4 || parts[0] != HASH_VERSION) return false
    val iterations = parts[1].toIntOrNull() ?: return false
    val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
    val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
    if (salt.isEmpty() || expected.isEmpty()) return false

    return MessageDigest.isEqual(derive(code, salt, iterations), expected)
  }

  private fun derive(code: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(code.toCharArray(), salt, iterations, KEY_BITS)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
