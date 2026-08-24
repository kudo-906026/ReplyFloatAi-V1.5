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
            val callStartTime = System.currentTimeMillis()
            val callId = com.example.state.AppStateManager.recordApiCallStart(
                provider = provider,
                model = provider.defaultModel,
                question = question
            )

            Log.i(
                "API_CALL_AUDIT",
                ">>> [API CALL #${com.example.state.AppStateManager.totalApiCallsCount.value}] Provider: ${provider.displayName} | Model: ${provider.defaultModel} | Time: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(callStartTime))} | Question: \"$question\""
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

                val duration = System.currentTimeMillis() - callStartTime

                if (result.isSuccess) {
                    val replyResult = result.getOrNull()
                    if (replyResult != null && replyResult.replies.isNotEmpty()) {
                        Log.i(
                            "API_CALL_AUDIT",
                            "<<< [API CALL SUCCESS] ${provider.displayName} returned ${replyResult.replies.size} replies in ${duration}ms. Stopping chain immediately."
                        )
                        com.example.state.AppStateManager.recordApiCallSuccess(callId, duration, replyResult.replies.size)
                        com.example.state.AppStateManager.recordProviderSuccess(provider)
                        return Result.success(replyResult.copy(provider = provider))
                    } else {
                        val emptyMsg = "Provider returned empty response list"
                        Log.w("API_CALL_AUDIT", "<<< [API CALL EMPTY] Provider [${provider.displayName}] in ${duration}ms: $emptyMsg")
                        com.example.state.AppStateManager.recordApiCallFailure(callId, duration, emptyMsg)
                        com.example.state.AppStateManager.recordProviderError(
                            provider = provider,
                            plainReason = "Received empty response from AI model",
                            technicalDetails = "Response payload did not contain parseable replies",
                            suggestedFix = "Try changing the Reply Length or Tone in Settings."
                        )
                        failureLogs.add("${provider.displayName}: $emptyMsg")
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: "Unknown API failure"
                    val (plainReason, fix) = deriveDiagnosticReasonAndFix(provider, errorMsg)
                    Log.w("API_CALL_AUDIT", "<<< [API CALL FAILED] Provider [${provider.displayName}] in ${duration}ms: $errorMsg")
                    com.example.state.AppStateManager.recordApiCallFailure(callId, duration, errorMsg)
                    com.example.state.AppStateManager.recordProviderError(
                        provider = provider,
                        plainReason = plainReason,
                        technicalDetails = errorMsg,
                        suggestedFix = fix
                    )
                    failureLogs.add("${provider.displayName}: $errorMsg")
                }
            } catch (e: CancellationException) {
                val duration = System.currentTimeMillis() - callStartTime
                com.example.state.AppStateManager.recordApiCallFailure(callId, duration, "Cancelled")
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - callStartTime
                val exMsg = e.localizedMessage ?: e.message ?: "Connection error"
                val (plainReason, fix) = deriveDiagnosticReasonAndFix(provider, exMsg)
                Log.e("API_CALL_AUDIT", "<<< [API CALL EXCEPTION] Provider [${provider.displayName}] in ${duration}ms: $exMsg", e)
                com.example.state.AppStateManager.recordApiCallFailure(callId, duration, exMsg)
                com.example.state.AppStateManager.recordProviderError(
                    provider = provider,
                    plainReason = plainReason,
                    technicalDetails = exMsg,
                    suggestedFix = fix
                )
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

    private fun deriveDiagnosticReasonAndFix(provider: AiProvider, rawError: String): Pair<String, String> {
        val lower = rawError.lowercase()
        return when {
            lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key") || lower.contains("api_key_invalid") || lower.contains("authentication") -> {
                "401 Unauthorized — Invalid API key" to "Check that your ${provider.displayName} API key is entered correctly in Settings and is active."
            }
            lower.contains("429") || lower.contains("quota") || lower.contains("rate limit") || lower.contains("resource_exhausted") || lower.contains("rate_limit") -> {
                "429 Too Many Requests — Quota/Rate limit exceeded" to "API quota exhausted on ${provider.displayName}. Auto-fallback will try remaining providers, or check your billing plan."
            }
            lower.contains("403") || lower.contains("permission_denied") || lower.contains("forbidden") -> {
                "403 Forbidden — Permission denied" to "Verify your ${provider.displayName} API key has access permissions for model ${provider.defaultModel}."
            }
            lower.contains("404") || lower.contains("not found") -> {
                "404 Not Found — Model endpoint unavailable" to "Model ${provider.defaultModel} was not found or is unsupported on your account tier."
            }
            lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("server error") -> {
                "503 Service Unavailable — Provider server error" to "The ${provider.displayName} service is temporarily experiencing outages. ReplyFloat will fall back automatically."
            }
            lower.contains("timeout") || lower.contains("connect") || lower.contains("unknownhost") || lower.contains("socket") -> {
                "Network timeout / Connection failure" to "Check device internet connection and verify ${provider.displayName} endpoints are reachable."
            }
            lower.contains("json") || lower.contains("parse") -> {
                "Invalid response format" to "Model returned an unexpected response structure. Retrying or switching tone may resolve this."
            }
            else -> {
                "API call failure ($rawError)" to "Review your ${provider.displayName} configuration in Settings."
            }
        }
    }
}

