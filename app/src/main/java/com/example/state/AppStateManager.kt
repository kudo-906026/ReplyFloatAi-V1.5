package com.example.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.api.AiFallbackEngine
import com.example.model.AiProvider
import com.example.model.ApiCallLog
import com.example.model.ApiCallStatus
import com.example.model.DetectedQuestion
import com.example.model.DiagnosticItem
import com.example.model.DiagnosticStatus
import com.example.model.ProviderSelectionMode
import com.example.model.QuestionDetectionHistory
import com.example.model.ReplyItem
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.SystemHealthState
import com.example.util.QuestionValidator
import com.example.util.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

data class ProviderErrorRecord(
    val plainReason: String,
    val technicalDetails: String,
    val suggestedFix: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AppStateManager {
    private const val TAG = "AppStateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var generationJob: Job? = null

    private val _settings = MutableStateFlow(ReplySettings())
    val settings: StateFlow<ReplySettings> = _settings.asStateFlow()

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

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _isAccessibilityRunning = MutableStateFlow(false)
    val isAccessibilityRunning: StateFlow<Boolean> = _isAccessibilityRunning.asStateFlow()

    private val _isOverlayExpanded = MutableStateFlow(false)
    val isOverlayExpanded: StateFlow<Boolean> = _isOverlayExpanded.asStateFlow()

    // Real-time API Call Counter & Audit Log
    private val _totalApiCallsCount = MutableStateFlow(0)
    val totalApiCallsCount: StateFlow<Int> = _totalApiCallsCount.asStateFlow()

    private val _providerCallCounts = MutableStateFlow<Map<AiProvider, Int>>(emptyMap())
    val providerCallCounts: StateFlow<Map<AiProvider, Int>> = _providerCallCounts.asStateFlow()

    private val _recentApiCallLogs = MutableStateFlow<List<ApiCallLog>>(emptyList())
    val recentApiCallLogs: StateFlow<List<ApiCallLog>> = _recentApiCallLogs.asStateFlow()

    // Question Deduplication Cache & In-Flight Guard
    private val seenQuestionsCache = Collections.synchronizedSet(LinkedHashSet<String>())
    @Volatile
    private var inFlightQuestionNormalized: String? = null

    // Live Diagnostics & System Health state
    private val _providerErrors = MutableStateFlow<Map<AiProvider, ProviderErrorRecord>>(emptyMap())
    private val _providerSuccess = MutableStateFlow<Map<AiProvider, Long>>(emptyMap())

    private val _diagnosticsState = MutableStateFlow(SystemHealthState())
    val diagnosticsState: StateFlow<SystemHealthState> = _diagnosticsState.asStateFlow()

    private val _isDiagnosticsPanelOpen = MutableStateFlow(false)
    val isDiagnosticsPanelOpen: StateFlow<Boolean> = _isDiagnosticsPanelOpen.asStateFlow()

    private val _history = MutableStateFlow<List<QuestionDetectionHistory>>(
        listOf(
            QuestionDetectionHistory(
                question = "Are you available for a quick sync tomorrow at 10 AM?",
                replies = listOf(
                    "Yes, 10 AM works great for me!",
                    "Could we push it to 11 AM instead?",
                    "Available then, looking forward to it."
                ),
                sourceApp = "Messaging",
                generatedByProvider = AiProvider.GEMINI,
                timestamp = System.currentTimeMillis() - 30_000
            )
        )
    )
    val history: StateFlow<List<QuestionDetectionHistory>> = _history.asStateFlow()

    private var lastProcessedQuestion: String? = null
    private var lastDetectionTime: Long = 0
    private var appContext: Context? = null

    init {
        recomputeDiagnostics()

        // Start lightweight background auto-cleanup ticker running on Dispatchers.Default
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                cleanupExpiredHistory()
            }
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        try {
            val savedSettings = SettingsPreferences.loadSettings(appCtx)
            _settings.value = savedSettings
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved settings from SharedPreferences", e)
        }
        recomputeDiagnostics()
    }

    private fun persistCurrentSettings() {
        val ctx = appContext ?: return
        try {
            SettingsPreferences.saveSettings(ctx, _settings.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings to SharedPreferences", e)
        }
    }

    fun cleanupExpiredHistory() {
        val cfg = _settings.value
        if (!cfg.autoDeleteHistory || _history.value.isEmpty()) return

        val maxAgeMs = cfg.autoDeleteMinutes.coerceIn(1, 10) * 60 * 1000L
        val now = System.currentTimeMillis()

        _history.update { currentList ->
            val filtered = currentList.filter { item ->
                (now - item.timestamp) < maxAgeMs
            }
            if (filtered.size == currentList.size) currentList else filtered
        }
    }

    fun updateSettings(newSettings: ReplySettings) {
        _settings.value = newSettings
        persistCurrentSettings()
        if (newSettings.autoDeleteHistory) {
            cleanupExpiredHistory()
        }
        recomputeDiagnostics()
    }

    fun updateProviderApiKey(provider: AiProvider, key: String) {
        _settings.update { current ->
            when (provider) {
                AiProvider.GEMINI -> current.copy(geminiApiKey = key.trim(), customApiKey = key.trim())
                AiProvider.OPENAI -> current.copy(openaiApiKey = key.trim())
                AiProvider.CLAUDE -> current.copy(claudeApiKey = key.trim())
                AiProvider.GROK -> current.copy(grokApiKey = key.trim())
            }
        }
        persistCurrentSettings()
        recomputeDiagnostics()
    }

    fun updateProviderKey(provider: AiProvider, key: String) {
        updateProviderApiKey(provider, key)
    }

    fun updateSelectionMode(mode: ProviderSelectionMode) {
        _settings.update { it.copy(selectionMode = mode) }
        persistCurrentSettings()
        recomputeDiagnostics()
    }

    fun updatePreferredProvider(provider: AiProvider) {
        _settings.update { it.copy(preferredProvider = provider) }
        persistCurrentSettings()
        recomputeDiagnostics()
    }

    fun moveProviderInChain(fromIndex: Int, toIndex: Int) {
        _settings.update { current ->
            val chain = current.providerChain.toMutableList()
            if (fromIndex in chain.indices && toIndex in chain.indices) {
                val item = chain.removeAt(fromIndex)
                chain.add(toIndex, item)
                current.copy(providerChain = chain)
            } else {
                current
            }
        }
        persistCurrentSettings()
    }

    fun updateProviderChain(newChain: List<AiProvider>) {
        _settings.update { it.copy(providerChain = newChain) }
        persistCurrentSettings()
    }

    fun updateAutoDeleteSettings(enabled: Boolean, minutes: Int) {
        _settings.update {
            it.copy(
                autoDeleteHistory = enabled,
                autoDeleteMinutes = minutes.coerceIn(1, 10)
            )
        }
        persistCurrentSettings()
        cleanupExpiredHistory()
    }

    fun updateReplyLength(length: ReplyLength) {
        _settings.update { it.copy(length = length) }
        persistCurrentSettings()
    }

    fun updateReplyCount(count: Int) {
        _settings.update { it.copy(count = count.coerceIn(1, 3)) }
        persistCurrentSettings()
    }

    fun updateTone(tone: ReplyTone) {
        _settings.update { it.copy(tone = tone) }
        persistCurrentSettings()
    }

    fun toggleMultiLanguage() {
        val newState = !_settings.value.multiLanguageEnabled
        _settings.update { it.copy(multiLanguageEnabled = newState) }
        persistCurrentSettings()
    }

    fun toggleScanning() {
        val newState = !_settings.value.scanningEnabled
        _settings.update { it.copy(scanningEnabled = newState) }
        persistCurrentSettings()
    }

    fun setMultiLanguageEnabled(enabled: Boolean) {
        _settings.update { it.copy(multiLanguageEnabled = enabled) }
        persistCurrentSettings()
    }

    fun setScanningEnabled(enabled: Boolean) {
        _settings.update { it.copy(scanningEnabled = enabled) }
        persistCurrentSettings()
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
        if (!running) {
            _isOverlayExpanded.value = false
            _activeReplies.value = emptyList()
            _currentQuestion.value = null
            _errorMessage.value = null
            generationJob?.cancel()
            _isGenerating.value = false
        }
        recomputeDiagnostics()
    }

    fun setAccessibilityRunning(running: Boolean) {
        _isAccessibilityRunning.value = running
        recomputeDiagnostics()
    }

    fun setOverlayExpanded(expanded: Boolean) {
        _isOverlayExpanded.value = expanded
    }

    fun closeMainBar() {
        _isOverlayExpanded.value = false
        _activeReplies.value = emptyList()
        _currentQuestion.value = null
        _errorMessage.value = null
    }

    fun setDiagnosticsPanelOpen(open: Boolean) {
        _isDiagnosticsPanelOpen.value = open
    }

    fun toggleDiagnosticsPanel() {
        _isDiagnosticsPanelOpen.update { !it }
    }

    // --- API Call Audit Logging ---

    fun recordApiCallStart(provider: AiProvider, model: String, question: String): String {
        val callId = UUID.randomUUID().toString()
        _totalApiCallsCount.update { it + 1 }
        _providerCallCounts.update { current ->
            current + (provider to ((current[provider] ?: 0) + 1))
        }
        val log = ApiCallLog(
            id = callId,
            provider = provider,
            model = model,
            question = question,
            timestamp = System.currentTimeMillis(),
            status = ApiCallStatus.IN_FLIGHT
        )
        _recentApiCallLogs.update { current ->
            (listOf(log) + current).take(60)
        }
        return callId
    }

    fun recordApiCallSuccess(callId: String, durationMs: Long, repliesCount: Int) {
        _recentApiCallLogs.update { list ->
            list.map { item ->
                if (item.id == callId) {
                    item.copy(
                        status = ApiCallStatus.SUCCESS,
                        durationMs = durationMs,
                        repliesCount = repliesCount
                    )
                } else item
            }
        }
    }

    fun recordApiCallFailure(callId: String, durationMs: Long, error: String) {
        _recentApiCallLogs.update { list ->
            list.map { item ->
                if (item.id == callId) {
                    item.copy(
                        status = ApiCallStatus.FAILED,
                        durationMs = durationMs,
                        error = error
                    )
                } else item
            }
        }
    }

    fun clearApiCallLogs() {
        _recentApiCallLogs.value = emptyList()
        _totalApiCallsCount.value = 0
        _providerCallCounts.value = emptyMap()
    }

    fun recordProviderSuccess(provider: AiProvider) {
        _providerErrors.update { it - provider }
        _providerSuccess.update { it + (provider to System.currentTimeMillis()) }
        recomputeDiagnostics()
    }

    fun recordProviderError(
        provider: AiProvider,
        plainReason: String,
        technicalDetails: String,
        suggestedFix: String
    ) {
        _providerErrors.update {
            it + (provider to ProviderErrorRecord(
                plainReason = plainReason,
                technicalDetails = technicalDetails,
                suggestedFix = suggestedFix,
                timestamp = System.currentTimeMillis()
            ))
        }
        recomputeDiagnostics()
    }

    fun clearAllErrors() {
        _providerErrors.value = emptyMap()
        _errorMessage.value = null
        recomputeDiagnostics()
    }

    fun recomputeDiagnostics(context: Context? = null) {
        val currentSettings = _settings.value
        val items = mutableListOf<DiagnosticItem>()

        // 1. Accessibility Service check
        val isA11yActive = _isAccessibilityRunning.value
        items.add(
            DiagnosticItem(
                id = "accessibility_service",
                componentName = "Accessibility Service",
                status = if (isA11yActive) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                plainDescription = if (isA11yActive) {
                    "Accessibility Service is active and capturing on-screen questions."
                } else {
                    "Accessibility Service is turned OFF. ReplyFloat cannot auto-detect questions from other apps."
                },
                technicalDetails = if (isA11yActive) "QuestionDetectorAccessibilityService is connected" else "QuestionDetectorAccessibilityService is inactive / unbound",
                suggestedFix = if (isA11yActive) null else "Open Android Settings > Accessibility > Installed apps > Enable 'ReplyFloatAi'"
            )
        )

        // 2. Overlay Permission check
        val isOverlayPermitted = if (context != null) {
            Settings.canDrawOverlays(context)
        } else {
            _isOverlayRunning.value
        }
        items.add(
            DiagnosticItem(
                id = "overlay_permission",
                componentName = "Overlay Permission",
                status = if (isOverlayPermitted) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                plainDescription = if (isOverlayPermitted) {
                    "Display over other apps permission is granted. Floating bar is active."
                } else {
                    "Display over other apps permission is not granted. Floating UI cannot appear."
                },
                technicalDetails = if (isOverlayPermitted) "SYSTEM_ALERT_WINDOW granted" else "Settings.canDrawOverlays() returned false",
                suggestedFix = if (isOverlayPermitted) null else "Go to Android Settings > Apps > Special app access > Display over other apps > Enable ReplyFloatAi"
            )
        )

        // 3. Question Detection Scanning check
        val isScanningOn = currentSettings.scanningEnabled
        items.add(
            DiagnosticItem(
                id = "question_detection",
                componentName = "Question Detection",
                status = when {
                    !isScanningOn -> DiagnosticStatus.WARNING
                    !isA11yActive -> DiagnosticStatus.WARNING
                    else -> DiagnosticStatus.HEALTHY
                },
                plainDescription = when {
                    !isScanningOn -> "Screen question scanning is paused by user."
                    !isA11yActive -> "Question scanner is waiting for Accessibility Service to be enabled."
                    else -> "Real-time AI question scanner is active with false-positive filtering."
                },
                technicalDetails = "QuestionValidator heuristic parser & URL filter running",
                suggestedFix = when {
                    !isScanningOn -> "Toggle 'Screen Scanning' ON in Settings or the floating overlay bar."
                    !isA11yActive -> "Enable Accessibility Service to resume live question capture."
                    else -> null
                }
            )
        )

        // 4. AI Providers checks (Gemini, OpenAI, Claude, Grok)
        AiProvider.entries.forEach { provider ->
            val error = _providerErrors.value[provider]
            val hasKey = when (provider) {
                AiProvider.GEMINI -> currentSettings.customApiKey.isNotBlank() || com.example.BuildConfig.GEMINI_API_KEY.isNotBlank()
                AiProvider.OPENAI -> currentSettings.openAiApiKey.isNotBlank()
                AiProvider.CLAUDE -> currentSettings.claudeApiKey.isNotBlank()
                AiProvider.GROK -> currentSettings.grokApiKey.isNotBlank()
            }

            val item = when {
                error != null -> {
                    DiagnosticItem(
                        id = "${provider.id}_provider",
                        componentName = "${provider.displayName} Provider",
                        status = DiagnosticStatus.ERROR,
                        plainDescription = "${provider.displayName} API call failed: ${error.plainReason}",
                        technicalDetails = error.technicalDetails,
                        suggestedFix = error.suggestedFix,
                        lastUpdated = error.timestamp
                    )
                }
                hasKey -> {
                    val lastSuccess = _providerSuccess.value[provider]
                    DiagnosticItem(
                        id = "${provider.id}_provider",
                        componentName = "${provider.displayName} Provider",
                        status = DiagnosticStatus.HEALTHY,
                        plainDescription = "Ready for replies (Model: ${provider.defaultModel})" + (if (lastSuccess != null) " • Last successful response verified" else ""),
                        technicalDetails = "API Key is configured and valid",
                        suggestedFix = null
                    )
                }
                else -> {
                    DiagnosticItem(
                        id = "${provider.id}_provider",
                        componentName = "${provider.displayName} Provider",
                        status = DiagnosticStatus.WARNING,
                        plainDescription = "No API key configured for ${provider.displayName}.",
                        technicalDetails = "API key is empty in Settings",
                        suggestedFix = "Add your ${provider.displayName} key in Settings > API Keys to use it for AI replies."
                    )
                }
            }
            items.add(item)
        }

        val errorCount = items.count { it.status == DiagnosticStatus.ERROR }
        val warningCount = items.count { it.status == DiagnosticStatus.WARNING }
        val healthyCount = items.count { it.status == DiagnosticStatus.HEALTHY }

        val overall = when {
            errorCount > 0 -> DiagnosticStatus.ERROR
            warningCount > 0 -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.HEALTHY
        }

        _diagnosticsState.value = SystemHealthState(
            overallStatus = overall,
            items = items,
            errorCount = errorCount,
            warningCount = warningCount,
            healthyCount = healthyCount
        )
    }

    fun normalizeQuestion(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun onQuestionDetected(rawQuestion: String, sourceApp: String? = null, force: Boolean = false) {
        val cleaned = cleanQuestion(rawQuestion)
        if (cleaned.isBlank()) return

        val normalized = normalizeQuestion(cleaned)
        if (normalized.isBlank()) return

        // 1. If scanning / Analyze is turned OFF in settings, ignore screen detection (unless forced)
        if (!force && !_settings.value.scanningEnabled) {
            Log.d(TAG, "[A11Y_DETECT] Analyze is OFF; skipping detection: \"$cleaned\"")
            return
        }

        val now = System.currentTimeMillis()

        if (!force) {
            // Guard 1: Is this exact question currently generating in-flight?
            if (normalized == inFlightQuestionNormalized) {
                Log.d(TAG, "[DEDUP] Question is already currently in-flight: \"$cleaned\"")
                return
            }

            // Guard 2: Active replies already visible on screen for this question
            val currentNormalized = normalizeQuestion(_currentQuestion.value?.text ?: "")
            if (currentNormalized == normalized && _activeReplies.value.isNotEmpty()) {
                Log.d(TAG, "[DEDUP] Active replies already visible on screen for: \"$cleaned\"")
                return
            }

            // Guard 3: If engine is currently generating an active question, don't overlap
            if (_isGenerating.value) {
                Log.d(TAG, "[DEDUP] Engine is busy generating; skipping background detection: \"$cleaned\"")
                return
            }

            // Guard 4: If this question was already detected and processed within the last 15 seconds, skip duplicate spam
            if (seenQuestionsCache.contains(normalized) && (now - lastDetectionTime < 15_000L)) {
                Log.d(TAG, "[DEDUP] Question was recently processed: \"$cleaned\"")
                return
            }
        }

        lastDetectionTime = now
        seenQuestionsCache.add(normalized)
        if (seenQuestionsCache.size > 100) {
            val iterator = seenQuestionsCache.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        val questionObj = DetectedQuestion(
            text = cleaned,
            sourceApp = sourceApp,
            timestamp = now
        )
        _currentQuestion.value = questionObj
        _isOverlayExpanded.value = true

        // Automatically call the AI to generate replies immediately without requiring any extra tap
        generateRepliesForQuestion(questionText = cleaned, sourceApp = sourceApp, force = true)
    }

    fun generateRepliesForQuestion(
        questionText: String? = null,
        sourceApp: String? = null,
        force: Boolean = true
    ) {
        val text = questionText ?: _currentQuestion.value?.text ?: return
        if (text.isBlank()) return

        val normalized = normalizeQuestion(text)

        // Cancel previous request
        generationJob?.cancel()

        _activeReplies.value = emptyList()
        _errorMessage.value = null
        _isGenerating.value = true
        inFlightQuestionNormalized = normalized
        seenQuestionsCache.add(normalized)

        generationJob = scope.launch {
            try {
                val currentCfg = _settings.value

                // Execute the fallback chain across configured providers (strictly 1 API call on success)
                val result = AiFallbackEngine.generateRepliesWithFallback(
                    question = text,
                    count = currentCfg.count,
                    length = currentCfg.length,
                    tone = currentCfg.tone,
                    multiLanguage = currentCfg.multiLanguageEnabled,
                    settings = currentCfg
                )

                result.onSuccess { aiResult ->
                    val provider = aiResult.provider
                    _activeProvider.value = provider

                    val items = aiResult.replies.map {
                        ReplyItem(text = it, generatedByProvider = provider)
                    }
                    _activeReplies.value = items
                    _errorMessage.value = null

                    // Update current question with any refined text, english translation and provider
                    val resolvedOriginal = aiResult.original.ifBlank { text }
                    val resolvedNormalized = normalizeQuestion(resolvedOriginal)
                    seenQuestionsCache.add(resolvedNormalized)

                    _currentQuestion.update { old ->
                        old?.copy(
                            text = resolvedOriginal,
                            englishMeaning = aiResult.englishMeaning,
                            generatedByProvider = provider
                        ) ?: DetectedQuestion(
                            text = resolvedOriginal,
                            englishMeaning = aiResult.englishMeaning,
                            sourceApp = sourceApp,
                            generatedByProvider = provider
                        )
                    }

                    lastProcessedQuestion = text
                    Log.i(TAG, "Successfully processed via [${provider.displayName}] for: \"$text\"")

                    // Add to history
                    if (items.isNotEmpty()) {
                        _history.update { oldList ->
                            val historyItem = QuestionDetectionHistory(
                                question = resolvedOriginal,
                                englishMeaning = aiResult.englishMeaning,
                                replies = aiResult.replies,
                                sourceApp = sourceApp ?: _currentQuestion.value?.sourceApp,
                                generatedByProvider = provider,
                                timestamp = System.currentTimeMillis()
                            )
                            (listOf(historyItem) + oldList).take(30)
                        }
                    }
                }.onFailure { ex ->
                    Log.e(TAG, "Failed to generate replies for \"$text\": ${ex.message}")
                    _activeReplies.value = emptyList()
                    _activeProvider.value = null
                    _errorMessage.value = ex.message ?: "All providers unavailable — check your API keys in Settings"
                    // Allow retrying this question later
                    seenQuestionsCache.remove(normalized)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Previous generation was cancelled for question: \"$text\"")
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateRepliesForQuestion", e)
                _activeReplies.value = emptyList()
                _activeProvider.value = null
                _errorMessage.value = e.message ?: "All providers unavailable — check your API keys in Settings"
                seenQuestionsCache.remove(normalized)
            } finally {
                _isGenerating.value = false
                inFlightQuestionNormalized = null
            }
        }
    }

    fun copyAndDismissReply(context: Context, item: ReplyItem) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("AI Reply", item.text)
            clipboard?.setPrimaryClip(clip)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Remove that specific card from active replies
        _activeReplies.update { current ->
            current.filterNot { it.id == item.id }
        }
    }

    fun dismissReply(itemId: String) {
        _activeReplies.update { current ->
            current.filterNot { it.id == itemId }
        }
    }

    fun clearReplies() {
        _activeReplies.value = emptyList()
        _currentQuestion.value = null
        _activeProvider.value = null
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun deleteHistoryItem(id: String) {
        _history.update { current ->
            current.filterNot { it.id == id }
        }
    }

    private fun cleanQuestion(raw: String): String {
        return QuestionValidator.cleanAndExtractQuestion(raw) ?: ""
    }
}
