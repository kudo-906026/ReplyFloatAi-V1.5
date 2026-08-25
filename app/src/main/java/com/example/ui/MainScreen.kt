package com.example.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Filter3
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.AiProvider
import com.example.model.ReplyLength
import com.example.model.ReplyTone
import com.example.service.FloatingOverlayService
import com.example.service.QuestionDetectorAccessibilityService
import com.example.state.AppStateManager
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityService(context)) }

    val isOverlayRunning by AppStateManager.isOverlayRunning.collectAsState()
    val isAccessibilityRunning by AppStateManager.isAccessibilityRunning.collectAsState()
    val settings by AppStateManager.settings.collectAsState()
    val currentQuestion by AppStateManager.currentQuestion.collectAsState()
    val activeReplies by AppStateManager.activeReplies.collectAsState()
    val activeProvider by AppStateManager.activeProvider.collectAsState()
    val isGenerating by AppStateManager.isGenerating.collectAsState()
    val errorMessage by AppStateManager.errorMessage.collectAsState()
    val history by AppStateManager.history.collectAsState()
    val healthState by AppStateManager.diagnosticsState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var testInputText by remember { mutableStateOf("Are you free to meet tomorrow at 3 PM?") }

    // Re-check permissions when activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = checkOverlayPermission(context)
                isAccessibilityEnabled = checkAccessibilityService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CrimsonPrimary.copy(alpha = 0.18f))
                                .border(1.dp, CrimsonPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "ReplyFloat AI",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ReplyFloatAi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "AI FLOATING ASSISTANT",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = CrimsonPrimary
                            )
                        }
                    }
                },
                actions = {
                    // System Health Diagnostics Badge (Tapping navigates directly to Diagnostics Tab)
                    AppDiagnosticsBadge(
                        healthState = healthState,
                        onClick = { selectedTab = 3 }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quick Overlay Status Pill
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isOverlayRunning) CrimsonPrimary.copy(alpha = 0.18f) else Color(0x2264748B)
                            )
                            .border(
                                1.dp,
                                if (isOverlayRunning) CrimsonPrimary else Color(0x4464748B),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isOverlayRunning) CrimsonPrimary else Color(0xFF94A3B8)
                                    )
                            )
                            Text(
                                text = if (isOverlayRunning) "Overlay Active" else "Overlay Inactive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOverlayRunning) CrimsonPrimary else Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Top Navigation Tabs (4 Tabs: Controls & Test, Settings & Providers, History, Diagnostics)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkBg,
                contentColor = CrimsonPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CrimsonPrimary,
                        height = 2.5.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = DarkCardBorder)
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = CrimsonPrimary,
                    unselectedContentColor = TextMuted,
                    text = { Text("Controls", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = CrimsonPrimary,
                    unselectedContentColor = TextMuted,
                    text = { Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    selectedContentColor = CrimsonPrimary,
                    unselectedContentColor = TextMuted,
                    text = { Text("History (${history.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    selectedContentColor = CrimsonPrimary,
                    unselectedContentColor = TextMuted,
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (healthState.overallStatus != com.example.model.DiagnosticStatus.HEALTHY) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (healthState.overallStatus == com.example.model.DiagnosticStatus.ERROR)
                                                CrimsonPrimary
                                            else
                                                Color(0xFFF59E0B)
                                        )
                                )
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> ControlsAndTestTab(
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled || isAccessibilityRunning,
                    isOverlayRunning = isOverlayRunning,
                    currentQuestion = currentQuestion?.text,
                    activeReplies = activeReplies,
                    activeProvider = activeProvider,
                    isGenerating = isGenerating,
                    errorMessage = errorMessage,
                    testInputText = testInputText,
                    onTestInputChange = { testInputText = it },
                    onStartOverlay = {
                        if (!hasOverlayPermission) {
                            requestOverlayPermission(context)
                        } else {
                            FloatingOverlayService.start(context)
                        }
                    },
                    onStopOverlay = {
                        FloatingOverlayService.stop(context)
                    },
                    onRequestOverlayPermission = { requestOverlayPermission(context) },
                    onRequestAccessibilityPermission = { requestAccessibilityPermission(context) },
                    onTestDetection = { q ->
                        AppStateManager.onQuestionDetected(q, "App Sandbox", force = true)
                    },
                    onRetryGeneration = {
                        AppStateManager.generateRepliesForQuestion()
                    },
                    onCopyReply = { reply ->
                        AppStateManager.copyAndDismissReply(context, reply)
                    },
                    onDismissReply = { id ->
                        AppStateManager.dismissReply(id)
                    },
                    onNavigateToSettings = {
                        selectedTab = 1
                    }
                )

                1 -> SettingsTab(
                    settings = settings,
                    onUpdateSettings = { AppStateManager.updateSettings(it) },
                    onUpdateSelectionMode = { AppStateManager.updateSelectionMode(it) },
                    onUpdatePreferredProvider = { AppStateManager.updatePreferredProvider(it) },
                    onUpdateLength = { AppStateManager.updateReplyLength(it) },
                    onUpdateCount = { AppStateManager.updateReplyCount(it) },
                    onUpdateTone = { AppStateManager.updateTone(it) },
                    onUpdateProviderKey = { provider, key -> AppStateManager.updateProviderKey(provider, key) },
                    onMoveProviderUp = { index ->
                        val currentChain = settings.providerChain.toMutableList()
                        if (index > 0 && index < currentChain.size) {
                            val temp = currentChain[index]
                            currentChain[index] = currentChain[index - 1]
                            currentChain[index - 1] = temp
                            AppStateManager.updateProviderChain(currentChain)
                        }
                    },
                    onMoveProviderDown = { index ->
                        val currentChain = settings.providerChain.toMutableList()
                        if (index >= 0 && index < currentChain.size - 1) {
                            val temp = currentChain[index]
                            currentChain[index] = currentChain[index + 1]
                            currentChain[index + 1] = temp
                            AppStateManager.updateProviderChain(currentChain)
                        }
                    }
                )

                2 -> HistoryTab(
                    history = history,
                    settings = settings,
                    onUpdateSettings = { AppStateManager.updateSettings(it) },
                    onDeleteHistoryItem = { AppStateManager.deleteHistoryItem(it) },
                    onClearHistory = { AppStateManager.clearHistory() },
                    onCopyReply = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Reply", text)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                )

                3 -> DiagnosticsTab(
                    healthState = healthState,
                    onRequestOverlayPermission = { requestOverlayPermission(context) },
                    onRequestAccessibilityPermission = { requestAccessibilityPermission(context) },
                    onNavigateToSettings = { selectedTab = 1 },
                    onTestRequest = {
                        val samplePrompt = testInputText.ifBlank { "What time should we meet tomorrow?" }
                        AppStateManager.onQuestionDetected(samplePrompt, "App Sandbox", force = true)
                    },
                    onClearErrors = { AppStateManager.clearAllErrors() }
                )
            }
        }
    }
}
}

@Composable
fun ControlsAndTestTab(
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayRunning: Boolean,
    currentQuestion: String?,
    activeReplies: List<com.example.model.ReplyItem>,
    activeProvider: AiProvider? = null,
    isGenerating: Boolean,
    errorMessage: String? = null,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onTestDetection: (String) -> Unit,
    onRetryGeneration: () -> Unit,
    onCopyReply: (com.example.model.ReplyItem) -> Unit,
    onDismissReply: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Floating Overlay Launch Card
        item {
            ControlPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_control_card"),
                isSelected = isOverlayRunning,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Floating AI Bar",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Floats over WhatsApp, Messenger, Gmail, and any app",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isOverlayRunning) CrimsonPrimary.copy(alpha = 0.2f) else DarkBg)
                                .border(1.dp, if (isOverlayRunning) CrimsonPrimary.copy(alpha = 0.6f) else DarkCardBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isOverlayRunning) CrimsonPrimary else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Start / Stop Toggle Button
                    Button(
                        onClick = {
                            if (isOverlayRunning) onStopOverlay() else onStartOverlay()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_overlay_button")
                            .then(if (isOverlayRunning) Modifier.crimsonGlow(radius = 10.dp, color = CrimsonPrimary.copy(alpha = 0.4f)) else Modifier),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) CrimsonPrimary else Color(0xFF271518),
                            contentColor = Color.White
                        ),
                        border = if (!isOverlayRunning) BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.6f)) else null
                    ) {
                        Icon(
                            imageVector = if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isOverlayRunning) Color.White else CrimsonPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOverlayRunning) "Stop Floating Overlay" else "Start Floating Overlay Bar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Permissions Status Section
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "SYSTEM PERMISSIONS",
                        icon = Icons.Default.Lock
                    )

                    // 1. Overlay Permission Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Draw Over Other Apps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasOverlayPermission) "Granted" else "Required for floating bar",
                                    fontSize = 11.sp,
                                    color = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }

                        if (!hasOverlayPermission) {
                            OutlinedButton(
                                onClick = onRequestOverlayPermission,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonPrimary),
                                modifier = Modifier.testTag("grant_overlay_perm_button")
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // 2. Accessibility Service Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.widthIn(max = 200.dp)) {
                                Text(
                                    text = "Accessibility Question Scanner",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAccessibilityEnabled) "Active (Scans '?' asynchronously)" else "Required to detect questions on screen",
                                    fontSize = 11.sp,
                                    color = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            OutlinedButton(
                                onClick = onRequestAccessibilityPermission,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonPrimary),
                                modifier = Modifier.testTag("grant_accessibility_perm_button")
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Live Floating Bar In-App Sandbox & Tester
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "INTERACTIVE QUESTION SANDBOX",
                        icon = Icons.Default.AutoAwesome
                    )

                    Text(
                        text = "Simulate on-screen text detection or test custom questions with Gemini:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    // Preset Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            "Are you free to meet tomorrow at 3 PM?",
                            "isko karo kya vote?",
                            "Demain à 14h, ça te convient?",
                            "¿Puedes revisar el documento hoy?",
                            "明日ミーティングは可能ですか？",
                            "What is your status update for today?"
                        )
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBg)
                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                    .clickable { onTestInputChange(preset) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = testInputText,
                        onValueChange = onTestInputChange,
                        label = { Text("Question containing '?'", color = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("question_input_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = CrimsonPrimary
                        ),
                        trailingIcon = {
                            if (testInputText.isNotBlank()) {
                                IconButton(onClick = { onTestInputChange("") }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        }
                    )

                    Button(
                        onClick = { onTestDetection(testInputText) },
                        enabled = testInputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("trigger_test_ai_button")
                            .then(if (testInputText.isNotBlank() && !isGenerating) Modifier.crimsonGlow(radius = 8.dp, color = CrimsonPrimary.copy(alpha = 0.35f)) else Modifier),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary,
                            disabledContainerColor = DarkBg
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calling AI fallback chain...", color = Color.White)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detect & Generate AI Replies", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Active Generated Replies / Loading / Error Section
                    if (isGenerating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkBg)
                                .border(1.dp, CrimsonPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = CrimsonPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Generating replies across configured providers...",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else if (errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRetryGeneration() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2D1214)
                            ),
                            border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = CrimsonPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Generation Unavailable",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CrimsonLight
                                    )
                                }
                                Text(
                                    text = errorMessage,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = onRetryGeneration,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CrimsonPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Chain", fontSize = 12.sp, color = Color.White)
                                    }

                                    OutlinedButton(
                                        onClick = onNavigateToSettings,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, DarkCardBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Settings", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (activeReplies.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SUGGESTED REPLIES:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary,
                                letterSpacing = 0.5.sp
                            )
                            if (activeProvider != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CrimsonPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, CrimsonPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "via ${activeProvider.displayName}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonLight,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        activeReplies.forEach { reply ->
                            ReplyCardItem(
                                reply = reply,
                                onCopy = { onCopyReply(reply) },
                                onDismiss = { onDismissReply(reply.id) }
                            )
                        }
                    } else if (currentQuestion != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1510B981))
                                .border(1.dp, Color(0x4410B981), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All reply cards copied to clipboard!",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    settings: com.example.model.ReplySettings,
    onUpdateSettings: (com.example.model.ReplySettings) -> Unit,
    onUpdateSelectionMode: (com.example.model.ProviderSelectionMode) -> Unit = {},
    onUpdatePreferredProvider: (AiProvider) -> Unit = {},
    onUpdateLength: (ReplyLength) -> Unit,
    onUpdateCount: (Int) -> Unit,
    onUpdateTone: (ReplyTone) -> Unit,
    onUpdateProviderKey: (AiProvider, String) -> Unit = { _, _ -> },
    onMoveProviderUp: (Int) -> Unit = {},
    onMoveProviderDown: (Int) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Provider Selection Mode Card
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "PROVIDER SELECTION MODE",
                        icon = Icons.Default.Tune
                    )

                    Text(
                        text = "Choose how ReplyFloat decides which AI model to call for instant replies:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    // Mode Selection Buttons (Auto Fallback vs Preferred Provider)
                    com.example.model.ProviderSelectionMode.entries.forEach { mode ->
                        val isSelected = settings.selectionMode == mode
                        ControlPanelCard(
                            isSelected = isSelected,
                            shape = RoundedCornerShape(12.dp),
                            onClick = { onUpdateSelectionMode(mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onUpdateSelectionMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CrimsonPrimary,
                                        unselectedColor = TextMuted
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) CrimsonLight else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else TextMuted,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // If Preferred Provider is selected, show provider selector
                    if (settings.selectionMode == com.example.model.ProviderSelectionMode.PREFERRED_PROVIDER) {
                        HorizontalDivider(color = DarkCardBorder)

                        Text(
                            text = "SELECT PREFERRED PROVIDER:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonPrimary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                val isSelected = settings.preferredProvider == provider
                                ControlPanelCard(
                                    isSelected = isSelected,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onUpdatePreferredProvider(provider) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = provider.displayName,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) CrimsonLight else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Calls ${settings.preferredProvider.displayName} first. If it encounters a network error, auth failure, or quota limit (HTTP 429), it will gracefully fall back to the next configured provider in order.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }

        // Multi-Provider Fallback Chain Reordering & Status Card
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
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
                        ControlPanelSectionHeader(
                            title = "PROVIDER FALLBACK ORDER",
                            icon = Icons.Default.Layers
                        )
                    }

                    Text(
                        text = "Reorder the fallback sequence below. If an attempt fails, ReplyFloat immediately proceeds to the next ready provider in this sequence with full diagnostic logging.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    // Reorderable list of providers
                    settings.providerChain.forEachIndexed { index, provider ->
                        val hasKey = when (provider) {
                            AiProvider.GEMINI -> settings.customApiKey.isNotBlank() || com.example.BuildConfig.GEMINI_API_KEY.isNotBlank()
                            AiProvider.OPENAI -> settings.openAiApiKey.isNotBlank()
                            AiProvider.CLAUDE -> settings.claudeApiKey.isNotBlank()
                            AiProvider.GROK -> settings.grokApiKey.isNotBlank()
                        }

                        val isFirstInAuto = index == 0 && settings.selectionMode == com.example.model.ProviderSelectionMode.AUTO_FALLBACK
                        val isPreferred = settings.selectionMode == com.example.model.ProviderSelectionMode.PREFERRED_PROVIDER && settings.preferredProvider == provider

                        ControlPanelCard(
                            isSelected = isFirstInAuto || isPreferred,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Order number badge
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index == 0) CrimsonPrimary
                                                else DarkSurfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = if (index == 0) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = provider.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isFirstInAuto) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = CrimsonPrimary.copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "PRIMARY",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CrimsonLight,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isPreferred) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = CrimsonPrimary.copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "PREFERRED",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CrimsonLight,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (hasKey) "Ready: ${provider.modelName}" else "No key entered yet",
                                            fontSize = 10.5.sp,
                                            color = if (hasKey) StatusGreen else TextMuted
                                        )
                                    }
                                }

                                // Up / Down reorder arrows
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onMoveProviderUp(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move ${provider.displayName} Up",
                                            tint = if (index > 0) CrimsonPrimary else TextMuted.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveProviderDown(index) },
                                        enabled = index < settings.providerChain.size - 1,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move ${provider.displayName} Down",
                                            tint = if (index < settings.providerChain.size - 1) CrimsonPrimary else TextMuted.copy(alpha = 0.3f),
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

        // Multi-Provider API Key Inputs
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "PROVIDER API KEYS",
                        icon = Icons.Default.Key
                    )

                    Text(
                        text = "Enter keys for as many providers as you have. You don't need all four — ReplyFloat will seamlessly route through whatever keys are provided.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    // 1. Gemini
                    ProviderApiKeyInputField(
                        provider = AiProvider.GEMINI,
                        currentKey = settings.geminiApiKey.ifBlank { settings.customApiKey },
                        isEnvKeyPresent = com.example.BuildConfig.GEMINI_API_KEY.isNotBlank(),
                        hint = "AI Studio environment key active by default (or custom AIzaSy...)",
                        onKeyChanged = { onUpdateProviderKey(AiProvider.GEMINI, it) }
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    // 2. OpenAI
                    ProviderApiKeyInputField(
                        provider = AiProvider.OPENAI,
                        currentKey = settings.openAiApiKey,
                        hint = "OpenAI key (sk-...)",
                        onKeyChanged = { onUpdateProviderKey(AiProvider.OPENAI, it) }
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    // 3. Claude
                    ProviderApiKeyInputField(
                        provider = AiProvider.CLAUDE,
                        currentKey = settings.claudeApiKey,
                        hint = "Anthropic Claude key (sk-ant-...)",
                        onKeyChanged = { onUpdateProviderKey(AiProvider.CLAUDE, it) }
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    // 4. Grok (xAI)
                    ProviderApiKeyInputField(
                        provider = AiProvider.GROK,
                        currentKey = settings.grokApiKey,
                        hint = "xAI Grok key (xai-...)",
                        onKeyChanged = { onUpdateProviderKey(AiProvider.GROK, it) }
                    )
                }
            }
        }

        // Reply Length Configuration
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "REPLY LENGTH",
                        icon = Icons.Default.Tune
                    )

                    Text(
                        text = "Choose the exact output format for AI response generation:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReplyLength.entries.forEach { lengthOption ->
                            val isSelected = settings.length == lengthOption
                            ControlPanelCard(
                                isSelected = isSelected,
                                shape = RoundedCornerShape(12.dp),
                                onClick = { onUpdateLength(lengthOption) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    ) {
                                        Text(
                                            text = lengthOption.label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) CrimsonLight else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = lengthOption.promptInstruction,
                                            fontSize = 11.5.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else TextMuted,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(CrimsonPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    1.dp,
                                                    DarkCardBorder,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reply Count Configuration
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "SUGGESTION COUNT (1 - 3)",
                        icon = Icons.Default.Filter3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val isSelected = settings.count == count
                            ControlPanelCard(
                                isSelected = isSelected,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { onUpdateCount(count) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = CrimsonPrimary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "$count ${if (count == 1) "Reply" else "Replies"}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) CrimsonLight else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tone & Style Selector
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlPanelSectionHeader(
                        title = "CONVERSATION TONE",
                        icon = Icons.Default.ChatBubbleOutline
                    )

                    Text(
                        text = "Set the personality and voice for AI-generated response suggestions:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReplyTone.entries.forEach { tone ->
                            val isSelected = settings.tone == tone
                            ControlPanelCard(
                                isSelected = isSelected,
                                shape = RoundedCornerShape(12.dp),
                                onClick = { onUpdateTone(tone) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    ) {
                                        Text(
                                            text = tone.label,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) CrimsonLight else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = tone.description,
                                            fontSize = 11.5.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else TextMuted,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(CrimsonPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    1.dp,
                                                    DarkCardBorder,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Multi-Language Mode (Any language / Hinglish)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CrimsonPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Multi-Language Mode (\"Lang\")",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.multiLanguageEnabled)
                                    "Detects any language & code-mixed/Hinglish (e.g. 'isko karo kya vote?') and replies in the exact same language"
                                else
                                    "Standard English-only replies",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    ControlPanelSwitch(
                        checked = settings.multiLanguageEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings(settings.copy(multiLanguageEnabled = checked))
                        }
                    )
                }
            }
        }

        // Question Scanning (Analyze on/off switch)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (settings.scanningEnabled) StatusGreen.copy(alpha = 0.2f) else DarkSurfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = if (settings.scanningEnabled) StatusGreen else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Analyze / Screen Scanning",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.scanningEnabled)
                                    "Active: scanning for questions in other apps"
                                else
                                    "Paused: zero background scanning or API calls",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    ControlPanelSwitch(
                        checked = settings.scanningEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings(settings.copy(scanningEnabled = checked))
                        }
                    )
                }
            }
        }

        // Auto-generation toggle
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Generate on Screen '?'",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically calls Gemini as soon as a question mark is detected on screen",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    ControlPanelSwitch(
                        checked = settings.autoGenerate,
                        onCheckedChange = { checked ->
                            onUpdateSettings(settings.copy(autoGenerate = checked))
                        }
                    )
                }
            }
        }

        // Auto-delete History (1-10 mins adjustable)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CrimsonPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = CrimsonPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Auto-Delete History",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.autoDeleteHistory) "Purges items after ${settings.autoDeleteMinutes} min" else "Disabled (kept until cleared)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        ControlPanelSwitch(
                            checked = settings.autoDeleteHistory,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(autoDeleteHistory = checked))
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = settings.autoDeleteHistory,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HorizontalDivider(color = DarkCardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EXPIRY DURATION (1 - 10 MINS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonPrimary,
                                    letterSpacing = 0.5.sp
                                )

                                // Stepper: - and + buttons with current minute badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (settings.autoDeleteMinutes > 1) {
                                                onUpdateSettings(settings.copy(autoDeleteMinutes = settings.autoDeleteMinutes - 1))
                                            }
                                        },
                                        enabled = settings.autoDeleteMinutes > 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Decrease minute",
                                            tint = if (settings.autoDeleteMinutes > 1) CrimsonPrimary else TextMuted.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CrimsonPrimary.copy(alpha = 0.15f))
                                            .border(1.dp, CrimsonPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${settings.autoDeleteMinutes} min${if (settings.autoDeleteMinutes > 1) "s" else ""}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonLight
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (settings.autoDeleteMinutes < 10) {
                                                onUpdateSettings(settings.copy(autoDeleteMinutes = settings.autoDeleteMinutes + 1))
                                            }
                                        },
                                        enabled = settings.autoDeleteMinutes < 10,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Increase minute",
                                            tint = if (settings.autoDeleteMinutes < 10) CrimsonPrimary else TextMuted.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Slider: 1 min to 10 min
                            Slider(
                                value = settings.autoDeleteMinutes.toFloat(),
                                onValueChange = { newValue ->
                                    onUpdateSettings(settings.copy(autoDeleteMinutes = newValue.toInt().coerceIn(1, 10)))
                                },
                                valueRange = 1f..10f,
                                steps = 8, // 2, 3, 4, 5, 6, 7, 8, 9
                                colors = SliderDefaults.colors(
                                    thumbColor = CrimsonPrimary,
                                    activeTrackColor = CrimsonPrimary,
                                    inactiveTrackColor = DarkSurfaceVariant
                                )
                            )

                            // Quick preset chips (all 1 to 10 minutes)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..10).forEach { mins ->
                                    val isSelected = settings.autoDeleteMinutes == mins
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) CrimsonPrimary else DarkSurfaceVariant
                                            )
                                            .then(
                                                if (isSelected) Modifier.crimsonGlow() else Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                            )
                                            .clickable {
                                                onUpdateSettings(settings.copy(autoDeleteMinutes = mins))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else TextMuted
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Detected questions and AI replies older than ${settings.autoDeleteMinutes} minute${if (settings.autoDeleteMinutes > 1) "s" else ""} will be deleted automatically.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderApiKeyInputField(
    provider: AiProvider,
    currentKey: String,
    isEnvKeyPresent: Boolean = false,
    hint: String,
    onKeyChanged: (String) -> Unit
) {
    var textValue by remember(currentKey) { mutableStateOf(currentKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    text = provider.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "(${provider.modelName})",
                    fontSize = 10.5.sp,
                    color = TextMuted
                )
            }

            // Status chip
            if (isEnvKeyPresent && textValue.isBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StatusGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "AI Studio Key Active",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (textValue.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StatusCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, StatusCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Custom Key Set",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Text(
                        text = "No Key",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onKeyChanged(it)
            },
            placeholder = { Text(hint, fontSize = 11.sp, color = TextMuted) },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (textValue.isNotBlank()) {
                        IconButton(onClick = {
                            textValue = ""
                            onKeyChanged("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = TextMuted)
                        }
                    }
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide key" else "Show key",
                            modifier = Modifier.size(16.dp),
                            tint = TextMuted
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CrimsonPrimary,
                unfocusedBorderColor = DarkCardBorder,
                focusedContainerColor = DarkSurfaceCard,
                unfocusedContainerColor = DarkSurfaceCard,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun HistoryTab(
    history: List<com.example.model.QuestionDetectionHistory>,
    settings: com.example.model.ReplySettings,
    onUpdateSettings: (com.example.model.ReplySettings) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onCopyReply: (String) -> Unit
) {
    // Dynamic 1-second ticker for real-time countdown calculation
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // History Header Card with Auto-delete Status, Stepper & Clear Button
        ControlPanelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
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
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (settings.autoDeleteHistory) CrimsonPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Auto-Delete History",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.autoDeleteHistory) "Purging after ${settings.autoDeleteMinutes} min" else "Auto-deletion disabled",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (history.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearHistory,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = CrimsonPrimary
                                ),
                                border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Clear All",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Clear", fontSize = 10.sp)
                            }
                        }

                        ControlPanelSwitch(
                            checked = settings.autoDeleteHistory,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(autoDeleteHistory = checked))
                            }
                        )
                    }
                }

                // If Auto-Delete is active, show quick adjustable buttons (1-10 mins) + stepper
                AnimatedVisibility(
                    visible = settings.autoDeleteHistory,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        HorizontalDivider(color = DarkCardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ADJUST TIME (1 - 10 MINS):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary,
                                letterSpacing = 0.4.sp
                            )

                            // Quick stepper (- / +)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (settings.autoDeleteMinutes > 1) {
                                            onUpdateSettings(settings.copy(autoDeleteMinutes = settings.autoDeleteMinutes - 1))
                                        }
                                    },
                                    enabled = settings.autoDeleteMinutes > 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = if (settings.autoDeleteMinutes > 1) CrimsonPrimary else TextMuted.copy(alpha = 0.3f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CrimsonPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, CrimsonPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${settings.autoDeleteMinutes}m",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonLight
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (settings.autoDeleteMinutes < 10) {
                                            onUpdateSettings(settings.copy(autoDeleteMinutes = settings.autoDeleteMinutes + 1))
                                        }
                                    },
                                    enabled = settings.autoDeleteMinutes < 10,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = if (settings.autoDeleteMinutes < 10) CrimsonPrimary else TextMuted.copy(alpha = 0.3f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Minute chips (1 to 10)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (1..10).forEach { mins ->
                                val isSelected = settings.autoDeleteMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) CrimsonPrimary else DarkSurfaceVariant
                                        )
                                        .then(
                                            if (isSelected) Modifier.crimsonGlow() else Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                        )
                                        .clickable {
                                            onUpdateSettings(settings.copy(autoDeleteMinutes = mins))
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QuestionAnswer,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "No questions in history",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (settings.autoDeleteHistory) {
                            "Questions with '?' appear here and auto-delete after ${settings.autoDeleteMinutes} min."
                        } else {
                            "Questions with '?' will appear here automatically."
                        },
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    ControlPanelCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.question,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!item.sourceApp.isNullOrBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(DarkSurfaceVariant)
                                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.sourceApp,
                                                    fontSize = 9.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        // Auto-delete timer badge or elapsed time
                                        if (settings.autoDeleteHistory) {
                                            val maxAgeMs = settings.autoDeleteMinutes * 60 * 1000L
                                            val elapsedMs = currentTimeMs - item.timestamp
                                            val remainingMs = (maxAgeMs - elapsedMs).coerceAtLeast(0)
                                            val totalSec = remainingMs / 1000
                                            val remMin = totalSec / 60
                                            val remSec = totalSec % 60
                                            val countdownStr = if (remMin > 0) "${remMin}m ${remSec}s" else "${remSec}s"

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (remainingMs < 30_000) CrimsonPrimary.copy(alpha = 0.2f)
                                                        else CrimsonPrimary.copy(alpha = 0.15f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (remainingMs < 30_000) CrimsonPrimary else CrimsonPrimary.copy(alpha = 0.4f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        tint = CrimsonLight,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = "Expires in $countdownStr",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = CrimsonLight
                                                    )
                                                }
                                            }
                                        } else {
                                            val elapsedSec = (currentTimeMs - item.timestamp) / 1000
                                            val timeStr = when {
                                                elapsedSec < 60 -> "Just now"
                                                elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
                                                else -> "${elapsedSec / 3600}h ago"
                                            }
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteHistoryItem(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete item",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (!item.englishMeaning.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(1.dp, CrimsonPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = CrimsonLight,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Meaning: ${item.englishMeaning}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CrimsonLight
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = DarkCardBorder)

                            item.replies.forEach { reply ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = reply,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onCopyReply(reply) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = CrimsonPrimary,
                                            modifier = Modifier.size(14.dp)
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

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkAccessibilityService(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    val packageName = context.packageName
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

private fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
