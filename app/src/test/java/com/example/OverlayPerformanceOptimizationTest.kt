package com.example

import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.state.AppStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OverlayPerformanceOptimizationTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
        AppStateManager.clearDiagnosticLogs()
        AppStateManager.clearReplies()
    }

    @Test
    fun testDuplicateQuestionDebounce_PreventsRapidReanalysis() {
        val question = "Are we still meeting at 5pm?"

        // Question initially not processed
        assertFalse(AppStateManager.isQuestionAlreadyProcessed(question))

        // First detection
        AppStateManager.onQuestionDetected(
            context = null,
            text = question,
            sourceApp = "WhatsApp",
            packageName = "com.whatsapp",
            forcedBypass = false,
            detectionMethod = DetectionMethod.ACCESSIBILITY
        )

        // Marked as processed and in cache immediately
        assertTrue(AppStateManager.isQuestionAlreadyProcessed(question))
        Thread.sleep(150)
        assertEquals(question, AppStateManager.currentQuestion.value?.text)

        // Attempting to process identical question immediately again without forcedBypass is ignored
        val initialLogCount = AppStateManager.diagnosticLogs.value.size
        AppStateManager.onQuestionDetected(
            context = null,
            text = question,
            sourceApp = "WhatsApp",
            packageName = "com.whatsapp",
            forcedBypass = false,
            detectionMethod = DetectionMethod.ACCESSIBILITY
        )

        // Diagnostic log count shouldn't have doubled
        assertEquals(initialLogCount, AppStateManager.diagnosticLogs.value.size)
    }

    @Test
    fun testDiagnosticLogs_CappedAtFiftyEntriesToPreventMemoryLeak() {
        // Add 60 diagnostic log entries
        for (i in 1..60) {
            AppStateManager.addDiagnosticLog(
                source = "BenchmarkTest",
                rawText = "Message $i",
                result = DetectionResultType.REJECTED,
                category = "TEST_CATEGORY",
                reason = "Benchmark test entry $i"
            )
        }

        // Must be capped at 50 max to prevent memory churn and UI lag
        val logCount = AppStateManager.diagnosticLogs.value.size
        assertEquals(50, logCount)
    }

    @Test
    fun testReplyItemsHaveDistinctStableIds() {
        val question = "What is the update?"
        AppStateManager.onQuestionDetected(
            context = null,
            text = question,
            sourceApp = "Telegram",
            packageName = "org.telegram.messenger",
            forcedBypass = true
        )

        // Set mock replies
        val replies = AppStateManager.activeReplies.value
        val distinctIds = replies.map { it.id }.toSet()
        assertEquals("Each reply must have a unique stable ID for Compose key optimization", replies.size, distinctIds.size)
    }
}
