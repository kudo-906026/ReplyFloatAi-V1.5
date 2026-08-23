package com.example.api

import android.util.Log
import com.example.model.AiProvider
import com.example.model.AiReplyResult
import com.example.model.ReplyLength
import com.example.model.ReplyTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GrokProviderClient : AiProviderClient {
    private const val TAG = "GrokProviderClient"
    override val provider: AiProvider = AiProvider.GROK
    private const val BASE_URL = "https://api.x.ai/v1/chat/completions"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateReplies(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        apiKey: String,
        multiLanguage: Boolean
    ): Result<AiReplyResult> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Missing Grok (xAI) API Key. Please add your key in Settings.")
            )
        }

        val prompt = AiPromptHelper.buildPrompt(
            question = question,
            count = count,
            length = length,
            tone = tone,
            multiLanguage = multiLanguage
        )

        try {
            val jsonBody = JSONObject().apply {
                put("model", provider.defaultModel) // grok-2-latest
                put("temperature", 0.7)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an intelligent instant-reply AI assistant for Android powered by Grok.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $trimmedKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing Grok (xAI) API request with model ${provider.defaultModel}")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val errObj = errJson.optJSONObject("error")
                    errObj?.optString("message") ?: errJson.optString("message", "HTTP ${response.code}: $responseBody")
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Grok API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Grok error (${response.code}): $errorMsg"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val text = message?.optString("content") ?: ""

            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Grok returned empty response"))
            }

            val parsedResult = if (multiLanguage) {
                AiPromptHelper.parseMultiLanguageResult(text, question, count, provider)
            } else {
                val parsedReplies = AiPromptHelper.parseReplies(text, count)
                if (parsedReplies.isEmpty()) {
                    return@withContext Result.failure(Exception("Could not parse replies from Grok response"))
                }
                AiReplyResult(
                    original = question,
                    englishMeaning = null,
                    replies = parsedReplies,
                    provider = provider
                )
            }

            if (parsedResult.replies.isEmpty()) {
                return@withContext Result.failure(Exception("Could not parse replies from Grok response"))
            }

            Log.d(TAG, "Grok success with ${parsedResult.replies.size} replies")
            Result.success(parsedResult)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Grok exception", e)
            Result.failure(Exception("Grok error: ${e.localizedMessage ?: "Network connection failure"}"))
        }
    }
}
