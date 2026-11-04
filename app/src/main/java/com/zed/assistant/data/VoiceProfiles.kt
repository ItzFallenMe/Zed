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
      label = "Zed Prime",
      geminiVoice = "Aoede",
      description = "Signature Zed. Warm, sharp, quietly amused.",
      style = "Speak with relaxed confidence and a small, knowing smile. Short, clean sentences."
    ),
    ZedVoiceProfile(
      id = "ev",
      label = "E.V. Mode",
      geminiVoice = "Kore",
      description = "E.V.-inspired partner. Dry, close, protective.",
      style = "Sound like a trusted partner in the room: dry, intimate, protective, and a little exasperated when the plan is stupid. Never syrupy."
    ),
    ZedVoiceProfile(
      id = "spark",
      label = "Spark",
      geminiVoice = "Puck",
      description = "Bright and quick. Best for banter and ideas.",
      style = "Bring bright, fast energy. Punchy lines, playful reactions, no rambling."
    ),
    ZedVoiceProfile(
      id = "gravel",
      label = "Gravel",
      geminiVoice = "Charon",
      description = "Low and deadpan. Every line lands dry.",
      style = "Use a low, deadpan delivery. Understatement over enthusiasm, with a razor-thin hint of humour."
    ),
    ZedVoiceProfile(
      id = "sentinel",
      label = "Sentinel",
      geminiVoice = "Fenrir",
      description = "Deep and steady. Calm in a crisis.",
      style = "Sound steady, grounded and quietly formidable. Fewer words, more weight."
    ),
    ZedVoiceProfile(
      id = "velvet",
      label = "Velvet",
      geminiVoice = "Leda",
      description = "Smooth and unhurried. Late-night company.",
      style = "Speak smoothly and unhurried, with warm restraint and space between thoughts."
    ),
    ZedVoiceProfile(
      id = "anchor",
      label = "Anchor",
      geminiVoice = "Orus",
      description = "Reassuring and clear. The practical one.",
      style = "Be clear, reassuring and practical. Calm the room without sounding clinical."
    ),
    ZedVoiceProfile(
      id = "current",
      label = "Current",
      geminiVoice = "Zephyr",
      description = "Airy and light. Quick, curious, nimble.",
      style = "Keep the delivery airy and nimble, curious and lightly teasing, with quick turns."
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
