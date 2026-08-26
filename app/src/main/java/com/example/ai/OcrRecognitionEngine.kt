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
    val lineCount: Int,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val detectedBlocks: List<String> = emptyList()
)

object OcrRecognitionEngine {

    // Lazy initialization of the on-device ML Kit text recognizer client (Latin script)
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
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
     * Analyzes OCR-extracted text blocks to find actionable questions or calculations
     */
    fun analyzeOcrOutput(ocrResult: OcrRecognitionResult, detectQuestionsOnly: Boolean): DetectionAnalysisResult {
        if (!ocrResult.isSuccess || ocrResult.rawText.isBlank()) {
            return DetectionAnalysisResult(
                isQuestion = false,
                category = "EMPTY_OCR",
                reason = "On-Device ML Kit OCR completed (${ocrResult.latencyMs}ms) but found no readable text in screenshot bitmap.",
                extractedQuestionText = ""
            )
        }

        // Analyze full combined text first
        val fullAnalysis = QuestionDetectionEngine.analyze(ocrResult.rawText, detectQuestionsOnly)
        if (fullAnalysis.isQuestion) {
            return fullAnalysis
        }

        // If not found in full block, check individual detected text blocks
        for (block in ocrResult.detectedBlocks) {
            val blockAnalysis = QuestionDetectionEngine.analyze(block, detectQuestionsOnly)
            if (blockAnalysis.isQuestion) {
                return blockAnalysis
            }
        }

        return fullAnalysis
    }
}
