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
    fun testOcrAnalyzer_HandlesGameChatWithPlayerAndRoleTags() {
        val chatOcrResult = OcrRecognitionResult(
            rawText = "[Doctor] Cyan: Did anyone see who killed Blue?\nYellow: I was in Medbay scanning\nEmergency Meeting in Progress",
            lineCount = 3,
            detectedBlocks = listOf(
                "[Doctor] Cyan: Did anyone see who killed Blue?",
                "Yellow: I was in Medbay scanning",
                "Emergency Meeting in Progress"
            ),
            latencyMs = 48L,
            isSuccess = true
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(chatOcrResult, detectQuestionsOnly = true)
        assertTrue("Chat with role prefix and question mark must be detected", analysis.isQuestion)
        assertTrue(
            "Extracted question should contain interrogative clause",
            analysis.extractedQuestionText.contains("Did anyone see who killed Blue", ignoreCase = true)
        )
    }

    @Test
    fun testModelSelector_OverridesUpdateProviderModelVisualState() {
        val defaultGemini = com.example.model.defaultBuiltInProviders().first { it.id == "gemini-api" }
        assertEquals("gemini-3.1-flash-lite", defaultGemini.modelName)

        // Select a different model preset (e.g. gemini-2.5-pro)
        AppStateManager.updateProviderModel(defaultGemini, "gemini-2.5-pro")

        val settings = AppStateManager.settings.value
        assertEquals("gemini-2.5-pro", settings.providerModelOverrides["gemini-api"])

        // Simulate provider mapping as performed in ProvidersTab and AiFallbackEngine
        val baseBuiltIn = com.example.model.defaultBuiltInProviders()
        val allProvidersMap = (baseBuiltIn + settings.customProviders).associateBy { it.id }.toMutableMap()
        settings.providerModelOverrides.forEach { (id, model) ->
            allProvidersMap[id]?.let { allProvidersMap[id] = it.copy(modelName = model) }
        }

        val mappedGemini = allProvidersMap["gemini-api"]
        assertNotNull(mappedGemini)
        assertEquals("gemini-2.5-pro", mappedGemini?.modelName)
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

    @Test
    fun testOcrEngine_DetectsBlankOrBlackProtectedPixels() {
        // 1. Completely blank/black pixel buffer (such as returned when FLAG_SECURE blanks out window content)
        val blackPixels = IntArray(64 * 64) { 0 }
        val isBlackProtected = OcrRecognitionEngine.isPixelArrayBlankOrBlack(blackPixels)
        assertTrue("Completely black/blank pixel buffer must be identified as blank/protected content", isBlackProtected)

        // 2. Uniform solid color pixel buffer (all identical pixels)
        val solidPixels = IntArray(64 * 64) { -1 } // 0xFFFFFFFF
        val isSolidProtected = OcrRecognitionEngine.isPixelArrayBlankOrBlack(solidPixels)
        assertTrue("Uniform solid non-content pixel buffer must be identified as blank/protected", isSolidProtected)

        // 3. Normal pixel buffer with contrast, colored text or shapes
        val activePixels = IntArray(64 * 64) { 0xFF141820.toInt() }
        // Introduce visible text/shape foreground pixels
        for (i in 100..150) {
            activePixels[i] = 0xFF00E5FF.toInt() // Bright cyan text pixel
        }
        val isActiveProtected = OcrRecognitionEngine.isPixelArrayBlankOrBlack(activePixels)
        assertFalse("Pixel buffer with visible text/contrast must not be treated as blank", isActiveProtected)
    }

    @Test
    fun testDiagnosticLogging_FlagSecureLogsClearAppLevelRestrictionReason() {
        AppStateManager.clearDiagnosticLogs()

        AppStateManager.simulateFlagSecureBlock("Super Sus (VM Container)")

        val logs = AppStateManager.diagnosticLogs.value
        assertEquals(1, logs.size)

        val log = logs.first()
        assertEquals("FLAG_SECURE_BLOCKED", log.category)
        assertEquals(DetectionResultType.REJECTED, log.result)
        assertEquals(true, log.screenshotCaptured)
        assertEquals("1080x2400", log.imageDimensions)
        assertEquals(true, log.isImageBlank)
        assertNotNull(log.ocrError)
    }

    @Test
    fun testDiagnosticLogging_ExplicitOcrTelemetry_DistinguishesFlagSecureFromExtractionFailure() {
        AppStateManager.clearDiagnosticLogs()

        // 1. Scenario A: FLAG_SECURE block (screenshot captured, but 1080x2400 blank black buffer)
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (Screen Capture)",
            rawText = "[Blank/Black Content: 1080x2400]",
            result = DetectionResultType.REJECTED,
            category = "FLAG_SECURE_BLOCKED",
            reason = "Screenshot captured successfully (1080x2400), but frame buffer is blank/black. 1080x2400 px: 100% solid uniform color (#FF000000).",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            screenshotCaptured = true,
            imageDimensions = "1080x2400",
            isImageBlank = true,
            ocrRawOutput = "[Blank Screen - ML Kit bypassed]",
            ocrError = "FLAG_SECURE window protection detected. Android WindowManager masked game surface with solid black dummy buffer."
        )

        // 2. Scenario B: Real screenshot captured fine (1080x2400, active game pixels), raw text extracted
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (ML Kit OCR)",
            rawText = "Who is the imposter among us?",
            result = DetectionResultType.MATCHED,
            category = "QUESTION_MARK_PRESENT",
            reason = "Screenshot captured (1080x2400, Active game graphics). ML Kit extracted 3 blocks in 42ms.",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            latencyMs = 42L,
            screenshotCaptured = true,
            imageDimensions = "1080x2400",
            isImageBlank = false,
            ocrRawOutput = "SUPER SUS\nWho is the imposter among us?\nVoting: 20s",
            ocrError = null
        )

        // 3. Scenario C: Real screenshot captured fine (1080x2400, active game pixels), but OCR extracted 0 text blocks
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (ML Kit OCR)",
            rawText = "[0 text blocks extracted]",
            result = DetectionResultType.REJECTED,
            category = "OCR_ZERO_TEXT_DETECTED",
            reason = "Screenshot captured (1080x2400, active game graphics), but ML Kit recognized 0 text blocks in 38ms.",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            latencyMs = 38L,
            screenshotCaptured = true,
            imageDimensions = "1080x2400",
            isImageBlank = false,
            ocrRawOutput = "[Empty / 0 Blocks]",
            ocrError = "ML Kit returned 0 text blocks on active game canvas"
        )

        val logs = AppStateManager.diagnosticLogs.value
        assertEquals(3, logs.size)

        val zeroTextLog = logs[0]
        val successLog = logs[1]
        val flagSecureLog = logs[2]

        // Verify Scenario A: FLAG_SECURE
        assertTrue("Scenario A screenshot must be captured", flagSecureLog.screenshotCaptured == true)
        assertEquals("1080x2400", flagSecureLog.imageDimensions)
        assertTrue("Scenario A must be identified as blank/black frame buffer", flagSecureLog.isImageBlank == true)
        assertTrue("Scenario A ocrError must detail FLAG_SECURE", flagSecureLog.ocrError?.contains("FLAG_SECURE") == true)

        // Verify Scenario B: Success OCR
        assertTrue("Scenario B screenshot must be captured", successLog.screenshotCaptured == true)
        assertEquals("1080x2400", successLog.imageDimensions)
        assertFalse("Scenario B image must not be blank", successLog.isImageBlank == true)
        assertEquals("SUPER SUS\nWho is the imposter among us?\nVoting: 20s", successLog.ocrRawOutput)
        assertEquals(null, successLog.ocrError)

        // Verify Scenario C: OCR zero text
        assertTrue("Scenario C screenshot must be captured", zeroTextLog.screenshotCaptured == true)
        assertFalse("Scenario C image must not be blank (active canvas graphics)", zeroTextLog.isImageBlank == true)
        assertEquals("[Empty / 0 Blocks]", zeroTextLog.ocrRawOutput)
        assertNotNull("Scenario C must specify error/issue", zeroTextLog.ocrError)
    }
}
