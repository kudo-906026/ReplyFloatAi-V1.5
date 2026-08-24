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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact diagnostics indicator dot/badge shown in the corner of the screen.
 * Green: All systems normal
 * Yellow: Warning / Optional unconfigured item
 * Red: Issue / Failure in progress
 */
@Composable
fun FloatingDiagnosticsDot(
    healthState: SystemHealthState,
    onClick: () -> Unit,
    onDragDelta: (Float, Float) -> Unit = { _, _ -> },
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
            DiagnosticStatus.HEALTHY -> Color(0xFF10B981) // Green
            DiagnosticStatus.WARNING -> Color(0xFFF59E0B) // Amber
            DiagnosticStatus.ERROR -> Color(0xFFEF4444)   // Red
        },
        label = "DotColor"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (healthState.overallStatus) {
            DiagnosticStatus.HEALTHY -> Color(0xE60D1F17)
            DiagnosticStatus.WARNING -> Color(0xE6261C08)
            DiagnosticStatus.ERROR -> Color(0xE6260B0B)
        },
        label = "BgColor"
    )

    Surface(
        modifier = modifier
            .testTag("floating_diagnostics_dot")
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.2.dp, dotColor.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulsing status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // Health Status text / Badge
            Text(
                text = when (healthState.overallStatus) {
                    DiagnosticStatus.HEALTHY -> "System OK"
                    DiagnosticStatus.WARNING -> "${healthState.warningCount} Notice"
                    DiagnosticStatus.ERROR -> "${healthState.errorCount} Issue${if (healthState.errorCount > 1) "s" else ""}"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )

            // Diagnostic Tool Icon
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "Diagnostics Health",
                tint = dotColor.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Compact, interactive diagnostics panel dialog showing live system health,
 * individual component issues, actual error codes, suggested fixes,
 * and a real-time API call counter and audit log.
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
        color = Color(0xFF140D0D),
        border = BorderStroke(
            1.2.dp,
            when (healthState.overallStatus) {
                DiagnosticStatus.HEALTHY -> Color(0xFF10B981).copy(alpha = 0.4f)
                DiagnosticStatus.WARNING -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                DiagnosticStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.6f)
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
                                    DiagnosticStatus.HEALTHY -> Color(0xFF10B981)
                                    DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
                                    DiagnosticStatus.ERROR -> Color(0xFFEF4444)
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
                                DiagnosticStatus.HEALTHY -> Color(0xFF34D399)
                                DiagnosticStatus.WARNING -> Color(0xFFFBBF24)
                                DiagnosticStatus.ERROR -> Color(0xFFF87171)
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
                    .background(Color(0xFF221616))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 0) Color(0xFFDC2626).copy(alpha = 0.35f) else Color.Transparent,
                    border = if (selectedTab == 0) BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)) else null
                ) {
                    Text(
                        text = "System Health",
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 1) Color(0xFFDC2626).copy(alpha = 0.35f) else Color.Transparent,
                    border = if (selectedTab == 1) BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)) else null
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
                            color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (totalApiCalls > 0) Color(0xFFEF4444) else Color.Gray.copy(alpha = 0.4f)
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
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Notices",
                        count = healthState.warningCount,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryBadge(
                        label = "Healthy",
                        count = healthState.healthyCount,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

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

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Bottom Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClearErrors,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear Errors", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onTestRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Providers", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        color = Color(0xFF38BDF8),
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
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Strict Dedup: Exactly 1 API call per unique question enforced.",
                            fontSize = 10.sp,
                            color = Color(0xFFD1FAE5)
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
                            color = Color.White.copy(alpha = 0.5f),
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

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Bottom Actions Row for API Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { AppStateManager.clearApiCallLogs() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Reset Counter & Logs", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onTestRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Providers (1 Call)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    modifier: Modifier = Modifier
) {
    val statusColor = when (item.status) {
        DiagnosticStatus.HEALTHY -> Color(0xFF10B981)
        DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
        DiagnosticStatus.ERROR -> Color(0xFFEF4444)
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1313)
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        fontSize = 13.sp,
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
                    color = Color(0xFF0D0808),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CODE:",
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
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Suggested Fix",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = "SUGGESTED FIX:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
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
        ApiCallStatus.SUCCESS -> Color(0xFF10B981)
        ApiCallStatus.FAILED -> Color(0xFFEF4444)
        ApiCallStatus.IN_FLIGHT -> Color(0xFFF59E0B)
    }

    val statusText = when (log.status) {
        ApiCallStatus.SUCCESS -> if (log.repliesCount > 0) "200 OK (${log.repliesCount} replies)" else "200 OK (0 replies)"
        ApiCallStatus.FAILED -> "FAILED"
        ApiCallStatus.IN_FLIGHT -> "CALLING..."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1212)
        ),
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
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${log.provider.displayName} · ${log.model}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7DD3FC),
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
                color = Color(0xFF0F0A0A)
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
                        color = Color(0xFFEF4444)
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
                        color = Color(0xFFF87171),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
