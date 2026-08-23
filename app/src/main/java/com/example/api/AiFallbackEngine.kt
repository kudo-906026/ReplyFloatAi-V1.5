package com.example.api

import android.util.Log
import com.example.model.AiProvider
import com.example.model.AiReplyResult
import com.example.model.ProviderSelectionMode
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
        AiProvider.GROK to GrokProviderClient
    )

    fun getClient(provider: AiProvider): AiProviderClient {
        return clients[provider] ?: GeminiProviderClient
    }

    /**
     * Executes reply generation respecting the user's configured provider selection mode.
     *
     * In PREFERRED_PROVIDER mode:
     * - First attempts the user-selected preferred provider.
     * - If that provider fails or is unconfigured, falls back to the remaining providers in chain order.
     *
     * In AUTO_FALLBACK mode:
     * - Sequentially tries providers in the fallback chain order.
     * - Automatically falls through to the next provider on error or rate limit.
     *
     * Detailed errors from each provider attempt are captured and logged to aid debugging.
     */
    suspend fun generateRepliesWithFallback(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        multiLanguage: Boolean,
        settings: ReplySettings
    ): Result<AiReplyResult> {
        // Determine the execution order based on selection mode
        val baseChain = settings.providerChain.ifEmpty {
            listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.CLAUDE, AiProvider.GROK)
        }

        val executionChain = when (settings.selectionMode) {
            ProviderSelectionMode.PREFERRED_PROVIDER -> {
                listOf(settings.preferredProvider) + baseChain.filter { it != settings.preferredProvider }
            }
            ProviderSelectionMode.AUTO_FALLBACK -> {
                baseChain
            }
        }

        Log.d(TAG, "Starting reply generation in [${settings.selectionMode.label}] mode. Target chain: ${executionChain.map { it.displayName }}")

        var attemptedCount = 0
        val failureLogs = mutableListOf<String>()
        val unconfiguredProviders = mutableListOf<String>()

        for (provider in executionChain) {
            val client = clients[provider]
            if (client == null) {
                Log.w(TAG, "No client registered for provider: ${provider.displayName}")
                continue
            }

            val rawKey = settings.getApiKeyFor(provider)

            // For Gemini, also check if BuildConfig key is available as fallback
            val hasUsableKey = if (provider == AiProvider.GEMINI) {
                rawKey.isNotBlank() || (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
            } else {
                rawKey.isNotBlank()
            }

            if (!hasUsableKey) {
                Log.d(TAG, "Skipping ${provider.displayName}: No API key configured in Settings")
                unconfiguredProviders.add(provider.displayName)
                continue
            }

            attemptedCount++
            val isPreferred = settings.selectionMode == ProviderSelectionMode.PREFERRED_PROVIDER && provider == settings.preferredProvider
            Log.d(
                TAG,
                "Calling provider [${provider.displayName}] (model=${provider.defaultModel}, preferred=$isPreferred, attempt #$attemptedCount)..."
            )

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
                        Log.i(TAG, "Successfully generated ${replyResult.replies.size} replies via [${provider.displayName}]")
                        return Result.success(replyResult.copy(provider = provider))
                    } else {
                        val emptyMsg = "Provider returned empty response list"
                        Log.w(TAG, "Provider [${provider.displayName}] issue: $emptyMsg")
                        failureLogs.add("${provider.displayName}: $emptyMsg")
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: "Unknown API failure"
                    Log.w(TAG, "Provider [${provider.displayName}] failed: $errorMsg")
                    failureLogs.add("${provider.displayName}: $errorMsg")
                }
            } catch (e: CancellationException) {
                // Do not intercept coroutine cancellation
                throw e
            } catch (e: Exception) {
                val exMsg = e.localizedMessage ?: e.message ?: "Connection error"
                Log.e(TAG, "Provider [${provider.displayName}] uncaught exception: $exMsg", e)
                failureLogs.add("${provider.displayName}: $exMsg")
            }
        }

        // If we reach here, all attempted providers failed or no keys were configured
        val finalErrorMessage = if (attemptedCount == 0) {
            "No API keys configured. Please add an API key for Gemini, OpenAI, Claude, or Grok in Settings."
        } else {
            buildString {
                appendLine("All attempted providers failed:")
                failureLogs.forEach { log ->
                    appendLine("• $log")
                }
                if (unconfiguredProviders.isNotEmpty()) {
                    append("Unconfigured: ${unconfiguredProviders.joinToString(", ")}")
                }
            }.trim()
        }

        Log.e(TAG, "Fallback chain exhausted. Attempted: $attemptedCount provider(s). Final summary:\n$finalErrorMessage")
        return Result.failure(Exception(finalErrorMessage))
    }
}
