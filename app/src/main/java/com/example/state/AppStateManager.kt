package com.example.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.api.GeminiClient
import com.example.model.DetectedQuestion
import com.example.model.QuestionDetectionHistory
import com.example.model.ReplyItem
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

object AppStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _settings = MutableStateFlow(ReplySettings())
    val settings: StateFlow<ReplySettings> = _settings.asStateFlow()

    private val _currentQuestion = MutableStateFlow<DetectedQuestion?>(null)
    val currentQuestion: StateFlow<DetectedQuestion?> = _currentQuestion.asStateFlow()

    private val _activeReplies = MutableStateFlow<List<ReplyItem>>(emptyList())
    val activeReplies: StateFlow<List<ReplyItem>> = _activeReplies.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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
                timestamp = System.currentTimeMillis() - 30_000
            )
        )
    )
    val history: StateFlow<List<QuestionDetectionHistory>> = _history.asStateFlow()

    private var lastDetectedText: String = ""
    private var lastDetectionTime: Long = 0

    init {
        // Start continuous background auto-cleanup ticker
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000L)
                cleanupExpiredHistory()
            }
        }
    }

    fun cleanupExpiredHistory() {
        val cfg = _settings.value
        if (!cfg.autoDeleteHistory) return

        val maxAgeMs = cfg.autoDeleteMinutes.coerceIn(1, 10) * 60 * 1000L
        val now = System.currentTimeMillis()

        _history.update { currentList ->
            val filtered = currentList.filter { item ->
                (now - item.timestamp) < maxAgeMs
            }
            filtered
        }
    }

    fun updateSettings(newSettings: ReplySettings) {
        _settings.value = newSettings
        if (newSettings.autoDeleteHistory) {
            cleanupExpiredHistory()
        }
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
            generateRepliesForQuestion(q)
        }
    }

    fun updateReplyCount(count: Int) {
        _settings.update { it.copy(count = count.coerceIn(1, 3)) }
        _currentQuestion.value?.text?.let { q ->
            generateRepliesForQuestion(q)
        }
    }

    fun updateTone(tone: ReplyTone) {
        _settings.update { it.copy(tone = tone) }
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

    fun onQuestionDetected(rawQuestion: String, sourceApp: String? = null) {
        val cleaned = cleanQuestion(rawQuestion)
        if (cleaned.isBlank()) return

        val now = System.currentTimeMillis()
        // Deduplicate within 3 seconds for identical question
        if (cleaned.equals(lastDetectedText, ignoreCase = true) && (now - lastDetectionTime) < 3000) {
            return
        }

        lastDetectedText = cleaned
        lastDetectionTime = now

        val questionObj = DetectedQuestion(
            text = cleaned,
            sourceApp = sourceApp,
            timestamp = now
        )
        _currentQuestion.value = questionObj

        if (_settings.value.autoGenerate) {
            generateRepliesForQuestion(cleaned, sourceApp)
        }
    }

    fun generateRepliesForQuestion(questionText: String? = null, sourceApp: String? = null) {
        val text = questionText ?: _currentQuestion.value?.text ?: return
        if (text.isBlank()) return

        scope.launch {
            _isGenerating.value = true
            val currentCfg = _settings.value

            val result = GeminiClient.generateReplies(
                question = text,
                count = currentCfg.count,
                length = currentCfg.length,
                tone = currentCfg.tone,
                customApiKey = currentCfg.customApiKey
            )

            _isGenerating.value = false

            result.onSuccess { replyTexts ->
                val items = replyTexts.map { ReplyItem(text = it) }
                _activeReplies.value = items

                // Add to history
                if (items.isNotEmpty()) {
                    _history.update { oldList ->
                        val historyItem = QuestionDetectionHistory(
                            question = text,
                            replies = replyTexts,
                            sourceApp = sourceApp ?: _currentQuestion.value?.sourceApp,
                            timestamp = System.currentTimeMillis()
                        )
                        (listOf(historyItem) + oldList).take(30)
                    }
                }
            }.onFailure {
                // If failed, generate fallback
                val fallbackTexts = listOf("Yes, sounds good.", "I will get back to you shortly.")
                _activeReplies.value = fallbackTexts.map { ReplyItem(text = it) }
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
        // Extract the sentence ending with ?
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
