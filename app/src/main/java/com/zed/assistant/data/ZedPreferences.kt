package com.zed.assistant.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray

/**
 * Typed access to Zed's local settings. The preference file name and the four
 * original keys are unchanged so existing installs keep their model, voice and
 * operator name. The API key is transparently migrated to Keystore-backed
 * AES-GCM the first time it is read.
 */
class ZedPreferences(context: Context) {

  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  companion object {
    const val PREFS = "zed_prefs"

    const val KEY_API_KEY = "api_key"
    const val KEY_MODEL = "gemini_model"
    const val KEY_VOICE = "gemini_voice"
    const val KEY_VOICE_PRESET = "gemini_voice_preset"
    const val KEY_USER_NAME = "user_name"
    const val KEY_LIQUID_GLASS = "liquid_glass_enabled"

    private const val TAG = "ZedPreferences"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    private const val KEY_OWNER_VOICE_ENABLED = "owner_voice_enabled"
    private const val KEY_OWNER_VOICE_PROFILE = "owner_voice_profile"
    private const val KEY_OWNER_VOICE_SAMPLES = "owner_voice_samples"
    private const val KEY_OWNER_VOICE_THRESHOLD = "owner_voice_threshold"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_OVERLAY_TOY = "overlay_toy"
    private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    private const val KEY_MEMORY_ENABLED = "memory_enabled"

    const val DEFAULT_THRESHOLD = 0.70f

    const val ROBOT_AVATAR = "robot-head"

    val OVERLAY_TOYS = listOf(
      ROBOT_AVATAR, "ghost", "alien", "blob", "cat", "circle", "cloud", "droid",
      "drop", "flower", "hexagon", "mech", "pebble", "pill", "puddle", "square",
      "star", "triangle"
    )
  }

  var apiKey: String
    get() = readSecret(KEY_API_KEY)
    set(value) = writeSecret(KEY_API_KEY, value.trim())

  /** True when the in-app key is present and safely encrypted. */
  val hasStoredApiKey: Boolean
    get() = prefs.getString(KEY_API_KEY, null)?.isNotBlank() == true

  var userName: String
    get() = prefs.getString(KEY_USER_NAME, "").orEmpty()
    set(value) = prefs.edit { putString(KEY_USER_NAME, value.trim()) }

  var model: String
    get() = prefs.getString(KEY_MODEL, "").orEmpty()
    set(value) = prefs.edit { putString(KEY_MODEL, value) }

  var voice: String
    get() = prefs.getString(KEY_VOICE, "").orEmpty()
    set(value) = prefs.edit { putString(KEY_VOICE, value) }

  var voicePreset: String
    get() = prefs.getString(KEY_VOICE_PRESET, "").orEmpty()
    set(value) = prefs.edit { putString(KEY_VOICE_PRESET, value) }

  var wakeWordEnabled: Boolean
    get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
    set(value) = prefs.edit { putBoolean(KEY_WAKE_WORD_ENABLED, value) }

  var isOwnerVoiceEnabled: Boolean
    get() = prefs.getBoolean(KEY_OWNER_VOICE_ENABLED, false)
    set(value) = prefs.edit { putBoolean(KEY_OWNER_VOICE_ENABLED, value) }

  var ownerVoiceThreshold: Float
    get() = prefs.getFloat(KEY_OWNER_VOICE_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(0.5f, 0.95f)
    set(value) = prefs.edit { putFloat(KEY_OWNER_VOICE_THRESHOLD, value.coerceIn(0.5f, 0.95f)) }

  var overlayEnabled: Boolean
    get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
    set(value) = prefs.edit { putBoolean(KEY_OVERLAY_ENABLED, value) }

  var overlayToy: String
    get() = prefs.getString(KEY_OVERLAY_TOY, ROBOT_AVATAR).orEmpty().ifBlank { ROBOT_AVATAR }
    set(value) = prefs.edit { putString(KEY_OVERLAY_TOY, value) }

  var soundEffectsEnabled: Boolean
    get() = prefs.getBoolean(KEY_SOUND_EFFECTS, true)
    set(value) = prefs.edit { putBoolean(KEY_SOUND_EFFECTS, value) }

  var memoryEnabled: Boolean
    get() = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
    set(value) = prefs.edit { putBoolean(KEY_MEMORY_ENABLED, value) }

  fun hasOwnerVoiceProfile(): Boolean = getOwnerVoiceProfile()?.isNotEmpty() == true

  fun getOwnerVoiceProfile(): FloatArray? = decodeVector(prefs.getString(KEY_OWNER_VOICE_PROFILE, null))

  fun getOwnerVoiceSamples(): List<FloatArray> {
    val raw = prefs.getString(KEY_OWNER_VOICE_SAMPLES, null) ?: return emptyList()
    return runCatching {
      val root = JSONArray(raw)
      (0 until root.length()).mapNotNull { index -> decodeVector(root.optString(index)) }
    }.getOrElse { emptyList() }
  }

  fun saveOwnerVoiceProfile(centroid: FloatArray, samples: List<FloatArray>) {
    val encodedSamples = JSONArray()
    samples.forEach { encodedSamples.put(encodeVector(it)) }
    prefs.edit {
      putString(KEY_OWNER_VOICE_PROFILE, encodeVector(centroid))
      putString(KEY_OWNER_VOICE_SAMPLES, encodedSamples.toString())
      putBoolean(KEY_OWNER_VOICE_ENABLED, true)
    }
  }

  fun clearOwnerVoiceProfile() {
    prefs.edit {
      remove(KEY_OWNER_VOICE_PROFILE)
      remove(KEY_OWNER_VOICE_SAMPLES)
      putBoolean(KEY_OWNER_VOICE_ENABLED, false)
    }
  }

  private fun readSecret(key: String): String {
    val raw = prefs.getString(key, "").orEmpty()
    if (raw.isBlank()) return ""
    if (!SecureStore.isEncrypted(raw)) {
      // Legacy plaintext key: encrypt it now, keep it working this session.
      SecureStore.encrypt(raw)?.let { encrypted ->
        prefs.edit { putString(key, encrypted) }
      }
      return raw
    }
    return SecureStore.decrypt(raw).orEmpty()
  }

  private fun writeSecret(key: String, value: String) {
    if (value.isBlank()) {
      prefs.edit { remove(key) }
      return
    }
    val encrypted = SecureStore.encrypt(value)
    if (encrypted == null) {
      Log.e(TAG, "Keystore unavailable; refusing to persist the API key in plaintext")
      return
    }
    prefs.edit { putString(key, encrypted) }
  }

  private fun encodeVector(vector: FloatArray): String {
    val array = JSONArray()
    vector.forEach { array.put(it.toDouble()) }
    return array.toString()
  }

  private fun decodeVector(raw: String?): FloatArray? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
      val array = JSONArray(raw)
      if (array.length() == 0) null
      else FloatArray(array.length()) { index -> array.getDouble(index).toFloat() }
    }.getOrNull()
  }

  @SuppressLint("ApplySharedPref")
  fun canWriteSettings(): Boolean = android.provider.Settings.System.canWrite(appContext)
}
