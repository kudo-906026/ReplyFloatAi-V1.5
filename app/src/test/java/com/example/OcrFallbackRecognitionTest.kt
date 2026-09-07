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
    fun testDiagnosticLogging_GameCanvasOcrSimulation() {
        AppStateManager.clearDiagnosticLogs()

        AppStateManager.simulateGameCanvasOcr(null, "Super Sus")

        var logs = AppStateManager.diagnosticLogs.value
        val start = System.currentTimeMillis()
        while (logs.isEmpty() && (System.currentTimeMillis() - start) < 2000) {
            Thread.sleep(50)
            logs = AppStateManager.diagnosticLogs.value
        }

        assertTrue("At least one diagnostic log must be recorded from game canvas OCR", logs.isNotEmpty())

        val log = logs.firstOrNull { it.source.contains("Game Canvas OCR") }
        assertTrue("Log source must indicate Game Canvas OCR", log != null)
        assertEquals(true, log!!.screenshotCaptured)
        assertEquals("900x450", log.imageDimensions)
        assertEquals(false, log.isImageBlank)
    }

    @Test
    fun testDiagnosticLogging_ExplicitOcrTelemetry_DistinguishesActiveScreenFromExtractionFailure() {
        AppStateManager.clearDiagnosticLogs()

        // 1. Scenario A: Real screenshot captured fine (1080x2400, active game pixels), raw text extracted
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (ML Kit OCR)",
            rawText = "Who is the imposter among us?",
            result = DetectionResultType.MATCHED,
            category = "QUESTION_MARK_PRESENT",
            reason = "Screenshot captured (1080x2400 RGBA_8888). ML Kit extracted 3 blocks in 42ms.",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            latencyMs = 42L,
            screenshotCaptured = true,
            imageDimensions = "1080x2400",
            isImageBlank = false,
            ocrRawOutput = "SUPER SUS\nWho is the imposter among us?\nVoting: 20s",
            ocrError = null
        )

        // 2. Scenario B: Real screenshot captured fine (1080x2400, active game pixels), but OCR extracted 0 text blocks
        AppStateManager.addDiagnosticLog(
            source = "Super Sus (ML Kit OCR)",
            rawText = "[0 text blocks in frame (1080x2400 RGBA_8888)]",
            result = DetectionResultType.REJECTED,
            category = "OCR_ZERO_TEXT_DETECTED",
            reason = "Screenshot captured (1080x2400 RGBA_8888). ML Kit recognized 0 text blocks in 38ms. Raw screen image contains no machine-readable Latin glyphs.",
            detectionMethod = DetectionMethod.MLKIT_OCR,
            latencyMs = 38L,
            screenshotCaptured = true,
            imageDimensions = "1080x2400",
            isImageBlank = false,
            ocrRawOutput = "[Empty / 0 Blocks]",
            ocrError = "ML Kit recognized 0 text blocks in frame (1080x2400 RGBA_8888)"
        )

        val logs = AppStateManager.diagnosticLogs.value
        assertEquals(2, logs.size)

        val zeroTextLog = logs[0]
        val successLog = logs[1]

        // Verify Scenario A: Success OCR
        assertTrue("Scenario A screenshot must be captured", successLog.screenshotCaptured == true)
        assertEquals("1080x2400", successLog.imageDimensions)
        assertFalse("Scenario A image must not be blank", successLog.isImageBlank == true)
        assertEquals("SUPER SUS\nWho is the imposter among us?\nVoting: 20s", successLog.ocrRawOutput)
        assertEquals(null, successLog.ocrError)

        // Verify Scenario B: OCR zero text
        assertTrue("Scenario B screenshot must be captured", zeroTextLog.screenshotCaptured == true)
        assertFalse("Scenario B image must not be blank (active canvas graphics)", zeroTextLog.isImageBlank == true)
        assertEquals("[Empty / 0 Blocks]", zeroTextLog.ocrRawOutput)
        assertNotNull("Scenario B must specify error/issue", zeroTextLog.ocrError)
    }

    @Test
    fun testOcrAnalyzer_WhatsAppScreenWithMultipleMessages_DetectsIndividualQuestionLineNotBlob() {
        // Simulates ML Kit OCR extraction from an active WhatsApp conversation
        val whatsAppLines = listOf(
            com.example.ai.OcrLine(text = "WhatsApp", bottomY = 50, topY = 20, lineIndex = 0),
            com.example.ai.OcrLine(text = "John", bottomY = 70, topY = 52, lineIndex = 1),
            com.example.ai.OcrLine(text = "Online", bottomY = 88, topY = 72, lineIndex = 2),
            com.example.ai.OcrLine(text = "TODAY", bottomY = 140, topY = 120, lineIndex = 3),
            com.example.ai.OcrLine(text = "Hey man, good morning!", bottomY = 220, topY = 190, lineIndex = 4),
            com.example.ai.OcrLine(text = "10:14 AM", bottomY = 245, topY = 225, lineIndex = 5),
            com.example.ai.OcrLine(text = "Yeah I saw the update", bottomY = 320, topY = 290, lineIndex = 6),
            com.example.ai.OcrLine(text = "10:15 AM", bottomY = 345, topY = 325, lineIndex = 7),
            com.example.ai.OcrLine(text = "Are we still meeting at the cafe at 5pm?", bottomY = 460, topY = 420, lineIndex = 8),
            com.example.ai.OcrLine(text = "10:16 AM", bottomY = 485, topY = 465, lineIndex = 9),
            com.example.ai.OcrLine(text = "Type a message", bottomY = 950, topY = 900, lineIndex = 10)
        )

        val whatsAppOcrResult = OcrRecognitionResult(
            rawText = "WhatsApp\nJohn\nOnline\nTODAY\nHey man, good morning!\n10:14 AM\nYeah I saw the update\n10:15 AM\nAre we still meeting at the cafe at 5pm?\n10:16 AM\nType a message",
            lineCount = whatsAppLines.size,
            latencyMs = 38L,
            isSuccess = true,
            detectedBlocks = listOf(
                "WhatsApp\nJohn\nOnline",
                "TODAY",
                "Hey man, good morning!\n10:14 AM",
                "Yeah I saw the update\n10:15 AM",
                "Are we still meeting at the cafe at 5pm?\n10:16 AM",
                "Type a message"
            ),
            detectedLines = whatsAppLines
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(whatsAppOcrResult, detectQuestionsOnly = true)

        assertTrue("WhatsApp screen with genuine new question must be detected", analysis.isQuestion)
        assertEquals(
            "Extracted question must be the exact matching question line, NOT the whole concatenated screen blob",
            "Are we still meeting at the cafe at 5pm?",
            analysis.extractedQuestionText
        )
        assertFalse("Extracted question must NOT contain timestamps", analysis.extractedQuestionText.contains("10:16 AM"))
        assertFalse("Extracted question must NOT contain app title", analysis.extractedQuestionText.contains("WhatsApp"))
        assertFalse("Extracted question must NOT contain older messages", analysis.extractedQuestionText.contains("good morning"))
    }

    @Test
    fun testOcrAnalyzer_WhatsAppScreenWithHinglishQuestion_DetectsExactHinglishLine() {
        val whatsAppHinglishLines = listOf(
            com.example.ai.OcrLine(text = "WhatsApp", bottomY = 50, topY = 20, lineIndex = 0),
            com.example.ai.OcrLine(text = "Group Chat", bottomY = 70, topY = 52, lineIndex = 1),
            com.example.ai.OcrLine(text = "Priya, Rahul, You", bottomY = 88, topY = 72, lineIndex = 2),
            com.example.ai.OcrLine(text = "TODAY", bottomY = 140, topY = 120, lineIndex = 3),
            com.example.ai.OcrLine(text = "Sab theek hai bhai", bottomY = 220, topY = 190, lineIndex = 4),
            com.example.ai.OcrLine(text = "11:00 AM", bottomY = 245, topY = 225, lineIndex = 5),
            com.example.ai.OcrLine(text = "isko karo kya vote?", bottomY = 320, topY = 290, lineIndex = 6),
            com.example.ai.OcrLine(text = "11:02 AM", bottomY = 345, topY = 325, lineIndex = 7),
            com.example.ai.OcrLine(text = "Type a message", bottomY = 950, topY = 900, lineIndex = 8)
        )

        val ocrResult = OcrRecognitionResult(
            rawText = "WhatsApp\nGroup Chat\nPriya, Rahul, You\nTODAY\nSab theek hai bhai\n11:00 AM\nisko karo kya vote?\n11:02 AM\nType a message",
            lineCount = whatsAppHinglishLines.size,
            latencyMs = 35L,
            isSuccess = true,
            detectedBlocks = listOf(
                "WhatsApp\nGroup Chat",
                "TODAY",
                "Sab theek hai bhai\n11:00 AM",
                "isko karo kya vote?\n11:02 AM",
                "Type a message"
            ),
            detectedLines = whatsAppHinglishLines
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, detectQuestionsOnly = true)

        assertTrue("Hinglish question in WhatsApp screen must be detected", analysis.isQuestion)
        assertEquals(
            "Extracted question must be the exact Hinglish question line",
            "isko karo kya vote?",
            analysis.extractedQuestionText
        )
    }

    @Test
    fun testOcrAnalyzer_WhatsAppScreenWithoutQuestion_RejectsScreen() {
        val nonQuestionLines = listOf(
            com.example.ai.OcrLine(text = "WhatsApp", bottomY = 50, topY = 20, lineIndex = 0),
            com.example.ai.OcrLine(text = "Alice", bottomY = 70, topY = 52, lineIndex = 1),
            com.example.ai.OcrLine(text = "10:14 AM", bottomY = 140, topY = 120, lineIndex = 2),
            com.example.ai.OcrLine(text = "I have reached the office.", bottomY = 220, topY = 190, lineIndex = 3),
            com.example.ai.OcrLine(text = "10:15 AM", bottomY = 245, topY = 225, lineIndex = 4),
            com.example.ai.OcrLine(text = "See you later today.", bottomY = 320, topY = 290, lineIndex = 5),
            com.example.ai.OcrLine(text = "10:16 AM", bottomY = 345, topY = 325, lineIndex = 6),
            com.example.ai.OcrLine(text = "Type a message", bottomY = 950, topY = 900, lineIndex = 7)
        )

        val ocrResult = OcrRecognitionResult(
            rawText = "WhatsApp\nAlice\n10:14 AM\nI have reached the office.\n10:15 AM\nSee you later today.\n10:16 AM\nType a message",
            lineCount = nonQuestionLines.size,
            latencyMs = 30L,
            isSuccess = true,
            detectedBlocks = listOf(
                "WhatsApp\nAlice",
                "10:14 AM\nI have reached the office.",
                "10:15 AM\nSee you later today.",
                "10:16 AM\nType a message"
            ),
            detectedLines = nonQuestionLines
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, detectQuestionsOnly = true)
        assertFalse("WhatsApp screen without questions must be rejected", analysis.isQuestion)
    }

    @Test
    fun testWhatsAppScreen_AnimeWorldHypotheticalQuestion_ExtractedAndDetected() {
        val targetQuestion = "If you could live inside any anime world for a week, which one would you pick?"
        val whatsAppLines = listOf(
            com.example.ai.OcrLine(text = "WhatsApp", bottomY = 50, topY = 20, lineIndex = 0),
            com.example.ai.OcrLine(text = "Anime Fan Club", bottomY = 75, topY = 52, lineIndex = 1),
            com.example.ai.OcrLine(text = "TODAY", bottomY = 140, topY = 120, lineIndex = 2),
            com.example.ai.OcrLine(text = "Naruto is legendary!", bottomY = 220, topY = 190, lineIndex = 3),
            com.example.ai.OcrLine(text = "10:14 AM", bottomY = 245, topY = 225, lineIndex = 4),
            com.example.ai.OcrLine(text = targetQuestion, bottomY = 460, topY = 410, lineIndex = 5),
            com.example.ai.OcrLine(text = "10:16 AM", bottomY = 485, topY = 465, lineIndex = 6),
            com.example.ai.OcrLine(text = "Type a message", bottomY = 950, topY = 900, lineIndex = 7)
        )

        val ocrResult = OcrRecognitionResult(
            rawText = "WhatsApp\nAnime Fan Club\nTODAY\nNaruto is legendary!\n10:14 AM\n$targetQuestion\n10:16 AM\nType a message",
            lineCount = whatsAppLines.size,
            latencyMs = 42L,
            isSuccess = true,
            detectedBlocks = listOf(
                "WhatsApp\nAnime Fan Club",
                "TODAY",
                "Naruto is legendary!\n10:14 AM",
                "$targetQuestion\n10:16 AM",
                "Type a message"
            ),
            detectedLines = whatsAppLines
        )

        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, detectQuestionsOnly = true)

        assertTrue("Target anime hypothetical question in WhatsApp screen must be detected", analysis.isQuestion)
        assertEquals("Extracted text must exactly match the target question line", targetQuestion, analysis.extractedQuestionText)

        // Verify QuestionDetectionEngine directly analyzes this question
        val directAnalysis = QuestionDetectionEngine.analyze(targetQuestion, detectQuestionsOnly = true)
        assertTrue("QuestionDetectionEngine must validate this hypothetical question directly", directAnalysis.isQuestion)

        // Verify detection triggers AppStateManager without errors and creates diagnostic log
        AppStateManager.onQuestionDetected(
            context = null,
            text = targetQuestion,
            sourceApp = "WhatsApp",
            packageName = "com.whatsapp",
            forcedBypass = true,
            detectionMethod = DetectionMethod.ACCESSIBILITY
        )

        val logs = AppStateManager.diagnosticLogs.value
        val matchedLog = logs.firstOrNull { it.rawText.contains(targetQuestion) }
        assertNotNull("Diagnostics log must contain an entry for the anime question", matchedLog)
        assertEquals("Log entry must be MATCHED", DetectionResultType.MATCHED, matchedLog?.result)
    }
}
