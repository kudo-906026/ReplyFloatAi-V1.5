package com.example

import com.example.ai.LangTranslationEngine
import com.example.model.ReplySettings
import com.example.state.AppStateManager
import com.example.state.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StandaloneLangBarTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
    }

    @Test
    fun testStrictJsonParsing_cleanValidResponse() {
        val rawJson = """{"original": "kaisa hai bhai", "meaning": "How are you doing, brother?", "detected_language": "Hinglish"}"""
        val result = LangTranslationEngine.parseAndValidateTranslation(rawJson, "kaisa hai bhai")

        assertTrue(result.isSuccessful)
        assertEquals("kaisa hai bhai", result.original)
        assertEquals("How are you doing, brother?", result.englishMeaning)
        assertEquals("Hinglish", result.detectedLanguage)
    }

    @Test
    fun testStrictValidation_rejectsModelReasoningAndPromptLeaks() {
        // AI model leaks <think> reasoning tokens and prompt instructions
        val leakingResponse = """
            <think>
            I need to translate "kya kar rahe ho".
            System instruction: answer specifically and return valid json.
            </think>
            {
                "original": "kya kar rahe ho",
                "meaning": "What are you doing?",
                "detected_language": "Hinglish"
            }
        """.trimIndent()

        val result = LangTranslationEngine.parseAndValidateTranslation(leakingResponse, "kya kar rahe ho")
        assertTrue(result.isSuccessful)
        assertEquals("kya kar rahe ho", result.original)
        assertEquals("What are you doing?", result.englishMeaning)
        // Must NEVER contain <think> or prompt keywords
        assertFalse(result.englishMeaning.contains("<think>"))
        assertFalse(result.englishMeaning.contains("System instruction"))
    }

    @Test
    fun testStrictValidation_rejectsInstructionEchoesAndRawJsonInFields() {
        // Bug reproduction: AI echoed the prompt into the meaning field
        val leakedPromptJson = """
            {
                "original": "You are a professional multilingual translator. Translate this message: bhai kaisa laga",
                "meaning": "CRITICAL: Return ONLY a valid JSON object with keys original and meaning",
                "detected_language": "Hinglish"
            }
        """.trimIndent()

        val result = LangTranslationEngine.parseAndValidateTranslation(leakedPromptJson, "bhai kaisa laga")
        // Blacklist kicks in! The leaked prompt must NEVER be displayed to the user
        assertFalse(result.original.contains("You are a professional"))
        assertFalse(result.englishMeaning.contains("CRITICAL:"))

        // Instead, fallback dictionary or safe fallback translates clean original
        assertEquals("bhai kaisa laga", result.original)
        assertEquals("Brother, how did you like my plan?", result.englishMeaning)
    }

    @Test
    fun testFallbackUnavailable_whenMalformedOrGarbageAiOutput() {
        // AI returns malformed text, raw HTML, or error dump
        val garbageOutput = "<html><body>502 Bad Gateway - System Error</body></html>"
        val result = LangTranslationEngine.parseAndValidateTranslation(garbageOutput, "kuch ajeeb question xyz123")

        // Must display "Translation unavailable" instead of malformed/raw text
        assertEquals(LangTranslationEngine.FALLBACK_UNAVAILABLE, result.englishMeaning)
        assertEquals("kuch ajeeb question xyz123", result.original)
        assertFalse(result.isSuccessful)
    }

    @Test
    fun testIndependentBarPositioning_noOverlapWithMainBar() {
        val settings = AppStateManager.settings.value

        // Main bar default position is typically (100, 300)
        val mainBarDefaultY = 300
        val langBarDefaultY = settings.langBarY

        // Lang bar default Y is 1050, distinctly placed well below main bar
        assertEquals(1050, langBarDefaultY)
        assertTrue(
            "Lang bar and Main bar must not overlap: vertical distance should be > 500px",
            Math.abs(langBarDefaultY - mainBarDefaultY) >= 500
        )

        // Updating Lang bar position independently
        AppStateManager.setLangBarPosition(200, 1100)
        assertEquals(200, AppStateManager.settings.value.langBarX)
        assertEquals(1100, AppStateManager.settings.value.langBarY)

        // Reset restores non-overlapping defaults
        AppStateManager.resetLangBarPosition()
        assertEquals(100, AppStateManager.settings.value.langBarX)
        assertEquals(1050, AppStateManager.settings.value.langBarY)
    }

    @Test
    fun testIndependentOpacityControls() {
        AppStateManager.setMainBarOpacity(0.90f)
        AppStateManager.setSmallBarOpacity(0.50f)
        AppStateManager.setLangBarOpacity(0.75f)

        val settings = AppStateManager.settings.value
        assertEquals(0.90f, settings.mainBarOpacity, 0.01f)
        assertEquals(0.50f, settings.smallBarOpacity, 0.01f)
        assertEquals(0.75f, settings.langBarOpacity, 0.01f)

        // Serialization and deserialization preserves all three opacities independently
        val serialized = SettingsStorage.serializeSettings(settings)
        val loaded = SettingsStorage.deserializeSettings(serialized)

        assertEquals(0.90f, loaded.mainBarOpacity, 0.01f)
        assertEquals(0.50f, loaded.smallBarOpacity, 0.01f)
        assertEquals(0.75f, loaded.langBarOpacity, 0.01f)
    }

    @Test
    fun testLangModeEnableDisableAndDismiss() {
        AppStateManager.setLangModeEnabled(true)
        assertTrue(AppStateManager.settings.value.langModeEnabled)

        // Trigger sample translation
        AppStateManager.testSampleHinglishTranslation()
        val activeState = AppStateManager.langTranslationState.value
        assertTrue(activeState.isVisible)
        assertEquals("bhai kaisa laga mera plan?", activeState.originalText)

        // Dismiss closes the bar
        AppStateManager.dismissLangBar()
        assertFalse(AppStateManager.langTranslationState.value.isVisible)

        // Disabling Lang mode also hides the bar
        AppStateManager.setLangModeEnabled(false)
        assertFalse(AppStateManager.settings.value.langModeEnabled)
        assertFalse(AppStateManager.langTranslationState.value.isVisible)
    }

    @Test
    fun testOfflineDictionaryTranslationCoverage() {
        val testCases = listOf(
            "kya chal raha hai" to "What's going on? / How are things?",
            "kahan ho bhai" to "Where are you right now?",
            "kab aaoge ghar" to "When will you arrive / come?",
            "khana khaya aapne" to "Did you have food / have you eaten?",
            "kya hua sab theek" to "What happened? / Is everything alright?",
            "como estas amigo" to "How are you?"
        )

        for ((input, expected) in testCases) {
            val result = LangTranslationEngine.fallbackOrUnavailable(input)
            assertTrue("Expected successful translation for '$input'", result.isSuccessful)
            assertEquals(expected, result.englishMeaning)
        }
    }
}
