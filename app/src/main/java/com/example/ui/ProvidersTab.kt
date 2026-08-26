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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
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
import com.example.ai.AiFallbackEngine
import com.example.model.AiModelTier
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.ReplySettings
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
    onUpdateApiKey: (AiProvider, String) -> Unit,
    onAddCustomProvider: (String, String, String, String) -> Unit,
    onDeleteCustomProvider: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var testingProviderId by remember { mutableStateOf<String?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var customApiKey by remember { mutableStateOf("") }

    val builtInProviders = listOf(
        AiProvider(
            id = "gemini-builtin",
            type = AiProviderType.GEMINI_BUILTIN,
            name = "gemini-builtin",
            displayName = "Gemini Flash (Built-in)",
            modelName = "gemini-2.5-flash",
            isBuiltIn = true,
            tier = AiModelTier.LIGHTWEIGHT
        ),
        AiProvider(
            id = "gemini-api",
            type = AiProviderType.GEMINI_API,
            name = "gemini-api",
            displayName = "Gemini Pro / Flash API",
            modelName = "gemini-2.5-flash",
            apiKey = settings.preferredProvider.takeIf { it.id == "gemini-api" }?.apiKey ?: "",
            tier = AiModelTier.PRO
        ),
        AiProvider(
            id = "openai",
            type = AiProviderType.OPENAI,
            name = "openai",
            displayName = "OpenAI GPT-4o Mini",
            modelName = "gpt-4o-mini",
            apiKey = settings.preferredProvider.takeIf { it.id == "openai" }?.apiKey ?: "",
            tier = AiModelTier.BALANCED
        ),
        AiProvider(
            id = "anthropic",
            type = AiProviderType.ANTHROPIC,
            name = "anthropic",
            displayName = "Anthropic Claude 3.5 Haiku",
            modelName = "claude-3-5-haiku-20241022",
            apiKey = settings.preferredProvider.takeIf { it.id == "anthropic" }?.apiKey ?: "",
            tier = AiModelTier.BALANCED
        ),
        AiProvider(
            id = "ollama",
            type = AiProviderType.OLLAMA_LOCAL,
            name = "ollama",
            displayName = "Ollama Local (Offline)",
            modelName = "llama3.2:1b",
            customEndpoint = "http://10.0.2.2:11434",
            tier = AiModelTier.LIGHTWEIGHT
        )
    )

    val allProviders = builtInProviders + settings.customProviders

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlPanelSectionHeader(
                    title = "AI PROVIDERS, REASONING MODELS & FALLBACK CHAIN",
                    icon = Icons.Default.Psychology,
                    accentColor = CrimsonPrimary
                )
                Text(
                    text = "Select primary inference engine. If API quota is reached or network drops, ReplyFloat immediately fails over to built-in local heuristics.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
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
                            Text("Save & Add Provider", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // List of AI Providers
        items(allProviders, key = { it.id }) { provider ->
            val isSelected = (activeProvider?.id == provider.id) || (settings.preferredProvider.id == provider.id)
            var keyInput by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
            val isTesting = testingProviderId == provider.id

            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_card_${provider.id}"),
                shapeRadius = 14.dp,
                isSelected = isSelected,
                activeColor = CrimsonPrimary,
                onClick = { onSelectPreferredProvider(provider) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) CrimsonPrimary else DarkSurfaceVariant)
                                    .border(1.dp, if (isSelected) CrimsonPrimary else DarkCardBorder, CircleShape)
                            )
                            Column {
                                Text(
                                    text = provider.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) CrimsonLight else TextWhite
                                )
                                Text(
                                    text = "Model: ${provider.modelName}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TechBlue
                                )
                            }
                        }

                        StatusBadge(
                            text = provider.tier.badge,
                            style = if (isSelected) StatusBadgeStyle.PURPLE_AI else StatusBadgeStyle.BLUE_INFO
                        )
                    }

                    // Optional API Key Input for Non-Built-in
                    if (!provider.isBuiltIn && provider.type != AiProviderType.OLLAMA_LOCAL) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = {
                                    keyInput = it
                                    onUpdateApiKey(provider, it)
                                },
                                placeholder = { Text("API Key...", color = TextMuted, fontSize = 11.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
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
                    }

                    // Test Connection Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                testingProviderId = provider.id
                                coroutineScope.launch {
                                    val result = AiFallbackEngine.testProviderConnection(provider)
                                    testingProviderId = null
                                    if (result.isSuccess) {
                                        Toast.makeText(context, result.getOrNull() ?: "Connection OK", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                                Text("Testing...", fontSize = 11.sp)
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
                }
            }
        }
    }
}
