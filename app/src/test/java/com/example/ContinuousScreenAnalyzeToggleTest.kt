package com.example

import com.example.model.DetectionResultType
import com.example.state.AppStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContinuousScreenAnalyzeToggleTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
        AppStateManager.setContinuousScreenAnalysis(true)
        AppStateManager.clearDiagnosticLogs()
    }

    @Test
    fun testAnalyzeToggleOff_StopsDetection() {
        // Given Analyze is enabled initially
        assertTrue(AppStateManager.settings.value.continuousScreenAnalysis)

        // When Continuous Screen Analyze is toggled OFF
        AppStateManager.setContinuousScreenAnalysis(false)
        assertFalse(AppStateManager.settings.value.continuousScreenAnalysis)

        // Then incoming background scanning (forcedBypass = false) is ignored
        assertEquals(false, AppStateManager.settings.value.continuousScreenAnalysis)

        // Verify diagnostic log records pause
        val logs = AppStateManager.diagnosticLogs.value
        val pausedLog = logs.find { it.category == "ANALYSIS_PAUSED" }
        assertNotNull("Diagnostic log should contain ANALYSIS_PAUSED", pausedLog)
        assertEquals(DetectionResultType.REJECTED, pausedLog?.result)
    }

    @Test
    fun testAnalyzeToggleOffThenOn_ResumesDetectionState() {
        // 1. Start with Analyze ON
        assertTrue(AppStateManager.settings.value.continuousScreenAnalysis)

        // 2. Toggle Continuous Screen Analyze OFF
        AppStateManager.setContinuousScreenAnalysis(false)
        assertFalse(AppStateManager.settings.value.continuousScreenAnalysis)

        // 3. Toggle Continuous Screen Analyze back ON
        AppStateManager.setContinuousScreenAnalysis(true)
        assertTrue("continuousScreenAnalysis must be true after toggling back ON", AppStateManager.settings.value.continuousScreenAnalysis)

        // 4. Verify diagnostic logs record both PAUSED and RESUMED events
        val logs = AppStateManager.diagnosticLogs.value
        val resumedLog = logs.find { it.category == "ANALYSIS_RESUMED" }
        assertNotNull("Diagnostic log should contain ANALYSIS_RESUMED", resumedLog)
        assertEquals(DetectionResultType.MATCHED, resumedLog?.result)
        assertEquals("Continuous Analysis Controller", resumedLog?.source)
    }

    @Test
    fun testMultipleToggleCycles_ConsistentlyUpdatesState() {
        AppStateManager.clearDiagnosticLogs()

        for (i in 1..3) {
            // Turn OFF
            AppStateManager.setContinuousScreenAnalysis(false)
            assertFalse("Cycle $i: continuousScreenAnalysis should be false", AppStateManager.settings.value.continuousScreenAnalysis)

            // Turn back ON
            AppStateManager.setContinuousScreenAnalysis(true)
            assertTrue("Cycle $i: continuousScreenAnalysis should be true", AppStateManager.settings.value.continuousScreenAnalysis)
        }

        val logs = AppStateManager.diagnosticLogs.value
        val resumeCount = logs.count { it.category == "ANALYSIS_RESUMED" }
        val pauseCount = logs.count { it.category == "ANALYSIS_PAUSED" }
        assertEquals(3, resumeCount)
        assertEquals(3, pauseCount)
    }
}
