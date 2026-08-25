package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AiProvider
import com.example.model.ApiCallLog
import com.example.model.ApiCallStatus
import com.example.model.DiagnosticItem
import com.example.model.DiagnosticStatus
import com.example.model.SystemHealthState
import com.example.state.AppStateManager
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean, compact in-app diagnostics badge for the main app UI header.
 * Shows a pulsing dot with system health state (Green / Yellow / Red).
 * Tapping it switches to the Diagnostics tab.
 */
@Composable
fun AppDiagnosticsBadge(
    healthState: SystemHealthState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (healthState.overallStatus == DiagnosticStatus.ERROR) 1.25f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (healthState.overallStatus == DiagnosticStatus.ERROR) 600 else 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val dotColor by animateColorAsState(
        targetValue = when (healthState.overallStatus) {
            DiagnosticStatus.HEALTHY -> Color(0xFF10B981)
            DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
            DiagnosticStatus.ERROR -> Color(0xFFEF4444)
        },
        label = "DotColor"
    )

    Surface(
        modifier = modifier
            .testTag("app_diagnostics_badge")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = dotColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = when (healthState.overallStatus) {
                    DiagnosticStatus.HEALTHY -> "System Healthy"
                    DiagnosticStatus.WARNING -> "${healthState.warningCount} Notice${if (healthState.warningCount > 1) "s" else ""}"
                    DiagnosticStatus.ERROR -> "${healthState.errorCount} Issue${if (healthState.errorCount > 1) "s" else ""}"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = dotColor
            )
        }
    }
}

/**
 * 4th Tab inside the Main App UI: "Diagnostics"
 * Displays full system health status (green/yellow/red), component breakdowns
 * (Accessibility Service, Overlay Permission, AI providers, Question Detection),
 * plain-language descriptions, technical error codes, suggested fixes,
 * quick-fix actionable buttons, and a real-time API Calls Audit Log.
 */
@Composable
fun DiagnosticsTab(
    healthState: SystemHealthState,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTestRequest: () -> Unit,
    onClearErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Components Health, 1: API Audit Log
    val totalApiCalls by AppStateManager.totalApiCallsCount.collectAsStateWithLifecycle()
    val providerCallCounts by AppStateManager.providerCallCounts.collectAsStateWithLifecycle()
    val apiCallLogs by AppStateManager.recentApiCallLogs.collectAsStateWithLifecycle()

    val statusColor = when (healthState.overallStatus) {
        DiagnosticStatus.HEALTHY -> Color(0xFF10B981)
        DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
        DiagnosticStatus.ERROR -> Color(0xFFEF4444)
    }

    LazyColumn(
        modifier = modifier
            .testTag("diagnostics_tab_view")
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Overall System Health Banner Card
        item {
            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_health_banner_card"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(statusColor.copy(alpha = 0.2f))
                                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (healthState.overallStatus) {
                                        DiagnosticStatus.HEALTHY -> Icons.Default.CheckCircle
                                        DiagnosticStatus.WARNING -> Icons.Default.Warning
                                        DiagnosticStatus.ERROR -> Icons.Default.Error
                                    },
                                    contentDescription = "System Health Status",
                                    tint = statusColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = when (healthState.overallStatus) {
                                        DiagnosticStatus.HEALTHY -> "All Systems Operational"
                                        DiagnosticStatus.WARNING -> "System Attention Needed"
                                        DiagnosticStatus.ERROR -> "Issues Detected"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = healthState.summaryText,
                                    fontSize = 12.sp,
                                    color = statusColor
                                )
                            }
                        }

                        // Live pulse pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = when (healthState.overallStatus) {
                                    DiagnosticStatus.HEALTHY -> "ACTIVE"
                                    DiagnosticStatus.WARNING -> "NOTICE"
                                    DiagnosticStatus.ERROR -> "ACTION REQ"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 4 Metric Counters Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricSummaryBadge(
                            label = "Errors",
                            count = healthState.errorCount,
                            color = CrimsonPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryBadge(
                            label = "Notices",
                            count = healthState.warningCount,
                            color = StatusOrange,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryBadge(
                            label = "Healthy",
                            count = healthState.healthyCount,
                            color = StatusGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryBadge(
                            label = "API Calls",
                            count = totalApiCalls,
                            color = StatusCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Global Diagnostics Quick Actions
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTestRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f).crimsonGlow(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Providers (1 Call)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onClearErrors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = CrimsonLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear Errors", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { AppStateManager.clearApiCallLogs() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = CrimsonLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Reset Audit", fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Section Switcher: Component Health vs. API Calls Audit Log
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSection = 0 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSection == 0) CrimsonPrimary.copy(alpha = 0.25f) else Color.Transparent,
                    border = if (selectedSection == 0) BorderStroke(0.8.dp, CrimsonPrimary.copy(alpha = 0.6f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = if (selectedSection == 0) Color.White else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Component Health (${healthState.items.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedSection == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSection == 0) Color.White else TextMuted
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSection = 1 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSection == 1) CrimsonPrimary.copy(alpha = 0.25f) else Color.Transparent,
                    border = if (selectedSection == 1) BorderStroke(0.8.dp, CrimsonPrimary.copy(alpha = 0.6f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = if (selectedSection == 1) Color.White else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "API Audit Log ($totalApiCalls)",
                            fontSize = 12.sp,
                            fontWeight = if (selectedSection == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSection == 1) Color.White else TextMuted
                        )
                    }
                }
            }
        }

        // 4. Section Content
        if (selectedSection == 0) {
            // Components List with live status, descriptions, error codes, and fixes
            items(healthState.items, key = { it.id }) { item ->
                DiagnosticItemCard(
                    item = item,
                    onFixAction = { actionType ->
                        when (actionType) {
                            "accessibility" -> onRequestAccessibilityPermission()
                            "overlay" -> onRequestOverlayPermission()
                            "settings" -> onNavigateToSettings()
                            "test" -> onTestRequest()
                        }
                    }
                )
            }
        } else {
            // API Calls Audit Log View
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StatusGreen.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, StatusGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Atomic Dedup Engine Active",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                            Text(
                                text = "Exactly 1 API call per detected question is guaranteed. No duplicate calls or concurrent leaks.",
                                fontSize = 10.5.sp,
                                color = StatusGreen.copy(alpha = 0.8f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            if (apiCallLogs.isEmpty()) {
                item {
                    ControlPanelCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No API calls recorded yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Trigger a question detection or tap 'Test Providers' to see the live telemetry log.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(apiCallLogs, key = { it.id }) { log ->
                    ApiCallLogCard(log = log)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Interactive diagnostics panel dialog for modal viewing inside the app.
 */
@Composable
fun DiagnosticsPanelDialog(
    healthState: SystemHealthState,
    onDismiss: () -> Unit,
    onClearErrors: () -> Unit,
    onTestRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val totalApiCalls by AppStateManager.totalApiCallsCount.collectAsStateWithLifecycle()
    val providerCallCounts by AppStateManager.providerCallCounts.collectAsStateWithLifecycle()
    val apiCallLogs by AppStateManager.recentApiCallLogs.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier
            .testTag("diagnostics_panel_dialog")
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = DarkBg,
        border = BorderStroke(
            1.2.dp,
            when (healthState.overallStatus) {
                DiagnosticStatus.HEALTHY -> StatusGreen.copy(alpha = 0.5f)
                DiagnosticStatus.WARNING -> StatusOrange.copy(alpha = 0.5f)
                DiagnosticStatus.ERROR -> CrimsonPrimary.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
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
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (healthState.overallStatus) {
                                    DiagnosticStatus.HEALTHY -> StatusGreen
                                    DiagnosticStatus.WARNING -> StatusOrange
                                    DiagnosticStatus.ERROR -> CrimsonPrimary
                                }
                            )
                    )
                    Column {
                        Text(
                            text = "SYSTEM HEALTH & DIAGNOSTICS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (selectedTab == 0) healthState.summaryText else "API Calls Audit: $totalApiCalls Total",
                            fontSize = 11.sp,
                            color = when (healthState.overallStatus) {
                                DiagnosticStatus.HEALTHY -> StatusGreen
                                DiagnosticStatus.WARNING -> StatusOrange
                                DiagnosticStatus.ERROR -> CrimsonLight
                            }
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Diagnostics",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tab Selector: System Health vs API Calls Audit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 0) CrimsonPrimary.copy(alpha = 0.25f) else Color.Transparent,
                    border = if (selectedTab == 0) BorderStroke(0.8.dp, CrimsonPrimary.copy(alpha = 0.5f)) else null
                ) {
                    Text(
                        text = "System Health",
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) Color.White else TextMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 1) CrimsonPrimary.copy(alpha = 0.25f) else Color.Transparent,
                    border = if (selectedTab == 1) BorderStroke(0.8.dp, CrimsonPrimary.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "API Calls Log",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (totalApiCalls > 0) CrimsonPrimary else Color.Gray.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = totalApiCalls.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // Overview Metric Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricSummaryBadge(
                        label = "Errors",
                        count = healthState.errorCount,
                        color = CrimsonPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Notices",
                        count = healthState.warningCount,
                        color = StatusOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Healthy",
                        count = healthState.healthyCount,
                        color = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = DarkCardBorder)

                // Scrollable Diagnostic Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(healthState.items, key = { it.id }) { item ->
                        DiagnosticItemCard(item = item)
                    }
                }

                HorizontalDivider(color = DarkCardBorder)

                // Bottom Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClearErrors,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = CrimsonLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear Errors", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onTestRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Providers", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                // API Calls Audit Tab Content
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricSummaryBadge(
                        label = "Total Calls",
                        count = totalApiCalls,
                        color = StatusCyan,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Gemini",
                        count = providerCallCounts[AiProvider.GEMINI] ?: 0,
                        color = Color(0xFF818CF8),
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Other",
                        count = (totalApiCalls - (providerCallCounts[AiProvider.GEMINI] ?: 0)).coerceAtLeast(0),
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = StatusGreen.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, StatusGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Strict Dedup: Exactly 1 API call per unique question enforced.",
                            fontSize = 10.sp,
                            color = StatusGreen
                        )
                    }
                }

                if (apiCallLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No API calls recorded yet.\nAsk a question or tap 'Test Providers' to start.",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(apiCallLogs, key = { it.id }) { log ->
                            ApiCallLogCard(log = log)
                        }
                    }
                }

                HorizontalDivider(color = DarkCardBorder)

                // Bottom Actions Row for API Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { AppStateManager.clearApiCallLogs() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = CrimsonLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Reset Counter & Logs", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onTestRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Providers (1 Call)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSummaryBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun DiagnosticItemCard(
    item: DiagnosticItem,
    onFixAction: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor = when (item.status) {
        DiagnosticStatus.HEALTHY -> StatusGreen
        DiagnosticStatus.WARNING -> StatusOrange
        DiagnosticStatus.ERROR -> CrimsonPrimary
    }

    val statusIcon = when (item.status) {
        DiagnosticStatus.HEALTHY -> Icons.Default.CheckCircle
        DiagnosticStatus.WARNING -> Icons.Default.Warning
        DiagnosticStatus.ERROR -> Icons.Default.Error
    }

    val statusLabel = when (item.status) {
        DiagnosticStatus.HEALTHY -> "ACTIVE / READY"
        DiagnosticStatus.WARNING -> "NOTICE"
        DiagnosticStatus.ERROR -> "ISSUE"
    }

    ControlPanelCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Component Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.componentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.2f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Plain language description
            Text(
                text = item.plainDescription,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 16.sp
            )

            // Technical Details / Exact Error code
            if (!item.technicalDetails.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(0.5.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CODE/STATUS:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor
                        )
                        Text(
                            text = item.technicalDetails,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Suggested Fix Callout
            if (!item.suggestedFix.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = StatusCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, StatusCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Suggested Fix",
                            tint = StatusCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = "SUGGESTED FIX:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusCyan
                            )
                            Text(
                                text = item.suggestedFix,
                                fontSize = 11.sp,
                                color = Color(0xFFE0F2FE),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Contextual Action Button if an action is available
            if (onFixAction != null && (item.status != DiagnosticStatus.HEALTHY || item.id.endsWith("_provider"))) {
                val (buttonText, actionType, buttonIcon) = when {
                    item.id == "accessibility_service" -> Triple("Open Accessibility Settings", "accessibility", Icons.Default.OpenInNew)
                    item.id == "overlay_permission" -> Triple("Grant Overlay Permission", "overlay", Icons.Default.OpenInNew)
                    item.id.endsWith("_provider") -> Triple("Configure Key in Settings", "settings", Icons.Default.Settings)
                    item.id == "question_detection" -> Triple("Test in Sandbox", "test", Icons.Default.Tune)
                    else -> Triple("Resolve Issue", "settings", Icons.Default.Build)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onFixAction(actionType) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusCyan
                        ),
                        border = BorderStroke(1.dp, StatusCyan.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = buttonText, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ApiCallLogCard(
    log: ApiCallLog,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val statusColor = when (log.status) {
        ApiCallStatus.SUCCESS -> StatusGreen
        ApiCallStatus.FAILED -> CrimsonPrimary
        ApiCallStatus.IN_FLIGHT -> StatusOrange
    }

    val statusText = when (log.status) {
        ApiCallStatus.SUCCESS -> if (log.repliesCount > 0) "200 OK (${log.repliesCount} replies)" else "200 OK (0 replies)"
        ApiCallStatus.FAILED -> "FAILED"
        ApiCallStatus.IN_FLIGHT -> "CALLING..."
    }

    ControlPanelCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Time, Provider & Model, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = StatusCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, StatusCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${log.provider.displayName} · ${log.model}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = StatusCyan,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (log.status == ApiCallStatus.IN_FLIGHT) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 1.2.dp,
                                color = statusColor
                            )
                        }
                        Text(
                            text = statusText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Question Text Snippet
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = DarkSurfaceVariant,
                border = BorderStroke(0.5.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Q:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonPrimary
                    )
                    Text(
                        text = log.question,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                }
            }

            // Latency / Error Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (log.durationMs > 0) {
                    Text(
                        text = "Latency: ${log.durationMs}ms",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (!log.error.isNullOrBlank()) {
                    Text(
                        text = log.error,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CrimsonLight,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
