package com.example.util

import android.util.Log

object QuestionValidator {
    private const val TAG = "QuestionValidator"

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // Safe fallback in pure JVM unit test environment
        }
    }

    // Recognized natural language question words/starters (English, conversational, romanized/Hinglish, French, Spanish)
    private val QUESTION_STARTERS = setOf(
        // English Interrogatives & Auxiliaries
        "who", "what", "where", "when", "why", "how", "which", "whose", "whom",
        "is", "are", "was", "were", "am",
        "do", "does", "did",
        "can", "could", "would", "should", "will", "won't", "shall", "may", "might", "must",
        "have", "has", "had", "haven't", "hasn't", "hadn't",
        "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't",
        "can't", "couldn't", "won't", "wouldn't", "shouldn't",
        "any", "anyone", "anybody", "anything", "anywhere",
        // Multi-word starts checked separately: "what if", "how about", "what about", "is it", "are you", "do you", "can you", "could you"

        // Romanized Hindi / Hinglish Interrogatives
        "kya", "kab", "kyu", "kyun", "kaise", "kaisa", "kaisi", "kaise",
        "kidhar", "kaha", "kahan", "konsa", "kaunsa", "kaunsi", "kaunse", "kaun",
        "kisne", "kisko", "kiske", "kitna", "kitni", "kitne", "bolo", "batao",
        "chalega", "hoga", "karoge", "karega", "aarahe", "aaoge",

        // Spanish / French / German
        "que", "qui", "quand", "comment", "pourquoi", "est-ce",
        "que", "que", "como", "cuando", "donde", "por", "quien", "cual",
        "was", "wer", "wo", "wann", "warum", "wie"
    )

    // Patterns that identify URLs, web endpoints, query strings, or server paths
    private val URL_OR_TECHNICAL_PATTERNS = listOf(
        Regex("(?i)^https?://"),
        Regex("(?i)^ftp://"),
        Regex("(?i)^www\\."),
        Regex("(?i)^mailto:"),
        Regex("(?i)^content://"),
        Regex("(?i)^file://"),
        Regex("(?i)\\b(?:com|org|net|io|app|gov|edu|co|ai|xyz|gl|dev|info)/[a-zA-Z0-9_\\-\\./]*\\?"),
        Regex("(?i)\\b(?:com|org|net|io|app|gov|edu|co|ai|xyz|gl|dev|info)\\?[a-zA-Z0-9_\\-=&%]+"),
        Regex("(?i)(?:oauth|signin|login|auth|accountchooser|redirect|callback|api/v\\d|v\\d/)[a-zA-Z0-9_\\-\\./]*\\?"),
        Regex("(?i)(?:continue=|redirect_uri=|client_id=|session_id=|flowName=|authuser=|token=|response_type=|code=|state=)"),
        Regex("(?i)%[0-9a-fA-F]{2}"), // URL percent-encoded characters like %20, %2F
        Regex("(?i)\\.(?:png|jpg|jpeg|gif|svg|webp|pdf|apk|zip|tar|gz|json|xml|js|css|ts|kt|java|py|html|htm|php|jsp)\\?"),
        Regex("(?i)[a-zA-Z0-9_\\-]+\\.[a-zA-Z]{2,6}/[a-zA-Z0-9_\\-\\./]+") // domain.com/path
    )

    // Code and script patterns
    private val CODE_PATTERNS = listOf(
        Regex("(?i)(?:SELECT|INSERT|UPDATE|DELETE)\\s+.*\\s+(?:FROM|INTO|SET|WHERE)"),
        Regex("(?i)(?:public|private|protected)\\s+(?:class|interface|void|fun|var|val)"),
        Regex("(?i)(?:function|def|var|val|const|let)\\s+[a-zA-Z0-9_]+\\s*="),
        Regex("[\\&\\|]{2}|[\\=\\!\\<\\>]{2,3}"), // &&, ||, ==, ===, !==
        Regex("\\b(?:null|undefined|NaN|true|false)\\s*\\?\\s*"),
        Regex("\\?\\s*[a-zA-Z0-9_]+\\s*:\\s*[a-zA-Z0-9_]+"), // ternary op: cond ? a : b
        Regex("<\\/?[a-zA-Z0-9]+.*?>") // HTML/XML tags
    )

    /**
     * Validates whether the given text is grammatically and contextually a genuine
     * natural language question rather than a URL, query parameter string, code fragment,
     * or technical file path.
     */
    fun isGenuineQuestion(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()

        // 1. Length constraints (genuine questions are typically 5 to 300 characters)
        if (trimmed.length < 4 || trimmed.length > 350) {
            return false
        }

        // 2. Must contain question mark
        if (!trimmed.contains("?") && !trimmed.contains("？") && !trimmed.contains("¿")) {
            return false
        }

        // 3. Reject URLs, web paths, authentication redirect links, and query strings
        for (pattern in URL_OR_TECHNICAL_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                logDebug("Rejected URL/technical pattern: \"$trimmed\"")
                return false
            }
        }

        // 4. Reject code syntax and database queries
        for (pattern in CODE_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                logDebug("Rejected code pattern: \"$trimmed\"")
                return false
            }
        }

        // 5. Check character composition: natural language questions consist predominantly of letters and spaces
        val lettersCount = trimmed.count { it.isLetter() }
        val digitsCount = trimmed.count { it.isDigit() }
        val specialCount = trimmed.length - lettersCount - digitsCount - trimmed.count { it.isWhitespace() }

        // Must have at least some letters
        if (lettersCount < 3) {
            return false
        }

        // If symbols/special characters (like /, &, =, %, _) outnumber letters, reject as technical string
        if (specialCount > lettersCount * 0.4) {
            logDebug("Rejected due to high symbol ratio: \"$trimmed\" (special=$specialCount, letters=$lettersCount)")
            return false
        }

        // If multiple slashes '/' exist without space, it's a file or URL path
        val slashCount = trimmed.count { it == '/' || it == '\\' }
        if (slashCount >= 2 && !trimmed.contains(" / ")) {
            logDebug("Rejected path slashes: \"$trimmed\"")
            return false
        }

        // 6. Tokenize words (alphabetic or unicode word tokens)
        val words = trimmed.split(Regex("[\\s,;:\\(\\)\\[\\]\"']+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.isNotBlank() }

        // Must have at least 2 words (or 1 recognized conversational question word like "Why?", "Really?")
        if (words.isEmpty()) {
            return false
        }

        if (words.size == 1) {
            val singleWord = words[0].lowercase()
            val allowedSingleWords = setOf(
                "why", "when", "where", "who", "what", "how", "really", "sure",
                "ready", "done", "available", "interested", "help", "serious",
                "kyu", "kyun", "kab", "kaise", "kidhar", "kaha"
            )
            return allowedSingleWords.contains(singleWord)
        }

        // 7. Check if text looks like natural language (starts with or contains question starter, or natural sentence structure)
        val firstWord = words.firstOrNull()?.lowercase() ?: ""
        val secondWord = if (words.size > 1) words[1].lowercase() else ""
        val combinedStart = "$firstWord $secondWord"

        val hasQuestionStarter = QUESTION_STARTERS.contains(firstWord) ||
                QUESTION_STARTERS.contains(secondWord) ||
                combinedStart in setOf(
            "what if", "how about", "what about", "is it", "are you", "do you",
            "can you", "could you", "would you", "will you", "shall we",
            "you think", "guess what", "right now", "anyone know", "any idea",
            "kya aap", "kya tum", "kya yeh", "bolo na", "sahi hai"
        )

        // If it doesn't start with a question starter, check if it contains a conversational question word or has normal natural sentence structure ending in ?
        if (!hasQuestionStarter) {
            val containsAnyStarter = words.any { QUESTION_STARTERS.contains(it.lowercase()) }
            val endsWithQuestionMark = trimmed.endsWith("?") || trimmed.endsWith("？")

            // If it ends with '?' and has at least 3 natural words with high letter ratio, accept
            if (endsWithQuestionMark && words.size >= 3) {
                return true
            }

            if (!containsAnyStarter) {
                // If it has no known question words and fewer than 3 words, reject false positive
                if (words.size < 3) {
                    logDebug("Rejected: no question starter and too short: \"$trimmed\"")
                    return false
                }
            }
        }

        return true
    }

    /**
     * Extracts and cleans the genuine question sentence from a raw scanned text string.
     */
    fun cleanAndExtractQuestion(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        val questionMarkIndex = trimmed.lastIndexOfAny(charArrayOf('?', '？'))
        if (questionMarkIndex == -1) {
            return if (isGenuineQuestion(trimmed)) trimmed else null
        }

        // Look for the start of the question sentence
        val sentenceStart = trimmed.substring(0, questionMarkIndex)
            .lastIndexOfAny(charArrayOf('\n', '.', '!', ';'))

        val candidate = if (sentenceStart != -1 && sentenceStart < questionMarkIndex) {
            trimmed.substring(sentenceStart + 1, questionMarkIndex + 1).trim()
        } else {
            trimmed.substring(0, questionMarkIndex + 1).trim()
        }

        return if (isGenuineQuestion(candidate)) {
            candidate.take(300)
        } else {
            null
        }
    }
}
