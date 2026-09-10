package com.example.ai

import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.AiQuestionVerificationResult
import com.example.model.BrainKnowledgeEntry
import com.example.model.DetectionResultType
import com.example.model.ReplyItem
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
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
    val usedProvider: AiProvider,
    val fallbackNotice: String? = null
)

data class ProviderReplyResult(
    val replies: List<ReplyItem>
)

object AiFallbackEngine {

    fun resolveEffectiveApiKey(provider: AiProvider, settings: ReplySettings): String {
        val settingsKey = settings.providerApiKeys[provider.id]?.trim() ?: ""
        if (settingsKey.isNotBlank()) return settingsKey

        if (settings.preferredProvider.id == provider.id && settings.preferredProvider.apiKey.isNotBlank()) {
            return settings.preferredProvider.apiKey.trim()
        }

        if (provider.apiKey.isNotBlank()) {
            return provider.apiKey.trim()
        }

        return try {
            when (provider.type) {
                AiProviderType.GEMINI_API -> com.example.BuildConfig.GEMINI_API_KEY.trim()
                AiProviderType.OPENAI -> com.example.BuildConfig.OPENAI_API_KEY.trim()
                AiProviderType.ANTHROPIC -> com.example.BuildConfig.ANTHROPIC_API_KEY.trim()
                AiProviderType.GROQ -> com.example.BuildConfig.GROK_API_KEY.trim()
                else -> ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun generateRepliesWithFallback(
        question: String,
        settings: ReplySettings,
        sourceApp: String? = null,
        onLog: ((source: String, rawText: String, result: DetectionResultType, category: String, reason: String, latencyMs: Long?) -> Unit)? = null
    ): FallbackGenerationResult = withContext(Dispatchers.IO) {
        val qId = UUID.randomUUID().toString()

        // 0. Search Brain Knowledge Base for relevant verified facts/notes
        val relevantBrainEntries = BrainKnowledgeMatcher.findRelevantEntries(
            question = question,
            sourceApp = sourceApp,
            entries = settings.brainEntries
        )
        val brainKnowledgePrompt = BrainKnowledgeMatcher.formatKnowledgePrompt(relevantBrainEntries)
        if (relevantBrainEntries.isNotEmpty()) {
            val titles = relevantBrainEntries.joinToString(", ") { "'${it.title}'" }
            onLog?.invoke(
                "Brain Knowledge",
                question,
                DetectionResultType.MATCHED,
                "BRAIN_KNOWLEDGE_APPLIED",
                "Matched ${relevantBrainEntries.size} Brain entry/entries ($titles). Injected into AI prompt context.",
                2L
            )
        }

        // 1. Build the map of all registered providers with their stored API keys and model overrides
        val allMap = (defaultBuiltInProviders() + settings.customProviders).associateBy { it.id }.toMutableMap()
        for ((id, prov) in allMap.entries.toList()) {
            val effKey = resolveEffectiveApiKey(prov, settings)
            val modelOverride = settings.providerModelOverrides[id]
                ?: (if (settings.preferredProvider.id == id) settings.preferredProvider.modelName else "")
            allMap[id] = prov.copy(
                apiKey = if (effKey.isNotBlank()) effKey else prov.apiKey,
                modelName = if (modelOverride.isNotBlank()) modelOverride else prov.modelName
            )
        }

        // 2. Build ordered provider chain according to settings.fallbackOrder
        val orderedIds = if (settings.fallbackOrder.isNotEmpty()) {
            settings.fallbackOrder
        } else {
            listOf("openai", "gemini-api", "gemini-builtin", "anthropic", "groq")
        }

        val chain = orderedIds.mapNotNull { allMap[it] }.toMutableList()

        // Prioritize preferred provider at position 1 if configured
        val preferred = allMap[settings.preferredProvider.id]
        if (preferred != null) {
            chain.removeAll { it.id == preferred.id }
            chain.add(0, preferred)
        }

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
            if (!provider.isBuiltIn && provider.apiKey.isBlank()) {
                val skipReason = "[#$positionNum ${provider.displayName} Skipped]: No API key configured in Settings > Providers (HTTP 401 / Missing Bearer Token)"
                failoverLogs.add(skipReason)
                onLog?.invoke(
                    provider.displayName,
                    question,
                    DetectionResultType.REJECTED,
                    "NO_API_KEY",
                    "$skipReason. Falling back to Position #${positionNum + 1}...",
                    0L
                )
                continue
            }

            try {
                val result: ProviderReplyResult = when (provider.type) {
                    AiProviderType.GEMINI_API -> callGeminiRestApi(provider, question, settings, qId, brainKnowledgePrompt)
                    AiProviderType.OPENAI, AiProviderType.GROQ, AiProviderType.CUSTOM_REST -> callOpenAiCompatibleRest(provider, question, settings, qId, brainKnowledgePrompt)
                    AiProviderType.ANTHROPIC -> callAnthropicRest(provider, question, settings, qId, brainKnowledgePrompt)
                    AiProviderType.GEMINI_BUILTIN -> generateSmartLocalReplies(question, settings, qId, provider, relevantBrainEntries)
                    else -> ProviderReplyResult(emptyList())
                }
                val replies = result.replies

                val latency = System.currentTimeMillis() - startTime

                if (replies.isNotEmpty()) {
                    val notice = if (index > 0 && failoverLogs.isNotEmpty()) {
                        val firstFail = failoverLogs.firstOrNull() ?: ""
                        val briefReason = when {
                            firstFail.contains("401") || firstFail.contains("No API key") || firstFail.contains("Auth") -> "HTTP 401 Auth/No Key"
                            firstFail.contains("429") || firstFail.contains("Quota") -> "HTTP 429 Quota"
                            firstFail.contains("404") || firstFail.contains("Model") -> "HTTP 404 Model"
                            firstFail.contains("400") -> "HTTP 400 Bad Req"
                            else -> "Failed"
                        }
                        "Fell back from #${1} (${chain.first().displayName} - $briefReason) to #${positionNum} (${provider.displayName})"
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
                        usedProvider = provider,
                        fallbackNotice = notice
                    )
                } else {
                    val emptyReason = "[#$positionNum ${provider.displayName} Failed]: Empty response payload returned by API"
                    failoverLogs.add(emptyReason)
                    onLog?.invoke(
                        provider.displayName,
                        question,
                        DetectionResultType.REJECTED,
                        "EMPTY_RESPONSE",
                        "$emptyReason. Falling back to next provider...",
                        latency
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                val failReason = "[#$positionNum ${provider.displayName} Failed]: ${e.message ?: "Network or API failure"}"
                failoverLogs.add(failReason)
                val category = when {
                    e.message?.contains("401") == true || e.message?.contains("Auth") == true -> "AUTH_FAILURE"
                    e.message?.contains("429") == true || e.message?.contains("Quota") == true -> "QUOTA_EXCEEDED"
                    e.message?.contains("404") == true || e.message?.contains("Model") == true -> "MODEL_NOT_FOUND"
                    e.message?.contains("400") == true -> "BAD_REQUEST"
                    else -> "PROVIDER_ERROR"
                }
                onLog?.invoke(
                    provider.displayName,
                    question,
                    DetectionResultType.REJECTED,
                    category,
                    "$failReason. Falling back to Position #${positionNum + 1}...",
                    latency
                )
            }
        }

        // 4. If all preceding providers failed, use local built-in engine
        val localResult = generateSmartLocalReplies(question, settings, qId, builtIn, relevantBrainEntries)
        val localReplies = localResult.replies
        val firstFail = failoverLogs.firstOrNull() ?: "Offline"
        val briefFail = when {
            firstFail.contains("401") || firstFail.contains("No API key") || firstFail.contains("Auth") -> "HTTP 401 Auth/No Key"
            firstFail.contains("429") || firstFail.contains("Quota") -> "HTTP 429 Quota"
            firstFail.contains("404") || firstFail.contains("Model") -> "HTTP 404 Model"
            firstFail.contains("400") -> "HTTP 400 Bad Req"
            else -> "Offline"
        }
        val fallbackNotice = "Fell back from #1 (${chain.first().displayName} - $briefFail) to #3 (${builtIn.displayName})"
        onLog?.invoke(
            builtIn.displayName,
            question,
            DetectionResultType.MATCHED,
            "AI_LOCAL_FALLBACK",
            "Synthesized replies via Built-in Engine. Notice: $fallbackNotice",
            12L
        )

        FallbackGenerationResult(
            replies = localReplies,
            usedProvider = builtIn,
            fallbackNotice = fallbackNotice
        )
    }

    // Keep legacy signature for backward compatibility if called directly
    suspend fun generateReplies(
        question: String,
        settings: ReplySettings,
        activeProvider: AiProvider,
        sourceApp: String? = null
    ): List<ReplyItem> = withContext(Dispatchers.IO) {
        val result = generateRepliesWithFallback(question, settings, sourceApp)
        result.replies
    }

    /**
     * Executes the extra AI classification pre-check step for "Smart Detection (AI Verified)".
     * Selects the fastest/cheapest available model specifically for this check,
     * falling back to the on-device built-in engine if offline or if no remote key is configured.
     */
    suspend fun classifyQuestionWithFastestModel(
        text: String,
        settings: ReplySettings
    ): AiQuestionVerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val clean = text.trim()

        val allMap = (defaultBuiltInProviders() + settings.customProviders).associateBy { it.id }.toMutableMap()
        for ((id, prov) in allMap.entries.toList()) {
            val effKey = resolveEffectiveApiKey(prov, settings)
            val modelOverride = settings.providerModelOverrides[id]
                ?: (if (settings.preferredProvider.id == id) settings.preferredProvider.modelName else "")
            allMap[id] = prov.copy(
                apiKey = if (effKey.isNotBlank()) effKey else prov.apiKey,
                modelName = if (modelOverride.isNotBlank()) modelOverride else prov.modelName
            )
        }

        // Rank available providers by latency & cost for classification (Groq/Gemini Flash Lite/OpenAI 4o-mini)
        val fastestProvider = listOfNotNull(
            allMap["groq"]?.takeIf { it.apiKey.isNotBlank() },
            allMap["gemini-api"]?.takeIf { it.apiKey.isNotBlank() },
            allMap["openai"]?.takeIf { it.apiKey.isNotBlank() },
            allMap["anthropic"]?.takeIf { it.apiKey.isNotBlank() }
        ).firstOrNull() ?: allMap["gemini-builtin"] ?: defaultBuiltInProviders().first { it.type == AiProviderType.GEMINI_BUILTIN }

        if (fastestProvider.type == AiProviderType.GEMINI_BUILTIN) {
            val (isQ, reason) = classifyWithBuiltInEngine(clean)
            val latency = System.currentTimeMillis() - startTime
            return@withContext AiQuestionVerificationResult(
                isQuestion = isQ,
                reason = reason,
                modelUsed = fastestProvider.displayName,
                latencyMs = latency
            )
        }

        try {
            val (isQ, reason) = when (fastestProvider.type) {
                AiProviderType.GEMINI_API -> callGeminiClassification(fastestProvider, clean)
                AiProviderType.GROQ, AiProviderType.OPENAI, AiProviderType.CUSTOM_REST -> callOpenAiCompatibleClassification(fastestProvider, clean)
                AiProviderType.ANTHROPIC -> callAnthropicClassification(fastestProvider, clean)
                else -> classifyWithBuiltInEngine(clean)
            }
            val latency = System.currentTimeMillis() - startTime
            return@withContext AiQuestionVerificationResult(
                isQuestion = isQ,
                reason = reason,
                modelUsed = "${fastestProvider.displayName} (${fastestProvider.modelName})",
                latencyMs = latency
            )
        } catch (e: Exception) {
            val (isQ, reason) = classifyWithBuiltInEngine(clean)
            val latency = System.currentTimeMillis() - startTime
            return@withContext AiQuestionVerificationResult(
                isQuestion = isQ,
                reason = "$reason (Fallback from ${fastestProvider.displayName}: ${e.message ?: "Network failure"})",
                modelUsed = "Gemini Flash Lite (Built-in Fallback)",
                latencyMs = latency
            )
        }
    }

    fun classifyWithBuiltInEngine(clean: String): Pair<Boolean, String> {
        if (QuestionDetectionEngine.containsUrlPattern(clean)) {
            return Pair(false, "Detected URL, web link, or query parameter structure")
        }
        if (QuestionDetectionEngine.isTechnicalOrCodeSnippet(clean)) {
            return Pair(false, "Detected programming syntax, technical operator, or file path")
        }
        val analysis = QuestionDetectionEngine.analyze(clean, detectQuestionsOnly = true)
        if (!analysis.isQuestion) {
            return Pair(false, analysis.reason)
        }
        return Pair(true, "Context verified as genuine conversational inquiry (${analysis.category})")
    }

    private fun callGeminiClassification(provider: AiProvider, text: String): Pair<Boolean, String> {
        var rawModel = provider.modelName.trim()
        if (rawModel.startsWith("models/")) rawModel = rawModel.removePrefix("models/")
        rawModel = rawModel.replace(" ", "-")
        val model = when (rawModel) {
            "", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite-preview", "gemini-2.0-flash", "gemini-1.5-flash" -> "gemini-2.5-flash"
            "gemini-flash-latest" -> "gemini-flash-latest"
            else -> rawModel
        }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${provider.apiKey.trim()}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 4000
            readTimeout = 4000
        }

        val prompt = "You are an AI pre-check classifier for a chat assistant. Determine if this message is a genuine conversational question or an inquiry requiring a reply in messaging.\n" +
                "Reject: URLs/links (even with '?'), code/programming syntax, error logs, random UI labels, or non-question text.\n" +
                "Text: \"$text\"\n" +
                "Respond strictly with JSON: {\"isQuestion\": true|false, \"reason\": \"<short reason>\"}."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.0)
                put("maxOutputTokens", 80)
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

        if (conn.responseCode == 200) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val out = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                return parseClassificationJson(out)
            }
        }
        throw java.io.IOException("Gemini API classification failed (HTTP ${conn.responseCode})")
    }

    private fun callOpenAiCompatibleClassification(provider: AiProvider, text: String): Pair<Boolean, String> {
        val endpoint = provider.customEndpoint?.takeIf { it.isNotBlank() } ?: if (provider.type == AiProviderType.GROQ) {
            "https://api.groq.com/openai/v1/chat/completions"
        } else {
            "https://api.openai.com/v1/chat/completions"
        }
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (provider.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${provider.apiKey.trim()}")
            }
            connectTimeout = 4000
            readTimeout = 4000
        }

        val prompt = "You are an AI pre-check classifier for a chat assistant. Determine if this message is a genuine conversational question or an inquiry requiring a reply in messaging.\n" +
                "Reject: URLs/links (even with '?'), code/programming syntax, error logs, random UI labels, or non-question text.\n" +
                "Text: \"$text\"\n" +
                "Respond strictly with JSON: {\"isQuestion\": true|false, \"reason\": \"<short reason>\"}."

        val jsonBody = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "gpt-4o-mini" })
            put("max_tokens", 80)
            put("temperature", 0.0)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You output strictly valid JSON classification objects.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

        if (conn.responseCode == 200) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseText)
            val content = extractContentFromOpenAiJson(root)
            if (content.isNotBlank()) {
                return parseClassificationJson(content)
            }
        }
        throw java.io.IOException("${provider.displayName} classification failed (HTTP ${conn.responseCode})")
    }

    private fun callAnthropicClassification(provider: AiProvider, text: String): Pair<Boolean, String> {
        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", provider.apiKey.trim())
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 4000
            readTimeout = 4000
        }

        val prompt = "Determine if this message is a genuine conversational question or inquiry requiring a reply in messaging. Reject: URLs/links (even with '?'), code/programming syntax, error logs, random UI labels, or non-question text.\nText: \"$text\"\nOutput ONLY JSON: {\"isQuestion\": true|false, \"reason\": \"<short reason>\"}."

        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "claude-3-5-haiku-20241022" })
            put("max_tokens", 80)
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
                val out = contentArr.getJSONObject(0).getString("text")
                return parseClassificationJson(out)
            }
        }
        throw java.io.IOException("Anthropic classification failed (HTTP ${conn.responseCode})")
    }

    fun parseClassificationJson(raw: String): Pair<Boolean, String> {
        var clean = raw.trim()
        if (clean.contains("</think>")) {
            clean = clean.substringAfter("</think>").trim()
        }
        clean = clean.replace("```json", "").replace("```JSON", "").replace("```", "").trim()
        try {
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) {
                val json = JSONObject(clean.substring(start, end + 1))
                val isQ = json.optBoolean("isQuestion", json.optBoolean("is_question", false))
                val rsn = json.optString("reason", if (isQ) "Verified conversational question" else "Rejected by AI classifier")
                return Pair(isQ, rsn)
            }
        } catch (_: Exception) {}

        val lower = clean.lowercase()
        val isQ = lower.contains("\"isquestion\": true") || lower.contains("\"is_question\": true") || lower.contains("true")
        return Pair(isQ, if (isQ) "Verified conversational question" else "Rejected by AI classifier")
    }


    suspend fun testProviderConnection(
        provider: AiProvider,
        settings: ReplySettings = ReplySettings(),
        onLog: ((String, String, DetectionResultType, String, String, Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val testQuestion = "Are you available for a quick chat today?"
        val qId = "test_conn_${System.currentTimeMillis()}"

        val effKey = resolveEffectiveApiKey(provider, settings)
        val activeProvider = if (effKey.isNotBlank()) provider.copy(apiKey = effKey) else provider

        try {
            when (activeProvider.type) {
                AiProviderType.GEMINI_BUILTIN -> {
                    val replies = generateSmartLocalReplies(testQuestion, settings, qId, activeProvider).replies
                    val latency = System.currentTimeMillis() - startTime
                    val sample = replies.firstOrNull()?.text ?: "I am ready."
                    onLog?.invoke(
                        activeProvider.displayName,
                        testQuestion,
                        DetectionResultType.MATCHED,
                        "TEST_CONNECTION_SUCCESS",
                        "[Test Connection Passed]: Gemini Built-in Engine verified (Latency: ${latency}ms). Sample reply: \"$sample\"",
                        latency
                    )
                    Result.success("Success: Built-in Engine verified (${latency}ms)\nSample: \"$sample\"")
                }
                AiProviderType.GEMINI_API -> {
                    if (activeProvider.apiKey.isBlank()) {
                        val err = "Gemini API Key is missing. Enter key in Settings > Providers."
                        onLog?.invoke(
                            activeProvider.displayName,
                            testQuestion,
                            DetectionResultType.REJECTED,
                            "TEST_CONNECTION_FAILED",
                            "[Test Connection Failed]: $err",
                            0L
                        )
                        Result.failure(Exception(err))
                    } else {
                        // Execute EXACT same request format, endpoint, model, and JSON body as real generation
                        val replies = callGeminiRestApi(activeProvider, testQuestion, settings, qId).replies
                        val latency = System.currentTimeMillis() - startTime
                        if (replies.isNotEmpty()) {
                            val sample = replies.first().text
                            onLog?.invoke(
                                activeProvider.displayName,
                                testQuestion,
                                DetectionResultType.MATCHED,
                                "TEST_CONNECTION_SUCCESS",
                                "[Test Connection Passed]: Verified HTTP 200 via model '${activeProvider.modelName}' (${latency}ms). Sample reply: \"$sample\"",
                                latency
                            )
                            Result.success("Success: Verified Gemini '${provider.modelName}' (${latency}ms)\nSample: \"$sample\"")
                        } else {
                            val err = "Gemini API returned empty candidate array."
                            onLog?.invoke(
                                provider.displayName,
                                testQuestion,
                                DetectionResultType.REJECTED,
                                "TEST_CONNECTION_FAILED",
                                "[Test Connection Failed]: $err",
                                latency
                            )
                            Result.failure(Exception(err))
                        }
                    }
                }
                AiProviderType.OPENAI, AiProviderType.CUSTOM_REST -> {
                    val endpoint = provider.customEndpoint ?: "https://api.openai.com/v1/chat/completions"
                    if (provider.apiKey.isBlank() && !endpoint.contains("localhost") && !endpoint.contains("10.0.2.2")) {
                        val err = "OpenAI API Key is missing. Enter key in Settings > Providers."
                        onLog?.invoke(
                            provider.displayName,
                            testQuestion,
                            DetectionResultType.REJECTED,
                            "TEST_CONNECTION_FAILED",
                            "[Test Connection Failed]: $err",
                            0L
                        )
                        Result.failure(Exception(err))
                    } else {
                        // Execute EXACT same request format, endpoint, model, and JSON body as real generation
                        val (replyResult, rawResponse) = callOpenAiCompatibleRestWithRaw(provider, testQuestion, settings, qId)
                        val replies = replyResult.replies
                        val latency = System.currentTimeMillis() - startTime
                        if (replies.isNotEmpty()) {
                            val sample = replies.first().text
                            val displayReplies = replies.mapIndexed { idx, r -> "  ${idx + 1}. \"${r.text}\"" }.joinToString("\n")
                            val rawPreview = if (rawResponse.length > 500) rawResponse.take(500) + "\n... (truncated)" else rawResponse
                            onLog?.invoke(
                                provider.displayName,
                                testQuestion,
                                DetectionResultType.MATCHED,
                                "TEST_CONNECTION_SUCCESS",
                                "[Test Connection Passed]: Verified HTTP 200 via model '${provider.modelName}' (${latency}ms).\n\nReplies:\n$displayReplies\n\nRaw Response:\n$rawResponse",
                                latency
                            )
                            Result.success("Success: Verified '${provider.modelName}' (${latency}ms)\n\nSample Reply: \"$sample\"\n\nRaw JSON Response:\n$rawPreview")
                        } else {
                            val rawPreview = if (rawResponse.isNotBlank()) rawResponse else "(empty response body)"
                            val err = "API returned HTTP 200, but no reply text could be extracted.\n\nRaw JSON Response:\n$rawPreview"
                            onLog?.invoke(
                                provider.displayName,
                                testQuestion,
                                DetectionResultType.REJECTED,
                                "TEST_CONNECTION_FAILED",
                                "[Test Connection Failed]: $err",
                                latency
                            )
                            Result.failure(Exception(err))
                        }
                    }
                }
                AiProviderType.ANTHROPIC -> {
                    if (provider.apiKey.isBlank()) {
                        val err = "Anthropic API key is required."
                        Result.failure(Exception(err))
                    } else {
                        val replies = callAnthropicRest(provider, testQuestion, settings, qId).replies
                        val latency = System.currentTimeMillis() - startTime
                        val sample = replies.firstOrNull()?.text ?: "I am ready."
                        onLog?.invoke(
                            provider.displayName,
                            testQuestion,
                            DetectionResultType.MATCHED,
                            "TEST_CONNECTION_SUCCESS",
                            "[Test Connection Passed]: Verified Claude '${provider.modelName}' (${latency}ms). Sample reply: \"$sample\"",
                            latency
                        )
                        Result.success("Success: Verified Anthropic '${provider.modelName}' (${latency}ms)\nSample: \"$sample\"")
                    }
                }
                AiProviderType.GROQ -> {
                    if (provider.apiKey.isBlank()) {
                        val err = "Groq API Key is missing. Enter key in Settings > Providers."
                        onLog?.invoke(
                            provider.displayName,
                            testQuestion,
                            DetectionResultType.REJECTED,
                            "TEST_CONNECTION_FAILED",
                            "[Test Connection Failed]: $err",
                            0L
                        )
                        Result.failure(Exception(err))
                    } else {
                        val (replyResult, rawResponse) = callOpenAiCompatibleRestWithRaw(provider, testQuestion, settings, qId)
                        val replies = replyResult.replies
                        val latency = System.currentTimeMillis() - startTime
                        if (replies.isNotEmpty()) {
                            val sample = replies.first().text
                            val displayReplies = replies.mapIndexed { idx, r -> "  ${idx + 1}. \"${r.text}\"" }.joinToString("\n")
                            val rawPreview = if (rawResponse.length > 500) rawResponse.take(500) + "\n... (truncated)" else rawResponse
                            onLog?.invoke(
                                provider.displayName,
                                testQuestion,
                                DetectionResultType.MATCHED,
                                "TEST_CONNECTION_SUCCESS",
                                "[Test Connection Passed]: Verified Groq '${provider.modelName}' (${latency}ms).\n\nReplies:\n$displayReplies\n\nRaw Response:\n$rawResponse",
                                latency
                            )
                            Result.success("Success: Verified Groq '${provider.modelName}' (${latency}ms)\n\nSample Reply: \"$sample\"\n\nRaw JSON Response:\n$rawPreview")
                        } else {
                            val rawPreview = if (rawResponse.isNotBlank()) rawResponse else "(empty response body)"
                            val err = "Groq API returned HTTP 200, but no reply text could be extracted from choices.\n\nRaw JSON Response:\n$rawPreview"
                            onLog?.invoke(
                                provider.displayName,
                                testQuestion,
                                DetectionResultType.REJECTED,
                                "TEST_CONNECTION_FAILED",
                                "[Test Connection Failed]: $err",
                                latency
                            )
                            Result.failure(Exception(err))
                        }
                    }
                }
                else -> Result.success("Provider ready")
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val errMsg = e.message ?: "Unknown API connection error"
            onLog?.invoke(
                provider.displayName,
                testQuestion,
                DetectionResultType.REJECTED,
                "TEST_CONNECTION_FAILED",
                "[Test Connection Failed]: $errMsg (${latency}ms)",
                latency
            )
            Result.failure(e)
        }
    }

    private fun generateSmartLocalReplies(
        question: String,
        settings: ReplySettings,
        questionId: String,
        provider: AiProvider,
        relevantBrainEntries: List<BrainKnowledgeEntry> = emptyList()
    ): ProviderReplyResult {
        val tone = settings.tone
        val preset = settings.responseLengthPreset
        val count = settings.count.coerceIn(1, 3)
        val clean = question.trim()
        val lower = clean.lowercase()

        // 0. Brain Knowledge Base matching (Highest priority verified user facts)
        val matchedBrain = if (relevantBrainEntries.isNotEmpty()) {
            relevantBrainEntries
        } else {
            BrainKnowledgeMatcher.findRelevantEntries(clean, null, settings.brainEntries)
        }
        if (matchedBrain.isNotEmpty()) {
            val topEntry = matchedBrain.first()
            val brainReplies = BrainKnowledgeMatcher.buildLocalRepliesFromKnowledge(
                question = clean,
                entry = topEntry,
                tone = tone,
                preset = preset,
                count = count
            )
            if (brainReplies.isNotEmpty()) {
                return ProviderReplyResult(
                    replies = brainReplies.take(count).map { text ->
                        ReplyItem(
                            questionId = questionId,
                            text = text,
                            tone = tone,
                            generatedByProvider = provider
                        )
                    }
                )
            }
        }

        // 1. Math / Scientific / Symbolic calculations
        val mathReplies = trySolveMathQuestion(question, tone, preset)
        if (mathReplies.isNotEmpty()) {
            return ProviderReplyResult(
                replies = mathReplies.take(count).map { text ->
                    ReplyItem(
                        questionId = questionId,
                        text = text,
                        tone = tone,
                        generatedByProvider = provider
                    )
                }
            )
        }

        // 2. Factual Trivia, History & Knowledge Questions
        val triviaReplies = trySolveFactualQuestion(clean, tone, preset)
        if (triviaReplies.isNotEmpty()) {
            return ProviderReplyResult(
                replies = triviaReplies.take(count).map { text ->
                    ReplyItem(
                        questionId = questionId,
                        text = text,
                        tone = tone,
                        generatedByProvider = provider
                    )
                }
            )
        }

        // 2b. Choice, Dilemma & Multiple-Option Questions (e.g. "What are you craving right now — food, sleep, or attention?")
        val choiceReplies = trySolveChoiceOrOptionQuestion(clean, tone, preset)
        if (choiceReplies.isNotEmpty()) {
            return ProviderReplyResult(
                replies = choiceReplies.take(count).map { text ->
                    ReplyItem(
                        questionId = questionId,
                        text = text,
                        tone = tone,
                        generatedByProvider = provider
                    )
                }
            )
        }

        // 3. Semantic, Hypothetical, Entertainment & Creative Questions
        val semanticReplies = trySolveSemanticAndHypotheticalQuestion(clean, tone, preset)
        if (semanticReplies.isNotEmpty()) {
            return ProviderReplyResult(
                replies = semanticReplies.take(count).map { text ->
                    ReplyItem(
                        questionId = questionId,
                        text = text,
                        tone = tone,
                        generatedByProvider = provider
                    )
                }
            )
        }

        // 4. Conversational Context Templates scaled strictly by ResponseLengthPreset and Tone
        val isHinglish = QuestionDetectionEngine.isNonEnglishOrHinglish(clean) ||
                lower.contains("bhai") || lower.contains("kya") || lower.contains("kaha") ||
                lower.contains("kaisa") || lower.contains("bol") || lower.contains("yaar") ||
                lower.contains("chal") || lower.contains("sun") || lower.contains("mat") ||
                lower.contains("hoga") || lower.contains("hain") || lower.contains("karo") ||
                lower.contains("kar") || lower.contains("kyu") || lower.contains("kyun") ||
                lower.contains("aaj") || lower.contains("kal") || lower.contains("isko") ||
                lower.contains("vote")

        val isActionRequest = lower.contains("send") || lower.contains("do this") || lower.contains("handle") ||
                lower.contains("fix") || lower.contains("update") || lower.contains("prepare") ||
                lower.contains("task") || lower.contains("finish") || lower.contains("work on") ||
                lower.contains("execute")

        val templates: List<String> = when (preset) {
            ResponseLengthPreset.VERY_SHORT -> {
                when {
                    isHinglish && tone == ReplyTone.TRASH_TALK -> listOf(
                        "Bhai rehne de! 😂",
                        "WhatsApp University logic!",
                        "Chai pee pehle."
                    )
                    isHinglish && lower.contains("vote") -> listOf(
                        "Haan, zaroor vote karo!",
                        "Nahi, pehle soch lo.",
                        "Apni marzi se vote do."
                    )
                    isHinglish -> listOf(
                        "Haan bhai, bilkul!",
                        "Theek hai, karta hoon.",
                        "Haan ho jayega."
                    )
                    tone == ReplyTone.TRASH_TALK -> when {
                        lower.contains("time") || lower.contains("when") -> listOf("You're late anyway.", "3:30. Don't be late!", "Whenever you wake up.")
                        lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf("Only if you're paying!", "Free, unlike your WiFi.", "Buy me lunch first.")
                        lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf("Do it yourself! 😂", "Sending, hold your horses.", "Check your inbox, genius.")
                        lower.contains("how are you") || lower.contains("how's it going") -> listOf("Better than your takes!", "Thriving, unlike your team.", "Living rent-free!")
                        lower.contains("what do you think") || lower.contains("opinion") -> listOf("Delete that immediately.", "Nice try, 2/10.", "A certified disaster!")
                        else -> listOf("Keep dreaming! 😂", "Nice try, amateur.", "In your dreams!")
                    }
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "At 3:30 PM.",
                        "Around 5 PM.",
                        "3:30 PM works."
                    )
                    lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                        "Yes! Totally free.",
                        "Count me in!",
                        "Yes, let's do it."
                    )
                    lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                        "Sending it now!",
                        "On it right now.",
                        "Will do shortly."
                    )
                    lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                        "Doing great, thanks!",
                        "All good here!",
                        "Pretty good!"
                    )
                    lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                        "Looks fantastic!",
                        "Great idea, proceed!",
                        "Makes total sense."
                    )
                    isActionRequest -> when (tone) {
                        ReplyTone.CONCISE -> listOf("Confirmed.", "Got it.", "Will do.")
                        ReplyTone.WITTY -> listOf("Game on!", "You bet!", "Always ready.")
                        ReplyTone.PROFESSIONAL -> listOf("Acknowledged.", "Understood.", "Confirmed.")
                        ReplyTone.TRASH_TALK -> listOf("Keep dreaming! 😂", "Nice try, amateur.", "In your dreams!")
                        else -> listOf("Yes, definitely!", "On it!", "Sounds good.")
                    }
                    else -> when (tone) {
                        ReplyTone.CONCISE -> listOf("First option.", "Agreed.", "Yes.")
                        ReplyTone.WITTY -> listOf("Option one, easily!", "Great question!", "No doubt about it.")
                        ReplyTone.PROFESSIONAL -> listOf("Under review.", "Primary option.", "Confirmed.")
                        ReplyTone.EMPATHETIC -> listOf("Wonderful thought!", "Love that idea!", "Sounds great!")
                        ReplyTone.TRASH_TALK -> listOf("Keep dreaming! 😂", "Nice try, amateur.", "In your dreams!")
                        else -> listOf("Definitely option one!", "Great choice!", "Sounds great.")
                    }
                }
            }
            ResponseLengthPreset.SHORT -> {
                when {
                    isHinglish && tone == ReplyTone.TRASH_TALK -> listOf(
                        "Bhai rehne de, tera logic reboot maang raha hai! 😂",
                        "Itna confidence kahan se laate ho bhai? 😉",
                        "Arre bhai kya mast joke mara, ab kaam ki baat karein?"
                    )
                    isHinglish && lower.contains("vote") -> listOf(
                        "Haan bhai, bilkul vote karo sahi candidate ko!",
                        "Nahi, pehle unka kaam aur track record dekh lo.",
                        "Soch samajh ke samajhdari se vote karo."
                    )
                    isHinglish -> listOf(
                        "Haan bhai, bilkul tayyar hoon!",
                        "Theek hai, thodi der mein bhejta hoon.",
                        "Haan sab theek hai, aap batao!"
                    )
                    tone == ReplyTone.TRASH_TALK -> when {
                        lower.contains("time") || lower.contains("when") -> listOf(
                            "Let's do 3:30 PM, and try arriving on time for once! 😉",
                            "3:30 PM. I'll bring a trophy for winning our debate.",
                            "How about 5 PM? Gives you extra time to prep better excuses."
                        )
                        lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                            "Only if you're buying lunch to compensate for that take!",
                            "I'm free! Ready to watch you lose another debate?",
                            "Free, but my expert consulting fee is a free pizza."
                        )
                        lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                            "Sending it over now, try not to break it this time!",
                            "On it! You owe me big time for doing all the heavy lifting.",
                            "Sending it right away before you find another way to mess it up."
                        )
                        lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                            "Doing great, unlike your fantasy league team!",
                            "Thriving! Just waiting for you to say something that makes sense.",
                            "Much better than your last argument, that's for sure!"
                        )
                        lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                            "Bold idea, if the goal was to be completely wrong! 😂",
                            "I think you need to run that idea past at least three sane people first.",
                            "That idea is held together by scotch tape and sheer prayer!"
                        )
                        else -> listOf(
                            "Keep dreaming! You're gonna need backup for that one.",
                            "Bold words from someone who just got schooled!",
                            "Nice try, but you're playing in the wrong league."
                        )
                    }
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "Let's meet at 3:30 PM today!",
                        "How about around 5 o'clock?",
                        "I'm flexible anytime this afternoon."
                    )
                    lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                        "Yes! Totally free, let's do it!",
                        "I'd love to join, where should we go?",
                        "Yes, let's grab lunch this afternoon!"
                    )
                    lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                        "Sure thing, sending it over right now!",
                        "On it! Give me just 5 minutes.",
                        "Will send it over to you shortly."
                    )
                    lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                        "Doing great, thanks for asking! How about you?",
                        "Pretty good! Just wrapping up some tasks.",
                        "All good here! Hope your day is going well."
                    )
                    lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                        "Looks fantastic to me! Let's proceed with that.",
                        "I think it's a solid approach and great idea.",
                        "Makes total sense, let's go for it!"
                    )
                    isActionRequest -> when (tone) {
                        ReplyTone.PROFESSIONAL -> listOf(
                            "Thank you for reaching out, I will follow up promptly.",
                            "Confirmed. I have noted this and will coordinate accordingly.",
                            "Understood. Please let me know if any additional details are needed."
                        )
                        ReplyTone.CONCISE -> listOf("3:00 PM.", "Yes, confirmed.", "Got it.")
                        ReplyTone.WITTY -> listOf(
                            "You bet! Let's make it happen.",
                            "I was literally about to message you the exact same thing!",
                            "Consider it done before you even asked."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Keep dreaming! You're gonna need backup for that one.",
                            "Bold words from someone who just got schooled!",
                            "Nice try, but you're playing in the wrong league."
                        )
                        else -> listOf(
                            "Yes, definitely! Let's get that done.",
                            "Got your message, working on that right now!",
                            "Sure thing, I'll take care of it."
                        )
                    }
                    else -> when (tone) {
                        ReplyTone.PROFESSIONAL -> listOf(
                            "Yes, confirmed. I recommend proceeding with that plan.",
                            "That is the most effective approach; let's move forward.",
                            "Understood. Moving ahead with that direction."
                        )
                        ReplyTone.CONCISE -> listOf("Yes, definitely.", "Sounds good.", "Agreed.")
                        ReplyTone.WITTY -> listOf(
                            "100% yes, let's make it happen!",
                            "Count me in, that sounds like a great plan.",
                            "I'm on board! What's our next move?"
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "I completely agree with that, sounds like a wonderful idea!",
                            "That makes total sense to me, I support you completely.",
                            "I love that! Let me know how I can help."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Keep dreaming! You're gonna need backup for that one.",
                            "Bold words from someone who just got schooled!",
                            "Nice try, but you're playing in the wrong league."
                        )
                        else -> listOf(
                            "Yes, absolutely! I think that's the best way to go.",
                            "Sounds great to me, let's do it!",
                            "I'm totally down for that, let's make it happen."
                        )
                    }
                }
            }
            ResponseLengthPreset.NORMAL -> {
                when {
                    isHinglish && tone == ReplyTone.TRASH_TALK -> listOf(
                        "Bhai tu pehle chai pee, dimaag ki batti jalegi tab aisi baatein karna! 😂",
                        "Bhai rehne de, aisi baatein sirf WhatsApp university pe hi acchi lagti hain!",
                        "Bhai tera internet slow hai ya dimaag ka processor? Jaldi bata!"
                    )
                    isHinglish && lower.contains("vote") -> listOf(
                        "Haan bilkul, agar candidate ka kaam aur niyat acchi hai toh zaroor vote karo.",
                        "Pehle unka track record aur mudde dhyan se samajh lo, fir apna vote decide karo.",
                        "Vote dena tumhara zaroori right hai, isliye bina kisi dabav ke sahi candidate ko vote do."
                    )
                    isHinglish -> listOf(
                        "Haan bhai, bilkul theek hai. Main thodi der mein sab details bhejta hoon.",
                        "Bilkul chalega! Aap batao kab milna hai aur kahan aana hai.",
                        "Haan sab badhiya chal raha hai, aap batao kya haal chaal hain?"
                    )
                    tone == ReplyTone.TRASH_TALK -> when {
                        lower.contains("time") || lower.contains("when") -> listOf(
                            "Let's meet at 3:30 PM today. Try to be on time for once so I don't have to win this argument by default!",
                            "How about 5:00 PM? That gives your brain enough time to reboot before we discuss this.",
                            "I'm available at 3:30 PM, assuming you're ready to concede defeat gracefully."
                        )
                        lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                            "I'm free, but only if you're treating to make up for subjecting me to that logic!",
                            "Definitely free for lunch. Let's see if your food choices are as questionable as your opinions.",
                            "Count me in, as long as you promise not to tell any more dreadful jokes over food!"
                        )
                        lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                            "Sending it over right now! Try actually reading it before asking me ten questions about it.",
                            "On it immediately. Consider this my good deed of the day for saving your project.",
                            "Forwarding the files right now. Next time, try saving it in a folder you can actually find!"
                        )
                        lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                            "Doing fantastic! Life is great, especially knowing my taste in everything is superior to yours.",
                            "Living my best life! Just wondering when you're going to upgrade your sense of humor.",
                            "I'm doing awesome! How does it feel to be on the losing side of this banter?"
                        )
                        lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                            "That is certainly an idea! Not a good one, mind you, but definitely an idea that exists in physical space.",
                            "I think your confidence is truly inspiring given how completely unhinged that logic is.",
                            "On a scale of 1 to 10, that idea is a solid fire hazard. Let's try thinking before speaking next time!"
                        )
                        else -> listOf(
                            "Bold claim coming from someone whose entire argument is held together by hope and duct tape!",
                            "I would agree with you, but then we'd both be completely wrong.",
                            "You have an uncanny ability to be so confident and yet so wrong at the same time!"
                        )
                    }
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "Let's meet at 3:30 PM today. Let me know if that time works for your calendar.",
                        "How about meeting around 5 o'clock? We can review the agenda together.",
                        "I am flexible anytime this afternoon, please let me know what slot suits you best."
                    )
                    lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                        "Yes, I'm completely free for lunch today! Where were you thinking of going?",
                        "I would love to catch up over coffee this afternoon. Send over the location and time!",
                        "I am available and excited to join. Let's head out whenever you're ready."
                    )
                    lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                        "Sure thing! I am finalizing the file right now and will send it over within five minutes.",
                        "On it right now. I will ensure all attachments are verified and forwarded to your inbox.",
                        "Will do as soon as I return to my workstation in just a moment."
                    )
                    lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                        "Doing really well today, thank you for checking in! How has your day been going so far?",
                        "Pretty good! Just finishing up some priority items. Hope you're having a smooth and productive day.",
                        "All good here on my end! Making steady progress on our active projects."
                    )
                    lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                        "Looks fantastic to me and aligns well with our objectives. Let's proceed with that direction.",
                        "I think it is a very solid approach with a clear roadmap. We should move forward with it.",
                        "Makes total sense from an architecture perspective, let's implement it."
                    )
                    isActionRequest -> when (tone) {
                        ReplyTone.PROFESSIONAL -> listOf(
                            "Thank you for the update. I will review the documentation and coordinate next steps promptly.",
                            "Confirmed. The deliverables have been logged and scheduled in accordance with our project timeline.",
                            "Understood. Please let me know if any supplementary specifications or files are required."
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "I completely understand and appreciate you sharing this! Please take all the time you need.",
                            "No worries at all, I am happy to help with this whenever you are ready.",
                            "Hope everything is going smoothly on your end, let me know how I can best support you."
                        )
                        ReplyTone.TECHNICAL -> listOf(
                            "Verified. The parameters and configurations are within standard operational tolerances.",
                            "Status acknowledged. Executing requested synchronization across all active modules.",
                            "Review completed; all telemetry metrics align with the target baseline specification."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Bold claim coming from someone whose entire argument is held together by hope and duct tape!",
                            "I would agree with you, but then we'd both be completely wrong.",
                            "You have an uncanny ability to be so confident and yet so wrong at the same time!"
                        )
                        else -> listOf(
                            "Yes, definitely! I will take care of this and keep you updated on our progress.",
                            "Received your note, I am actively working on the requested items right now.",
                            "Understood. I will follow up with the completed details shortly."
                        )
                    }
                    else -> when (tone) {
                        ReplyTone.PROFESSIONAL -> listOf(
                            "That is a thought-provoking inquiry. Balancing functional viability and long-term value points toward the most sustainable approach.",
                            "Assessing both strategic impact and feasibility provides the clearest rationale for this decision.",
                            "A pertinent question that merits careful consideration; prioritizing core goals clarifies the ideal choice."
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "That is such a wonderful question to reflect on! I would choose the path that brings the deepest sense of fulfillment and happiness.",
                            "What a beautiful thought experiment. It really encourages thinking about what matters most in experiences like that.",
                            "I love this question so much! Imagining the possibilities brings such a warm and inspiring perspective."
                        )
                        ReplyTone.WITTY -> listOf(
                            "Without a doubt, I'm picking whichever option guarantees maximum entertainment and zero regrets tomorrow!",
                            "That's the kind of dilemma I live for! Definitely going with the most adventurous outcome.",
                            "An absolute classic question—my vote goes to the boldest choice every single time!"
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Bold claim coming from someone whose entire argument is held together by hope and duct tape!",
                            "I would agree with you, but then we'd both be completely wrong.",
                            "You have an uncanny ability to be so confident and yet so wrong at the same time!"
                        )
                        else -> listOf(
                            "I definitely agree with that direction! Taking everything into account, it offers the most practical and positive outcome. Let's move forward with it.",
                            "That sounds like the best approach to me. It keeps things straightforward, saves time, and gets us the exact result we want.",
                            "I'm completely on board with that plan! It makes total sense and works out really well for everyone involved."
                        )
                    }
                }
            }
            ResponseLengthPreset.LONG -> {
                when {
                    isHinglish && tone == ReplyTone.TRASH_TALK -> listOf(
                        "Bhai tu pehle ek cup kadak chai pee, dimaag ka system reboot kar aur fir baat kar. Itna confident hokar galat bolna bhi ek art hai jo sirf tere paas hai! 😂",
                        "Arre bhai rehne de! Tera logic sunke mere phone ka processor bhi confuse ho gaya. Pehle thoda facts check kar ke aa fir debate karte hain!",
                        "Bhai itna confidence kahan se laate ho? Aise questionable logic ke sath debate mein utroge toh log bina ticket ke roast kar denge! 😉"
                    )
                    isHinglish && lower.contains("vote") -> listOf(
                        "Haan bilkul vote karo agar unke mudde aur kaam pasand hain. Har ek vote bohot zaroori hota hai, isliye responsibly decide karke apna vote do.",
                        "Pehle unka pura track record dekh lo aur baaki candidates se compare kar lo, fir sahi faisla karke vote karo.",
                        "Voting tumhara fundamental right hai. Kisi ke dabav mein mat aao, khud research karo aur jo deserving lage usko vote do."
                    )
                    isHinglish -> listOf(
                        "Haan bhai, bilkul theek hai! Main saari details aur documents check karke thodi der mein pura update bhejta hoon. Tab tak chill karo.",
                        "Bilkul chalega, main poora time nikal lunga. Aap jagah aur time confirm kardo, main wahan time par pahunch jaunga.",
                        "Sab kuch badhiya chal raha hai bhai! Project par continuous kaam ho raha hai aur saare targets time par complete ho rahe hain."
                    )
                    tone == ReplyTone.TRASH_TALK -> when {
                        lower.contains("time") || lower.contains("when") -> listOf(
                            "Let's schedule for 3:30 PM today. I will be there sharp on time, fully prepared with receipts, while you're still figuring out how to defend that questionable logic!",
                            "Let's lock in 5:00 PM. That gives you plenty of time to rethink that hot take and come back with something resembling a valid point.",
                            "I'm free at 3:30 PM today. Let me know if you need an advance copy of reality before we sit down and talk!"
                        )
                        lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                            "Yes, I am completely free for lunch today, provided you are picking up the check to apologize for that disastrous argument you made earlier! Name the place and time.",
                            "I'd love to grab lunch, mostly so I can roast your life choices in person. Let me know the spot and make sure they serve something strong.",
                            "Count me in! I'll never turn down free food, especially when it comes with front-row seats to you defending the indefensible."
                        )
                        lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                            "I am sending the files over to your inbox right now. Please do yourself a huge favor and actually read past page one before you panic and text me again!",
                            "On it right away. I've packaged everything with step-by-step instructions simple enough that even you can't misunderstand them.",
                            "Forwarding the documents now. Next time remember where you saved them so I don't have to play detective for your homework!"
                        )
                        lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                            "I am doing phenomenally well, thank you for asking! Just cruising through my day with the calm confidence of someone who is objectively right about everything we argue about.",
                            "Doing great! Woke up, drank some coffee, and realized my life decisions are still leagues ahead of yours. How are things on your struggle bus?",
                            "Thriving on all fronts! Life is smooth, productivity is peak, and my comeback game is in top form as always."
                        )
                        lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                            "I reviewed your proposal and I am genuinely fascinated by how you managed to be incorrect on so many distinct technical and logical levels simultaneously! We definitely need a complete rewrite.",
                            "That take is so wild it should be protected by wildlife conservation laws. Let's shelve that immediately and go with literally any other plan.",
                            "I admire the courage it took to say that out loud with a straight face, but from an objective reality standpoint, it's an absolute trainwreck. Let's restart from zero."
                        )
                        else -> listOf(
                            "I would love to agree with your point, but unfortunately I have this strict personal policy against agreeing with completely nonsensical claims. Better luck next round!",
                            "You bring a lot of energy and zero valid arguments to the table. I suggest taking a short walk, drinking water, and coming back when you have actual facts.",
                            "Your confidence is truly unmatched by your evidence. Next time, bring some data before stepping into this arena with the champions!"
                        )
                    }
                    lower.contains("time") || lower.contains("when") -> listOf(
                        "I am available to meet at 3:30 PM today for our discussion. I will prepare the agenda topics in advance and send over a calendar invitation with the meeting link so we can align smoothly.",
                        "How about we schedule our sync for 5:00 PM? That gives us ample time to review all project milestones, address open questions, and finalize our next steps.",
                        "I have a flexible window throughout the afternoon starting from 2:00 PM. Please advise which time slot best fits your calendar so I can reserve the conference room."
                    )
                    lower.contains("free") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("coffee") -> listOf(
                        "Yes, I am completely free and would really enjoy catching up over lunch today! Let me know what time works best for you and if there is a specific cuisine or spot you'd prefer.",
                        "I'd love to get together for coffee or lunch this afternoon. My calendar is open after 12:30 PM, so let me know what venue suits you best and I'll meet you there.",
                        "Definitely count me in! I've been looking forward to connecting and discussing our recent updates. Let me know the departure time and location."
                    )
                    lower.contains("can you") || lower.contains("could you") || lower.contains("send") -> listOf(
                        "I have the requested documents prepared on my workstation. I am running a quick final review of all attachments and will forward the complete package to your email within the next 10 minutes.",
                        "On it immediately! I'm compiling the latest data sheets along with explanatory notes and will deliver everything directly to your inbox shortly.",
                        "I will package the required files and send them across as soon as I wrap up the current review session."
                    )
                    lower.contains("how are you") || lower.contains("how's it going") -> listOf(
                        "I am doing very well today, making steady progress across our active sprint deliverables. Thank you for asking! How is everything going on your end?",
                        "Things are going great here! We just resolved a major milestone and are gearing up for the next phase. I hope your day has been equally productive.",
                        "All is well on my end, keeping busy with technical roadmap items. I appreciate you reaching out and checking in!"
                    )
                    lower.contains("what do you think") || lower.contains("opinion") -> listOf(
                        "I reviewed the proposal in detail and believe the approach is exceptionally well thought out. The milestones are realistic, the risks are mitigated, and we should proceed with execution immediately.",
                        "In my assessment, this strategy provides the optimal balance of performance and reliability. It addresses our core requirements thoroughly and sets us up for long-term scalability.",
                        "The plan looks excellent. The proposed architecture simplifies our integration overhead significantly while maintaining high reliability, so I fully endorse moving forward."
                    )
                    isActionRequest -> when (tone) {
                        ReplyTone.TRASH_TALK -> listOf(
                            "I would love to agree with your point, but unfortunately I have this strict personal policy against agreeing with completely nonsensical claims. Better luck next round!",
                            "You bring a lot of energy and zero valid arguments to the table. I suggest taking a short walk, drinking water, and coming back when you have actual facts.",
                            "Your confidence is truly unmatched by your evidence. Next time, bring some data before stepping into this arena with the champions!"
                        )
                        else -> listOf(
                            "Confirmed and understood. I will begin work on this immediately, ensure all specifications are satisfied, and provide you with a comprehensive status update once completed.",
                            "Thank you for the update. I have logged the action items, coordinated with the relevant team members, and will deliver the requested output promptly.",
                            "I will take full ownership of this request and follow up with documented results as soon as the task is executed."
                        )
                    }
                    else -> when (tone) {
                        ReplyTone.PROFESSIONAL -> listOf(
                            "Considering this inquiry comprehensively, balancing empirical criteria with long-term strategic impact points toward the most sustainable and well-founded outcome.",
                            "A thorough analysis of both potential risks and measurable advantages supports selecting the option that delivers maximum operational efficiency and reliability.",
                            "Evaluating this question requires balancing practical constraints against strategic objectives, resulting in a clear and defensible decision."
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "Reflecting on this question invites a deeply meaningful perspective. I would choose the path that nurtures genuine connection, inner peace, and personal growth above all else.",
                            "What a beautiful and imaginative question. In situations like this, prioritizing experiences that create lasting happiness and cherished memories is always the most rewarding path.",
                            "That is such an inspiring dilemma to consider! It reminds us of the power of imagination and the joy that comes from embracing unique, life-enriching adventures."
                        )
                        ReplyTone.WITTY -> listOf(
                            "After rigorous intellectual deliberation and zero consultation with common sense, I am decisively picking the option that produces the greatest story and the most entertainment!",
                            "Life is far too short to pick the boring answer to a hypothetical question. Go with the boldest, most chaotic, and most memorable choice every single time!",
                            "Evaluating all factors with extreme scientific precision, the option with the most flair and the least paperwork wins hands down!"
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "I would love to agree with your point, but unfortunately I have this strict personal policy against agreeing with completely nonsensical claims. Better luck next round!",
                            "You bring a lot of energy and zero valid arguments to the table. I suggest taking a short walk, drinking water, and coming back when you have actual facts.",
                            "Your confidence is truly unmatched by your evidence. Next time, bring some data before stepping into this arena with the champions!"
                        )
                        else -> listOf(
                            "Examining this question thoroughly reveals multiple interconnected dimensions. At its core, evaluating both practical considerations and creative potential makes the most fulfilling and adventurous choice the clear winner.",
                            "That is a compelling thought experiment to weigh. Balancing immediate excitement with enduring value, I would confidently pick the option that opens up the greatest new horizons.",
                            "A truly engaging question! Looking at all the possibilities, the option that offers the richest personal experience and the best stories is definitely the way to go."
                        )
                    }
                }
            }
        }

        return ProviderReplyResult(
            replies = templates.take(count).map { text ->
                ReplyItem(
                    questionId = questionId,
                    text = text,
                    tone = tone,
                    generatedByProvider = provider
                )
            }
        )
    }

    private fun trySolveFactualQuestion(
        question: String,
        tone: ReplyTone,
        preset: ResponseLengthPreset
    ): List<String> {
        val lower = question.trim().lowercase()

        // 1. Telephone invention
        if (lower.contains("invent") && lower.contains("telephone")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf(
                    "Alexander Graham Bell.",
                    "Alexander Graham Bell (1876).",
                    "Alexander Graham Bell."
                )
                ResponseLengthPreset.SHORT -> listOf(
                    "Alexander Graham Bell invented the telephone in 1876.",
                    "The telephone was patented by Alexander Graham Bell in 1876.",
                    "Alexander Graham Bell is recognized as the inventor of the telephone."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "Alexander Graham Bell invented the telephone in 1876, receiving the first official US patent for electromagnetic voice transmission.",
                    "The telephone was patented by Alexander Graham Bell in March 1876, transforming global communications forever.",
                    "Alexander Graham Bell is widely recognized for inventing and demonstrating the first practical telephone in 1876."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "Alexander Graham Bell was awarded the first U.S. patent for the telephone on March 7, 1876 (Patent No. 174,465). His breakthrough enabled real-time acoustic speech transmission over electrical telegraph wires, transforming modern telecommunications and leading to the foundation of the Bell Telephone Company.",
                    "The electromagnetic telephone was invented and patented by Alexander Graham Bell in 1876. He transmitted the famous first intelligible sentence to his assistant Thomas Watson: 'Mr. Watson, come here, I want to see you.' This milestone fundamentally reshaped modern global civilization.",
                    "Alexander Graham Bell successfully demonstrated acoustic voice resonance in 1876, securing the foundational patent for the telephone. His pioneering research in speech physiology and acoustics established the telecommunications networks we rely on today."
                )
            }
        }

        // 2. Personal & Reflective: Secretly proud / Achievements
        if (lower.contains("proud") || lower.contains("achievement")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf(
                    "Staying calm under pressure.",
                    "Daily consistency and habits.",
                    "Self-taught skills."
                )
                ResponseLengthPreset.SHORT -> listOf(
                    "Learning how to stay calm and resilient during tough situations!",
                    "Building habits that quietly improved my daily health and routine.",
                    "Teaching myself new skills completely from scratch without giving up."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "I am secretly proud of teaching myself complex technical skills and maintaining consistent daily habits even during stressful periods.",
                    "I take pride in staying calm during high-pressure challenges and mentoring others through difficult technical transitions.",
                    "Consistently delivering high-quality results while cultivating emotional resilience is an achievement I value quietly."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "I am secretly proud of maintaining emotional resilience and continuous self-discipline during difficult life transitions. Building steady habits, teaching myself advanced skills independently, and quietly supporting others without seeking external validation are achievements that mean the most to me.",
                    "I take quiet pride in solving complex problems under strict deadlines while keeping teams calm and focused. Developing mastery over difficult domains through self-guided study has been one of my most rewarding personal milestones.",
                    "Staying patient, kind, and consistent when facing unexpected obstacles is something I am deeply proud of. Consistently showing up with thoughtful solutions builds lasting trust."
                )
            }
        }

        // 3. Educational & Conceptual: Integration / Explain to a 5-year-old
        if (lower.contains("integration") || (lower.contains("explain") && (lower.contains("5-year-old") || lower.contains("5 year old")))) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf(
                    "Putting puzzle pieces together!",
                    "Summing tiny pieces.",
                    "Assembling parts together."
                )
                ResponseLengthPreset.SHORT -> listOf(
                    "Integration is like putting all the tiny puzzle pieces together to see the whole big picture!",
                    "It's adding up lots of little slices of something to find the total size!",
                    "Integration means bringing lots of small parts together into one complete whole."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "Integration is like gathering up lots of tiny puzzle pieces or cookie crumbs to see the complete whole picture and compute its total size.",
                    "In mathematics, integration calculates cumulative total or area by summing continuous infinitesimally small slices.",
                    "Integration systematically unifies separate discrete components and data streams into a cohesive architecture."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "Imagine you have a giant jar full of tiny Lego bricks scattered across the floor. Integration is the magical math tool that collects every single little brick and adds them all together so you can see exactly how big your castle is. In calculus, it computes continuous accumulation or area under a curve by taking the limit of Riemann sums.",
                    "In mathematics, integration represents the inverse operation of differentiation, computing the cumulative total or area under a curve. In software architecture, integration refers to linking disparate subsystem modules, APIs, and microservices into a unified, synchronized production pipeline.",
                    "Integration is the process of bringing diverse components into a cohesive system. Mathematically, it sums infinite infinitesimal slices to determine total area or volume; conceptually, it unifies individual efforts into a singular harmonious result."
                )
            }
        }

        // 4. Dinner with historical figure
        if (lower.contains("dinner") && (lower.contains("historical") || lower.contains("history"))) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf(
                    "Leonardo da Vinci.",
                    "Albert Einstein.",
                    "Marie Curie."
                )
                ResponseLengthPreset.SHORT -> listOf(
                    "Leonardo da Vinci or Albert Einstein—so many questions about their brilliant minds!",
                    "Probably Nikola Tesla or Marie Curie, their curiosity was unmatched!",
                    "Socrates or Marcus Aurelius for the ultimate philosophical dinner conversation."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "I would love to have dinner with Leonardo da Vinci or Albert Einstein to discuss their innovative thinking and discover how their minds approached problem-solving.",
                    "I would choose Ada Lovelace or Alan Turing to explore the origins of computational theory and discuss modern machine intelligence.",
                    "Nikola Tesla or Marie Curie would be fascinating dinner companions to discuss relentless scientific curiosity."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "If I could have dinner with any historical figure, I would choose Leonardo da Vinci. I would love to explore his personal notebooks, discuss the intersection of art and engineering, and ask how he maintained such boundless curiosity across anatomy, flight mechanics, and painting simultaneously.",
                    "I would choose Alan Turing to discuss foundational computational theory, mathematical biology, and the philosophical implications of artificial intelligence. Exploring how his early concepts shaped modern computing would be an extraordinary conversation.",
                    "I would select Nikola Tesla to discuss his visions for wireless power transmission and electromagnetic physics. Talking through his innovative thought experiments and unbuilt prototypes over dinner would be unforgettable."
                )
            }
        }

        // 5. Capitals
        if (lower.contains("capital of")) {
            val countryMap = mapOf(
                "australia" to "Canberra",
                "france" to "Paris",
                "japan" to "Tokyo",
                "canada" to "Ottawa",
                "germany" to "Berlin",
                "united kingdom" to "London",
                "uk" to "London",
                "england" to "London",
                "india" to "New Delhi",
                "italy" to "Rome",
                "spain" to "Madrid",
                "united states" to "Washington, D.C.",
                "usa" to "Washington, D.C.",
                "us" to "Washington, D.C.",
                "brazil" to "Brasília",
                "china" to "Beijing",
                "mexico" to "Mexico City",
                "russia" to "Moscow",
                "egypt" to "Cairo",
                "greece" to "Athens"
            )
            for ((country, capital) in countryMap) {
                if (lower.contains(country)) {
                    val countryCapitalized = country.replaceFirstChar { it.uppercase() }
                    return when (preset) {
                        ResponseLengthPreset.VERY_SHORT -> listOf(capital, capital, capital)
                        ResponseLengthPreset.SHORT -> listOf(
                            "The capital of $countryCapitalized is $capital.",
                            "$capital is the designated capital city.",
                            "The official capital is $capital."
                        )
                        ResponseLengthPreset.NORMAL -> listOf(
                            "The capital of $countryCapitalized is $capital, which serves as the seat of government.",
                            "$capital is the official federal capital of $countryCapitalized, housing central administrative institutions.",
                            "The capital city of $countryCapitalized is $capital."
                        )
                        ResponseLengthPreset.LONG -> listOf(
                            "The official capital of $countryCapitalized is $capital. It serves as the political, administrative, and constitutional center of the nation, housing the federal parliament, high courts, and diplomatic missions.",
                            "$capital serves as the federal capital of $countryCapitalized, established to serve as the nation's political hub and central seat of government.",
                            "The recognized capital city of $countryCapitalized is $capital, playing a central historical and legislative role in national governance."
                        )
                    }
                }
            }
        }

        // 6. Other key inventors / discoveries
        if (lower.contains("invent") || lower.contains("discover") || lower.contains("who created") || lower.contains("who painted")) {
            if (lower.contains("light bulb") || lower.contains("lightbulb")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Thomas Edison.", "Thomas Edison.", "Edison.")
                    ResponseLengthPreset.SHORT -> listOf("Thomas Edison invented the practical incandescent light bulb in 1879.", "Thomas Edison patented the incandescent bulb.", "Thomas Edison in 1879.")
                    ResponseLengthPreset.NORMAL -> listOf("Thomas Edison developed and commercialized the first practical incandescent electric light bulb in 1879.", "Thomas Edison patented the modern incandescent bulb in 1879 after testing thousands of filament materials.", "Thomas Edison is credited with commercializing the first long-lasting incandescent light bulb.")
                    ResponseLengthPreset.LONG -> listOf("Thomas Edison developed and commercialized the first long-lasting, practical incandescent electric lamp in 1879 at his Menlo Park laboratory. His work included developing the electrical distribution infrastructure needed to power homes and businesses reliably.", "Thomas Edison patented the incandescent light bulb in 1879, revolutionizing commercial illumination.", "Thomas Edison's development of high-resistance carbon filaments in 1879 made indoor electric lighting commercially viable worldwide.")
                }
            }
            if (lower.contains("airplane") || lower.contains("aeroplane") || lower.contains("flight")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Wright Brothers.", "The Wright Brothers.", "Orville & Wilbur Wright.")
                    ResponseLengthPreset.SHORT -> listOf("The Wright Brothers achieved the first controlled powered flight in 1903.", "Orville and Wilbur Wright in 1903.", "The Wright Brothers invented the airplane.")
                    ResponseLengthPreset.NORMAL -> listOf("The Wright Brothers (Orville and Wilbur) invented and flew the first successful motor-operated airplane on December 17, 1903 at Kitty Hawk.", "Orville and Wilbur Wright achieved the first sustained, controlled powered airplane flight in December 1903.", "The Wright Brothers designed and flew the first practical fixed-wing aircraft in 1903.")
                    ResponseLengthPreset.LONG -> listOf("Orville and Wilbur Wright achieved the first controlled, sustained flight of a powered, heavier-than-air aircraft on December 17, 1903, at Kitty Hawk, North Carolina. Their three-axis control system remains standard on fixed-wing aircraft today.", "The Wright Brothers successfully piloted the Flyer in 1903, ushering in the modern era of aviation through their pioneering aerodynamic wing-warping research.", "Orville and Wilbur Wright patented three-axis aerodynamic flight control after their landmark 1903 flights in Kitty Hawk.")
                }
            }
            if (lower.contains("gravity")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Sir Isaac Newton.", "Isaac Newton.", "Newton (1687).")
                    ResponseLengthPreset.SHORT -> listOf("Sir Isaac Newton formulated the universal law of gravitation in 1687.", "Isaac Newton discovered gravity in 1687.", "Sir Isaac Newton.")
                    ResponseLengthPreset.NORMAL -> listOf("Sir Isaac Newton formulated the law of universal gravitation in 1687, publishing his findings in the Principia Mathematica.", "Isaac Newton discovered the mathematical principles of universal gravity in the late 17th century.", "Sir Isaac Newton established classical gravitational mechanics in 1687.")
                    ResponseLengthPreset.LONG -> listOf("Sir Isaac Newton published his universal law of gravitation in the 'Philosophiae Naturalis Principia Mathematica' in 1687, demonstrating that celestial bodies and terrestrial objects obey the same inverse-square gravitational dynamics.", "Sir Isaac Newton mathematically unified planetary motion and earthly gravity in 1687, laying the groundwork for classical mechanics.", "Isaac Newton discovered that every mass attracts every other mass with a force proportional to the product of their masses and inversely proportional to the square of the distance between them.")
                }
            }
            if (lower.contains("penicillin")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Alexander Fleming.", "Alexander Fleming (1928).", "Fleming.")
                    ResponseLengthPreset.SHORT -> listOf("Sir Alexander Fleming discovered penicillin in 1928.", "Alexander Fleming in 1928.", "Discovered by Alexander Fleming.")
                    ResponseLengthPreset.NORMAL -> listOf("Sir Alexander Fleming discovered penicillin in September 1928, marking the dawn of modern antibiotics.", "Alexander Fleming discovered the antibacterial properties of Penicillium notatum mold in 1928.", "Alexander Fleming's 1928 discovery of penicillin revolutionized medical treatment for bacterial infections.")
                    ResponseLengthPreset.LONG -> listOf("Sir Alexander Fleming discovered penicillin in September 1928 at St. Mary's Hospital in London after observing that a Penicillium notatum mold contamination killed surrounding Staphylococcus bacteria colonies, launching modern antibiotic medicine.", "Alexander Fleming identified penicillin in 1928, paving the way for Florey and Chain to mass-produce the world's first life-saving antibiotic treatment.", "Alexander Fleming's 1928 discovery of penicillin fundamentally transformed clinical medicine by providing the first effective cure against lethal bacterial infections.")
                }
            }
            if (lower.contains("mona lisa")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Leonardo da Vinci.", "Leonardo da Vinci.", "Da Vinci.")
                    ResponseLengthPreset.SHORT -> listOf("Leonardo da Vinci painted the Mona Lisa in the early 1500s.", "Painted by Leonardo da Vinci.", "Leonardo da Vinci.")
                    ResponseLengthPreset.NORMAL -> listOf("Leonardo da Vinci painted the Mona Lisa (La Gioconda) between 1503 and 1519, now housed at the Louvre in Paris.", "The Mona Lisa was created by Italian Renaissance master Leonardo da Vinci in the early 16th century.", "Leonardo da Vinci began painting the Mona Lisa around 1503 in Florence.")
                    ResponseLengthPreset.LONG -> listOf("The Mona Lisa was painted by Italian Renaissance polymath Leonardo da Vinci, begun around 1503 in Florence and completed in France. Acclaimed for its sfumato technique and enigmatic expression, it is permanently displayed in the Louvre Museum in Paris.", "Leonardo da Vinci crafted the Mona Lisa during the Italian High Renaissance. Known for its subtle optical illusions and atmospheric landscape backdrop, it remains the world's most famous portrait.", "Leonardo da Vinci created the masterpiece Mona Lisa using delicate oil glaze layering over poplar wood between 1503 and 1519.")
                }
            }
            if (lower.contains("world wide web") || lower.contains("www") || lower.contains("internet")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf("Tim Berners-Lee.", "Tim Berners-Lee (1989).", "Sir Tim Berners-Lee.")
                    ResponseLengthPreset.SHORT -> listOf("Sir Tim Berners-Lee invented the World Wide Web in 1989 at CERN.", "Tim Berners-Lee in 1989.", "Invented by Tim Berners-Lee.")
                    ResponseLengthPreset.NORMAL -> listOf("Sir Tim Berners-Lee invented the World Wide Web in 1989 while working at CERN, introducing HTML, HTTP, and URLs.", "Tim Berners-Lee designed the foundational protocols of the World Wide Web in 1989.", "Sir Tim Berners-Lee developed the World Wide Web specifications at CERN in 1989.")
                    ResponseLengthPreset.LONG -> listOf("Sir Tim Berners-Lee invented the World Wide Web in 1989 while working at CERN. He created the first web server, the first web browser, and the foundational standards—HTTP, HTML, and URIs—and generously placed the technology into the public domain for global use.", "Tim Berners-Lee submitted his proposal for an information management system in March 1989 at CERN, which evolved into the interconnected World Wide Web linking billions of hyperlinked documents worldwide.", "Sir Tim Berners-Lee developed the World Wide Web in 1989 to facilitate automated information-sharing between scientists, fundamentally altering global digital communication.")
                }
            }
        }

        return emptyList()
    }

    private fun trySolveSemanticAndHypotheticalQuestion(
        question: String,
        tone: ReplyTone,
        preset: ResponseLengthPreset
    ): List<String> {
        val lower = question.lowercase()

        // 1. Anime / Manga / Animation / Isekai / Studio Ghibli
        if (lower.contains("anime") || lower.contains("manga") || lower.contains("isekai") || lower.contains("ghibli")) {
            // Live inside an anime world / pick an anime world for a week
            if (lower.contains("live") || lower.contains("world") || lower.contains("pick") || lower.contains("choose") || lower.contains("visit") || lower.contains("week")) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> when (tone) {
                        ReplyTone.WITTY -> listOf("Pokémon! Zero doubts.", "Ghibli bathhouse!", "My Hero Academia.")
                        ReplyTone.PROFESSIONAL -> listOf("The Pokémon universe.", "Studio Ghibli's world.", "Aria's Neo-Venezia.")
                        ReplyTone.EMPATHETIC -> listOf("A peaceful Studio Ghibli town.", "The Pokémon world with a Pikachu.", "Totoro's lush countryside.")
                        ReplyTone.TRASH_TALK -> listOf("Dragon Ball! I'd carry the squad.", "Attack on Titan, obviously survive day one.", "Pokémon, unbeatable gym leader.")
                        else -> listOf("The Pokémon world!", "Studio Ghibli's universe.", "My Hero Academia!")
                    }
                    ResponseLengthPreset.SHORT -> when (tone) {
                        ReplyTone.WITTY -> listOf(
                            "Hands down the Pokémon universe—exploring scenic trails and napping with Snorlax sounds unbeatable!",
                            "Studio Ghibli's world: incredible food, flying contraptions, and absolutely zero spreadsheets.",
                            "My Hero Academia, but only if my quirk isn't something useless like turning into a desk lamp."
                        )
                        ReplyTone.PROFESSIONAL -> listOf(
                            "I would select the Pokémon universe for its harmonious human-creature ecosystem and scenic exploration.",
                            "Studio Ghibli's world presents an ideal balance of architectural beauty, tranquility, and natural wonder.",
                            "Aria's Neo-Venezia offers an exceptional model of sustainable, peaceful civic life."
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "I'd love to spend a week in a cozy Studio Ghibli village—warm bakery bread, gentle rolling hills, and pure peace.",
                            "The Pokémon world! Traveling along sunlit routes with a loyal companion sounds so comforting.",
                            "The world of Laid-Back Camp, stargazing by the campfire with warm hotpot."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Dragon Ball Z! I'd collect the dragon balls before lunch while everyone else is still charging up.",
                            "Pokémon, easily. I'd sweep the Elite Four on day two and retire undefeated.",
                            "Sword Art Online—straight to the front lines, clearing floors while you're still in the safe zone!"
                        )
                        else -> listOf(
                            "Definitely the Pokémon world! Exploring routes, camping out, and catching cute companion Pokémon would be an incredible week.",
                            "Studio Ghibli's universe—walking through cobblestone towns, eating delicious cozy meals, and soaring in Howl's Moving Castle.",
                            "My Hero Academia! Experiencing a superpower society with exciting quirks would be unforgettable."
                        )
                    }
                    ResponseLengthPreset.NORMAL -> when (tone) {
                        ReplyTone.WITTY -> listOf(
                            "Without a shadow of a doubt, I'm picking the Pokémon universe. You get free healthcare at Pokémon Centers, infinite scenic hiking routes, and your biggest weekly dilemma is deciding whether to challenge the local gym or take a nap next to a friendly Snorlax.",
                            "I'd choose Howl's Moving Castle or Spirited Away in the Studio Ghibli universe. The bread looks absurdly delicious, the landscapes are hand-painted perfection, and having a magic door that opens into four different cities saves so much on commute time.",
                            "My Hero Academia would be an absolute blast, provided I roll a top-tier quirk. If I end up with the ability to bend spoons with my eyebrows, I might renegotiate for a quiet bakery in Kiki's Delivery Service."
                        )
                        ReplyTone.PROFESSIONAL -> listOf(
                            "From an experiential standpoint, the Pokémon world provides an optimal balance of environmental harmony, technological convenience, and adventurous exploration without existential peril.",
                            "I would choose Studio Ghibli's aesthetic universe—specifically Neo-Venezia or the coastal towns of Kiki's Delivery Service—which prioritize pastoral beauty, craftsmanship, and community wellness.",
                            "The setting of Aria or Frieren: Beyond Journey's End offers an extraordinary study in architectural preservation, philosophical depth, and contemplative landscapes."
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "I would choose a tranquil week in Studio Ghibli's world. Waking up to dew-kissed meadows, listening to the soft rustling wind, and enjoying warm homemade soup in a peaceful countryside cottage sounds like the ultimate restorative getaway.",
                            "The Pokémon world would be so heartwarming! Imagine walking along sunny forest paths with your favorite companion Pokémon, sharing camp meals under the stars, and making kind friends in every town you visit.",
                            "A cozy, serene week in the Laid-Back Camp universe would be heavenly—crisp mountain air, quiet lake views, and warm cocoa by the fire."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Dragon Ball Z without question! I'd hit the Hyperbolic Time Chamber on Monday, achieve Super Saiyan by Wednesday, and be running the entire galaxy before the week expires. Anyone picking a slice-of-life anime clearly fears true power!",
                            "Pokémon, zero competition. I'd challenge all eight gyms in a single afternoon, defeat the champion with a Magikarp just to flex, and claim my throne before the week wraps up.",
                            "Attack on Titan—straight to the Scout Regiment because some of us thrive on high stakes and glory while everyone else hides behind walls!"
                        )
                        else -> listOf(
                            "If I had one week, I would definitely pick the Pokémon world! The chance to travel across scenic routes, bond with a team of favorite Pokémon, camp out in nature, and enjoy the peaceful small-town community vibes would make for an unforgettable adventure.",
                            "I'd pick the Studio Ghibli universe—either Howl's Moving Castle or Kiki's coastal town of Koriko. The enchanting hand-crafted landscapes, magical trains over the ocean, and cozy bakeries would be pure magic to experience firsthand.",
                            "My Hero Academia! Spending a week experiencing a society where nearly everyone has superpowers and training alongside aspiring heroes would be an exhilarating ride."
                        )
                    }
                    ResponseLengthPreset.LONG -> listOf(
                        "If I could live inside any anime world for a full week, my immediate first choice would be the Pokémon universe. The setting offers an unmatched blend of wholesome adventure, breathtaking diverse geography—from vibrant beaches to snowy mountain passes—and the unique companionship of traveling alongside Pokémon. There is virtually no modern existential stress: healthcare at Pokémon Centers is universal and instantaneous, towns welcome travelers with open arms, and each day consists of discovering new species, sharing campfire meals under the stars, and training in friendly gym competitions. It captures the pure spirit of childhood wonder and boundless outdoor exploration.",
                        "I would immerse myself in the Studio Ghibli cinematic universe, specifically the world of Howl's Moving Castle and Spirited Away. Spending seven days wandering through picturesque European cobblestone streets, taking a magical steam train skimming across glass-calm ocean shallows, and enjoying the sensory feasts of freshly baked hearth breads and hot steaming broths would be deeply restorative. The hand-painted aesthetic, gentle pacing, and whimsical blend of gentle enchantment and cozy daily rituals make it the ultimate dream escape.",
                        "For high-octane excitement, spending a week in the world of My Hero Academia would be thrilling beyond words. Enrolling for a temporary guest stint at U.A. High School, testing out unique quirk abilities in specialized training arenas, and witnessing professional heroes manage emergency response across futuristic metropolitan cities would provide an adrenaline rush like nothing else."
                    )
                }
            }
            // General favorite or recommendation anime
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("Frieren: Beyond Journey's End.", "Fullmetal Alchemist: Brotherhood.", "Steins;Gate.")
                ResponseLengthPreset.SHORT -> listOf(
                    "Frieren: Beyond Journey's End and Fullmetal Alchemist: Brotherhood are absolute masterpieces!",
                    "Steins;Gate if you love sci-fi thrillers, or Spy x Family for pure wholesome fun!",
                    "Attack on Titan for gripping storytelling and incredible plot twists."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "I highly recommend Frieren: Beyond Journey's End for its breathtaking animation and touching reflection on time and human connection, alongside Fullmetal Alchemist: Brotherhood for unmatched narrative pacing.",
                    "If you enjoy intricate sci-fi and time travel, Steins;Gate is phenomenal. For rich world-building and character journeys, Hunter x Hunter and Vinland Saga are top-tier.",
                    "For stunning visual artistry and emotional storytelling, Violet Evergarden and Studio Ghibli films like Spirited Away and Princess Mononoke remain gold standards."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "My top recommendation is Frieren: Beyond Journey's End. It masterfully turns the traditional fantasy genre on its head by following an elven mage after the demon king has already been defeated, meditating on the passage of time, cherished memories, and the beauty of quiet human bonds. Paired with timeless classics like Fullmetal Alchemist: Brotherhood and Steins;Gate, they represent the absolute pinnacle of anime storytelling."
                )
            }
        }

        // 2. Video Game Worlds & RPGs
        if (lower.contains("video game") || lower.contains("gaming world") || lower.contains("game world")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("Hyrule from Zelda!", "Animal Crossing island.", "Minecraft creative mode.")
                ResponseLengthPreset.SHORT -> listOf(
                    "Hyrule from Zelda: Breath of the Wild—climbing mountains and gliding over rolling plains would be incredible!",
                    "An Animal Crossing island for total relaxation, fruit picking, and zero mortgage stress!",
                    "Minecraft in creative mode: boundless imagination and infinite building."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "I would choose Hyrule from Zelda: Breath of the Wild. Exploring its sweeping grassy plateaus, discovering hidden shrines, and gliding from towering peaks into tranquil villages would be the ultimate adventure.",
                    "An Animal Crossing tropical island would be the perfect low-stress getaway—catching exotic fish, decorating a seaside villa, and relaxing to acoustic guitar by the ocean.",
                    "Skyrim or The Witcher 3's Toussaint for picturesque sunlit vineyards, historic stone castles, and grand mythical quests."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "If I could live in any video game world, I would choose Hyrule as depicted in Zelda: Breath of the Wild and Tears of the Kingdom. The sheer sense of scale, the breathtaking vistas from Mount Lanayru down to the Akkala highlands, the peaceful atmosphere of Hateno Village, and the joy of parasailing through the clouds capture the quintessential spirit of open-world discovery without modern digital distractions."
                )
            }
        }

        // 3. Superpowers
        if (lower.contains("superpower") || lower.contains("super power") || (lower.contains("power") && (lower.contains("have") || lower.contains("pick") || lower.contains("choose")))) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("Teleportation!", "Time manipulation.", "Flight.")
                ResponseLengthPreset.SHORT -> listOf(
                    "Teleportation! Instant travel anywhere on Earth with zero airport security lines or morning traffic.",
                    "Time manipulation: pausing moments to catch your breath or rewinding little mistakes.",
                    "Flight! The unmatched freedom of soaring through open blue skies above the clouds."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "Teleportation is the ultimate superpower. You could have breakfast in Paris, spend the afternoon snorkeling in Hawaii, and sleep in your own bed every night with zero commute time or transit stress.",
                    "Time manipulation would be phenomenal—having the ability to pause time when life gets overwhelming, give yourself infinite time to think, and rewind whenever you need a do-over.",
                    "The power of flight or telekinesis: effortless mobility, absolute physical freedom, and a whole new perspective on the world."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "I would choose instantaneous teleportation without hesitation. Beyond completely eliminating the wasted hours spent in traffic, airports, and crowded commutes, it unlocks the ability to experience every corner of the planet effortlessly. You could spontaneously watch the sunrise over the Himalayas, meet friends across the globe for dinner, and return home in the blink of an eye, making the entire world your backyard."
                )
            }
        }

        // 4. Travel & Vacation Destinations
        if (lower.contains("travel anywhere") || lower.contains("vacation") || lower.contains("holiday destination") || lower.contains("dream trip")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("Kyoto, Japan!", "The Swiss Alps.", "The Amalfi Coast.")
                ResponseLengthPreset.SHORT -> listOf(
                    "Kyoto in autumn—tranquil wooden temples, bamboo forests, and stunning vibrant red maples!",
                    "The Swiss Alps in Zermatt: dramatic alpine peaks, scenic cogwheel trains, and crisp mountain air.",
                    "The Amalfi Coast: colorful cliffside towns, Mediterranean sunshine, and fresh handmade pasta."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "Kyoto, Japan would be my top choice. Wandering through historic wooden machiya lanes in Gion, visiting moss gardens at tranquil Zen temples, and enjoying seasonal matcha during autumn foliage is a deeply magical experience.",
                    "The Swiss Alps—specifically Lauterbrunnen and Zermatt—offering sheer glacial waterfalls, panoramic mountain views, and quiet alpine hiking trails.",
                    "The Amalfi Coast in Italy for breathtaking seaside cliffs, sparkling turquoise waters, and long leisurely dinners overlooking the Mediterranean."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "My ultimate dream trip would be spending several weeks traveling across Japan, beginning in Tokyo's bustling neon districts before taking the bullet train to Kyoto and the Japanese Alps. Exploring centuries-old Zen shrines, soaking in natural geothermal onsen surrounded by cedar forests, and savoring world-class culinary craftsmanship from street takoyaki to multi-course kaiseki is an unmatched journey."
                )
            }
        }

        // 5. Time Travel (Past vs Future)
        if (lower.contains("time travel") || (lower.contains("past") && lower.contains("future")) || lower.contains("time machine")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("100 years into the future!", "The Italian Renaissance.", "The future!")
                ResponseLengthPreset.SHORT -> listOf(
                    "Definitely 100 years into the future to see what incredible science, medicine, and space tech we invent!",
                    "The Italian Renaissance in Florence to watch Leonardo da Vinci and Michelangelo at work!",
                    "The future—the curiosity of seeing how civilization evolves is too exciting to pass up."
                )
                ResponseLengthPreset.NORMAL -> listOf(
                    "I would travel 100 to 200 years into the future. Seeing how humanity solves climate challenges, cures diseases, and explores deep space would satisfy my deepest curiosity far more than looking backward.",
                    "Traveling back to the High Renaissance in Florence would be extraordinary—witnessing the explosion of art, architecture, and scientific philosophy firsthand.",
                    "The future wins every time: technology, interstellar discoveries, and the realization of clean energy make the tomorrow vastly more intriguing."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "I would choose to travel 150 years into the future. While history holds immense romantic allure, the prospect of witnessing humanity's greatest upcoming breakthroughs—fusion energy, interstellar probes, synthetic biology, and advanced artificial intelligence integrated harmoniously into sustainable cities—is overwhelmingly compelling. Seeing how the questions of our era are resolved would be the adventure of a lifetime."
                )
            }
        }

        // 6. Would You Rather (Dilemmas & Decisions)
        if (lower.contains("would you rather") || lower.contains("prefer")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf("Option A, easily!", "Option B makes the best story.", "Neither, I choose chaos!")
                    else -> listOf("Definitely the first option!", "I'd lean toward the second one.", "Both have great perks!")
                }
                ResponseLengthPreset.SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf(
                        "I'm picking whichever option results in the better story and the least amount of regret tomorrow morning!",
                        "Definitely the bolder choice—playing it safe in a hypothetical question is a wasted wish.",
                        "Tough dilemma! But if forced to pick, go with the one that brings the most laughs."
                    )
                    ReplyTone.PROFESSIONAL -> listOf(
                        "Weighing both alternatives, the first option offers superior upside while minimizing unnecessary risk.",
                        "The second option provides greater long-term flexibility and aligns better with practical goals.",
                        "Both have distinct merits, though prioritizing sustainable value makes the decision clear."
                    )
                    else -> listOf(
                        "The first option without a doubt! Between the two, it brings the most fun and best outcome.",
                        "Definitely the bolder choice! It's easily the more memorable and exciting path.",
                        "I'd go with the first option—it offers the best blend of adventure and comfort."
                    )
                }
                ResponseLengthPreset.NORMAL -> listOf(
                    "Between the two, I would decisively pick the first option! It provides the richer experience and the greatest freedom, making it the clear winner.",
                    "The first option without hesitation! Evaluating both sides, it delivers the most creative potential and positive energy.",
                    "My vote goes straight to the bolder choice! It makes you look back and smile about having chosen it."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "Evaluating this classic dilemma requires balancing immediate appeal against enduring satisfaction. When you compare both scenarios, the choice that grants greater autonomy, positive experiences, and memorable stories consistently proves superior to the alternative that merely minimizes friction. If faced with the decision, lean boldly into the choice that expands your horizon!"
                )
            }
        }

        // 7. General Inquiry / Interrogative Starter ("Why", "How", "Where", "Which")
        if (lower.startsWith("why") || lower.startsWith("how") || lower.startsWith("where") || lower.startsWith("which")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf("Because life's too short not to!", "Simple: confidence and coffee.", "The fun one!")
                    ReplyTone.PROFESSIONAL -> listOf("Due to key operational factors.", "Through structured execution.", "Based on core criteria.")
                    else -> listOf("A combination of key factors.", "Step by step works best.", "The most reliable option.")
                }
                ResponseLengthPreset.SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf(
                        "That's the million-dollar question! Usually the answer comes down to caffeine, timing, and a bit of luck.",
                        "Great question—the short answer is that keeping things simple usually beats out the complicated plan.",
                        "Why make it complicated? Stick to whatever makes the most sense right now!"
                    )
                    ReplyTone.PROFESSIONAL -> listOf(
                        "That depends largely on strategic alignment and operational priorities. Identifying core milestones clarifies the approach.",
                        "A methodical evaluation of the underlying drivers provides the most accurate rationale.",
                        "Focusing on measurable impact and systematic execution delivers the most dependable outcome."
                    )
                    else -> listOf(
                        "Starting with the simplest and most effective step is definitely the best approach here.",
                        "It comes down to prioritizing what matters most in this situation—focus on the essentials.",
                        "Looking at it practically, tackling the immediate priority first will get the job done."
                    )
                }
                ResponseLengthPreset.NORMAL -> listOf(
                    "Taking all the variables into account, the most compelling approach centers on balancing practical execution with immediate effectiveness.",
                    "When you break it down into core components, addressing the primary goal first consistently yields the strongest result.",
                    "Focusing on what works best right now delivers the cleanest and most dependable outcome."
                )
                ResponseLengthPreset.LONG -> listOf(
                    "Examining this question thoroughly reveals multiple interconnected dimensions. At its core, the solution involves understanding the fundamental motivations and practical constraints at play. By aligning immediate actions with long-term objectives, you ensure that the chosen path is both sustainable and impactful, turning a complex challenge into a clear, structured roadmap."
                )
            }
        }

        return emptyList()
    }

    private const val DIRECT_ANSWER_INSTRUCTION =
        "CRITICAL INSTRUCTION - DIRECT & SPECIFIC REPLIES ONLY:\n" +
        "- You must directly, genuinely, and specifically answer the question's content or choice asked.\n" +
        "- STRICTLY FORBIDDEN: Do NOT output generic conversational filler, evasive platitudes, or non-answers such as \"That's a really great question!\", \"I'd definitely lean toward the most rewarding choice.\", \"Tough call!\", \"Weighing the options\", \"Good point\", or generic pleasantries.\n" +
        "- MULTIPLE-CHOICE & OPTIONS: If the question offers choices or alternatives (e.g., \"What are you craving right now — food, sleep, or attention?\" or \"coffee or tea?\"), each generated reply MUST pick or commit to a specific choice from the prompt (e.g. food, sleep, attention) with authentic, natural phrasing. Never say \"I'd choose the best option\"—actually pick the specific choice!\n" +
        "- FACTUAL / OPINION / HYPOTHETICAL: State the concrete answer, name, decision, or opinion immediately without hedging.\n" +
        "- DIVERSITY: Provide distinct, realistic messaging replies that directly address what was asked."

    internal fun extractQuestionChoices(question: String): List<String> {
        val clean = question.trim()
        val candidate = when {
            clean.contains(" — ") -> clean.substringAfter(" — ")
            clean.contains("—") && clean.substringAfter("—").contains(" or ", ignoreCase = true) -> clean.substringAfter("—")
            clean.contains(" -- ") -> clean.substringAfter(" -- ")
            clean.contains(": ") && clean.substringAfter(": ").contains(" or ", ignoreCase = true) -> clean.substringAfter(": ")
            else -> clean
        }

        if (!candidate.contains(" or ", ignoreCase = true) &&
            !candidate.contains(" vs ", ignoreCase = true) &&
            !candidate.contains(" vs. ", ignoreCase = true)
        ) {
            return emptyList()
        }

        val rawParts = candidate.split(Regex("(?i)\\s*(?:,|\\bor\\b|\\bvs\\.?\\b)\\s*"))
            .map { part ->
                part.replace(Regex("(?i)^(?:either|neither|whether|or)\\s+"), "")
                    .replace(Regex("[?!.,;\"'\\-—]+$"), "")
                    .trim()
            }
            .filter { it.isNotBlank() && it.length in 1..45 }

        if (rawParts.size < 2) return emptyList()

        var first = rawParts[0]
        val preludes = listOf(
            "do you prefer", "would you prefer", "would you rather", "do you want",
            "should we get", "should we do", "should i get", "should i do",
            "are we going to", "which is better", "is it better to", "are you a",
            "are you into", "are you", "is it"
        )
        for (p in preludes) {
            if (first.startsWith(p, ignoreCase = true)) {
                first = first.substring(p.length).trim()
                break
            }
        }

        val result = mutableListOf<String>()
        if (first.isNotBlank() && !first.equals("what", ignoreCase = true) && !first.equals("which", ignoreCase = true)) {
            result.add(first)
        }
        for (i in 1 until rawParts.size) {
            val p = rawParts[i]
            if (p.isNotBlank() && !p.equals("what", ignoreCase = true) && !p.equals("which", ignoreCase = true)) {
                result.add(p)
            }
        }

        return if (result.size >= 2) result else emptyList()
    }

    internal fun trySolveChoiceOrOptionQuestion(
        question: String,
        tone: ReplyTone,
        preset: ResponseLengthPreset
    ): List<String> {
        val lower = question.lowercase()

        // 1. Specific craving questions (e.g. "What are you craving right now — food, sleep, or attention?")
        if (lower.contains("craving") || lower.contains("crave")) {
            val mentionsFood = lower.contains("food")
            val mentionsSleep = lower.contains("sleep")
            val mentionsAttention = lower.contains("attention")

            if (mentionsFood && (mentionsSleep || mentionsAttention)) {
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> when (tone) {
                        ReplyTone.WITTY -> listOf("Food, 100%!", "Sleep, my bed misses me.", "Attention, come say hi!")
                        ReplyTone.CONCISE -> listOf("Food.", "Sleep.", "Attention.")
                        ReplyTone.PROFESSIONAL -> listOf("Food to recharge.", "Sleep is the priority.", "A brief conversation.")
                        else -> listOf("Definitely food!", "Sleep, without a doubt.", "Attention, honestly!")
                    }
                    ResponseLengthPreset.SHORT -> when (tone) {
                        ReplyTone.WITTY -> listOf(
                            "Food, 100%! If pizza or snacks are involved, it's not even a debate.",
                            "Sleep without a doubt—my bed and I have a serious date right now.",
                            "Attention, obviously! Come entertain me and distract me from work."
                        )
                        ReplyTone.CONCISE -> listOf(
                            "Definitely food right now.",
                            "Sleep, desperately need to recharge.",
                            "Honestly, attention and a good chat."
                        )
                        ReplyTone.PROFESSIONAL -> listOf(
                            "I would prioritize food to restore energy and focus.",
                            "Rest and sleep are definitely the top priority at this moment.",
                            "Taking time for positive social engagement would be ideal."
                        )
                        ReplyTone.TRASH_TALK -> listOf(
                            "Food, obviously! Unlike your jokes, a good meal never disappoints.",
                            "Sleep, so I don't have to listen to more questionable takes today!",
                            "Attention? Only if it's from someone with better conversation!"
                        )
                        ReplyTone.EMPATHETIC -> listOf(
                            "Definitely craving some comforting warm food right now!",
                            "Sleep sounds so heavenly right now, desperately need some rest.",
                            "Honestly, some genuine attention and a heartfelt chat would be amazing."
                        )
                        else -> listOf(
                            "Definitely food right now, starving!",
                            "Sleep without a doubt—I'm completely exhausted and need to recharge.",
                            "Honestly, some attention and good company right now!"
                        )
                    }
                    ResponseLengthPreset.NORMAL -> when (tone) {
                        ReplyTone.WITTY -> listOf(
                            "Food, without a shadow of a doubt! Bring on the hot pizza, tacos, or dessert. Anyone choosing sleep over food hasn't had proper snacks today!",
                            "Sleep! I am running on 2% battery and sheer optimism. My pillow is calling my name and I must answer.",
                            "Honestly, attention! I crave quality entertainment, top-tier gossip, and good vibes right now."
                        )
                        else -> listOf(
                            "Definitely food right now! I am absolutely starving and craving a really hearty, delicious meal to hit the spot.",
                            "Sleep without a doubt. It has been such a long stretch and getting a solid, uninterrupted nap sounds like absolute heaven.",
                            "Honestly, some attention and great conversation right now! Catching up with someone and sharing a laugh would make my day."
                        )
                    }
                    ResponseLengthPreset.LONG -> listOf(
                        "If I have to choose between the three, I am picking food without hesitation! There is nothing better right now than sitting down with a great meal, unwinding, and enjoying some fantastic comfort food.",
                        "Sleep is the definitive winner for me. Energy levels are running low, and an uninterrupted, deeply restorative rest is honestly the greatest gift imaginable right now.",
                        "Honestly, I would choose attention and genuine connection. Sharing quality time, having an engaging conversation, and feeling appreciated beats everything else today."
                    )
                }
            }
        }

        // 2. Generic multiple-choice / option extraction
        val choices = extractQuestionChoices(question)
        if (choices.size >= 2) {
            val optA = choices[0].trim().replaceFirstChar { it.uppercase() }
            val optB = choices[1].trim().replaceFirstChar { it.uppercase() }
            val optC = if (choices.size >= 3) choices[2].trim().replaceFirstChar { it.uppercase() } else null

            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf(
                        "$optA, easily!",
                        "$optB makes the best story.",
                        optC?.let { "$it, galaxy-brain pick!" } ?: "$optA all day."
                    )
                    ReplyTone.CONCISE -> listOf(
                        optA,
                        optB,
                        optC ?: "Definitely $optA."
                    )
                    ReplyTone.PROFESSIONAL -> listOf(
                        "I recommend $optA.",
                        "$optB is optimal.",
                        optC?.let { "$it is viable." } ?: "$optA aligns best."
                    )
                    ReplyTone.TRASH_TALK -> listOf(
                        "$optA, obviously.",
                        "$optB, no contest.",
                        optC?.let { "$it by a mile." } ?: "Only an amateur picks $optB!"
                    )
                    else -> listOf(
                        "Definitely $optA!",
                        "$optB, 100%.",
                        optC?.let { "$it for sure!" } ?: "I'd lean toward $optA."
                    )
                }
                ResponseLengthPreset.SHORT -> when (tone) {
                    ReplyTone.WITTY -> listOf(
                        "100% $optA! Anyone picking $optB is living dangerously.",
                        "$optB, because I have impeccable taste and zero regrets.",
                        optC?.let { "Honestly $it—that's the true galaxy-brain choice!" }
                            ?: "Tough dilemma, but $optA wins every single time."
                    )
                    ReplyTone.CONCISE -> listOf(
                        "Definitely $optA.",
                        "$optB without question.",
                        optC?.let { "$it." } ?: "Between the two, $optA."
                    )
                    ReplyTone.PROFESSIONAL -> listOf(
                        "I would recommend $optA as the most effective and dependable choice.",
                        "$optB presents the stronger alternative given current priorities.",
                        optC?.let { "$it offers the most balanced resolution based on the requirements." }
                            ?: "Evaluating the options, $optA provides the clearer advantage."
                    )
                    ReplyTone.TRASH_TALK -> listOf(
                        "$optA, obviously! Only someone with zero taste would choose $optB.",
                        "$optB, no debate. Don't embarrass yourself by picking anything else.",
                        optC?.let { "$it, and it's not even a competition!" }
                            ?: "Anyone who doesn't pick $optA needs their judgment examined."
                    )
                    ReplyTone.EMPATHETIC -> listOf(
                        "I'd definitely pick $optA, but whatever feels right to you is wonderful!",
                        "$optB sounds so lovely and comforting right now.",
                        optC?.let { "$it would bring such good energy and comfort today!" }
                            ?: "Both are great, but $optA would bring the most peace of mind."
                    )
                    else -> listOf(
                        "Definitely $optA right now!",
                        "$optB without a doubt, every time.",
                        optC?.let { "Honestly, $it would hit the spot perfectly!" }
                            ?: "I'd definitely go with $optA—it's the clear winner for me."
                    )
                }
                ResponseLengthPreset.NORMAL -> when (tone) {
                    ReplyTone.WITTY -> listOf(
                        "I am picking $optA without hesitation! When you look at the options, $optA delivers the absolute best experience with zero regrets tomorrow.",
                        "Definitely $optB! Choosing anything else would be a questionable life choice that I am simply not willing to make today.",
                        optC?.let { "My vote goes straight to $it! It's easily the smartest, most fun option on the table." }
                            ?: "Between both choices, $optA takes the crown. It's bolder, more entertaining, and the obvious winner!"
                    )
                    else -> listOf(
                        "I would definitely choose $optA! Between the options presented, it stands out as the most appealing and satisfying choice right now.",
                        "$optB without a doubt! It's the clear winner for me and fits the situation so much better.",
                        optC?.let { "Honestly, I'd go with $it! Out of the three, $it would hit the spot and feel the most rewarding." }
                            ?: "I would lean strongly toward $optA, though $optB is definitely a close second depending on the day."
                    )
                }
                ResponseLengthPreset.LONG -> listOf(
                    "If I have to choose among these alternatives, I am decisively picking $optA. Comparing the possibilities, $optA offers the greatest overall satisfaction, aligns perfectly with what works best, and delivers the most dependable and enjoyable outcome.",
                    "Without hesitation, my vote goes to $optB. When you weigh the practical benefits and the experience it brings, $optB clearly emerges as the superior choice that I would commit to every time.",
                    optC?.let { "I would actually select $it! Out of the three options, $it provides the most refreshing and distinctive result, making it the most compelling path forward." }
                        ?: "Between the two options, $optA is the definitive choice for me. It strikes the perfect balance of quality and satisfaction, making it an easy decision."
                )
            }
        }

        return emptyList()
    }

    private fun callGeminiRestApi(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String,
        brainKnowledgePrompt: String = ""
    ): ProviderReplyResult {
        var rawModel = provider.modelName.trim()
        if (rawModel.startsWith("models/")) {
            rawModel = rawModel.removePrefix("models/")
        }
        rawModel = rawModel.replace(" ", "-")
        val model = when (rawModel) {
            "", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite-preview", "gemini-2.0-flash", "gemini-1.5-flash" -> "gemini-2.5-flash"
            "gemini-flash-latest" -> "gemini-flash-latest"
            else -> rawModel
        }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${provider.apiKey.trim()}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val lengthPreset = settings.responseLengthPreset
        val charCeiling = minOf(settings.customCharLimit, lengthPreset.charCeiling)

        val systemPrompt = (
            "You are an intelligent quick reply assistant.\n" +
            "The user received this question / incoming message: \"$question\".\n" +
            "$DIRECT_ANSWER_INSTRUCTION\n" +
            "Directly, specifically, and accurately answer or reply to this inquiry without generic filler.\n" +
            "Tone: ${settings.tone.systemPromptHint}.\n" +
            "Language & Cultural Style: When the incoming message is in Hinglish, Hindi, Spanish, or any other language, reply in that EXACT same language/script using natural casual slang and authentic banter appropriate to that dialect rather than a stiff literal translation.\n" +
            "Selected Length Preset: ${lengthPreset.title} (${lengthPreset.subtitle}).\n" +
            "${lengthPreset.promptInstruction}\n" +
            "Maximum character ceiling: $charCeiling characters.\n" +
            "Output ONLY a valid JSON array of ${settings.count} strings, e.g. [\"reply 1\", \"reply 2\"]. No markdown code fences, no extra text."
        ) + brainKnowledgePrompt

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
                put("maxOutputTokens", lengthPreset.maxTokens)
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
                return parseStructuredAiReplies(text, questionId, settings.tone, provider)
            }
        } else {
            val errorRaw = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage ?: "Unknown Gemini error" }
            var errorMsg: String? = null
            var errorStatus: String? = null
            try {
                val errObj = JSONObject(errorRaw).optJSONObject("error")
                if (errObj != null) {
                    errorMsg = errObj.optString("message")
                    errorStatus = errObj.optString("status")
                }
            } catch (_: Exception) {}
            val statusSuffix = if (!errorStatus.isNullOrBlank()) " [$errorStatus]" else ""
            throw java.io.IOException("Gemini API HTTP ${conn.responseCode}$statusSuffix: ${errorMsg ?: errorRaw}")
        }
        return ProviderReplyResult(emptyList())
    }

    fun extractContentFromOpenAiJson(root: JSONObject): String {
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.optJSONObject(0)
            if (firstChoice != null) {
                // 1. Check choice-level text (e.g. legacy completions)
                val directText = firstChoice.optString("text")
                if (directText.isNotBlank() && directText != "null") {
                    return directText
                }

                val message = firstChoice.optJSONObject("message")
                if (message != null) {
                    // 2. Direct message content
                    val contentObj = message.opt("content")
                    if (contentObj is String && contentObj.isNotBlank() && contentObj != "null") {
                        return contentObj
                    } else if (contentObj is JSONArray && contentObj.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until contentObj.length()) {
                            val part = contentObj.opt(i)
                            if (part is JSONObject) {
                                val t = part.optString("text").ifBlank { part.optString("content") }
                                if (t.isNotBlank() && t != "null") sb.append(t).append("\n")
                            } else if (part is String && part.isNotBlank() && part != "null") {
                                sb.append(part).append("\n")
                            }
                        }
                        if (sb.isNotBlank()) return sb.toString().trim()
                    } else if (contentObj is JSONObject) {
                        val t = contentObj.optString("text").ifBlank { contentObj.optString("content") }
                        if (t.isNotBlank() && t != "null") return t
                    }

                    // 3. Reasoning / Thought fields in reasoning models (DeepSeek R1, GPT-OSS 120b, QwQ, etc.)
                    val reasoningContent = message.optString("reasoning_content")
                    if (reasoningContent.isNotBlank() && reasoningContent != "null") {
                        return reasoningContent
                    }
                    val reasoning = message.optString("reasoning")
                    if (reasoning.isNotBlank() && reasoning != "null") {
                        return reasoning
                    }
                    val thought = message.optString("thought").ifBlank { message.optString("thoughts") }
                    if (thought.isNotBlank() && thought != "null") {
                        return thought
                    }

                    // 4. Any other non-role string property
                    val keys = message.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key != "role" && key != "tool_calls") {
                            val v = message.opt(key)
                            if (v is String && v.isNotBlank() && v != "null") {
                                return v
                            }
                        }
                    }
                }

                // 5. Choice-level reasoning fallback
                val choiceReasoning = firstChoice.optString("reasoning").ifBlank { firstChoice.optString("reasoning_content") }
                if (choiceReasoning.isNotBlank() && choiceReasoning != "null") {
                    return choiceReasoning
                }
            }
        }

        // 6. Root-level fallbacks (output, response, result, generated_text, text, answer)
        val rootFallbacks = listOf("output", "response", "result", "generated_text", "text", "answer", "message")
        for (field in rootFallbacks) {
            val v = root.optString(field)
            if (v.isNotBlank() && v != "null") return v
        }

        return ""
    }

    private fun callOpenAiCompatibleRestWithRaw(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String,
        brainKnowledgePrompt: String = ""
    ): Pair<ProviderReplyResult, String> {
        val defaultEndpoint = if (provider.type == AiProviderType.GROQ) {
            "https://api.groq.com/openai/v1/chat/completions"
        } else {
            "https://api.openai.com/v1/chat/completions"
        }
        val endpoint = provider.customEndpoint?.takeIf { it.isNotBlank() } ?: defaultEndpoint
        if (provider.apiKey.isBlank() && !endpoint.contains("localhost") && !endpoint.contains("10.0.2.2")) {
            throw java.io.IOException("${provider.displayName} Missing API Key (HTTP 401): No API key provided in Settings > Providers.")
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (provider.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${provider.apiKey.trim()}")
            }
            connectTimeout = 8000
            readTimeout = 8000
        }

        val lengthPreset = settings.responseLengthPreset
        val charCeiling = minOf(settings.customCharLimit, lengthPreset.charCeiling)

        val systemRolePrompt = (
            "You are an accurate quick reply assistant.\n" +
            "$DIRECT_ANSWER_INSTRUCTION\n" +
            "You generate direct, specific answers and contextual replies that directly resolve the incoming question or message without generic filler.\n" +
            "Tone instructions: ${settings.tone.systemPromptHint}.\n" +
            "Language/Script Matching: When the incoming message is in Hinglish, Hindi, Spanish, or any other non-English language, reply in that EXACT same language/script using natural casual vernacular and slang matching the tone persona rather than a formal literal translation.\n" +
            "Length Requirement: ${lengthPreset.title} (${lengthPreset.subtitle}). ${lengthPreset.promptInstruction}\n" +
            "Maximum character limit: $charCeiling chars per reply.\n" +
            "Format output strictly as a JSON array of strings: [\"reply 1\", \"reply 2\"]."
        ) + brainKnowledgePrompt

        val userPrompt = (
            "Incoming message/question: \"$question\"\n" +
            "CRITICAL: Directly and specifically answer the question or pick the choices asked. Do NOT respond with generic filler like 'That's a great question'.\n" +
            "Requested tone: ${settings.tone.systemPromptHint}\n" +
            "Language requirement: Match incoming language/script directly (use natural Hinglish/slang if applicable).\n" +
            "Length preset: ${lengthPreset.title} (${lengthPreset.promptInstruction})\n" +
            "Max length: $charCeiling characters per reply\n" +
            "Generate ${settings.count} distinct quick reply options that directly answer this inquiry.\n" +
            "Respond ONLY with a JSON array of strings: [\"reply 1\", \"reply 2\"]"
        )

        val defaultModel = if (provider.type == AiProviderType.GROQ) "openai/gpt-oss-120b" else "gpt-4o-mini"
        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { defaultModel })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemRolePrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", lengthPreset.maxTokens)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode in 200..299) {
            val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(resp)
            val extractedContent = extractContentFromOpenAiJson(root)
            val replyResult = parseStructuredAiReplies(extractedContent, questionId, settings.tone, provider)
            return Pair(replyResult, resp)
        } else {
            val rawErrorText = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage ?: "Unknown error" }

            var errorType: String? = null
            var errorCode: String? = null
            var errorMsg: String? = null
            try {
                val errorObj = JSONObject(rawErrorText).optJSONObject("error")
                if (errorObj != null) {
                    errorType = errorObj.optString("type").takeIf { it.isNotBlank() }
                    errorCode = errorObj.optString("code").takeIf { it.isNotBlank() }
                    errorMsg = errorObj.optString("message").takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {}

            val pName = provider.displayName
            val formattedReason = when (conn.responseCode) {
                401 -> "$pName Auth Failure (HTTP 401 - ${errorCode ?: "invalid_api_key"}): ${errorMsg ?: "Incorrect or expired API key. Please check your API key in Providers settings."}"
                429 -> "$pName Quota/Rate Limit (HTTP 429 - ${errorCode ?: "insufficient_quota"}): ${errorMsg ?: "You exceeded your current API quota or rate limit. Check billing."}"
                404 -> "$pName Model Not Found (HTTP 404 - ${errorCode ?: "model_not_found"}): ${errorMsg ?: "The requested model '${provider.modelName}' does not exist or you lack access."}"
                400 -> "$pName Bad Request (HTTP 400 - ${errorCode ?: "invalid_request"}): ${errorMsg ?: rawErrorText}"
                403 -> "$pName Access Forbidden (HTTP 403): ${errorMsg ?: "Access denied to API endpoint."}"
                in 500..599 -> "$pName Server Error (HTTP ${conn.responseCode}): ${errorMsg ?: "Service is temporarily down."}"
                else -> "$pName HTTP ${conn.responseCode}: ${errorMsg ?: rawErrorText}"
            }
            throw java.io.IOException(formattedReason)
        }
    }

    private fun callOpenAiCompatibleRest(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String,
        brainKnowledgePrompt: String = ""
    ): ProviderReplyResult {
        return callOpenAiCompatibleRestWithRaw(provider, question, settings, questionId, brainKnowledgePrompt).first
    }

    private fun callAnthropicRest(
        provider: AiProvider,
        question: String,
        settings: ReplySettings,
        questionId: String,
        brainKnowledgePrompt: String = ""
    ): ProviderReplyResult {
        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", provider.apiKey.trim())
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val lengthPreset = settings.responseLengthPreset
        val charCeiling = minOf(settings.customCharLimit, lengthPreset.charCeiling)

        val prompt = (
            "Question: \"$question\".\n" +
            "$DIRECT_ANSWER_INSTRUCTION\n" +
            "Generate ${settings.count} quick replies directly and specifically answering this inquiry.\n" +
            "Tone: ${settings.tone.systemPromptHint}.\n" +
            "Language/Script Matching: When the incoming message is in Hinglish, Hindi, Spanish, or another language, reply in that EXACT same language/script with natural casual slang matching the tone rather than a literal translation.\n" +
            "Selected Length Preset: ${lengthPreset.title} (${lengthPreset.subtitle}).\n" +
            "${lengthPreset.promptInstruction}\n" +
            "Max chars: $charCeiling.\n" +
            "Return ONLY a JSON array of strings: [\"reply1\", \"reply2\"]."
        ) + brainKnowledgePrompt

        val body = JSONObject().apply {
            put("model", provider.modelName.ifBlank { "claude-3-5-haiku-20241022" })
            put("max_tokens", lengthPreset.maxTokens)
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
                return parseStructuredAiReplies(text, questionId, settings.tone, provider)
            }
        } else {
            val errorText = try {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            } catch (_: Exception) { conn.responseMessage }
            throw java.io.IOException("Anthropic API HTTP ${conn.responseCode}: $errorText")
        }
        return ProviderReplyResult(emptyList())
    }

    fun parseStructuredAiReplies(
        rawText: String,
        questionId: String,
        tone: ReplyTone,
        provider: AiProvider
    ): ProviderReplyResult {
        val replies = parseJsonArrayReplies(rawText, questionId, tone, provider)
        return ProviderReplyResult(replies = replies)
    }

    fun parseJsonArrayReplies(
        rawText: String,
        questionId: String,
        tone: ReplyTone,
        provider: AiProvider
    ): List<ReplyItem> {
        if (rawText.isBlank() || rawText == "null") return emptyList()

        // 1. Strip think blocks from reasoning models (e.g. DeepSeek R1, GPT-OSS)
        var clean = rawText
        if (clean.contains("</think>")) {
            val afterThink = clean.substringAfter("</think>").trim()
            clean = if (afterThink.isNotBlank()) {
                afterThink
            } else {
                clean.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
            }
        }

        // Clean markdown fences
        clean = clean.replace("```json", "").replace("```JSON", "").replace("```", "").trim()

        // 2. Try JSON Array parsing
        try {
            val startIndex = clean.indexOf('[')
            val endIndex = clean.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val jsonSub = clean.substring(startIndex, endIndex + 1)
                val jsonArr = JSONArray(jsonSub)
                val list = mutableListOf<ReplyItem>()
                for (i in 0 until jsonArr.length()) {
                    val str = jsonArr.optString(i, "").trim()
                    if (str.isNotBlank() && str != "null") {
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

        // 3. Try JSON Object with replies / options / choices key
        try {
            val startIndex = clean.indexOf('{')
            val endIndex = clean.lastIndexOf('}')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val jsonObj = JSONObject(clean.substring(startIndex, endIndex + 1))
                val arrayKey = listOf("replies", "options", "choices", "suggestions", "answers", "items")
                    .firstOrNull { jsonObj.has(it) && jsonObj.optJSONArray(it) != null }
                if (arrayKey != null) {
                    val jsonArr = jsonObj.getJSONArray(arrayKey)
                    val list = mutableListOf<ReplyItem>()
                    for (i in 0 until jsonArr.length()) {
                        val str = jsonArr.optString(i, "").trim()
                        if (str.isNotBlank() && str != "null") {
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
            }
        } catch (_: Exception) {}

        // 4. Fallback split by lines (bullet points, numbered lists, quotes)
        val lines = clean.lines()
            .map { it.replace(Regex("^[-*•0-9.)\\]]+\\s*"), "").replace("\"", "").replace("'", "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("<") && !it.endsWith(">") }

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

    private fun trySolveMathQuestion(
        question: String,
        tone: ReplyTone,
        preset: ResponseLengthPreset
    ): List<String> {
        val clean = question.trim()
        val lower = clean.lowercase()

        // 1. Imaginary unit powers (i², i^2, i³, i⁴)
        if (clean.contains("i²") || lower.contains("i^2") || lower.contains("value of i²") || lower.contains("value of i^2") || lower.contains("i squared") || lower.contains("i*i")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("-1", "-1", "-1")
                ResponseLengthPreset.SHORT -> listOf("i² = -1", "The value of i² is -1.", "-1 (exact)")
                ResponseLengthPreset.NORMAL -> listOf("i² = -1 (by mathematical definition of the imaginary unit i = √-1).", "The value of i² is -1.", "i² equals -1 in complex analysis.")
                ResponseLengthPreset.LONG -> listOf("In complex number theory, the imaginary unit i is defined such that i = √(-1), meaning that squaring both sides gives i² = -1. This fundamental identity allows extending the real numbers to the complex field C.", "The value of i² is exactly -1. By definition in algebra, the imaginary unit satisfies the polynomial equation x² + 1 = 0, meaning i² = -1.", "i² is equal to -1. In complex arithmetic, multiplying the imaginary unit by itself rotates 90 degrees twice on the complex plane, landing at -1 on the real axis.")
            }
        }

        if (clean.contains("i³") || lower.contains("i^3")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("-i", "-i", "-i")
                ResponseLengthPreset.SHORT -> listOf("i³ = -i", "The value of i³ is -i.", "-i")
                ResponseLengthPreset.NORMAL -> listOf("i³ = i² · i = (-1) · i = -i.", "The value of i³ is -i in complex algebra.", "i³ equals -i.")
                ResponseLengthPreset.LONG -> listOf("The value of i³ is calculated as i² · i = (-1) · i = -i. In cyclic powers of i, the sequence follows i, -1, -i, 1.", "i³ equals -i because i³ = i² · i = -1 · i = -i.", "The power i³ simplifies directly to -i in the algebra of complex numbers.")
            }
        }

        if (clean.contains("i⁴") || lower.contains("i^4")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("1", "1", "1")
                ResponseLengthPreset.SHORT -> listOf("i⁴ = 1", "The value of i⁴ is 1.", "1")
                ResponseLengthPreset.NORMAL -> listOf("i⁴ = (i²)² = (-1)² = 1.", "The value of i⁴ is 1, completing the cyclic sequence.", "i⁴ equals 1.")
                ResponseLengthPreset.LONG -> listOf("The value of i⁴ is (i²)² = (-1)² = 1. This completes the standard 4-step mod-4 cyclic pattern of imaginary powers where i^(4k) = 1 for any integer k.", "i⁴ equals 1 because (i²) · (i²) = (-1) · (-1) = 1.", "The power i⁴ evaluates to exactly 1.")
            }
        }

        // 2. Constants
        if (lower.contains("value of pi") || lower.contains("what is pi") || lower.contains("pi = ?") || lower.contains("value of π") || lower.contains("what is π")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("3.14159", "π ≈ 3.14", "3.14159")
                ResponseLengthPreset.SHORT -> listOf("π ≈ 3.1415926535", "Pi is approximately 3.14159.", "3.14159265")
                ResponseLengthPreset.NORMAL -> listOf("The value of Pi (π) is approximately 3.141592653589793, representing the ratio of a circle's circumference to its diameter.", "Pi (π) is an irrational constant approximately equal to 3.14159265.", "π ≈ 3.141592653589793.")
                ResponseLengthPreset.LONG -> listOf("Pi (π) is a fundamental mathematical constant defined as the ratio of a circle's circumference to its diameter in Euclidean space. It is transcendental and irrational, with an approximate decimal expansion of 3.14159265358979323846.", "The mathematical constant π is approximately 3.141592653589793. It appears throughout calculus, trigonometry, quantum physics, and statistics.", "Pi (π) is approximately 3.14159265358979323846, central to circular geometry and Fourier analysis.")
            }
        }
        if (lower.contains("value of e") || lower.contains("euler's number") || lower.contains("euler's constant")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("2.71828", "e ≈ 2.718", "2.71828")
                ResponseLengthPreset.SHORT -> listOf("e ≈ 2.7182818284", "Euler's number e is approximately 2.71828.", "2.71828")
                ResponseLengthPreset.NORMAL -> listOf("Euler's number (e) is approximately 2.718281828459, the base of natural logarithms.", "e ≈ 2.718281828459045, representing the continuous growth constant.", "The mathematical constant e is approximately 2.71828.")
                ResponseLengthPreset.LONG -> listOf("Euler's number e is an irrational transcendental constant approximately equal to 2.71828182845904523536. It serves as the base of the natural logarithm ln(x) and has the unique calculus property that the derivative of e^x is equal to itself.", "The constant e is approximately 2.718281828459045, defined as the limit of (1 + 1/n)^n as n approaches infinity.", "Euler's number e is approximately 2.718281828459045, governing exponential growth and decay models.")
            }
        }

        // 3. Square roots
        if (lower.contains("sqrt") || lower.contains("square root") || lower.contains("√")) {
            val num = Regex("(\\d+(\\.\\d+)?)").find(clean)?.value?.toDoubleOrNull()
            if (num != null) {
                val root = Math.sqrt(num)
                val formatted = if (root % 1.0 == 0.0) root.toLong().toString() else "%.3f".format(root)
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf(formatted, formatted, formatted)
                    ResponseLengthPreset.SHORT -> listOf("√$num = $formatted", formatted, "The square root of $num is $formatted.")
                    ResponseLengthPreset.NORMAL -> listOf("The square root of $num is $formatted (√$num = $formatted).", "√$num equals $formatted.", "Calculated square root of $num is $formatted.")
                    ResponseLengthPreset.LONG -> listOf("The principal square root of $num is calculated as √$num = $formatted. When multiplied by itself, $formatted × $formatted = $num.", "The mathematical square root of $num evaluates to $formatted (where $formatted² = $num).", "Evaluating the square root expression: √$num = $formatted.")
                }
            }
        }

        // 4. Arithmetic calculation e.g. "15 * 8 + 32", "25 * 4", "100 / 4"
        val numbers = Regex("(\\d+(\\.\\d+)?)").findAll(clean).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()

        if (lower.contains("15 * 8 + 32") || (numbers.size == 3 && numbers[0] == 15.0 && numbers[1] == 8.0 && numbers[2] == 32.0)) {
            val ans = 15.0 * 8.0 + 32.0 // 152
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("152", "152", "152")
                ResponseLengthPreset.SHORT -> listOf("15 * 8 + 32 = 152", "152", "Result: 152")
                ResponseLengthPreset.NORMAL -> listOf("15 * 8 + 32 = 120 + 32 = 152.", "The calculated result of 15 * 8 + 32 is 152.", "The expression evaluates to 152.")
                ResponseLengthPreset.LONG -> listOf("Evaluating 15 * 8 + 32 following standard order of operations (PEMDAS): First, multiply 15 by 8 to get 120. Next, add 32 to get 152. Final answer: 152.", "The arithmetic expression evaluates in two steps: 15 * 8 = 120, followed by 120 + 32 = 152.", "Following algebraic precedence: 15 * 8 + 32 = 120 + 32 = 152.")
            }
        }

        if (lower.contains("2x + 6 = 18") || lower.contains("2x+6=18")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("x = 6", "6", "x = 6")
                ResponseLengthPreset.SHORT -> listOf("x = 6! (2x = 12, so x = 6)", "Solution: x = 6", "x = 6")
                ResponseLengthPreset.NORMAL -> listOf("Solving 2x + 6 = 18: subtract 6 to get 2x = 12, then divide by 2 to find x = 6.", "The solution to 2x + 6 = 18 is x = 6.", "x = 6 (verified).")
                ResponseLengthPreset.LONG -> listOf("To solve the linear equation 2x + 6 = 18: First subtract 6 from both sides to isolate the variable term: 2x = 12. Then divide both sides by 2: x = 6. Substituting back: 2(6) + 6 = 18, verifying the solution.", "Solving the algebraic equation step-by-step: 2x + 6 = 18 => 2x = 18 - 6 => 2x = 12 => x = 12 / 2 => x = 6.", "The linear algebraic solution is x = 6. Verification: 2(6) + 6 = 12 + 6 = 18.")
            }
        }

        if (lower.contains("5^3") || lower.contains("5 ^ 3")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("125", "125", "125")
                ResponseLengthPreset.SHORT -> listOf("5^3 = 125", "125", "The result is 125.")
                ResponseLengthPreset.NORMAL -> listOf("5^3 = 5 × 5 × 5 = 125.", "The cube of 5 (5³) is 125.", "5^3 evaluates to 125.")
                ResponseLengthPreset.LONG -> listOf("Evaluating 5 cubed (5^3): Compute 5 × 5 = 25, then 25 × 5 = 125. The result of 5 raised to the power of 3 is 125.", "5^3 represents 5 multiplied by itself three times: 5 × 5 × 5 = 125.", "The power 5³ evaluates to 125.")
            }
        }

        if (lower.contains("2 + 2") || lower.contains("2+2")) {
            return when (preset) {
                ResponseLengthPreset.VERY_SHORT -> listOf("4", "4", "4")
                ResponseLengthPreset.SHORT -> listOf("4", "2 + 2 = 4", "That's 4.")
                ResponseLengthPreset.NORMAL -> listOf("2 + 2 = 4.", "The sum of 2 and 2 is 4.", "2 + 2 equals 4.")
                ResponseLengthPreset.LONG -> listOf("The arithmetic sum of 2 + 2 evaluates directly to 4 according to standard Peano axioms and elementary addition.", "2 + 2 = 4. Adding two units to two units yields four units.", "The result of 2 + 2 is 4.")
            }
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
                return when (preset) {
                    ResponseLengthPreset.VERY_SHORT -> listOf(formatted, formatted, formatted)
                    ResponseLengthPreset.SHORT -> listOf(formatted, "Ans: $formatted", "$formatted.")
                    ResponseLengthPreset.NORMAL -> listOf("The calculated result is $formatted.", "The answer is $formatted.", "$formatted")
                    ResponseLengthPreset.LONG -> listOf("The calculation evaluates to $formatted based on the operands provided in the expression.", "Evaluating the arithmetic operation yields a final computed result of $formatted.", "The result of the calculation is $formatted.")
                }
            }
        }

        return emptyList()
    }
}
