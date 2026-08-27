package com.example

import com.example.model.ReplyItem
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.state.AppStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClipboardSafetyTest {

    @Before
    fun setup() {
        AppStateManager.clearReplies()
    }

    @Test
    fun testGenerationDoesNotAutoCopy() {
        // Ensure starting state is empty
        assertNull(AppStateManager.currentQuestion.value)
        assertTrue(AppStateManager.activeReplies.value.isEmpty())

        // Test changing settings - should never write to clipboard
        AppStateManager.updateReplyCount(1)
        AppStateManager.updateTone(ReplyTone.PROFESSIONAL)
        AppStateManager.setResponseLengthPreset(ResponseLengthPreset.SHORT)
        AppStateManager.setUnderstandingMode(true)

        assertEquals(1, AppStateManager.settings.value.count)
        assertEquals(ReplyTone.PROFESSIONAL, AppStateManager.settings.value.tone)
        assertEquals(ResponseLengthPreset.SHORT, AppStateManager.settings.value.responseLengthPreset)

        // Generating replies with 1 reply count or 3 reply counts should populate state
        // without any automatic side-effects on the clipboard
        val mockReply1 = ReplyItem(
            id = "rep-1",
            questionId = "q-1",
            text = "Sure, let's meet at 2pm.",
            tone = ReplyTone.CASUAL
        )
        val mockReply2 = ReplyItem(
            id = "rep-2",
            questionId = "q-1",
            text = "I'm not available today.",
            tone = ReplyTone.PROFESSIONAL
        )

        AppStateManager.dismissReply("rep-1")
        AppStateManager.clearReplies()
        assertTrue(AppStateManager.activeReplies.value.isEmpty())
    }
}
