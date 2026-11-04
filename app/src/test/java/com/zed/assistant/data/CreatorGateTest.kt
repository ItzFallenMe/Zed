package com.zed.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorGateTest {

  @Test
  fun recognizesCreatorCommands() {
    assertTrue(CreatorGate.isCreatorRequest("I am your creator"))
    assertTrue(CreatorGate.isCreatorRequest("I'm the creator"))
    assertTrue(CreatorGate.isCreatorRequest("main tumhara creator hoon"))
    assertTrue(CreatorGate.isCreatorRequest("activate creator mode"))
    assertTrue(CreatorGate.isCreatorRequest("active master control"))
    assertTrue(CreatorGate.isCreatorRequest("Zed, master control"))
  }

  @Test
  fun ignoresQuestionsAboutTheCreator() {
    assertFalse(CreatorGate.isCreatorRequest("Who is your creator?"))
    assertFalse(CreatorGate.isCreatorRequest("What is master control?"))
    assertFalse(CreatorGate.isCreatorRequest("Tell me about Vedant"))
    assertFalse(CreatorGate.isCreatorRequest(""))
  }

  @Test
  fun readsDigitsAndSpokenDigits() {
    assertEquals("160123", CreatorGate.normalizeCodeInput("160123"))
    assertEquals("160123", CreatorGate.normalizeCodeInput("1 6 0 1 2 3"))
    assertEquals("160123", CreatorGate.normalizeCodeInput("one six zero one two three"))
    assertEquals("160123", CreatorGate.normalizeCodeInput("my code is 160 123"))
    assertEquals("160123", CreatorGate.normalizeCodeInput("code: one-six-zero-one-two-three"))
  }

  @Test
  fun picksTheCodeAndNotOtherNumbers() {
    assertEquals("160123", CreatorGate.normalizeCodeInput("I have 2 cats and 160123"))
    assertEquals("160123", CreatorGate.normalizeCodeInput("my age is 25, code 160123"))
  }

  @Test
  fun rejectsMissingOrMalformedCodes() {
    assertNull(CreatorGate.normalizeCodeInput("open YouTube"))
    assertNull(CreatorGate.normalizeCodeInput("12"))
    assertNull(CreatorGate.normalizeCodeInput("1234567890123"))
    assertFalse(CreatorGate.isValidCodeFormat("12 34"))
    assertFalse(CreatorGate.isValidCodeFormat("abcd"))
  }
}
