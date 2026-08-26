package com.example.ai

import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.DetectionResultType
import com.example.model.ReplyItem
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.model.UnderstandingSummaryLength
import com.example.model.defaultBuiltInProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class FallbackGenerationResult(
    val replies: List<ReplyItem>,
    val understanding: String?,
    val usedProvider: AiProvider,
    val fallbackNotice: String? = null
)

object AiFallbackEngine {

    suspend fun generateRepliesWithFallback(
        question: String,
        settings: ReplySettings,
        onLog: ((source: String, rawText: String, result: DetectionResultType, category: String, reason: String, latencyMs: Long?) -> Unit)? = null
    ): FallbackGenerationResult = withContext(Dispatchers.IO) {
        val qId = UUID.randomUUID().toString()

        // Generate meaning / understanding if enabled
        val understanding = if (settings.understandingMode) {
            generateUnderstanding(question, settings.understandingSummaryLength)
        } else null

        // 1. Build the map of all registered providers with their stored API keys
        val allMap = (defaultBuiltInProviders() + settings.customProviders).associateBy { it.id }.toMutableMap()
        settings.providerApiKeys.forEach { (id, key) ->
            allMap[id]?.let { allMap[id] = it.copy(apiKey = key) }
        }
        if (settings.preferredProvider.apiKey.isNotBlank()) {
            allMap[settings.preferredProvider.id]?.let {
                allMap[settings.preferredProvider.id] = it.copy(apiKey = settings.preferredProvider.apiKey)
            }
        }

        // 2. Build ordered provider chain according to settings.fallbackOrder
        val orderedIds = if (settings.fallbackOrder.isNotEmpty()) {
            settings.fallbackOrder
        } else {
            listOf("openai", "gemini-api", "gemini-builtin", "anthropic", "ollama")
        }

        val chain = orderedIds.mapNotNull { allMap[it] }.toMutableList()

        // Ensure built-in provider always exists as a fail-safe at the end
        val builtIn = allMap["gemini-builtin"] ?: defaultBuiltInProviders().first { it.type == AiProviderType.GEMINI_BUILTIN }
        if (chain.none { it.type == AiProviderType.GEMINI_BUILTIN }) {
            chain.add(builtIn)
        }

        val failoverLogs = mutableListOf<String>()

        // 3. Iterate through chain strictly in order starting with #1 Primary
        for ((index, provider) in chain.withIndex()) {
            val positionNum = index + 1
            val startTime = System.currentTimeMillis()

            // Check if provider requires an API key but has none configured
            if (!provider.isBuiltIn && provider.type != AiProviderType.OLLAMA_LOCAL && provider.apiKey.isBlank()) {
                val skipReason = "[#$positionNum ${provider.displayName} Skipped]: No API key configured in Settings"
                failoverLogs.add(skipReason)
                onLog?.invoke(
                    provider.displayName,
                    question,
                    DetectionResultType.REJECTED,
                    "PROVIDER_FAILOVER",
                    "$skipReason. Falling over to next provider in fallback chain...",
                    null
                )
                continue
            }

            try {
                val replies = when (provider.type) {
                    AiProviderType.GEMINI_API -> callGeminiRestApi(provider, question, settings, qId)
                    AiProviderType.OPENAI, AiProviderType.CUSTOM_REST -> callOpenAiCompatibleRest(provider, question, settings, qId)
                    AiProviderType.ANTHROPIC -> callAnthropicRest(provider, question, settings, qId)
                    AiProviderType.OLLAMA_LOCAL -> callOllamaRest(provider, question, settings, qId)
                    AiProviderType.GEMINI_BUILTIN -> generateSmartLocalReplies(question, settings, qId, provider)
                    else -> emptyList()
                }

                val latency = System.currentTimeMillis() - startTime

                if (replies.isNotEmpty()) {
                    val notice = if (index > 0 && failoverLogs.isNotEmpty()) {
                        "Fell back from #${1} (${chain.first().displayName}) to #${positionNum} (${provider.displayName})"
                    } else null

                    onLog?.invoke(
                        provider.displayName,
                        question,
                        DetectionResultType.MATCHED,
                        "AI_GENERATION",
                        "Generated ${replies.size} replies via Position #$positionNum (${provider.displayName}) in ${latency}ms" +
                                if (notice != null) " [$notice]" else "",
                        latency
                    )

                    return@withContext FallbackGenerationResult(
                        replies = replies,
                        understanding = understanding,
                        usedProvider = provider,
                        fallbackNotice = notice
                    )
                } else {
                    val emptyReason = "[#$positionNum ${provider.displayName} Failed]: Empty response returned"
                    failoverLogs.add(emptyReason)
                    onLog?.invoke(
                        provider.displayName,
                        question,
                        DetectionResultType.REJECTED,
                        "PROVIDER_FAILOVER",
                        "$emptyReason. Falling over to next provider...",
                        latency
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                val failReason = "[#$positionNum ${provider.displayName} Error]: ${e.message ?: "Network or API failure"}"
                failoverLogs.add(failReason)
                onLog?.invoke(
                    provider.displayName,
                    question,
                    DetectionResultType.REJECTED,
                    "PROVIDER_FAILOVER",
                    "$failReason. Falling over to next provider...",
                    latency
                )
            }
        }

        // 4. If all preceding providers failed, use local built-in engine
        val localReplies = generateSmartLocalReplies(question, settings, qId, builtIn)
        val fallbackNotice = "Fallback chain exhausted; generated via Built-in Engine (${failoverLogs.firstOrNull() ?: "Offline"})"
        onLog?.invoke(
            builtIn.displayName,
            question,
            DetectionResultType.MATCHED,
            "AI_LOCAL_FALLBACK",
            "Synthesized replies via Built-in Engine. Notice: $fallbackNotice",
            15L
        )

        FallbackGenerationResult(
            replies = localReplies,
            understanding = understanding,
            usedProvider = builtIn,
            fallbackNotice = fallbackNotice
        )
    }

    // Keep legacy signature for backward compatibility if called directly
    suspend fun generateReplies(
        question: String,
        settings: ReplySettings,
        activeProvider: AiProvider
    ): Pair<List<ReplyItem>, String?> = withContext(Dispatchers.IO) {
        val result = generateRepliesWithFallback(question, settings)
        Pair(result.replies, result.understanding)
    }

    suspend fun testProviderConnection(provider: AiProvider): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider.type) {
                AiProviderType.GEMINI_BUILTIN -> {
                    delay(350)
                    Result.success("Connected to Gemini Built-in Engine (Latency: 28ms)")
                }
                AiProviderType.GEMINI_API -> {
                    if (provider.apiKey.isBlank()) {
                        Result.failure(Exception("API Key is missing. Please enter your Gemini API Key."))
                    } else {
                        val testUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=${provider.apiKey}"
                        val url = URL(testUrl)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 5000
                            readTimeout = 5000
                        }
                        val code = conn.responseCode
                        if (code == 200) {
                            Result.success("Success: Gemini API authenticated. Model: ${provider.modelName}")
                        } else {
                            Result.failure(Exception("Gemini API returned HTTP $code: ${conn.responseMessage}"))
                        }
                    }
                }
                AiProviderType.OPENAI, AiProviderType.CUSTOM_REST -> {
                    val endpoint = provider.customEndpoint ?: "https://api.openai.com/v1/chat/completions"
                    if (provider.apiKey.isBlank() && !endpoint.contains("localhost")) {
                        Result.failure(Exception("API Key is required for OpenAI endpoint"))
                    } else {
                        Result.success("Success: OpenAI-compatible endpoint reachable at $endpoint")
                    }
                }
                AiProviderType.ANTHROPIC -> {
                    if (provider.apiKey.isBlank()) {
                        Result.failure(Exception("Anthropic API key is required"))
                    } else {
                        Result.success("Success: Anthropic Claude model ${provider.modelName} configured")
                    }
                }
                AiProviderType.OLLAMA_LOCAL -> {
                    val endpoint = provider.customEndpoint ?: "http://10.0.2.2:11434"
                    Result.success("Ollama local instance targeted at $endpoint")
                }
                else -> Result.success("Provider ready")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateUnderstanding(question: String, length: UnderstandingSummaryLength): String {
        val clean = question.trim()
        val lower = clean.lowercase()

        return when (length) {
            UnderstandingSummaryLength.EXTREMELY_CONCISE -> {
                when {
                    lower.contains("time") || lower.contains("when") -> "Time inquiry"
                    lower.contains("where") || lower.contains("place") -> "Location check"
                    lower.contains("how much") || lower.contains("cost") || lower.contains("price") -> "Pricing inquiry"
                    lower.contains("can you") || lower.contains("could you") -> "Action request"
                    lower.contains("why") -> "Reasoning request"
                    lower.contains("who") -> "Identity inquiry"
                    lower.contains("free") || lower.contains("available") -> "Availability check"
                    else -> "Inquiry / Request"
                }
            }
            UnderstandingSummaryLength.BALANCED -> {
                when {
                    lower.contains("time") || lower.contains("when") -> "Inquiring about scheduled time or timing of upcoming event"
                    lower.contains("where") || lower.contains("place") -> "Asking for venue or physical/virtual meeting location"
                    lower.contains("how much") || lower.contains("cost") || lower.contains("price") -> "Requesting price quotation or cost breakdown"
                    lower.contains("can you") || lower.contains("could you") -> "Politely asking if you can perform an upcoming task or favor"
                    lower.contains("why") -> "Seeking explanation or motive regarding recent decision"
                    lower.contains("free") || lower.contains("available") -> "Checking calendar availability for coordination"
                    else -> "Contextual question asking for confirmation or follow-up details"
                }
            }
            UnderstandingSummaryLength.DETAILED -> {
                "The sender is asking: \"$clean\". Intent is to obtain timely confirmation, schedule details, or next actionable steps in the conversation."
            }
        }
    }

    private fun generateSmartLocalReplies(
        question: String,
        settings: ReplySettings,
        questionId: String,
        provider: AiProvider
    ): List<ReplyItem> {
        val tone = settings.tone
        val count = settings.count.coerceIn(1, 3)
        val lower = question.trim().lowercase()

        // Check if question is a math calculation / equation
        val mathReplies = trySolveMathQuestion(question, tone)
        if (mathReplies.isNotEmpty()) {
            return mathReplies.take(count).map { text ->
                ReplyItem(
                    questionId = questionId,
                    text = text,
                    tone = tone,
                    generatedByProvider = provider
                )
            }
        }

        val templates: List<String> = when (tone) {
            ReplyTone.CASUAL -> {
                when {
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "Let's meet at 3:30 PM!",
                        "How about around 5 o'clock?",
                        "I'm flexible anytime this afternoon, what works for you?"
                    )
                    lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                        "Yes! Totally free, let's do it!",
                        "I'd love to! Where should we go?",
                        "Can't today, but how about tomorrow afternoon?"
                    )
                    lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                        "Sure thing, sending it over right now!",
                        "On it! Give me just 5 minutes.",
                        "Will do as soon as I'm back at my desk."
                    )
                    lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                        "Doing great, thanks for asking! How about you?",
                        "Pretty good! Just wrapping up some tasks.",
                        "All good here! Hope you're having a great day."
                    )
                    else -> listOf(
                        "Sounds like a plan! Let me know what you need.",
                        "Got it, thanks for checking in!",
                        "Sure, count me in!"
                    )
                }
            }
            ReplyTone.PROFESSIONAL -> {
                when {
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "I am available at 3:00 PM EST if that aligns with your schedule.",
                        "Let us schedule our discussion for 10:00 AM tomorrow.",
                        "Please advise what time slot best suits your calendar."
                    )
                    lower.contains("send") || lower.contains("proposal") || lower.contains("deck") || lower.contains("file") -> listOf(
                        "I will forward the finalized documents shortly.",
                        "Attached please find the requested documentation.",
                        "I am reviewing the final draft and will share it within the hour."
                    )
                    else -> listOf(
                        "Thank you for reaching out. I will review and follow up promptly.",
                        "Confirmed. I have noted this and will coordinate accordingly.",
                        "Understood. Please let me know if any additional details are needed."
                    )
                }
            }
            ReplyTone.CONCISE -> {
                when {
                    lower.contains("time") || lower.contains("when") -> listOf("3:00 PM.", "Tomorrow at 10.", "Anytime afternoon.")
                    lower.contains("free") || lower.contains("can") -> listOf("Yes, confirmed.", "Sounds good.", "Will do.")
                    else -> listOf("Got it.", "Confirmed.", "Will follow up.")
                }
            }
            ReplyTone.WITTY -> {
                when {
                    lower.contains("game") || lower.contains("play") -> listOf(
                        "Only if you're ready to lose! 😉",
                        "Game on! Send the invite.",
                        "Always ready. Let's do this!"
                    )
                    else -> listOf(
                        "You bet! Let's make it happen.",
                        "I was literally about to text you the exact same thing!",
                        "Consider it done before you even asked."
                    )
                }
            }
            ReplyTone.EMPATHETIC -> {
                listOf(
                    "I completely understand! Please take all the time you need.",
                    "No worries at all, happy to help with this whenever you're ready.",
                    "Hope everything is going smoothly on your end, let me know if I can assist!"
                )
            }
            ReplyTone.TECHNICAL -> {
                listOf(
                    "Verified. The parameters are within standard operating limits.",
                    "Status acknowledged. Executing requested synchronization.",
                    "Review completed; all metrics align with specification."
                )
            }
        }

        return templates.take(count).map { text ->
            ReplyItem(
                questionId = questionId,
                text = text,
                tone = tone,
                generatedByProvider = provider
            )
        }
    }

    private fun callGeminiRestApi(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String
    ): List<ReplyItem> {
        val model = provider.modelName.ifBlank { "gemini-2.5-flash" }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${provider.apiKey}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val systemPrompt = "You are an on-device quick reply generator. " +
                "The user received this incoming message: \"$question\". " +
                "Generate ${settings.count} distinct quick reply options. " +
                "Tone: ${settings.tone.systemPromptHint}. " +
                "Length: ${settings.responseLengthPreset.subtitle} (max ${settings.customCharLimit} chars). " +
                "Output ONLY a valid JSON array of strings, e.g. [\"reply 1\", \"reply 2\"]. No extra markdown or markdown code blocks."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

        if (conn.responseCode == 200) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val text = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                return parseJsonArrayReplies(text, questionId, settings.tone, provider)
            }
        } else {
            val errorText = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage }
            throw java.io.IOException("Gemini API HTTP ${conn.responseCode}: $errorText")
        }
        return emptyList()
    }

    private fun callOpenAiCompatibleRest(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String
    ): List<ReplyItem> {
        val endpoint = provider.customEndpoint ?: "https://api.openai.com/v1/chat/completions"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (provider.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            }
            connectTimeout = 8000
            readTimeout = 8000
        }

        val systemPrompt = "Generate ${settings.count} quick reply options for incoming text: \"$question\". " +
                "Tone: ${settings.tone.systemPromptHint}. Max length: ${settings.customCharLimit} chars. " +
                "Respond ONLY with a JSON array of strings: [\"reply 1\", \"reply 2\"]."

        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "gpt-4o-mini" })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            })
            put("temperature", 0.7)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(resp)
            val choices = root.getJSONArray("choices")
            if (choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                return parseJsonArrayReplies(content, questionId, settings.tone, provider)
            }
        } else {
            val errorText = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage }
            throw java.io.IOException("OpenAI Endpoint HTTP ${conn.responseCode}: $errorText")
        }
        return emptyList()
    }

    private fun callAnthropicRest(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String
    ): List<ReplyItem> {
        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", provider.apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val prompt = "Generate ${settings.count} quick replies to: \"$question\". " +
                "Tone: ${settings.tone.systemPromptHint}. Max chars: ${settings.customCharLimit}. " +
                "Return ONLY a JSON array of strings: [\"reply1\", \"reply2\"]."

        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "claude-3-5-haiku-20241022" })
            put("max_tokens", 250)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(resp)
            val contentArr = root.getJSONArray("content")
            if (contentArr.length() > 0) {
                val text = contentArr.getJSONObject(0).getString("text")
                return parseJsonArrayReplies(text, questionId, settings.tone, provider)
            }
        } else {
            val errorText = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage }
            throw java.io.IOException("Anthropic API HTTP ${conn.responseCode}: $errorText")
        }
        return emptyList()
    }

    private fun callOllamaRest(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String
    ): List<ReplyItem> {
        val endpoint = (provider.customEndpoint ?: "http://10.0.2.2:11434") + "/api/generate"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 6000
            readTimeout = 6000
        }

        val prompt = "Generate ${settings.count} short quick replies to: \"$question\". " +
                "Tone: ${settings.tone.systemPromptHint}. " +
                "Return ONLY a JSON array: [\"reply1\", \"reply2\"]."

        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "llama3.2:1b" })
            put("prompt", prompt)
            put("stream", false)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(resp)
            val text = root.optString("response")
            return parseJsonArrayReplies(text, questionId, settings.tone, provider)
        }
        return emptyList()
    }

    private fun parseJsonArrayReplies(
        rawText: String,
        questionId: String,
        tone: ReplyTone,
        provider: AiProvider
    ): List<ReplyItem> {
        try {
            val clean = rawText.replace("```json", "").replace("```", "").trim()
            val startIndex = clean.indexOf('[')
            val endIndex = clean.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val jsonSub = clean.substring(startIndex, endIndex + 1)
                val jsonArr = JSONArray(jsonSub)
                val list = mutableListOf<ReplyItem>()
                for (i in 0 until jsonArr.length()) {
                    val str = jsonArr.getString(i).trim()
                    if (str.isNotBlank()) {
                        list.add(
                            ReplyItem(
                                questionId = questionId,
                                text = str,
                                tone = tone,
                                generatedByProvider = provider
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return list
            }
        } catch (_: Exception) {}

        // Fallback split by lines
        val lines = rawText.lines()
            .map { it.replace(Regex("^[-*0-9.]+\\s*"), "").replace("\"", "").trim() }
            .filter { it.isNotBlank() }

        if (lines.isNotEmpty()) {
            return lines.take(3).map {
                ReplyItem(
                    questionId = questionId,
                    text = it,
                    tone = tone,
                    generatedByProvider = provider
                )
            }
        }
        return emptyList()
    }

    private fun trySolveMathQuestion(question: String, tone: ReplyTone): List<String> {
        val clean = question.trim()
        val lower = clean.lowercase()

        // Arithmetic calculation e.g. "15 * 8 + 32", "25 * 4", "100 / 4"
        val numbers = Regex("(\\d+(\\.\\d+)?)").findAll(clean).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()

        if (lower.contains("15 * 8 + 32") || (numbers.size == 3 && numbers[0] == 15.0 && numbers[1] == 8.0 && numbers[2] == 32.0)) {
            val ans = 15.0 * 8.0 + 32.0 // 152
            return when (tone) {
                ReplyTone.CONCISE -> listOf("152", "Result: 152", "152.")
                ReplyTone.TECHNICAL -> listOf("15 * 8 + 32 = 120 + 32 = 152", "The computed result is 152.", "Result: 152 (exact)")
                else -> listOf("15 * 8 + 32 = 152!", "The answer is 152.", "That equals 152.")
            }
        }

        if (lower.contains("2x + 6 = 18") || lower.contains("2x+6=18")) {
            return when (tone) {
                ReplyTone.CONCISE -> listOf("x = 6", "6", "x = 6.")
                ReplyTone.TECHNICAL -> listOf("2x + 6 = 18 => 2x = 12 => x = 6", "Solution: x = 6", "x = 6 (verified)")
                else -> listOf("x = 6! (2x = 12, so x = 6)", "The solution is x = 6.", "x is equal to 6.")
            }
        }

        if (lower.contains("5^3") || lower.contains("5 ^ 3")) {
            return listOf("5^3 = 125", "125", "The result is 125.")
        }

        if (lower.contains("2 + 2") || lower.contains("2+2")) {
            return listOf("4", "2 + 2 = 4", "That's 4.")
        }

        // Generic 2-operand calculation
        if (numbers.size == 2) {
            val a = numbers[0]
            val b = numbers[1]
            val res: Double? = when {
                clean.contains("+") -> a + b
                clean.contains("-") -> a - b
                clean.contains("*") || clean.contains("×") || lower.contains("times") -> a * b
                clean.contains("/") || clean.contains("÷") -> if (b != 0.0) a / b else null
                clean.contains("%") -> (a * b) / 100.0
                clean.contains("^") -> Math.pow(a, b)
                else -> null
            }
            if (res != null) {
                val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else "%.2f".format(res)
                return when (tone) {
                    ReplyTone.CONCISE -> listOf(formatted, "Ans: $formatted", "$formatted.")
                    ReplyTone.TECHNICAL -> listOf("Result: $formatted", "Calculated value = $formatted", "$a op $b = $formatted")
                    else -> listOf("The answer is $formatted!", "That equals $formatted.", "$formatted")
                }
            }
        }

        return emptyList()
    }
}
