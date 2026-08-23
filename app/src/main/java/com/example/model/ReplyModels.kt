package com.example.model

import java.util.UUID

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val hint: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        defaultModel = "gemini-2.5-flash",
        hint = "Google AI Studio Key"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        hint = "OpenAI API Key (sk-...)"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Claude",
        defaultModel = "claude-3-5-haiku-20241022",
        hint = "Anthropic Claude Key (sk-ant-...)"
    ),
    GROK(
        id = "grok",
        displayName = "Grok",
        defaultModel = "grok-2-latest",
        hint = "xAI API Key (xai-...)"
    );

    val modelName: String get() = defaultModel

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
        }
    }
}

enum class ProviderSelectionMode(val id: String, val label: String, val description: String) {
    AUTO_FALLBACK(
        id = "auto",
        label = "Auto Fallback Chain",
        description = "Sequentially attempts providers in fallback chain order on error or rate-limit"
    ),
    PREFERRED_PROVIDER(
        id = "preferred",
        label = "Preferred Provider",
        description = "Uses your chosen provider first; falls back to others only if it fails"
    );

    companion object {
        fun fromId(id: String): ProviderSelectionMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO_FALLBACK
        }
    }
}

enum class ReplyLength(val label: String, val promptInstruction: String) {
    ONE_WORD(
        label = "1 word",
        promptInstruction = "Provide exactly ONE single word as the reply (e.g. 'Sure', 'Tomorrow', 'Definitely', 'Unavailable'). Do not include multiple words, quotes, or conversational filler."
    ),
    SHORT(
        label = "short",
        promptInstruction = "Provide a very short reply of 2 to 5 words maximum (e.g., 'Sounds great to me!', 'I will check shortly.')."
    ),
    ONE_LINE(
        label = "1 line",
        promptInstruction = "Provide exactly one clear, complete single-sentence reply that fits on one line."
    ),
    TWO_LINES(
        label = "2 lines",
        promptInstruction = "Provide a concise two-sentence or two-line reply providing brief context and answer."
    ),
    FIVE_TO_SEVEN_LINES(
        label = "5-7 lines",
        promptInstruction = "Provide a comprehensive, well-structured response of 5 to 7 sentences/lines explaining points clearly."
    );

    companion object {
        fun fromLabel(label: String): ReplyLength {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: ONE_LINE
        }
    }
}

enum class ReplyTone(val label: String, val promptInstruction: String) {
    CASUAL("Casual & Friendly", "Friendly, warm, and natural conversational tone."),
    PROFESSIONAL("Professional", "Polite, articulate, respectful, and business-appropriate."),
    CONCISE("Direct", "Direct, straightforward, and to the point without extra fluff."),
    ENTHUSIASTIC("Enthusiastic", "Upbeat, positive, energetic, and engaging."),
    WITTY("Witty", "Light-hearted, clever, and pleasantly playful.")
}

data class ReplyItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val generatedByProvider: AiProvider? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReplySettings(
    val count: Int = 3,
    val length: ReplyLength = ReplyLength.ONE_LINE,
    val tone: ReplyTone = ReplyTone.CASUAL,
    val autoGenerate: Boolean = true,
    val autoDeleteHistory: Boolean = true,
    val autoDeleteMinutes: Int = 5,
    val multiLanguageEnabled: Boolean = false,
    val scanningEnabled: Boolean = true,
    // Provider Selection Mode
    val selectionMode: ProviderSelectionMode = ProviderSelectionMode.AUTO_FALLBACK,
    val preferredProvider: AiProvider = AiProvider.GEMINI,
    // Multi-provider keys
    val customApiKey: String = "", // Legacy alias for gemini
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val claudeApiKey: String = "",
    val grokApiKey: String = "",
    // Fallback chain ordering
    val providerChain: List<AiProvider> = listOf(
        AiProvider.GEMINI,
        AiProvider.OPENAI,
        AiProvider.CLAUDE,
        AiProvider.GROK
    )
) {
    val openAiApiKey: String get() = openaiApiKey

    fun getApiKeyFor(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> geminiApiKey.ifBlank { customApiKey }
            AiProvider.OPENAI -> openaiApiKey
            AiProvider.CLAUDE -> claudeApiKey
            AiProvider.GROK -> grokApiKey
        }.trim()
    }
}

data class DetectedQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val englishMeaning: String? = null,
    val sourceApp: String? = null,
    val generatedByProvider: AiProvider? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuestionDetectionHistory(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val englishMeaning: String? = null,
    val replies: List<String> = emptyList(),
    val sourceApp: String? = null,
    val generatedByProvider: AiProvider? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiReplyResult(
    val original: String,
    val englishMeaning: String? = null,
    val replies: List<String> = emptyList(),
    val provider: AiProvider = AiProvider.GEMINI
)

typealias GeminiReplyResult = AiReplyResult
