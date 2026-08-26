package com.example.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.ai.AiFallbackEngine
import com.example.ai.OcrRecognitionEngine
import com.example.ai.QuestionDetectionEngine
import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.model.DiagnosticLogEntry
import com.example.model.DetectedQuestion
import com.example.model.OverlayBarStyle
import com.example.model.OverlayInteractionMode
import com.example.model.ReplyItem
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.model.SavedOverlayPosition
import com.example.model.UnderstandingSummaryLength
import com.example.model.WhitelistedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object AppStateManager {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _settings = MutableStateFlow(ReplySettings())
    val settings: StateFlow<ReplySettings> = _settings.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _isAccessibilityRunning = MutableStateFlow(false)
    val isAccessibilityRunning: StateFlow<Boolean> = _isAccessibilityRunning.asStateFlow()

    private val _currentQuestion = MutableStateFlow<DetectedQuestion?>(null)
    val currentQuestion: StateFlow<DetectedQuestion?> = _currentQuestion.asStateFlow()

    private val _activeReplies = MutableStateFlow<List<ReplyItem>>(emptyList())
    val activeReplies: StateFlow<List<ReplyItem>> = _activeReplies.asStateFlow()

    private val _activeProvider = MutableStateFlow<AiProvider?>(null)
    val activeProvider: StateFlow<AiProvider?> = _activeProvider.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _questionsHistory = MutableStateFlow<List<DetectedQuestion>>(emptyList())
    val questionsHistory: StateFlow<List<DetectedQuestion>> = _questionsHistory.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    init {
        _activeProvider.value = _settings.value.preferredProvider
    }

    fun addDiagnosticLog(
        source: String,
        rawText: String,
        result: DetectionResultType,
        category: String,
        reason: String,
        detectionMethod: DetectionMethod = DetectionMethod.ACCESSIBILITY,
        latencyMs: Long? = null
    ) {
        val entry = DiagnosticLogEntry(
            source = source,
            rawText = rawText,
            result = result,
            category = category,
            reason = reason,
            detectionMethod = detectionMethod,
            latencyMs = latencyMs
        )
        _diagnosticLogs.value = listOf(entry) + _diagnosticLogs.value.take(49)
    }

    fun clearDiagnosticLogs() {
        _diagnosticLogs.value = emptyList()
    }

    fun updateOcrSettings(enableOcr: Boolean, debounceMs: Int) {
        _settings.value = _settings.value.copy(
            enableOcrFallback = enableOcr,
            ocrDebounceMs = debounceMs
        )
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun setAccessibilityRunning(running: Boolean) {
        _isAccessibilityRunning.value = running
    }

    fun updatePreferredProvider(provider: AiProvider) {
        _settings.value = _settings.value.copy(preferredProvider = provider)
        _activeProvider.value = provider
    }

    fun updateProviderApiKey(provider: AiProvider, newKey: String) {
        if (provider.isCustom) {
            val updated = _settings.value.customProviders.map {
                if (it.id == provider.id) it.copy(apiKey = newKey) else it
            }
            _settings.value = _settings.value.copy(customProviders = updated)
        }
        if (_settings.value.preferredProvider.id == provider.id) {
            val updatedPref = _settings.value.preferredProvider.copy(apiKey = newKey)
            _settings.value = _settings.value.copy(preferredProvider = updatedPref)
            _activeProvider.value = updatedPref
        }
    }

    fun addCustomProvider(name: String, model: String, endpoint: String, apiKey: String) {
        val newProvider = AiProvider(
            id = UUID.randomUUID().toString(),
            type = AiProviderType.CUSTOM_REST,
            name = name.lowercase().replace(" ", "-"),
            displayName = name,
            modelName = model,
            apiKey = apiKey,
            customEndpoint = endpoint.ifBlank { null },
            isCustom = true,
            tier = AiModelTier.BALANCED
        )
        val updated = _settings.value.customProviders + newProvider
        _settings.value = _settings.value.copy(customProviders = updated)
    }

    fun deleteCustomProvider(providerId: String) {
        val updated = _settings.value.customProviders.filter { it.id != providerId }
        _settings.value = _settings.value.copy(customProviders = updated)
        if (_settings.value.preferredProvider.id == providerId) {
            val fallback = AiProvider(
                id = "gemini-builtin",
                type = AiProviderType.GEMINI_BUILTIN,
                name = "gemini-builtin",
                displayName = "Gemini Flash (Built-in)",
                modelName = "gemini-2.5-flash",
                isBuiltIn = true,
                tier = AiModelTier.LIGHTWEIGHT
            )
            _settings.value = _settings.value.copy(preferredProvider = fallback)
            _activeProvider.value = fallback
        }
    }

    fun toggleAppWhitelist(packageName: String) {
        val updated = _settings.value.appsWhitelist.map {
            if (it.packageName == packageName) it.copy(isEnabled = !it.isEnabled) else it
        }
        _settings.value = _settings.value.copy(appsWhitelist = updated)
    }

    fun addCustomApp(appName: String, packageName: String, category: String) {
        val newApp = WhitelistedApp(
            packageName = packageName.trim(),
            appName = appName.trim(),
            category = category.trim().ifBlank { "Custom" },
            isEnabled = true,
            isCustom = true
        )
        val updated = _settings.value.appsWhitelist + newApp
        _settings.value = _settings.value.copy(appsWhitelist = updated)
    }

    fun deleteCustomApp(packageName: String) {
        val updated = _settings.value.appsWhitelist.filter { it.packageName != packageName }
        _settings.value = _settings.value.copy(appsWhitelist = updated)
    }

    fun updateTone(tone: ReplyTone) {
        _settings.value = _settings.value.copy(tone = tone)
    }

    fun updateReplyCount(count: Int) {
        _settings.value = _settings.value.copy(count = count.coerceIn(1, 3))
    }

    fun setUnderstandingMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(understandingMode = enabled)
    }

    fun setUnderstandingSummaryLength(length: UnderstandingSummaryLength) {
        _settings.value = _settings.value.copy(understandingSummaryLength = length)
    }

    fun setAutoGenerateReplies(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoGenerate = enabled)
    }

    fun setDetectQuestionsOnly(enabled: Boolean) {
        _settings.value = _settings.value.copy(detectQuestionsOnly = enabled)
    }

    fun setPrefetchOnAppFocus(enabled: Boolean) {
        _settings.value = _settings.value.copy(prefetchOnAppFocus = enabled)
    }

    fun setAutoCopySingleReply(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoCopySingleReply = enabled)
    }

    fun setExpandableReplies(enabled: Boolean) {
        _settings.value = _settings.value.copy(expandableReplies = enabled)
    }

    fun setResponseLengthPreset(preset: ResponseLengthPreset) {
        _settings.value = _settings.value.copy(responseLengthPreset = preset)
    }

    fun setCustomCharLimit(limit: Int) {
        _settings.value = _settings.value.copy(customCharLimit = limit)
    }

    fun setCacheRetentionMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(cacheRetentionMinutes = minutes)
    }

    fun setHistoryRetentionDays(days: Int) {
        _settings.value = _settings.value.copy(historyRetentionDays = days)
    }

    fun setContinuousScreenAnalysis(enabled: Boolean) {
        _settings.value = _settings.value.copy(continuousScreenAnalysis = enabled)
    }

    fun setRealTimeNodeTracking(enabled: Boolean) {
        _settings.value = _settings.value.copy(realTimeNodeTracking = enabled)
    }

    fun setSmartDebounceMs(ms: Int) {
        _settings.value = _settings.value.copy(smartDebounceMs = ms)
    }

    fun setOcrFallbackEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enableOcrFallback = enabled)
    }

    fun setOcrDebounceMs(ms: Int) {
        _settings.value = _settings.value.copy(ocrDebounceMs = ms)
    }

    fun setOverlayBarStyle(style: OverlayBarStyle) {
        _settings.value = _settings.value.copy(overlayBarStyle = style)
    }

    fun setOverlayInteractionMode(mode: OverlayInteractionMode) {
        _settings.value = _settings.value.copy(overlayInteractionMode = mode)
    }

    fun setAutoHideEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoHideEnabled = enabled)
    }

    fun setAutoHideDelaySec(sec: Int) {
        _settings.value = _settings.value.copy(autoHideDelaySec = sec)
    }

    fun setScreenIdleTimeoutSec(sec: Int) {
        _settings.value = _settings.value.copy(screenIdleTimeoutSec = sec)
    }

    fun setOverlayOpacity(opacity: Float) {
        _settings.value = _settings.value.copy(overlayOpacity = opacity)
    }

    fun setOverlayCornerRadius(radius: Int) {
        _settings.value = _settings.value.copy(overlayCornerRadius = radius)
    }

    fun setOverlayTextSizeSp(size: Int) {
        _settings.value = _settings.value.copy(overlayTextSizeSp = size)
    }

    fun saveOverlayPosition(packageName: String, appName: String, x: Int, y: Int) {
        val filtered = _settings.value.savedPositions.filter { it.packageName != packageName }
        val newPos = SavedOverlayPosition(packageName = packageName, appName = appName, x = x, y = y)
        _settings.value = _settings.value.copy(savedPositions = filtered + newPos)
    }

    fun deleteSavedPosition(id: String) {
        val updated = _settings.value.savedPositions.filter { it.id != id }
        _settings.value = _settings.value.copy(savedPositions = updated)
    }

    fun clearAllSavedPositions() {
        _settings.value = _settings.value.copy(savedPositions = emptyList())
    }

    fun clearAllStorage() {
        _currentQuestion.value = null
        _activeReplies.value = emptyList()
        _questionsHistory.value = emptyList()
        _errorMessage.value = null
    }

    fun dismissReply(replyId: String) {
        _activeReplies.value = _activeReplies.value.filter { it.id != replyId }
    }

    fun clearReplies() {
        _currentQuestion.value = null
        _activeReplies.value = emptyList()
        _errorMessage.value = null
    }

    fun copyAndDismissReply(context: Context, reply: ReplyItem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("ReplyFloat", reply.text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard: \"${reply.text}\"", Toast.LENGTH_SHORT).show()
        dismissReply(reply.id)
    }

    fun onQuestionDetected(
        context: Context,
        text: String,
        sourceApp: String?,
        packageName: String? = null,
        forcedBypass: Boolean = false,
        detectionMethod: DetectionMethod = DetectionMethod.ACCESSIBILITY,
        ocrLatencyMs: Long? = null
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val sourceLabel = sourceApp ?: packageName ?: if (detectionMethod == DetectionMethod.MLKIT_OCR) "OCR Screen Engine" else "Accessibility Scanner"
        val analysis = QuestionDetectionEngine.analyze(cleanText, _settings.value.detectQuestionsOnly)

        if (!analysis.isQuestion && !forcedBypass) {
            addDiagnosticLog(
                source = sourceLabel,
                rawText = cleanText,
                result = DetectionResultType.REJECTED,
                category = analysis.category,
                reason = analysis.reason,
                detectionMethod = detectionMethod,
                latencyMs = ocrLatencyMs
            )
            return
        }

        val reasonSuffix = if (detectionMethod == DetectionMethod.MLKIT_OCR) {
            " [OCR Fallback: ${ocrLatencyMs ?: 0}ms - On-device ML Kit text recognition]"
        } else {
            " [Primary: Fast Accessibility Node Scan]"
        }

        addDiagnosticLog(
            source = sourceLabel,
            rawText = cleanText,
            result = DetectionResultType.MATCHED,
            category = analysis.category,
            reason = "${analysis.reason}$reasonSuffix",
            detectionMethod = detectionMethod,
            latencyMs = ocrLatencyMs
        )

        val provider = _activeProvider.value ?: _settings.value.preferredProvider

        scope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            val question = DetectedQuestion(
                text = cleanText,
                sourceApp = sourceApp,
                packageName = packageName,
                generatedByProvider = provider,
                detectionMethod = detectionMethod,
                ocrLatencyMs = ocrLatencyMs
            )

            _currentQuestion.value = question
            _questionsHistory.value = listOf(question) + _questionsHistory.value.take(49)

            try {
                val (replies, meaning) = AiFallbackEngine.generateReplies(
                    cleanText,
                    _settings.value,
                    provider
                )

                _currentQuestion.value = question.copy(englishMeaning = meaning)
                _activeReplies.value = replies

                if (_settings.value.autoCopySingleReply && replies.size == 1) {
                    copyAndDismissReply(context, replies.first())
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to generate AI replies"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun simulateQuestionDetected(context: Context, text: String, sourceApp: String? = "Simulator") {
        onQuestionDetected(
            context = context,
            text = text,
            sourceApp = sourceApp,
            detectionMethod = DetectionMethod.ACCESSIBILITY
        )
    }

    /**
     * Executes real On-Device ML Kit Text Recognition on a simulated custom canvas (e.g. unreadable WebView, game canvas)
     * entirely in a background coroutine thread (Dispatchers.Default), ensuring zero UI blocking.
     */
    fun simulateOcrQuestionDetected(
        context: Context,
        renderedText: String,
        sourceApp: String = "Custom Canvas App (OCR Fallback)"
    ) {
        scope.launch(Dispatchers.Default) {
            val ocrResult = OcrRecognitionEngine.simulateCustomCanvasOcr(renderedText)
            val analysis = OcrRecognitionEngine.analyzeOcrOutput(ocrResult, _settings.value.detectQuestionsOnly)

            if (!ocrResult.isSuccess || ocrResult.rawText.isBlank()) {
                addDiagnosticLog(
                    source = sourceApp,
                    rawText = renderedText,
                    result = DetectionResultType.REJECTED,
                    category = "OCR_EMPTY",
                    reason = "On-Device ML Kit OCR completed in ${ocrResult.latencyMs}ms but detected no readable text on screen. UI was never blocked.",
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    latencyMs = ocrResult.latencyMs
                )
                return@launch
            }

            if (!analysis.isQuestion) {
                addDiagnosticLog(
                    source = sourceApp,
                    rawText = ocrResult.rawText,
                    result = DetectionResultType.REJECTED,
                    category = analysis.category,
                    reason = "On-Device ML Kit OCR extracted text in ${ocrResult.latencyMs}ms: ${analysis.reason}",
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    latencyMs = ocrResult.latencyMs
                )
                return@launch
            }

            // On matched question from OCR fallback
            launch(Dispatchers.Main) {
                onQuestionDetected(
                    context = context,
                    text = ocrResult.rawText,
                    sourceApp = sourceApp,
                    forcedBypass = true,
                    detectionMethod = DetectionMethod.MLKIT_OCR,
                    ocrLatencyMs = ocrResult.latencyMs
                )
            }
        }
    }
}
