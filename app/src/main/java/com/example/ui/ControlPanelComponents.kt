package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.TextMuted

/**
 * Modifier that draws a soft outer glow shadow around active elements
 */
fun Modifier.crimsonGlow(
    radius: Dp = 8.dp,
    color: Color = CrimsonPrimary.copy(alpha = 0.35f),
    shapeRadius: Dp = 14.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(
            radius.toPx(),
            0f,
            0f,
            color.toArgb()
        )
        canvas.drawRoundRect(
            0f,
            0f,
            size.width,
            size.height,
            shapeRadius.toPx(),
            shapeRadius.toPx(),
            paint
        )
    }
}

/**
 * Sleek rounded pill switch with animated transitions and glowing active state
 */
@Composable
fun ControlPanelSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val thumbSize = 20.dp
    val padding = 3.dp

    val targetOffset = if (checked) trackWidth - thumbSize - padding else padding
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = 200),
        label = "switch_thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) CrimsonPrimary else Color(0xFF221614),
        animationSpec = tween(durationMillis = 200),
        label = "switch_track"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFFF5E6E) else Color(0xFF382320),
        animationSpec = tween(durationMillis = 200),
        label = "switch_border"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else Color(0xFF9E8E8B),
        animationSpec = tween(durationMillis = 200),
        label = "switch_thumb_color"
    )

    val glowModifier = if (checked) {
        Modifier.crimsonGlow(radius = 6.dp, color = CrimsonPrimary.copy(alpha = 0.4f), shapeRadius = 14.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(glowModifier)
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .size(thumbSize)
                .shadow(elevation = if (checked) 3.dp else 1.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

/**
 * Standard Control Panel Card with subtle border and optional active glowing border
 */
@Composable
fun ControlPanelCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    shapeRadius: Dp = 14.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(shapeRadius),
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val targetBorderColor by animateColorAsState(
        targetValue = if (isSelected) CrimsonPrimary else DarkCardBorder,
        animationSpec = tween(durationMillis = 200),
        label = "card_border"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) DarkCardElevated else DarkCard,
        animationSpec = tween(durationMillis = 200),
        label = "card_bg"
    )

    val glowModifier = if (isSelected) {
        Modifier.crimsonGlow(radius = 10.dp, color = CrimsonPrimary.copy(alpha = 0.28f), shapeRadius = shapeRadius)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .then(glowModifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = border ?: BorderStroke(if (isSelected) 1.5.dp else 1.dp, targetBorderColor)
    ) {
        content()
    }
}

/**
 * Section Header for Control Panel modules
 */
@Composable
fun ControlPanelSectionHeader(
    title: String,
    icon: ImageVector? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CrimsonPrimary.copy(alpha = 0.15f))
                        .border(1.dp, CrimsonPrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = CrimsonPrimary,
                letterSpacing = 0.8.sp
            )
        }

        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CrimsonPrimary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Monospace technical display value
 */
@Composable
fun MonospaceValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Medium
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color
    )
}
