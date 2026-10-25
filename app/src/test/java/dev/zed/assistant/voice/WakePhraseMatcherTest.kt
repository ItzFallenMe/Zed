package dev.zed.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun `the name alone wakes zed`() {
        assertTrue(WakePhraseMatcher.matches("zed", "zed"))
        assertTrue(WakePhraseMatcher.matches("Hey Zed!", "zed"))
        assertTrue(WakePhraseMatcher.matches("जेड", "zed"))
    }

    @Test
    fun `recogniser spellings still wake zed`() {
        assertTrue(WakePhraseMatcher.matches("zad", "zed"))
        assertTrue(WakePhraseMatcher.matches("ok zade", "zed"))
    }

    @Test
    fun `unrelated speech does not wake zed`() {
        assertFalse(WakePhraseMatcher.matches("what is the weather", "zed"))
        assertFalse(WakePhraseMatcher.matches("says the man", "zed"))
    }

    @Test
    fun `a custom phrase is honoured`() {
        assertTrue(WakePhraseMatcher.matches("hello computer", "hello computer"))
        // The assistant's own name always works, whatever phrase is configured.
        assertTrue(WakePhraseMatcher.matches("zed", "hello computer"))
        assertFalse(WakePhraseMatcher.matches("hello there", "hello computer"))
    }

    @Test
    fun `the bare wake word strips to nothing`() {
        assertEquals("", WakePhraseMatcher.stripWake("zed", "zed"))
        assertEquals("", WakePhraseMatcher.stripWake("Hey Zed!", "zed"))
        assertEquals("", WakePhraseMatcher.stripWake("suno zed", "zed"))
    }

    @Test
    fun `the wake word is removed from the front of a request`() {
        assertEquals(
            "what's the time",
            WakePhraseMatcher.stripWake("hey zed, what's the time", "zed")
        )
        assertEquals(
            "turn on the torch",
            WakePhraseMatcher.stripWake("Zed turn on the torch", "zed")
        )
        assertEquals(
            "battery",
            WakePhraseMatcher.stripWake("arre zed battery", "zed")
        )
    }

    @Test
    fun `a request without a wake word is left alone`() {
        assertEquals(
            "turn off wifi",
            WakePhraseMatcher.stripWake("turn off wifi", "zed")
        )
    }
}
