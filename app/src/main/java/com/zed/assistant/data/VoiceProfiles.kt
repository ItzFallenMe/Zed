package com.zed.assistant.data

/**
 * Named voice presets. Each one selects a distinct Gemini native voice and also
 * adds a short delivery direction to the persona, so the voice and the writing
 * style match instead of being eight names for the same performance.
 */
data class ZedVoiceProfile(
  val id: String,
  val label: String,
  val geminiVoice: String,
  val description: String,
  val style: String
)

object ZedVoiceProfiles {

  const val DEFAULT_ID = "prime"

  val ALL = listOf(
    ZedVoiceProfile(
      id = "prime",
      label = "Female · Aoede",
      geminiVoice = "Aoede",
      description = "Female-sounding voice · clear and warm.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "ev",
      label = "Female · Kore",
      geminiVoice = "Kore",
      description = "Female-sounding voice · steady and direct.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "spark",
      label = "Male · Puck",
      geminiVoice = "Puck",
      description = "Male-sounding voice · bright and quick.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "gravel",
      label = "Male · Charon",
      geminiVoice = "Charon",
      description = "Male-sounding voice · low and measured.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "sentinel",
      label = "Male · Fenrir",
      geminiVoice = "Fenrir",
      description = "Male-sounding voice · deep and steady.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "velvet",
      label = "Female · Leda",
      geminiVoice = "Leda",
      description = "Female-sounding voice · smooth and soft.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "anchor",
      label = "Male · Orus",
      geminiVoice = "Orus",
      description = "Male-sounding voice · clear and grounded.",
      style = ""
    ),
    ZedVoiceProfile(
      id = "current",
      label = "Female · Zephyr",
      geminiVoice = "Zephyr",
      description = "Female-sounding voice · airy and light.",
      style = ""
    )
  )

  fun byId(id: String?): ZedVoiceProfile =
    ALL.firstOrNull { it.id == id } ?: ALL.first()

  fun byVoiceName(name: String?): ZedVoiceProfile =
    ALL.firstOrNull { it.geminiVoice.equals(name, ignoreCase = true) } ?: ALL.first()

  /** Resolves the saved preset, falling back to a legacy raw Gemini voice name. */
  fun resolve(presetId: String?, legacyVoice: String?): ZedVoiceProfile {
    if (!presetId.isNullOrBlank()) {
      ALL.firstOrNull { it.id == presetId }?.let { return it }
    }
    return byVoiceName(legacyVoice)
  }
}
