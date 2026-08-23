package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuestionDetectorAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "QuestionDetectorAccessibilityService connected")
        AppStateManager.setAccessibilityRunning(true)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 900
        serviceInfo = info
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

        // Filter out rapid micro-events: skip non-textual cursor/selection/scroll fluctuations
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val changeTypes = event.contentChangeTypes
            if (changeTypes != 0 && (changeTypes and (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)) == 0) {
                return
            }
        }

        // Debounce scanning to ~950ms to ensure the screen stops changing before analyzing
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            delay(950)
            scanActiveWindowForQuestions(packageName)
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
        } ?: return

        // Verify root node doesn't belong to our app/overlay
        val rootPkg = rootNode.packageName?.toString() ?: ""
        if (isOurAppPackage(rootPkg)) {
            return
        }

        try {
            val questions = ArrayList<String>(4)
            findQuestionsInNode(rootNode, questions, 0)

            if (questions.isNotEmpty()) {
                // Get the most recent / deepest question
                val targetQuestion = questions.lastOrNull()
                if (!targetQuestion.isNullOrBlank()) {
                    AppStateManager.onQuestionDetected(targetQuestion, sourcePackage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning accessibility nodes", e)
        }
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
            } catch (e: Exception) {
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

        if (text.contains("?") || text.contains("？")) {
            val clean = text.trim()
            if (clean.length in 4..300 && !outQuestions.contains(clean)) {
                outQuestions.add(clean)
            }
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
                lower.contains("gemini is crafting")
    }

    override fun onInterrupt() {
        Log.d(TAG, "QuestionDetectorAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "QuestionDetectorAccessibilityService destroyed")
        AppStateManager.setAccessibilityRunning(false)
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "QuestionDetectorService"
    }
}
