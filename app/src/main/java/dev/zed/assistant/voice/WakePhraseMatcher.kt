package dev.zed.assistant.voice

/**
 * Pure text matcher for the wake phrase. Kept free of Android imports so it can be
 * unit tested. Recognisers rarely produce the exact spelling of a wake phrase, so we
 * accept common near-misses ("bolo jed", "बोलो ज़ेड") and a bare assistant name.
 */
object WakePhraseMatcher {

    private val assistantAliases = listOf(
        "zed", "jed", "zad", "zhed", "jhed", "said zed",
        "जेड", "ज़ेड", "जेड", "जेड"
    )

    private val triggers = listOf(
        "bolo", "bolo", "bola", "bole", "hey", "ok", "okay", "arre", "sun", "suno", "oi",
        "बोलो", "बोल", "अरे", "सुनो", "हे"
    )

    private val extraWakePhrases = listOf("jarvis", "जार्विस", "hey jarvis")

    fun matches(transcript: String, phrase: String): Boolean {
        val text = normalize(transcript)
        if (text.isBlank()) return false

        val configured = normalize(phrase).ifBlank { "bolo zed" }
        if (text.contains(configured)) return true

        if (extraWakePhrases.any { text.contains(normalize(it)) }) return true

        val hasTrigger = triggers.any { text.contains(normalize(it)) }
        val hasName = assistantAliases.any { alias -> containsWordLike(text, normalize(alias)) }
        if (hasName) {
            // "zed" alone is allowed because the recogniser often drops "bolo".
            return true
        }
        if (hasTrigger && text.contains("zed")) return true
        return false
    }

    private fun containsWordLike(text: String, token: String): Boolean {
        if (token.isBlank()) return false
        if (text.contains(token)) return true
        // Allow a single character error on longer names (jed / sed / zad already listed).
        return false
    }

    fun normalize(input: String): String {
        val lowered = input.lowercase()
        val builder = StringBuilder(lowered.length)
        for (ch in lowered) {
            if (ch.isLetterOrDigit() || ch.code > 0x0900) {
                builder.append(ch)
            } else {
                builder.append(' ')
            }
        }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }
}
