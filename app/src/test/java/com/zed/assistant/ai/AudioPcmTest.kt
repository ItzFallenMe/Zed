package com.zed.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPcmTest {

  @Test
  fun sampleRateFromMimeReadsAdvertisedRate() {
    assertEquals(16_000, AudioPcm.sampleRateFromMime("audio/pcm;rate=16000"))
    assertEquals(24_000, AudioPcm.sampleRateFromMime("AUDIO/PCM;RATE=24000"))
  }

  @Test
  fun sampleRateFromMimeUsesSafeDefaultForMissingOrInvalidInput() {
    assertEquals(AudioPcm.DEFAULT_OUTPUT_RATE, AudioPcm.sampleRateFromMime(null))
    assertEquals(AudioPcm.DEFAULT_OUTPUT_RATE, AudioPcm.sampleRateFromMime("audio/pcm;rate=4000"))
    assertEquals(AudioPcm.DEFAULT_OUTPUT_RATE, AudioPcm.sampleRateFromMime("audio/pcm"))
  }

  @Test
  fun rmsIsZeroForSilenceAndBoundedForSignal() {
    assertEquals(0f, AudioPcm.rms(ByteArray(32)), 0f)
    val fullScale = ByteArray(32) { index -> if (index % 2 == 0) 0xFF.toByte() else 0x7F }
    assertTrue(AudioPcm.rms(fullScale) in 0.99f..1f)
  }

  @Test
  fun toneHasExpectedPcmLengthAndNonZeroSignal() {
    val tone = AudioPcm.tone(seconds = 0.1, sampleRate = 10_000)
    assertEquals(2_000, tone.size)
    assertTrue(tone.any { it != 0.toByte() })
  }
}
