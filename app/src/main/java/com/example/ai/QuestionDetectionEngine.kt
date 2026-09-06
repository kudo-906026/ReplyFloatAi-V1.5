package com.example.ai

import com.example.model.DetectionResultType

data class DetectionAnalysisResult(
    val isQuestion: Boolean,
    val category: String,
    val reason: String,
    val extractedQuestionText: String
)

object QuestionDetectionEngine {

    // Common question starter keywords & auxiliary interrogative verbs
    val QUESTION_WORDS = setOf(
        // Wh- interrogatives
        "why", "what", "how", "who", "whom", "whose", "when", "where", "which",
        // Contractions
        "what's", "whats", "how's", "hows", "why's", "whys", "who's", "whos",
        "where's", "wheres", "when's", "whens",
        // Modal & auxiliary verbs
        "can", "can't", "cant", "could", "couldn't", "couldnt",
        "would", "wouldn't", "wouldnt", "will", "won't", "wont",
        "should", "shouldn't", "shouldnt", "shall", "may", "might", "must",
        // Be-verbs
        "is", "isn't", "isnt", "are", "aren't", "arent", "am",
        "was", "wasn't", "wasnt", "were", "weren't", "werent",
        // Do-verbs
        "do", "don't", "dont", "does", "doesn't", "doesnt", "did", "didn't", "didnt",
        // Have-verbs
        "have", "haven't", "havent", "has", "hasn't", "hasnt", "had", "hadn't", "hadnt"
    )

    // Math operation symbols & patterns
    private val MATH_OPERATOR_REGEX = Regex("[+\\-*/×÷^%=<>√π∫∑±]")
    private val MATH_ARITHMETIC_REGEX = Regex("(\\b\\d+([.,]\\d+)?\\s*[+\\-*/×÷^%]\\s*\\d+([.,]\\d+)?\\b)")
    private val MATH_EQUATION_REGEX = Regex("(\\b\\d*[a-zA-Z]?\\s*[+\\-*/×÷^]\\s*\\d*[a-zA-Z]?\\s*=\\s*(\\d+|\\?|[a-zA-Z]+)\\b)|(\\b[a-zA-Z]\\s*=\\s*\\d+\\b)")
    private val MATH_PROMPT_KEYWORDS = listOf(
        "calculate", "solve", "evaluate", "compute", "simplify",
        "find x", "find y", "integral", "derivative",
        "square root", "percentage of", "how much is"
    )

    // Conversational question phrases
    private val QUESTION_PHRASES = listOf(
        "let me know if", "any idea", "do you know", "could you tell me",
        "can you tell me", "what do you think", "are you free", "are you available",
        "what time", "how much", "how many", "is it possible", "would it be possible",
        "tell me about", "wondering if", "check if", "wanna", "want to"
    )

    fun matchesAnyTrigger(text: String, triggers: List<com.example.model.TriggerItem>): Pair<Boolean, String?> {
        val enabled = triggers.filter { it.isEnabled && it.pattern.isNotBlank() }
        if (enabled.isEmpty()) {
            return false to null
        }
        val lowerText = text.lowercase().trim()
        val words = extractWords(text)

        for (trigger in enabled) {
            val pattern = trigger.pattern.trim().lowercase()
            if (pattern == "?" || pattern == "？" || pattern == "¿") {
                if (text.contains("?") || text.contains("？") || text.contains("¿")) {
                    return true to pattern
                }
            } else if (pattern.all { it.isLetterOrDigit() || it == '_' }) {
                // Word pattern: match whole word (e.g. "why", "what", "how", "whom", "huh")
                if (words.contains(pattern)) {
                    return true to pattern
                }
            } else {
                // Compound symbol or punctuation (e.g. "huh?", "?!", etc.)
                if (lowerText.contains(pattern)) {
                    return true to pattern
                }
            }
        }
        return false to null
    }

    fun analyze(
        rawText: String,
        detectQuestionsOnly: Boolean,
        triggers: List<com.example.model.TriggerItem> = com.example.state.AppStateManager.settings.value.triggers
    ): DetectionAnalysisResult {
        val trimmed = rawText.trim()

        if (trimmed.length < 3) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "TOO_SHORT",
                reason = "Text length (${trimmed.length} chars) is below minimum threshold (3 chars)",
                extractedQuestionText = trimmed
            )
        }

        // Ignore URLs and file paths that happen to have '?'
        if (isUrlOrFilePath(trimmed)) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "URL_OR_PATH",
                reason = "Text appears to be a URL, link, or path with query parameters",
                extractedQuestionText = trimmed
            )
        }

        val (hasTriggerMatch, matchedTrigger) = matchesAnyTrigger(trimmed, triggers)

        // MANDATORY CHECK: If detectQuestionsOnly is enabled, text WITHOUT an enabled trigger word/symbol is NEVER a question
        if (detectQuestionsOnly && !hasTriggerMatch) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "NO_QUESTION_TRIGGER",
                reason = "Rejected: Text does not contain any enabled question trigger word or symbol",
                extractedQuestionText = trimmed
            )
        }

        // 1. Check for Math Notation / Math Prompts (Must contain math calculation keywords or equation)
        val mathResult = checkMathNotation(trimmed)
        if (mathResult != null) {
            return mathResult
        }

        // 2. If trigger matched, accept question
        if (hasTriggerMatch && matchedTrigger != null) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = if (matchedTrigger == "?" || matchedTrigger == "？") "QUESTION_MARK" else "TRIGGER_WORD",
                reason = "Contains enabled question trigger '$matchedTrigger'",
                extractedQuestionText = trimmed
            )
        }

        // 3. Conversational question phrases
        val lowerText = trimmed.lowercase()
        for (phrase in QUESTION_PHRASES) {
            if (lowerText.contains(phrase)) {
                if (hasTriggerMatch || !detectQuestionsOnly) {
                    return DetectionAnalysisResult(
                        isQuestion = true,
                        category = "CONVERSATIONAL_PHRASE",
                        reason = "Detected conversational inquiry phrase '$phrase'",
                        extractedQuestionText = trimmed
                    )
                }
            }
        }

        // 4. Multi-line checks
        if (trimmed.contains("\n")) {
            val multiLineResult = checkMultiLineQuestion(trimmed)
            if (multiLineResult != null) {
                return multiLineResult
            }
        }

        // 5. Sentence starter with question word
        val starterResult = checkSentenceStarters(trimmed)
        if (starterResult != null && (hasTriggerMatch || !detectQuestionsOnly)) {
            return starterResult
        }

        // 6. If detectQuestionsOnly is disabled, accept all messaging text
        if (!detectQuestionsOnly) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "GENERAL_MESSAGING",
                reason = "General message accepted because 'Detect Questions Only' filter is disabled",
                extractedQuestionText = trimmed
            )
        }

        // 7. Otherwise, safely classify as normal non-question messaging text
        return DetectionAnalysisResult(
            isQuestion = false,
            category = "NORMAL_STATEMENT",
            reason = "No question trigger or interrogative structure detected.",
            extractedQuestionText = trimmed
        )
    }

    private fun extractWords(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[\\s,;:.!?\"'()\\[\\]{}]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun isUrlOrFilePath(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("www.") ||
                lower.startsWith("file://") ||
                (lower.contains("?") && (lower.contains("utm_") || lower.contains(".com/") || lower.contains(".org/") || lower.contains(".net/")))
    }

    private fun checkMathNotation(text: String): DetectionAnalysisResult? {
        val lower = text.lowercase().trim()

        for (kw in MATH_PROMPT_KEYWORDS) {
            if (lower.contains(kw)) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MATH_PROMPT",
                    reason = "Detected mathematical prompt keyword '$kw'",
                    extractedQuestionText = text
                )
            }
        }

        val eqMatch = MATH_EQUATION_REGEX.find(text)
        if (eqMatch != null) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "MATH_EQUATION",
                reason = "Detected mathematical equation structure: '${eqMatch.value}'",
                extractedQuestionText = text
            )
        }

        val arithMatch = MATH_ARITHMETIC_REGEX.find(text)
        if (arithMatch != null && (text.contains("?") || lower.startsWith("what") || lower.startsWith("how") || lower.startsWith("is") || text.contains("="))) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "MATH_EXPRESSION",
                reason = "Detected arithmetic calculation: '${arithMatch.value}'",
                extractedQuestionText = text
            )
        }

        return null
    }

    private fun checkSentenceStarters(text: String): DetectionAnalysisResult? {
        val sentences = text.split(Regex("[.!;]\\s*|\n+")).map { it.trim() }.filter { it.isNotBlank() }

        for (sentence in sentences) {
            val words = extractWords(sentence)
            val firstWord = words.firstOrNull() ?: continue

            if (firstWord in QUESTION_WORDS) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "QUESTION_STARTER",
                    reason = "Sentence begins with interrogative starter '$firstWord' (\"$sentence\")",
                    extractedQuestionText = text
                )
            }
        }
        return null
    }

    private fun checkMultiLineQuestion(text: String): DetectionAnalysisResult? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val hasAnyQMark = text.contains("?") || text.contains("？") || text.contains("¿")

        for ((index, line) in lines.withIndex()) {
            val lineWords = extractWords(line)
            val hasQMark = line.contains("?") || line.contains("？") || line.contains("¿")
            val hasQWord = lineWords.any { it in QUESTION_WORDS }

            if (hasQMark && hasQWord) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MULTILINE_QUESTION",
                    reason = "Found interrogative clause on Line ${index + 1}: '$line'",
                    extractedQuestionText = text
                )
            }

            val firstWord = lineWords.firstOrNull()
            if (firstWord != null && firstWord in QUESTION_WORDS && (hasQMark || hasAnyQMark)) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MULTILINE_QUESTION",
                    reason = "Found question starter '$firstWord' on Line ${index + 1}: '$line'",
                    extractedQuestionText = text
                )
            }
        }
        return null
    }
}
