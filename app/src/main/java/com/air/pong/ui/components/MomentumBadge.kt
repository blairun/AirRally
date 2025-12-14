package com.air.pong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Only visible when multiplier > 1.0 (rally length >= 10).
 * The label "STREAK" should be provided by the parent layout.
 * 
 * Uses zero layout size so it doesn't push other elements - content overflows visually.
 * 
 * @param rallyLength Current rally length (used to calculate multiplier)
 */
@Composable
fun MomentumBadge(
    rallyLength: Int,
    modifier: Modifier = Modifier
) {
    val multiplier = GameEngine.getMomentumMultiplier(rallyLength)
    
    // Determine if badge should be visible
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
    
    // Determine visual intensity level (matches getMomentumMultiplier tiers)
    val intensityLevel = when {
        multiplier >= 1.5f -> 5 // Rainbow shimmer (50+ rally)
        multiplier >= 1.4f -> 4 // Strong pulse (40-49 rally)
        multiplier >= 1.3f -> 3 // Pulse (30-39 rally)
        multiplier >= 1.2f -> 2 // Brighter glow (20-29 rally)
        else -> 1 // Subtle glow (10-19 rally, 1.1×)
    }
    
    // Infinite transition for animations
    val infiniteTransition = rememberInfiniteTransition(label = "momentum")
    
    // Glow alpha animation (levels 1-2)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (intensityLevel >= 2) 0.5f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Pulse scale animation (level 3+ = 1.3× and higher)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            intensityLevel >= 4 -> 1.08f  // Strong pulse for 1.4×+
            intensityLevel >= 3 -> 1.05f  // Normal pulse for 1.3×
            else -> 1f                     // No pulse for 1.1×-1.2×
        },
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rainbow hue rotation (level 5 = 1.5×)
    val hueRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow"
    )
    
    // Calculate colors based on intensity
    val baseColor = when (intensityLevel) {
        5 -> Color.hsv(hueRotation, 0.8f, 1f) // Rainbow for 1.5×
        4 -> Color(0xFFFF5722) // Red-orange for 1.4×
        3 -> Color(0xFFFF6D00) // Deep orange for 1.3×
        2 -> Color(0xFFFF9100) // Bright orange for 1.2×
        else -> Color(0xFFFFAB40) // Subtle orange for 1.1×
    }
    
    val glowColor by animateColorAsState(
        targetValue = baseColor.copy(alpha = glowAlpha),
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
        // Glow circle - sized to match avatar (100.dp diameter)
        Box(
            modifier = Modifier
                .size(75.dp)
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
                tint = baseColor,
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

