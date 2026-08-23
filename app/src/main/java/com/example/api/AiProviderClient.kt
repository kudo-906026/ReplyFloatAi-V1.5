package com.example.api

import com.example.model.AiProvider
import com.example.model.AiReplyResult
import com.example.model.ReplyLength
import com.example.model.ReplyTone

/**
 * Shared interface for AI providers in the automatic fallback chain.
 * Decouples provider-specific request serialization and response parsing
 * from the fallback orchestration engine.
 */
interface AiProviderClient {
    val provider: AiProvider

    suspend fun generateReplies(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        apiKey: String,
        multiLanguage: Boolean
    ): Result<AiReplyResult>
}
