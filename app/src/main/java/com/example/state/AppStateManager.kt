package com.example.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.api.AiFallbackEngine
import com.example.model.AiProvider
import com.example.model.DetectedQuestion
import com.example.model.QuestionDetectionHistory
import com.example.model.ReplyItem
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone
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

    init {
        // Start lightweight background auto-cleanup ticker running on Dispatchers.Default
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                cleanupExpiredHistory()
            }
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
        if (newSettings.autoDeleteHistory) {
            cleanupExpiredHistory()
        }
    }

    fun updateProviderApiKey(provider: AiProvider, key: String) {
        _settings.update { current ->
            when (provider) {
                AiProvider.GEMINI -> current.copy(geminiApiKey = key.trim(), customApiKey = key.trim())
                AiProvider.OPENAI -> current.copy(openaiApiKey = key.trim())
                AiProvider.CLAUDE -> current.copy(claudeApiKey = key.trim())
                AiProvider.GROQ -> current.copy(groqApiKey = key.trim())
            }
        }
    }

    fun updateProviderKey(provider: AiProvider, key: String) {
        updateProviderApiKey(provider, key)
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
    }

    fun updateProviderChain(newChain: List<AiProvider>) {
        _settings.update { it.copy(providerChain = newChain) }
    }

    fun updateAutoDeleteSettings(enabled: Boolean, minutes: Int) {
        _settings.update {
            it.copy(
                autoDeleteHistory = enabled,
                autoDeleteMinutes = minutes.coerceIn(1, 10)
            )
        }
        cleanupExpiredHistory()
    }

    fun updateReplyLength(length: ReplyLength) {
        _settings.update { it.copy(length = length) }
        // If we currently have a question, regenerate replies with the new length
        _currentQuestion.value?.text?.let { q ->
            generateRepliesForQuestion(q, force = true)
        }
    }

    fun updateReplyCount(count: Int) {
        _settings.update { it.copy(count = count.coerceIn(1, 3)) }
        _currentQuestion.value?.text?.let { q ->
            generateRepliesForQuestion(q, force = true)
        }
    }

    fun updateTone(tone: ReplyTone) {
        _settings.update { it.copy(tone = tone) }
    }

    fun toggleMultiLanguage() {
        val newState = !_settings.value.multiLanguageEnabled
        _settings.update { it.copy(multiLanguageEnabled = newState) }
        _currentQuestion.value?.text?.let { q ->
            generateRepliesForQuestion(q, force = true)
        }
    }

    fun toggleScanning() {
        val newState = !_settings.value.scanningEnabled
        _settings.update { it.copy(scanningEnabled = newState) }
    }

    fun setMultiLanguageEnabled(enabled: Boolean) {
        _settings.update { it.copy(multiLanguageEnabled = enabled) }
    }

    fun setScanningEnabled(enabled: Boolean) {
        _settings.update { it.copy(scanningEnabled = enabled) }
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun setAccessibilityRunning(running: Boolean) {
        _isAccessibilityRunning.value = running
    }

    fun setOverlayExpanded(expanded: Boolean) {
        _isOverlayExpanded.value = expanded
    }

    fun onQuestionDetected(rawQuestion: String, sourceApp: String? = null, force: Boolean = false) {
        val cleaned = cleanQuestion(rawQuestion)
        if (cleaned.isBlank()) return

        val now = System.currentTimeMillis()

        // 1. Once a question has been successfully answered, don't regenerate replies for it again
        // unless forced (e.g. manual user test) or the question text genuinely differs
        if (!force && cleaned.equals(lastProcessedQuestion, ignoreCase = true)) {
            Log.d(TAG, "Ignoring already answered/processed question: \"$cleaned\"")
            return
        }

        // 2. If already currently generating this exact question or showing its active replies
        if (!force && cleaned.equals(_currentQuestion.value?.text, ignoreCase = true)) {
            if (_isGenerating.value || _activeReplies.value.isNotEmpty()) {
                return
            }
        }

        lastDetectionTime = now

        val questionObj = DetectedQuestion(
            text = cleaned,
            sourceApp = sourceApp,
            timestamp = now
        )
        _currentQuestion.value = questionObj

        if (_settings.value.autoGenerate) {
            generateRepliesForQuestion(cleaned, sourceApp, force = force)
        }
    }

    fun generateRepliesForQuestion(
        questionText: String? = null,
        sourceApp: String? = null,
        force: Boolean = true
    ) {
        val text = questionText ?: _currentQuestion.value?.text ?: return
        if (text.isBlank()) return

        // If not forced and already processed with active replies, skip
        if (!force && text.equals(lastProcessedQuestion, ignoreCase = true) && _activeReplies.value.isNotEmpty()) {
            return
        }

        // 1. Cancel any previous in-flight AI request so it cannot overwrite this one
        generationJob?.cancel()

        // 2. Clear existing replies, clear error, and show loading indicator immediately
        _activeReplies.value = emptyList()
        _errorMessage.value = null
        _isGenerating.value = true

        generationJob = scope.launch {
            try {
                val currentCfg = _settings.value

                // Execute the fallback chain across configured providers
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

                    // Mark question as successfully processed
                    lastProcessedQuestion = text
                    Log.d(TAG, "Successfully processed via [${provider.displayName}] for: \"$text\"")

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
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Previous generation was cancelled for question: \"$text\"")
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateRepliesForQuestion", e)
                _activeReplies.value = emptyList()
                _activeProvider.value = null
                _errorMessage.value = e.message ?: "All providers unavailable — check your API keys in Settings"
            } finally {
                _isGenerating.value = false
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
        val trimmed = raw.trim()
        val questionMarkIndex = trimmed.lastIndexOfAny(charArrayOf('?', '？'))
        if (questionMarkIndex == -1) return trimmed

        val sentenceStart = trimmed.substring(0, questionMarkIndex).lastIndexOfAny(charArrayOf('\n', '.', '!', ';'))
        val extracted = if (sentenceStart != -1 && sentenceStart < questionMarkIndex) {
            trimmed.substring(sentenceStart + 1, questionMarkIndex + 1).trim()
        } else {
            trimmed.substring(0, questionMarkIndex + 1).trim()
        }

        return extracted.take(300)
    }
}
