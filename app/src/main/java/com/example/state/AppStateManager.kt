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
import com.example.model.defaultBuiltInProviders
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

    private val processedQuestionsCache = mutableMapOf<String, Long>()
    private val answeredQuestions = mutableSetOf<String>()

    init {
        _activeProvider.value = _settings.value.preferredProvider
    }

    fun normalizeQuestionText(text: String): String {
        return text.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    fun isQuestionAlreadyProcessed(text: String): Boolean {
        val norm = normalizeQuestionText(text)
        if (_currentQuestion.value != null && normalizeQuestionText(_currentQuestion.value!!.text) == norm) {
            return true
        }
        if (answeredQuestions.contains(norm)) {
            return true
        }
        val processedAt = processedQuestionsCache[norm]
        if (processedAt != null) {
            val elapsed = System.currentTimeMillis() - processedAt
            val retentionMs = (_settings.value.autoPurgeTimerMinutes.coerceAtLeast(1)) * 60 * 1000L
            if (elapsed < retentionMs) {
                return true
            }
        }
        return false
    }

    fun purgeExpiredData() {
        val minutes = _settings.value.autoPurgeTimerMinutes
        if (minutes <= 0) return
        val cutoff = System.currentTimeMillis() - (minutes * 60 * 1000L)
        _questionsHistory.value = _questionsHistory.value.filter { it.timestamp >= cutoff }
        val expiredKeys = processedQuestionsCache.filter { it.value < cutoff }.keys
        expiredKeys.forEach { processedQuestionsCache.remove(it) }
    }

    fun refreshServiceStatuses(context: Context) {
        _isAccessibilityRunning.value = checkAccessibilityServiceRunning(context)
    }

    fun checkAccessibilityServiceRunning(context: Context): Boolean {
        return try {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
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
        val currentOrder = _settings.value.fallbackOrder.toMutableList()
        if (currentOrder.contains(provider.id)) {
            currentOrder.remove(provider.id)
            currentOrder.add(0, provider.id)
        } else {
            currentOrder.add(0, provider.id)
        }
        _settings.value = _settings.value.copy(
            preferredProvider = provider,
            fallbackOrder = currentOrder
        )
        _activeProvider.value = provider
    }

    fun updateFallbackOrder(newOrder: List<String>) {
        val allMap = (defaultBuiltInProviders() + _settings.value.customProviders).associateBy { it.id }
        val topProvider = newOrder.firstOrNull()?.let { allMap[it] } ?: _settings.value.preferredProvider
        _settings.value = _settings.value.copy(
            fallbackOrder = newOrder,
            preferredProvider = topProvider
        )
        _activeProvider.value = topProvider
    }

    fun moveProviderInFallbackOrder(fromIndex: Int, toIndex: Int) {
        val current = _settings.value.fallbackOrder.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            updateFallbackOrder(current)
        }
    }

    fun setPrimaryFallbackProvider(providerId: String) {
        val current = _settings.value.fallbackOrder.toMutableList()
        current.remove(providerId)
        current.add(0, providerId)
        updateFallbackOrder(current)
    }

    fun updateProviderApiKey(provider: AiProvider, newKey: String) {
        val updatedKeys = _settings.value.providerApiKeys.toMutableMap()
        updatedKeys[provider.id] = newKey

        if (provider.isCustom) {
            val updated = _settings.value.customProviders.map {
                if (it.id == provider.id) it.copy(apiKey = newKey) else it
            }
            _settings.value = _settings.value.copy(
                customProviders = updated,
                providerApiKeys = updatedKeys
            )
        } else {
            _settings.value = _settings.value.copy(providerApiKeys = updatedKeys)
        }

        if (_settings.value.preferredProvider.id == provider.id) {
            val updatedPref = _settings.value.preferredProvider.copy(apiKey = newKey)
            _settings.value = _settings.value.copy(preferredProvider = updatedPref)
            _activeProvider.value = updatedPref
        }
    }

    fun addCustomProvider(name: String, model: String, endpoint: String, apiKey: String) {
        val id = UUID.randomUUID().toString()
        val newProvider = AiProvider(
            id = id,
            type = AiProviderType.CUSTOM_REST,
            name = name.lowercase().replace(" ", "-"),
            displayName = name,
            modelName = model,
            apiKey = apiKey,
            customEndpoint = endpoint.ifBlank { null },
            isCustom = true,
            tier = AiModelTier.BALANCED
        )
        val updatedCustom = _settings.value.customProviders + newProvider
        val updatedKeys = _settings.value.providerApiKeys + (id to apiKey)
        val updatedOrder = _settings.value.fallbackOrder + id
        _settings.value = _settings.value.copy(
            customProviders = updatedCustom,
            providerApiKeys = updatedKeys,
            fallbackOrder = updatedOrder
        )
    }

    fun deleteCustomProvider(providerId: String) {
        val updated = _settings.value.customProviders.filter { it.id != providerId }
        val updatedKeys = _settings.value.providerApiKeys.filterKeys { it != providerId }
        val updatedOrder = _settings.value.fallbackOrder.filter { it != providerId }
        _settings.value = _settings.value.copy(
            customProviders = updated,
            providerApiKeys = updatedKeys,
            fallbackOrder = updatedOrder
        )
        if (_settings.value.preferredProvider.id == providerId) {
            val fallback = defaultBuiltInProviders().first { it.type == AiProviderType.GEMINI_BUILTIN }
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

    fun setReplyAutoDeleteMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(replyAutoDeleteMinutes = minutes.coerceIn(0, 10))
    }

    fun setHistoryPurgeMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(
            historyPurgeMinutes = minutes.coerceIn(1, 10),
            autoPurgeTimerMinutes = minutes.coerceIn(1, 10)
        )
        purgeExpiredData()
    }

    fun setAutoPurgeTimerMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(
            autoPurgeTimerMinutes = minutes,
            historyPurgeMinutes = if (minutes in 1..10) minutes else _settings.value.historyPurgeMinutes
        )
        purgeExpiredData()
    }

    fun deleteHistoryItem(id: String) {
        _questionsHistory.value = _questionsHistory.value.filter { it.id != id }
    }

    fun clearHistory() {
        _questionsHistory.value = emptyList()
        processedQuestionsCache.clear()
        answeredQuestions.clear()
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
        processedQuestionsCache.clear()
        answeredQuestions.clear()
        com.example.service.QuestionDetectorAccessibilityService.resetLastProcessedText()
    }

    fun dismissReply(replyId: String) {
        _activeReplies.value = _activeReplies.value.filter { it.id != replyId }
    }

    fun clearReplies() {
        _currentQuestion.value = null
        _activeReplies.value = emptyList()
        _errorMessage.value = null
        com.example.service.QuestionDetectorAccessibilityService.resetLastProcessedText()
    }

    fun copyAndDismissReply(context: Context, reply: ReplyItem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("ReplyFloat", reply.text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard: \"${reply.text}\"", Toast.LENGTH_SHORT).show()
        
        _currentQuestion.value?.text?.let { qText ->
            answeredQuestions.add(normalizeQuestionText(qText))
        }
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

        // Deduplication check: Do not re-trigger if already seen, answered, or currently active
        if (!forcedBypass && isQuestionAlreadyProcessed(cleanText)) {
            return
        }

        val sourceLabel = sourceApp ?: packageName ?: if (detectionMethod == DetectionMethod.MLKIT_OCR) "OCR Screen Engine" else "Accessibility Scanner"
        val analysis = QuestionDetectionEngine.analyze(cleanText, _settings.value.detectQuestionsOnly)

        val hasQuestionMark = cleanText.contains("?") || cleanText.contains("？") || cleanText.contains("¿")
        if (_settings.value.detectQuestionsOnly && !hasQuestionMark) {
            addDiagnosticLog(
                source = sourceLabel,
                rawText = cleanText,
                result = DetectionResultType.REJECTED,
                category = "NO_QUESTION_MARK",
                reason = "Rejected: Text does not contain a question mark '?' (Strict interrogation mark check)",
                detectionMethod = detectionMethod,
                latencyMs = ocrLatencyMs
            )
            return
        }

        if (!analysis.isQuestion) {
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

        // Record question in processed cache
        val norm = normalizeQuestionText(cleanText)
        processedQuestionsCache[norm] = System.currentTimeMillis()
        purgeExpiredData()

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

        scope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            _activeReplies.value = emptyList()

            val initialQuestion = DetectedQuestion(
                text = cleanText,
                sourceApp = sourceApp,
                packageName = packageName,
                generatedByProvider = _activeProvider.value ?: _settings.value.preferredProvider,
                detectionMethod = detectionMethod,
                ocrLatencyMs = ocrLatencyMs
            )

            _currentQuestion.value = initialQuestion
            _questionsHistory.value = listOf(initialQuestion) + _questionsHistory.value.take(49)

            try {
                val fallbackResult = AiFallbackEngine.generateRepliesWithFallback(
                    question = cleanText,
                    settings = _settings.value,
                    onLog = { src, raw, res, cat, rsn, lat ->
                        addDiagnosticLog(
                            source = src,
                            rawText = raw,
                            result = res,
                            category = cat,
                            reason = rsn,
                            detectionMethod = detectionMethod,
                            latencyMs = lat
                        )
                    }
                )

                // Update active provider state to match the exact provider that successfully yielded the replies
                _activeProvider.value = fallbackResult.usedProvider

                val finalQuestion = initialQuestion.copy(
                    englishMeaning = fallbackResult.understanding,
                    generatedByProvider = fallbackResult.usedProvider,
                    fallbackNotice = fallbackResult.fallbackNotice
                )

                _currentQuestion.value = finalQuestion
                _activeReplies.value = fallbackResult.replies
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
