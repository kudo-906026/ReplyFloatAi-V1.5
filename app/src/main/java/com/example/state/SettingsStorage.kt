package com.example.state

import android.content.Context
import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.OverlayBarStyle
import com.example.model.OverlayInteractionMode
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.model.SavedOverlayPosition
import com.example.model.UnderstandingSummaryLength
import com.example.model.WhitelistedApp
import com.example.model.defaultBuiltInProviders
import com.example.model.defaultWhitelistedApps
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SettingsStorage {
    private const val PREFS_NAME = "reply_float_settings_prefs"
    private const val KEY_SETTINGS_JSON = "saved_reply_settings_v1"

    fun saveSettings(context: Context, settings: ReplySettings) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = serializeSettings(settings)
            prefs.edit().putString(KEY_SETTINGS_JSON, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSettings(context: Context): ReplySettings {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SETTINGS_JSON, null)
            if (!json.isNullOrBlank()) {
                return deserializeSettings(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ReplySettings()
    }

    fun serializeSettings(settings: ReplySettings): String {
        val root = JSONObject()

        // 1. Fallback order
        val fallbackArray = JSONArray()
        settings.fallbackOrder.forEach { fallbackArray.put(it) }
        root.put("fallbackOrder", fallbackArray)

        // 2. Preferred provider ID
        root.put("preferredProviderId", settings.preferredProvider.id)

        // 3. Provider API Keys
        val keysObj = JSONObject()
        settings.providerApiKeys.forEach { (k, v) -> keysObj.put(k, v) }
        root.put("providerApiKeys", keysObj)

        // 3b. Provider Model Overrides
        val modelsObj = JSONObject()
        settings.providerModelOverrides.forEach { (k, v) -> modelsObj.put(k, v) }
        root.put("providerModelOverrides", modelsObj)

        // 4. Custom Providers
        val customArray = JSONArray()
        settings.customProviders.forEach { cp ->
            val cpObj = JSONObject().apply {
                put("id", cp.id)
                put("name", cp.name)
                put("displayName", cp.displayName)
                put("modelName", cp.modelName)
                put("apiKey", cp.apiKey)
                put("customEndpoint", cp.customEndpoint ?: "")
                put("tier", cp.tier.name)
                put("type", cp.type.name)
            }
            customArray.put(cpObj)
        }
        root.put("customProviders", customArray)

        // 5. Apps Whitelist
        val appsArray = JSONArray()
        settings.appsWhitelist.forEach { app ->
            val appObj = JSONObject().apply {
                put("packageName", app.packageName)
                put("appName", app.appName)
                put("category", app.category)
                put("isEnabled", app.isEnabled)
                put("isCustom", app.isCustom)
            }
            appsArray.put(appObj)
        }
        root.put("appsWhitelist", appsArray)

        // 6. Saved Overlay Positions
        val posArray = JSONArray()
        settings.savedPositions.forEach { pos ->
            val posObj = JSONObject().apply {
                put("id", pos.id)
                put("packageName", pos.packageName)
                put("appName", pos.appName)
                put("x", pos.x)
                put("y", pos.y)
            }
            posArray.put(posObj)
        }
        root.put("savedPositions", posArray)

        // 7. Core Configuration Fields
        root.put("tone", settings.tone.name)
        root.put("count", settings.count)
        root.put("autoGenerate", settings.autoGenerate)
        root.put("detectQuestionsOnly", settings.detectQuestionsOnly)
        root.put("prefetchOnAppFocus", settings.prefetchOnAppFocus)
        root.put("autoCopySingleReply", settings.autoCopySingleReply)
        root.put("understandingMode", settings.understandingMode)
        root.put("understandingSummaryLength", settings.understandingSummaryLength.name)
        root.put("expandableReplies", settings.expandableReplies)
        root.put("responseLengthPreset", settings.responseLengthPreset.name)
        root.put("customCharLimit", settings.customCharLimit)
        root.put("replyAutoDeleteMinutes", settings.replyAutoDeleteMinutes)
        root.put("historyPurgeMinutes", settings.historyPurgeMinutes)
        root.put("autoPurgeTimerMinutes", settings.autoPurgeTimerMinutes)
        root.put("cacheRetentionMinutes", settings.cacheRetentionMinutes)
        root.put("historyRetentionDays", settings.historyRetentionDays)
        root.put("continuousScreenAnalysis", settings.continuousScreenAnalysis)
        root.put("realTimeNodeTracking", settings.realTimeNodeTracking)
        root.put("smartDebounceMs", settings.smartDebounceMs)
        root.put("overlayBarStyle", settings.overlayBarStyle.name)
        root.put("overlayInteractionMode", settings.overlayInteractionMode.name)
        root.put("autoHideEnabled", settings.autoHideEnabled)
        root.put("autoHideDelaySec", settings.autoHideDelaySec)
        root.put("screenIdleTimeoutSec", settings.screenIdleTimeoutSec)
        root.put("overlayOpacity", settings.overlayOpacity.toDouble())
        root.put("overlayCornerRadius", settings.overlayCornerRadius)
        root.put("overlayTextSizeSp", settings.overlayTextSizeSp)
        root.put("enableOcrFallback", settings.enableOcrFallback)
        root.put("ocrDebounceMs", settings.ocrDebounceMs)

        return root.toString()
    }

    fun deserializeSettings(jsonStr: String): ReplySettings {
        val root = JSONObject(jsonStr)

        // 1. Custom Providers
        val customProviders = mutableListOf<AiProvider>()
        if (root.has("customProviders")) {
            val customArray = root.getJSONArray("customProviders")
            for (i in 0 until customArray.length()) {
                val cpObj = customArray.getJSONObject(i)
                val type = try {
                    AiProviderType.valueOf(cpObj.optString("type", "CUSTOM_REST"))
                } catch (_: Exception) {
                    AiProviderType.CUSTOM_REST
                }
                val tier = try {
                    AiModelTier.valueOf(cpObj.optString("tier", "BALANCED"))
                } catch (_: Exception) {
                    AiModelTier.BALANCED
                }
                customProviders.add(
                    AiProvider(
                        id = cpObj.getString("id"),
                        type = type,
                        name = cpObj.optString("name", "custom"),
                        displayName = cpObj.optString("displayName", "Custom Provider"),
                        modelName = cpObj.optString("modelName", ""),
                        apiKey = cpObj.optString("apiKey", ""),
                        customEndpoint = cpObj.optString("customEndpoint").takeIf { it.isNotBlank() },
                        isCustom = true,
                        tier = tier
                    )
                )
            }
        }

        // 2. API Keys
        val providerApiKeys = mutableMapOf<String, String>()
        if (root.has("providerApiKeys")) {
            val keysObj = root.getJSONObject("providerApiKeys")
            val keysIter = keysObj.keys()
            while (keysIter.hasNext()) {
                val k = keysIter.next()
                providerApiKeys[k] = keysObj.getString(k)
            }
        }

        // 2b. Model Overrides
        val providerModelOverrides = mutableMapOf<String, String>()
        if (root.has("providerModelOverrides")) {
            val modelsObj = root.getJSONObject("providerModelOverrides")
            val modelsIter = modelsObj.keys()
            while (modelsIter.hasNext()) {
                val k = modelsIter.next()
                providerModelOverrides[k] = modelsObj.getString(k)
            }
        }

        // 3. Fallback Order
        val fallbackOrder = mutableListOf<String>()
        if (root.has("fallbackOrder")) {
            val fallbackArray = root.getJSONArray("fallbackOrder")
            for (i in 0 until fallbackArray.length()) {
                fallbackOrder.add(fallbackArray.getString(i))
            }
        }

        // 4. Map all available providers with their updated API keys and model overrides
        val allBuiltIn = defaultBuiltInProviders().map { bp ->
            val key = providerApiKeys[bp.id]
            val model = providerModelOverrides[bp.id]
            var updated = bp
            if (!key.isNullOrBlank()) updated = updated.copy(apiKey = key)
            if (!model.isNullOrBlank()) updated = updated.copy(modelName = model)
            updated
        }
        val allProvidersMap = (allBuiltIn + customProviders).associateBy { it.id }.toMutableMap()

        // 5. Final fallback order
        val finalFallbackOrder = if (fallbackOrder.isNotEmpty()) {
            val valid = fallbackOrder.filter { allProvidersMap.containsKey(it) }.toMutableList()
            allProvidersMap.keys.forEach { if (!valid.contains(it)) valid.add(it) }
            valid
        } else {
            listOf("openai", "gemini-api", "gemini-builtin", "anthropic", "groq")
        }

        // 6. Preferred Provider
        val preferredProviderId = root.optString("preferredProviderId", finalFallbackOrder.firstOrNull() ?: "openai")
        val preferredProvider = allProvidersMap[preferredProviderId]
            ?: finalFallbackOrder.firstOrNull()?.let { allProvidersMap[it] }
            ?: defaultBuiltInProviders()[0]

        // 7. Apps Whitelist
        val appsWhitelist = mutableListOf<WhitelistedApp>()
        if (root.has("appsWhitelist")) {
            val appsArray = root.getJSONArray("appsWhitelist")
            for (i in 0 until appsArray.length()) {
                val appObj = appsArray.getJSONObject(i)
                appsWhitelist.add(
                    WhitelistedApp(
                        packageName = appObj.getString("packageName"),
                        appName = appObj.optString("appName", ""),
                        category = appObj.optString("category", "General"),
                        isEnabled = appObj.optBoolean("isEnabled", true),
                        isCustom = appObj.optBoolean("isCustom", false)
                    )
                )
            }
        }
        val finalApps = if (appsWhitelist.isNotEmpty()) appsWhitelist else defaultWhitelistedApps()

        // 8. Saved Overlay Positions
        val savedPositions = mutableListOf<SavedOverlayPosition>()
        if (root.has("savedPositions")) {
            val posArray = root.getJSONArray("savedPositions")
            for (i in 0 until posArray.length()) {
                val posObj = posArray.getJSONObject(i)
                savedPositions.add(
                    SavedOverlayPosition(
                        id = posObj.optString("id", UUID.randomUUID().toString()),
                        packageName = posObj.getString("packageName"),
                        appName = posObj.optString("appName", ""),
                        x = posObj.getInt("x"),
                        y = posObj.getInt("y")
                    )
                )
            }
        }

        val tone = try {
            ReplyTone.valueOf(root.optString("tone", ReplyTone.CASUAL.name))
        } catch (_: Exception) {
            ReplyTone.CASUAL
        }

        val underLength = try {
            UnderstandingSummaryLength.valueOf(root.optString("understandingSummaryLength", UnderstandingSummaryLength.BALANCED.name))
        } catch (_: Exception) {
            UnderstandingSummaryLength.BALANCED
        }

        val respLengthPreset = try {
            ResponseLengthPreset.valueOf(root.optString("responseLengthPreset", ResponseLengthPreset.SHORT.name))
        } catch (_: Exception) {
            ResponseLengthPreset.SHORT
        }

        val overlayStyle = try {
            OverlayBarStyle.valueOf(root.optString("overlayBarStyle", OverlayBarStyle.MINIMAL_PILL.name))
        } catch (_: Exception) {
            OverlayBarStyle.MINIMAL_PILL
        }

        val interactionMode = try {
            OverlayInteractionMode.valueOf(root.optString("overlayInteractionMode", OverlayInteractionMode.FLOATING_DRAGGABLE.name))
        } catch (_: Exception) {
            OverlayInteractionMode.FLOATING_DRAGGABLE
        }

        return ReplySettings(
            preferredProvider = preferredProvider,
            fallbackOrder = finalFallbackOrder,
            providerApiKeys = providerApiKeys,
            providerModelOverrides = providerModelOverrides,
            tone = tone,
            count = root.optInt("count", 3),
            autoGenerate = root.optBoolean("autoGenerate", true),
            detectQuestionsOnly = root.optBoolean("detectQuestionsOnly", true),
            prefetchOnAppFocus = root.optBoolean("prefetchOnAppFocus", true),
            autoCopySingleReply = root.optBoolean("autoCopySingleReply", false),
            understandingMode = root.optBoolean("understandingMode", true),
            understandingSummaryLength = underLength,
            expandableReplies = root.optBoolean("expandableReplies", true),
            responseLengthPreset = respLengthPreset,
            customCharLimit = root.optInt("customCharLimit", 120),
            replyAutoDeleteMinutes = root.optInt("replyAutoDeleteMinutes", 1),
            historyPurgeMinutes = root.optInt("historyPurgeMinutes", 5),
            autoPurgeTimerMinutes = root.optInt("autoPurgeTimerMinutes", 5),
            cacheRetentionMinutes = root.optInt("cacheRetentionMinutes", 5),
            historyRetentionDays = root.optInt("historyRetentionDays", 7),
            continuousScreenAnalysis = root.optBoolean("continuousScreenAnalysis", true),
            realTimeNodeTracking = root.optBoolean("realTimeNodeTracking", true),
            smartDebounceMs = root.optInt("smartDebounceMs", 300),
            overlayBarStyle = overlayStyle,
            overlayInteractionMode = interactionMode,
            autoHideEnabled = root.optBoolean("autoHideEnabled", true),
            autoHideDelaySec = root.optInt("autoHideDelaySec", 12),
            screenIdleTimeoutSec = root.optInt("screenIdleTimeoutSec", 30),
            overlayOpacity = root.optDouble("overlayOpacity", 0.95).toFloat(),
            overlayCornerRadius = root.optInt("overlayCornerRadius", 18),
            overlayTextSizeSp = root.optInt("overlayTextSizeSp", 13),
            savedPositions = savedPositions,
            appsWhitelist = finalApps,
            customProviders = customProviders,
            enableOcrFallback = root.optBoolean("enableOcrFallback", true),
            ocrDebounceMs = root.optInt("ocrDebounceMs", 1200)
        )
    }
}
