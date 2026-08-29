package com.example

import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.defaultBuiltInProviders
import com.example.state.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStorageTest {

    @Test
    fun testSettingsSerializationAndDeserialization() {
        val geminiProvider = defaultBuiltInProviders().first { it.type == AiProviderType.GEMINI_BUILTIN }
        val groqProvider = defaultBuiltInProviders().first { it.type == AiProviderType.GROQ }

        val customProvider = AiProvider(
            id = "custom_test_1",
            type = AiProviderType.CUSTOM_REST,
            name = "custom-llm",
            displayName = "Custom LLM",
            modelName = "mistral-7b",
            apiKey = "sk-custom-12345",
            isCustom = true
        )

        val originalSettings = ReplySettings(
            preferredProvider = geminiProvider,
            fallbackOrder = listOf(geminiProvider.id, groqProvider.id, customProvider.id),
            customProviders = listOf(customProvider),
            providerApiKeys = mapOf(geminiProvider.id to "test_key", customProvider.id to "sk-custom-12345"),
            tone = ReplyTone.CASUAL,
            count = 3
        )

        val jsonString = SettingsStorage.serializeSettings(originalSettings)
        assertNotNull(jsonString)
        assertTrue(jsonString.isNotBlank())

        val restoredSettings = SettingsStorage.deserializeSettings(jsonString)
        assertEquals(geminiProvider.id, restoredSettings.preferredProvider.id)
        assertEquals(geminiProvider.id, restoredSettings.fallbackOrder.first())
        assertEquals(listOf(geminiProvider.id, groqProvider.id, customProvider.id), restoredSettings.fallbackOrder.take(3))
        assertEquals(1, restoredSettings.customProviders.size)
        assertEquals("Custom LLM", restoredSettings.customProviders[0].displayName)
        assertEquals(ReplyTone.CASUAL, restoredSettings.tone)
        assertEquals(3, restoredSettings.count)
    }

    @Test
    fun testStickyFallbackOrderReordering() {
        val defaultList = defaultBuiltInProviders()
        val p1 = defaultList[0]
        val p2 = defaultList[1]
        val p3 = defaultList[2]

        val initialOrder = listOf(p1.id, p2.id, p3.id)
        val settings = ReplySettings(
            preferredProvider = p1,
            fallbackOrder = initialOrder
        )

        // Simulate p1 failing and p2 succeeding -> sticky fallback reorders p2 to front
        val usedProvider = p2
        val newOrder = settings.fallbackOrder.toMutableList()
        newOrder.remove(usedProvider.id)
        newOrder.add(0, usedProvider.id)

        val updatedSettings = settings.copy(
            preferredProvider = usedProvider,
            fallbackOrder = newOrder
        )

        assertEquals(p2.id, updatedSettings.fallbackOrder[0])
        assertEquals(p1.id, updatedSettings.fallbackOrder[1])
        assertEquals(p3.id, updatedSettings.fallbackOrder[2])
        assertEquals(p2.id, updatedSettings.preferredProvider.id)

        val serialized = SettingsStorage.serializeSettings(updatedSettings)
        val deserialized = SettingsStorage.deserializeSettings(serialized)
        assertEquals(p2.id, deserialized.fallbackOrder[0])
        assertEquals(p2.id, deserialized.preferredProvider.id)
    }
}
