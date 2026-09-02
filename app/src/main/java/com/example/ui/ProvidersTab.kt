package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import android.content.ClipData
import android.content.ClipboardManager
import com.example.ai.AiFallbackEngine
import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.DetectionMethod
import com.example.model.ReplySettings
import com.example.model.defaultBuiltInProviders
import com.example.state.AppStateManager
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun ProvidersTab(
    settings: ReplySettings,
    activeProvider: AiProvider?,
    onSelectPreferredProvider: (AiProvider) -> Unit,
    onMoveFallbackOrder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onSetPrimaryFallback: (String) -> Unit = {},
    onUpdateApiKey: (AiProvider, String) -> Unit,
    onUpdateModel: (AiProvider, String) -> Unit = { _, _ -> },
    onAddCustomProvider: (String, String, String, String) -> Unit,
    onDeleteCustomProvider: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var testingProviderId by remember { mutableStateOf<String?>(null) }
    var testResultsMap by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var customApiKey by remember { mutableStateOf("") }

    val baseBuiltIn = defaultBuiltInProviders()
    val allProvidersMap = (baseBuiltIn + settings.customProviders).associateBy { it.id }.toMutableMap()

    // Sync saved API keys into provider objects
    settings.providerApiKeys.forEach { (id, key) ->
        allProvidersMap[id]?.let { allProvidersMap[id] = it.copy(apiKey = key) }
    }
    // Sync saved model overrides into provider objects
    settings.providerModelOverrides.forEach { (id, model) ->
        allProvidersMap[id]?.let { allProvidersMap[id] = it.copy(modelName = model) }
    }
    if (settings.preferredProvider.apiKey.isNotBlank()) {
        allProvidersMap[settings.preferredProvider.id]?.let {
            allProvidersMap[settings.preferredProvider.id] = it.copy(apiKey = settings.preferredProvider.apiKey)
        }
    }
    if (settings.preferredProvider.modelName.isNotBlank()) {
        allProvidersMap[settings.preferredProvider.id]?.let {
            allProvidersMap[settings.preferredProvider.id] = it.copy(modelName = settings.preferredProvider.modelName)
        }
    }

    // Build the ordered list according to settings.fallbackOrder
    val orderedIds = if (settings.fallbackOrder.isNotEmpty()) {
        val list = settings.fallbackOrder.toMutableList()
        allProvidersMap.keys.forEach { if (!list.contains(it)) list.add(it) }
        list
    } else {
        listOf("openai", "gemini-api", "gemini-builtin", "anthropic", "groq")
    }

    val orderedProviders = orderedIds.mapNotNull { allProvidersMap[it] }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlPanelSectionHeader(
                    title = "PROVIDER FALLBACK ORDER & PRIORITY CHAIN",
                    icon = Icons.Default.Psychology,
                    accentColor = CrimsonPrimary
                )
                Text(
                    text = "ReplyFloat requests suggestions strictly following this fallback order. Position #1 is queried first; if it fails (e.g. missing key, network error, quota exceeded), it immediately fails over to Position #2 with failure reasons logged in Diagnostics.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Live Fallback Chain Visual Banner
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ACTIVE INFERENCE SEQUENCE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TechBlue
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        orderedProviders.take(4).forEachIndexed { idx, p ->
                            val isPrimary = idx == 0
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPrimary) CrimsonPrimary.copy(alpha = 0.25f) else DarkSurfaceVariant,
                                border = BorderStroke(1.dp, if (isPrimary) CrimsonPrimary else DarkCardBorder)
                            ) {
                                Text(
                                    text = "#${idx + 1} ${p.displayName.split(" ").first()}",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPrimary) CrimsonLight else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            if (idx < orderedProviders.take(4).size - 1) {
                                Text("➔", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // Add Custom Model Button
        item {
            Button(
                onClick = { showAddCustomDialog = !showAddCustomDialog },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showAddCustomDialog) CrimsonPrimary else DarkSurfaceVariant,
                    contentColor = TextWhite
                ),
                border = BorderStroke(1.dp, if (showAddCustomDialog) CrimsonPrimary else DarkCardBorder)
            ) {
                Icon(
                    imageVector = if (showAddCustomDialog) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showAddCustomDialog) "Close Custom Setup" else "Register Custom REST / OpenAI Compatible Model",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Add Custom Model Form
        if (showAddCustomDialog) {
            item {
                ControlPanelCard(
                    modifier = Modifier.fillMaxWidth(),
                    shapeRadius = 14.dp,
                    isSelected = true,
                    activeColor = CrimsonPrimary
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add Custom OpenAI-Compatible LLM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = CrimsonLight
                        )

                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Provider Name (e.g. Together AI, Groq)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            label = { Text("Model ID (e.g. meta-llama/Llama-3-8b-chat-hf)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("Endpoint URL (e.g. https://api.groq.com/openai/v1/chat/completions)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = { customApiKey = it },
                            label = { Text("API Key (Optional / Injected)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        Button(
                            onClick = {
                                if (customName.isNotBlank() && customModel.isNotBlank()) {
                                    onAddCustomProvider(customName, customModel, customEndpoint, customApiKey)
                                    customName = ""
                                    customModel = ""
                                    customEndpoint = ""
                                    customApiKey = ""
                                    showAddCustomDialog = false
                                    Toast.makeText(context, "Registered custom AI provider", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save & Add to Chain", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // List of AI Providers in exact fallback order
        itemsIndexed(orderedProviders, key = { _, provider -> provider.id }) { index, provider ->
            val positionNum = index + 1
            val isPrimary = positionNum == 1
            val isUsedNow = activeProvider?.id == provider.id
            val currentApiKey = settings.providerApiKeys[provider.id] ?: provider.apiKey
            var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
            val isTesting = testingProviderId == provider.id

            val hasKey = currentApiKey.isNotBlank() || provider.isBuiltIn

            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_card_${provider.id}"),
                shapeRadius = 14.dp,
                isSelected = isPrimary,
                activeColor = if (isPrimary) CrimsonPrimary else DarkCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row: Position Rank + Provider Name + Move Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Position Rank Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPrimary) CrimsonPrimary else DarkSurfaceVariant,
                                border = BorderStroke(1.dp, if (isPrimary) CrimsonPrimary else DarkCardBorder)
                            ) {
                                Text(
                                    text = if (isPrimary) "#1 PRIMARY" else "#$positionNum FALLBACK",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrimary) TextWhite else TechBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = provider.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isPrimary) CrimsonLight else TextWhite
                                )
                                Text(
                                    text = "Model: ${provider.modelName}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TechBlue
                                )
                            }
                        }

                        // Move Up / Down Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (index > 0) onMoveFallbackOrder(index, index - 1)
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) TextWhite else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < orderedProviders.size - 1) onMoveFallbackOrder(index, index + 1)
                                },
                                enabled = index < orderedProviders.size - 1,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = if (index < orderedProviders.size - 1) TextWhite else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Model Selector & Preset Chips
                    if (!provider.isBuiltIn) {
                        var isEditingModel by remember(provider.id) { mutableStateOf(false) }
                        var customModelInput by remember(provider.id, provider.modelName) { mutableStateOf(provider.modelName) }

                        val presetModels = when (provider.type) {
                            AiProviderType.GROQ -> listOf("openai/gpt-oss-120b", "groq/compound", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
                            AiProviderType.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
                            AiProviderType.GEMINI_API -> listOf("gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-1.5-flash")
                            AiProviderType.ANTHROPIC -> listOf("claude-3-5-haiku-20241022", "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307")
                            else -> emptyList()
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Model: ${provider.modelName}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TechBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = { isEditingModel = !isEditingModel },
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Model",
                                        tint = TextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEditingModel) "Done" else "Custom Model", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            if (presetModels.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetModels.forEach { modelPreset ->
                                        val isSelected = provider.modelName == modelPreset
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) TechBlue.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                            border = BorderStroke(1.dp, if (isSelected) TechBlue else DarkCardBorder),
                                            modifier = Modifier.clickable {
                                                onUpdateModel(provider, modelPreset)
                                                Toast.makeText(context, "Model switched to: $modelPreset", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = modelPreset,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) TechBlue else TextMuted,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isEditingModel) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customModelInput,
                                        onValueChange = { customModelInput = it },
                                        placeholder = { Text("Enter custom model ID...", fontSize = 10.5.sp, color = TextMuted) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TechBlue,
                                            unfocusedBorderColor = DarkCardBorder,
                                            focusedTextColor = TextWhite,
                                            unfocusedTextColor = TextWhite,
                                            focusedContainerColor = DarkSurfaceVariant,
                                            unfocusedContainerColor = DarkSurfaceVariant
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (customModelInput.isNotBlank()) {
                                                onUpdateModel(provider, customModelInput.trim())
                                                isEditingModel = false
                                                Toast.makeText(context, "Model updated: ${customModelInput.trim()}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Text("Set", fontSize = 11.sp, color = TextWhite)
                                    }
                                }
                            }
                        }
                    }

                    // Key Status Notice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (provider.isBuiltIn) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TechGreen, modifier = Modifier.size(13.dp))
                                Text("Built-in Local Heuristics (Always available offline)", fontSize = 11.sp, color = TechGreen)
                            }
                        } else if (hasKey) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TechGreen, modifier = Modifier.size(13.dp))
                                Text("API Key configured", fontSize = 11.sp, color = TechGreen)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(13.dp))
                                Text("No API key entered (Will auto-skip to next fallback)", fontSize = 11.sp, color = AccentYellow)
                            }
                        }

                        if (!isPrimary) {
                            TextButton(
                                onClick = { onSetPrimaryFallback(provider.id) },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Set as #1 Primary", fontSize = 11.sp, color = CrimsonLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // API Key Input for Non-Built-in
                    if (!provider.isBuiltIn) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = {
                                keyInput = it
                                onUpdateApiKey(provider, it)
                            },
                            placeholder = { Text("Enter ${provider.displayName} API Key...", color = TextMuted, fontSize = 11.5.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )
                    }

                    // Bottom Action Bar: Test Connection & Delete Custom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                testingProviderId = provider.id
                                coroutineScope.launch {
                                    val providerWithKey = provider.copy(apiKey = keyInput)
                                    onUpdateApiKey(providerWithKey, keyInput)
                                    val result = AiFallbackEngine.testProviderConnection(
                                        provider = providerWithKey,
                                        settings = settings,
                                        onLog = { src, raw, res, cat, rsn, lat ->
                                            AppStateManager.addDiagnosticLog(
                                                source = src,
                                                rawText = raw,
                                                result = res,
                                                category = cat,
                                                reason = rsn,
                                                detectionMethod = DetectionMethod.ACCESSIBILITY,
                                                latencyMs = lat
                                            )
                                        }
                                    )
                                    testingProviderId = null
                                    if (result.isSuccess) {
                                        val msg = result.getOrNull() ?: "Verified OK"
                                        testResultsMap = testResultsMap + (provider.id to (true to msg))
                                        Toast.makeText(context, "Success: Verified ${provider.displayName}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "Unknown error"
                                        testResultsMap = testResultsMap + (provider.id to (false to err))
                                        Toast.makeText(context, "Connection Error: $err", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TechBlue),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TechBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing POST generation...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (provider.isCustom) {
                            IconButton(
                                onClick = { onDeleteCustomProvider(provider.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonLight, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Rich Verified / Error Status Box with Raw Response & Copy
                    val testResult = testResultsMap[provider.id]
                    if (testResult != null) {
                        val isOk = testResult.first
                        val resultText = testResult.second
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOk) TechGreen.copy(alpha = 0.10f) else CrimsonPrimary.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, if (isOk) TechGreen.copy(alpha = 0.45f) else CrimsonPrimary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isOk) TechGreen else CrimsonLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isOk) "Real Generation Verified (Exact POST & Model)" else "Generation Check Failed",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOk) TechGreen else CrimsonLight
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("API Response", resultText))
                                            Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Response",
                                            tint = TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkSurfaceVariant,
                                    border = BorderStroke(0.5.dp, DarkCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = resultText,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isOk) TextPrimary else CrimsonLight.copy(alpha = 0.95f),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
