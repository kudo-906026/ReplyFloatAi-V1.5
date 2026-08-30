package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiProvider
import com.example.model.AiProviderType
import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.model.DiagnosticLogEntry
import com.example.model.ReplySettings
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticFilter(val label: String) {
    ALL("All Events"),
    FAILOVERS("Failovers & Errors"),
    MATCHED("Matched Questions"),
    REJECTED("Filtered Noise")
}

data class SystemIssueInfo(
    val component: String,
    val statusCode: String,
    val isCritical: Boolean,
    val explanation: String,
    val suggestedFix: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

@Composable
fun DiagnosticsTab(
    settings: ReplySettings,
    activeProvider: AiProvider?,
    isOverlayRunning: Boolean,
    hasOverlayPermission: Boolean,
    isAccessibilityRunning: Boolean,
    diagnosticLogs: List<DiagnosticLogEntry>,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(DiagnosticFilter.ALL) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Evaluate system issues dynamically
    val detectedIssues = mutableListOf<SystemIssueInfo>()

    // 1. Check Primary AI Provider & API Key
    val primaryProvider = settings.preferredProvider
    val primaryKey = settings.providerApiKeys[primaryProvider.id] ?: primaryProvider.apiKey
    if (primaryProvider.type in listOf(AiProviderType.OPENAI, AiProviderType.GEMINI_API, AiProviderType.ANTHROPIC) && primaryKey.isBlank()) {
        detectedIssues.add(
            SystemIssueInfo(
                component = "Primary AI Provider (${primaryProvider.displayName})",
                statusCode = "HTTP 401 / NO_API_KEY",
                isCritical = false,
                explanation = "${primaryProvider.displayName} is set as your #1 provider but has no API Key configured. Inbound questions will automatically failover to secondary providers.",
                suggestedFix = "Add a valid API key in the Providers tab, or select 'Gemini Flash (Built-in)' as your preferred provider for zero-config offline responses.",
                actionLabel = "Configure Key"
            )
        )
    }

    // 2. Check System Overlay Permission
    if (!hasOverlayPermission) {
        detectedIssues.add(
            SystemIssueInfo(
                component = "Floating Overlay Window (Daemon)",
                statusCode = "PERMISSION_DENIED",
                isCritical = true,
                explanation = "Android System Alert Window permission is not granted. Floating reply bars cannot draw over WhatsApp, Telegram, or other chat apps.",
                suggestedFix = "Tap below to open Android Settings and enable 'Display over other apps' for ReplyFloat.",
                actionLabel = "Grant Permission",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                }
            )
        )
    }

    // 3. Check Accessibility Service
    if (!isAccessibilityRunning) {
        detectedIssues.add(
            SystemIssueInfo(
                component = "Accessibility Question Detector",
                statusCode = "SERVICE_DISABLED",
                isCritical = true,
                explanation = "ReplyFloat Accessibility Service is disabled in Android Settings. Active chat message scanning is currently paused.",
                suggestedFix = "Go to Android Settings > Accessibility > Installed Apps > enable 'ReplyFloat Detector'.",
                actionLabel = "Open Accessibility",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                    Toast.makeText(context, "Turn on 'ReplyFloat Detector' in the Accessibility list", Toast.LENGTH_LONG).show()
                }
            )
        )
    }

    // Check recent diagnostic logs for API errors (e.g. 429 quota, 401 auth, bad request)
    val recentApiError = diagnosticLogs.firstOrNull { it.category in listOf("AUTH_FAILURE", "QUOTA_EXCEEDED", "PROVIDER_ERROR", "BAD_REQUEST", "MODEL_NOT_FOUND") }
    if (recentApiError != null) {
        val (code, fix) = when (recentApiError.category) {
            "AUTH_FAILURE" -> "HTTP 401 Unauthorized" to "Verify the API key entered for this provider in the Providers tab."
            "QUOTA_EXCEEDED" -> "HTTP 429 Quota Exceeded" to "Billing balance is exhausted on upstream provider. Check billing dashboard or rely on Built-in Gemini."
            "MODEL_NOT_FOUND" -> "HTTP 404 Model Not Found" to "The specified model name is deprecated or invalid for your API tier."
            else -> "HTTP ${recentApiError.category}" to "Upstream endpoint reported an error. Fallback engine handled the request."
        }
        detectedIssues.add(
            SystemIssueInfo(
                component = "Upstream Provider (${recentApiError.source})",
                statusCode = code,
                isCritical = false,
                explanation = recentApiError.reason,
                suggestedFix = fix
            )
        )
    }

    val systemHealthStatus = when {
        detectedIssues.any { it.isCritical } -> "ACTION REQUIRED"
        detectedIssues.isNotEmpty() -> "DEGRADED (FAILOVER ACTIVE)"
        else -> "HEALTHY (ALL SYSTEMS OPERATIONAL)"
    }

    val healthColor = when {
        detectedIssues.any { it.isCritical } -> CrimsonPrimary
        detectedIssues.isNotEmpty() -> AccentYellow
        else -> TechGreen
    }

    val filteredLogs = remember(diagnosticLogs, selectedFilter) {
        when (selectedFilter) {
            DiagnosticFilter.ALL -> diagnosticLogs
            DiagnosticFilter.FAILOVERS -> diagnosticLogs.filter { it.category in listOf("AUTH_FAILURE", "QUOTA_EXCEEDED", "NO_API_KEY", "PROVIDER_ERROR", "BAD_REQUEST", "MODEL_NOT_FOUND", "EMPTY_RESPONSE", "FAILOVER") || it.reason.contains("fallback", ignoreCase = true) || it.reason.contains("fail", ignoreCase = true) }
            DiagnosticFilter.MATCHED -> diagnosticLogs.filter { it.result == DetectionResultType.MATCHED }
            DiagnosticFilter.REJECTED -> diagnosticLogs.filter { it.result == DetectionResultType.REJECTED }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. System Health Status Card
        item {
            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_health_card"),
                shapeRadius = 14.dp,
                isSelected = true,
                activeColor = healthColor
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
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(healthColor.copy(alpha = 0.2f))
                                    .border(1.dp, healthColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (detectedIssues.isEmpty()) Icons.Default.HealthAndSafety else Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = healthColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "SYSTEM HEALTH MONITOR",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = systemHealthStatus,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = healthColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = healthColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, healthColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${diagnosticLogs.size} Events Logged",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = healthColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // Live Component Health Matrix
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Component Readiness Matrix:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ComponentStatusBadge(
                                name = "Accessibility",
                                isOk = isAccessibilityRunning,
                                status = if (isAccessibilityRunning) "ONLINE" else "DISABLED",
                                icon = Icons.Default.AccessibilityNew,
                                modifier = Modifier.weight(1f)
                            )
                            ComponentStatusBadge(
                                name = "Overlay Window",
                                isOk = hasOverlayPermission && isOverlayRunning,
                                status = if (hasOverlayPermission) (if (isOverlayRunning) "ACTIVE" else "IDLE") else "NO PERM",
                                icon = Icons.Default.Layers,
                                modifier = Modifier.weight(1f)
                            )
                            ComponentStatusBadge(
                                name = "AI Engine Chain",
                                isOk = true,
                                status = "${settings.fallbackOrder.size} Ready",
                                icon = Icons.Default.Psychology,
                                modifier = Modifier.weight(1f)
                            )
                            ComponentStatusBadge(
                                name = "OCR Engine",
                                isOk = settings.enableOcrFallback,
                                status = if (settings.enableOcrFallback) "READY" else "OFF",
                                icon = Icons.Default.CameraAlt,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Active Issue & Troubleshooting Panel
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "COMPONENT DIAGNOSTICS & ISSUE RESOLUTION",
                        icon = Icons.Default.Warning,
                        accentColor = if (detectedIssues.isEmpty()) TechGreen else AccentYellow
                    )

                    if (detectedIssues.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            color = TechGreen.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, TechGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TechGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "All Systems Operating Nominally",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = TechGreen
                                    )
                                    Text(
                                        text = "Status: 200 OK. Detection scanner, fallback chain, and floating overlay daemon are healthy. No active errors.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            detectedIssues.forEach { issue ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (issue.isCritical) CrimsonPrimary.copy(alpha = 0.12f) else DarkSurfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (issue.isCritical) CrimsonPrimary.copy(alpha = 0.5f) else AccentYellow.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(11.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (issue.isCritical) Icons.Default.Error else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (issue.isCritical) CrimsonLight else AccentYellow,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = issue.component,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = TextWhite,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = (if (issue.isCritical) CrimsonPrimary else AccentYellow).copy(alpha = 0.2f),
                                                border = BorderStroke(0.5.dp, if (issue.isCritical) CrimsonPrimary else AccentYellow)
                                            ) {
                                                Text(
                                                    text = issue.statusCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (issue.isCritical) CrimsonLight else AccentYellow,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = issue.explanation,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DarkCardElevated,
                                            border = BorderStroke(0.5.dp, DarkCardBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = if (issue.actionLabel != null) 8.dp else 0.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "SUGGESTED FIX:",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = TechBlue
                                                    )
                                                    Text(
                                                        text = issue.suggestedFix,
                                                        fontSize = 10.5.sp,
                                                        color = TextWhite
                                                    )
                                                }

                                                if (issue.actionLabel != null && issue.onAction != null) {
                                                    Button(
                                                        onClick = issue.onAction,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (issue.isCritical) CrimsonPrimary else TechBlue
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(
                                                            text = issue.actionLabel,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false
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
                }
            }
        }

        // 3. Real-Time Diagnostic & Failover Event Stream
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
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
                        ControlPanelSectionHeader(
                            title = "REAL-TIME DIAGNOSTIC LOGS",
                            icon = Icons.Default.Speed,
                            accentColor = CrimsonPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy report button
                            IconButton(
                                onClick = {
                                    val report = buildString {
                                        appendLine("=== REPLYFLOAT SYSTEM DIAGNOSTIC REPORT ===")
                                        appendLine("Health: $systemHealthStatus")
                                        appendLine("Timestamp: ${Date()}")
                                        appendLine("Accessibility: $isAccessibilityRunning")
                                        appendLine("Overlay: $hasOverlayPermission (Running: $isOverlayRunning)")
                                        appendLine("Primary Provider: ${primaryProvider.displayName}")
                                        appendLine("Fallback Order: ${settings.fallbackOrder.joinToString(" -> ")}")
                                        appendLine("Auto-Purge Window: ${settings.autoPurgeTimerMinutes} min")
                                        appendLine("\n=== DIAGNOSTIC LOGS (${diagnosticLogs.size} entries) ===")
                                        diagnosticLogs.take(20).forEach { log ->
                                            appendLine("[${timeFormat.format(Date(log.timestamp))}] [${log.result}] [${log.category}] (${log.source}) -> ${log.reason} | Text: \"${log.rawText}\"")
                                        }
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("ReplyFloat Diagnostics", report))
                                    Toast.makeText(context, "Diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Report",
                                    tint = TechBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Clear logs button
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Logs",
                                    tint = CrimsonLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Filter Chips Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DiagnosticFilter.entries.toTypedArray()) { filter ->
                            val isSelected = selectedFilter == filter
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedFilter = filter },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) CrimsonPrimary else DarkSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) CrimsonLight else DarkCardBorder)
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextWhite else TextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (diagnosticLogs.isEmpty()) "No diagnostic events yet. Trigger a message scan or use the Simulator to generate events." else "No events match the selected filter.",
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredLogs.take(25).forEach { log ->
                                val isMatched = log.result == DetectionResultType.MATCHED
                                val isOcr = log.detectionMethod == DetectionMethod.MLKIT_OCR
                                val isError = log.category in listOf("AUTH_FAILURE", "QUOTA_EXCEEDED", "NO_API_KEY", "PROVIDER_ERROR", "BAD_REQUEST", "MODEL_NOT_FOUND", "EMPTY_RESPONSE", "FAILOVER")

                                val borderColor = when {
                                    isError -> CrimsonPrimary.copy(alpha = 0.6f)
                                    isMatched && isOcr -> AccentPurple.copy(alpha = 0.6f)
                                    isMatched -> TechGreen.copy(alpha = 0.5f)
                                    else -> DarkCardBorder
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .padding(end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isError -> CrimsonPrimary
                                                                isMatched -> TechGreen
                                                                else -> AccentYellow
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = if (isError) log.category else log.result.label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = when {
                                                        isError -> CrimsonLight
                                                        isMatched -> TechGreen
                                                        else -> AccentYellow
                                                    }
                                                )

                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = if (isOcr) AccentPurple.copy(alpha = 0.2f) else TechBlue.copy(alpha = 0.15f),
                                                    border = BorderStroke(0.5.dp, if (isOcr) AccentPurple.copy(alpha = 0.4f) else TechBlue.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        text = if (isOcr) "OCR" else "Node",
                                                        fontSize = 8.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isOcr) AccentPurple else TechBlue,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                if (!isError && log.category.isNotBlank()) {
                                                    Text(
                                                        text = "• ${log.category}",
                                                        fontSize = 10.sp,
                                                        color = TextSecondary,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${log.source} @ ${timeFormat.format(Date(log.timestamp))}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = "\"${log.rawText.replace("\n", " ↵ ")}\"",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextWhite,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = if (isError) "Diagnostic: ${log.reason}" else "Reason: ${log.reason}",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
                                            color = when {
                                                isError -> CrimsonLight
                                                isMatched && isOcr -> AccentPurple.copy(alpha = 0.95f)
                                                isMatched -> TechGreen.copy(alpha = 0.9f)
                                                else -> TextSecondary
                                            },
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun ComponentStatusBadge(
    name: String,
    isOk: Boolean,
    status: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isOk) TechGreen else AccentYellow
    Surface(
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceVariant,
        border = BorderStroke(0.5.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = name,
                fontSize = 8.5.sp,
                color = TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = status,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = statusColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
