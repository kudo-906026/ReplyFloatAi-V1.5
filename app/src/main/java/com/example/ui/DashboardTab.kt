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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.AiProvider
import com.example.model.ReplySettings
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.CrimsonDark
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

@Composable
fun DashboardTab(
    isOverlayRunning: Boolean,
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    settings: ReplySettings,
    activeProvider: AiProvider?,
    onRefreshPermissions: () -> Unit = {},
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Status Card
        item {
            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_hero_card"),
                shapeRadius = 14.dp,
                isSelected = isOverlayRunning,
                activeColor = AccentGreen
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isOverlayRunning) AccentGreen else CrimsonPrimary)
                            )
                            Text(
                                text = if (isOverlayRunning) "FLOATING OVERLAY RUNNING" else "ASSISTANT OFFLINE",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = if (isOverlayRunning) AccentGreen else CrimsonLight
                            )
                        }

                        StatusBadge(
                            text = if (isOverlayRunning) "DAEMON RUNNING" else "STANDBY",
                            style = if (isOverlayRunning) StatusBadgeStyle.GREEN_LIVE else StatusBadgeStyle.RED_WARNING
                        )
                    }

                    Text(
                        text = if (isOverlayRunning) {
                            "ReplyFloat is actively scanning on-screen questions in ${settings.appsWhitelist.count { it.isEnabled }} whitelisted apps. Tap the floating pill to view suggestions."
                        } else {
                            "Tap 'Start Floating Assistant' below to launch the overlay. Ensure System Overlay and Accessibility permissions are granted."
                        },
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    // Master Start / Stop Action Button
                    Button(
                        onClick = {
                            if (isOverlayRunning) onStopAssistant() else onStartAssistant()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("master_assistant_toggle_button")
                            .softGlow(
                                color = if (isOverlayRunning) CrimsonPrimary.copy(alpha = 0.4f) else AccentGreen.copy(alpha = 0.4f),
                                radius = 8.dp,
                                shapeRadius = 10.dp
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) CrimsonPrimary else AccentGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOverlayRunning) "Stop Floating Overlay" else "Start Floating Assistant",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Diagnostic Permission Gates
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlPanelSectionHeader(
                            title = "PERMISSION GATES & ACCESSIBILITY STATUS",
                            icon = Icons.Default.Security,
                            accentColor = CrimsonPrimary
                        )

                        androidx.compose.material3.TextButton(
                            onClick = onRefreshPermissions,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Refresh", fontSize = 11.sp, color = TechBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Gate 1: System Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (hasOverlayPermission) AccentGreen else AccentYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "System Overlay Permission",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = TextWhite
                                )
                                Text(
                                    text = if (hasOverlayPermission) "Granted (SYSTEM_ALERT_WINDOW)" else "Required to draw floating pill",
                                    fontSize = 10.5.sp,
                                    color = if (hasOverlayPermission) TechGreen else AccentYellow
                                )
                            }
                        }

                        if (!hasOverlayPermission) {
                            OutlinedButton(
                                onClick = onRequestOverlayPermission,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLight),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            StatusBadge(text = "ENABLED", style = StatusBadgeStyle.GREEN_LIVE)
                        }
                    }

                    // Gate 2: Accessibility Service
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) AccentGreen else AccentYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "On-Screen Question Detector",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = TextWhite
                                )
                                Text(
                                    text = if (isAccessibilityEnabled) "Active background listener" else "Required to detect chat bubbles",
                                    fontSize = 10.5.sp,
                                    color = if (isAccessibilityEnabled) TechGreen else AccentYellow
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            OutlinedButton(
                                onClick = onRequestAccessibilityPermission,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLight),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            StatusBadge(text = "RUNNING", style = StatusBadgeStyle.GREEN_LIVE)
                        }
                    }
                }
            }
        }

        // Live Active Profile & Configuration Summary
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
                        title = "CURRENT AI PROFILE & ACTIVE ARCHITECTURE",
                        icon = Icons.Default.Tune,
                        accentColor = CrimsonPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Active Model", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = activeProvider?.displayName ?: settings.preferredProvider.displayName,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Reasoning Tone", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = settings.tone.label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonLight
                            )
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Overlay Style", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = settings.overlayBarStyle.title,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechBlue
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Whitelisted Apps", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = "${settings.appsWhitelist.count { it.isEnabled }} Enabled",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
