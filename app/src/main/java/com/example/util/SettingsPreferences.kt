package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AiProvider
import com.example.model.ProviderSelectionMode
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone

object SettingsPreferences {
    private const val PREFS_NAME = "reply_float_ai_prefs"

    private const val KEY_REPLY_LENGTH = "pref_reply_length"
    private const val KEY_REPLY_COUNT = "pref_reply_count"
    private const val KEY_REPLY_TONE = "pref_reply_tone"
    private const val KEY_AUTO_GENERATE = "pref_auto_generate"
    private const val KEY_AUTO_DELETE_HISTORY = "pref_auto_delete_history"
    private const val KEY_AUTO_DELETE_MINUTES = "pref_auto_delete_minutes"
    private const val KEY_MULTI_LANGUAGE = "pref_multi_language"
    private const val KEY_SCANNING_ENABLED = "pref_scanning_enabled"

    private const val KEY_SELECTION_MODE = "pref_selection_mode"
    private const val KEY_PREFERRED_PROVIDER = "pref_preferred_provider"
    private const val KEY_GEMINI_KEY = "pref_gemini_api_key"
    private const val KEY_OPENAI_KEY = "pref_openai_api_key"
    private const val KEY_CLAUDE_KEY = "pref_claude_api_key"
    private const val KEY_GROK_KEY = "pref_grok_api_key"
    private const val KEY_PROVIDER_CHAIN = "pref_provider_chain"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadSettings(context: Context): ReplySettings {
        val prefs = getPrefs(context)
        val defaultSettings = ReplySettings()

        val lengthName = prefs.getString(KEY_REPLY_LENGTH, defaultSettings.length.name)
        val length = try {
            if (lengthName != null) ReplyLength.valueOf(lengthName) else defaultSettings.length
        } catch (e: Exception) {
            defaultSettings.length
        }

        val count = prefs.getInt(KEY_REPLY_COUNT, defaultSettings.count).coerceIn(1, 3)

        val toneName = prefs.getString(KEY_REPLY_TONE, defaultSettings.tone.name)
        val tone = try {
            if (toneName != null) ReplyTone.valueOf(toneName) else defaultSettings.tone
        } catch (e: Exception) {
            defaultSettings.tone
        }

        val autoGenerate = prefs.getBoolean(KEY_AUTO_GENERATE, defaultSettings.autoGenerate)
        val autoDeleteHistory = prefs.getBoolean(KEY_AUTO_DELETE_HISTORY, defaultSettings.autoDeleteHistory)
        val autoDeleteMinutes = prefs.getInt(KEY_AUTO_DELETE_MINUTES, defaultSettings.autoDeleteMinutes).coerceIn(1, 10)
        val multiLanguage = prefs.getBoolean(KEY_MULTI_LANGUAGE, defaultSettings.multiLanguageEnabled)
        val scanningEnabled = prefs.getBoolean(KEY_SCANNING_ENABLED, defaultSettings.scanningEnabled)

        val selectionModeName = prefs.getString(KEY_SELECTION_MODE, defaultSettings.selectionMode.name)
        val selectionMode = try {
            if (selectionModeName != null) ProviderSelectionMode.valueOf(selectionModeName) else defaultSettings.selectionMode
        } catch (e: Exception) {
            defaultSettings.selectionMode
        }

        val preferredProviderName = prefs.getString(KEY_PREFERRED_PROVIDER, defaultSettings.preferredProvider.name)
        val preferredProvider = try {
            if (preferredProviderName != null) AiProvider.valueOf(preferredProviderName) else defaultSettings.preferredProvider
        } catch (e: Exception) {
            defaultSettings.preferredProvider
        }

        val geminiKey = prefs.getString(KEY_GEMINI_KEY, defaultSettings.geminiApiKey) ?: ""
        val openAiKey = prefs.getString(KEY_OPENAI_KEY, defaultSettings.openaiApiKey) ?: ""
        val claudeKey = prefs.getString(KEY_CLAUDE_KEY, defaultSettings.claudeApiKey) ?: ""
        val grokKey = prefs.getString(KEY_GROK_KEY, defaultSettings.grokApiKey) ?: ""

        val chainString = prefs.getString(KEY_PROVIDER_CHAIN, null)
        val providerChain = if (!chainString.isNullOrBlank()) {
            val list = chainString.split(",").mapNotNull { name ->
                try {
                    AiProvider.valueOf(name.trim())
                } catch (e: Exception) {
                    null
                }
            }
            if (list.isNotEmpty()) list else defaultSettings.providerChain
        } else {
            defaultSettings.providerChain
        }

        return ReplySettings(
            count = count,
            length = length,
            tone = tone,
            autoGenerate = autoGenerate,
            autoDeleteHistory = autoDeleteHistory,
            autoDeleteMinutes = autoDeleteMinutes,
            multiLanguageEnabled = multiLanguage,
            scanningEnabled = scanningEnabled,
            selectionMode = selectionMode,
            preferredProvider = preferredProvider,
            customApiKey = geminiKey,
            geminiApiKey = geminiKey,
            openaiApiKey = openAiKey,
            claudeApiKey = claudeKey,
            grokApiKey = grokKey,
            providerChain = providerChain
        )
    }

    fun saveSettings(context: Context, settings: ReplySettings) {
        val chainString = settings.providerChain.joinToString(",") { it.name }
        getPrefs(context).edit()
            .putString(KEY_REPLY_LENGTH, settings.length.name)
            .putInt(KEY_REPLY_COUNT, settings.count)
            .putString(KEY_REPLY_TONE, settings.tone.name)
            .putBoolean(KEY_AUTO_GENERATE, settings.autoGenerate)
            .putBoolean(KEY_AUTO_DELETE_HISTORY, settings.autoDeleteHistory)
            .putInt(KEY_AUTO_DELETE_MINUTES, settings.autoDeleteMinutes)
            .putBoolean(KEY_MULTI_LANGUAGE, settings.multiLanguageEnabled)
            .putBoolean(KEY_SCANNING_ENABLED, settings.scanningEnabled)
            .putString(KEY_SELECTION_MODE, settings.selectionMode.name)
            .putString(KEY_PREFERRED_PROVIDER, settings.preferredProvider.name)
            .putString(KEY_GEMINI_KEY, settings.geminiApiKey)
            .putString(KEY_OPENAI_KEY, settings.openaiApiKey)
            .putString(KEY_CLAUDE_KEY, settings.claudeApiKey)
            .putString(KEY_GROK_KEY, settings.grokApiKey)
            .putString(KEY_PROVIDER_CHAIN, chainString)
            .apply()
    }
}
