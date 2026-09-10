package com.example

import com.example.ai.AiFallbackEngine
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFallbackEngineTest {

    @Test
    fun testTelephoneInventionQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val result = AiFallbackEngine.generateRepliesWithFallback("Who invented the telephone?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val firstReply = result.replies.first().text
        assertTrue(
            "Expected reply to contain Alexander Graham Bell, but was: $firstReply",
            firstReply.contains("Alexander Graham Bell") || firstReply.contains("Bell")
        )
    }

    @Test
    fun testImaginaryUnitMathAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CONCISE)
        val result = AiFallbackEngine.generateRepliesWithFallback("What is the value of i²?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val firstReply = result.replies.first().text
        assertTrue(
            "Expected reply to equal -1, but was: $firstReply",
            firstReply.contains("-1")
        )
    }

    @Test
    fun testHistoricalFigureHypotheticalQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val question = "If you could have dinner with any historical figure, who would it be and why?"
        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val replyText = result.replies.first().text
        assertTrue(
            "Expected reply to mention historical figures, but was: $replyText",
            replyText.contains("da Vinci") || replyText.contains("Einstein") || replyText.contains("Tesla") || replyText.contains("Socrates")
        )
    }

    @Test
    fun testCapitalQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CONCISE)
        val result = AiFallbackEngine.generateRepliesWithFallback("What is the capital of Australia?", settings)
        
        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val replyText = result.replies.first().text
        assertTrue(
            "Expected reply to contain Canberra, but was: $replyText",
            replyText.contains("Canberra")
        )
    }

    @Test
    fun testGroqProviderConfigurationAndModel() {
        val groqProvider = com.example.model.defaultBuiltInProviders().first { it.id == "groq" }
        assertEquals(com.example.model.AiProviderType.GROQ, groqProvider.type)
        assertEquals("openai/gpt-oss-120b", groqProvider.modelName)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", groqProvider.customEndpoint)
    }

    @Test
    fun testGroqProviderTestConnectionValidation() = runBlocking {
        val groqProvider = com.example.model.defaultBuiltInProviders().first { it.id == "groq" }
        val settings = ReplySettings()
        
        // Without API key
        val resultWithoutKey = AiFallbackEngine.testProviderConnection(groqProvider, settings)
        assertTrue(resultWithoutKey.isFailure)
        assertTrue(resultWithoutKey.exceptionOrNull()?.message?.contains("Groq API Key is missing") == true)
    }

    @Test
    fun testResponseLengthPresetsScaling() = runBlocking {
        val question = "Who invented the telephone?"
        
        val veryShortSettings = ReplySettings(responseLengthPreset = com.example.model.ResponseLengthPreset.VERY_SHORT)
        val shortSettings = ReplySettings(responseLengthPreset = com.example.model.ResponseLengthPreset.SHORT)
        val normalSettings = ReplySettings(responseLengthPreset = com.example.model.ResponseLengthPreset.NORMAL)
        val longSettings = ReplySettings(responseLengthPreset = com.example.model.ResponseLengthPreset.LONG)

        val veryShortResult = AiFallbackEngine.generateRepliesWithFallback(question, veryShortSettings)
        val shortResult = AiFallbackEngine.generateRepliesWithFallback(question, shortSettings)
        val normalResult = AiFallbackEngine.generateRepliesWithFallback(question, normalSettings)
        val longResult = AiFallbackEngine.generateRepliesWithFallback(question, longSettings)

        val vShortLen = veryShortResult.replies.first().text.length
        val shortLen = shortResult.replies.first().text.length
        val normalLen = normalResult.replies.first().text.length
        val longLen = longResult.replies.first().text.length

        assertTrue("Expected very short length ($vShortLen) <= short length ($shortLen)", vShortLen <= shortLen)
        assertTrue("Expected short length ($shortLen) <= normal length ($normalLen)", shortLen <= normalLen)
        assertTrue("Expected normal length ($normalLen) <= long length ($longLen)", normalLen <= longLen)
        assertTrue("Expected very short to be under 35 chars, was $vShortLen", vShortLen <= 35)
        assertTrue("Expected long to be over 100 chars, was $longLen", longLen >= 100)
    }

    @Test
    fun testGroqReasoningJsonExtraction() {
        // 1. Standard choices[0].message.content
        val standardJson = org.json.JSONObject("""
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "[\"Reply A\", \"Reply B\"]"
                        }
                    }
                ]
            }
        """.trimIndent())
        assertEquals("[\"Reply A\", \"Reply B\"]", AiFallbackEngine.extractContentFromOpenAiJson(standardJson))

        // 2. Reasoning model choices[0].message.reasoning (when content is empty)
        val reasoningJson = org.json.JSONObject("""
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "",
                            "reasoning": "[\"Reasoned 1\", \"Reasoned 2\"]"
                        }
                    }
                ]
            }
        """.trimIndent())
        assertEquals("[\"Reasoned 1\", \"Reasoned 2\"]", AiFallbackEngine.extractContentFromOpenAiJson(reasoningJson))

        // 3. Reasoning model choices[0].message.reasoning_content
        val reasoningContentJson = org.json.JSONObject("""
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "reasoning_content": "[\"Deep reply 1\", \"Deep reply 2\"]"
                        }
                    }
                ]
            }
        """.trimIndent())
        assertEquals("[\"Deep reply 1\", \"Deep reply 2\"]", AiFallbackEngine.extractContentFromOpenAiJson(reasoningContentJson))
    }

    @Test
    fun testTrashTalkToneOption() = runBlocking {
        val trashTalkTone = ReplyTone.TRASH_TALK
        assertEquals("Trash Talk", trashTalkTone.label)
        assertEquals("Savage, witty, competitive banter — playful roasting, not genuinely abusive.", trashTalkTone.description)
        assertTrue(trashTalkTone.systemPromptHint.contains("HARD SAFETY RULE"))
        assertTrue(trashTalkTone.exampleReply.isNotBlank())

        val settings = ReplySettings(tone = ReplyTone.TRASH_TALK)
        val result = AiFallbackEngine.generateRepliesWithFallback("What do you think of my idea?", settings)
        assertTrue(result.replies.isNotEmpty())
        val reply = result.replies.first().text
        assertTrue("Expected playful roast or witty comeback in reply, was: $reply", reply.isNotEmpty())
    }

    @Test
    fun testTrashTalkHinglishReplies() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.TRASH_TALK)
        val result = AiFallbackEngine.generateRepliesWithFallback("bhai kaisa laga mera plan?", settings)
        assertTrue(result.replies.isNotEmpty())
        val reply = result.replies.first().text
        assertTrue(
            "Expected Hinglish banter in reply, was: $reply",
            reply.contains("Bhai", ignoreCase = true) || reply.contains("logic", ignoreCase = true) || reply.contains("WhatsApp", ignoreCase = true) || reply.contains("chai", ignoreCase = true)
        )
    }

    @Test
    fun testAnimeHypotheticalQuestionAnswering() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val question = "If you could live inside any anime world for a week, which one would you pick?"
        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)

        assertTrue("Expected replies to not be empty", result.replies.isNotEmpty())
        val reply = result.replies.first().text
        assertTrue(
            "Reply should NOT be a stale canned task completion response like 'Let's get that done', was: $reply",
            !reply.contains("Let's get that done", ignoreCase = true) &&
                    !reply.contains("working on that right now", ignoreCase = true)
        )
        assertTrue(
            "Expected anime world answer (e.g. Pokémon, Spirited Away, anime), was: $reply",
            reply.contains("anime", ignoreCase = true) ||
                    reply.contains("Pokémon", ignoreCase = true) ||
                    reply.contains("Pokemon", ignoreCase = true) ||
                    reply.contains("world", ignoreCase = true) ||
                    reply.contains("Ghibli", ignoreCase = true) ||
                    reply.contains("Titan", ignoreCase = true) ||
                    reply.contains("Demon Slayer", ignoreCase = true)
        )
    }

    @Test
    fun testGeneralQuestionFallbackDoesNotReturnActionCompletion() = runBlocking {
        val settings = ReplySettings(tone = ReplyTone.CASUAL)
        val question = "What is your favorite weekend activity?"
        val result = AiFallbackEngine.generateRepliesWithFallback(question, settings)

        assertTrue(result.replies.isNotEmpty())
        val reply = result.replies.first().text
        assertTrue(
            "General question should not return action completion template, was: $reply",
            !reply.contains("Let's get that done", ignoreCase = true) &&
                    !reply.contains("working on that right now", ignoreCase = true)
        )
    }
}
