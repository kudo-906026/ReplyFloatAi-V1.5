package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.AiProvider
import com.example.model.ProviderSelectionMode
import com.example.model.ReplyLength
import com.example.model.ReplySettings
import com.example.model.ReplyTone

object SettingsPreferences {
    private const val TAG = "SettingsPreferences"
    private const val GENERAL_PREFS_NAME = "reply_float_ai_prefs"
    private const val ENCRYPTED_PREFS_NAME = "reply_float_ai_secure_keys"

    // General configuration keys
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
    private const val KEY_PROVIDER_CHAIN = "pref_provider_chain"

    // Encrypted API key storage keys
    private const val KEY_GEMINI_KEY = "pref_gemini_api_key"
    private const val KEY_OPENAI_KEY = "pref_openai_api_key"
    private const val KEY_CLAUDE_KEY = "pref_claude_api_key"
    private const val KEY_GROK_KEY = "pref_grok_api_key"

    @Volatile
    private var securePrefsInstance: SharedPreferences? = null

    private fun getGeneralPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(GENERAL_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getSecurePrefs(context: Context): SharedPreferences {
        securePrefsInstance?.let { return it }
        synchronized(this) {
            securePrefsInstance?.let { return it }
            val appContext = context.applicationContext
            val prefs = try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    appContext,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences init failed; falling back to private prefs for keys", e)
                appContext.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
            }
            securePrefsInstance = prefs
            return prefs
        }
    }

    fun loadSettings(context: Context): ReplySettings {
        val genPrefs = getGeneralPrefs(context)
        val secPrefs = getSecurePrefs(context)
        val defaultSettings = ReplySettings()

        val lengthName = genPrefs.getString(KEY_REPLY_LENGTH, defaultSettings.length.name)
        val length = try {
            if (lengthName != null) ReplyLength.valueOf(lengthName) else defaultSettings.length
        } catch (_: Exception) {
            defaultSettings.length
        }

        val count = genPrefs.getInt(KEY_REPLY_COUNT, defaultSettings.count).coerceIn(1, 3)

        val toneName = genPrefs.getString(KEY_REPLY_TONE, defaultSettings.tone.name)
        val tone = try {
            if (toneName != null) ReplyTone.valueOf(toneName) else defaultSettings.tone
        } catch (_: Exception) {
            defaultSettings.tone
        }

        val autoGenerate = genPrefs.getBoolean(KEY_AUTO_GENERATE, defaultSettings.autoGenerate)
        val autoDeleteHistory = genPrefs.getBoolean(KEY_AUTO_DELETE_HISTORY, defaultSettings.autoDeleteHistory)
        val autoDeleteMinutes = genPrefs.getInt(KEY_AUTO_DELETE_MINUTES, defaultSettings.autoDeleteMinutes).coerceIn(1, 10)
        val multiLanguage = genPrefs.getBoolean(KEY_MULTI_LANGUAGE, defaultSettings.multiLanguageEnabled)
        val scanningEnabled = genPrefs.getBoolean(KEY_SCANNING_ENABLED, defaultSettings.scanningEnabled)

        val selectionModeName = genPrefs.getString(KEY_SELECTION_MODE, defaultSettings.selectionMode.name)
        val selectionMode = try {
            if (selectionModeName != null) ProviderSelectionMode.valueOf(selectionModeName) else defaultSettings.selectionMode
        } catch (_: Exception) {
            defaultSettings.selectionMode
        }

        val preferredProviderName = genPrefs.getString(KEY_PREFERRED_PROVIDER, defaultSettings.preferredProvider.name)
        val preferredProvider = try {
            if (preferredProviderName != null) AiProvider.valueOf(preferredProviderName) else defaultSettings.preferredProvider
        } catch (_: Exception) {
            defaultSettings.preferredProvider
        }

        val chainString = genPrefs.getString(KEY_PROVIDER_CHAIN, null)
        val providerChain = if (!chainString.isNullOrBlank()) {
            val list = chainString.split(",").mapNotNull { name ->
                try {
                    AiProvider.valueOf(name.trim())
                } catch (_: Exception) {
                    null
                }
            }
            if (list.isNotEmpty()) list else defaultSettings.providerChain
        } else {
            defaultSettings.providerChain
        }

        // Load keys from encrypted preferences (with legacy migration fallback from general prefs if previously stored there)
        val geminiKey = (secPrefs.getString(KEY_GEMINI_KEY, null)
            ?: genPrefs.getString(KEY_GEMINI_KEY, defaultSettings.geminiApiKey) ?: "").trim()
        val openAiKey = (secPrefs.getString(KEY_OPENAI_KEY, null)
            ?: genPrefs.getString(KEY_OPENAI_KEY, defaultSettings.openaiApiKey) ?: "").trim()
        val claudeKey = (secPrefs.getString(KEY_CLAUDE_KEY, null)
            ?: genPrefs.getString(KEY_CLAUDE_KEY, defaultSettings.claudeApiKey) ?: "").trim()
        val grokKey = (secPrefs.getString(KEY_GROK_KEY, null)
            ?: genPrefs.getString(KEY_GROK_KEY, defaultSettings.grokApiKey) ?: "").trim()

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
        try {
            val chainString = settings.providerChain.joinToString(",") { it.name }
            getGeneralPrefs(context).edit()
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
                .putString(KEY_PROVIDER_CHAIN, chainString)
                .apply()

            val effectiveGeminiKey = settings.geminiApiKey.ifBlank { settings.customApiKey }.trim()
            getSecurePrefs(context).edit()
                .putString(KEY_GEMINI_KEY, effectiveGeminiKey)
                .putString(KEY_OPENAI_KEY, settings.openaiApiKey.trim())
                .putString(KEY_CLAUDE_KEY, settings.claudeApiKey.trim())
                .putString(KEY_GROK_KEY, settings.grokApiKey.trim())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist settings", e)
        }
    }

    fun saveProviderKey(context: Context, provider: AiProvider, key: String) {
        try {
            val trimmedKey = key.trim()
            val prefKey = when (provider) {
                AiProvider.GEMINI -> KEY_GEMINI_KEY
                AiProvider.OPENAI -> KEY_OPENAI_KEY
                AiProvider.CLAUDE -> KEY_CLAUDE_KEY
                AiProvider.GROK -> KEY_GROK_KEY
            }
            getSecurePrefs(context).edit().putString(prefKey, trimmedKey).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist key for $provider", e)
        }
    }
}
