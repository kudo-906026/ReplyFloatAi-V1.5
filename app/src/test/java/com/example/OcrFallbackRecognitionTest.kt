package com.example

import com.example.ai.OcrRecognitionEngine
import com.example.ai.OcrRecognitionResult
import com.example.ai.QuestionDetectionEngine
import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.state.AppStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OcrFallbackRecognitionTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
        AppStateManager.setContinuousScreenAnalysis(true)
        AppStateManager.clearDiagnosticLogs()
    }

    @Test
    fun testOcrAnalyzer_ExtractsQuestionFromSuperSusGameScreen() {
        // Simulated OCR output from Super Sus emergency meeting canvas
        val gameOcrResult = OcrRecognitionResult(
            rawText = "SUPER SUS - EMERGENCY MEETING\nVOTING: 00:24\nRed Player: Where were you when the body was found in Electrical?\nTimer: 15s remaining",
            lineCount = 4,
            detectedBlocks = listOf(
                "SUPER SUS - EMERGENCY MEETING",
                "VOTING: 00:24",
                "Red Player: Where were you when the body was found in Electrical?",
                "Timer: 15s remaining"
            ),
            latencyMs = 52L,
            isSuccess = true
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(gameOcrResult, detectQuestionsOnly = true)

        assertTrue("Game screen with question line should be detected as question", analysis.isQuestion)
        assertTrue(
            "Extracted question text should prioritize the question line",
            analysis.extractedQuestionText.contains("Where were you", ignoreCase = true)
        )
    }

    @Test
    fun testOcrAnalyzer_RejectsGameScreenWithoutQuestion() {
        val nonQuestionGameOcr = OcrRecognitionResult(
            rawText = "SUPER SUS\nROUND 1 / 5\nPlayer Blue completed task in Shields.\nDiscussion time ends.",
            lineCount = 4,
            detectedBlocks = listOf(
                "SUPER SUS",
                "ROUND 1 / 5",
                "Player Blue completed task in Shields.",
                "Discussion time ends."
            ),
            latencyMs = 45L,
            isSuccess = true
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(nonQuestionGameOcr, detectQuestionsOnly = true)

        assertFalse("Game screen with zero question marks or interrogatives must be rejected", analysis.isQuestion)
    }

    @Test
    fun testDiagnosticLogging_DistinguishesAccessibilityVsOcrFallback() {
        // Fast primary path (e.g. WhatsApp with accessibility nodes)
        AppStateManager.addDiagnosticLog(
            source = "WhatsApp",
            rawText = "Are you free for dinner tonight?",
            result = DetectionResultType.MATCHED,
            category = "QUESTION_MARK_PRESENT",
            reason = "Matches interrogative pattern [Primary: Fast Accessibility Node Scan]",
            detectionMethod = DetectionMethod.ACCESSIBILITY,
            latencyMs = 4L
        )

        // OCR Fallback path (e.g. Super Sus with 0 accessibility nodes)
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (Custom Canvas UI)",
            rawText = "Who is the imposter among us?",
            result = DetectionResultType.MATCHED,
            category = "QUESTION_MARK_PRESENT",
            reason = "Matches interrogative pattern [OCR Fallback: 58ms - On-device ML Kit text recognition]",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            latencyMs = 58L
        )

        val logs = AppStateManager.diagnosticLogs.value
        assertEquals(2, logs.size)

        val accessibilityLog = logs.find { it.source == "WhatsApp" }
        val ocrLog = logs.find { it.source == "Super Sus (Custom Canvas UI)" }

        assertNotNull(accessibilityLog)
        assertNotNull(ocrLog)

        assertEquals(DetectionMethod.ACCESSIBILITY, accessibilityLog?.detectionMethod)
        assertEquals(DetectionMethod.MLKIT_OCR, ocrLog?.detectionMethod)
        assertEquals(58L, ocrLog?.latencyMs)
    }

    @Test
    fun testSuperSusApp_IsInDefaultWhitelist() {
        val defaultApps = com.example.model.defaultWhitelistedApps()
        val superSusPlayStore = defaultApps.find { it.packageName == "com.je.supersus" }
        val superSusGlobal = defaultApps.find { it.packageName == "com.piogame.supersus" }
        assertNotNull("Super Sus (com.je.supersus) must be included in default whitelisted apps", superSusPlayStore)
        assertTrue("Super Sus (com.je.supersus) must be enabled by default", superSusPlayStore?.isEnabled == true)
        assertNotNull("Super Sus Global (com.piogame.supersus) must be included in default whitelisted apps", superSusGlobal)
        assertTrue("Super Sus Global (com.piogame.supersus) must be enabled by default", superSusGlobal?.isEnabled == true)
    }
}
