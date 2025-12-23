package com.air.pong.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.random.Random

/**
 * Ambient background animation for the Home Screen.
 * Shows random ping pong balls flying across the screen with parabolic arcs.
 * Includes both 3D shading on the ball and a ground shadow for depth.
 */
@Composable
fun HomeBackgroundAnimation(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // State to hold active balls
    val balls = remember { mutableStateListOf<BackgroundBall>() }
    
    // Frame ticker to force recomposition
    var time by remember { mutableLongStateOf(0L) }
    
    // Animation loop
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        val startTime = lastFrameTime
        
        while (isActive) {
            withFrameNanos { frameTime ->
                // Update time to force redraw
                time = frameTime - startTime
                
                val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTime
                
                // 1. Update existing balls
                val iterator = balls.iterator()
                while (iterator.hasNext()) {
                    val ball = iterator.next()
                    ball.update(deltaSeconds)
                    
                    // Remove if finished
                    if (ball.progress >= 1.0f) {
                        iterator.remove()
                    }
                }
                
                // 2. Spawn new balls
                // Max 2 balls on screen
                if (balls.size < 2) {
                    // Random chance to spawn: approx once every 2-4 seconds
                    // If 0 balls, higher chance to spawn one soon
                    val spawnChance = if (balls.isEmpty()) 0.02f else 0.005f
                    
                    if (Random.nextFloat() < spawnChance) {
                        val isLeftToRight = Random.nextBoolean()
                        val isLob = Random.nextBoolean() // 50/50 mix of flat vs lob
                        
                        balls.add(
                            BackgroundBall(
                                isLeftToRight = isLeftToRight,
                                isLob = isLob,
                                duration = if (isLob) Random.nextDouble(2.0, 3.0).toFloat() else Random.nextDouble(1.2, 1.8).toFloat(),
                                startDelay = 0f 
                            )
                        )
                    }
                }
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Force invalidation every frame
        @Suppress("UNUSED_VARIABLE")
        val playhead = time
        
        val width = size.width
        val height = size.height
        // Define "ground" line for shadow (e.g., 85% down the screen, similar to table height)
        val groundY = height * 0.85f
        
        balls.forEach { ball ->
            drawBallWithShadow(
                ball = ball,
                width = width,
                height = height,
                groundY = groundY,
                density = density.density
            )
        }
    }
}

private class BackgroundBall(
    val isLeftToRight: Boolean,
    val isLob: Boolean,
    val duration: Float,
    val startDelay: Float
) {
    var elapsedTime = -startDelay
    var progress = 0f
    
    // Random variations
    val arcHeightFactor = if (isLob) Random.nextFloat() * 0.3f + 0.4f else Random.nextFloat() * 0.2f + 0.1f // % of screen height
    val yBase = Random.nextFloat() * 0.2f + 0.5f // Start/End Y position (50-70% of screen height)
    
    // Randomize start/end points offscreen (10% to 40% margin)
    // This naturally randomizes the peak location (e.g. starting further back moves peak later)
    val startMarginPct = Random.nextFloat() * 0.3f + 0.1f 
    val endMarginPct = Random.nextFloat() * 0.3f + 0.1f
    
    fun update(dt: Float) {
        elapsedTime += dt
        if (elapsedTime > 0) {
            progress = (elapsedTime / duration).coerceIn(0f, 1f)
        }
    }
}

private fun DrawScope.drawBallWithShadow(
    ball: BackgroundBall,
    width: Float,
    height: Float,
    groundY: Float,
    density: Float
) {
    if (ball.progress <= 0f) return

    // Calculate randomized start/end X
    val startMargin = width * ball.startMarginPct
    val endMargin = width * ball.endMarginPct

    val startX = if (ball.isLeftToRight) -startMargin else width + startMargin
    val endX = if (ball.isLeftToRight) width + endMargin else -endMargin
    
    val startY = height * ball.yBase
    val endY = height * ball.yBase // Simple: start and end at same height
    
    // Standard Parabolic Arc (Natural motion)
    val t = ball.progress
    val x = startX + (endX - startX) * t
    
    // Height calculation
    // Max height in pixels
    val maxArcPixels = height * ball.arcHeightFactor
    // Parabola: 4 * h * t * (1-t)
    val heightOffset = maxArcPixels * 4 * t * (1 - t)
    
    val linearY = startY + (endY - startY) * t
    val y = linearY - heightOffset
    
    // Ball Visuals
    val ballRadius = 12.dp.toPx()
    val ballColor = Color(0xFFFFCC80) // Light Orange
    
    // 1. Ground Shadow
    // Shadow tracks X, sits on groundY
    // Shadow shrinks and fades as ball goes higher
    val heightRatio = heightOffset / maxArcPixels // 0..1 (1 is highest)
    val shadowScale = 1.0f - (heightRatio * 0.5f) // Shrinks to 50% at peak
    val shadowAlpha = 0.4f - (heightRatio * 0.3f) // Fades to 0.1 at peak
    
    drawOval(
        color = Color.Black.copy(alpha = shadowAlpha),
        topLeft = Offset(x - (ballRadius * shadowScale), groundY - (ballRadius * 0.2f)),
        size = Size(ballRadius * 2 * shadowScale, ballRadius * 0.4f * shadowScale) // Flattened oval
    )
    
    // 2. Ball with 3D effect
    // Main circle
    drawCircle(
        color = ballColor,
        radius = ballRadius,
        center = Offset(x, y)
    )
    
    // Inner shadow (Crescent) for 3D effect
    // Using Radial Gradient offset to create highlight/shadow
    // Highlight top-left, Shadow bottom-right
    
    val gradientBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.6f), // Highlight
            Color.Transparent,
            Color.Black.copy(alpha = 0.1f) // Shadow
        ),
        center = Offset(x - ballRadius * 0.3f, y - ballRadius * 0.3f), // Highlight offset to top-left
        radius = ballRadius * 1.2f
    )
    
    drawCircle(
        brush = gradientBrush,
        radius = ballRadius,
        center = Offset(x, y)
    )
    
    // Extra rim shadow for volume (bottom right)
    drawArc(
        color = Color.Black.copy(alpha = 0.1f),
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(x - ballRadius, y - ballRadius),
        size = Size(ballRadius * 2, ballRadius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
}
