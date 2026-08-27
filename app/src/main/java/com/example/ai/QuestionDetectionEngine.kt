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

    fun analyze(rawText: String, detectQuestionsOnly: Boolean): DetectionAnalysisResult {
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

        // Check if text contains interrogative punctuation mark '?' or '？' or '¿'
        val hasQuestionMark = trimmed.contains("?") || trimmed.contains("？") || trimmed.contains("¿")

        // MANDATORY CHECK: If detectQuestionsOnly is enabled, text WITHOUT a '?' is NEVER a detected question
        if (detectQuestionsOnly && !hasQuestionMark) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "NO_QUESTION_MARK",
                reason = "Rejected: Text does not contain a question mark '?' (Mandatory question mark check)",
                extractedQuestionText = trimmed
            )
        }

        // 1. Check for Math Notation / Math Prompts (Must contain '?' or math calculation keywords)
        val mathResult = checkMathNotation(trimmed)
        if (mathResult != null) {
            return mathResult
        }

        // 2. Extract words
        val words = extractWords(trimmed)

        // Find if text contains any question words
        val matchedQuestionWord = words.firstOrNull { it in QUESTION_WORDS }

        // 3. Combined Question Word + Question Mark detection (Highest priority)
        if (hasQuestionMark && matchedQuestionWord != null) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "QUESTION_WORD_AND_MARK",
                reason = "Contains question mark '?' combined with question word '$matchedQuestionWord'",
                extractedQuestionText = trimmed
            )
        }

        // 4. Conversational question phrases with question mark (e.g. "let me know if...?", "are you free...?")
        val lowerText = trimmed.lowercase()
        for (phrase in QUESTION_PHRASES) {
            if (lowerText.contains(phrase)) {
                if (hasQuestionMark || !detectQuestionsOnly) {
                    return DetectionAnalysisResult(
                        isQuestion = true,
                        category = "CONVERSATIONAL_PHRASE",
                        reason = "Detected conversational inquiry phrase '$phrase' with '?'",
                        extractedQuestionText = trimmed
                    )
                }
            }
        }

        // 5. Multi-line checks with question mark
        if (trimmed.contains("\n")) {
            val multiLineResult = checkMultiLineQuestion(trimmed)
            if (multiLineResult != null) {
                return multiLineResult
            }
        }

        // 6. Sentence starter with question word AND question mark
        val starterResult = checkSentenceStarters(trimmed)
        if (starterResult != null && (hasQuestionMark || !detectQuestionsOnly)) {
            return starterResult
        }

        // 7. If text has '?' and is short (<= 6 words) and sounds like a query
        if (hasQuestionMark && words.size in 1..6 && !isUrlOrFilePath(trimmed)) {
            val lastWord = words.lastOrNull() ?: ""
            if (lastWord in listOf("right", "really", "sure", "correct", "true", "ready", "ok", "okay", "yes", "no") || words.size <= 4) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "SHORT_QUESTION",
                    reason = "Short conversational query with question mark '?'",
                    extractedQuestionText = trimmed
                )
            }
        }

        // 8. If detectQuestionsOnly is disabled, accept all messaging text
        if (!detectQuestionsOnly) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "GENERAL_MESSAGING",
                reason = "General message accepted because 'Detect Questions Only' filter is disabled",
                extractedQuestionText = trimmed
            )
        }

        // 9. Otherwise, safely classify as normal non-question messaging text
        return DetectionAnalysisResult(
            isQuestion = false,
            category = "NORMAL_STATEMENT",
            reason = if (hasQuestionMark) {
                "Punctuation '?' found but missing interrogative question words (why, what, how, who, etc.)"
            } else {
                "No question mark '?' or interrogative structure detected."
            },
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
