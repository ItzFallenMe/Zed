package dev.zed.assistant.core.memory

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEmbedderTest {

    @Test
    fun embeddingHasFixedShapeAndUnitLength() {
        val vector = TextEmbedder.embed("I parked the car in B2 yesterday")
        assertEquals(TextEmbedder.DIMENSIONS, vector.size)
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() })
        assertTrue("expected unit vector, norm was $norm", abs(norm - 1.0) < 0.001)
    }

    @Test
    fun identicalTextScoresOne() {
        val a = TextEmbedder.embed("my sister's name is Riya")
        val b = TextEmbedder.embed("my sister's name is Riya")
        assertEquals(1f, TextEmbedder.cosine(a, b), 0.0001f)
    }

    @Test
    fun relatedTextBeatsUnrelated() {
        val query = TextEmbedder.embed("where did I park the car")
        val related = TextEmbedder.embed("parked the car in B2, level two")
        val unrelated = TextEmbedder.embed("send a voice note to mom on whatsapp")
        val relatedScore = TextEmbedder.cosine(query, related)
        val unrelatedScore = TextEmbedder.cosine(query, unrelated)
        assertTrue(
            "related ($relatedScore) should outrank unrelated ($unrelatedScore)",
            relatedScore > unrelatedScore
        )
    }

    @Test
    fun emptyTextIsSafe() {
        val vector = TextEmbedder.embed("")
        assertEquals(TextEmbedder.DIMENSIONS, vector.size)
        assertEquals(0f, TextEmbedder.cosine(vector, vector), 0.0001f)
    }
}
