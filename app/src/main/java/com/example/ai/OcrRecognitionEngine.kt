package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OcrRecognitionResult(
    val rawText: String,
    val lineCount: Int = 0,
    val latencyMs: Long = 0L,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val detectedBlocks: List<String> = emptyList()
)

data class BitmapAnalysisResult(
    val isBlankOrBlack: Boolean,
    val width: Int,
    val height: Int,
    val samplePixelCount: Int,
    val hasVisibleColor: Boolean,
    val details: String
)

object OcrRecognitionEngine {

    // Lazy initialization of the on-device ML Kit text recognizer client (Latin script)
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Analyzes a sample pixel buffer and returns structured inspection telemetry.
     */
    fun analyzePixelArray(pixels: IntArray, width: Int = 0, height: Int = 0): BitmapAnalysisResult {
        if (pixels.isEmpty()) {
            return BitmapAnalysisResult(
                isBlankOrBlack = true,
                width = width,
                height = height,
                samplePixelCount = 0,
                hasVisibleColor = false,
                details = "Sample buffer is empty (0 pixels)"
            )
        }

        val firstPixel = pixels[0]
        var allIdentical = true
        var hasVisibleColor = false
        var coloredPixelCount = 0

        for (pixel in pixels) {
            if (pixel != firstPixel) {
                allIdentical = false
            }
            val alpha = (pixel ushr 24) and 0xFF
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF

            // A pixel has visible non-black content if it is sufficiently opaque and has visible luminance/color
            if (alpha > 15 && (red > 15 || green > 15 || blue > 15)) {
                hasVisibleColor = true
                coloredPixelCount++
            }
        }

        val isBlank = allIdentical || !hasVisibleColor
        val details = if (isBlank) {
            if (allIdentical) {
                val hex = String.format("#%08X", firstPixel)
                "${width}x${height} px: 100% solid uniform color ($hex)."
            } else {
                "${width}x${height} px: Solid dark buffer ($coloredPixelCount/${pixels.size} colored sample pixels)."
            }
        } else {
            "${width}x${height} px: Active screen graphics ($coloredPixelCount/${pixels.size} colored sample pixels)."
        }

        return BitmapAnalysisResult(
            isBlankOrBlack = isBlank,
            width = width,
            height = height,
            samplePixelCount = pixels.size,
            hasVisibleColor = hasVisibleColor,
            details = details
        )
    }

    /**
     * Evaluates a sampled array of ARGB_8888 pixel values to detect whether screen capture returned
     * pure black or uniform unrendered content.
     */
    fun isPixelArrayBlankOrBlack(pixels: IntArray): Boolean {
        return analyzePixelArray(pixels).isBlankOrBlack
    }

    /**
     * Performs pixel telemetry analysis on the captured screenshot bitmap,
     * diagnosing whether the buffer contains visible color or unrendered blank pixels.
     * Safely handles both Software and Hardware bitmaps.
     */
    fun analyzeBitmapContent(bitmap: Bitmap): BitmapAnalysisResult {
        val width = try { bitmap.width } catch (_: Exception) { 0 }
        val height = try { bitmap.height } catch (_: Exception) { 0 }
        if (width <= 0 || height <= 0) {
            return BitmapAnalysisResult(
                isBlankOrBlack = false,
                width = 0,
                height = 0,
                samplePixelCount = 0,
                hasVisibleColor = true,
                details = "Buffer reported dimensions (${width}x${height})"
            )
        }

        return try {
            val sampleSize = 64
            // If bitmap is Hardware-backed, copy to software ARGB_8888 or draw to canvas to sample pixels safely
            val softwareBitmap: Bitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                val temp = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(temp)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, sampleSize, sampleSize), paint)
                temp
            } else {
                Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
            }

            val pixels = IntArray(sampleSize * sampleSize)
            softwareBitmap.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
            if (softwareBitmap != bitmap) {
                softwareBitmap.recycle()
            }

            analyzePixelArray(pixels, width, height)
        } catch (e: Exception) {
            // Default to hasVisibleColor = true so we NEVER falsely reject valid screen captures
            BitmapAnalysisResult(
                isBlankOrBlack = false,
                width = width,
                height = height,
                samplePixelCount = 0,
                hasVisibleColor = true,
                details = "Screen capture frame active (${width}x${height} px)"
            )
        }
    }

    /**
     * Checks whether a screenshot bitmap contains blank or completely unrendered content.
     */
    fun isBitmapBlankOrBlack(bitmap: Bitmap): Boolean {
        return analyzeBitmapContent(bitmap).isBlankOrBlack
    }

    /**
     * Executes On-Device ML Kit Text Recognition entirely on a background coroutine thread (Dispatchers.Default).
     * This guarantees that screen rendering and UI interaction are NEVER blocked.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): OcrRecognitionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val visionText = suspendCancellableCoroutine<Text> { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { text ->
                        if (continuation.isActive) {
                            continuation.resume(text)
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(exception))
                        }
                    }
            }

            val latency = System.currentTimeMillis() - startTime
            val blocks = visionText.textBlocks.map { it.text.trim() }.filter { it.isNotBlank() }

            OcrRecognitionResult(
                rawText = visionText.text.trim(),
                lineCount = visionText.textBlocks.sumOf { it.lines.size },
                latencyMs = latency,
                isSuccess = true,
                detectedBlocks = blocks
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            OcrRecognitionResult(
                rawText = "",
                lineCount = 0,
                latencyMs = latency,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.message ?: "OCR Recognition Exception",
                detectedBlocks = emptyList()
            )
        }
    }

    /**
     * Simulates rendering custom graphic canvas pixels (e.g. Flutter custom canvas, Unity, or unreadable WebView)
     * and performs real on-device ML Kit Text Recognition inference on the rendered bitmap in a background coroutine.
     */
    suspend fun simulateCustomCanvasOcr(textToRender: String): OcrRecognitionResult = withContext(Dispatchers.Default) {
        val width = 900
        val height = 450
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw dark themed background bubble
        val bgPaint = Paint().apply {
            color = Color.rgb(24, 28, 38)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw simulated chat bubble background
        val bubblePaint = Paint().apply {
            color = Color.rgb(38, 45, 62)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, 40f, width - 40f, height - 40f, 24f, 24f, bubblePaint)

        // Draw text on canvas with high-contrast text paint
        val textPaint = Paint().apply {
            color = Color.rgb(245, 247, 250)
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val lines = textToRender.split("\n")
        var yOffset = 110f
        for (line in lines) {
            canvas.drawText(line, 80f, yOffset, textPaint)
            yOffset += 48f
        }

        // Run real ML Kit Text Recognition inference on the generated bitmap
        recognizeTextFromBitmap(bitmap)
    }

    /**
     * Analyzes OCR-extracted text blocks and lines to find actionable questions or calculations,
     * prioritizing the latest question block/line when gaming HUD or multiple overlay text elements are present.
     * Enforces the exact same trigger filtering as accessibility service so plain words/labels never trigger.
     */
    fun analyzeOcrOutput(
        ocrResult: OcrRecognitionResult,
        detectQuestionsOnly: Boolean,
        triggers: List<com.example.model.TriggerItem> = com.example.state.AppStateManager.settings.value.triggers
    ): DetectionAnalysisResult {
        if (!ocrResult.isSuccess || ocrResult.rawText.isBlank()) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "EMPTY_OCR",
                reason = "On-Device ML Kit OCR completed (${ocrResult.latencyMs}ms) but found no readable text in screenshot bitmap.",
                extractedQuestionText = ""
            )
        }

        val allCandidates = mutableListOf<String>()

        // 1. First, collect individual detected blocks and lines
        for (block in ocrResult.detectedBlocks) {
            val lines = block.split("\n").map { it.trim() }.filter { it.length >= 3 }
            allCandidates.addAll(lines)
            if (lines.size > 1) {
                allCandidates.add(block.trim())
            }
        }

        val rawLines = ocrResult.rawText.split("\n").map { it.trim() }.filter { it.length >= 3 }
        allCandidates.addAll(rawLines)

        // Deduplicate preserving chronological/visual scanning order
        val distinctCandidates = allCandidates.distinct()

        // 1. Prioritize candidates matching an enabled question trigger (from bottom to top, most recent first)
        val triggerCandidates = distinctCandidates.filter {
            QuestionDetectionEngine.matchesAnyTrigger(it, triggers).first
        }

        for (candidate in triggerCandidates.asReversed()) {
            val rawAnalysis = QuestionDetectionEngine.analyze(candidate, detectQuestionsOnly, triggers)
            if (rawAnalysis.isQuestion) {
                return rawAnalysis.copy(extractedQuestionText = candidate)
            }

            // Also check stripped message (e.g. "Red (Detective): Who killed Blue?" -> "Who killed Blue?")
            val stripped = stripChatSenderPrefix(candidate)
            if (stripped != candidate && stripped.length >= 3) {
                val strippedAnalysis = QuestionDetectionEngine.analyze(stripped, detectQuestionsOnly, triggers)
                if (strippedAnalysis.isQuestion) {
                    return strippedAnalysis.copy(extractedQuestionText = candidate)
                }
            }
        }

        // 2. If detectQuestionsOnly is disabled, allow general messaging candidates
        if (!detectQuestionsOnly) {
            for (candidate in distinctCandidates.asReversed()) {
                val rawAnalysis = QuestionDetectionEngine.analyze(candidate, false, triggers)
                if (rawAnalysis.isQuestion) {
                    return rawAnalysis.copy(extractedQuestionText = candidate)
                }

                val stripped = stripChatSenderPrefix(candidate)
                if (stripped != candidate && stripped.length >= 3) {
                    val strippedAnalysis = QuestionDetectionEngine.analyze(stripped, false, triggers)
                    if (strippedAnalysis.isQuestion) {
                        return strippedAnalysis.copy(extractedQuestionText = candidate)
                    }
                }
            }
        }

        // If no candidate matched the trigger filter, reject
        return DetectionAnalysisResult(
            isQuestion = false,
            category = "NO_QUESTION_TRIGGER",
            reason = "OCR extracted text, but none of the text blocks/lines contained an enabled question trigger word or symbol",
            extractedQuestionText = ""
        )
    }

    private fun stripChatSenderPrefix(text: String): String {
        // Handle "Player: message", "[Player]: message", "(Role) Name: message"
        if (text.contains(":")) {
            val afterColon = text.substringAfter(":").trim()
            if (afterColon.isNotBlank()) return afterColon
        }
        // Handle "[Player] message"
        if (text.startsWith("[") && text.contains("]")) {
            val afterBracket = text.substringAfter("]").trim()
            if (afterBracket.isNotBlank()) return afterBracket
        }
        // Handle "(Player) message"
        if (text.startsWith("(") && text.contains(")")) {
            val afterParen = text.substringAfter(")").trim()
            if (afterParen.isNotBlank()) return afterParen
        }
        return text
    }
}
