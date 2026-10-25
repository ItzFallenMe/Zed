package dev.zed.assistant.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPolicyTest {

    @Test
    fun `durable details about the owner are permanent`() {
        val durable = listOf(
            "I parked in B2, level two",
            "my sister's name is Riya",
            "I'm allergic to peanuts",
            "I work the night shift every Tuesday",
            "mera naam Rohan hai",
            "I always take my coffee black"
        )
        durable.forEach { line ->
            assertEquals(line, MemoryPolicy.Kind.PERMANENT, MemoryPolicy.judge(line).kind)
        }
    }

    @Test
    fun `explicit requests are kept even without a marker`() {
        assertEquals(
            MemoryPolicy.Kind.PERMANENT,
            MemoryPolicy.judge("the wifi here is terrible", explicit = true).kind
        )
    }

    @Test
    fun `small talk is refused`() {
        val filler = listOf("thanks", "ok", "good morning", "hmm", "theek hai")
        filler.forEach { line ->
            assertEquals(line, MemoryPolicy.Kind.REFUSE, MemoryPolicy.judge(line).kind)
        }
    }

    @Test
    fun `questions are not facts`() {
        assertEquals(
            MemoryPolicy.Kind.REFUSE,
            MemoryPolicy.judge("what is the weather right now?").kind
        )
    }

    @Test
    fun `credentials are refused even when asked`() {
        val secrets = listOf(
            "the wifi password is hunter2",
            "my card number is 4111 1111 1111 1111",
            "otp is 482913",
            "save this key AIzaSyD-1234567890abcdef",
            "gsk_abcdefghijklmnop12345",
            "my aadhaar is 1234 5678 9012"
        )
        secrets.forEach { line ->
            assertTrue(line, MemoryPolicy.looksLikeCredential(line))
            assertEquals(line, MemoryPolicy.Kind.REFUSE, MemoryPolicy.judge(line, explicit = true).kind)
        }
    }

    @Test
    fun `a short ordinal is not mistaken for a secret`() {
        assertEquals(
            MemoryPolicy.Kind.PERMANENT,
            MemoryPolicy.judge("my locker is 4412", explicit = true).kind
        )
    }

    @Test
    fun `long neutral sentences become episodes`() {
        assertEquals(
            MemoryPolicy.Kind.EPISODE,
            MemoryPolicy.judge("we talked about the trip and it went on for a while").kind
        )
    }

    @Test
    fun `empty input is refused`() {
        assertEquals(MemoryPolicy.Kind.REFUSE, MemoryPolicy.judge("").kind)
        assertEquals(MemoryPolicy.Kind.REFUSE, MemoryPolicy.judge("   ").kind)
    }
}
