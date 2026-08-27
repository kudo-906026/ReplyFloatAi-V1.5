package com.example

import com.example.ai.AiFallbackEngine
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFallbackEngineTest {

    @Test
    fun testTelephoneInventionQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val result = AiFallbackEngine.generateRepliesWithFallback("Who invented the telephone?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val firstReply = result.replies.first().text
        assertTrue(
            "Expected reply to contain Alexander Graham Bell, but was: $firstReply",
            firstReply.contains("Alexander Graham Bell") || firstReply.contains("Bell")
        )
    }

    @Test
    fun testImaginaryUnitMathAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CONCISE)
        val result = AiFallbackEngine.generateRepliesWithFallback("What is the value of i²?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val firstReply = result.replies.first().text
        assertTrue(
            "Expected reply to equal -1, but was: $firstReply",
            firstReply.contains("-1")
        )
    }

    @Test
    fun testHistoricalFigureHypotheticalQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val question = "If you could have dinner with any historical figure, who would it be and why?"
        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val replyText = result.replies.first().text
        assertTrue(
            "Expected reply to mention historical figures, but was: $replyText",
            replyText.contains("da Vinci") || replyText.contains("Einstein") || replyText.contains("Tesla") || replyText.contains("Socrates")
        )
    }

    @Test
    fun testCapitalQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CONCISE)
        val result = AiFallbackEngine.generateRepliesWithFallback("What is the capital of Australia?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val replyText = result.replies.first().text
        assertTrue(
            "Expected reply to contain Canberra, but was: $replyText",
            replyText.contains("Canberra")
        )
    }
}
