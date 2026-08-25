package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.state.AppStateManager
import com.example.util.QuestionValidator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class QuestionDetectorAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var periodicOcrJob: Job? = null
    private var lastEmittedQuestionNormalized: String? = null
    private val localEmittedQuestions = LinkedHashSet<String>()
    private val isOcrRunning = AtomicBoolean(false)

    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "QuestionDetectorAccessibilityService connected")
        AppStateManager.init(this)
        AppStateManager.setAccessibilityRunning(true)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 600
        serviceInfo = info

        startPeriodicOcrWorker()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. If scanning is paused by user, immediately ignore all accessibility events
        if (!AppStateManager.settings.value.scanningEnabled) {
            return
        }

        // Skip events from our own app and overlay windows
        val packageName = event.packageName?.toString() ?: ""
        if (isOurAppPackage(packageName)) {
            return
        }

        // Filter out rapid micro-events: skip non-textual cursor/selection fluctuations
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val changeTypes = event.contentChangeTypes
            if (changeTypes != 0 && (changeTypes and (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)) == 0) {
                return
            }
        }

        // Debounce scanning to ~700ms to ensure the screen stabilizes
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            delay(700)
            scanActiveWindowForQuestions(packageName)
        }
    }

    private fun startPeriodicOcrWorker() {
        periodicOcrJob?.cancel()
        periodicOcrJob = serviceScope.launch {
            while (true) {
                delay(2500)
                try {
                    val settings = AppStateManager.settings.value
                    if (settings.scanningEnabled && !AppStateManager.isGenerating.value) {
                        // Opportunistic fallback check for game screens or custom canvas apps
                        triggerOcrScreenScan("Game/Custom-UI Canvas")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Periodic OCR cycle caught exception: ${e.message}")
                }
            }
        }
    }

    private fun scanActiveWindowForQuestions(sourcePackage: String) {
        // Double check scanning is enabled
        if (!AppStateManager.settings.value.scanningEnabled) return
        if (isOurAppPackage(sourcePackage)) return

        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root in active window", e)
            null
        }

        // Verify root node doesn't belong to our app/overlay
        val rootPkg = rootNode?.packageName?.toString() ?: ""
        if (isOurAppPackage(rootPkg)) {
            return
        }

        var foundQuestionInNodes = false

        if (rootNode != null) {
            try {
                val questions = ArrayList<String>(4)
                findQuestionsInNode(rootNode, questions, 0)

                if (questions.isNotEmpty()) {
                    val targetQuestion = questions.lastOrNull()
                    if (!targetQuestion.isNullOrBlank()) {
                        val normalized = normalizeForDedup(targetQuestion)
                        if (normalized.isNotBlank()) {
                            if (normalized != lastEmittedQuestionNormalized && !localEmittedQuestions.contains(normalized)) {
                                markAndEmitQuestion(targetQuestion, normalized, sourcePackage, isOcr = false)
                                foundQuestionInNodes = true
                            } else {
                                foundQuestionInNodes = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning accessibility nodes", e)
            }
        }

        // If native node scanning found nothing (e.g. Unity, Cocos2d, Flutter custom canvas, games),
        // invoke OCR screen text recognition to capture text directly from the screen
        if (!foundQuestionInNodes) {
            triggerOcrScreenScan(sourcePackage)
        }
    }

    private fun triggerOcrScreenScan(sourcePackage: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!AppStateManager.settings.value.scanningEnabled) return
        if (isOcrRunning.getAndSet(true)) return

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        serviceScope.launch {
                            try {
                                val hardwareBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()

                                if (bitmap != null) {
                                    runOcrOnBitmap(bitmap, sourcePackage)
                                } else {
                                    isOcrRunning.set(false)
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error decoding screenshot bitmap", e)
                                isOcrRunning.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.d(TAG, "Accessibility takeScreenshot failed with code: $errorCode")
                        isOcrRunning.set(false)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "takeScreenshot invocation error", e)
            isOcrRunning.set(false)
        }
    }

    private fun runOcrOnBitmap(bitmap: Bitmap, sourcePackage: String) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    try {
                        bitmap.recycle()
                        handleOcrResults(visionText, sourcePackage)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling OCR text results", e)
                    } finally {
                        isOcrRunning.set(false)
                    }
                }
                .addOnFailureListener { e ->
                    bitmap.recycle()
                    isOcrRunning.set(false)
                    Log.w(TAG, "OCR recognition process failed", e)
                }
        } catch (e: Throwable) {
            bitmap.recycle()
            isOcrRunning.set(false)
            Log.e(TAG, "Error preparing image for OCR", e)
        }
    }

    private fun handleOcrResults(visionText: Text, sourcePackage: String) {
        if (!AppStateManager.settings.value.scanningEnabled) return

        val detectedCandidates = mutableListOf<String>()

        for (block in visionText.textBlocks) {
            val blockText = block.text
            if (isInternalOverlayText(blockText)) continue

            // 1. First inspect individual lines (game chats, in-app messages)
            for (line in block.lines) {
                val lineText = line.text
                if (isInternalOverlayText(lineText)) continue
                val question = QuestionValidator.cleanAndExtractQuestion(lineText)
                if (question != null && !detectedCandidates.contains(question)) {
                    detectedCandidates.add(question)
                }
            }

            // 2. Also inspect multi-line block content
            val blockQuestion = QuestionValidator.cleanAndExtractQuestion(blockText)
            if (blockQuestion != null && !detectedCandidates.contains(blockQuestion)) {
                detectedCandidates.add(blockQuestion)
            }
        }

        if (detectedCandidates.isNotEmpty()) {
            val targetQuestion = detectedCandidates.last()
            val normalized = normalizeForDedup(targetQuestion)
            if (normalized.isNotBlank()) {
                if (normalized != lastEmittedQuestionNormalized && !localEmittedQuestions.contains(normalized)) {
                    markAndEmitQuestion(targetQuestion, normalized, sourcePackage, isOcr = true)
                }
            }
        }
    }

    private fun markAndEmitQuestion(
        questionText: String,
        normalized: String,
        sourcePackage: String,
        isOcr: Boolean
    ) {
        localEmittedQuestions.add(normalized)
        if (localEmittedQuestions.size > 60) {
            val it = localEmittedQuestions.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        lastEmittedQuestionNormalized = normalized

        val tagPrefix = if (isOcr) "[OCR_SCREEN_SCAN]" else "[NATIVE_A11Y_NODE]"
        Log.d(TAG, "$tagPrefix Detected question: \"$questionText\" (Source: $sourcePackage)")
        AppStateManager.onQuestionDetected(questionText, sourcePackage, force = false)
    }

    private fun normalizeForDedup(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findQuestionsInNode(
        node: AccessibilityNodeInfo?,
        outQuestions: MutableList<String>,
        depth: Int
    ) {
        if (node == null || depth > 10 || outQuestions.size >= 5) return

        // Exclude our own app's nodes / overlay elements
        val nodePkg = node.packageName?.toString() ?: ""
        if (isOurAppPackage(nodePkg)) {
            return
        }

        // Skip invisible nodes to reduce traversal overhead
        if (!node.isVisibleToUser) {
            return
        }

        // Check text and contentDescription
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        checkAndAddQuestion(text, outQuestions)
        checkAndAddQuestion(desc, outQuestions)

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                findQuestionsInNode(child, outQuestions, depth + 1)
            }
        }
    }

    private fun checkAndAddQuestion(text: String?, outQuestions: MutableList<String>) {
        if (text.isNullOrBlank() || text.length < 3) return
        if (isInternalOverlayText(text)) return

        val extractedQuestion = QuestionValidator.cleanAndExtractQuestion(text)
        if (extractedQuestion != null && !outQuestions.contains(extractedQuestion)) {
            outQuestions.add(extractedQuestion)
        }
    }

    private fun isOurAppPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        return pkg == this.packageName ||
                pkg == "com.example" ||
                pkg.contains("replyfloatai", ignoreCase = true)
    }

    private fun isInternalOverlayText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("replyfloatai") ||
                lower.contains("detected question") ||
                lower.contains("suggested replies") ||
                lower.contains("couldn't generate reply") ||
                lower.contains("generating replies") ||
                lower.contains("tap to retry") ||
                lower.contains("gemini is crafting") ||
                lower.contains("ai studio key active")
    }

    override fun onInterrupt() {
        Log.d(TAG, "QuestionDetectorAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "QuestionDetectorAccessibilityService destroyed")
        AppStateManager.setAccessibilityRunning(false)
        serviceScope.cancel()
        try {
            textRecognizer.close()
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "QuestionDetectorService"
    }
}
