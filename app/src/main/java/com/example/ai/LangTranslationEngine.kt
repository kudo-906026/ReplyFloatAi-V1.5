package com.example.ai

import com.example.model.AiProvider
import com.example.model.AiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LangTranslationResult(
    val original: String,
    val englishMeaning: String,
    val detectedLanguage: String = "Hinglish / Multilingual",
    val isSuccessful: Boolean = true
)

object LangTranslationEngine {

    const val FALLBACK_UNAVAILABLE = "Translation unavailable"

    // Blacklist phrases from system prompts, instruction templates, reasoning tags, or raw JSON syntax
    private val FORBIDDEN_INSTRUCTION_PATTERNS = listOf(
        "you are an",
        "multilingual assistant",
        "quick reply assistant",
        "critical:",
        "system instruction",
        "incoming message",
        "incoming question",
        "output only",
        "json object",
        "json array",
        "curly braces",
        "direct_answer_instruction",
        "answer specifically",
        "response length preset",
        "character ceiling",
        "tone:",
        "as an ai",
        "here is the translation",
        "<think>",
        "</think>",
        "```json",
        "```",
        "\"original\":",
        "\"meaning\":",
        "\"replies\":"
    )

    /**
     * Checks if a candidate string contains forbidden prompt instructions, reasoning tokens, or JSON syntax.
     */
    fun containsInstructionOrPromptLeak(text: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase().trim()
        if (lower.startsWith("{") || lower.startsWith("[") || lower.endsWith("}") || lower.endsWith("]")) {
            return true
        }
        return FORBIDDEN_INSTRUCTION_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Parse and strictly validate AI output into distinct original and meaning fields.
     * Guarantees that raw model output, prompt instructions, or unparsed JSON are NEVER returned.
     * If validation fails, falls back cleanly to offline dictionary or "Translation unavailable".
     */
    fun parseAndValidateTranslation(
        rawResponse: String?,
        fallbackOriginal: String
    ): LangTranslationResult {
        val cleanFallbackOriginal = fallbackOriginal.trim()

        if (rawResponse.isNullOrBlank()) {
            return fallbackOrUnavailable(cleanFallbackOriginal)
        }

        // 1. Strip reasoning blocks (<think>...</think>) from reasoning models
        var sanitized = rawResponse
        if (sanitized.contains("</think>")) {
            val after = sanitized.substringAfter("</think>").trim()
            sanitized = if (after.isNotBlank()) after else sanitized.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        } else if (sanitized.contains("<think>")) {
            sanitized = sanitized.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        }

        // Strip markdown code fences
        sanitized = sanitized.replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        // 2. Locate JSON object boundary { ... }
        val startIndex = sanitized.indexOf('{')
        val endIndex = sanitized.lastIndexOf('}')

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            try {
                val jsonString = sanitized.substring(startIndex, endIndex + 1)
                val jsonObj = JSONObject(jsonString)

                // Extract fields strictly
                var parsedOriginal = jsonObj.optString("original", "").trim()
                if (parsedOriginal.isBlank() || parsedOriginal == "null" || containsInstructionOrPromptLeak(parsedOriginal)) {
                    // Always rely on the authentic source text captured from the device screen
                    parsedOriginal = cleanFallbackOriginal
                }

                var parsedMeaning = jsonObj.optString("meaning", "").trim()
                if (parsedMeaning.isBlank() || parsedMeaning == "null") {
                    parsedMeaning = jsonObj.optString("translation", "").trim()
                }
                if (parsedMeaning.isBlank() || parsedMeaning == "null") {
                    parsedMeaning = jsonObj.optString("english", "").trim()
                }

                val detectedLang = jsonObj.optString("detected_language", "Hinglish / Multilingual")
                    .takeIf { it.isNotBlank() && it != "null" && !containsInstructionOrPromptLeak(it) }
                    ?: "Hinglish / Multilingual"

                // Strict validation: meaning must not contain prompt instructions, raw JSON, or reasoning leaks
                if (parsedMeaning.isNotBlank() && !containsInstructionOrPromptLeak(parsedMeaning)) {
                    return LangTranslationResult(
                        original = parsedOriginal,
                        englishMeaning = parsedMeaning,
                        detectedLanguage = detectedLang,
                        isSuccessful = true
                    )
                }
            } catch (_: Exception) {
                // JSON parsing failed, fall through to fallback
            }
        }

        // 3. If AI JSON parsing failed or was invalid, use offline dictionary or safe fallback
        return fallbackOrUnavailable(cleanFallbackOriginal)
    }

    /**
     * Offline smart dictionary for common everyday Hinglish and South Asian conversational queries.
     * If no dictionary match is found, returns FALLBACK_UNAVAILABLE ("Translation unavailable").
     */
    fun fallbackOrUnavailable(question: String): LangTranslationResult {
        val clean = question.trim()
        val lower = clean.lowercase()

        val dictionaryTranslation = lookupOfflineDictionary(lower)
        return if (dictionaryTranslation != null) {
            LangTranslationResult(
                original = clean,
                englishMeaning = dictionaryTranslation.first,
                detectedLanguage = dictionaryTranslation.second,
                isSuccessful = true
            )
        } else {
            LangTranslationResult(
                original = clean,
                englishMeaning = FALLBACK_UNAVAILABLE,
                detectedLanguage = "Unknown",
                isSuccessful = false
            )
        }
    }

    private fun lookupOfflineDictionary(lower: String): Pair<String, String>? {
        // High-confidence conversational mappings
        return when {
            (lower.contains("bhai") || lower.contains("yaar")) && (lower.contains("plan") || lower.contains("kaisa") || lower.contains("kaisi")) -> {
                "Brother, how did you like my plan?" to "Hinglish"
            }
            (lower.contains("vote") || lower.contains("voting")) && (lower.contains("karo") || lower.contains("kya") || lower.contains("isko") || lower.contains("du")) -> {
                "Should I vote for this / whom to vote for?" to "Hinglish"
            }
            lower.contains("kya chal raha") || lower.contains("kya chal rha") || lower.contains("kya haal") -> {
                "What's going on? / How are things?" to "Hinglish"
            }
            lower.contains("kaha ho") || lower.contains("kahan ho") || lower.contains("kidhar ho") -> {
                "Where are you right now?" to "Hinglish"
            }
            lower.contains("kab aaoge") || lower.contains("kab aarahe") || lower.contains("kab pahuchoge") || lower.contains("kab aoge") -> {
                "When will you arrive / come?" to "Hinglish"
            }
            lower.contains("khana khaya") || lower.contains("khana kha liya") || lower.contains("khana ho gaya") -> {
                "Did you have food / have you eaten?" to "Hinglish"
            }
            lower.contains("kaisa hai") || lower.contains("kaisi ho") || lower.contains("kaise ho") || lower.contains("kaisa h") -> {
                "How are you doing?" to "Hinglish"
            }
            lower.contains("kya hua") || lower.contains("kya ho gaya") || lower.contains("kya dikkat") -> {
                "What happened? / Is everything alright?" to "Hinglish"
            }
            lower.contains("kitna time") || lower.contains("kitni der") || lower.contains("kitna waqt") -> {
                "How much time will it take?" to "Hinglish"
            }
            lower.contains("kya kar rahe") || lower.contains("kya kar rha") || lower.contains("kya kr rhe") -> {
                "What are you doing?" to "Hinglish"
            }
            lower.contains("sab theek") || lower.contains("sab badhiya") -> {
                "Is everything fine / all good?" to "Hinglish"
            }
            lower.contains("paise") && (lower.contains("kab") || lower.contains("bhejo") || lower.contains("de")) -> {
                "When will you send the money / please send money." to "Hinglish"
            }
            lower.contains("chale") || lower.contains("chalna hai") || lower.contains("chaloge") -> {
                "Shall we go / are you coming?" to "Hinglish"
            }
            lower.contains("como estas") || lower.contains("cómo estás") -> {
                "How are you?" to "Spanish"
            }
            lower.contains("donde estas") || lower.contains("dónde estás") -> {
                "Where are you?" to "Spanish"
            }
            lower.contains("que paso") || lower.contains("qué pasó") -> {
                "What happened?" to "Spanish"
            }
            else -> null
        }
    }

    /**
     * Executes translation using active AI provider with strict fallback and timeout.
     */
    suspend fun translateQuestion(
        question: String,
        provider: AiProvider,
        apiKey: String?
    ): LangTranslationResult = withContext(Dispatchers.IO) {
        val clean = question.trim()
        if (clean.isBlank()) {
            return@withContext LangTranslationResult("", FALLBACK_UNAVAILABLE, "Unknown", false)
        }

        // Fast offline dictionary check first
        val dictMatch = lookupOfflineDictionary(clean.lowercase())
        if (dictMatch != null) {
            return@withContext LangTranslationResult(
                original = clean,
                englishMeaning = dictMatch.first,
                detectedLanguage = dictMatch.second,
                isSuccessful = true
            )
        }

        // If provider is local built-in, or no API key is available, return dictionary or fallback
        if (provider.type == AiProviderType.GEMINI_BUILTIN || apiKey.isNullOrBlank()) {
            return@withContext fallbackOrUnavailable(clean)
        }

        try {
            val systemPrompt = "You are a professional multilingual translator.\n" +
                    "Translate the provided message into plain, natural English.\n" +
                    "Output strictly a JSON object with keys:\n" +
                    "\"original\": the exact incoming message,\n" +
                    "\"meaning\": the plain English translation of what the sender is asking or stating,\n" +
                    "\"detected_language\": the name of the detected language or dialect (e.g. \"Hinglish\", \"Hindi\", \"Spanish\").\n" +
                    "No conversational filler. Output ONLY the JSON object."

            val userContent = "Message to translate: \"$clean\""

            val rawOutput = when (provider.type) {
                AiProviderType.GEMINI_API -> {
                    callGeminiForTranslation(clean, apiKey, provider.modelName)
                }
                AiProviderType.OPENAI -> {
                    callOpenAiForTranslation(systemPrompt, userContent, apiKey, provider.modelName.ifBlank { "gpt-4o-mini" }, "https://api.openai.com/v1/chat/completions")
                }
                AiProviderType.GROQ, AiProviderType.DEEPSEEK -> {
                    callOpenAiForTranslation(systemPrompt, userContent, apiKey, provider.modelName.ifBlank { "openai/gpt-oss-120b" }, "https://api.groq.com/openai/v1/chat/completions")
                }
                AiProviderType.ANTHROPIC -> {
                    callAnthropicForTranslation(systemPrompt, userContent, apiKey, provider.modelName.ifBlank { "claude-3-5-haiku-20241022" })
                }
                AiProviderType.CUSTOM_REST -> {
                    val endpoint = provider.customEndpoint?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1/chat/completions"
                    callOpenAiForTranslation(systemPrompt, userContent, apiKey, provider.modelName.ifBlank { "gpt-4o-mini" }, endpoint)
                }
                else -> null
            }

            return@withContext parseAndValidateTranslation(rawOutput, clean)
        } catch (_: Exception) {
            return@withContext fallbackOrUnavailable(clean)
        }
    }

    private fun callGeminiForTranslation(text: String, apiKey: String, modelName: String): String {
        val model = modelName.ifBlank { "gemini-2.5-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5000
            readTimeout = 7000
            doOutput = true
        }

        val prompt = "Translate this message into natural English. Message: \"$text\"\n" +
                "Output ONLY a valid JSON object: {\"original\": \"$text\", \"meaning\": \"<English translation>\", \"detected_language\": \"<language name>\"}"

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            conn.disconnect()
            throw RuntimeException("Gemini HTTP $responseCode")
        }

        val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()

        val json = JSONObject(responseStr)
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val first = candidates.getJSONObject(0)
        val content = first.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        if (parts.length() == 0) return ""
        return parts.getJSONObject(0).optString("text", "")
    }

    private fun callOpenAiForTranslation(
        systemPrompt: String,
        userContent: String,
        apiKey: String,
        modelName: String,
        endpoint: String
    ): String {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 5000
            readTimeout = 7000
            doOutput = true
        }

        val body = JSONObject().apply {
            put("model", modelName)
            put("temperature", 0.1)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            conn.disconnect()
            throw RuntimeException("HTTP $responseCode")
        }

        val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()

        val json = JSONObject(responseStr)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return ""
        return message.optString("content", "")
    }

    private fun callAnthropicForTranslation(
        systemPrompt: String,
        userContent: String,
        apiKey: String,
        modelName: String
    ): String {
        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 5000
            readTimeout = 7000
            doOutput = true
        }

        val body = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 256)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            conn.disconnect()
            throw RuntimeException("Anthropic HTTP $responseCode")
        }

        val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()

        val json = JSONObject(responseStr)
        val contentArr = json.optJSONArray("content") ?: return ""
        if (contentArr.length() == 0) return ""
        return contentArr.getJSONObject(0).optString("text", "")
    }
}
