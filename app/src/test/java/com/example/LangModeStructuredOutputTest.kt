package com.example

import com.example.ai.AiFallbackEngine
import com.example.ai.QuestionDetectionEngine
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.DetectionResultType
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.state.AppStateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LangModeStructuredOutputTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
        AppStateManager.clearReplies()
    }

    @Test
    fun testHinglishQuestionDetection_IskoKaroKyaVote() {
        val testQuestion = "isko karo kya vote?"

        // 1. Verify detected as non-English / Hinglish
        assertTrue(
            "Question '$testQuestion' must be classified as non-English/Hinglish",
            QuestionDetectionEngine.isNonEnglishOrHinglish(testQuestion)
        )

        // 2. Verify detected as a valid question
        val analysis = QuestionDetectionEngine.analyze(
            rawText = testQuestion,
            detectQuestionsOnly = true
        )
        assertTrue(
            "Question '$testQuestion' should be detected as a question, was: ${analysis.reason}",
            analysis.isQuestion
        )
        assertTrue(QuestionDetectionEngine.isGrammaticallyPlausibleQuestion(testQuestion))
    }

    @Test
    fun testParseStructuredAiReplies_WithJsonPayload() {
        val mockJson = """
            {
                "original": "isko karo kya vote?",
                "meaning": "Should I vote for this?",
                "replies": [
                    "Haan bhai, bilkul vote kar do!",
                    "Nahi, rehne do mat karo vote."
                ]
            }
        """.trimIndent()

        val dummyProvider = AiProvider(
            id = "test-ai",
            type = AiProviderType.GEMINI_API,
            name = "Test AI",
            displayName = "Test AI",
            modelName = "gemini-1.5-flash"
        )

        val result = AiFallbackEngine.parseStructuredAiReplies(
            rawText = mockJson,
            questionId = "q-123",
            tone = ReplyTone.CASUAL,
            provider = dummyProvider
        )

        assertEquals("isko karo kya vote?", result.original)
        assertEquals("Should I vote for this?", result.meaning)
        assertEquals(2, result.replies.size)
        assertEquals("Haan bhai, bilkul vote kar do!", result.replies[0].text)
        assertEquals("Nahi, rehne do mat karo vote.", result.replies[1].text)
    }

    @Test
    fun testParseStructuredAiReplies_FallbackToJsonArray() {
        val mockJsonArray = """
            ["Haan vote kardo", "Nahi mat karo"]
        """.trimIndent()

        val dummyProvider = AiProvider(
            id = "test-ai",
            type = AiProviderType.GEMINI_API,
            name = "Test AI",
            displayName = "Test AI",
            modelName = "gemini-1.5-flash"
        )

        val result = AiFallbackEngine.parseStructuredAiReplies(
            rawText = mockJsonArray,
            questionId = "q-123",
            tone = ReplyTone.CONCISE,
            provider = dummyProvider
        )

        assertNull(result.original)
        assertNull(result.meaning)
        assertEquals(2, result.replies.size)
        assertEquals("Haan vote kardo", result.replies[0].text)
    }

    @Test
    fun testLangModeOn_GeneratesOriginalMeaningAndRepliesForHinglish() = runBlocking {
        val question = "isko karo kya vote?"
        val settings = ReplySettings(
            understandingMode = true,
            tone = ReplyTone.CASUAL,
            responseLengthPreset = ResponseLengthPreset.SHORT,
            count = 2
        )

        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)

        // 1. Replies generated in same language/dialect (Hinglish)
        assertTrue("Replies must not be empty", result.replies.isNotEmpty())
        val firstReply = result.replies.first().text
        assertTrue(
            "Reply must be in Hinglish matching the question context (vote/haan/kar/bhai), was: $firstReply",
            firstReply.contains("vote", ignoreCase = true) ||
                firstReply.contains("kar", ignoreCase = true) ||
                firstReply.contains("haan", ignoreCase = true) ||
                firstReply.contains("bhai", ignoreCase = true)
        )

        // 2. Understanding (Meaning in plain English) must be populated when Lang mode is ON
        assertNotNull("Meaning/understanding must be present when Lang mode is ON", result.understanding)
        assertTrue(
            "Meaning should be in plain English explaining the vote question, was: ${result.understanding}",
            result.understanding?.contains("vote", ignoreCase = true) == true
        )
    }

    @Test
    fun testLangModeOff_PreservesNormalDisplayWithoutMeaning() = runBlocking {
        val question = "isko karo kya vote?"
        val settings = ReplySettings(
            understandingMode = false,
            tone = ReplyTone.CASUAL,
            responseLengthPreset = ResponseLengthPreset.SHORT,
            count = 2
        )

        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)

        // Replies still present
        assertTrue("Replies must not be empty", result.replies.isNotEmpty())
        // When Lang mode is OFF, understanding/meaning must be null
        assertNull("When Lang mode is OFF, understanding must be null", result.understanding)
    }

    @Test
    fun testAppStateManager_ProcessingHinglishQuestionWithLangMode() {
        val question = "isko karo kya vote?"
        AppStateManager.setUnderstandingMode(true)

        AppStateManager.onQuestionDetected(
            context = null,
            text = question,
            sourceApp = "WhatsApp",
            packageName = "com.whatsapp",
            forcedBypass = true
        )

        Thread.sleep(200)

        val detected = AppStateManager.currentQuestion.value
        assertNotNull("Current question must not be null", detected)
        assertEquals(question, detected?.text)
        assertNotNull("englishMeaning must be populated when Lang mode is ON", detected?.englishMeaning)
        assertTrue(
            "englishMeaning should explain the vote question, was: ${detected?.englishMeaning}",
            detected?.englishMeaning?.contains("vote", ignoreCase = true) == true
        )

        val replies = AppStateManager.activeReplies.value
        assertTrue("Active replies must be generated", replies.isNotEmpty())
    }
}
