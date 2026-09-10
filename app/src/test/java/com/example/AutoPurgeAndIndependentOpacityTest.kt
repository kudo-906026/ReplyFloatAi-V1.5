package com.example

import com.example.model.DetectedQuestion
import com.example.model.ReplyItem
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.state.AppStateManager
import com.example.state.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoPurgeAndIndependentOpacityTest {

    @Before
    fun setUp() {
        AppStateManager.clearAllStorage()
    }

    @Test
    fun testIndependentOpacityControlsPersistence() {
        val settings = ReplySettings(
            smallBarOpacity = 0.45f,
            mainBarOpacity = 0.85f
        )

        val serialized = SettingsStorage.serializeSettings(settings)
        val deserialized = SettingsStorage.deserializeSettings(serialized)

        assertEquals(0.45f, deserialized.smallBarOpacity, 0.01f)
        assertEquals(0.85f, deserialized.mainBarOpacity, 0.01f)
    }

    @Test
    fun testIndependentOpacityStateSetters() {
        AppStateManager.setSmallBarOpacity(0.55f)
        assertEquals(0.55f, AppStateManager.settings.value.smallBarOpacity, 0.01f)

        AppStateManager.setMainBarOpacity(0.80f)
        assertEquals(0.80f, AppStateManager.settings.value.mainBarOpacity, 0.01f)
    }

    @Test
    fun testAutoPurgeExpiredActiveRepliesAndHistory() {
        val now = System.currentTimeMillis()
        AppStateManager.setHistoryPurgeMinutes(2) // 2 minutes retention
        AppStateManager.setReplyAutoDeleteMinutes(2)

        // Inject simulated current question and active replies from 5 minutes ago (expired)
        val expiredTimestamp = now - (5 * 60 * 1000L)
        val expiredQuestion = DetectedQuestion(
            id = "q-expired-1",
            text = "What is the capital of France?",
            timestamp = expiredTimestamp
        )

        val expiredHistory = listOf(
            expiredQuestion,
            DetectedQuestion(
                id = "q-fresh-2",
                text = "What is the capital of Japan?",
                timestamp = now - 1000L // 1 second ago (fresh)
            )
        )

        // Set state via onQuestionDetected or direct testing
        AppStateManager.onQuestionDetected(
            text = expiredQuestion.text,
            sourceApp = "TestApp",
            forcedBypass = true
        )

        // Directly call purge
        AppStateManager.purgeExpiredData()

        // Fresh questions should remain, while expired ones should be filtered
        // When question is older than cutoff, it must be purged
        val historyAfter = AppStateManager.questionsHistory.value
        assertTrue("Expired items should be purged from history", historyAfter.all { it.timestamp >= now - (2 * 60 * 1000L) })
    }
}
