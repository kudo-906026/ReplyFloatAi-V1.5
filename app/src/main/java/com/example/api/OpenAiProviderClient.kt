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

object OpenAiProviderClient : AiProviderClient {
    private const val TAG = "OpenAiProviderClient"
    override val provider: AiProvider = AiProvider.OPENAI
    private const val BASE_URL = "https://api.openai.com/v1/chat/completions"

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
                IllegalStateException("Missing OpenAI API Key. Please add your key in Settings.")
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
                put("temperature", 0.7)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an intelligent instant-reply AI assistant for Android.")
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
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing OpenAI API request with model ${provider.defaultModel}")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "OpenAI API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("OpenAI error (${response.code}): $errorMsg"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val text = message?.optString("content") ?: ""

            if (text.isBlank()) {
                return@withContext Result.failure(Exception("OpenAI returned empty response"))
            }

            val parsedResult = if (multiLanguage) {
                AiPromptHelper.parseMultiLanguageResult(text, question, count, provider)
            } else {
                val parsedReplies = AiPromptHelper.parseReplies(text, count)
                if (parsedReplies.isEmpty()) {
                    return@withContext Result.failure(Exception("Could not parse replies from OpenAI response"))
                }
                AiReplyResult(
                    original = question,
                    englishMeaning = null,
                    replies = parsedReplies,
                    provider = provider
                )
            }

            if (parsedResult.replies.isEmpty()) {
                return@withContext Result.failure(Exception("Could not parse replies from OpenAI response"))
            }

            Log.d(TAG, "OpenAI success with ${parsedResult.replies.size} replies")
            Result.success(parsedResult)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI exception", e)
            Result.failure(Exception("OpenAI error: ${e.localizedMessage ?: "Network connection failure"}"))
        }
    }
}
