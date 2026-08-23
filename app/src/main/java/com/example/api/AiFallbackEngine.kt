package com.example.api

import android.util.Log
import com.example.model.AiProvider
import com.example.model.AiReplyResult
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import kotlinx.coroutines.CancellationException

object AiFallbackEngine {
    private const val TAG = "AiFallbackEngine"

    private val clients: Map<AiProvider, AiProviderClient> = mapOf(
        AiProvider.GEMINI to GeminiProviderClient,
        AiProvider.OPENAI to OpenAiProviderClient,
        AiProvider.CLAUDE to ClaudeProviderClient,
        AiProvider.GROQ to GroqProviderClient
    )

    fun getClient(provider: AiProvider): AiProviderClient {
        return clients[provider] ?: GeminiProviderClient
    }

    /**
     * Executes the configured AI provider fallback chain.
     * Tries providers in order of settings.providerChain.
     * If a provider fails (429 quota, bad key, network error), automatically falls through to the next provider.
     * If all providers in chain fail or have no key, returns error message:
     * "All providers unavailable — check your API keys in Settings"
     */
    suspend fun generateRepliesWithFallback(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        multiLanguage: Boolean,
        settings: ReplySettings
    ): Result<AiReplyResult> {
        val chain = settings.providerChain.ifEmpty {
            listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.CLAUDE, AiProvider.GROQ)
        }

        var attemptedCount = 0
        val failureLogs = mutableListOf<String>()

        for (provider in chain) {
            val client = clients[provider] ?: continue
            val rawKey = settings.getApiKeyFor(provider)

            // For Gemini, also check if BuildConfig key is available as fallback
            val hasUsableKey = if (provider == AiProvider.GEMINI) {
                rawKey.isNotBlank() || (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
            } else {
                rawKey.isNotBlank()
            }

            if (!hasUsableKey) {
                Log.d(TAG, "Skipping ${provider.displayName}: No API key configured in Settings")
                continue
            }

            attemptedCount++
            Log.d(TAG, "Attempting provider [${provider.displayName}] (chain index ${chain.indexOf(provider) + 1}/${chain.size})...")

            try {
                val result = client.generateReplies(
                    question = question,
                    count = count,
                    length = length,
                    tone = tone,
                    apiKey = rawKey,
                    multiLanguage = multiLanguage
                )

                if (result.isSuccess) {
                    val replyResult = result.getOrNull()
                    if (replyResult != null && replyResult.replies.isNotEmpty()) {
                        Log.i(TAG, "Successfully generated replies via [${provider.displayName}]")
                        return Result.success(replyResult.copy(provider = provider))
                    }
                }

                // If failed, record and continue to next provider in fallback chain
                val exception = result.exceptionOrNull()
                val errorMsg = exception?.message ?: "Unknown error"
                Log.w(TAG, "Provider [${provider.displayName}] failed: $errorMsg. Falling back to next provider in chain...")
                failureLogs.add("${provider.displayName}: $errorMsg")
            } catch (e: CancellationException) {
                // Do not catch coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Provider [${provider.displayName}] exception: ${e.localizedMessage}. Falling back to next provider...")
                failureLogs.add("${provider.displayName}: ${e.localizedMessage}")
            }
        }

        // If we reach here, all attempted providers failed or no provider had an API key configured
        val finalErrorMessage = "All providers unavailable — check your API keys in Settings"
        Log.e(TAG, "Fallback chain exhausted. Attempted: $attemptedCount provider(s). Errors: $failureLogs")
        return Result.failure(Exception(finalErrorMessage))
    }
}
