package dev.zed.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun bareNameWakes() {
        assertTrue(WakePhraseMatcher.matches("zed", "zed"))
        assertTrue(WakePhraseMatcher.matches("Zed.", "zed"))
        assertTrue(WakePhraseMatcher.matches("zed turn on the light", "zed"))
    }

    @Test
    fun friendlyTriggersWork() {
        assertTrue(WakePhraseMatcher.matches("hey zed", "zed"))
        assertTrue(WakePhraseMatcher.matches("okay zed, what's up", "zed"))
        assertTrue(WakePhraseMatcher.matches("arey zed sun na", "zed"))
    }

    @Test
    fun recogniserSpellingsWork() {
        assertTrue(WakePhraseMatcher.matches("zad", "zed"))
        assertTrue(WakePhraseMatcher.matches("zade play music", "zed"))
        assertTrue(WakePhraseMatcher.matches("जेड, टॉर्च जलाओ", "zed"))
        assertTrue(WakePhraseMatcher.matches("अरे ज़ेड", "zed"))
    }

    @Test
    fun normalSpeechDoesNotWake() {
        assertFalse(WakePhraseMatcher.matches("kal ka mausam kaisa hai", "zed"))
        assertFalse(WakePhraseMatcher.matches("", "zed"))
        assertFalse(WakePhraseMatcher.matches("open whatsapp", "zed"))
        assertFalse(WakePhraseMatcher.matches("pizza order karo", "zed"))
        assertFalse(WakePhraseMatcher.matches("please zeddy", "zed"))
    }

    @Test
    fun customPhraseIsHonoured() {
        assertTrue(WakePhraseMatcher.matches("hey computer", "hey computer"))
    }
}
