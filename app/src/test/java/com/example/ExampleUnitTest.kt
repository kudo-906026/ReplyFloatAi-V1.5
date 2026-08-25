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

  @Test
  fun testDiagnostics_healthStateEvaluation() {
    val items = listOf(
      com.example.model.DiagnosticItem(
        id = "accessibility_service",
        componentName = "Accessibility Service",
        status = com.example.model.DiagnosticStatus.WARNING,
        plainDescription = "Not running",
        technicalDetails = "isAccessibilityRunning == false",
        suggestedFix = "Enable in Android Settings"
      ),
      com.example.model.DiagnosticItem(
        id = "gemini_provider",
        componentName = "Gemini Provider",
        status = com.example.model.DiagnosticStatus.HEALTHY,
        plainDescription = "Ready with gemini-3.1-flash-lite-preview",
        technicalDetails = "gemini-3.1-flash-lite-preview · Key configured",
        suggestedFix = null
      )
    )

    val state = com.example.model.SystemHealthState(
      overallStatus = com.example.model.DiagnosticStatus.WARNING,
      items = items,
      errorCount = 0,
      warningCount = 1,
      healthyCount = 1
    )

    assertEquals(com.example.model.DiagnosticStatus.WARNING, state.overallStatus)
    assertEquals(1, state.warningCount)
    assertEquals(0, state.errorCount)
    assertEquals(1, state.healthyCount)
    assertEquals(2, state.items.size)
    assertEquals("1 notice", state.summaryText)
  }

  @Test
  fun testSettings_customValuesAndToggles() {
    val initial = ReplySettings()
    val modified = initial.copy(
      length = com.example.model.ReplyLength.TWO_LINES,
      count = 2,
      multiLanguageEnabled = true,
      scanningEnabled = false
    )

    assertEquals(com.example.model.ReplyLength.TWO_LINES, modified.length)
    assertEquals(2, modified.count)
    assertTrue(modified.multiLanguageEnabled)
    assertFalse(modified.scanningEnabled)
  }

  @Test
  fun testProviderApiKeys_allFourProvidersHandled() {
    val settings = ReplySettings(
      geminiApiKey = "AIzaSyGeminiTestKey123",
      openaiApiKey = "sk-OpenAiTestKey456",
      claudeApiKey = "sk-ant-ClaudeTestKey789",
      grokApiKey = "xai-GrokTestKey012"
    )

    assertEquals("AIzaSyGeminiTestKey123", settings.getApiKeyFor(com.example.model.AiProvider.GEMINI))
    assertEquals("sk-OpenAiTestKey456", settings.getApiKeyFor(com.example.model.AiProvider.OPENAI))
    assertEquals("sk-ant-ClaudeTestKey789", settings.getApiKeyFor(com.example.model.AiProvider.CLAUDE))
    assertEquals("xai-GrokTestKey012", settings.getApiKeyFor(com.example.model.AiProvider.GROK))
  }

  @Test
  fun testGameChatQuestionValidation() {
    val gameChat1 = "Player1: Anyone ready to start the raid?"
    val extracted1 = com.example.util.QuestionValidator.cleanAndExtractQuestion(gameChat1)
    assertEquals("Anyone ready to start the raid?", extracted1)

    val gameChat2 = "[Guild] where are we meeting for the boss?"
    val extracted2 = com.example.util.QuestionValidator.cleanAndExtractQuestion(gameChat2)
    assertEquals("where are we meeting for the boss?", extracted2)
  }

  @Test
  fun testTrashTalkAndLordTones() {
    val trashTalk = com.example.model.ReplyTone.TRASH_TALK
    val lord = com.example.model.ReplyTone.LORD

    assertEquals("Trash Talk", trashTalk.label)
    assertTrue(trashTalk.description.contains("Savage"))
    assertEquals("Lord", lord.label)
    assertTrue(lord.description.contains("sovereign being"))

    val promptTrashTalk = com.example.api.AiPromptHelper.buildPrompt(
      question = "Are you going to beat me in this match?",
      count = 2,
      length = com.example.model.ReplyLength.ONE_LINE,
      tone = trashTalk,
      multiLanguage = false
    )
    assertTrue(promptTrashTalk.contains("PERSONA DIRECTIVE"))
    assertTrue(promptTrashTalk.contains("trash-talk"))

    val promptLord = com.example.api.AiPromptHelper.buildPrompt(
      question = "What time are we meeting?",
      count = 2,
      length = com.example.model.ReplyLength.ONE_LINE,
      tone = lord,
      multiLanguage = false
    )
    assertTrue(promptLord.contains("PERSONA DIRECTIVE"))
    assertTrue(promptLord.contains("mortal") || promptLord.contains("servant"))
  }

  @Test
  fun testStopOverlayStateCleanup() {
    com.example.state.AppStateManager.setOverlayRunning(true)
    com.example.state.AppStateManager.setOverlayExpanded(true)
    assertTrue(com.example.state.AppStateManager.isOverlayRunning.value)
    assertTrue(com.example.state.AppStateManager.isOverlayExpanded.value)

    com.example.state.AppStateManager.setOverlayRunning(false)
    assertFalse(com.example.state.AppStateManager.isOverlayRunning.value)
    assertFalse(com.example.state.AppStateManager.isOverlayExpanded.value)
    assertTrue(com.example.state.AppStateManager.activeReplies.value.isEmpty())
    assertEquals(null, com.example.state.AppStateManager.currentQuestion.value)
  }
}

