package com.example.model

import java.util.UUID

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
    val timestamp: Long = System.currentTimeMillis()
)

data class ReplySettings(
    val count: Int = 3,
    val length: ReplyLength = ReplyLength.ONE_LINE,
    val tone: ReplyTone = ReplyTone.CASUAL,
    val autoGenerate: Boolean = true,
    val autoDeleteHistory: Boolean = true,
    val autoDeleteMinutes: Int = 5,
    val customApiKey: String = ""
)

data class DetectedQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sourceApp: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuestionDetectionHistory(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val replies: List<String> = emptyList(),
    val sourceApp: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
