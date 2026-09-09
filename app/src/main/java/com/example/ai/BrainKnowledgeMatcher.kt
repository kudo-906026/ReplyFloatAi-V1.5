package com.example.ai

import com.example.model.BrainKnowledgeEntry
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset

object BrainKnowledgeMatcher {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "what", "how", "who", "whom", "whose",
        "which", "why", "when", "where", "can", "could", "would", "should", "do", "does",
        "did", "have", "has", "had", "you", "your", "my", "me", "i", "we", "they", "them",
        "it", "its", "to", "for", "in", "on", "at", "by", "from", "of", "and", "or", "so",
        "if", "with", "about", "tell", "give", "please", "bhai", "kya", "hai", "ho", "ka", "ki"
    )

    /**
     * Scores and retrieves the most relevant Brain knowledge entries for a detected question and app context.
     */
    fun findRelevantEntries(
        question: String,
        sourceApp: String? = null,
        entries: List<BrainKnowledgeEntry>,
        limit: Int = 3
    ): List<BrainKnowledgeEntry> {
        val enabledEntries = entries.filter { it.isEnabled && it.content.isNotBlank() }
        if (enabledEntries.isEmpty() || question.isBlank()) return emptyList()

        val cleanQuestion = question.lowercase().trim()
        val questionTokens = cleanQuestion.split(Regex("[\\s,?.!:;\"'()\\[\\]\\-_/]+"))
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

        val appLower = sourceApp?.lowercase()?.trim() ?: ""

        val scored = enabledEntries.mapNotNull { entry ->
            var score = 0
            val titleLower = entry.title.lowercase()
            val contentLower = entry.content.lowercase()
            val categoryLower = entry.category.lowercase()
            val tagsLower = entry.tags.map { it.lowercase() }

            // 1. Direct or partial containment in title
            if (cleanQuestion.contains(titleLower) || titleLower.contains(cleanQuestion)) {
                score += 45
            }

            // 2. Token overlap with title (strongest semantic indicator)
            for (token in questionTokens) {
                if (titleLower.contains(token)) {
                    score += 16
                }
                if (tagsLower.any { it.contains(token) || token.contains(it) }) {
                    score += 14
                }
                if (contentLower.contains(token)) {
                    score += 8
                }
            }

            // 3. Category matching sourceApp (e.g. question in "Super Sus" matching "Super Sus Quiz")
            if (appLower.isNotBlank()) {
                if (categoryLower.contains(appLower) || appLower.contains(categoryLower)) {
                    score += 22
                }
                if (titleLower.contains(appLower) || tagsLower.any { it.contains(appLower) }) {
                    score += 15
                }
            }

            // 4. Check if question explicitly mentions category words
            val categoryTokens = categoryLower.split(" ").filter { it.length > 3 }
            for (catWord in categoryTokens) {
                if (cleanQuestion.contains(catWord)) {
                    score += 12
                }
            }

            if (score >= 12) {
                entry to score
            } else {
                null
            }
        }

        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Formats retrieved Brain knowledge entries into a clear system prompt instruction.
     */
    fun formatKnowledgePrompt(relevantEntries: List<BrainKnowledgeEntry>): String {
        if (relevantEntries.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\nCRITICAL USER KNOWLEDGE BASE (BRAIN STORED FACTS):\n")
        sb.append("The user has explicitly saved verified knowledge and trivia in their personal Brain memory:\n")
        relevantEntries.forEachIndexed { idx, entry ->
            sb.append("${idx + 1}. [Category: ${entry.category}] \"${entry.title}\":\n   \"${entry.content}\"\n")
        }
        sb.append("HARD MANDATE: You MUST prioritize and draw directly upon these verified Brain facts above general assumptions. If the detected question asks about these topics, answer using the exact facts stated above with authentic conversational phrasing!\n")
        return sb.toString()
    }

    /**
     * Generates intelligent local replies using matched Brain knowledge directly, without needing cloud API calls.
     */
    fun buildLocalRepliesFromKnowledge(
        question: String,
        entry: BrainKnowledgeEntry,
        tone: ReplyTone,
        preset: ResponseLengthPreset,
        count: Int = 3
    ): List<String> {
        val content = entry.content.trim()
        val sentences = content.split(Regex("[.\n]+")).map { it.trim() }.filter { it.isNotBlank() }

        // Find the sentence that best matches the question tokens
        val qTokens = question.lowercase().split(Regex("[\\s,?.!:;\"'()\\[\\]\\-_/]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }

        val bestSentence = sentences.maxByOrNull { sentence ->
            val sLower = sentence.lowercase()
            qTokens.count { token -> sLower.contains(token) }
        } ?: sentences.firstOrNull() ?: content

        val candidates = mutableListOf<String>()

        when (preset) {
            ResponseLengthPreset.VERY_SHORT -> {
                when (tone) {
                    ReplyTone.CASUAL -> {
                        candidates.add(bestSentence)
                        candidates.add("It's $bestSentence")
                        candidates.add(sentences.firstOrNull() ?: bestSentence)
                    }
                    ReplyTone.PROFESSIONAL -> {
                        candidates.add(bestSentence)
                        candidates.add("Confirmed: $bestSentence")
                        candidates.add("Based on recorded data: $bestSentence")
                    }
                    ReplyTone.WITTY -> {
                        candidates.add("Easy: $bestSentence!")
                        candidates.add("$bestSentence — 100% verified.")
                        candidates.add("Definitely $bestSentence!")
                    }
                    else -> {
                        candidates.add(bestSentence)
                        candidates.add("It is $bestSentence")
                        candidates.add(sentences.firstOrNull() ?: bestSentence)
                    }
                }
            }
            ResponseLengthPreset.SHORT -> {
                when (tone) {
                    ReplyTone.CASUAL -> {
                        candidates.add("$bestSentence!")
                        candidates.add("According to notes: $bestSentence.")
                        candidates.add("That's $bestSentence!")
                    }
                    ReplyTone.PROFESSIONAL -> {
                        candidates.add("According to recorded details, $bestSentence.")
                        candidates.add("Verified information: $bestSentence.")
                        candidates.add("The recorded answer is $bestSentence.")
                    }
                    ReplyTone.WITTY -> {
                        candidates.add("I know this one! $bestSentence.")
                        candidates.add("That's an easy one: $bestSentence!")
                        candidates.add("Looked it up in my memory bank: $bestSentence!")
                    }
                    else -> {
                        candidates.add("According to the knowledge base, $bestSentence.")
                        candidates.add("$bestSentence.")
                        candidates.add("The answer is $bestSentence.")
                    }
                }
            }
            ResponseLengthPreset.NORMAL, ResponseLengthPreset.LONG -> {
                val fullSummary = if (sentences.size > 1) {
                    sentences.take(2).joinToString(". ") + "."
                } else {
                    bestSentence
                }
                candidates.add("Based on the saved knowledge base: $fullSummary")
                candidates.add("According to verified records: $bestSentence. $content")
                candidates.add("The recorded answer indicates that $fullSummary")
            }
        }

        return candidates.distinct().take(count).ifEmpty { listOf(content) }
    }
}
