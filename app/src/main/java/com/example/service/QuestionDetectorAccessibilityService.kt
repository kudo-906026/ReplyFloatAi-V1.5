package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
    private var lastScanAttemptTime: Long = 0L
    private var lastRejectedLoggedText: String = ""
    private var lastOcrScanTime: Long = 0L
    private var isOcrProcessing: Boolean = false

    // Dedicated background executor for taking screenshots and image decoding off the main thread
    private val captureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun resetState() {
        lastProcessedText = ""
        lastProcessedTime = 0L
        lastScanAttemptTime = 0L
        lastRejectedLoggedText = ""
        lastOcrScanTime = 0L
        isOcrProcessing = false
        pendingEventScanJob?.cancel()
        pendingSettledScanJob?.cancel()
    }

    // Background coroutine scope ensuring zero UI/main thread blocking
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var continuousScanJob: kotlinx.coroutines.Job? = null
    private var pendingEventScanJob: kotlinx.coroutines.Job? = null
    private var pendingSettledScanJob: kotlinx.coroutines.Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppStateManager.init(this)
        AppStateManager.setAccessibilityRunning(true)

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = info
        } catch (_: Exception) {
        }

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
        val type = event.eventType
        // Only trigger scans on events that change screen text, window state, or scroll position
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val debounce = AppStateManager.settings.value.smartDebounceMs.toLong().coerceAtLeast(300L)
        if (now - lastScanAttemptTime < debounce) {
            // Trailing edge debounce: schedule deferred scan when rapid bursts subside
            pendingEventScanJob?.cancel()
            pendingEventScanJob = serviceScope.launch(Dispatchers.Main) {
                delay(debounce)
                performWindowScan(isContinuousTick = false, forcedBypass = true)
            }
            return
        }

        // 1. Immediate scan upon accessibility event
        performWindowScan(isContinuousTick = false, forcedBypass = false, event = event)

        // 2. Post-render settling scan (180ms trailing): guarantees that text rendered after the initial layout phase is captured
        pendingSettledScanJob?.cancel()
        pendingSettledScanJob = serviceScope.launch(Dispatchers.Main) {
            delay(180L)
            performWindowScan(isContinuousTick = false, forcedBypass = true)
        }
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

        val now = System.currentTimeMillis()
        if (!forcedBypass && (now - lastScanAttemptTime < settings.smartDebounceMs)) {
            return
        }
        lastScanAttemptTime = now

        // Resolve application window root node and package name.
        // In Android, if the keyboard (Gboard, Swiftkey, etc.) or SystemUI is active or focused,
        // rootInActiveWindow points to the keyboard! We must resolve the actual TYPE_APPLICATION window.
        var targetRootNode: AccessibilityNodeInfo? = null
        var pkgName: String? = null

        val eventPkg = event?.packageName?.toString()
        if (eventPkg != null && eventPkg != applicationContext.packageName && !isSystemOrKeyboardPackage(eventPkg)) {
            pkgName = eventPkg
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val appWindows = windows.filter { win ->
                    win.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    win.root != null &&
                    win.root?.packageName != null &&
                    win.root?.packageName?.toString() != applicationContext.packageName &&
                    !isSystemOrKeyboardPackage(win.root?.packageName?.toString() ?: "")
                }
                val appWindow = appWindows.firstOrNull { it.isActive || it.isFocused } ?: appWindows.firstOrNull()
                if (appWindow != null) {
                    targetRootNode = appWindow.root
                    if (pkgName == null) {
                        pkgName = targetRootNode?.packageName?.toString()
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (targetRootNode == null) {
            val rawRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            val rawPkg = rawRoot?.packageName?.toString()
            if (rawPkg != null && rawPkg != applicationContext.packageName && !isSystemOrKeyboardPackage(rawPkg)) {
                targetRootNode = rawRoot
                if (pkgName == null) pkgName = rawPkg
            }
        }

        if (pkgName == null) {
            pkgName = targetRootNode?.packageName?.toString() ?: eventPkg
        }

        if (pkgName == null || pkgName == applicationContext.packageName || isSystemOrKeyboardPackage(pkgName)) {
            return
        }

        val whitelistedApp = settings.appsWhitelist.find { it.packageName == pkgName && it.isEnabled }
        val appName = whitelistedApp?.appName ?: getAppNameFromPackage(pkgName)

        if (whitelistedApp == null && !forcedBypass) {
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<VisibleScannedNode>()
        var foundAnyReadableNodes = false

        // 1. Deep traversal of application window root node with reverse child traversal
        if (targetRootNode != null) {
            collectVisibleTextNodesSafely(
                node = targetRootNode,
                outList = visibleNodes,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentDepth = 0,
                maxDepth = 30,
                maxNodes = 250
            )
        }

        // 2. ALWAYS collect from event.source if present to capture newly inserted / updated views
        if (event != null) {
            val sourceNode = try { event.source } catch (_: Exception) { null }
            if (sourceNode != null) {
                collectVisibleTextNodesSafely(
                    node = sourceNode,
                    outList = visibleNodes,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    currentDepth = 0,
                    maxDepth = 20,
                    maxNodes = 100
                )
            }
        }

        // 3. Fallback to event text if no nodes were collected
        if (visibleNodes.isEmpty() && event != null) {
            val eventTexts = event.text.mapNotNull { it?.toString()?.trim() }.filter { it.length >= 3 && !isIgnoredUiString(it) }
            for (raw in eventTexts) {
                visibleNodes.add(
                    VisibleScannedNode(
                        text = raw,
                        bounds = Rect(0, screenHeight / 2, screenWidth, screenHeight),
                        bottomY = screenHeight - 20,
                        topY = screenHeight / 2
                    )
                )
            }
        }

        var foundQuestion = false

        if (visibleNodes.isNotEmpty()) {
            foundAnyReadableNodes = true

            // Deduplicate visible text entries preserving lowest screen position (most recent message)
            val distinctNodes = visibleNodes
                .groupBy { it.text }
                .map { (_, nodes) -> nodes.maxByOrNull { it.bottomY } ?: nodes.first() }

            val sortedNodes = distinctNodes.sortedByDescending { it.bottomY }

            // Find lowest valid question node (testing both cleaned and raw candidate text)
            var matchedQuestionText: String? = null
            var matchedAnalysis: com.example.ai.DetectionAnalysisResult? = null

            for (node in sortedNodes) {
                val rawCandidate = node.text.trim()
                if (rawCandidate.length < 3) continue

                // A. Cleaned candidate (timestamps, sender prefixes, checkmarks stripped)
                val cleanCandidate = OcrRecognitionEngine.cleanCandidateLine(rawCandidate)
                if (cleanCandidate.length >= 3 && !isIgnoredUiString(cleanCandidate)) {
                    val cleanAnalysis = QuestionDetectionEngine.analyze(cleanCandidate, settings.detectQuestionsOnly, settings.triggers)
                    if (cleanAnalysis.isQuestion) {
                        matchedQuestionText = cleanCandidate
                        matchedAnalysis = cleanAnalysis
                        break
                    }
                }

                // B. Raw candidate text
                if (cleanCandidate != rawCandidate && !isIgnoredUiString(rawCandidate)) {
                    val rawAnalysis = QuestionDetectionEngine.analyze(rawCandidate, settings.detectQuestionsOnly, settings.triggers)
                    if (rawAnalysis.isQuestion) {
                        matchedQuestionText = rawCandidate
                        matchedAnalysis = rawAnalysis
                        break
                    }
                }
            }

            if (matchedQuestionText != null && matchedAnalysis != null) {
                foundQuestion = true
                val candidateText = matchedQuestionText

                val isSameAsLastProcessed = candidateText == lastProcessedText && (now - lastProcessedTime < 4000L)
                val isSameAsCurrentActive = !forcedBypass && candidateText == AppStateManager.currentQuestion.value?.text

                if (!isSameAsLastProcessed && !isSameAsCurrentActive) {
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
            }

            // If no valid question matched, explicitly evaluate candidate nodes for triggers / question marks
            // and log diagnostic rejection so a question is NEVER simply missing from the log!
            if (!foundQuestion) {
                // Priority: find candidate with ? or trigger word that was evaluated and rejected
                val candidateWithTrigger = sortedNodes.firstOrNull { node ->
                    val txt = node.text
                    txt.contains("?") || txt.contains("？") || txt.contains("¿") ||
                    QuestionDetectionEngine.matchesAnyTrigger(txt, settings.triggers).first
                } ?: if (!isContinuousTick) sortedNodes.firstOrNull() else null

                if (candidateWithTrigger != null) {
                    val loggedText = candidateWithTrigger.text.trim()
                    if (loggedText != lastRejectedLoggedText && loggedText != lastProcessedText) {
                        lastRejectedLoggedText = loggedText
                        val cleanLogged = OcrRecognitionEngine.cleanCandidateLine(loggedText)
                        val analysis = QuestionDetectionEngine.analyze(
                            if (cleanLogged.length >= 3) cleanLogged else loggedText,
                            settings.detectQuestionsOnly,
                            settings.triggers
                        )
                        AppStateManager.addDiagnosticLog(
                            source = "$appName (Visible Screen Node)",
                            rawText = loggedText,
                            result = DetectionResultType.REJECTED,
                            category = analysis.category,
                            reason = analysis.reason,
                            detectionMethod = DetectionMethod.ACCESSIBILITY
                        )
                    }
                }
            }
        }

        // 2. OCR FALLBACK: Trigger on-device ML Kit OCR when accessibility did NOT find any valid question,
        // or when accessibility found 0 readable visible nodes.
        if (!foundQuestion && settings.enableOcrFallback) {
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

            val targetDisplayId = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    windows.firstOrNull { it.isActive || it.isFocused }?.displayId
                        ?: Display.DEFAULT_DISPLAY
                } else {
                    Display.DEFAULT_DISPLAY
                }
            }.getOrNull() ?: Display.DEFAULT_DISPLAY

            try {
                takeScreenshot(
                    targetDisplayId,
                    captureExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bufferWidth = try { hardwareBuffer.width } catch (_: Exception) { 0 }
                            val bufferHeight = try { hardwareBuffer.height } catch (_: Exception) { 0 }
                            val bufferFormat = try { hardwareBuffer.format } catch (_: Exception) { -1 }

                            val formatName = when (bufferFormat) {
                                1 -> "RGBA_8888"
                                2 -> "RGBX_8888"
                                3 -> "RGB_888"
                                4 -> "RGB_565"
                                22 -> "RGBA_FP16"
                                43 -> "RGBA_1010102"
                                else -> "FORMAT_$bufferFormat"
                            }

                            var hwBitmap: Bitmap? = null
                            var softwareBitmap: Bitmap? = null
                            var conversionNotes = ""

                            try {
                                hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                if (hwBitmap != null) {
                                    // 1. Primary attempt: software copy (ARGB_8888)
                                    try {
                                        softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        if (softwareBitmap != null) {
                                            conversionNotes = "ARGB_8888 software copy"
                                        }
                                    } catch (e: Exception) {
                                        conversionNotes = "hwBitmap.copy failed: ${e.message}"
                                    }

                                    // 2. Secondary fallback: software Canvas draw fallback if copy was null
                                    if (softwareBitmap == null && bufferWidth > 0 && bufferHeight > 0) {
                                        try {
                                            val canvasBitmap = Bitmap.createBitmap(bufferWidth, bufferHeight, Bitmap.Config.ARGB_8888)
                                            val canvas = Canvas(canvasBitmap)
                                            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                                            canvas.drawBitmap(hwBitmap, 0f, 0f, paint)
                                            softwareBitmap = canvasBitmap
                                            conversionNotes = (if (conversionNotes.isNotBlank()) "$conversionNotes; " else "") + "software Canvas draw fallback successful"
                                        } catch (e: Exception) {
                                            conversionNotes = (if (conversionNotes.isNotBlank()) "$conversionNotes; " else "") + "Canvas draw failed: ${e.message}"
                                        }
                                    }
                                } else {
                                    conversionNotes = "Bitmap.wrapHardwareBuffer returned null"
                                }
                            } catch (e: Exception) {
                                conversionNotes = "Exception wrapping hardware buffer: ${e.message}"
                            }

                            // If software copy is unavailable, ML Kit on Android 10+ accepts hardware bitmaps directly!
                            val bitmapForOcr = softwareBitmap ?: hwBitmap

                            if (bitmapForOcr != null) {
                                val isHardwareDirect = (softwareBitmap == null)
                                if (isHardwareDirect) {
                                    conversionNotes = (if (conversionNotes.isNotBlank()) "$conversionNotes; " else "") + "Hardware Bitmap direct mode"
                                }

                                serviceScope.launch(Dispatchers.Default) {
                                    try {
                                        runOcrProcessingOnBackground(
                                            bitmap = bitmapForOcr,
                                            appName = appName,
                                            pkgName = pkgName,
                                            bufferWidth = bufferWidth,
                                            bufferHeight = bufferHeight,
                                            formatName = formatName,
                                            conversionNotes = conversionNotes
                                        )
                                    } finally {
                                        isOcrProcessing = false
                                        try { softwareBitmap?.recycle() } catch (_: Exception) {}
                                        try { hwBitmap?.recycle() } catch (_: Exception) {}
                                        try { hardwareBuffer.close() } catch (_: Exception) {}
                                    }
                                }
                                return
                            }

                            // If conversion completely failed
                            isOcrProcessing = false
                            try { hardwareBuffer.close() } catch (_: Exception) {}

                            AppStateManager.addDiagnosticLog(
                                source = "$appName (Screen Capture)",
                                rawText = "[Buffer Conversion Failed: ${bufferWidth}x${bufferHeight} $formatName]",
                                result = DetectionResultType.REJECTED,
                                category = "BITMAP_CONVERT_FAILED",
                                reason = "Screen frame buffer received (${bufferWidth}x${bufferHeight}, format=$formatName), but bitmap creation failed: $conversionNotes",
                                detectionMethod = DetectionMethod.MLKIT_OCR,
                                screenshotCaptured = true,
                                imageDimensions = if (bufferWidth > 0) "${bufferWidth}x${bufferHeight}" else null,
                                isImageBlank = false,
                                ocrRawOutput = null,
                                ocrError = "Conversion failure: $conversionNotes"
                            )
                        }

                        override fun onFailure(errorCode: Int) {
                            isOcrProcessing = false
                            val (errorName, errorExplanation) = when (errorCode) {
                                1 -> "INTERNAL_ERROR (1)" to "Android OS internal screen capture error or compositor synchronization issue."
                                2 -> "NO_ACCESSIBILITY_ACCESS (2)" to "Screenshot permission not granted to Accessibility Service by Android OS. Verify service is enabled in Android Accessibility settings."
                                3 -> "INTERVAL_TIME_SHORT (3)" to "Screenshots requested too rapidly. Throttled by Android system rate limiter."
                                4 -> "INVALID_DISPLAY (4)" to "Display ID ($targetDisplayId) is invalid or unavailable."
                                5 -> "INVALID_WINDOW (5)" to "Target window is detached, invalid, or secured."
                                else -> "ERROR_CODE_$errorCode" to "takeScreenshot failure code $errorCode."
                            }

                            AppStateManager.addDiagnosticLog(
                                source = "$appName (Screen Capture)",
                                rawText = "[Capture Failed: $errorName]",
                                result = DetectionResultType.REJECTED,
                                category = "SCREENSHOT_FAILED",
                                reason = "AccessibilityService.takeScreenshot failed on display $targetDisplayId ($errorName): $errorExplanation",
                                detectionMethod = DetectionMethod.MLKIT_OCR,
                                screenshotCaptured = false,
                                imageDimensions = null,
                                isImageBlank = false,
                                ocrRawOutput = null,
                                ocrError = "takeScreenshot errorCode=$errorCode ($errorName): $errorExplanation"
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
                    reason = "takeScreenshot invocation threw exception on display $targetDisplayId: ${e.localizedMessage ?: e.message ?: "Unknown error"}",
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    screenshotCaptured = false,
                    imageDimensions = null,
                    isImageBlank = false,
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
        bufferWidth: Int,
        bufferHeight: Int,
        formatName: String,
        conversionNotes: String
    ) {
        val dimensions = "${bufferWidth}x${bufferHeight}"
        val ocrResult = OcrRecognitionEngine.recognizeTextFromBitmap(bitmap)
        val settings = AppStateManager.settings.value
        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, settings.detectQuestionsOnly, settings.triggers)
        val detectedQuestionText = analysis.extractedQuestionText
        val hasExtractedText = ocrResult.rawText.isNotBlank()

        if (analysis.isQuestion && detectedQuestionText.isNotBlank()) {
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
                reason = "Screenshot captured ($dimensions $formatName, $conversionNotes). ML Kit extracted ${ocrResult.detectedLines.size} lines / ${ocrResult.detectedBlocks.size} blocks in ${ocrResult.latencyMs}ms. Question pattern matched on line: '$detectedQuestionText'.",
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
                    "Screenshot captured ($dimensions $formatName), but ML Kit TextRecognition failed in ${ocrResult.latencyMs}ms: ${ocrResult.errorMessage ?: "Unknown error"}"
                !hasExtractedText ->
                    "Screenshot captured ($dimensions $formatName). ML Kit recognized 0 text blocks in ${ocrResult.latencyMs}ms. Raw screen image contains no machine-readable Latin glyphs ($conversionNotes)."
                else ->
                    "Screenshot captured ($dimensions $formatName). ML Kit extracted ${ocrResult.detectedLines.size} lines / ${ocrResult.detectedBlocks.size} blocks in ${ocrResult.latencyMs}ms, but no line matched active question criteria: ${analysis.reason}"
            }

            val ocrErrorDesc = when {
                !ocrResult.isSuccess -> ocrResult.errorMessage ?: "ML Kit inference failed"
                !hasExtractedText -> "ML Kit recognized 0 text blocks in frame ($dimensions $formatName)"
                else -> null
            }

            // Identify primary evaluated line (e.g. line containing ? or lowest chat line) to display individual line in diagnostics
            val evaluatedCandidateLine = if (ocrResult.detectedLines.isNotEmpty()) {
                ocrResult.detectedLines.asReversed().firstOrNull { line ->
                    val t = line.text.trim()
                    t.contains("?") || t.contains("？") || t.contains("¿") ||
                    QuestionDetectionEngine.matchesAnyTrigger(t, settings.triggers).first
                }?.text?.trim()
                ?: ocrResult.detectedLines.lastOrNull()?.text?.trim()
            } else {
                ocrResult.rawText.lines().lastOrNull { it.isNotBlank() }?.trim()
            }

            val displayedRawText = when {
                !hasExtractedText -> "[0 text blocks in frame ($dimensions $formatName)]"
                !evaluatedCandidateLine.isNullOrBlank() -> evaluatedCandidateLine
                else -> ocrResult.rawText
            }

            AppStateManager.addDiagnosticLog(
                source = "$appName (ML Kit OCR)",
                rawText = displayedRawText,
                result = DetectionResultType.REJECTED,
                category = category,
                reason = reason,
                detectionMethod = DetectionMethod.MLKIT_OCR,
                latencyMs = ocrResult.latencyMs,
                screenshotCaptured = true,
                imageDimensions = dimensions,
                isImageBlank = false,
                ocrRawOutput = if (hasExtractedText) ocrResult.rawText else "[Empty / 0 Blocks]",
                ocrError = ocrErrorDesc
            )
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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

        // Viewport bounds check: include all nodes visible in the display area without artificial edge clipping
        val isWithinViewport = bounds.width() > 0 &&
                bounds.height() > 0 &&
                bounds.bottom > 0 &&
                bounds.top < screenHeight &&
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
        // Traverse children in reverse order (bottom-up in messaging lists so newest messages are prioritized)
        for (i in (childCount - 1) downTo 0) {
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
        try {
            captureExecutor.shutdown()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}


