package com.air.pong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.air.pong.core.game.GameEngine

/**
 * Momentum multiplier badge value that displays the fire icon and multiplier.
 * Visual effects escalate with multiplier level:
 * - 1.1×: Subtle orange glow
 * - 1.2×: Brighter glow
 * - 1.3×: Pulsing effect
 * - 1.5×: Rainbow shimmer
 * 
 * Visible when total multiplier > 1.0 (from momentum or spin).
 * The label "STREAK" should be provided by the parent layout.
 * 
 * Uses zero layout size so it doesn't push other elements - content overflows visually.
 * 
 * @param rallyLength Current rally length (used to calculate momentum multiplier)
 * @param spinMultiplier Additional spin multiplier bonus (0.0 to 0.5)
 * @param enabled Whether click interaction is enabled
 * @param onClick Optional callback when clicked
 */
@Composable
fun MomentumBadge(
    rallyLength: Int,
    spinMultiplier: Float = 0f,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val momentumMultiplier = GameEngine.getMomentumMultiplier(rallyLength)
    val multiplier = momentumMultiplier + spinMultiplier
    
    // Determine if badge should be visible - show when any bonus is active
    val shouldBeVisible = multiplier > 1.0f
    
    // Animate visibility with different timing for fade in vs fade out
    val visibilityAlpha by animateFloatAsState(
        targetValue = if (shouldBeVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (shouldBeVisible) 600 else 500,
            easing = LinearOutSlowInEasing
        ),
        label = "badgeVisibility"
    )
    
    // Determine visual intensity level based on multiplier thresholds
    // 1: invisible (handled by visibility check)
    // 1.1-1.5: white circle, white flame
    // 1.5-2: white circle, orange flame
    // 2-2.5: orange circle, orange flame
    // 2.5-3: rainbow circle, white flame
    // 3+: rainbow circle, rainbow flame
    val circleColorLevel = when {
        multiplier >= 3.0f -> 5  // Rainbow
        multiplier >= 2.5f -> 4  // Rainbow
        multiplier >= 2.0f -> 3  // Orange
        multiplier >= 1.5f -> 2  // White
        else -> 1                // White (1.1-1.5)
    }
    
    val flameColorLevel = when {
        multiplier >= 3.0f -> 5  // Rainbow
        multiplier >= 2.5f -> 2  // White
        multiplier >= 1.5f -> 3  // Orange
        else -> 1                // White (1.1-1.5)
    }
    
    // Infinite transition for animations
    val infiniteTransition = rememberInfiniteTransition(label = "momentum")
    
    // Glow alpha animation - pulsing/brightening effect
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Pulse scale animation - increases with multiplier
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            multiplier >= 2.5f -> 1.08f  // Strong pulse for rainbow
            multiplier >= 2.0f -> 1.06f  // Medium pulse
            multiplier >= 1.5f -> 1.04f  // Light pulse
            else -> 1.02f                // Subtle pulse
        },
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rainbow hue rotation (for rainbow effects)
    val hueRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow"
    )
    
    // Calculate circle color based on level
    val circleBaseColor = when (circleColorLevel) {
        5, 4 -> Color.hsv(hueRotation, 0.8f, 1f) // Rainbow for 2.5+
        3 -> Color(0xFFFF9100)                   // Orange for 2-2.5
        else -> Color.White                       // White for 1.1-2
    }
    
    // Calculate flame color based on level
    val flameColor = when (flameColorLevel) {
        5 -> Color.hsv(hueRotation, 0.8f, 1f)    // Rainbow for 3+
        3 -> Color(0xFFFF9100)                   // Orange for 1.5-2.5
        else -> Color.White                       // White for 1.1-1.5 and 2.5-3
    }
    
    val glowColor by animateColorAsState(
        targetValue = circleBaseColor.copy(alpha = glowAlpha),
        label = "glowColor"
    )
    
    // Format multiplier text - compact format
    val multiplierText = "×${String.format("%.1f", multiplier)}"
    
    // Zero-size container that allows content to overflow
    // Uses custom layout modifier to report zero size to parent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer(alpha = visibilityAlpha)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // Report zero size to parent layout
                layout(0, 0) {
                    // Place content centered at (0,0) - it overflows but doesn't affect layout
                    placeable.place(-placeable.width / 2, -placeable.height / 2)
                }
            }
    ) {
        // Touch target container with size
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(75.dp)
                .clip(CircleShape)
                .then(
                    if (enabled && onClick != null && shouldBeVisible) {
                        Modifier.clickable(onClick = onClick) 
                    } else {
                        Modifier
                    }
                )
        ) {
            // Glow circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale
                    )
                    .drawBehind {
                        drawCircle(
                            color = glowColor,
                            radius = size.width / 2
                        )
                    }
            )
            
            // Content: fire icon + multiplier
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak multiplier",
                    tint = flameColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = multiplierText,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

