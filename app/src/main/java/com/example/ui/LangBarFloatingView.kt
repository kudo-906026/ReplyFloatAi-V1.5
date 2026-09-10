package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.state.AppStateManager

@Composable
fun LangBarFloatingView(
    context: Context,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onResetPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by AppStateManager.settings.collectAsState()
    val langState by AppStateManager.langTranslationState.collectAsState()

    var isSettingsOpen by remember { mutableStateOf(false) }

    // Floating card with independent opacity
    Card(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .testTag("lang_bar_container"),
        shape = RoundedCornerShape(settings.overlayCornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = settings.langBarOpacity.coerceIn(0.20f, 1.0f)
            )
        ),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Drag handle and top bar header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag Lang Bar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isSettingsOpen) Icons.Default.Tune else Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSettingsOpen) "LANG SETTINGS" else "LANG BAR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isSettingsOpen && langState.detectedLanguage.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = langState.detectedLanguage,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isSettingsOpen = !isSettingsOpen },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("lang_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = if (isSettingsOpen) Icons.Default.Check else Icons.Default.Settings,
                            contentDescription = if (isSettingsOpen) "Done" else "Lang Settings",
                            tint = if (isSettingsOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("lang_bar_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Lang Bar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Content area: Translation view OR Dedicated Lang settings panel
            if (isSettingsOpen) {
                LangBarSettingsPanel(
                    settings = settings,
                    onResetPosition = onResetPosition,
                    onCloseSettings = { isSettingsOpen = false }
                )
            } else {
                LangBarTranslationContent(
                    context = context,
                    state = langState,
                    textSizeSp = settings.overlayTextSizeSp
                )
            }
        }
    }
}

@Composable
private fun LangBarTranslationContent(
    context: Context,
    state: com.example.state.LangTranslationState,
    textSizeSp: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Original Text Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "ORIGINAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = state.originalText.ifBlank { "No message detected" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSizeSp.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("lang_bar_original_text")
                )
            }
        }

        // 2. Meaning (English) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "MEANING (ENGLISH)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (state.isTranslating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Translating meaning...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = (textSizeSp - 1).sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val meaning = state.englishMeaning.ifBlank { "Translation unavailable" }
                    val isUnavailable = meaning == com.example.ai.LangTranslationEngine.FALLBACK_UNAVAILABLE || state.isFailed

                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = textSizeSp.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isUnavailable) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("lang_bar_meaning_text")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUnavailable && state.originalText.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    AppStateManager.triggerLangTranslation(state.originalText)
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 11.sp)
                            }
                        }

                        if (!isUnavailable && meaning.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Meaning", meaning))
                                    Toast.makeText(context, "English meaning copied", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("lang_bar_copy_meaning_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Meaning", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LangBarSettingsPanel(
    settings: com.example.model.ReplySettings,
    onResetPosition: () -> Unit,
    onCloseSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Master Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lang Mode Active",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Auto-translate non-English questions",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.langModeEnabled,
                onCheckedChange = { AppStateManager.setLangModeEnabled(it) },
                modifier = Modifier.testTag("lang_bar_active_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        // 2. Opacity Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lang Bar Opacity",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(settings.langBarOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.langBarOpacity,
                onValueChange = { AppStateManager.setLangBarOpacity(it) },
                valueRange = 0.20f..1.0f,
                modifier = Modifier.testTag("lang_bar_opacity_slider")
            )
        }

        // 3. Position and Sample Test Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onResetPosition,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("lang_bar_reset_position_button"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Pos", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                    AppStateManager.testSampleHinglishTranslation()
                    onCloseSettings()
                },
                modifier = Modifier
                    .weight(1.3f)
                    .height(34.dp)
                    .testTag("lang_bar_test_sample_button"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Sample", fontSize = 11.sp)
            }
        }

        Text(
            text = "Replies remain in the main bar. Lang bar only displays parsed Original & Meaning.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
