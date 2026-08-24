package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AiProvider
import com.example.model.DiagnosticStatus
import com.example.model.ReplyItem
import com.example.model.ReplyLength
import com.example.model.SystemHealthState
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
    val activeProvider by AppStateManager.activeProvider.collectAsState()
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
                activeProvider = activeProvider,
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
                onClose = { AppStateManager.closeMainBar() },
                onDragDelta = onDragDelta
            )
        } else {
            CollapsedFloatingBar(
                hasQuestion = currentQuestion != null,
                questionSnippet = currentQuestion?.text,
                repliesCount = activeReplies.size,
                activeProvider = activeProvider,
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
    activeProvider: com.example.model.AiProvider? = null,
    isGenerating: Boolean,
    errorMessage: String? = null,
    onExpand: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("collapsed_floating_bar")
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onExpand() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xD0120B0B),
        border = BorderStroke(
            1.dp,
            when {
                errorMessage != null -> Color(0xFFEF4444).copy(alpha = 0.8f)
                isGenerating -> Color(0xFF38BDF8).copy(alpha = 0.8f)
                hasQuestion -> Color(0xFFDC2626).copy(alpha = 0.8f)
                else -> Color.White.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Logo / Progress / Error
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.dp
                )
            } else if (errorMessage != null) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "ReplyFloat AI",
                    tint = if (hasQuestion) Color(0xFFFF6B6B) else Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Compact clean AI label / status
            Text(
                text = when {
                    isGenerating -> "Thinking..."
                    errorMessage != null -> "Error"
                    hasQuestion && repliesCount > 0 -> "$repliesCount Replies"
                    hasQuestion -> "Question"
                    else -> "ReplyFloat AI"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Replies indicator dot if available and not generating
            if (repliesCount > 0 && !isGenerating && errorMessage == null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
        }
    }
}

@Composable
fun ExpandedOverlayBar(
    currentQuestion: String?,
    englishMeaning: String? = null,
    sourceApp: String?,
    replies: List<ReplyItem>,
    activeProvider: com.example.model.AiProvider? = null,
    isGenerating: Boolean,
    errorMessage: String? = null,
    healthState: SystemHealthState? = null,
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
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF51A0808),
        border = BorderStroke(1.2.dp, Color(0xFFDC2626).copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Bar
            OverlayHeader(
                activeProvider = activeProvider,
                onCollapse = onCollapse,
                onClose = onClose,
                onDragDelta = onDragDelta
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Question / Translation display
            OverlayQuestionSection(
                currentQuestion = currentQuestion,
                englishMeaning = englishMeaning,
                multiLanguageEnabled = multiLanguageEnabled,
                isGenerating = isGenerating,
                onRegenerate = onRegenerate
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Length Selector
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF8B1515) else Color(0x33441818),
                        modifier = Modifier.clickable { onLengthSelected(len) }
                    ) {
                        Text(
                            text = len.label,
                            color = if (isSelected) Color.White else Color(0xFFD4C8C8),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Count Selector & Quick Action Toggles
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFFB91C1C) else Color(0x33441818),
                            modifier = Modifier.clickable { onCountSelected(count) }
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) Color.White else Color(0xFFD4C8C8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Compact Quick Toggles: Lang & Analyze
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Language Toggle
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (multiLanguageEnabled) Color(0xFF0284C7) else Color(0x33441818),
                        border = BorderStroke(
                            0.8.dp,
                            if (multiLanguageEnabled) Color(0xFF38BDF8) else Color(0x44666666)
                        ),
                        modifier = Modifier
                            .clickable { onToggleMultiLanguage() }
                            .testTag("lang_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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

                    // Analyze Toggle
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (scanningEnabled) Color(0xFF15803D) else Color(0xFF4B5563),
                        border = BorderStroke(
                            0.8.dp,
                            if (scanningEnabled) Color(0xFF4ADE80) else Color(0x449CA3AF)
                        ),
                        modifier = Modifier
                            .clickable { onToggleScanning() }
                            .testTag("analyze_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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

            // Auto-Delete History Timer Adjuster
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
                        // Decrement
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    if (autoDeleteMinutes > 1) {
                                        onAutoDeleteMinutesChanged(autoDeleteMinutes - 1)
                                    }
                                },
                            shape = CircleShape,
                            color = Color(0x33441818)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease delete time",
                                    tint = if (autoDeleteMinutes > 1) Color.White else Color(0xFF665555),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Time display
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF8B1515)
                        ) {
                            Text(
                                text = "${autoDeleteMinutes}m",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Increment
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    if (autoDeleteMinutes < 10) {
                                        onAutoDeleteMinutesChanged(autoDeleteMinutes + 1)
                                    }
                                },
                            shape = CircleShape,
                            color = Color(0x33441818)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase delete time",
                                    tint = if (autoDeleteMinutes < 10) Color.White else Color(0xFF665555),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
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
            OverlayRepliesSection(
                replies = replies,
                activeProvider = activeProvider,
                isGenerating = isGenerating,
                errorMessage = errorMessage,
                hasQuestion = !currentQuestion.isNullOrBlank(),
                multiLanguageEnabled = multiLanguageEnabled,
                onRegenerate = onRegenerate,
                onCopyReply = onCopyReply,
                onDismissReply = onDismissReply
            )
        }
    }
}

@Composable
private fun OverlayHeader(
    activeProvider: com.example.model.AiProvider? = null,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragDelta: (Float, Float) -> Unit
) {
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
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF080C14)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "ReplyFloat AI",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "ReplyFloatAi",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (activeProvider != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDC2626).copy(alpha = 0.3f),
                    border = BorderStroke(0.6.dp, Color(0xFFFF8A8A).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "via ${activeProvider.displayName}",
                        color = Color(0xFFFFD4D4),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Minimize button (Collapses main bar back to small bar)
            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onCollapse() }
                    .testTag("collapse_overlay_button"),
                shape = CircleShape,
                color = Color(0x33FFFFFF),
                border = BorderStroke(0.8.dp, Color(0x44FFFFFF))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Minimize to small bar",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Close / X button (Fully closes/dismisses the main bar from screen)
            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .testTag("close_overlay_button"),
                shape = CircleShape,
                color = Color(0x33EF4444),
                border = BorderStroke(1.dp, Color(0x99EF4444))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close and dismiss window",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayQuestionSection(
    currentQuestion: String?,
    englishMeaning: String?,
    multiLanguageEnabled: Boolean,
    isGenerating: Boolean,
    onRegenerate: () -> Unit
) {
    if (multiLanguageEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Original
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0x333A1818),
                border = BorderStroke(0.8.dp, Color(0x44DC2626))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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

            // Meaning (English)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0x280284C7),
                border = BorderStroke(0.8.dp, Color(0x4438BDF8))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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
                        text = when {
                            !englishMeaning.isNullOrBlank() -> englishMeaning
                            isGenerating -> "Translating meaning with Gemini..."
                            !currentQuestion.isNullOrBlank() -> "Translating..."
                            else -> "English translation will appear here"
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
        // Standard single question box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0x333A1818),
            border = BorderStroke(0.8.dp, Color(0x44DC2626))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
}

@Composable
private fun OverlayRepliesSection(
    replies: List<ReplyItem>,
    activeProvider: com.example.model.AiProvider? = null,
    isGenerating: Boolean,
    errorMessage: String?,
    hasQuestion: Boolean,
    multiLanguageEnabled: Boolean,
    onRegenerate: () -> Unit,
    onCopyReply: (ReplyItem) -> Unit,
    onDismissReply: (String) -> Unit
) {
    if (isGenerating) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0x333A1818)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = Color(0xFFFF5757),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = "Generating replies across providers...",
                    color = Color(0xFFFEE2E2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else if (errorMessage != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRegenerate() },
            shape = RoundedCornerShape(12.dp),
            color = Color(0x443F1212),
            border = BorderStroke(1.dp, Color(0xFFEF4444))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                        text = "Generation Unavailable",
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
                    maxLines = 3,
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x223A1818)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (hasQuestion) "All replies copied!" else "No active question",
                    color = Color(0xFFD4C8C8),
                    fontSize = 11.sp
                )
                if (hasQuestion) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (multiLanguageEnabled) "REPLY (in original language):" else "SUGGESTED REPLIES:",
                    color = Color(0xFFD4C8C8),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                if (activeProvider != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.25f),
                        border = BorderStroke(0.5.dp, Color(0xFFFF8A8A).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "via ${activeProvider.displayName}",
                            color = Color(0xFFFFC0C0),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Isolate each card with a stable key so only the targeted card changes upon copy/dismiss
            replies.forEach { reply ->
                key(reply.id) {
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

@Composable
fun ReplyCardItem(
    reply: ReplyItem,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reply_card_${reply.id}"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0x332D1212),
        border = BorderStroke(0.8.dp, Color(0x44DC2626))
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
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

                // Copy Button
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
