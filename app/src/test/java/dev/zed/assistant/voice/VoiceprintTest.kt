package dev.zed.assistant.voice

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceprintTest {

    private fun tone(
        fundamental: Double,
        harmonics: List<Double>,
        seconds: Double = 2.0,
        noise: Double = 0.0
    ): ShortArray {
        val samples = ShortArray((Voiceprint.SAMPLE_RATE * seconds).toInt())
        var seed = 7L
        for (i in samples.indices) {
            var value = 0.0
            harmonics.forEachIndexed { index, amplitude ->
                val frequency = fundamental * (index + 1)
                value += amplitude * sin(2 * PI * frequency * i / Voiceprint.SAMPLE_RATE)
            }
            if (noise > 0.0) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                value += noise * ((seed % 1000) / 1000.0 - 0.5)
            }
            samples[i] = (value * 8000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    @Test
    fun embeddingHasExpectedShape() {
        val embedding = Voiceprint.embed(tone(140.0, listOf(1.0, 0.5, 0.25)))
        assertNotNull(embedding)
        assertEquals(Voiceprint.DIMENSIONS, embedding!!.size)
    }

    @Test
    fun sameVoiceMatchesItself() {
        val voice = tone(130.0, listOf(1.0, 0.6, 0.3, 0.1))
        val first = Voiceprint.embed(voice)!!
        val second = Voiceprint.embed(voice.copyOf())!!
        val score = Voiceprint.cosine(first, second)
        assertTrue("identical samples should score 1.0 but was $score", score > 0.999f)
    }

    @Test
    fun differentVoicesScoreLower() {
        val voiceA = Voiceprint.embed(tone(110.0, listOf(1.0, 0.7, 0.35, 0.15)))!!
        val voiceB = Voiceprint.embed(tone(230.0, listOf(0.6, 1.0, 0.2, 0.05)))!!
        val same = Voiceprint.cosine(voiceA, voiceA)
        val different = Voiceprint.cosine(voiceA, voiceB)
        assertTrue("different voices should score below identity", different < same)
        assertTrue("different voices scored too high: $different", different < 0.98f)
    }

    @Test
    fun silenceIsRejected() {
        assertEquals(null, Voiceprint.embed(ShortArray(1000)))
        assertEquals(null, Voiceprint.embed(ShortArray(Voiceprint.SAMPLE_RATE * 2)))
    }
}
