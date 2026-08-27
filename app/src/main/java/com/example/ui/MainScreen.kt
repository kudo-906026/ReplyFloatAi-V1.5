package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AiProvider
import com.example.service.FloatingOverlayService
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

enum class ControlPanelTab(val title: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "tab_dashboard"),
    SIMULATOR("Simulator", Icons.Default.Speed, "tab_simulator"),
    APPS("Apps", Icons.Default.Apps, "tab_apps"),
    PROVIDERS("Providers", Icons.Default.Psychology, "tab_providers"),
    OVERLAY("Overlay", Icons.Default.Layers, "tab_overlay"),
    REPLIES("Replies", Icons.Default.QuestionAnswer, "tab_replies")
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val settings by AppStateManager.settings.collectAsStateWithLifecycle()
    val isOverlayRunning by AppStateManager.isOverlayRunning.collectAsStateWithLifecycle()
    val isAccessibilityRunning by AppStateManager.isAccessibilityRunning.collectAsStateWithLifecycle()
    val currentQuestion by AppStateManager.currentQuestion.collectAsStateWithLifecycle()
    val activeReplies by AppStateManager.activeReplies.collectAsStateWithLifecycle()
    val activeProvider by AppStateManager.activeProvider.collectAsStateWithLifecycle()
    val isGenerating by AppStateManager.isGenerating.collectAsStateWithLifecycle()
    val errorMessage by AppStateManager.errorMessage.collectAsStateWithLifecycle()
    val diagnosticLogs by AppStateManager.diagnosticLogs.collectAsStateWithLifecycle()

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }

    fun updatePermissions() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
        AppStateManager.refreshServiceStatuses(context)
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                updatePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceCard)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CrimsonPrimary.copy(alpha = 0.2f))
                                .border(1.dp, CrimsonPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "ReplyFloat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextWhite,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "INTELLIGENT OVERLAY DAEMON",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, if (isOverlayRunning) AccentGreen.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isOverlayRunning) AccentGreen else CrimsonPrimary)
                        )
                        Text(
                            text = if (isOverlayRunning) "OVERLAY ACTIVE" else "IDLE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverlayRunning) TechGreen else TextMuted
                        )
                    }
                }

                // Scrollable Top Tabs Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurfaceCard,
                    contentColor = CrimsonPrimary,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CrimsonPrimary,
                            height = 2.dp
                        )
                    },
                    divider = {}
                ) {
                    ControlPanelTab.entries.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            modifier = Modifier.testTag(tab.tag),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) CrimsonPrimary else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextWhite else TextMuted
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (ControlPanelTab.entries[selectedTab]) {
                ControlPanelTab.DASHBOARD -> {
                    DashboardTab(
                        isOverlayRunning = isOverlayRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        isAccessibilityEnabled = isAccessibilityRunning,
                        settings = settings,
                        activeProvider = activeProvider,
                        onRefreshPermissions = { updatePermissions() },
                        onStartAssistant = {
                            updatePermissions()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                FloatingOverlayService.start(context)
                                AppStateManager.setOverlayRunning(true)
                                Toast.makeText(context, "Floating Assistant Started", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onStopAssistant = {
                            FloatingOverlayService.stop(context)
                            AppStateManager.setOverlayRunning(false)
                            Toast.makeText(context, "Floating Assistant Stopped", Toast.LENGTH_SHORT).show()
                        },
                        onRequestOverlayPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        onRequestAccessibilityPermission = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(context, "Enable 'ReplyFloat Detector' in Accessibility list", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                ControlPanelTab.SIMULATOR -> {
                    SimulatorTab(
                        currentQuestion = currentQuestion,
                        activeReplies = activeReplies,
                        isGenerating = isGenerating,
                        errorMessage = errorMessage,
                        settings = settings,
                        activeProvider = activeProvider,
                        diagnosticLogs = diagnosticLogs,
                        onSimulateQuestion = { text, source ->
                            AppStateManager.simulateQuestionDetected(context, text, source)
                        },
                        onSimulateOcrQuestion = { text, source ->
                            AppStateManager.simulateOcrQuestionDetected(context, text, source)
                        },
                        onCopyReply = { reply ->
                            AppStateManager.copyAndDismissReply(context, reply)
                        },
                        onClearDiagnosticLogs = {
                            AppStateManager.clearDiagnosticLogs()
                        }
                    )
                }
                ControlPanelTab.APPS -> {
                    AppsTab(
                        appsList = settings.appsWhitelist,
                        onToggleApp = { AppStateManager.toggleAppWhitelist(it) },
                        onAddCustomApp = { name, pkg, cat -> AppStateManager.addCustomApp(name, pkg, cat) },
                        onDeleteCustomApp = { AppStateManager.deleteCustomApp(it) }
                    )
                }
                ControlPanelTab.PROVIDERS -> {
                    ProvidersTab(
                        settings = settings,
                        activeProvider = activeProvider,
                        onSelectPreferredProvider = { AppStateManager.updatePreferredProvider(it) },
                        onMoveFallbackOrder = { from, to -> AppStateManager.moveProviderInFallbackOrder(from, to) },
                        onSetPrimaryFallback = { AppStateManager.setPrimaryFallbackProvider(it) },
                        onUpdateApiKey = { provider, key -> AppStateManager.updateProviderApiKey(provider, key) },
                        onAddCustomProvider = { name, model, endpoint, key ->
                            AppStateManager.addCustomProvider(name, model, endpoint, key)
                        },
                        onDeleteCustomProvider = { AppStateManager.deleteCustomProvider(it) }
                    )
                }
                ControlPanelTab.OVERLAY -> {
                    OverlayTab(
                        settings = settings,
                        onSetContinuousAnalysis = { AppStateManager.setContinuousScreenAnalysis(it) },
                        onSetRealTimeNodeTracking = { AppStateManager.setRealTimeNodeTracking(it) },
                        onSetSmartDebounceMs = { AppStateManager.setSmartDebounceMs(it) },
                        onSetOcrFallbackEnabled = { AppStateManager.setOcrFallbackEnabled(it) },
                        onSetOcrDebounceMs = { AppStateManager.setOcrDebounceMs(it) },
                        onSetOverlayBarStyle = { AppStateManager.setOverlayBarStyle(it) },
                        onSetOverlayInteractionMode = { AppStateManager.setOverlayInteractionMode(it) },
                        onSetAutoHideEnabled = { AppStateManager.setAutoHideEnabled(it) },
                        onSetAutoHideDelaySec = { AppStateManager.setAutoHideDelaySec(it) },
                        onSetScreenIdleTimeoutSec = { AppStateManager.setScreenIdleTimeoutSec(it) },
                        onSetOverlayOpacity = { AppStateManager.setOverlayOpacity(it) },
                        onSetOverlayCornerRadius = { AppStateManager.setOverlayCornerRadius(it) },
                        onSetOverlayTextSizeSp = { AppStateManager.setOverlayTextSizeSp(it) },
                        onDeleteSavedPosition = { AppStateManager.deleteSavedPosition(it) },
                        onClearAllSavedPositions = { AppStateManager.clearAllSavedPositions() }
                    )
                }
                ControlPanelTab.REPLIES -> {
                    RepliesTab(
                        settings = settings,
                        onUpdateTone = { AppStateManager.updateTone(it) },
                        onUpdateReplyCount = { AppStateManager.updateReplyCount(it) },
                        onSetUnderstandingMode = { AppStateManager.setUnderstandingMode(it) },
                        onSetUnderstandingSummaryLength = { AppStateManager.setUnderstandingSummaryLength(it) },
                        onSetAutoGenerateReplies = { AppStateManager.setAutoGenerateReplies(it) },
                        onSetDetectQuestionsOnly = { AppStateManager.setDetectQuestionsOnly(it) },
                        onSetPrefetchOnAppFocus = { AppStateManager.setPrefetchOnAppFocus(it) },
                        onSetAutoCopySingleReply = { AppStateManager.setAutoCopySingleReply(it) },
                        onSetExpandableReplies = { AppStateManager.setExpandableReplies(it) },
                        onSetResponseLengthPreset = { AppStateManager.setResponseLengthPreset(it) },
                        onSetCustomCharLimit = { AppStateManager.setCustomCharLimit(it) },
                        onSetCacheRetentionMinutes = { AppStateManager.setCacheRetentionMinutes(it) },
                        onSetHistoryRetentionDays = { AppStateManager.setHistoryRetentionDays(it) }
                    )
                }
            }
        }
    }
}
