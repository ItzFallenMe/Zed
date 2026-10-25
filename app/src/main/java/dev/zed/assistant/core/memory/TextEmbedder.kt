package dev.zed.assistant.core.memory

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline fallback embedding: hashed word unigrams and bigrams with sublinear term
 * frequency. It is lexical rather than truly semantic, but it is deterministic, instant,
 * costs nothing, and keeps memory search working with no connection and no key.
 *
 * Pure Kotlin so it can be unit tested.
 */
object TextEmbedder {

    const val DIMENSIONS = 256

    fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val words = tokenize(text)
        if (words.isEmpty()) return vector

        val counts = HashMap<String, Int>()
        words.forEach { counts.merge("w:$it", 1, Int::plus) }
        words.zipWithNext { a, b -> counts.merge("b:$a $b", 1, Int::plus) }

        for ((token, count) in counts) {
            val weight = if (token.startsWith("b:")) 0.55f else 1f
            val value = weight * (1f + ln(count.toFloat()))
            val hash = fnv1a(token)
            val index = (hash and 0x7FFFFFFF) % DIMENSIONS
            // Sign trick: identical tokens always land the same way, collisions cancel out.
            val sign = if (hash and 0x40000000 == 0) 1f else -1f
            vector[index] += sign * value
        }

        var norm = 0f
        for (value in vector) norm += value * value
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun tokenize(text: String): List<String> {
        val builder = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            builder.append(if (ch.isLetterOrDigit()) ch else ' ')
        }
        return builder.toString()
            .split(' ')
            .filter { it.isNotBlank() }
    }

    private fun fnv1a(token: String): Int {
        var hash = -0x7ee3623b // 2166136261
        for (ch in token) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash
    }
}
