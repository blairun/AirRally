package com.air.pong.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A pulsing grid/hash pattern overlay that creates a "force field" shield effect.
 * The overlay is semi-transparent so the avatar is visible underneath.
 * 
 * Features:
 * - Grid/hash pattern of intersecting lines in a circular mask
 * - Pulsing animation alternating between blue and white
 * - Subtle pulse in opacity for a "breathing" effect
 * - Can rotate with avatar when spinning
 * 
 * @param size Total diameter of the shield overlay
 * @param rotation Current rotation in degrees (to sync with avatar spin)
 * @param modifier Additional modifiers
 */
@Composable
fun ShieldOverlay(
    size: Dp,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Animation for pulsing color between blue and white
    val pulseTransition = rememberInfiniteTransition(label = "shieldPulse")
    
    // Color pulse: blue <-> white over 1.5 seconds
    val colorProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldColorPulse"
    )
    
    // Opacity pulse: creates a "breathing" effect (0.5 to 0.8)
    val opacityProgress by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldOpacityPulse"
    )
    
    // Calculate current color (blue to white)
    val shieldBlue = Color(0xFF64B5F6)
    val shieldWhite = Color.White
    val currentColor = lerp(shieldBlue, shieldWhite, colorProgress)
    
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val centerX = size.toPx() / 2f
        val centerY = size.toPx() / 2f
        val radius = min(centerX, centerY) - 4.dp.toPx() // Small padding from edge
        
        // Create circular clip path
        val circlePath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius
                )
            )
        }
        
        // Draw within circular mask
        clipPath(circlePath) {
            // Draw grid pattern
            drawGridPattern(
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                color = currentColor.copy(alpha = opacityProgress),
                lineSpacing = 15.dp.toPx()
            )
        }
        
        // Draw outer ring (slightly brighter)
        drawCircle(
            color = currentColor.copy(alpha = opacityProgress + 0.15f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw inner glow ring
        drawCircle(
            color = currentColor.copy(alpha = opacityProgress * 0.5f),
            radius = radius - 6.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Draws a hash/grid pattern of intersecting diagonal and horizontal lines.
 */
private fun DrawScope.drawGridPattern(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    lineSpacing: Float
) {
    val strokeWidth = 2.dp.toPx()
    val extent = radius * 1.5f // Extend beyond circle to ensure full coverage after clipping
    
    // Horizontal lines
    var y = centerY - extent
    while (y <= centerY + extent) {
        drawLine(
            color = color,
            start = Offset(centerX - extent, y),
            end = Offset(centerX + extent, y),
            strokeWidth = strokeWidth
        )
        y += lineSpacing
    }
    
    // Vertical lines
    var x = centerX - extent
    while (x <= centerX + extent) {
        drawLine(
            color = color,
            start = Offset(x, centerY - extent),
            end = Offset(x, centerY + extent),
            strokeWidth = strokeWidth
        )
        x += lineSpacing
    }
    
    // Diagonal lines (45 degrees) - creates hash pattern
    val diagSpacing = lineSpacing * 1.5f
    
    // Top-left to bottom-right diagonals
    var offset = -extent * 2
    while (offset <= extent * 2) {
        drawLine(
            color = color.copy(alpha = color.alpha * 0.6f), // Slightly fainter diagonals
            start = Offset(centerX + offset - extent, centerY - extent),
            end = Offset(centerX + offset + extent, centerY + extent),
            strokeWidth = strokeWidth * 0.8f
        )
        offset += diagSpacing
    }
    
    // Top-right to bottom-left diagonals
    offset = -extent * 2
    while (offset <= extent * 2) {
        drawLine(
            color = color.copy(alpha = color.alpha * 0.6f),
            start = Offset(centerX + offset + extent, centerY - extent),
            end = Offset(centerX + offset - extent, centerY + extent),
            strokeWidth = strokeWidth * 0.8f
        )
        offset += diagSpacing
    }
}
