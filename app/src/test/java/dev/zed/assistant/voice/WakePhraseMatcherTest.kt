package dev.zed.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun `exact phrase matches`() {
        assertTrue(WakePhraseMatcher.matches("bolo zed", "bolo zed"))
        assertTrue(WakePhraseMatcher.matches("Bolo Zed, turn on the light", "bolo zed"))
    }

    @Test
    fun `devanagari phrase matches`() {
        assertTrue(WakePhraseMatcher.matches("बोलो ज़ेड", "bolo zed"))
        assertTrue(WakePhraseMatcher.matches("अरे जेड, टॉर्च जलाओ", "bolo zed"))
    }

    @Test
    fun `recogniser near misses match`() {
        assertTrue(WakePhraseMatcher.matches("bolo jed", "bolo zed"))
        assertTrue(WakePhraseMatcher.matches("hey zed", "bolo zed"))
        assertTrue(WakePhraseMatcher.matches("jarvis", "bolo zed"))
    }

    @Test
    fun `unrelated speech does not match`() {
        assertFalse(WakePhraseMatcher.matches("kal ka mausam kaisa hai", "bolo zed"))
        assertFalse(WakePhraseMatcher.matches("", "bolo zed"))
        assertFalse(WakePhraseMatcher.matches("open whatsapp", "bolo zed"))
    }

    @Test
    fun `custom phrase is honoured`() {
        assertTrue(WakePhraseMatcher.matches("hello max", "hello max"))
        assertFalse(WakePhraseMatcher.matches("hello max", "different phrase"))
    }
}
