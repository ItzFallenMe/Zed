package dev.zed.assistant.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Local-only settings store. Nothing here ever leaves the device.
 */
class ZedPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = sp.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = sp.edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()

    var wakeEnabled: Boolean
        get() = sp.getBoolean(KEY_WAKE_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_WAKE_ENABLED, value).apply()

    var wakePhrase: String
        get() = sp.getString(KEY_WAKE_PHRASE, "bolo zed") ?: "bolo zed"
        set(value) = sp.edit().putString(KEY_WAKE_PHRASE, value.trim().lowercase()).apply()

    var speakReplies: Boolean
        get() = sp.getBoolean(KEY_SPEAK_REPLIES, true)
        set(value) = sp.edit().putBoolean(KEY_SPEAK_REPLIES, value).apply()

    var assistantName: String
        get() = sp.getString(KEY_NAME, "Zed") ?: "Zed"
        set(value) = sp.edit().putString(KEY_NAME, value.trim().ifBlank { "Zed" }).apply()

    var customInstructions: String
        get() = sp.getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
        set(value) = sp.edit().putString(KEY_CUSTOM_INSTRUCTIONS, value.take(2000)).apply()

    var language: String
        get() = sp.getString(KEY_LANGUAGE, "hi-IN") ?: "hi-IN"
        set(value) = sp.edit().putString(KEY_LANGUAGE, value).apply()

    var bestVoice: Boolean
        get() = sp.getBoolean(KEY_BEST_VOICE, true)
        set(value) = sp.edit().putBoolean(KEY_BEST_VOICE, value).apply()

    /** Exact system TTS voice name; blank means "pick the best". */
    var ttsVoiceName: String
        get() = sp.getString(KEY_TTS_VOICE, "") ?: ""
        set(value) = sp.edit().putString(KEY_TTS_VOICE, value.trim()).apply()

    /** Gemini Live prebuilt voice name. */
    var liveVoiceName: String
        get() = sp.getString(KEY_LIVE_VOICE_NAME, "Puck") ?: "Puck"
        set(value) = sp.edit().putString(KEY_LIVE_VOICE_NAME, value.trim().ifBlank { "Puck" }).apply()

    var liveVoice: Boolean
        get() = sp.getBoolean(KEY_LIVE_VOICE, true)
        set(value) = sp.edit().putBoolean(KEY_LIVE_VOICE, value).apply()

    var cloudBrain: Boolean
        get() = sp.getBoolean(KEY_CLOUD_BRAIN, true)
        set(value) = sp.edit().putBoolean(KEY_CLOUD_BRAIN, value).apply()

    var keepHistory: Boolean
        get() = sp.getBoolean(KEY_KEEP_HISTORY, true)
        set(value) = sp.edit().putBoolean(KEY_KEEP_HISTORY, value).apply()

    var antiTheftArmed: Boolean
        get() = sp.getBoolean(KEY_ANTITHEFT, false)
        set(value) = sp.edit().putBoolean(KEY_ANTITHEFT, value).apply()

    var motionSensitivity: Int
        get() = sp.getInt(KEY_SENSITIVITY, 2)
        set(value) = sp.edit().putInt(KEY_SENSITIVITY, value).apply()

    var floatingBubble: Boolean
        get() = sp.getBoolean(KEY_FLOATING_BUBBLE, false)
        set(value) = sp.edit().putBoolean(KEY_FLOATING_BUBBLE, value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var flashlightOn: Boolean
        get() = sp.getBoolean(KEY_TORCH, false)
        set(value) = sp.edit().putBoolean(KEY_TORCH, value).apply()

    var lastProjectionAt: Long
        get() = sp.getLong(KEY_PROJECTION_AT, 0L)
        set(value) = sp.edit().putLong(KEY_PROJECTION_AT, value).apply()

    companion object {
        const val NAME = "zed_prefs"
        const val DEFAULT_MODEL = "gemini-2.0-flash-live-001"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_WAKE_ENABLED = "wake_enabled"
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_SPEAK_REPLIES = "speak_replies"
        private const val KEY_NAME = "assistant_name"
        private const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_BEST_VOICE = "best_voice"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_LIVE_VOICE_NAME = "live_voice_name"
        private const val KEY_LIVE_VOICE = "live_voice"
        private const val KEY_CLOUD_BRAIN = "cloud_brain"
        private const val KEY_KEEP_HISTORY = "keep_history"
        private const val KEY_ANTITHEFT = "anti_theft"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_FLOATING_BUBBLE = "floating_bubble"
        private const val KEY_TORCH = "torch"
        private const val KEY_PROJECTION_AT = "projection_at"
    }
}
