package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ai.OcrRecognitionEngine
import com.example.ai.QuestionDetectionEngine
import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VisibleScannedNode(
    val text: String,
    val bounds: Rect,
    val bottomY: Int,
    val topY: Int
)

class QuestionDetectorAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: QuestionDetectorAccessibilityService? = null

        fun resetLastProcessedText() {
            instance?.resetState()
        }

        fun resetScanningState() {
            instance?.resetState()
        }

        fun triggerImmediateRescan() {
            instance?.scanActiveWindowNow()
        }
    }

    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0L
    private var lastOcrScanTime: Long = 0L
    private var isOcrProcessing: Boolean = false

    private fun resetState() {
        lastProcessedText = ""
        lastProcessedTime = 0L
        lastOcrScanTime = 0L
        isOcrProcessing = false
    }

    // Background coroutine scope ensuring zero UI/main thread blocking
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var continuousScanJob: kotlinx.coroutines.Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppStateManager.init(this)
        AppStateManager.setAccessibilityRunning(true)
        startContinuousScanLoop()
    }

    private fun startContinuousScanLoop() {
        continuousScanJob?.cancel()
        continuousScanJob = serviceScope.launch {
            while (isActive) {
                try {
                    val settings = AppStateManager.settings.value
                    if (settings.continuousScreenAnalysis) {
                        withContext(Dispatchers.Main) {
                            performWindowScan(isContinuousTick = true, forcedBypass = false)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(1500L)
            }
        }
    }

    fun scanActiveWindowNow() {
        serviceScope.launch(Dispatchers.Main) {
            performWindowScan(isContinuousTick = false, forcedBypass = true)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        performWindowScan(isContinuousTick = false, forcedBypass = false, event = event)
    }

    private fun performWindowScan(
        isContinuousTick: Boolean,
        forcedBypass: Boolean = false,
        event: AccessibilityEvent? = null
    ) {
        val settings = AppStateManager.settings.value
        if (!settings.continuousScreenAnalysis && !forcedBypass) {
            return
        }

        val rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
        var pkgName = rootNode?.packageName?.toString() ?: event?.packageName?.toString()

        if (pkgName == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val activeWindow = windows.firstOrNull { it.isActive || it.isFocused } ?: windows.firstOrNull()
                pkgName = activeWindow?.root?.packageName?.toString()
            } catch (_: Exception) {
            }
        }

        if (pkgName == null || pkgName == applicationContext.packageName || isSystemOrKeyboardPackage(pkgName)) {
            return
        }

        val whitelistedApp = settings.appsWhitelist.find { it.packageName == pkgName && it.isEnabled } ?: return
        val appName = whitelistedApp.appName

        val now = System.currentTimeMillis()
        if (!forcedBypass && (now - lastProcessedTime < settings.smartDebounceMs)) {
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<VisibleScannedNode>()
        var foundAnyReadableNodes = false

        if (rootNode != null) {
            collectVisibleTextNodesSafely(
                node = rootNode,
                outList = visibleNodes,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentDepth = 0,
                maxDepth = 10,
                maxNodes = 60
            )
        }

        if (visibleNodes.isEmpty() && event != null) {
            val sourceNode = try { event.source } catch (_: Exception) { null }
            if (sourceNode != null) {
                collectVisibleTextNodesSafely(
                    node = sourceNode,
                    outList = visibleNodes,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    currentDepth = 0,
                    maxDepth = 6,
                    maxNodes = 20
                )
            }
        }

        if (visibleNodes.isEmpty() && event != null) {
            val eventTexts = event.text.mapNotNull { it?.toString()?.trim() }.filter { it.length >= 3 && !isIgnoredUiString(it) }
            for (raw in eventTexts) {
                visibleNodes.add(
                    VisibleScannedNode(
                        text = raw,
                        bounds = Rect(0, screenHeight / 2, screenWidth, screenHeight - 100),
                        bottomY = screenHeight - 100,
                        topY = screenHeight / 2
                    )
                )
            }
        }

        if (visibleNodes.isNotEmpty()) {
            foundAnyReadableNodes = true

            // Deduplicate visible text entries preserving lowest screen position
            val distinctNodes = visibleNodes
                .groupBy { it.text }
                .map { (_, nodes) -> nodes.maxByOrNull { it.bottomY } ?: nodes.first() }

            val sortedNodes = distinctNodes.sortedByDescending { it.bottomY }

            val lowestValidQuestionNode = sortedNodes.firstOrNull { node ->
                val candidateText = node.text.trim()
                if (candidateText.length < 3) return@firstOrNull false
                val analysis = QuestionDetectionEngine.analyze(candidateText, settings.detectQuestionsOnly)
                val hasQuestionMark = candidateText.contains("?") || candidateText.contains("？") || candidateText.contains("¿")
                analysis.isQuestion && (hasQuestionMark || !settings.detectQuestionsOnly)
            }

            if (lowestValidQuestionNode != null) {
                val candidateText = lowestValidQuestionNode.text.trim()

                if (candidateText == lastProcessedText || (!forcedBypass && candidateText == AppStateManager.currentQuestion.value?.text)) {
                    return
                }

                lastProcessedText = candidateText
                lastProcessedTime = now

                AppStateManager.onQuestionDetected(
                    context = this@QuestionDetectorAccessibilityService,
                    text = candidateText,
                    sourceApp = appName,
                    packageName = pkgName,
                    forcedBypass = forcedBypass,
                    detectionMethod = DetectionMethod.ACCESSIBILITY
                )
                return
            }

            // If no valid question node found, log rejection diagnostic if not a tick loop
            if (!isContinuousTick) {
                val lowestNode = sortedNodes.firstOrNull()
                if (lowestNode != null && lowestNode.text != lastProcessedText) {
                    val lowestAnalysis = QuestionDetectionEngine.analyze(lowestNode.text, settings.detectQuestionsOnly)
                    AppStateManager.addDiagnosticLog(
                        source = "$appName (Visible Screen Node)",
                        rawText = lowestNode.text,
                        result = DetectionResultType.REJECTED,
                        category = lowestAnalysis.category,
                        reason = lowestAnalysis.reason,
                        detectionMethod = DetectionMethod.ACCESSIBILITY
                    )
                }
            }
        }

        // 2. OCR FALLBACK: Trigger on-device ML Kit OCR when accessibility found NO readable visible text (e.g. Super Sus, custom canvas games)
        if (!foundAnyReadableNodes && settings.enableOcrFallback) {
            triggerOcrFallbackIfEligible(appName, pkgName, now, if (forcedBypass) 0 else settings.ocrDebounceMs)
        }
    }

    private fun triggerOcrFallbackIfEligible(appName: String, pkgName: String, now: Long, ocrDebounceMs: Int) {
        if (isOcrProcessing || (now - lastOcrScanTime < ocrDebounceMs)) {
            return
        }

        lastOcrScanTime = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isOcrProcessing = true

            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    applicationContext.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bufferWidth = try { hardwareBuffer.width } catch (_: Exception) { 0 }
                            val bufferHeight = try { hardwareBuffer.height } catch (_: Exception) { 0 }

                            val bitmap = try {
                                Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            } catch (_: Exception) {
                                null
                            } finally {
                                hardwareBuffer.close()
                            }

                            if (bitmap != null) {
                                val softwareCopy = try {
                                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                } catch (_: Exception) {
                                    null
                                }

                                if (softwareCopy != null) {
                                    val bitmapAnalysis = OcrRecognitionEngine.analyzeBitmapContent(softwareCopy)
                                    val dims = "${bitmapAnalysis.width}x${bitmapAnalysis.height}"

                                    // Check if screenshot returned blank/black content consistent with FLAG_SECURE blocking
                                    if (bitmapAnalysis.isBlankOrBlack) {
                                        isOcrProcessing = false
                                        AppStateManager.addDiagnosticLog(
                                            source = "$appName (Screen Capture)",
                                            rawText = "[Blank/Black Content: $dims]",
                                            result = DetectionResultType.REJECTED,
                                            category = "FLAG_SECURE_BLOCKED",
                                            reason = "Screenshot captured successfully ($dims), but frame buffer is blank/black. ${bitmapAnalysis.details}",
                                            detectionMethod = DetectionMethod.MLKIT_OCR,
                                            screenshotCaptured = true,
                                            imageDimensions = dims,
                                            isImageBlank = true,
                                            ocrRawOutput = "[Blank Screen - ML Kit bypassed]",
                                            ocrError = "FLAG_SECURE window protection detected. Android WindowManager masked game surface with solid black/uniform dummy buffer."
                                        )
                                        return
                                    }

                                    // Screenshot captured with real graphics! Process entirely on background coroutine
                                    serviceScope.launch(Dispatchers.Default) {
                                        try {
                                            runOcrProcessingOnBackground(softwareCopy, appName, pkgName, bitmapAnalysis)
                                        } finally {
                                            isOcrProcessing = false
                                        }
                                    }
                                    return
                                } else {
                                    isOcrProcessing = false
                                    AppStateManager.addDiagnosticLog(
                                        source = "$appName (Screen Capture)",
                                        rawText = "[Hardware Bitmap Copy Failed: ${bufferWidth}x${bufferHeight}]",
                                        result = DetectionResultType.REJECTED,
                                        category = "BITMAP_CONVERT_FAILED",
                                        reason = "Screenshot captured (${bufferWidth}x${bufferHeight} px), but software ARGB_8888 copy failed.",
                                        detectionMethod = DetectionMethod.MLKIT_OCR,
                                        screenshotCaptured = true,
                                        imageDimensions = "${bufferWidth}x${bufferHeight}",
                                        isImageBlank = null,
                                        ocrRawOutput = null,
                                        ocrError = "Bitmap.copy(ARGB_8888) returned null"
                                    )
                                    return
                                }
                            }

                            // Null content returned from hardware buffer
                            isOcrProcessing = false
                            AppStateManager.addDiagnosticLog(
                                source = "$appName (Screen Capture)",
                                rawText = "[Null Bitmap from Hardware Buffer]",
                                result = DetectionResultType.REJECTED,
                                category = "HARDWARE_BUFFER_NULL",
                                reason = "Screenshot returned hardware buffer (${bufferWidth}x${bufferHeight} px), but wrapping into Bitmap returned null.",
                                detectionMethod = DetectionMethod.MLKIT_OCR,
                                screenshotCaptured = false,
                                imageDimensions = if (bufferWidth > 0) "${bufferWidth}x${bufferHeight}" else null,
                                isImageBlank = null,
                                ocrRawOutput = null,
                                ocrError = "Bitmap.wrapHardwareBuffer failed"
                            )
                        }

                        override fun onFailure(errorCode: Int) {
                            isOcrProcessing = false
                            val (errorName, errorExplanation) = when (errorCode) {
                                1 -> "INTERNAL_ERROR (1)" to "Android OS internal failure or driver restriction."
                                2 -> "NO_ACCESSIBILITY_ACCESS (2)" to "Screen capture denied by Android WindowManager (target app enforces FLAG_SECURE or lacks capture permission)."
                                3 -> "INTERVAL_TIME_SHORT (3)" to "Screenshots requested too rapidly (throttled by system rate-limiter)."
                                4 -> "INVALID_DISPLAY (4)" to "Default display ID is invalid or not available."
                                5 -> "INVALID_WINDOW (5)" to "Target window detached or secured."
                                else -> "ERROR_CODE_$errorCode" to "Unknown capture failure code."
                            }

                            val isFlagSecure = errorCode == 2
                            AppStateManager.addDiagnosticLog(
                                source = "$appName (Screen Capture)",
                                rawText = "[Capture Failed: $errorName]",
                                result = DetectionResultType.REJECTED,
                                category = if (isFlagSecure) "FLAG_SECURE_BLOCKED" else "SCREENSHOT_FAILED",
                                reason = "AccessibilityService.takeScreenshot failed ($errorName): $errorExplanation",
                                detectionMethod = DetectionMethod.MLKIT_OCR,
                                screenshotCaptured = false,
                                imageDimensions = null,
                                isImageBlank = null,
                                ocrRawOutput = null,
                                ocrError = "takeScreenshot onFailure($errorCode): $errorExplanation"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                isOcrProcessing = false
                AppStateManager.addDiagnosticLog(
                    source = "$appName (Screen Capture)",
                    rawText = "[Capture Exception: ${e.javaClass.simpleName}]",
                    result = DetectionResultType.REJECTED,
                    category = "SCREENSHOT_EXCEPTION",
                    reason = "takeScreenshot threw exception: ${e.localizedMessage ?: e.message ?: "Unknown error"}",
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    screenshotCaptured = false,
                    imageDimensions = null,
                    isImageBlank = null,
                    ocrRawOutput = null,
                    ocrError = "${e.javaClass.name}: ${e.message}"
                )
            }
        } else {
            // Android API < 30
            AppStateManager.addDiagnosticLog(
                source = "$appName (Accessibility Scan)",
                rawText = "[No Text Nodes Found]",
                result = DetectionResultType.REJECTED,
                category = "ACCESSIBILITY_EMPTY",
                reason = "Accessibility scan found 0 text nodes. (On-Device OCR fallback requires Android 11+ / API 30+).",
                detectionMethod = DetectionMethod.ACCESSIBILITY
            )
        }
    }

    private suspend fun runOcrProcessingOnBackground(
        bitmap: Bitmap,
        appName: String,
        pkgName: String,
        bitmapAnalysis: com.example.ai.BitmapAnalysisResult
    ) {
        val dimensions = "${bitmapAnalysis.width}x${bitmapAnalysis.height}"
        val ocrResult = OcrRecognitionEngine.recognizeTextFromBitmap(bitmap)
        val settings = AppStateManager.settings.value
        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, settings.detectQuestionsOnly)
        val detectedQuestionText = if (analysis.extractedQuestionText.isNotBlank()) analysis.extractedQuestionText else ocrResult.rawText
        val hasQuestionMark = detectedQuestionText.contains("?") || detectedQuestionText.contains("？") || detectedQuestionText.contains("¿")
        val hasExtractedText = ocrResult.rawText.isNotBlank()

        if (analysis.isQuestion && (hasQuestionMark || !settings.detectQuestionsOnly) && detectedQuestionText.isNotBlank()) {
            if (detectedQuestionText != lastProcessedText) {
                lastProcessedText = detectedQuestionText
                AppStateManager.onQuestionDetected(
                    context = this@QuestionDetectorAccessibilityService,
                    text = detectedQuestionText,
                    sourceApp = appName,
                    packageName = pkgName,
                    forcedBypass = false,
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    ocrLatencyMs = ocrResult.latencyMs
                )
            }
            AppStateManager.addDiagnosticLog(
                source = "$appName (ML Kit OCR)",
                rawText = detectedQuestionText,
                result = DetectionResultType.MATCHED,
                category = analysis.category,
                reason = "Screenshot captured ($dimensions, ${bitmapAnalysis.details}). ML Kit extracted ${ocrResult.detectedBlocks.size} blocks (${ocrResult.rawText.length} chars) in ${ocrResult.latencyMs}ms. Question pattern matched.",
                detectionMethod = DetectionMethod.MLKIT_OCR,
                latencyMs = ocrResult.latencyMs,
                screenshotCaptured = true,
                imageDimensions = dimensions,
                isImageBlank = false,
                ocrRawOutput = ocrResult.rawText,
                ocrError = null
            )
        } else {
            val category = when {
                !ocrResult.isSuccess -> "OCR_EXTRACTION_ERROR"
                !hasExtractedText -> "OCR_ZERO_TEXT_DETECTED"
                else -> analysis.category
            }

            val reason = when {
                !ocrResult.isSuccess ->
                    "Screenshot captured ($dimensions), but ML Kit TextRecognition failed in ${ocrResult.latencyMs}ms: ${ocrResult.errorMessage ?: "Unknown error"}"
                !hasExtractedText ->
                    "Screenshot captured ($dimensions, active game graphics), but ML Kit recognized 0 text blocks in ${ocrResult.latencyMs}ms. The game screen has no machine-readable Latin text glyphs."
                else ->
                    "Screenshot captured ($dimensions). ML Kit successfully extracted ${ocrResult.detectedBlocks.size} blocks (${ocrResult.rawText.length} chars) in ${ocrResult.latencyMs}ms, but text was rejected: ${analysis.reason}"
            }

            AppStateManager.addDiagnosticLog(
                source = "$appName (ML Kit OCR)",
                rawText = if (hasExtractedText) ocrResult.rawText else "[0 text blocks extracted]",
                result = DetectionResultType.REJECTED,
                category = category,
                reason = reason,
                detectionMethod = DetectionMethod.MLKIT_OCR,
                latencyMs = ocrResult.latencyMs,
                screenshotCaptured = true,
                imageDimensions = dimensions,
                isImageBlank = false,
                ocrRawOutput = if (hasExtractedText) ocrResult.rawText else "[Empty / 0 Blocks]",
                ocrError = if (!ocrResult.isSuccess) ocrResult.errorMessage else if (!hasExtractedText) "ML Kit returned 0 text blocks on active game canvas" else null
            )
        }
    }

    private fun isSystemOrKeyboardPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("com.android.systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("honeyboard") ||
                lower.contains("latin") ||
                lower == "android"
    }

    private fun collectVisibleTextNodesSafely(
        node: AccessibilityNodeInfo?,
        outList: MutableList<VisibleScannedNode>,
        screenWidth: Int,
        screenHeight: Int,
        currentDepth: Int,
        maxDepth: Int,
        maxNodes: Int
    ) {
        if (node == null || currentDepth > maxDepth || outList.size >= maxNodes) return

        // CRITICAL: Only consider nodes that are currently visible to the user on screen
        if (!node.isVisibleToUser) {
            return
        }

        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
        } catch (_: Exception) {
            return
        }

        // Viewport bounds check: discard offscreen / scrolled away nodes
        val viewportTop = 40
        val viewportBottom = screenHeight - 40

        val isWithinViewport = bounds.width() > 0 &&
                bounds.height() > 0 &&
                bounds.bottom > viewportTop &&
                bounds.top < viewportBottom &&
                bounds.right > 0 &&
                bounds.left < screenWidth

        if (!isWithinViewport) {
            return
        }

        // Skip non-message interactive controls such as buttons, seekbars, progress bars
        val className = node.className?.toString() ?: ""
        val isActionButton = className.contains("Button") ||
                className.contains("SeekBar") ||
                className.contains("ProgressBar") ||
                className.contains("TabWidget") ||
                className.contains("Switch") ||
                className.contains("CheckBox")

        if (!isActionButton) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 3 && !isIgnoredUiString(text)) {
                outList.add(
                    VisibleScannedNode(
                        text = text,
                        bounds = bounds,
                        bottomY = bounds.bottom,
                        topY = bounds.top
                    )
                )
            }

            val contentDesc = node.contentDescription?.toString()?.trim()
            if (!contentDesc.isNullOrBlank() && contentDesc.length >= 3 && contentDesc != text && !isIgnoredUiString(contentDesc)) {
                outList.add(
                    VisibleScannedNode(
                        text = contentDesc,
                        bounds = bounds,
                        bottomY = bounds.bottom,
                        topY = bounds.top
                    )
                )
            }
        }

        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            if (outList.size >= maxNodes) break
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectVisibleTextNodesSafely(child, outList, screenWidth, screenHeight, currentDepth + 1, maxDepth, maxNodes)
            }
        }
    }

    private fun isIgnoredUiString(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower in listOf(
            "type a message", "message", "search", "search...", "send",
            "calls", "chats", "status", "settings", "camera", "online",
            "typing...", "today", "yesterday", "delivered", "read", "photo", "video",
            "reply", "forward", "copy", "delete", "info"
        ) || lower.matches(Regex("^\\d{1,2}:\\d{2}(\\s*(am|pm))?$"))
    }

    override fun onInterrupt() {
        AppStateManager.setAccessibilityRunning(false)
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        AppStateManager.setAccessibilityRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}


