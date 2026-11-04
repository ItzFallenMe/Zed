package com.zed.assistant.audio

import android.util.Log
import com.zed.assistant.data.ZedPreferences
import kotlin.math.sqrt
import org.json.JSONObject
import org.vosk.Recognizer

/**
 * Owner-voice verification using Vosk's x-vector speaker model, fully offline.
 * Matching is nearest-neighbour over the enrolled samples plus their centroid,
 * which is more robust than a single averaged vector across different tones.
 */
object SpeakerVerifier {

  private const val TAG = "ZedSpeaker"
  private const val SAMPLE_RATE = 16_000f
  private const val MIN_AUDIO_BYTES = 4_000 // ~125 ms

  data class Match(
    val isMatch: Boolean,
    val distance: Float,
    val similarityPercent: Int,
    val hasVoiceVector: Boolean,
    val threshold: Float
  )

  fun cosineDistance(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 1f
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (index in a.indices) {
      dot += a[index] * b[index]
      normA += a[index] * a[index]
      normB += b[index] * b[index]
    }
    if (normA <= 0.0 || normB <= 0.0) return 1f
    val cosine = (dot / (sqrt(normA) * sqrt(normB))).coerceIn(-1.0, 1.0)
    return (1.0 - cosine).toFloat().coerceIn(0f, 2f)
  }

  fun similarityPercent(distance: Float): Int =
    ((1f - distance / 1.35f) * 100f).toInt().coerceIn(0, 100)

  fun centroid(vectors: List<FloatArray>): FloatArray? {
    if (vectors.isEmpty()) return null
    val dimension = vectors.first().size
    if (dimension == 0) return null
    val average = FloatArray(dimension)
    var count = 0
    for (vector in vectors) {
      if (vector.size != dimension) continue
      for (index in 0 until dimension) average[index] += vector[index]
      count++
    }
    if (count == 0) return null
    var norm = 0.0
    for (index in 0 until dimension) {
      average[index] /= count
      norm += average[index] * average[index]
    }
    val length = sqrt(norm).toFloat()
    if (length > 0f) for (index in 0 until dimension) average[index] /= length
    return average
  }

  fun extract(pcm: ByteArray): FloatArray? {
    val model = VoskModelManager.model
    val speakerModel = VoskModelManager.speakerModel
    if (model == null || speakerModel == null) {
      Log.w(TAG, "Voice models are not ready")
      return null
    }
    if (pcm.size < MIN_AUDIO_BYTES) return null

    var recognizer: Recognizer? = null
    return try {
      recognizer = Recognizer(model, SAMPLE_RATE, speakerModel)
      var offset = 0
      val chunk = 4096
      while (offset < pcm.size) {
        val length = minOf(chunk, pcm.size - offset)
        recognizer.acceptWaveForm(pcm.copyOfRange(offset, offset + length), length)
        offset += length
      }
      // Complete the nnet3 pooling window on short utterances.
      val padding = ByteArray(12_800)
      recognizer.acceptWaveForm(padding, padding.size)

      val result = recognizer.finalResult ?: return null
      val json = JSONObject(result)
      if (!json.has("spk")) return null
      val array = json.getJSONArray("spk")
      if (array.length() == 0) null
      else FloatArray(array.length()) { index -> array.getDouble(index).toFloat() }
    } catch (error: Throwable) {
      Log.e(TAG, "Could not extract speaker vector", error)
      null
    } finally {
      runCatching { recognizer?.close() }
    }
  }

  fun verify(pcm: ByteArray, preferences: ZedPreferences): Match {
    val threshold = preferences.ownerVoiceThreshold
    if (!preferences.isOwnerVoiceEnabled || !preferences.hasOwnerVoiceProfile()) {
      return Match(true, 0f, 100, true, threshold)
    }

    val vector = extract(pcm)
      ?: return Match(false, 1f, 0, false, threshold)

    val candidates = buildList {
      preferences.getOwnerVoiceProfile()?.let(::add)
      addAll(preferences.getOwnerVoiceSamples())
    }
    if (candidates.isEmpty()) return Match(true, 0f, 100, true, threshold)

    val distance = candidates.minOf { cosineDistance(vector, it) }
    return Match(
      isMatch = distance <= threshold,
      distance = distance,
      similarityPercent = similarityPercent(distance),
      hasVoiceVector = true,
      threshold = threshold
    )
  }
}
