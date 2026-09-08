package com.example

import com.example.ai.OcrBlock
import com.example.ai.OcrLine
import com.example.ai.OcrRecognitionEngine
import com.example.ai.OcrRecognitionResult
import com.example.ai.QuestionDetectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppQuestionDetectionTest {

    private val targetQuestion = "If you could live inside any anime world for a week, which one would you pick?"

    @Test
    fun testDirectQuestionDetection() {
        val analysis = QuestionDetectionEngine.analyze(
            rawText = targetQuestion,
            detectQuestionsOnly = true
        )

        assertTrue("Target anime question must be detected as a question", analysis.isQuestion)
        assertEquals(targetQuestion, analysis.extractedQuestionText)
    }

    @Test
    fun testQuestionWithTrailingTimestampAndCheckmarks() {
        val rawWhatsAppMessage = "$targetQuestion 10:45 AM ✓✓"
        val cleaned = OcrRecognitionEngine.cleanCandidateLine(rawWhatsAppMessage)

        assertEquals(targetQuestion, cleaned)

        val analysis = QuestionDetectionEngine.analyze(
            rawText = cleaned,
            detectQuestionsOnly = true
        )

        assertTrue("Cleaned WhatsApp question must match", analysis.isQuestion)
        assertEquals(targetQuestion, analysis.extractedQuestionText)
    }

    @Test
    fun testQuestionWithSenderPrefix() {
        val rawWhatsAppMessage = "Ken: $targetQuestion 10:45 AM ✓"
        val cleaned = OcrRecognitionEngine.cleanCandidateLine(rawWhatsAppMessage)

        assertEquals(targetQuestion, cleaned)

        val analysis = QuestionDetectionEngine.analyze(
            rawText = cleaned,
            detectQuestionsOnly = true
        )

        assertTrue("Cleaned question with sender prefix stripped must match", analysis.isQuestion)
        assertEquals(targetQuestion, analysis.extractedQuestionText)
    }

    @Test
    fun testOcrMultiLineMessageBubbleMatching() {
        // Simulating ML Kit OCR output for a WhatsApp bubble where the text wraps across two lines
        val line1 = OcrLine(
            text = "If you could live inside any anime world for a week,",
            bottomY = 1450,
            lineIndex = 0,
            blockIndex = 0
        )
        val line2 = OcrLine(
            text = "which one would you pick? 11:20 AM ✓✓",
            bottomY = 1500,
            lineIndex = 1,
            blockIndex = 0
        )

        val structuredBlock = OcrBlock(
            text = "If you could live inside any anime world for a week,\nwhich one would you pick? 11:20 AM ✓✓",
            bottomY = 1500,
            blockIndex = 0,
            lines = listOf(line1, line2)
        )

        val ocrResult = OcrRecognitionResult(
            isSuccess = true,
            rawText = "If you could live inside any anime world for a week,\nwhich one would you pick? 11:20 AM ✓✓",
            lineCount = 2,
            detectedLines = listOf(line1, line2),
            detectedBlocks = listOf(structuredBlock.text),
            structuredBlocks = listOf(structuredBlock),
            latencyMs = 35L
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(
            ocrResult = ocrResult,
            detectQuestionsOnly = true
        )

        assertTrue("Multi-line WhatsApp bubble must be recognized as question", analysis.isQuestion)
        assertEquals(targetQuestion, analysis.extractedQuestionText)
    }

    @Test
    fun testIgnoredUiAndTimestampFiltering() {
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("10:45 AM"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("10:45 AM ✓✓"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("10:45"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("Type a message"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("✓✓"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("Online"))
        assertTrue(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("Typing..."))

        // Genuine messages must NOT be ignored
        assertFalse(OcrRecognitionEngine.isIgnoredUiOrTimestampLine(targetQuestion))
        assertFalse(OcrRecognitionEngine.isIgnoredUiOrTimestampLine("which one would you pick?"))
    }
}
