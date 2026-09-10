package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiFallbackEngine
import com.example.ai.QuestionDetectionEngine
import com.example.model.DetectedQuestion
import com.example.model.ReplyItem
import com.example.model.ReplySettings
import com.example.model.ResponseLengthPreset
import com.example.state.AppStateManager
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
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

enum class OverlayBarMode {
    SMALL_PILL, // Collapsed floating pill
    MAIN_BAR    // Expanded bar with question, controls, and reply cards
}

@Composable
fun FloatingOverlayView(
    context: Context,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onClose: () -> Unit
) {
    val settings by AppStateManager.settings.collectAsStateWithLifecycle()
    val currentQuestion by AppStateManager.currentQuestion.collectAsStateWithLifecycle()
    val activeReplies by AppStateManager.activeReplies.collectAsStateWithLifecycle()
    val isGenerating by AppStateManager.isGenerating.collectAsStateWithLifecycle()
    val activeProvider by AppStateManager.activeProvider.collectAsStateWithLifecycle()

    var currentMode by remember { mutableStateOf(OverlayBarMode.MAIN_BAR) }

    val hasContent = remember(currentQuestion, activeReplies, isGenerating) {
        currentQuestion != null || activeReplies.isNotEmpty() || isGenerating
    }

    val cornerRadius = remember(currentMode, settings.overlayCornerRadius) {
        if (currentMode == OverlayBarMode.SMALL_PILL) 24.dp else settings.overlayCornerRadius.dp
    }
    val containerShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val borderColor = remember(hasContent) {
        if (hasContent) CrimsonPrimary.copy(alpha = 0.9f) else DarkCardBorder
    }

    val currentOpacity = when (currentMode) {
        OverlayBarMode.SMALL_PILL -> settings.smallBarOpacity
        OverlayBarMode.MAIN_BAR -> settings.mainBarOpacity
    }.coerceIn(0.20f, 1.0f)

    // Main Floating Container - styled with independent bar opacity
    Box(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 350.dp)
            .graphicsLayer(alpha = currentOpacity)
            .clip(containerShape)
            .background(DarkBg.copy(alpha = currentOpacity))
            .border(
                1.5.dp,
                borderColor.copy(alpha = (currentOpacity * 0.95f).coerceIn(0.25f, 1.0f)),
                containerShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .padding(if (currentMode == OverlayBarMode.SMALL_PILL) 6.dp else 10.dp)
    ) {
        when (currentMode) {
            OverlayBarMode.SMALL_PILL -> {
                SmallBarPill(
                    settings = settings,
                    hasContent = hasContent,
                    isGenerating = isGenerating,
                    currentQuestion = currentQuestion,
                    onExpandMain = { currentMode = OverlayBarMode.MAIN_BAR },
                    onClose = onClose
                )
            }
            OverlayBarMode.MAIN_BAR -> {
                MainBarExpanded(
                    context = context,
                    settings = settings,
                    currentQuestion = currentQuestion,
                    activeReplies = activeReplies,
                    isGenerating = isGenerating,
                    activeProvider = activeProvider,
                    onCollapse = { currentMode = OverlayBarMode.SMALL_PILL },
                    onClose = onClose
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Small Bar — Collapsed/Minimized Floating Pill
// -------------------------------------------------------------
@Composable
private fun SmallBarPill(
    settings: ReplySettings,
    hasContent: Boolean,
    isGenerating: Boolean,
    currentQuestion: DetectedQuestion?,
    onExpandMain: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onExpandMain() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag pill",
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )

        // Pulsing Status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isGenerating -> TechBlue
                        hasContent -> AccentGreen
                        else -> CrimsonPrimary
                    }
                )
        )

        Text(
            text = "ReplyFloat",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (hasContent) TextWhite else TextSecondary
        )

        if (currentQuestion != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CrimsonPrimary.copy(alpha = 0.25f),
                border = BorderStroke(0.5.dp, CrimsonPrimary)
            ) {
                Text(
                    text = "1 Q",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonLight,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        // Quick Analyze Toggle
        IconButton(
            onClick = {
                AppStateManager.setContinuousScreenAnalysis(!settings.continuousScreenAnalysis)
            },
            modifier = Modifier
                .size(20.dp)
                .testTag("small_pill_quick_analyze_toggle")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ScreenSearchDesktop,
                    contentDescription = if (settings.continuousScreenAnalysis) "Analyze ON (Tap to pause)" else "Analyze OFF (Tap to resume)",
                    tint = if (settings.continuousScreenAnalysis) TechGreen else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                if (settings.continuousScreenAnalysis) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(TechGreen)
                    )
                }
            }
        }

        IconButton(
            onClick = onExpandMain,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Expand to Main Bar",
                tint = TextWhite,
                modifier = Modifier.size(12.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close overlay",
                tint = CrimsonLight,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 2. Main Bar — Expanded Bar with Detected Question & Replies
// -------------------------------------------------------------
@Composable
private fun MainBarExpanded(
    context: Context,
    settings: ReplySettings,
    currentQuestion: DetectedQuestion?,
    activeReplies: List<ReplyItem>,
    isGenerating: Boolean,
    activeProvider: com.example.model.AiProvider?,
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    val hasContent = currentQuestion != null || activeReplies.isNotEmpty() || isGenerating

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // Main Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag overlay",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (hasContent) AccentGreen else CrimsonPrimary)
                )

                Text(
                    text = "ReplyFloat",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasContent) TextWhite else TextMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Quick Analyze Toggle Button
                IconButton(
                    onClick = {
                        AppStateManager.setContinuousScreenAnalysis(!settings.continuousScreenAnalysis)
                    },
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("quick_analyze_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ScreenSearchDesktop,
                            contentDescription = if (settings.continuousScreenAnalysis) "Analyze ON (Tap to pause)" else "Analyze OFF (Tap to resume)",
                            tint = if (settings.continuousScreenAnalysis) TechGreen else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        if (settings.continuousScreenAnalysis) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(TechGreen)
                            )
                        }
                    }
                }

                // Collapse to Small Bar
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "Minimize to Pill",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Close overlay
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close overlay",
                        tint = CrimsonLight,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Expanded Body
        if (isGenerating) {
            GeneratingProgressIndicator()
        } else if (currentQuestion != null) {
            // Detected Question card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = currentQuestion.text,
                    fontSize = (settings.overlayTextSizeSp - 1).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick In-Bar Length & Count Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Length preset selector
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ResponseLengthPreset.entries.forEach { preset ->
                        val isSelected = settings.responseLengthPreset == preset
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    AppStateManager.setResponseLengthPreset(preset)
                                    // Re-trigger generation with updated preset
                                    AppStateManager.onQuestionDetected(
                                        context = context,
                                        text = currentQuestion.text,
                                        sourceApp = currentQuestion.sourceApp,
                                        packageName = currentQuestion.packageName,
                                        forcedBypass = true
                                    )
                                },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) CrimsonPrimary else DarkSurfaceCard,
                            border = BorderStroke(0.5.dp, if (isSelected) CrimsonLight else DarkCardBorder)
                        ) {
                            Text(
                                text = preset.shortLabel,
                                fontSize = 8.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextWhite else TextSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Reply Count pills (1, 2, 3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2, 3).forEach { count ->
                        val isSelected = settings.count == count
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    AppStateManager.updateReplyCount(count)
                                    AppStateManager.onQuestionDetected(
                                        context = context,
                                        text = currentQuestion.text,
                                        sourceApp = currentQuestion.sourceApp,
                                        packageName = currentQuestion.packageName,
                                        forcedBypass = true
                                    )
                                },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) TechBlue else DarkSurfaceCard,
                            border = BorderStroke(0.5.dp, if (isSelected) TechBlue else DarkCardBorder)
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 8.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextWhite else TextSecondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Reply Cards (remain in Main Bar with copy buttons, not duplicated in Lang bar)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeReplies.forEach { reply ->
                    androidx.compose.runtime.key(reply.id) {
                        ReplyCardItem(
                            reply = reply,
                            textSizeSp = settings.overlayTextSizeSp,
                            onClick = {
                                AppStateManager.copyAndDismissReply(context, reply)
                            }
                        )
                    }
                }
            }

            // Quick Actions Footer with solid opaque background, clear contrast and spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Solid Re-generate Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            AppStateManager.onQuestionDetected(
                                context = context,
                                text = currentQuestion.text,
                                sourceApp = currentQuestion.sourceApp,
                                packageName = currentQuestion.packageName,
                                forcedBypass = true
                            )
                        },
                    shape = RoundedCornerShape(6.dp),
                    color = DarkCardElevated,
                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = CrimsonLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Re-generate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CrimsonLight
                        )
                    }
                }

                // Solid Dismiss Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            AppStateManager.clearReplies()
                        },
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Dismiss",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextWhite
                        )
                    }
                }
            }
        } else {
            // Idle State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenSearchDesktop,
                        contentDescription = null,
                        tint = TechGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (settings.continuousScreenAnalysis) "Listening for inbound questions..." else "Analysis is paused",
                        fontSize = 10.sp,
                        color = if (settings.continuousScreenAnalysis) TextSecondary else TextMuted
                    )
                }
            }
        }
    }
}



@Composable
private fun GeneratingProgressIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = CrimsonPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Synthesizing AI replies...",
            fontSize = 11.5.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun ReplyCardItem(
    reply: ReplyItem,
    textSizeSp: Int,
    onClick: () -> Unit
) {
    val cardShape = remember { RoundedCornerShape(6.dp) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        color = DarkCardElevated,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = reply.text,
                fontSize = textSizeSp.sp,
                color = TextWhite,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = TechBlue,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

