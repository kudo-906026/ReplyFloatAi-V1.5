package com.example.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoPurgeTimerOption
import com.example.model.ReplySettings
import com.example.model.ReplyTone
import com.example.model.ResponseLengthPreset
import com.example.model.UnderstandingSummaryLength
import com.example.state.AppStateManager
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ContentCopy
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
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite

@Composable
fun RepliesTab(
    settings: ReplySettings,
    onUpdateTone: (ReplyTone) -> Unit,
    onUpdateReplyCount: (Int) -> Unit,
    onSetUnderstandingMode: (Boolean) -> Unit,
    onSetUnderstandingSummaryLength: (UnderstandingSummaryLength) -> Unit,
    onSetAutoGenerateReplies: (Boolean) -> Unit,
    onSetDetectQuestionsOnly: (Boolean) -> Unit,
    onSetPrefetchOnAppFocus: (Boolean) -> Unit,
    onSetAutoCopySingleReply: (Boolean) -> Unit,
    onSetExpandableReplies: (Boolean) -> Unit,
    onSetResponseLengthPreset: (ResponseLengthPreset) -> Unit,
    onSetCustomCharLimit: (Int) -> Unit,
    onSetReplyAutoDeleteMinutes: (Int) -> Unit = { AppStateManager.setReplyAutoDeleteMinutes(it) },
    onSetHistoryPurgeMinutes: (Int) -> Unit = { AppStateManager.setHistoryPurgeMinutes(it) },
    onSetCacheRetentionMinutes: (Int) -> Unit,
    onSetHistoryRetentionDays: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Generation Parameters & AI Persona Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "GENERATION PARAMETERS & AI PERSONA",
                        icon = Icons.Default.Psychology,
                        accentColor = CrimsonPrimary
                    )

                    // Reply Count Option (1 - 3)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Candidate Suggestions Count", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                            MonospaceValue(text = "${settings.count} options", color = TechBlue)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 2, 3).forEach { countOption ->
                                val isSelected = settings.count == countOption
                                ControlPanelCard(
                                    modifier = Modifier.weight(1f),
                                    isSelected = isSelected,
                                    activeColor = CrimsonPrimary,
                                    shapeRadius = 6.dp,
                                    onClick = { onUpdateReplyCount(countOption) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$countOption Suggestion${if (countOption > 1) "s" else ""}",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) CrimsonLight else TextWhite
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // Persona / Tone Selectors
                    Text(
                        text = "Conversational Persona & Tone Hint:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = TextWhite
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReplyTone.entries.forEach { tone ->
                            val isSelected = settings.tone == tone
                            ControlPanelCard(
                                modifier = Modifier.fillMaxWidth(),
                                isSelected = isSelected,
                                activeColor = CrimsonPrimary,
                                onClick = { onUpdateTone(tone) },
                                shapeRadius = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CrimsonPrimary else DarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) CrimsonPrimary else DarkCardBorder, CircleShape)
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = tone.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) CrimsonLight else TextWhite
                                        )
                                        Text(
                                            text = tone.description,
                                            fontSize = 10.5.sp,
                                            color = TextSecondary,
                                            lineHeight = 14.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) CrimsonPrimary.copy(alpha = 0.15f) else DarkSurfaceVariant.copy(alpha = 0.6f),
                                            border = BorderStroke(0.5.dp, if (isSelected) CrimsonPrimary.copy(alpha = 0.4f) else DarkCardBorder)
                                        ) {
                                            Text(
                                                text = "Example: \"${tone.exampleReply}\"",
                                                fontSize = 9.5.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = if (isSelected) TextWhite.copy(alpha = 0.9f) else TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                lineHeight = 13.sp
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

        // 2. Understanding Mode & Summary Synthesis Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "UNDERSTANDING MODE & SYNTHESIS",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = CrimsonPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sender Intent & Understanding Mode", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                            Text("Displays contextual meaning preview alongside reply options", fontSize = 10.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.understandingMode,
                            onCheckedChange = onSetUnderstandingMode,
                            activeColor = AccentBlue
                        )
                    }

                    if (settings.understandingMode) {
                        Text(
                            text = "Intent Summary Granularity:",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            UnderstandingSummaryLength.entries.forEach { summaryLength ->
                                val isSelected = settings.understandingSummaryLength == summaryLength
                                ControlPanelCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    isSelected = isSelected,
                                    activeColor = CrimsonPrimary,
                                    onClick = { onSetUnderstandingSummaryLength(summaryLength) },
                                    shapeRadius = 6.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = summaryLength.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) CrimsonLight else TextWhite
                                        )
                                        Text(
                                            text = summaryLength.description,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Automation, Heuristics & Filtering Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "AUTOMATION, HEURISTICS & DEDUPLICATION",
                        icon = Icons.Default.FilterAlt,
                        accentColor = CrimsonPrimary
                    )

                    // Auto-Generate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Generation on Detection", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                            Text("Instantly call LLM without requiring manual overlay taps", fontSize = 10.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.autoGenerate,
                            onCheckedChange = onSetAutoGenerateReplies,
                            activeColor = AccentBlue
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // Detect Questions Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Question Filtering", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                            Text("Trigger on interrogative marks (?) combined with question words", fontSize = 10.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.detectQuestionsOnly,
                            onCheckedChange = onSetDetectQuestionsOnly,
                            activeColor = AccentBlue
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    HorizontalDivider(color = DarkCardBorder)

                    // Reply Auto-Delete on Copy Timer (1m up to 10m)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reply Auto-Delete on Copy Timer", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                                Text("Auto-clears copied replies from floating bar after delay", fontSize = 10.5.sp, color = TextSecondary)
                            }
                            MonospaceValue(
                                text = if (settings.replyAutoDeleteMinutes == 0) "Instant" else "${settings.replyAutoDeleteMinutes} min",
                                color = CrimsonLight
                            )
                        }

                        // 1m..10m Selectable Range Pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = settings.replyAutoDeleteMinutes == 0
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSetReplyAutoDeleteMinutes(0) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CrimsonPrimary else DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) CrimsonLight else DarkCardBorder)
                                ) {
                                    Text(
                                        text = "Instant",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextWhite else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            items((1..10).toList()) { min ->
                                val isSelected = settings.replyAutoDeleteMinutes == min
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSetReplyAutoDeleteMinutes(min) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CrimsonPrimary else DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) CrimsonLight else DarkCardBorder)
                                ) {
                                    Text(
                                        text = "${min}m",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextWhite else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = settings.replyAutoDeleteMinutes.toFloat(),
                            onValueChange = { onSetReplyAutoDeleteMinutes(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonPrimary,
                                activeTrackColor = CrimsonPrimary,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // History Storage Purge Timer (1m up to 10m)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("History Storage Purge Timer", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                                Text("Auto-purges captured question logs and duplicate cache", fontSize = 10.5.sp, color = TextSecondary)
                            }
                            MonospaceValue(
                                text = "${settings.historyPurgeMinutes} min",
                                color = TechBlue
                            )
                        }

                        // 1m..10m Selectable Range Pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items((1..10).toList()) { min ->
                                val isSelected = settings.historyPurgeMinutes == min
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSetHistoryPurgeMinutes(min) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) TechBlue else DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) AccentBlue else DarkCardBorder)
                                ) {
                                    Text(
                                        text = "${min}m",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextWhite else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = settings.historyPurgeMinutes.toFloat(),
                            onValueChange = { onSetHistoryPurgeMinutes(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = TechBlue,
                                activeTrackColor = TechBlue,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // Prefetch on Focus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefetch on Foreground Launch", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextWhite)
                            Text("Warm up LLM context when whitelisted app opens", fontSize = 10.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.prefetchOnAppFocus,
                            onCheckedChange = onSetPrefetchOnAppFocus,
                            activeColor = AccentBlue
                        )
                    }
                }
            }
        }

        // 4. Length Limits & History Retention Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "LENGTH LIMITS & HISTORY RETENTION",
                        icon = Icons.Default.ShortText,
                        accentColor = CrimsonPrimary
                    )

                    // Length Presets
                    Text("Response Length Preset:", fontSize = 11.5.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ResponseLengthPreset.entries.forEach { preset ->
                            val isSelected = settings.responseLengthPreset == preset
                            ControlPanelCard(
                                modifier = Modifier.weight(1f),
                                isSelected = isSelected,
                                activeColor = CrimsonPrimary,
                                onClick = { onSetResponseLengthPreset(preset) },
                                shapeRadius = 6.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CrimsonLight else TextWhite
                                    )
                                }
                            }
                        }
                    }

                    // Custom Character Limit Slider
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Maximum Character Ceiling", fontSize = 11.5.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.customCharLimit} chars", color = TechBlue)
                        }
                        Slider(
                            value = settings.customCharLimit.toFloat(),
                            onValueChange = { onSetCustomCharLimit(it.toInt()) },
                            valueRange = 30f..300f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // History Retention Slider
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recent Interrogation Log Retention", fontSize = 11.5.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.historyRetentionDays} days", color = TechBlue)
                        }
                        Slider(
                            value = settings.historyRetentionDays.toFloat(),
                            onValueChange = { onSetHistoryRetentionDays(it.toInt()) },
                            valueRange = 1f..30f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}
