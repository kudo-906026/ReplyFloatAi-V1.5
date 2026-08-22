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
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 150
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Skip events from our own app
        val packageName = event.packageName?.toString() ?: ""
        if (packageName == this.packageName) {
            return
        }

        // Debounce scanning to avoid high CPU usage or thread contention
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            delay(250) // Debounce 250ms
            scanActiveWindowForQuestions(packageName)
        }
    }

    private fun scanActiveWindowForQuestions(sourcePackage: String) {
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root in active window", e)
            null
        } ?: return

        try {
            val questions = mutableListOf<String>()
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
        if (node == null || depth > 25 || outQuestions.size >= 10) return

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

        if (text.contains("?") || text.contains("？")) {
            val clean = text.trim()
            if (clean.length in 4..300 && !outQuestions.contains(clean)) {
                outQuestions.add(clean)
            }
        }
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
