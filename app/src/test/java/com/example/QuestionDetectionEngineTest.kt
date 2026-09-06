package com.example

import com.example.ai.QuestionDetectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDetectionEngineTest {

    @Test
    fun testScenario1_ShortSimpleQuestions() {
        val q1 = QuestionDetectionEngine.analyze("Are you free for lunch tomorrow?", detectQuestionsOnly = true)
        assertTrue("Expected q1 to be detected as a question", q1.isQuestion)

        val q2 = QuestionDetectionEngine.analyze("If you could have dinner with any historical figure, who would it be and why?", detectQuestionsOnly = true)
        assertTrue("Expected dinner with historical figure question to be detected", q2.isQuestion)

        val q3 = QuestionDetectionEngine.analyze("Can you send me the updated proposal?", detectQuestionsOnly = true)
        assertTrue("Expected q3 to be detected via modal verb starter with '?'", q3.isQuestion)
    }

    @Test
    fun testScenario_StatementWithoutQuestionMarkIsRejected() {
        // User reported: "Okay, final boss question:" was previously grabbed despite not having '?'
        val statement = QuestionDetectionEngine.analyze("Okay, final boss question:", detectQuestionsOnly = true)
        assertFalse("Expected 'Okay, final boss question:' to be rejected because it lacks '?'", statement.isQuestion)
        assertEquals("NO_QUESTION_MARK", statement.category)

        val statement2 = QuestionDetectionEngine.analyze("Let me ask you something", detectQuestionsOnly = true)
        assertFalse("Expected statement without '?' to be rejected", statement2.isQuestion)
        assertEquals("NO_QUESTION_MARK", statement2.category)

        val statement3 = QuestionDetectionEngine.analyze("Final boss question for today", detectQuestionsOnly = true)
        assertFalse("Expected statement without '?' to be rejected", statement3.isQuestion)
        assertEquals("NO_QUESTION_MARK", statement3.category)
    }

    @Test
    fun testScenario2_LongMultilineQuestions() {
        val multiline1 = """
            Hi Alex,
            Could we reschedule our meeting to tomorrow afternoon at 3 PM?
            Thanks!
        """.trimIndent()
        val res1 = QuestionDetectionEngine.analyze(multiline1, detectQuestionsOnly = true)
        assertTrue("Expected multiline question with '?' to be detected", res1.isQuestion)

        val multiline2 = """
            Hey team, quick update on the roadmap.
            We finished sprint 4 yesterday.
            When should we sync up for sprint planning?
        """.trimIndent()
        val res2 = QuestionDetectionEngine.analyze(multiline2, detectQuestionsOnly = true)
        assertTrue("Expected multiline question with interrogative line to be detected", res2.isQuestion)
    }

    @Test
    fun testScenario3_MathNotationQuestions() {
        val math1 = QuestionDetectionEngine.analyze("Can you calculate 15 * 8 + 32?", detectQuestionsOnly = true)
        assertTrue("Expected math question with arithmetic to be detected", math1.isQuestion)
        assertEquals("MATH_PROMPT", math1.category)

        val math2 = QuestionDetectionEngine.analyze("Solve for x: 2x + 6 = 18?", detectQuestionsOnly = true)
        assertTrue("Expected algebraic equation to be detected", math2.isQuestion)
        assertEquals("MATH_PROMPT", math2.category)

        val math3 = QuestionDetectionEngine.analyze("What is 5^3?", detectQuestionsOnly = true)
        assertTrue("Expected exponent math prompt to be detected", math3.isQuestion)

        val math4 = QuestionDetectionEngine.analyze("25 * 4 = ?", detectQuestionsOnly = true)
        assertTrue("Expected math expression with ? to be detected", math4.isQuestion)
    }

    @Test
    fun testScenario4_NormalMessagingTextNonQuestions() {
        val msg1 = QuestionDetectionEngine.analyze("I'm heading out now, see you soon.", detectQuestionsOnly = true)
        assertFalse("Expected normal statement to be rejected when filtering is on", msg1.isQuestion)
        assertEquals("NO_QUESTION_MARK", msg1.category)

        val msg2 = QuestionDetectionEngine.analyze("Thanks for sending the files over.", detectQuestionsOnly = true)
        assertFalse("Expected greeting/acknowledgement statement to be rejected", msg2.isQuestion)
        assertEquals("NO_QUESTION_MARK", msg2.category)

        val msg3 = QuestionDetectionEngine.analyze("ok", detectQuestionsOnly = true)
        assertFalse("Expected short snippet to be rejected", msg3.isQuestion)
        assertEquals("TOO_SHORT", msg3.category)
    }

    @Test
    fun testOcrOutputAnalysis() {
        val ocrResult = com.example.ai.OcrRecognitionResult(
            rawText = "What is the capital of Australia?",
            lineCount = 1,
            latencyMs = 45,
            isSuccess = true,
            detectedBlocks = listOf("What is the capital of Australia?")
        )
        val analysis = com.example.ai.OcrRecognitionEngine.analyzeOcrOutput(ocrResult, detectQuestionsOnly = true)
        assertTrue("Expected OCR result to be recognized as question", analysis.isQuestion)

        val emptyOcr = com.example.ai.OcrRecognitionResult(
            rawText = "",
            lineCount = 0,
            latencyMs = 30,
            isSuccess = false,
            errorMessage = "No text found"
        )
        val emptyAnalysis = com.example.ai.OcrRecognitionEngine.analyzeOcrOutput(emptyOcr, detectQuestionsOnly = true)
        assertFalse("Expected empty OCR to be marked not a question", emptyAnalysis.isQuestion)
        assertEquals("EMPTY_OCR", emptyAnalysis.category)
    }

    @Test
    fun testQuestionFilteringToggle() {
        // When detectQuestionsOnly is false, all non-blank text >= 3 chars is accepted
        val statement = QuestionDetectionEngine.analyze("I'm heading out now, see you soon.", detectQuestionsOnly = false)
        assertTrue("Expected text to be accepted when detectQuestionsOnly is false", statement.isQuestion)
        assertEquals("GENERAL_MESSAGING", statement.category)
    }

    @Test
    fun testSharedLinkContainingQuestionMark_IsStrictlyRejected() {
        // Shared YouTube links containing '?' query parameter must never be treated as questions
        val youtubeLink = "https://youtu.be/EvwgFim-zXs?si=dQw4w9WgXcQ"
        val ytResult = QuestionDetectionEngine.analyze(youtubeLink, detectQuestionsOnly = true)
        assertFalse("Expected YouTube link with '?' query param to be rejected", ytResult.isQuestion)
        assertEquals("URL_OR_PATH", ytResult.category)

        // Shared link with accompanying text / question word
        val linkWithMessage = "What do you think of this video? https://youtu.be/EvwgFim-zXs?si=EvwgFim-zXs"
        val msgResult = QuestionDetectionEngine.analyze(linkWithMessage, detectQuestionsOnly = true)
        assertFalse("Expected message with embedded URL to be rejected", msgResult.isQuestion)
        assertEquals("URL_OR_PATH", msgResult.category)

        // Standard web search URL with query parameters
        val searchUrl = "www.google.com/search?q=jetpack+compose&oq=compose"
        val searchResult = QuestionDetectionEngine.analyze(searchUrl, detectQuestionsOnly = true)
        assertFalse("Expected search URL to be rejected", searchResult.isQuestion)
        assertEquals("URL_OR_PATH", searchResult.category)

        // Generic domain.tld path with query parameter
        val domainUrl = "github.com/android/compose-samples?tab=readme-ov-file"
        val domainResult = QuestionDetectionEngine.analyze(domainUrl, detectQuestionsOnly = true)
        assertFalse("Expected domain path with query to be rejected", domainResult.isQuestion)
        assertEquals("URL_OR_PATH", domainResult.category)
    }

    @Test
    fun testTechnicalAndCodeStatementsWithQuestionMark_AreRejected() {
        // Ternary operator in code snippet
        val ternarySnippet = "val status = isReady ? 1 : 0"
        val ternaryResult = QuestionDetectionEngine.analyze(ternarySnippet, detectQuestionsOnly = true)
        assertFalse("Expected ternary operator to be rejected", ternaryResult.isQuestion)
        assertEquals("REJECTED_TECHNICAL_TEXT", ternaryResult.category)

        // Kotlin safe-call operator
        val safeCallSnippet = "user?.account?.balance ?: 0"
        val safeCallResult = QuestionDetectionEngine.analyze(safeCallSnippet, detectQuestionsOnly = true)
        assertFalse("Expected safe-call expression to be rejected", safeCallResult.isQuestion)
        assertEquals("REJECTED_TECHNICAL_TEXT", safeCallResult.category)

        // SQL query with placeholder
        val sqlSnippet = "SELECT * FROM users WHERE status = ?"
        val sqlResult = QuestionDetectionEngine.analyze(sqlSnippet, detectQuestionsOnly = true)
        assertFalse("Expected SQL placeholder query to be rejected", sqlResult.isQuestion)
        assertEquals("REJECTED_TECHNICAL_TEXT", sqlResult.category)

        // System file path with wildcard/question mark
        val filePath = "C:\\Users\\Admin\\AppData\\doc?.pdf"
        val fileResult = QuestionDetectionEngine.analyze(filePath, detectQuestionsOnly = true)
        assertFalse("Expected file path with '?' to be rejected", fileResult.isQuestion)
        assertEquals("REJECTED_TECHNICAL_TEXT", fileResult.category)

        // CLI command help flag
        val cliFlag = "Run script.py /?"
        val cliResult = QuestionDetectionEngine.analyze(cliFlag, detectQuestionsOnly = true)
        assertFalse("Expected CLI flag /? to be rejected", cliResult.isQuestion)
        assertEquals("REJECTED_TECHNICAL_TEXT", cliResult.category)
    }

    @Test
    fun testContextualJudgment_OnlyGenuineQuestionsTrigger() {
        // Genuine conversational inquiry
        val genuineQ1 = "Where were you when the body was found in Electrical?"
        val res1 = QuestionDetectionEngine.analyze(genuineQ1, detectQuestionsOnly = true)
        assertTrue("Expected genuine question 1 to trigger detection", res1.isQuestion)
        assertTrue("Expected category to be QUESTION_STARTER or QUESTION_MARK", res1.category in listOf("QUESTION_STARTER", "QUESTION_MARK"))

        // Genuine meeting scheduling question
        val genuineQ2 = "What time are we meeting today?"
        val res2 = QuestionDetectionEngine.analyze(genuineQ2, detectQuestionsOnly = true)
        assertTrue("Expected genuine question 2 to trigger detection", res2.isQuestion)
        assertTrue("Expected category to be CONVERSATIONAL_PHRASE, QUESTION_STARTER, or QUESTION_MARK", res2.category in listOf("CONVERSATIONAL_PHRASE", "QUESTION_STARTER", "QUESTION_MARK"))

        // Genuine party invitation question
        val genuineQ3 = "Are you coming to the party tonight?"
        val res3 = QuestionDetectionEngine.analyze(genuineQ3, detectQuestionsOnly = true)
        assertTrue("Expected genuine question 3 to trigger detection", res3.isQuestion)
        assertTrue("Expected category to be QUESTION_STARTER or QUESTION_MARK", res3.category in listOf("QUESTION_STARTER", "QUESTION_MARK"))

        // Short conversational inquiry
        val genuineQ4 = "Everything okay?"
        val res4 = QuestionDetectionEngine.analyze(genuineQ4, detectQuestionsOnly = true)
        assertTrue("Expected short inquiry to trigger detection", res4.isQuestion)
        assertEquals("QUESTION_MARK", res4.category)

        // Question without valid grammatical/conversational question context is rejected
        val randomTextWithQMark = "lorem ipsum dolor sit amet?"
        val resRandom = QuestionDetectionEngine.analyze(randomTextWithQMark, detectQuestionsOnly = true)
        assertFalse("Expected non-conversational text with '?' to be rejected", resRandom.isQuestion)
        assertEquals("REJECTED_NON_QUESTION_CONTEXT", resRandom.category)
    }

    @Test
    fun testSmartDetectionAiVerified_ClassifierRejectsUrlsAndCode() {
        val ytLink = "https://youtu.be/EvwgFim-zXs?si=abc12345"
        val (isQ1, reason1) = com.example.ai.AiFallbackEngine.classifyWithBuiltInEngine(ytLink)
        assertFalse("Expected URL with '?' to be rejected by AI pre-check classifier", isQ1)
        assertTrue("Expected URL reason", reason1.contains("URL", ignoreCase = true))

        val codeSnippet = "val result = isValid ? 1 : 0"
        val (isQ2, reason2) = com.example.ai.AiFallbackEngine.classifyWithBuiltInEngine(codeSnippet)
        assertFalse("Expected code ternary snippet to be rejected by AI pre-check classifier", isQ2)
        assertTrue("Expected technical syntax reason", reason2.contains("syntax", ignoreCase = true) || reason2.contains("code", ignoreCase = true))
    }

    @Test
    fun testSmartDetectionAiVerified_ClassifierAcceptsGenuineQuestions() {
        val genuine = "Could you give me an update on our project deadline?"
        val (isQ, reason) = com.example.ai.AiFallbackEngine.classifyWithBuiltInEngine(genuine)
        assertTrue("Expected genuine question to be accepted by AI pre-check classifier", isQ)
        assertTrue("Expected genuine inquiry verification", reason.contains("genuine", ignoreCase = true) || reason.contains("Verified", ignoreCase = true))
    }

    @Test
    fun testSmartDetectionAiVerified_JsonParsing() {
        val rawTrueJson = """{"isQuestion": true, "reason": "Conversational inquiry about schedule"}"""
        val (parsedTrue, reasonTrue) = com.example.ai.AiFallbackEngine.parseClassificationJson(rawTrueJson)
        assertTrue("Expected parsedTrue to be true", parsedTrue)
        assertEquals("Conversational inquiry about schedule", reasonTrue)

        val rawFalseJson = """{"isQuestion": false, "reason": "Contains URL query param"}"""
        val (parsedFalse, reasonFalse) = com.example.ai.AiFallbackEngine.parseClassificationJson(rawFalseJson)
        assertFalse("Expected parsedFalse to be false", parsedFalse)
        assertEquals("Contains URL query param", reasonFalse)

        val markdownJson = "```json\n{\"isQuestion\": false, \"reason\": \"Random UI text\"}\n```"
        val (parsedMd, reasonMd) = com.example.ai.AiFallbackEngine.parseClassificationJson(markdownJson)
        assertFalse("Expected markdown wrapped JSON to parse false", parsedMd)
        assertEquals("Random UI text", reasonMd)
    }
}
