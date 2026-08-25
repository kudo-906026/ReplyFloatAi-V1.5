package com.example.api

import com.example.model.AiProvider
import com.example.model.AiReplyResult
import com.example.model.ReplyLength
import com.example.model.ReplyTone
import org.json.JSONObject

object AiPromptHelper {

    fun buildPrompt(
        question: String,
        count: Int,
        length: ReplyLength,
        tone: ReplyTone,
        multiLanguage: Boolean
    ): String {
        val personaDirective = when (tone) {
            ReplyTone.TRASH_TALK -> "PERSONA DIRECTIVE: Write the reply fully in a witty, roasting, banter-heavy trash-talk voice (friendly, sharp teasing between friends), while still directly and accurately answering the detected question."
            ReplyTone.LORD -> "PERSONA DIRECTIVE: Write the reply fully in a theatrical, grandiose sovereign lord voice (addressing the contact as 'servant' or 'mortal' with royal/divine pronouncements), while still accurately answering the detected question."
            else -> null
        }

        return if (multiLanguage) {
            buildString {
                appendLine("You are an intelligent multi-language instant-reply AI assistant for Android.")
                appendLine("The user was asked the following question on screen:")
                appendLine("\"$question\"")
                appendLine()
                appendLine("TASK & INSTRUCTIONS:")
                appendLine("1. Detect the exact language, dialect, and script of the input question (e.g. Hinglish like 'isko karo kya vote?', French, Spanish, Russian Cyrillic, Japanese Kanji/Kana, Hindi, etc.).")
                appendLine("2. Translate what the question means into clear, plain English.")
                appendLine("3. Generate exactly $count distinct, natural reply options in the EXACT SAME language, dialect, and script as the original question.")
                appendLine("4. Length rule: ${length.promptInstruction}")
                appendLine("5. Tone rule: ${tone.promptInstruction}")
                if (personaDirective != null) {
                    appendLine("6. $personaDirective")
                }
                appendLine()
                appendLine("OUTPUT FORMAT:")
                appendLine("Return a valid JSON object ONLY. Do NOT include markdown code blocks, backticks, or extra commentary. Structure:")
                appendLine("{")
                appendLine("  \"original\": \"<exact question in original language/script>\",")
                appendLine("  \"english_meaning\": \"<plain English translation of the question>\",")
                appendLine("  \"replies\": [")
                appendLine("    \"<reply 1 in same language as original>\",")
                appendLine("    \"<reply 2 in same language as original>\"")
                appendLine("  ]")
                appendLine("}")
            }
        } else {
            buildString {
                appendLine("You are an intelligent instant-reply AI assistant for Android.")
                appendLine("The user was asked the following question on screen:")
                appendLine("\"$question\"")
                appendLine()
                appendLine("LANGUAGE INSTRUCTION: Reply in clear, natural English.")
                appendLine("Generate exactly $count distinct, ready-to-send reply options.")
                appendLine("STRICT LENGTH CONSTRAINT: ${length.promptInstruction}")
                appendLine("TONE CONSTRAINT: ${tone.promptInstruction}")
                if (personaDirective != null) {
                    appendLine(personaDirective)
                }
                appendLine()
                appendLine("RULES:")
                appendLine("1. Return ONLY the reply options.")
                appendLine("2. Prefix each individual reply option with '>>> ' at the start of the line.")
                appendLine("3. Do NOT include markdown headers, quotes around the replies, numbered lists (like 1., 2.), or explanations.")
                appendLine("4. Each reply should be ready to paste directly into a chat or message.")
            }
        }
    }

    fun parseMultiLanguageResult(
        rawText: String,
        fallbackQuestion: String,
        targetCount: Int,
        provider: AiProvider
    ): AiReplyResult {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                val jsonStr = cleaned.substring(firstBrace, lastBrace + 1)
                val json = JSONObject(jsonStr)
                val original = json.optString("original", fallbackQuestion).ifBlank { fallbackQuestion }
                val englishMeaning = json.optString("english_meaning", "").ifBlank { null }
                val repliesArr = json.optJSONArray("replies")
                val repliesList = mutableListOf<String>()
                if (repliesArr != null) {
                    for (i in 0 until repliesArr.length()) {
                        val r = cleanReply(repliesArr.optString(i))
                        if (r.isNotBlank()) {
                            repliesList.add(r)
                        }
                    }
                }

                if (repliesList.isNotEmpty()) {
                    return AiReplyResult(
                        original = original,
                        englishMeaning = englishMeaning,
                        replies = repliesList.take(targetCount),
                        provider = provider
                    )
                }
            }
        } catch (e: Exception) {
            // Fall through to line parsing
        }

        // Fallback to line parsing
        val lines = parseReplies(rawText, targetCount)
        return AiReplyResult(
            original = fallbackQuestion,
            englishMeaning = null,
            replies = lines,
            provider = provider
        )
    }

    fun parseReplies(
        rawText: String,
        targetCount: Int
    ): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        var currentBuffer = StringBuilder()

        for (line in lines) {
            if (line.startsWith(">>>")) {
                if (currentBuffer.isNotBlank()) {
                    result.add(cleanReply(currentBuffer.toString()))
                    currentBuffer = StringBuilder()
                }
                val cleaned = line.removePrefix(">>>").trim()
                if (cleaned.isNotBlank()) {
                    currentBuffer.append(cleaned)
                }
            } else if (line.matches(Regex("^(\\d+\\.|[-*•])\\s+.*"))) {
                if (currentBuffer.isNotBlank()) {
                    result.add(cleanReply(currentBuffer.toString()))
                    currentBuffer = StringBuilder()
                }
                val cleaned = line.replace(Regex("^(\\d+\\.|[-*•])\\s+"), "").trim()
                if (cleaned.isNotBlank()) {
                    currentBuffer.append(cleaned)
                }
            } else {
                if (currentBuffer.isNotEmpty()) {
                    currentBuffer.append(" ").append(line)
                } else {
                    currentBuffer.append(line)
                }
            }
        }

        if (currentBuffer.isNotBlank()) {
            result.add(cleanReply(currentBuffer.toString()))
        }

        return result
            .map { cleanReply(it) }
            .filter { it.isNotBlank() && !it.startsWith("Here are") && !it.startsWith("Sure, here") }
            .take(targetCount)
    }

    fun cleanReply(text: String): String {
        return text
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .removePrefix("Option 1:").removePrefix("Option 2:").removePrefix("Option 3:")
            .trim()
    }
}
