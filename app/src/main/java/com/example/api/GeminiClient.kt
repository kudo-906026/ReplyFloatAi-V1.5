package com.example.api

import com.example.model.AiReplyResult
import com.example.model.GeminiReplyResult
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone

/**
 * Backwards-compatibility wrapper for GeminiClient, delegating to the unified
 * multi-provider AI engine.
 */
object GeminiClient {
    suspend fun generateReplies(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        customApiKey: String? = null,
        multiLanguage: Boolean = false
    ): Result<GeminiReplyResult> {
        return GeminiProviderClient.generateReplies(
            question = question,
            count = count,
            length = length,
            tone = tone,
            apiKey = customApiKey ?: "",
            multiLanguage = multiLanguage
        )
    }

    suspend fun generateRepliesWithSettings(
        question: String,
        settings: ReplySettings
    ): Result<AiReplyResult> {
        return AiFallbackEngine.generateRepliesWithFallback(
            question = question,
            count = settings.count,
            length = settings.length,
            tone = settings.tone,
            multiLanguage = settings.multiLanguageEnabled,
            settings = settings
        )
    }
}
