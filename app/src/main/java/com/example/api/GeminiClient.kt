package com.example.api

import android.util.Log
import com.example.BuildConfig
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

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateReplies(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        customApiKey: String? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No valid Gemini API key configured.")
            return@withContext Result.failure(
                IllegalStateException("Missing Gemini API Key. Please add your API key in Settings tab.")
            )
        }

        val prompt = buildString {
            appendLine("You are an intelligent instant-reply AI assistant for Android.")
            appendLine("The user was asked the following question on screen:")
            appendLine("\"$question\"")
            appendLine()
            appendLine("Generate exactly $count distinct, ready-to-send reply options.")
            appendLine("STRICT LENGTH CONSTRAINT: ${length.promptInstruction}")
            appendLine("TONE CONSTRAINT: ${tone.promptInstruction}")
            appendLine()
            appendLine("RULES:")
            appendLine("1. Return ONLY the reply options.")
            appendLine("2. Prefix each individual reply option with '>>> ' at the start of the line.")
            appendLine("3. Do NOT include markdown headers, quotes around the replies, numbered lists (like 1., 2.), or explanations.")
            appendLine("4. Each reply should be ready to paste directly into a chat or message.")
        }

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
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing Gemini API request for question: \"$question\" (count=$count, length=${length.label})")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Gemini API error (code ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Gemini error (${response.code}): $errorMsg"))
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isBlank()) {
                Log.w(TAG, "Gemini returned empty text response")
                return@withContext Result.failure(Exception("No replies generated by Gemini"))
            }

            val parsedReplies = parseReplies(text, count)
            if (parsedReplies.isEmpty()) {
                return@withContext Result.failure(Exception("Could not parse replies from Gemini response"))
            }

            Log.d(TAG, "Successfully received and parsed ${parsedReplies.size} replies from Gemini")
            Result.success(parsedReplies)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Gemini API request was cancelled by new question")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API", e)
            Result.failure(Exception(e.localizedMessage ?: "Network or connection error"))
        }
    }

    private fun parseReplies(
        rawText: String,
        targetCount: Int
    ): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()

        var currentBuffer = StringBuilder()

        for (line in lines) {
            if (line.startsWith(">>>")) {
                if (currentBuffer.isNotBlank()) {
                    result.add(cleanReply(currentBuffer.toString()))
                    currentBuffer = StringBuilder()
                }
                val cleaned = line.removePrefix(">>>").trim()
                if (cleaned.isNotBlank()) {
                    currentBuffer.append(cleaned)
                }
            } else if (line.matches(Regex("^(\\d+\\.|[-*•])\\s+.*"))) {
                if (currentBuffer.isNotBlank()) {
                    result.add(cleanReply(currentBuffer.toString()))
                    currentBuffer = StringBuilder()
                }
                val cleaned = line.replace(Regex("^(\\d+\\.|[-*•])\\s+"), "").trim()
                if (cleaned.isNotBlank()) {
                    currentBuffer.append(cleaned)
                }
            } else {
                if (currentBuffer.isNotEmpty()) {
                    currentBuffer.append(" ").append(line)
                } else {
                    currentBuffer.append(line)
                }
            }
        }

        if (currentBuffer.isNotBlank()) {
            result.add(cleanReply(currentBuffer.toString()))
        }

        return result
            .map { cleanReply(it) }
            .filter { it.isNotBlank() && !it.startsWith("Here are") && !it.startsWith("Sure, here") }
            .take(targetCount)
    }

    private fun cleanReply(text: String): String {
        return text
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .removePrefix("Option 1:").removePrefix("Option 2:").removePrefix("Option 3:")
            .trim()
    }

    private fun generateFallbackReplies(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone
    ): List<String> {
        val qLower = question.lowercase()
        val isTime = qLower.contains("when") || qLower.contains("time") || qLower.contains("tomorrow") || qLower.contains("today")
        val isMeeting = qLower.contains("meet") || qLower.contains("call") || qLower.contains("free") || qLower.contains("available")
        val isStatus = qLower.contains("how are") || qLower.contains("doing") || qLower.contains("status") || qLower.contains("update")
        val isChoice = qLower.contains("or ") || qLower.contains("which")

        val options = when (length) {
            ReplyLength.ONE_WORD -> when {
                isMeeting || isTime -> listOf("Available", "Sure", "Confirmed", "Tomorrow", "Declined")
                isStatus -> listOf("Good", "Great", "Progressing", "Pending", "Done")
                isChoice -> listOf("First", "Second", "Either", "Both", "Neither")
                else -> listOf("Yes", "No", "Definitely", "Confirmed", "Understood")
            }
            ReplyLength.SHORT -> when {
                isMeeting -> listOf("I'm free then!", "Sounds good to me.", "Let's do tomorrow.", "Can we reschedule?")
                isTime -> listOf("Around 3 PM works.", "In about 20 mins.", "First thing tomorrow.", "Sometime this afternoon.")
                isStatus -> listOf("Going really well!", "Almost finished now.", "Still working on it.", "All set and ready.")
                else -> listOf("Sounds great!", "Sure, count me in.", "I'll check and update you.", "Thanks for asking!")
            }
            ReplyLength.ONE_LINE -> when {
                isMeeting -> listOf(
                    "Yes, that time works perfectly for me to meet.",
                    "I have a quick conflict then, but I can do 30 minutes later.",
                    "Sounds great, I'll send over the invite link right away."
                )
                isTime -> listOf(
                    "I should be ready by early afternoon tomorrow.",
                    "Let's aim for 4:00 PM if that suits your schedule.",
                    "I'll have the final details ready by end of day."
                )
                isStatus -> listOf(
                    "Everything is on track and running smoothly on my end.",
                    "Just wrapping up the final review and will share updates shortly.",
                    "Making solid progress, expect the full summary in an hour."
                )
                else -> listOf(
                    "Thanks for reaching out! I'd be happy to help with that.",
                    "Yes, absolutely. Let me know what other details you need.",
                    "I'll take care of this and follow up with you as soon as possible."
                )
            }
            ReplyLength.TWO_LINES -> when {
                isMeeting -> listOf(
                    "Yes, that schedule works great for me.\nI'll add it to my calendar and see you then!",
                    "I won't be able to make that exact time.\nCould we push it by an hour or connect tomorrow morning?",
                    "Thanks for asking! I'm completely free then and looking forward to catching up."
                )
                else -> listOf(
                    "I received your message and am looking into it right now.\nI'll share the complete answer within a few minutes.",
                    "Yes, that sounds like the right approach.\nLet's proceed with this plan and keep everyone in the loop.",
                    "Thank you for the update. I appreciate you letting me know and will take action right away."
                )
            }
            ReplyLength.FIVE_TO_SEVEN_LINES -> listOf(
                "Thank you for reaching out regarding this question.\nI have reviewed the details and everything looks well-aligned.\nWe can proceed according to the timeline we previously discussed.\nPlease let me know if you need any additional supporting material.\nI'll remain available if any other questions pop up.\nLooking forward to speaking soon!",
                "Thanks for checking in on this!\nI'm currently reviewing all related items to ensure nothing gets missed.\nInitial progress has been very positive with no major blockers so far.\nI will compile the full breakdown and share it with you by end of day.\nFeel free to ping me if anything urgent comes up in the meantime.",
                "Yes, absolutely! That direction makes complete sense for our goal.\nI've already started preparing the necessary steps on my side.\nWe should see the first batch of results within the next day or two.\nLet me know if there are specific priority items you'd like me to focus on first.\nHave a wonderful rest of your day!"
            )
        }

        return options.shuffled().take(count.coerceIn(1, 3))
    }
}
