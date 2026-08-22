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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.ReplyLength
import com.example.model.ReplyTone
import com.example.service.FloatingOverlayService
import com.example.service.QuestionDetectorAccessibilityService
import com.example.state.AppStateManager

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
    val isGenerating by AppStateManager.isGenerating.collectAsState()
    val history by AppStateManager.history.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var testInputText by remember { mutableStateOf("Are you free to meet tomorrow at 3 PM?") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B1515), Color(0xFFFF5757))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "replyFloatAi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "AI Floating Assistant",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Quick Status Pill
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isOverlayRunning) Color(0x3310B981) else Color(0x2264748B)
                            )
                            .border(
                                1.dp,
                                if (isOverlayRunning) Color(0xFF10B981) else Color(0x4464748B),
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
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isOverlayRunning) Color(0xFF10B981) else Color(0xFF94A3B8)
                                    )
                            )
                            Text(
                                text = if (isOverlayRunning) "Overlay Active" else "Overlay Inactive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOverlayRunning) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Controls & Test", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Settings & Length", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History (${history.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> ControlsAndTestTab(
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled || isAccessibilityRunning,
                    isOverlayRunning = isOverlayRunning,
                    currentQuestion = currentQuestion?.text,
                    activeReplies = activeReplies,
                    isGenerating = isGenerating,
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
                        AppStateManager.onQuestionDetected(q, "App Sandbox")
                    },
                    onCopyReply = { reply ->
                        AppStateManager.copyAndDismissReply(context, reply)
                    },
                    onDismissReply = { id ->
                        AppStateManager.dismissReply(id)
                    }
                )

                1 -> SettingsTab(
                    settings = settings,
                    onUpdateSettings = { AppStateManager.updateSettings(it) },
                    onUpdateLength = { AppStateManager.updateReplyLength(it) },
                    onUpdateCount = { AppStateManager.updateReplyCount(it) },
                    onUpdateTone = { AppStateManager.updateTone(it) }
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
    isGenerating: Boolean,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onTestDetection: (String) -> Unit,
    onCopyReply: (com.example.model.ReplyItem) -> Unit,
    onDismissReply: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Floating Overlay Launch Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_control_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8B1515).copy(alpha = 0.2f),
                                    Color(0xFFFF5757).copy(alpha = 0.15f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Start / Stop Toggle Button
                        Button(
                            onClick = {
                                if (isOverlayRunning) onStopOverlay() else onStartOverlay()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("toggle_overlay_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOverlayRunning) Color(0xFFDC2626) else Color(0xFF8B1515),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOverlayRunning) "Stop Floating Overlay" else "Start Floating Overlay Bar",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Permissions Status Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SYSTEM PERMISSIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
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
                                modifier = Modifier.testTag("grant_overlay_perm_button")
                            ) {
                                Text("Grant", fontSize = 11.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

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
                                modifier = Modifier.testTag("grant_accessibility_perm_button")
                            ) {
                                Text("Enable", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Floating Bar In-App Sandbox & Tester
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Interactive Question Sandbox",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Simulate on-screen text detection or test custom questions with Gemini:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            "Could you review the design by tonight?",
                            "Which option works best for you?",
                            "What is your status update for today?"
                        )
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        label = { Text("Question containing '?'") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("question_input_field"),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (testInputText.isNotBlank()) {
                                IconButton(onClick = { onTestInputChange("") }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
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
                            .testTag("trigger_test_ai_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini is generating replies...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detect & Generate AI Replies")
                        }
                    }

                    // Active Generated Replies Section
                    if (activeReplies.isNotEmpty()) {
                        Text(
                            text = "SUGGESTED REPLIES (Tap Copy to put on clipboard & remove):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        activeReplies.forEach { reply ->
                            ReplyCardItem(
                                reply = reply,
                                onCopy = { onCopyReply(reply) },
                                onDismiss = { onDismissReply(reply.id) }
                            )
                        }
                    } else if (currentQuestion != null && !isGenerating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1510B981))
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
    onUpdateLength: (ReplyLength) -> Unit,
    onUpdateCount: (Int) -> Unit,
    onUpdateTone: (ReplyTone) -> Unit
) {
    var apiKeyText by remember { mutableStateOf(settings.customApiKey) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Reply Length Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "REPLY LENGTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Choose the exact output format for Gemini replies:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ReplyLength.entries.forEach { lengthOption ->
                        val isSelected = settings.length == lengthOption
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                                )
                                .clickable { onUpdateLength(lengthOption) }
                                .padding(12.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lengthOption.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lengthOption.promptInstruction,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reply Count Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SUGGESTION COUNT (1 - 3)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val isSelected = settings.count == count
                            Button(
                                onClick = { onUpdateCount(count) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = "$count ${if (count == 1) "Reply" else "Replies"}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tone & Style Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CONVERSATION TONE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    ReplyTone.entries.forEach { tone ->
                        val isSelected = settings.tone == tone
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateTone(tone) }
                                .padding(vertical = 4.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = tone.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tone.promptInstruction,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-generation toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EXPIRY DURATION (1 - 10 MINS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
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
                                            tint = if (settings.autoDeleteMinutes > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${settings.autoDeleteMinutes} min${if (settings.autoDeleteMinutes > 1) "s" else ""}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
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
                                            tint = if (settings.autoDeleteMinutes < 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
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
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Detected questions and AI replies older than ${settings.autoDeleteMinutes} minute${if (settings.autoDeleteMinutes > 1) "s" else ""} will be deleted automatically.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Custom API Key Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GEMINI API CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "Default uses injected environment key. You can also specify an optional custom key below:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            onUpdateSettings(settings.copy(customApiKey = it))
                        },
                        label = { Text("Custom Gemini API Key (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        }
                    )
                }
            }
        }
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
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
                            tint = if (settings.autoDeleteHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    contentColor = MaterialTheme.colorScheme.error
                                )
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

                        Switch(
                            checked = settings.autoDeleteHistory,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(autoDeleteHistory = checked))
                            },
                            modifier = Modifier.size(height = 24.dp, width = 40.dp)
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ADJUST TIME (1 - 10 MINS):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
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
                                        tint = if (settings.autoDeleteMinutes > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${settings.autoDeleteMinutes}m",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
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
                                        tint = if (settings.autoDeleteMinutes < 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QuestionAnswer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
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
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.sourceApp,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                        if (remainingMs < 30_000) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                                                        tint = if (remainingMs < 30_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = "Expires in $countdownStr",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (remainingMs < 30_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            item.replies.forEach { reply ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                                            tint = MaterialTheme.colorScheme.primary,
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
