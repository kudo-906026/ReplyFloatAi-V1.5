package com.example

import com.example.model.AiProvider
import com.example.model.ProviderSelectionMode
import com.example.model.ReplySettings
import com.example.util.QuestionValidator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testQuestionValidator_rejectsUrlsAndTechnicalStrings() {
    assertFalse(QuestionValidator.isGenuineQuestion("com/v3/signin/accountchooser?"))
    assertFalse(QuestionValidator.isGenuineQuestion("https://accounts.google.com/signin/v2/identifier?flowName=GlifWebSignIn"))
    assertFalse(QuestionValidator.isGenuineQuestion("api/v1/user/auth?token=12345&redirect=true"))
    assertFalse(QuestionValidator.isGenuineQuestion("image.png?width=200&height=200"))
    assertFalse(QuestionValidator.isGenuineQuestion("isAvailable ? true : false"))
  }

  @Test
  fun testQuestionValidator_acceptsNaturalQuestions() {
    assertTrue(QuestionValidator.isGenuineQuestion("Are you free for lunch today?"))
    assertTrue(QuestionValidator.isGenuineQuestion("Where did you put the project keys?"))
    assertTrue(QuestionValidator.isGenuineQuestion("Can you send me the latest file?"))
    assertTrue(QuestionValidator.isGenuineQuestion("What time does the movie start?"))
    assertTrue(QuestionValidator.isGenuineQuestion("Why?"))
    assertTrue(QuestionValidator.isGenuineQuestion("Kya aap aaj meeting attend karoge?"))
  }

  @Test
  fun testQuestionValidator_extractsQuestionsProperly() {
    val text = "Hey there! Are you available to chat?"
    val extracted = QuestionValidator.cleanAndExtractQuestion(text)
    assertEquals("Are you available to chat?", extracted)

    val falseText = "Open this link: example.com/login?client_id=123"
    assertNull(QuestionValidator.cleanAndExtractQuestion(falseText))
  }

  @Test
  fun testSettings_providerSelectionModeDefault() {
    val settings = ReplySettings()
    assertEquals(ProviderSelectionMode.AUTO_FALLBACK, settings.selectionMode)
    assertEquals(AiProvider.GEMINI, settings.preferredProvider)
    assertTrue(settings.providerChain.contains(AiProvider.GROK))
    assertFalse(settings.providerChain.any { it.id == "groq" })
  }
}

