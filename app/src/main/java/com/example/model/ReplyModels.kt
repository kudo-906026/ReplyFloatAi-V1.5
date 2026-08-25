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
        defaultModel = "gemini-3.1-flash-lite-preview",
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

enum class ReplyTone(
    val label: String,
    val description: String,
    val promptInstruction: String
) {
    CASUAL(
        label = "Casual & Friendly",
        description = "Friendly, warm, and natural conversational tone.",
        promptInstruction = "Friendly, warm, and natural conversational tone."
    ),
    PROFESSIONAL(
        label = "Professional",
        description = "Polite, articulate, respectful, and business-appropriate.",
        promptInstruction = "Polite, articulate, respectful, and business-appropriate."
    ),
    CONCISE(
        label = "Direct",
        description = "Direct, straightforward, and to the point without extra fluff.",
        promptInstruction = "Direct, straightforward, and to the point without extra fluff."
    ),
    ENTHUSIASTIC(
        label = "Enthusiastic",
        description = "Upbeat, positive, energetic, and engaging.",
        promptInstruction = "Upbeat, positive, energetic, and engaging."
    ),
    WITTY(
        label = "Witty",
        description = "Light-hearted, clever, and pleasantly playful.",
        promptInstruction = "Light-hearted, clever, and pleasantly playful."
    ),
    TRASH_TALK(
        label = "Trash Talk",
        description = "Savage, teasing, and playfully roasting — banter-heavy comebacks.",
        promptInstruction = "Savage, teasing, and playfully roasting persona delivering banter-heavy comebacks. Write witty and cutting replies in a joking, competitive-banter style (like friendly trash talk between friends; humorous and sharp, not genuinely cruel or targeting real vulnerabilities), while fully answering the detected question in this persona's voice."
    ),
    LORD(
        label = "Lord",
        description = "A theatrical, larger-than-life persona that speaks as an all-powerful sovereign being — grandiose, commanding, and mythic in tone, referring to the user's contact as 'servant' or 'mortal' and framing every answer as a divine pronouncement.",
        promptInstruction = "A theatrical, larger-than-life fictional roleplay persona of an all-powerful sovereign being. Speak in a grandiose, commanding, and mythic tone, addressing the user's contact as 'servant' or 'mortal' and framing every answer as a divine pronouncement or royal decree, while still answering the actual detected question in this persona's voice."
    );

    companion object {
        fun fromName(name: String): ReplyTone {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CASUAL
        }
    }
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

enum class ApiCallStatus {
    IN_FLIGHT,
    SUCCESS,
    FAILED
}

data class ApiCallLog(
    val id: String = UUID.randomUUID().toString(),
    val provider: AiProvider,
    val model: String,
    val question: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ApiCallStatus = ApiCallStatus.IN_FLIGHT,
    val durationMs: Long = 0L,
    val repliesCount: Int = 0,
    val error: String? = null
)
