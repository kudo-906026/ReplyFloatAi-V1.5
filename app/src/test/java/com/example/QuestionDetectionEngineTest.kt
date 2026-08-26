package com.example

import com.example.ai.QuestionDetectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDetectionEngineTest {

    @Test
    fun testScenario1_ShortSimpleQuestions() {
        val q1 = QuestionDetectionEngine.analyze("Are you free for lunch tomorrow?", detectQuestionsOnly = true)
        assertTrue("Expected q1 to be detected as a question", q1.isQuestion)
        assertEquals("QUESTION_PUNCTUATION", q1.category)

        val q2 = QuestionDetectionEngine.analyze("What time is the team meeting", detectQuestionsOnly = true)
        assertTrue("Expected q2 to be detected via interrogative starter", q2.isQuestion)
        assertEquals("QUESTION_STARTER", q2.category)

        val q3 = QuestionDetectionEngine.analyze("Can you send me the updated proposal", detectQuestionsOnly = true)
        assertTrue("Expected q3 to be detected via modal verb starter", q3.isQuestion)
        assertEquals("QUESTION_STARTER", q3.category)
    }

    @Test
    fun testScenario2_LongMultilineQuestions() {
        val multiline1 = """
            Hi Alex,
            Could we reschedule our meeting to tomorrow afternoon at 3 PM?
            Thanks!
        """.trimIndent()
        val res1 = QuestionDetectionEngine.analyze(multiline1, detectQuestionsOnly = true)
        assertTrue("Expected multiline question with '?' to be detected", res1.isQuestion)

        val multiline2 = """
            Hey team, quick update on the roadmap.
            We finished sprint 4 yesterday.
            When should we sync up for sprint planning
        """.trimIndent()
        val res2 = QuestionDetectionEngine.analyze(multiline2, detectQuestionsOnly = true)
        assertTrue("Expected multiline question with interrogative line to be detected", res2.isQuestion)
        assertEquals("MULTILINE_QUESTION", res2.category)
    }

    @Test
    fun testScenario3_MathNotationQuestions() {
        val math1 = QuestionDetectionEngine.analyze("Can you calculate 15 * 8 + 32?", detectQuestionsOnly = true)
        assertTrue("Expected math question with arithmetic to be detected", math1.isQuestion)
        assertEquals("MATH_PROMPT", math1.category)

        val math2 = QuestionDetectionEngine.analyze("Solve for x: 2x + 6 = 18", detectQuestionsOnly = true)
        assertTrue("Expected algebraic equation to be detected", math2.isQuestion)
        assertEquals("MATH_PROMPT", math2.category)

        val math3 = QuestionDetectionEngine.analyze("What is 5^3", detectQuestionsOnly = true)
        assertTrue("Expected exponent math prompt to be detected", math3.isQuestion)

        val math4 = QuestionDetectionEngine.analyze("25 * 4 = ?", detectQuestionsOnly = true)
        assertTrue("Expected math expression with ? to be detected", math4.isQuestion)
    }

    @Test
    fun testScenario4_NormalMessagingTextNonQuestions() {
        val msg1 = QuestionDetectionEngine.analyze("I'm heading out now, see you soon.", detectQuestionsOnly = true)
        assertFalse("Expected normal statement to be rejected when filtering is on", msg1.isQuestion)
        assertEquals("NORMAL_STATEMENT", msg1.category)

        val msg2 = QuestionDetectionEngine.analyze("Thanks for sending the files over.", detectQuestionsOnly = true)
        assertFalse("Expected greeting/acknowledgement statement to be rejected", msg2.isQuestion)
        assertEquals("NORMAL_STATEMENT", msg2.category)

        val msg3 = QuestionDetectionEngine.analyze("ok", detectQuestionsOnly = true)
        assertFalse("Expected short snippet to be rejected", msg3.isQuestion)
        assertEquals("TOO_SHORT", msg3.category)
    }

    @Test
    fun testOcrOutputAnalysis() {
        val ocrResult = com.example.ai.OcrRecognitionResult(
            rawText = "What is the capital of Australia?",
            lineCount = 1,
            latencyMs = 45,
            isSuccess = true,
            detectedBlocks = listOf("What is the capital of Australia?")
        )
        val analysis = com.example.ai.OcrRecognitionEngine.analyzeOcrOutput(ocrResult, detectQuestionsOnly = true)
        assertTrue("Expected OCR result to be recognized as question", analysis.isQuestion)

        val emptyOcr = com.example.ai.OcrRecognitionResult(
            rawText = "",
            lineCount = 0,
            latencyMs = 30,
            isSuccess = false,
            errorMessage = "No text found"
        )
        val emptyAnalysis = com.example.ai.OcrRecognitionEngine.analyzeOcrOutput(emptyOcr, detectQuestionsOnly = true)
        assertFalse("Expected empty OCR to be marked not a question", emptyAnalysis.isQuestion)
        assertEquals("EMPTY_OCR", emptyAnalysis.category)
    }

    @Test
    fun testQuestionFilteringToggle() {
        // When detectQuestionsOnly is false, all non-blank text >= 3 chars is accepted
        val statement = QuestionDetectionEngine.analyze("I'm heading out now, see you soon.", detectQuestionsOnly = false)
        assertTrue("Expected text to be accepted when detectQuestionsOnly is false", statement.isQuestion)
        assertEquals("GENERAL_MESSAGING", statement.category)
    }
}
