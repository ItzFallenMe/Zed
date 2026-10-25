package dev.zed.assistant.voice

/**
 * Pure text matcher for the wake word. Kept free of Android imports so it can be unit
 * tested. Recognisers rarely produce the exact spelling of a short name, so all the
 * spellings "zed" is usually transcribed as are accepted, with or without a "hey".
 */
object WakePhraseMatcher {

    /** Every spelling speech engines return for the name Zed. */
    private val assistantAliases = listOf(
        "zed", "zad", "zade", "zayd", "zhead", "jed", "shed",
        "जेड", "ज़ेड", "ज़ेड", "जैड"
    )

    /** "hey zed", "ok zed", "अरे ज़ेड" and friends: the name alone is enough. */
    private val triggers = listOf(
        "hey", "hi", "ok", "okay", "yo", "arre", "arey", "suno", "sun", "oi",
        "हे", "अरे", "सुनो", "ओए"
    )

    fun matches(transcript: String, phrase: String): Boolean {
        val text = normalize(transcript)
        if (text.isBlank()) return false

        val configured = normalize(phrase).ifBlank { DEFAULT_PHRASE }
        if (containsWordLike(text, configured)) return true

        // The wake word is the assistant's name, so the name alone always wakes Zed.
        if (assistantAliases.any { containsWordLike(text, normalize(it)) }) return true

        // Tolerate the recogniser splitting a leading trigger into its own word.
        return triggers.any { trigger ->
            text.startsWith(normalize(trigger)) &&
                assistantAliases.any { text.contains(normalize(it)) }
        }
    }

    /**
     * Drops a wake phrase from the front of a transcript, so the model never receives the
     * bare name as if it were a request: "hey zed, what's the time" → "what's the time".
     * Returns an empty string when the name was all that was said.
     */
    fun stripWake(transcript: String, phrase: String): String {
        val configured = normalize(phrase).ifBlank { DEFAULT_PHRASE }
        val wakeTokens = (triggers + assistantAliases + configured.split(' '))
            .flatMap { normalize(it).split(' ') }
            .filter { it.isNotBlank() }
            .toSet()
        val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var index = 0
        while (index < words.size) {
            val word = normalize(words[index])
            if (word.isBlank() || word !in wakeTokens) break
            index++
        }
        return words.drop(index)
            .joinToString(" ")
            .trim()
            .trim(',', '.', '!', '?', '।', ';', ':')
            .trim()
    }

    private fun containsWordLike(text: String, token: String): Boolean {
        if (token.isBlank()) return false
        var index = text.indexOf(token)
        while (index >= 0) {
            val before = index == 0 || !text[index - 1].isLetterOrDigit()
            val afterIndex = index + token.length
            val after = afterIndex >= text.length || !text[afterIndex].isLetterOrDigit()
            if (before && after) return true
            index = text.indexOf(token, index + 1)
        }
        return false
    }

    const val DEFAULT_PHRASE = "zed"

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
