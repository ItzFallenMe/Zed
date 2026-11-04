package com.zed.assistant.data

import java.util.Locale

/**
 * Pure, testable rules for creator recognition. Kept free of Android types so
 * the phrase detector and the spoken-code normaliser can be unit tested.
 *
 * This is deliberately stricter than a simple "contains creator" check: a
 * question such as "who is your creator?" must never open the challenge.
 */
object CreatorGate {

  const val DEFAULT_CODE = "160123"
  const val MIN_CODE_LENGTH = 4
  const val MAX_CODE_LENGTH = 12

  private val TRIGGER_PATTERNS = listOf(
    // "I am your creator", "main tumhara creator hoon", "I'm the maker".
    Regex(
      "\\b(?:i\\s*am|i'm|im|main|mai|mein)\\s+" +
        "(?:(?:the|your|tumhara|tumhari|aapka|aapki|tera|teri)\\s+)?" +
        "(?:creator|maker|master|boss)\\b"
    ),
    // "Activate creator mode", "enable master control".
    Regex(
      "\\b(?:activate|enable|enter|engage|start)\\s+(?:the\\s+)?" +
        "(?:creator|master)\\s+(?:mode|control|access|override)\\b"
    ),
    // "Active master control" / "activate master control".
    Regex("\\b(?:active|activate)\\s+master\\s+control\\b"),
    // A bare command: "Zed, creator access", "master control".
    Regex(
      "^\\s*(?:(?:hey|ok|okay)\\s+)?(?:zed[, ]+)?(?:creator|master)\\s+" +
        "(?:mode|control|access|override)\\s*$"
    )
  )

  private val CODE_KEYWORD = Regex("\\b(?:access\\s+code|code|passcode|pin|password)\\b")

  private val WORD_DIGITS = mapOf(
    "zero" to "0", "oh" to "0", "o" to "0",
    "one" to "1", "won" to "1",
    "two" to "2", "to" to "2", "too" to "2",
    "three" to "3", "tree" to "3",
    "four" to "4", "for" to "4",
    "five" to "5",
    "six" to "6", "sex" to "6",
    "seven" to "7",
    "eight" to "8", "ate" to "8",
    "nine" to "9",
    "ten" to "10"
  )

  fun isCreatorRequest(raw: String): Boolean {
    val text = raw.lowercase(Locale.ROOT).trim()
    if (text.isBlank()) return false
    return TRIGGER_PATTERNS.any { it.containsMatchIn(text) }
  }

  fun isValidCodeFormat(raw: String): Boolean =
    raw.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH && raw.all(Char::isDigit)

  /**
   * Turns what speech recognition heard into the digits the user meant.
   * Handles "160123", "160 123", "one six zero one two three", and
   * "my code is one-six-zero-one-two-three".
   */
  fun normalizeCodeInput(raw: String): String? {
    val text = raw.lowercase(Locale.ROOT).replace('-', ' ')
    if (text.isBlank()) return null

    val keyword = CODE_KEYWORD.find(text)
    val segment = if (keyword != null) text.substring(keyword.range.last + 1) else text
    val tokens = Regex("[a-z]+|\\d+").findAll(segment).map { it.value }.toList()
    if (tokens.isEmpty()) return null

    // Choose the longest uninterrupted run of number words or digit groups.
    // This avoids gluing an unrelated age or quantity to the code in sentences
    // such as "I have 2 cats and 160123". The keyword only narrows the search
    // window; it does not turn every number in the sentence into one code.
    var best = ""
    val current = StringBuilder()
    for (token in tokens) {
      val digits = digitValue(token)
      if (digits != null) {
        current.append(digits)
      } else {
        if (current.length > best.length) best = current.toString()
        current.clear()
      }
    }
    if (current.length > best.length) best = current.toString()
    return best.takeIf(::isValidCodeFormat)
  }

  private fun digitValue(token: String): String? {
    if (token.all(Char::isDigit)) return token
    return WORD_DIGITS[token]
  }
}
