package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReplyItem
import com.example.model.ReplyLength
import com.example.state.AppStateManager

@Composable
fun FloatingOverlayContent(
    modifier: Modifier = Modifier,
    onDragDelta: (Float, Float) -> Unit = { _, _ -> },
    onCloseService: () -> Unit = {}
) {
    val context = LocalContext.current
    val isExpanded by AppStateManager.isOverlayExpanded.collectAsState()
    val currentQuestion by AppStateManager.currentQuestion.collectAsState()
    val activeReplies by AppStateManager.activeReplies.collectAsState()
    val isGenerating by AppStateManager.isGenerating.collectAsState()
    val errorMessage by AppStateManager.errorMessage.collectAsState()
    val settings by AppStateManager.settings.collectAsState()

    AnimatedContent(
        targetState = isExpanded,
        label = "OverlayExpandAnimation",
        modifier = modifier
    ) { expanded ->
        if (expanded) {
            ExpandedOverlayBar(
                currentQuestion = currentQuestion?.text,
                englishMeaning = currentQuestion?.englishMeaning,
                sourceApp = currentQuestion?.sourceApp,
                replies = activeReplies,
                isGenerating = isGenerating,
                errorMessage = errorMessage,
                selectedLength = settings.length,
                selectedCount = settings.count,
                autoDeleteEnabled = settings.autoDeleteHistory,
                autoDeleteMinutes = settings.autoDeleteMinutes,
                multiLanguageEnabled = settings.multiLanguageEnabled,
                scanningEnabled = settings.scanningEnabled,
                onLengthSelected = { AppStateManager.updateReplyLength(it) },
                onCountSelected = { AppStateManager.updateReplyCount(it) },
                onToggleMultiLanguage = { AppStateManager.toggleMultiLanguage() },
                onToggleScanning = { AppStateManager.toggleScanning() },
                onAutoDeleteMinutesChanged = { mins ->
                    AppStateManager.updateAutoDeleteSettings(settings.autoDeleteHistory, mins)
                },
                onRegenerate = { AppStateManager.generateRepliesForQuestion() },
                onCopyReply = { reply -> AppStateManager.copyAndDismissReply(context, reply) },
                onDismissReply = { replyId -> AppStateManager.dismissReply(replyId) },
                onCollapse = { AppStateManager.setOverlayExpanded(false) },
                onClose = onCloseService,
                onDragDelta = onDragDelta
            )
        } else {
            CollapsedFloatingBar(
                hasQuestion = currentQuestion != null,
                questionSnippet = currentQuestion?.text,
                repliesCount = activeReplies.size,
                isGenerating = isGenerating,
                errorMessage = errorMessage,
                onExpand = { AppStateManager.setOverlayExpanded(true) },
                onDragDelta = onDragDelta
            )
        }
    }
}

@Composable
fun CollapsedFloatingBar(
    hasQuestion: Boolean,
    questionSnippet: String?,
    repliesCount: Int,
    isGenerating: Boolean,
    errorMessage: String? = null,
    onExpand: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier
            .testTag("collapsed_floating_bar")
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = if (errorMessage != null) {
                        listOf(Color(0xFFEF4444), Color(0xFFF59E0B))
                    } else {
                        listOf(Color(0xFFDC2626), Color(0xFFFF5757))
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF0180B0B))
            .clickable { onExpand() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag handle icon
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to move",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )

            // Sparkling AI Icon / Progress / Error
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (errorMessage != null) {
                                listOf(Color(0xFFB91C1C), Color(0xFFEF4444))
                            } else {
                                listOf(Color(0xFF8B1515), Color(0xFFFF5757))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else if (errorMessage != null) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Quick Reply",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Info text / Question badge
            Column(
                modifier = Modifier.widthIn(max = 140.dp)
            ) {
                Text(
                    text = "replyFloatAi",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isGenerating) {
                        "Thinking..."
                    } else if (errorMessage != null) {
                        "Error, tap to retry"
                    } else if (hasQuestion && !questionSnippet.isNullOrBlank()) {
                        questionSnippet
                    } else {
                        "Waiting for '?'..."
                    },
                    color = if (errorMessage != null) Color(0xFFFCA5A5) else if (hasQuestion) Color(0xFFFF8A8A) else Color(0xFFD4C8C8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Replies badge if available and not generating
            if (repliesCount > 0 && !isGenerating && errorMessage == null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$repliesCount replies",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expand icon
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand overlay",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandedOverlayBar(
    currentQuestion: String?,
    englishMeaning: String? = null,
    sourceApp: String?,
    replies: List<ReplyItem>,
    isGenerating: Boolean,
    errorMessage: String? = null,
    selectedLength: ReplyLength,
    selectedCount: Int,
    autoDeleteEnabled: Boolean = true,
    autoDeleteMinutes: Int = 5,
    multiLanguageEnabled: Boolean = false,
    scanningEnabled: Boolean = true,
    onLengthSelected: (ReplyLength) -> Unit,
    onCountSelected: (Int) -> Unit,
    onToggleMultiLanguage: () -> Unit = {},
    onToggleScanning: () -> Unit = {},
    onAutoDeleteMinutesChanged: (Int) -> Unit = {},
    onRegenerate: () -> Unit,
    onCopyReply: (ReplyItem) -> Unit,
    onDismissReply: (String) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("expanded_overlay_bar")
            .widthIn(min = 300.dp, max = 380.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFDC2626).copy(alpha = 0.7f),
                        Color(0xFFFF5757).copy(alpha = 0.7f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF51E0A0A)), // Deep crimson slate transparent overlay
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
        ) {
            // Drag & Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag overlay",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "replyFloatAi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(28.dp).testTag("collapse_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp).testTag("close_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detected Question Box (or Stacked Original + Meaning when Lang is ON)
            if (multiLanguageEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Original
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x333A1818))
                            .border(0.8.dp, Color(0x44DC2626), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A8A),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "ORIGINAL:",
                                        color = Color(0xFFFF8A8A),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                if (!currentQuestion.isNullOrBlank()) {
                                    IconButton(
                                        onClick = onRegenerate,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Regenerate",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (!currentQuestion.isNullOrBlank()) {
                                    currentQuestion
                                } else {
                                    "No question detected on screen yet"
                                },
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Meaning (English)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x280284C7))
                            .border(0.8.dp, Color(0x4438BDF8), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "MEANING (ENGLISH):",
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = if (!englishMeaning.isNullOrBlank()) {
                                    englishMeaning
                                } else if (isGenerating) {
                                    "Translating meaning with Gemini..."
                                } else if (!currentQuestion.isNullOrBlank()) {
                                    "Translating..."
                                } else {
                                    "English translation will appear here"
                                },
                                color = Color(0xFFE0F2FE),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // When Lang mode is OFF: keep current single-question display as-is
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x333A1818))
                        .border(0.8.dp, Color(0x44DC2626), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QuestionMark,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8A8A),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "DETECTED QUESTION",
                                    color = Color(0xFFFF8A8A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            if (!currentQuestion.isNullOrBlank()) {
                                IconButton(
                                    onClick = onRegenerate,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (!currentQuestion.isNullOrBlank()) {
                                currentQuestion
                            } else {
                                "No '?' detected on screen yet. Type a question or test in the main app."
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reply Length Selector (1 word, short, 1 line, 2 lines, 5-7 lines)
            Text(
                text = "LENGTH:",
                color = Color(0xFFD4C8C8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReplyLength.entries.forEach { len ->
                    val isSelected = selectedLength == len
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF8B1515) else Color(0x33441818)
                            )
                            .clickable { onLengthSelected(len) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = len.label,
                            color = if (isSelected) Color.White else Color(0xFFD4C8C8),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reply Count Selector (1, 2, 3) + Lang Toggle & Analyze Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "COUNT:",
                        color = Color(0xFFD4C8C8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    listOf(1, 2, 3).forEach { count ->
                        val isSelected = selectedCount == count
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) Color(0xFFB91C1C) else Color(0x33441818)
                                )
                                .clickable { onCountSelected(count) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) Color.White else Color(0xFFD4C8C8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Compact Quick Toggles: Lang & Analyze
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Language Toggle Button ("Lang: ON/OFF")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (multiLanguageEnabled) Color(0xFF0284C7) else Color(0x33441818)
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (multiLanguageEnabled) Color(0xFF38BDF8) else Color(0x44666666),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onToggleMultiLanguage() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("lang_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = if (multiLanguageEnabled) Color.White else Color(0xFFB0A8A8),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (multiLanguageEnabled) "Lang: ON" else "Lang: OFF",
                                color = if (multiLanguageEnabled) Color.White else Color(0xFFB0A8A8),
                                fontSize = 9.5.sp,
                                fontWeight = if (multiLanguageEnabled) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // Analyze On/Off Toggle Button (Green when active, Gray when paused)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (scanningEnabled) Color(0xFF15803D) else Color(0xFF4B5563)
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (scanningEnabled) Color(0xFF4ADE80) else Color(0x449CA3AF),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onToggleScanning() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("analyze_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (scanningEnabled) Icons.Default.Radar else Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (scanningEnabled) "Analyze: ON" else "Analyze: OFF",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Auto-Delete History Timer Adjuster (1-10 mins)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (autoDeleteEnabled) Color(0xFFFF5757) else Color(0xFF887777),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "AUTO-DELETE:",
                        color = Color(0xFFD4C8C8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (autoDeleteEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Decrement button (-)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x33441818))
                                .clickable {
                                    if (autoDeleteMinutes > 1) {
                                        onAutoDeleteMinutesChanged(autoDeleteMinutes - 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease delete time",
                                tint = if (autoDeleteMinutes > 1) Color.White else Color(0xFF665555),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        // Current minutes display
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF8B1515))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${autoDeleteMinutes}m",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Increment button (+)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x33441818))
                                .clickable {
                                    if (autoDeleteMinutes < 10) {
                                        onAutoDeleteMinutesChanged(autoDeleteMinutes + 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase delete time",
                                tint = if (autoDeleteMinutes < 10) Color.White else Color(0xFF665555),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Off",
                        color = Color(0xFF887777),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Replies Section
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x333A1818))
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color(0xFFFF5757),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Generating replies with Gemini...",
                            color = Color(0xFFFEE2E2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (errorMessage != null) {
                // Prominent Error State with Tap to Retry
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .background(Color(0x443F1212))
                        .clickable { onRegenerate() }
                        .padding(12.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Couldn't generate reply",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = errorMessage,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Button(
                            onClick = onRegenerate,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("retry_generation_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tap to Retry",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (replies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x223A1818))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (currentQuestion.isNullOrBlank()) "No active question" else "All replies copied!",
                            color = Color(0xFFD4C8C8),
                            fontSize = 11.sp
                        )
                        if (!currentQuestion.isNullOrBlank()) {
                            TextButton(
                                onClick = onRegenerate,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Generate More",
                                    color = Color(0xFFFF5757),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (multiLanguageEnabled) {
                        Text(
                            text = "REPLY (in original language):",
                            color = Color(0xFFD4C8C8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    replies.forEach { reply ->
                        ReplyCardItem(
                            reply = reply,
                            onCopy = { onCopyReply(reply) },
                            onDismiss = { onDismissReply(reply.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyCardItem(
    reply: ReplyItem,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("reply_card_${reply.id}")
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x44DC2626), RoundedCornerShape(12.dp))
                .background(Color(0x442D1212)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
            ) {
                Text(
                    text = reply.text,
                    color = Color(0xFFFEE2E2),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dismiss X
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFD4C8C8),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Copy Button (Tapping copy puts text on clipboard and removes card)
                    Button(
                        onClick = onCopy,
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("copy_reply_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B1515),
                            contentColor = Color.White
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
