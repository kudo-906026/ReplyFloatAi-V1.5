package com.example.model

import java.util.UUID

enum class AiProviderType {
    GEMINI_BUILTIN,
    GEMINI_API,
    OPENAI,
    ANTHROPIC,
    DEEPSEEK,
    GROQ,
    CUSTOM_REST
}

enum class AiModelTier(val label: String, val badge: String) {
    BALANCED("Balanced", "Fast & Smart"),
    LIGHTWEIGHT("Lightweight", "Ultra-Fast"),
    PRO("Pro / Reasoning", "Deep Reasoning")
}

data class AiProvider(
    val id: String,
    val type: AiProviderType,
    val name: String,
    val displayName: String,
    val modelName: String,
    val apiKey: String = "",
    val customEndpoint: String? = null,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val tier: AiModelTier = AiModelTier.BALANCED,
    val latencyMs: Long? = null,
    val isCustom: Boolean = false,
    val statusText: String = "Ready"
)

enum class ReplyTone(
    val label: String,
    val description: String,
    val systemPromptHint: String,
    val exampleReply: String
) {
    CASUAL(
        label = "Casual & Friendly",
        description = "Relaxed, informal, and friendly tone for social apps",
        systemPromptHint = "Keep the reply casual, brief, warm, friendly, natural conversational texting style.",
        exampleReply = "Hey! Sounds good to me, see you then!"
    ),
    PROFESSIONAL(
        label = "Professional & Crisp",
        description = "Polite, concise, business-appropriate replies",
        systemPromptHint = "Keep the reply professional, polite, concise, and articulate. Business messaging tone.",
        exampleReply = "Received with thanks. I'll review and follow up shortly."
    ),
    CONCISE(
        label = "Ultra-Concise",
        description = "1 to 5 words max for rapid lightning responses",
        systemPromptHint = "Extremely short reply. 1 to 5 words maximum.",
        exampleReply = "Sounds great, will do."
    ),
    WITTY(
        label = "Witty & Fun",
        description = "Playful, lighthearted, and clever responses",
        systemPromptHint = "Slightly witty, playful, and fun tone, yet helpful and not overly sarcastic.",
        exampleReply = "Count me in! What's the plan?"
    ),
    EMPATHETIC(
        label = "Empathetic & Supportive",
        description = "Caring, understanding, and encouraging tone",
        systemPromptHint = "Compassionate, warm, empathetic, and supportive tone.",
        exampleReply = "I understand completely. Take your time, no rush at all!"
    ),
    TECHNICAL(
        label = "Technical & Precise",
        description = "Direct, factual, and mathematically accurate",
        systemPromptHint = "Technical, factual, direct, precise response.",
        exampleReply = "Confirmed. The latency is within the acceptable threshold."
    ),
    TRASH_TALK(
        label = "Trash Talk",
        description = "Savage, witty, competitive banter — playful roasting, not genuinely abusive.",
        systemPromptHint = "Savage, sharp, cutting, confident comebacks and playful put-downs about someone's argument, logic, or taste — like friendly trash talk between friends. Keep it playful-aggressive, not genuinely offensive. HARD SAFETY RULE: Never include sexual content, slurs, threats, or degrading language about someone's family members. When Lang mode is ON or the detected message is in Hinglish or another language, reply in that same language/script with natural casual slang appropriate to it, not a literal translation.",
        exampleReply = "Bold claim for someone whose logic is currently held together by hope and duct tape! 😂"
    )
}

enum class ResponseLengthPreset(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val approxChars: String,
    val maxWords: Int,
    val charCeiling: Int,
    val maxTokens: Int,
    val promptInstruction: String
) {
    VERY_SHORT(
        title = "Very Short",
        shortLabel = "Very Short",
        subtitle = "1 - 5 words",
        approxChars = "~10-30 chars",
        maxWords = 5,
        charCeiling = 35,
        maxTokens = 60,
        promptInstruction = "CRITICAL LENGTH CONSTRAINT: Provide ultra-short replies strictly between 1 and 5 words total (maximum 5 words / ~30 characters). Do NOT output complete long sentences or polite conversational filler. Examples: 'Yes, confirmed.', 'At 3:30 PM.', 'Sounds great!', 'Alexander Graham Bell.', 'i² = -1'."
    ),
    SHORT(
        title = "Short",
        shortLabel = "Short",
        subtitle = "1 concise sentence",
        approxChars = "~30-80 chars",
        maxWords = 14,
        charCeiling = 85,
        maxTokens = 120,
        promptInstruction = "CRITICAL LENGTH CONSTRAINT: Provide short replies consisting of exactly 1 concise, direct sentence (maximum 14 words / ~80 characters). Example: 'Alexander Graham Bell invented the telephone in 1876.'"
    ),
    NORMAL(
        title = "Normal",
        shortLabel = "Normal",
        subtitle = "1 - 2 sentences",
        approxChars = "~80-160 chars",
        maxWords = 28,
        charCeiling = 170,
        maxTokens = 250,
        promptInstruction = "CRITICAL LENGTH CONSTRAINT: Provide normal-length replies of 1 to 2 standard sentences giving clear context and balanced explanation (around 15 to 28 words / ~140 characters). Example: 'Alexander Graham Bell invented the telephone in 1876, receiving the first official US patent for electromagnetic voice transmission.'"
    ),
    LONG(
        title = "Detailed",
        shortLabel = "Detailed",
        subtitle = "Complete explanation",
        approxChars = "~160-320 chars",
        maxWords = 65,
        charCeiling = 350,
        maxTokens = 500,
        promptInstruction = "CRITICAL LENGTH CONSTRAINT: Provide detailed, comprehensive replies of 2 to 4 thorough sentences providing full background context, reasoning, and clear explanation (around 35 to 65 words / ~250 characters). Example: 'Alexander Graham Bell was awarded the first U.S. patent for the telephone on March 7, 1876 (Patent No. 174,465). His breakthrough enabled real-time acoustic speech transmission over electrical telegraph wires, transforming modern telecommunications.'"
    )
}

enum class UnderstandingSummaryLength(
    val label: String,
    val description: String,
    val exampleText: String
) {
    EXTREMELY_CONCISE("Micro (1-3 Words)", "Ultra-compact intent tag", "Reschedule sync"),
    BALANCED("Balanced (1 Sentence)", "Clean synthesis of underlying intent", "Inquiring about meeting availability at 4 PM"),
    DETAILED("Detailed Analysis", "Full breakdown of intent, context, and nuance", "Asking for clarification on project timeline due to upcoming deadline")
}

enum class AutoPurgeTimerOption(val minutes: Int, val label: String, val description: String) {
    MIN_1(1, "1 Min", "Purge after 1 minute"),
    MIN_2(2, "2 Min", "Purge after 2 minutes"),
    MIN_3(3, "3 Min", "Purge after 3 minutes"),
    MIN_4(4, "4 Min", "Purge after 4 minutes"),
    MIN_5(5, "5 Min", "Purge after 5 minutes"),
    MIN_6(6, "6 Min", "Purge after 6 minutes"),
    MIN_7(7, "7 Min", "Purge after 7 minutes"),
    MIN_8(8, "8 Min", "Purge after 8 minutes"),
    MIN_9(9, "9 Min", "Purge after 9 minutes"),
    MIN_10(10, "10 Min", "Purge after 10 minutes"),
    NEVER(0, "Disabled", "Keep until manually cleared")
}

enum class OverlayBarStyle(
    val title: String,
    val description: String
) {
    MINIMAL_PILL("Floating Pill", "Compact pill with expandable suggestions sheet"),
    DOCK_BOTTOM("Bottom Dock", "Sticky dock above keyboard and active input node"),
    FLOATING_BUBBLE("Floating Bubble", "Draggable circular avatar expanding into a popup tray"),
    HEADER_BAR("Top Status Bar", "Subtle top banner pinned below system notification bar")
}

enum class OverlayInteractionMode(
    val title: String,
    val description: String
) {
    FLOATING_DRAGGABLE("Freely Draggable", "Move anywhere on screen; snaps softly to edges"),
    ANCHORED_TO_INPUT("Anchor to Input Field", "Automatically hovers 12dp above focused typing area"),
    LOCKED_SIDEBAR("Edge Side Drawer", "Collapses into a discreet edge tab when idle")
}

data class WhitelistedApp(
    val packageName: String,
    val appName: String,
    val category: String = "Messaging",
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false
)

data class SavedOverlayPosition(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val x: Int,
    val y: Int
)

data class ReplySettings(
    val preferredProvider: AiProvider = defaultBuiltInProviders()[0],
    val fallbackOrder: List<String> = listOf("openai", "gemini-api", "gemini-builtin", "anthropic", "groq"),
    val providerApiKeys: Map<String, String> = emptyMap(),
    val providerModelOverrides: Map<String, String> = emptyMap(),
    val tone: ReplyTone = ReplyTone.CASUAL,
    val count: Int = 3,
    val autoGenerate: Boolean = true,
    val detectQuestionsOnly: Boolean = true,
    val prefetchOnAppFocus: Boolean = true,
    val autoCopySingleReply: Boolean = false,
    val understandingMode: Boolean = true,
    val understandingSummaryLength: UnderstandingSummaryLength = UnderstandingSummaryLength.BALANCED,
    val expandableReplies: Boolean = true,
    val responseLengthPreset: ResponseLengthPreset = ResponseLengthPreset.SHORT,
    val customCharLimit: Int = 120,
    val replyAutoDeleteMinutes: Int = 1,
    val historyPurgeMinutes: Int = 5,
    val autoPurgeTimerMinutes: Int = 5,
    val cacheRetentionMinutes: Int = 5,
    val historyRetentionDays: Int = 7,
    val continuousScreenAnalysis: Boolean = true,
    val realTimeNodeTracking: Boolean = true,
    val smartDebounceMs: Int = 300,
    val overlayBarStyle: OverlayBarStyle = OverlayBarStyle.MINIMAL_PILL,
    val overlayInteractionMode: OverlayInteractionMode = OverlayInteractionMode.FLOATING_DRAGGABLE,
    val autoHideEnabled: Boolean = true,
    val autoHideDelaySec: Int = 12,
    val screenIdleTimeoutSec: Int = 30,
    val overlayOpacity: Float = 0.95f,
    val overlayCornerRadius: Int = 18,
    val overlayTextSizeSp: Int = 13,
    val savedPositions: List<SavedOverlayPosition> = emptyList(),
    val appsWhitelist: List<WhitelistedApp> = defaultWhitelistedApps(),
    val customProviders: List<AiProvider> = emptyList(),
    val enableOcrFallback: Boolean = true,
    val ocrDebounceMs: Int = 1200
)

fun defaultBuiltInProviders(): List<AiProvider> = listOf(
    AiProvider(
        id = "openai",
        type = AiProviderType.OPENAI,
        name = "openai",
        displayName = "OpenAI GPT-4o Mini",
        modelName = "gpt-4o-mini",
        apiKey = "",
        tier = AiModelTier.BALANCED
    ),
    AiProvider(
        id = "gemini-api",
        type = AiProviderType.GEMINI_API,
        name = "gemini-api",
        displayName = "Gemini Flash Lite API",
        modelName = "gemini-3.1-flash-lite",
        apiKey = "",
        tier = AiModelTier.PRO
    ),
    AiProvider(
        id = "gemini-builtin",
        type = AiProviderType.GEMINI_BUILTIN,
        name = "gemini-builtin",
        displayName = "Gemini Flash Lite (Built-in)",
        modelName = "gemini-3.1-flash-lite",
        isBuiltIn = true,
        tier = AiModelTier.LIGHTWEIGHT
    ),
    AiProvider(
        id = "anthropic",
        type = AiProviderType.ANTHROPIC,
        name = "anthropic",
        displayName = "Anthropic Claude 3.5 Haiku",
        modelName = "claude-3-5-haiku-20241022",
        apiKey = "",
        tier = AiModelTier.BALANCED
    ),
    AiProvider(
        id = "groq",
        type = AiProviderType.GROQ,
        name = "groq",
        displayName = "Groq (openai/gpt-oss-120b)",
        modelName = "openai/gpt-oss-120b",
        apiKey = "",
        customEndpoint = "https://api.groq.com/openai/v1/chat/completions",
        tier = AiModelTier.LIGHTWEIGHT
    )
)

fun defaultWhitelistedApps(): List<WhitelistedApp> = listOf(
    WhitelistedApp("com.whatsapp", "WhatsApp", "Messaging", true),
    WhitelistedApp("org.telegram.messenger", "Telegram", "Messaging", true),
    WhitelistedApp("com.facebook.orca", "Messenger", "Social", true),
    WhitelistedApp("com.instagram.android", "Instagram Direct", "Social", true),
    WhitelistedApp("com.discord", "Discord", "Community", true),
    WhitelistedApp("com.slack", "Slack", "Workplace", true),
    WhitelistedApp("com.google.android.apps.messaging", "Google Messages (SMS)", "SMS", true),
    WhitelistedApp("com.google.android.gm", "Gmail", "Email", true),
    WhitelistedApp("com.microsoft.teams", "Microsoft Teams", "Workplace", true),
    WhitelistedApp("com.twitter.android", "X (Twitter DMs)", "Social", true),
    WhitelistedApp("com.linkedin.android", "LinkedIn Messaging", "Professional", true),
    WhitelistedApp("com.reddit.frontpage", "Reddit Chat", "Social", true),
    WhitelistedApp("com.je.supersus", "Super Sus (Who Is The Impostor)", "Gaming / Custom Canvas", true),
    WhitelistedApp("com.piogame.supersus", "Super Sus (Global)", "Gaming / Custom Canvas", true),
    WhitelistedApp("com.innersloth.spacemafia", "Among Us", "Gaming / Custom Canvas", true),
    WhitelistedApp("com.roblox.client", "Roblox Chat", "Gaming / Custom Canvas", true)
)

enum class DetectionMethod(val label: String, val shortBadge: String) {
    ACCESSIBILITY("Accessibility Node Scan", "FAST (Primary)"),
    MLKIT_OCR("ML Kit On-Device OCR", "OCR Fallback")
}

data class DetectedQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sourceApp: String? = null,
    val packageName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val englishMeaning: String? = null,
    val generatedByProvider: AiProvider? = null,
    val fallbackNotice: String? = null,
    val detectionMethod: DetectionMethod = DetectionMethod.ACCESSIBILITY,
    val ocrLatencyMs: Long? = null
)

data class ReplyItem(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val text: String,
    val tone: ReplyTone,
    val generatedByProvider: AiProvider? = null,
    val fallbackNotice: String? = null,
    val isCustomized: Boolean = false
)

enum class DetectionResultType(val label: String) {
    MATCHED("MATCHED"),
    REJECTED("REJECTED")
}

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val rawText: String,
    val result: DetectionResultType,
    val category: String,
    val reason: String,
    val detectionMethod: DetectionMethod = DetectionMethod.ACCESSIBILITY,
    val latencyMs: Long? = null,
    // Explicit Screenshot & On-Device OCR Telemetry Fields
    val screenshotCaptured: Boolean? = null,
    val imageDimensions: String? = null,
    val isImageBlank: Boolean? = null,
    val ocrRawOutput: String? = null,
    val ocrError: String? = null
)

