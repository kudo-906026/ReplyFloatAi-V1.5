package com.example.api

import android.util.Log
import com.example.BuildConfig
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

object GeminiProviderClient : AiProviderClient {
    private const val TAG = "GeminiProviderClient"
    override val provider: AiProvider = AiProvider.GEMINI
    private const val MODEL = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

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
        val resolvedKey = when {
            apiKey.isNotBlank() -> apiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }

        if (resolvedKey.isBlank() || resolvedKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No valid Gemini API key configured.")
            return@withContext Result.failure(
                IllegalStateException("Missing Gemini API Key. Please add your key in Settings.")
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
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("topK", 40)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$resolvedKey")
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing Gemini API request: count=$count, length=${length.label}, multiLang=$multiLanguage")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Gemini API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Gemini error (${response.code}): $errorMsg"))
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Gemini returned empty text response"))
            }

            val parsedResult = if (multiLanguage) {
                AiPromptHelper.parseMultiLanguageResult(text, question, count, provider)
            } else {
                val parsedReplies = AiPromptHelper.parseReplies(text, count)
                if (parsedReplies.isEmpty()) {
                    return@withContext Result.failure(Exception("Could not parse replies from Gemini response"))
                }
                AiReplyResult(
                    original = question,
                    englishMeaning = null,
                    replies = parsedReplies,
                    provider = provider
                )
            }

            if (parsedResult.replies.isEmpty()) {
                return@withContext Result.failure(Exception("Could not parse replies from Gemini response"))
            }

            Log.d(TAG, "Gemini success with ${parsedResult.replies.size} replies")
            Result.success(parsedResult)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini exception", e)
            Result.failure(Exception("Gemini error: ${e.localizedMessage ?: "Network connection failure"}"))
        }
    }
}
