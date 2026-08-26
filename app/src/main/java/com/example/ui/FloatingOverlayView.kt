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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DetectedQuestion
import com.example.model.ReplyItem
import com.example.model.ReplySettings
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

@Composable
fun FloatingOverlayView(
    context: Context,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClose: () -> Unit
) {
    val settings by AppStateManager.settings.collectAsStateWithLifecycle()
    val currentQuestion by AppStateManager.currentQuestion.collectAsStateWithLifecycle()
    val activeReplies by AppStateManager.activeReplies.collectAsStateWithLifecycle()
    val isGenerating by AppStateManager.isGenerating.collectAsStateWithLifecycle()
    val activeProvider by AppStateManager.activeProvider.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(true) }

    val hasContent = currentQuestion != null || activeReplies.isNotEmpty() || isGenerating

    Box(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 340.dp)
            .alpha(settings.overlayOpacity)
            .clip(RoundedCornerShape(settings.overlayCornerRadius.dp))
            .background(DarkBg.copy(alpha = 0.95f))
            .border(
                1.5.dp,
                if (hasContent) CrimsonPrimary.copy(alpha = 0.8f) else DarkCardBorder,
                RoundedCornerShape(settings.overlayCornerRadius.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Drag Handle & Header
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
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag overlay",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (hasContent) AccentGreen else CrimsonPrimary)
                    )

                    val providerLabel = currentQuestion?.generatedByProvider?.displayName?.split(" ")?.first()
                        ?: activeProvider?.displayName?.split(" ")?.first()
                        ?: settings.preferredProvider.displayName.split(" ").first()

                    Text(
                        text = "ReplyFloat",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasContent) TextWhite else TextMuted
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CrimsonPrimary.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, CrimsonPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "via $providerLabel",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = CrimsonLight,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasContent) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle expand",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = CrimsonLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Content Body
            if (isExpanded) {
                if (isGenerating) {
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
                            text = "Synthesizing...",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                } else if (currentQuestion != null) {
                    // Question Preview & Meaning
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentQuestion?.text ?: "",
                            fontSize = (settings.overlayTextSizeSp - 1).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            maxLines = 2
                        )

                        if (currentQuestion?.fallbackNotice != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TechBlue.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, TechBlue.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = currentQuestion?.fallbackNotice ?: "",
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TechBlue,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        if (settings.understandingMode && currentQuestion?.englishMeaning != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CrimsonLight,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = currentQuestion?.englishMeaning ?: "",
                                    fontSize = 10.sp,
                                    color = CrimsonLight,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Replies List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeReplies.forEachIndexed { index, reply ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        AppStateManager.copyAndDismissReply(context, reply)
                                    },
                                shape = RoundedCornerShape(6.dp),
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
                                        fontSize = settings.overlayTextSizeSp.sp,
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
                    }
                } else {
                    Text(
                        text = "Listening for inbound questions...",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
