package com.example.ai

import com.example.model.DetectionResultType

data class DetectionAnalysisResult(
    val isQuestion: Boolean,
    val category: String,
    val reason: String,
    val extractedQuestionText: String
)

object QuestionDetectionEngine {

    // Common question starter keywords & auxiliary verbs
    private val QUESTION_STARTERS = listOf(
        // Wh- interrogatives
        "what", "what's", "whats", "when", "when's", "whens",
        "where", "where's", "wheres", "which", "who", "who's", "whos",
        "whom", "whose", "why", "why's", "how", "how's", "hows",
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
    // Strictly match mathematical equations (e.g., "2x + 6 = 18", "25 * 4 = ?", "5^3 = ?"), not arbitrary strings with '='
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

        // 1. Check for Math Notation / Math Prompts first
        val mathResult = checkMathNotation(trimmed)
        if (mathResult != null) {
            return mathResult
        }

        // 2. Check for explicit question punctuation (accounting for trailing emojis/quotes/spaces)
        val questionMarkResult = checkQuestionMark(trimmed)
        if (questionMarkResult != null) {
            return questionMarkResult
        }

        // 3. Check for multi-line question structure
        if (trimmed.contains("\n")) {
            val multiLineResult = checkMultiLineQuestion(trimmed)
            if (multiLineResult != null) {
                return multiLineResult
            }
        }

        // 4. Check for sentence-level question starters or interrogative phrases
        val starterResult = checkQuestionStarters(trimmed)
        if (starterResult != null) {
            return starterResult
        }

        // 5. If detectQuestionsOnly is disabled, accept all messaging text
        if (!detectQuestionsOnly) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "GENERAL_MESSAGING",
                reason = "General message accepted because 'Detect Questions Only' filter is disabled",
                extractedQuestionText = trimmed
            )
        }

        // 6. Otherwise, safely classify as normal non-question messaging text
        return DetectionAnalysisResult(
            isQuestion = false,
            category = "NORMAL_STATEMENT",
            reason = "No question mark ('?'), interrogative starter, or math formula detected. Filtered as statement text.",
            extractedQuestionText = trimmed
        )
    }

    private fun checkMathNotation(text: String): DetectionAnalysisResult? {
        val lower = text.lowercase().trim()

        // Check for math keywords (calculate, solve, evaluate, etc.)
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

        // Check for algebraic equations e.g. "2x + 6 = 18", "x^2 + 5 = 30", "5^3 = ?"
        val eqMatch = MATH_EQUATION_REGEX.find(text)
        if (eqMatch != null) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "MATH_EQUATION",
                reason = "Detected mathematical equation structure: '${eqMatch.value}'",
                extractedQuestionText = text
            )
        }

        // Check for arithmetic operations e.g. "15 * 8 + 32", "240 / 6"
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

    private fun checkQuestionMark(text: String): DetectionAnalysisResult? {
        // Strip trailing emojis, closing quotes, brackets, and whitespace
        val sanitizedEnd = text.trimEnd { it.isWhitespace() || it == '"' || it == '\'' || it == ')' || it == ']' || it == '}' || it.isSurrogate() || it.category == CharCategory.OTHER_SYMBOL }

        if (sanitizedEnd.endsWith("?") || sanitizedEnd.endsWith("？") || text.contains("?") || text.contains("？")) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "QUESTION_PUNCTUATION",
                reason = "Detected interrogative punctuation mark '?' in message",
                extractedQuestionText = text
            )
        }
        return null
    }

    private fun checkMultiLineQuestion(text: String): DetectionAnalysisResult? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for ((index, line) in lines.withIndex()) {
            val stripped = line.trimEnd { it.isWhitespace() || it == '"' || it == '\'' || it == ')' }

            // Line ends with question mark
            if (stripped.endsWith("?") || stripped.endsWith("？") || stripped.contains("?")) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MULTILINE_QUESTION",
                    reason = "Found interrogative clause on Line ${index + 1}: '$line'",
                    extractedQuestionText = text
                )
            }

            // Line starts with question starter
            val firstWord = stripped.split(Regex("\\s+")).firstOrNull()?.lowercase()?.trim(',', ':', ';', '\"', '\'')
            if (firstWord != null && QUESTION_STARTERS.contains(firstWord)) {
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

    private fun checkQuestionStarters(text: String): DetectionAnalysisResult? {
        val clean = text.trim()
        val sentences = clean.split(Regex("[.!;]\\s*|\n+")).map { it.trim() }.filter { it.isNotBlank() }

        for (sentence in sentences) {
            val words = sentence.split(Regex("\\s+")).map { it.trim(',', ':', ';', '\"', '\'', '(', ')') }
            val firstWord = words.firstOrNull()?.lowercase() ?: continue

            if (QUESTION_STARTERS.contains(firstWord)) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "QUESTION_STARTER",
                    reason = "Sentence begins with interrogative starter '$firstWord' (\"$sentence\")",
                    extractedQuestionText = clean
                )
            }

            val lowerSentence = sentence.lowercase()
            for (phrase in QUESTION_PHRASES) {
                if (lowerSentence.startsWith(phrase) || lowerSentence.contains(phrase)) {
                    return DetectionAnalysisResult(
                        isQuestion = true,
                        category = "CONVERSATIONAL_PHRASE",
                        reason = "Detected conversational inquiry phrase '$phrase'",
                        extractedQuestionText = clean
                    )
                }
            }
        }

        return null
    }
}
