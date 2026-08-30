package com.example.ui

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
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OverlayBarStyle
import com.example.model.OverlayInteractionMode
import com.example.model.ReplySettings
import com.example.model.SavedOverlayPosition
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
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
fun OverlayTab(
    settings: ReplySettings,
    onSetContinuousAnalysis: (Boolean) -> Unit,
    onSetRealTimeNodeTracking: (Boolean) -> Unit,
    onSetSmartDebounceMs: (Int) -> Unit,
    onSetOcrFallbackEnabled: (Boolean) -> Unit = {},
    onSetOcrDebounceMs: (Int) -> Unit = {},
    onSetOverlayBarStyle: (OverlayBarStyle) -> Unit,
    onSetOverlayInteractionMode: (OverlayInteractionMode) -> Unit,
    onSetAutoHideEnabled: (Boolean) -> Unit,
    onSetAutoHideDelaySec: (Int) -> Unit,
    onSetScreenIdleTimeoutSec: (Int) -> Unit,
    onSetOverlayOpacity: (Float) -> Unit,
    onSetOverlayCornerRadius: (Int) -> Unit,
    onSetOverlayTextSizeSp: (Int) -> Unit,
    onDeleteSavedPosition: (String) -> Unit,
    onClearAllSavedPositions: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Continuous Screen Analysis Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "CONTINUOUS SCREEN ANALYSIS",
                        icon = Icons.Default.ScreenSearchDesktop,
                        accentColor = CrimsonPrimary
                    )

                    // Main Analysis Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous On-Screen Scanning", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextWhite)
                            Text("Real-time node detection for questions and conversational prompts", fontSize = 11.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.continuousScreenAnalysis,
                            onCheckedChange = onSetContinuousAnalysis,
                            activeColor = AccentBlue
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // Real-Time Node Tracking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Real-Time Viewport Tracking", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextWhite)
                            Text("Track dynamic scroll events and input focused nodes", fontSize = 11.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.realTimeNodeTracking,
                            onCheckedChange = onSetRealTimeNodeTracking,
                            activeColor = AccentBlue
                        )
                    }

                    // Smart Debounce Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Smart Event Debounce", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.smartDebounceMs} ms", color = TechBlue)
                        }
                        Slider(
                            value = settings.smartDebounceMs.toFloat(),
                            onValueChange = { onSetSmartDebounceMs(it.toInt()) },
                            valueRange = 100f..1000f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonPrimary,
                                activeTrackColor = CrimsonPrimary,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // 2. On-Device OCR Fallback Engine (Google ML Kit)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlPanelSectionHeader(
                            title = "ON-DEVICE OCR FALLBACK (ML KIT)",
                            icon = Icons.Default.CameraAlt,
                            accentColor = TechGreen
                        )
                        StatusBadge(text = "ON-DEVICE • FAST", style = StatusBadgeStyle.GREEN_LIVE)
                    }

                    Text(
                        text = "Activates Google ML Kit on-device text recognition ONLY when accessibility scanning returns 0 readable text nodes (e.g. Flutter custom canvas, games, or unreadable WebViews). Runs 100% on background threads with zero UI freezing.",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )

                    // OCR Fallback Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable On-Device ML Kit OCR Fallback", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextWhite)
                            Text("Primary scan remains instant accessibility node traversal everywhere", fontSize = 11.sp, color = TextMuted)
                        }
                        ControlPanelSwitch(
                            checked = settings.enableOcrFallback,
                            onCheckedChange = onSetOcrFallbackEnabled,
                            activeColor = TechGreen
                        )
                    }

                    // OCR Debounce Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("OCR Background Scan Debounce", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.ocrDebounceMs} ms", color = TechGreen)
                        }
                        Slider(
                            value = settings.ocrDebounceMs.toFloat(),
                            onValueChange = { onSetOcrDebounceMs(it.toInt()) },
                            valueRange = 500f..3000f,
                            steps = 25,
                            colors = SliderDefaults.colors(
                                thumbColor = TechGreen,
                                activeTrackColor = TechGreen,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // 2. Overlay Display Architecture Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "OVERLAY DISPLAY ARCHITECTURE",
                        icon = Icons.Default.Layers,
                        accentColor = CrimsonPrimary
                    )

                    Text(
                        text = "Select floating window architecture and anchoring presentation:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayBarStyle.entries.forEach { style ->
                            val isSelected = settings.overlayBarStyle == style
                            ControlPanelCard(
                                modifier = Modifier.fillMaxWidth(),
                                isSelected = isSelected,
                                activeColor = CrimsonPrimary,
                                onClick = { onSetOverlayBarStyle(style) },
                                shapeRadius = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CrimsonPrimary else DarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) CrimsonPrimary else DarkCardBorder, CircleShape)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = style.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) CrimsonLight else TextWhite
                                        )
                                        Text(
                                            text = style.description,
                                            fontSize = 11.sp,
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

        // 3. Auto-Hide & Screen Timeout Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "AUTO-HIDE & SCREEN TIMEOUT",
                        icon = Icons.Default.AvTimer,
                        accentColor = CrimsonPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Hide on Inactivity", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextWhite)
                            Text("Fade overlay when no conversational interaction occurs", fontSize = 11.5.sp, color = TextSecondary)
                        }
                        ControlPanelSwitch(
                            checked = settings.autoHideEnabled,
                            onCheckedChange = onSetAutoHideEnabled,
                            activeColor = AccentBlue
                        )
                    }

                    if (settings.autoHideEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Auto-Hide Inactivity Delay", fontSize = 12.sp, color = TextSecondary)
                                MonospaceValue(text = "${settings.autoHideDelaySec} sec", color = TechBlue)
                            }
                            Slider(
                                value = settings.autoHideDelaySec.toFloat(),
                                onValueChange = { onSetAutoHideDelaySec(it.toInt()) },
                                valueRange = 5f..60f,
                                steps = 10,
                                colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                            )
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Screen Idle Dismiss Timeout", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.screenIdleTimeoutSec} sec", color = TechBlue)
                        }
                        Slider(
                            value = settings.screenIdleTimeoutSec.toFloat(),
                            onValueChange = { onSetScreenIdleTimeoutSec(it.toInt()) },
                            valueRange = 10f..120f,
                            steps = 10,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }
                }
            }
        }

        // 4. Per-Application Saved Positions Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlPanelSectionHeader(
                            title = "PER-APPLICATION SAVED POSITIONS",
                            icon = Icons.Default.PinDrop,
                            accentColor = CrimsonPrimary
                        )

                        if (settings.savedPositions.isNotEmpty()) {
                            TextButton(onClick = onClearAllSavedPositions) {
                                Text("Reset All", fontSize = 11.sp, color = CrimsonLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Pinned overlay coordinates restored automatically when target app enters foreground:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    if (settings.savedPositions.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Text(
                                text = "No per-app positions stored. Drag overlay inside any app to record coordinates.",
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            settings.savedPositions.forEach { pos ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(pos.appName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextWhite)
                                        Text(
                                            text = "${pos.packageName} [X=${pos.x}dp, Y=${pos.y}dp]",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = TechBlue
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteSavedPosition(pos.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete position",
                                            tint = CrimsonLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Overlay Interaction Modes Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "OVERLAY INTERACTION MODES",
                        icon = Icons.Default.TouchApp,
                        accentColor = CrimsonPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayInteractionMode.entries.forEach { mode ->
                            val isSelected = settings.overlayInteractionMode == mode
                            ControlPanelCard(
                                modifier = Modifier.fillMaxWidth(),
                                isSelected = isSelected,
                                activeColor = CrimsonPrimary,
                                onClick = { onSetOverlayInteractionMode(mode) },
                                shapeRadius = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CrimsonPrimary else DarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) CrimsonPrimary else DarkCardBorder, CircleShape)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = mode.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) CrimsonLight else TextWhite
                                        )
                                        Text(
                                            text = mode.description,
                                            fontSize = 11.sp,
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

        // 6. Dimensions, Transparency & Typography Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "DIMENSIONS, TRANSPARENCY & TYPOGRAPHY",
                        icon = Icons.Default.FormatSize,
                        accentColor = CrimsonPrimary
                    )

                    // Opacity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val opacityPercent = (settings.overlayOpacity * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Surface Opacity", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "$opacityPercent%", color = TechBlue)
                        }
                        Slider(
                            value = settings.overlayOpacity,
                            onValueChange = { onSetOverlayOpacity(it) },
                            valueRange = 0.50f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }

                    // Corner Radius Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pill Corner Radius", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.overlayCornerRadius} dp", color = TechBlue)
                        }
                        Slider(
                            value = settings.overlayCornerRadius.toFloat(),
                            onValueChange = { onSetOverlayCornerRadius(it.toInt()) },
                            valueRange = 8f..28f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }

                    // Typography Size Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reply Preview Text Size", fontSize = 12.sp, color = TextSecondary)
                            MonospaceValue(text = "${settings.overlayTextSizeSp} sp", color = TechBlue)
                        }
                        Slider(
                            value = settings.overlayTextSizeSp.toFloat(),
                            onValueChange = { onSetOverlayTextSizeSp(it.toInt()) },
                            valueRange = 11f..18f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary, inactiveTrackColor = DarkSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}
