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

object ClaudeProviderClient : AiProviderClient {
    private const val TAG = "ClaudeProviderClient"
    override val provider: AiProvider = AiProvider.CLAUDE
    private const val BASE_URL = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"

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
                IllegalStateException("Missing Claude API Key. Please add your key in Settings.")
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
                put("model", provider.defaultModel)
                put("max_tokens", 1024)
                val messages = JSONArray().apply {
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
                .addHeader("x-api-key", trimmedKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing Claude API request with model ${provider.defaultModel}")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Claude API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Claude error (${response.code}): $errorMsg"))
            }

            val responseJson = JSONObject(responseBody)
            val contentArr = responseJson.optJSONArray("content")
            val firstBlock = contentArr?.optJSONObject(0)
            val text = firstBlock?.optString("text") ?: ""

            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Claude returned empty response"))
            }

            val parsedResult = if (multiLanguage) {
                AiPromptHelper.parseMultiLanguageResult(text, question, count, provider)
            } else {
                val parsedReplies = AiPromptHelper.parseReplies(text, count)
                if (parsedReplies.isEmpty()) {
                    return@withContext Result.failure(Exception("Could not parse replies from Claude response"))
                }
                AiReplyResult(
                    original = question,
                    englishMeaning = null,
                    replies = parsedReplies,
                    provider = provider
                )
            }

            if (parsedResult.replies.isEmpty()) {
                return@withContext Result.failure(Exception("Could not parse replies from Claude response"))
            }

            Log.d(TAG, "Claude success with ${parsedResult.replies.size} replies")
            Result.success(parsedResult)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Claude exception", e)
            Result.failure(Exception("Claude error: ${e.localizedMessage ?: "Network connection failure"}"))
        }
    }
}
