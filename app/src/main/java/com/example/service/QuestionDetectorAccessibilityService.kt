package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
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
import kotlinx.coroutines.launch

class QuestionDetectorAccessibilityService : AccessibilityService() {

    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0L
    private var lastOcrScanTime: Long = 0L
    private var isOcrProcessing: Boolean = false

    // Background coroutine scope ensuring zero UI/main thread blocking
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppStateManager.setAccessibilityRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        // Check if package is whitelisted
        val settings = AppStateManager.settings.value
        val whitelistedApp = settings.appsWhitelist.find { it.packageName == pkgName && it.isEnabled }
        if (whitelistedApp == null) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < settings.smartDebounceMs) {
            // Debounce active
            return
        }

        val appName = whitelistedApp.appName

        // 1. PRIMARY SCAN: Fast Accessibility Node / Event Text Scan (Instant, zero latency)
        val eventTexts = event.text.mapNotNull { it?.toString() }
        for (raw in eventTexts) {
            if (processCandidateText(raw, appName, pkgName, "AccessibilityEvent.text")) {
                lastProcessedTime = now
                return
            }
        }

        // 2. Scan window node hierarchy
        var foundAnyReadableNodes = false
        if (settings.continuousScreenAnalysis) {
            try {
                rootInActiveWindow?.let { rootNode ->
                    val collectedCandidates = mutableListOf<String>()
                    collectTextNodesSafely(rootNode, collectedCandidates, currentDepth = 0, maxDepth = 8, maxNodes = 40)

                    if (collectedCandidates.isNotEmpty()) {
                        foundAnyReadableNodes = true
                    }

                    // Test individual text nodes first
                    for (candidate in collectedCandidates) {
                        if (processCandidateText(candidate, appName, pkgName, "ScreenNode")) {
                            lastProcessedTime = now
                            return
                        }
                    }

                    // Test concatenated sibling text if multiple consecutive text segments exist
                    if (collectedCandidates.size > 1) {
                        val combined = collectedCandidates.take(5).joinToString(" ").trim()
                        if (combined.length > 6 && combined != lastProcessedText) {
                            if (processCandidateText(combined, appName, pkgName, "ConcatenatedNodes")) {
                                lastProcessedTime = now
                                return
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Safeguard against framework access exceptions
            }
        }

        // 3. OCR FALLBACK ONLY: Trigger on-device ML Kit OCR ONLY when accessibility found NO readable text
        if (!foundAnyReadableNodes && settings.enableOcrFallback) {
            triggerOcrFallbackIfEligible(appName, pkgName, now, settings.ocrDebounceMs)
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
                                    // Process entirely on background coroutine
                                    serviceScope.launch(Dispatchers.Default) {
                                        try {
                                            runOcrProcessingOnBackground(softwareCopy, appName, pkgName)
                                        } finally {
                                            isOcrProcessing = false
                                        }
                                    }
                                    return
                                }
                            }
                            isOcrProcessing = false
                        }

                        override fun onFailure(errorCode: Int) {
                            isOcrProcessing = false
                            AppStateManager.addDiagnosticLog(
                                source = "$appName (Screen Capture)",
                                rawText = "[No Accessibility Text]",
                                result = DetectionResultType.REJECTED,
                                category = "SCREENSHOT_UNAVAILABLE",
                                reason = "Screen capture failed with error code $errorCode. Window is protected or uncapturable.",
                                detectionMethod = DetectionMethod.MLKIT_OCR
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                isOcrProcessing = false
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

    private suspend fun runOcrProcessingOnBackground(bitmap: Bitmap, appName: String, pkgName: String) {
        val ocrResult = OcrRecognitionEngine.recognizeTextFromBitmap(bitmap)
        val settings = AppStateManager.settings.value
        val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, settings.detectQuestionsOnly)

        if (analysis.isQuestion && ocrResult.rawText.isNotBlank()) {
            if (ocrResult.rawText != lastProcessedText) {
                lastProcessedText = ocrResult.rawText
                AppStateManager.onQuestionDetected(
                    context = this@QuestionDetectorAccessibilityService,
                    text = ocrResult.rawText,
                    sourceApp = appName,
                    packageName = pkgName,
                    forcedBypass = true,
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    ocrLatencyMs = ocrResult.latencyMs
                )
            }
        } else {
            // Log rejection diagnostic clearly identifying ML Kit OCR Fallback
            AppStateManager.addDiagnosticLog(
                source = "$appName (ML Kit OCR Fallback)",
                rawText = if (ocrResult.rawText.isNotBlank()) ocrResult.rawText else "[No OCR Text in Image]",
                result = DetectionResultType.REJECTED,
                category = analysis.category,
                reason = if (ocrResult.rawText.isBlank()) {
                    "Accessibility returned 0 nodes. ML Kit OCR scanned screen in ${ocrResult.latencyMs}ms but found no readable text."
                } else {
                    "Accessibility returned 0 nodes. ML Kit OCR extracted text in ${ocrResult.latencyMs}ms: ${analysis.reason}"
                },
                detectionMethod = DetectionMethod.MLKIT_OCR,
                latencyMs = ocrResult.latencyMs
            )
        }
    }

    private fun collectTextNodesSafely(
        node: AccessibilityNodeInfo?,
        outList: MutableList<String>,
        currentDepth: Int,
        maxDepth: Int,
        maxNodes: Int
    ) {
        if (node == null || currentDepth > maxDepth || outList.size >= maxNodes) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length >= 3) {
            outList.add(text)
        }

        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrBlank() && contentDesc.length >= 3 && contentDesc != text) {
            outList.add(contentDesc)
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (outList.size >= maxNodes) break
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectTextNodesSafely(child, outList, currentDepth + 1, maxDepth, maxNodes)
            }
        }
    }

    private fun processCandidateText(raw: String, appName: String, pkgName: String, sourceTag: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length < 3) return false

        // Prevent duplicate processing of the exact same message within short period
        if (trimmed == lastProcessedText) {
            return false
        }

        val settings = AppStateManager.settings.value
        val analysis = QuestionDetectionEngine.analyze(trimmed, settings.detectQuestionsOnly)

        if (analysis.isQuestion) {
            lastProcessedText = trimmed
            AppStateManager.onQuestionDetected(
                context = this,
                text = trimmed,
                sourceApp = appName,
                packageName = pkgName,
                forcedBypass = true,
                detectionMethod = DetectionMethod.ACCESSIBILITY
            )
            return true
        } else {
            // Log rejection diagnostic so it is transparent that scanning ran
            AppStateManager.addDiagnosticLog(
                source = "$appName ($sourceTag)",
                rawText = trimmed,
                result = DetectionResultType.REJECTED,
                category = analysis.category,
                reason = analysis.reason,
                detectionMethod = DetectionMethod.ACCESSIBILITY
            )
        }

        return false
    }

    override fun onInterrupt() {
        AppStateManager.setAccessibilityRunning(false)
    }

    override fun onDestroy() {
        AppStateManager.setAccessibilityRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}


