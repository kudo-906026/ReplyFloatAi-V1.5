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

    // Hinglish and South Asian interrogative keywords
    val HINGLISH_QUESTION_WORDS = setOf(
        "kya", "kaise", "kaisa", "kaisi", "kaha", "kahan", "kidhar",
        "kab", "kyu", "kyun", "kitna", "kitni", "kitne", "kaun",
        "kisko", "kisse", "kiska", "kiske", "kare", "karo", "hoga",
        "chalega", "bhai", "yaar", "isko", "usko", "karna"
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

    // Conversational inquiry & confirmation terms that form valid short inquiries with '?'
    val CONVERSATIONAL_INQUIRY_WORDS = setOf(
        "huh", "really", "ready", "agree", "thoughts", "serious", "sure", "okay", "ok",
        "done", "interested", "coming", "free", "busy", "there", "good", "fine", "cool",
        "correct", "right", "wrong", "true", "false", "possible", "safe"
    )

    // Regex matching any standard URL, web link, shortener, or domain.tld/path pattern
    val URL_PATTERN_REGEX = Regex(
        "(?i)(" +
            "https?://[^\\s]+" +
            "|\\bwww\\.[a-zA-Z0-9\\-]+\\.[a-zA-Z0-9.\\-_]+(/[^\\s]*)?" +
            "|\\b(youtu\\.be|t\\.co|bit\\.ly|tinyurl\\.com|goo\\.gl|t\\.me|wa\\.me)/[^\\s]+" +
            "|\\b[a-zA-Z0-9\\-]+\\.(com|org|net|edu|gov|io|ai|co|app|dev|me|info|biz|tv|xyz|uk|ca|de|jp|fr|au|in|ru|br|cn)/[^\\s]+" +
            "|\\b[a-zA-Z0-9\\-]+\\.(com|org|net|edu|gov|io|ai|co|app|dev|me|info|biz|tv|xyz|uk|ca|de|jp|fr|au|in|ru|br|cn)\\?[^\\s]+" +
        ")"
    )

    // Regex matching programming code snippets, file paths, operators, or technical syntax
    private val CODE_OR_TECHNICAL_PATTERNS = listOf(
        // Ternary operator: ' ? ' or '? :' or '?:'
        Regex("\\s\\?\\s"),
        Regex("\\?\\s*:"),
        Regex("\\?:"),
        // Safe-call operator: ?. (e.g. user?.name, obj?.field)
        Regex("\\?\\."),
        // Null assertion / optional types / casting: as?, is?, !!
        Regex("\\b(as\\?|is\\?)\\b"),
        Regex("!!"),
        // Kotlin / Swift / TypeScript type declaration with nullable: e.g. ': String?' or ': User?' or 'var count: Int?' or '<String?>'
        Regex("(:\\s*|as\\s+|is\\s+|<)[A-Z][a-zA-Z0-9_]*\\?\\s*([=,;)\\]>]|\\z)"),
        Regex("\\b(val|var|let|const)\\s+[a-zA-Z0-9_]+\\s*:\\s*[A-Z][a-zA-Z0-9_]*\\?"),
        // C# / Java nullable declaration: e.g. 'int? count' or 'String? name'
        Regex("\\b(int|long|bool|double|float|string)\\?\\s+[a-zA-Z0-9_]+"),
        // SQL query placeholders: 'WHERE ... = ?' or 'VALUES (?)'
        Regex("(?i)\\b(WHERE|VALUES|SET|AND|OR)\\s+[a-zA-Z0-9_]+\\s*(=|<|>|LIKE|IN)\\s*\\?"),
        Regex("(?i)VALUES\\s*\\(\\s*\\?"),
        // Regular expression syntax with ?: or ?= or ?<= or ?! or ?<!
        Regex("\\(\\?[=!:<!]"),
        // CLI command flags: e.g. /? or -?
        Regex("(\\s|^)(/\\?|-\\?)(\\s|\\z)"),
        // System file paths with directory separators: e.g. C:\path\file, /usr/bin, folder/file
        Regex("[a-zA-Z]:\\\\|\\\\|/(etc|usr|bin|var|opt|tmp|home|sys|proc|app|src)/"),
        // File path with extension ending or containing ?: e.g. file.kt?, main.cpp?, test.py?
        Regex("\\.[a-zA-Z0-9]{2,4}\\?"),
        // Code structural tokens: semicolons at end of line preceded by assignment or call
        Regex("=\\s*[^;]+;\\s*$"),
        Regex("^(val|var|fun|def|function|class|interface|import|package|SELECT|INSERT|UPDATE|DELETE)\\s+")
    )

    fun containsUrlPattern(text: String): Boolean {
        return URL_PATTERN_REGEX.containsMatchIn(text)
    }

    fun isTechnicalOrCodeSnippet(text: String): Boolean {
        return CODE_OR_TECHNICAL_PATTERNS.any { it.containsMatchIn(text) }
    }

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

    fun isGrammaticallyPlausibleQuestion(text: String): Boolean {
        val lowerText = text.lowercase()
        val words = extractWords(text)

        // 1. Sentence starter with question word
        if (checkSentenceStarters(text) != null) return true

        // 2. Contains any Wh- word, modal verb, auxiliary verb
        if (words.any { it in QUESTION_WORDS }) return true

        // 2b. Hinglish interrogative words (e.g. "kya", "karo", "isko", "kaise")
        if (words.any { it in HINGLISH_QUESTION_WORDS }) return true

        // 3. Contains conversational inquiry phrase
        if (QUESTION_PHRASES.any { lowerText.contains(it) }) return true

        // 4. Contains conversational inquiry term (e.g. "huh", "really", "ready", "okay", "thoughts", etc.)
        if (words.any { it in CONVERSATIONAL_INQUIRY_WORDS }) return true

        // 5. Math expression / calculation
        if (checkMathNotation(text) != null) return true

        // 6. Multi-line interrogative clause
        if (text.contains("\n") && checkMultiLineQuestion(text) != null) return true

        return false
    }

    fun isNonEnglishOrHinglish(text: String): Boolean {
        val clean = text.trim()
        val lower = clean.lowercase()

        // 1. Non-Latin scripts (Devanagari, Cyrillic, Arabic, Chinese, Japanese, etc.)
        for (char in clean) {
            val block = Character.UnicodeBlock.of(char)
            if (block != Character.UnicodeBlock.BASIC_LATIN &&
                block != Character.UnicodeBlock.LATIN_1_SUPPLEMENT &&
                block != Character.UnicodeBlock.LATIN_EXTENDED_A &&
                block != Character.UnicodeBlock.GENERAL_PUNCTUATION &&
                !char.isWhitespace() && !char.isDigit()
            ) {
                return true
            }
        }

        // 2. Hinglish vocabulary and grammatical markers
        val hinglishMarkers = setOf(
            "kya", "karo", "kar", "kare", "karen", "karna", "karega", "karegi",
            "isko", "usko", "jisko", "kisko", "kaise", "kaisa", "kaisi",
            "kaha", "kahan", "jaha", "jahan", "bol", "bolo", "bhai", "yaar",
            "chal", "chalo", "sun", "suno", "mat", "hoga", "hogi", "hoge",
            "hain", "hai", "tha", "thi", "the", "kyu", "kyun", "aaj", "kal",
            "nahi", "nahin", "haan", "theek", "thik", "accha", "achha", "achhi",
            "batana", "batao", "bata", "dekh", "dekho", "kuch", "kuchh", "mera",
            "meri", "mere", "tera", "teri", "tere", "apna", "apni", "apne",
            "kab", "jab", "tab", "ab", "sab", "hum", "tum", "aap", "unka",
            "inka", "bhejo", "bhej", "de", "do", "le", "lo", "rehne", "rehta",
            "vote", "kidhar", "kitna", "kitni", "kitne", "kaun"
        )

        val words = extractWords(clean)
        val hinglishWordCount = words.count { it in hinglishMarkers }
        if (hinglishWordCount >= 1 && (words.size <= 5 || hinglishWordCount >= 2 || words.any { it in setOf("kya", "karo", "isko", "kaise", "kahan", "kyun", "nahi", "bhai", "yaar") })) {
            return true
        }

        // 3. Foreign non-English markers (Spanish, French, etc.)
        val foreignMarkers = setOf(
            "como", "donde", "cuando", "porque", "hola", "amigo", "gracias",
            "comment", "pourquoi", "bonjour", "merci"
        )
        if (words.any { it in foreignMarkers }) {
            return true
        }

        return false
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

        // 1. Strict URL filtering: ANY text matching a URL pattern is NEVER treated as a question,
        // even if it contains '?' in a query parameter or question words elsewhere in the message.
        if (containsUrlPattern(trimmed)) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "URL_OR_PATH",
                reason = "Text appears to be a URL, link, or path with query parameters",
                extractedQuestionText = trimmed
            )
        }

        // 2. Strict Technical / Code Snippet filtering: reject ternary operators, safe calls, SQL, file paths
        if (isTechnicalOrCodeSnippet(trimmed)) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "REJECTED_TECHNICAL_TEXT",
                reason = "Text contains code syntax, file paths, or technical placeholders rather than a natural conversation question",
                extractedQuestionText = trimmed
            )
        }

        val (hasTriggerMatch, matchedTrigger) = matchesAnyTrigger(trimmed, triggers)

        // MANDATORY CHECK: If detectQuestionsOnly is enabled, text WITHOUT an enabled trigger word/symbol is NEVER a question
        if (detectQuestionsOnly && !hasTriggerMatch) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "NO_QUESTION_MARK",
                reason = "Rejected: Text does not contain any enabled question trigger or question mark",
                extractedQuestionText = trimmed
            )
        }

        // 3. Check for Math Notation / Math Prompts (Must contain math calculation keywords or equation)
        val mathResult = checkMathNotation(trimmed)
        if (mathResult != null) {
            return mathResult
        }

        // 4. Conversational question phrases
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

        // 5. Multi-line checks
        if (trimmed.contains("\n")) {
            val multiLineResult = checkMultiLineQuestion(trimmed)
            if (multiLineResult != null) {
                return multiLineResult
            }
        }

        // 6. Sentence starter with question word
        val starterResult = checkSentenceStarters(trimmed)
        if (starterResult != null && (hasTriggerMatch || !detectQuestionsOnly)) {
            return starterResult
        }

        // 7. Contextual natural language validation for questions with '?' or trigger words
        val isPlausible = isGrammaticallyPlausibleQuestion(trimmed)
        if (isPlausible && (hasTriggerMatch || !detectQuestionsOnly)) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = if (matchedTrigger == "?" || matchedTrigger == "？" || trimmed.contains("?")) "QUESTION_MARK" else "TRIGGER_WORD",
                reason = "Valid conversational question structure detected with trigger '${matchedTrigger ?: "?"}'",
                extractedQuestionText = trimmed
            )
        }

        // 8. If text has '?' or trigger but is NOT grammatically/conversationally plausible as a question
        if (hasTriggerMatch) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "REJECTED_NON_QUESTION_CONTEXT",
                reason = "Question mark '?' or trigger does not appear in a grammatically plausible natural language question structure",
                extractedQuestionText = trimmed
            )
        }

        // 9. If detectQuestionsOnly is disabled, accept all messaging text
        if (!detectQuestionsOnly) {
            return DetectionAnalysisResult(
                isQuestion = true,
                category = "GENERAL_MESSAGING",
                reason = "General message accepted because 'Detect Questions Only' filter is disabled",
                extractedQuestionText = trimmed
            )
        }

        // 10. Otherwise, safely classify as normal non-question messaging text
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

    fun isUrlOrFilePath(text: String): Boolean {
        return containsUrlPattern(text) || isTechnicalOrCodeSnippet(text)
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

            if (firstWord in QUESTION_WORDS || firstWord in HINGLISH_QUESTION_WORDS) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "QUESTION_STARTER",
                    reason = "Sentence begins with interrogative starter '$firstWord' (\"$sentence\")",
                    extractedQuestionText = sentence
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
            val hasQWord = lineWords.any { it in QUESTION_WORDS || it in HINGLISH_QUESTION_WORDS }

            if (hasQMark && hasQWord) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MULTILINE_QUESTION",
                    reason = "Found interrogative clause on Line ${index + 1}: '$line'",
                    extractedQuestionText = line
                )
            }

            val firstWord = lineWords.firstOrNull()
            if (firstWord != null && (firstWord in QUESTION_WORDS || firstWord in HINGLISH_QUESTION_WORDS) && (hasQMark || hasAnyQMark)) {
                return DetectionAnalysisResult(
                    isQuestion = true,
                    category = "MULTILINE_QUESTION",
                    reason = "Found question starter '$firstWord' on Line ${index + 1}: '$line'",
                    extractedQuestionText = line
                )
            }
        }
        return null
    }
}
