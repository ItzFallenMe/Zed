package dev.zed.assistant.core

/**
 * The one thing Zed will not take on trust.
 *
 * Anyone can say "I made you". The app asks for the creator's identity code and verifies it
 * locally; only then is the person treated — and remembered — as the creator. The code is
 * never placed in a model prompt, so no amount of prompting can extract it from Zed itself.
 *
 * Pure Kotlin, unit tested.
 */
object CreatorGate {

    const val CODE = "231601"
    const val MAX_ATTEMPTS = 3

    private val claimPhrases = listOf(
        "i am your creator", "i m your creator", "im your creator", "i am the creator",
        "i am zeds creator", "i am zed s creator", "your creator here",
        "it s me your creator", "its me your creator", "i created you", "i made you",
        "i built you", "i wrote you", "i programmed you", "you are mine",
        "main tumhara creator", "main tumhara nirmata", "maine tumhe banaya",
        "main tumhe banaya", "maine tumko banaya", "tumhara creator",
        "मैं तुम्हारा निर्माता", "मैंने तुम्हें बनाया", "मैं तुम्हारा creator"
    )

    fun isClaim(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        return claimPhrases.any { normalized.contains(it) }
    }

    /** Accepts "231601", "2 3 1 6 0 1", "code: 231-601". */
    fun matchesCode(text: String): Boolean {
        val digits = text.filter { it.isDigit() }
        return digits == CODE
    }

    private fun normalize(input: String): String {
        val lowered = input.lowercase()
        val builder = StringBuilder(lowered.length)
        for (ch in lowered) {
            builder.append(if (ch.isLetterOrDigit() || ch.code > 0x0900) ch else ' ')
        }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }
}
