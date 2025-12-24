package com.air.pong.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.SwingType
import com.air.pong.core.game.isLob
import com.air.pong.core.game.isSmash
import kotlinx.coroutines.launch

/**
 * Background animation for Solo Rally mode showing ball bouncing between paddle and wall.
 * 
 * The animation visualizes:
 * - WAITING_FOR_SERVE: Ball stationary near player's paddle
 * - SERVE: Ball bounces on table (25%), hits wall (50%), bounces on table (75%)
 * - RALLY: Ball travels to wall (40%), bounces on table (75%), returns to paddle
 */
@Composable
fun SoloBallBounceAnimation(
    gamePhase: GamePhase,
    lastSwingType: SwingType?,
    flightTimeMs: Long,
    ballArrivalTimestamp: Long,
    myAvatarResId: Int,
    isServing: Boolean,
    modifier: Modifier = Modifier,
    totalMultiplier: Float = 1.0f  // Total multiplier for rainbow/streak effects
) {
    // Calculate animation progress based on real time vs arrival
    var animationProgress by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    
    // Update progress continuously during active phases
    LaunchedEffect(gamePhase, ballArrivalTimestamp, flightTimeMs) {
        if (gamePhase == GamePhase.RALLY && ballArrivalTimestamp > 0) {
            val startTime = ballArrivalTimestamp - flightTimeMs
            val now = System.currentTimeMillis()
            if (now >= startTime) {
                isAnimating = true
            }
            while (true) {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                if (elapsed > 0) {
                    isAnimating = true
                    animationProgress = (elapsed.toFloat() / flightTimeMs.toFloat()).coerceIn(0f, 1f)
                }
                kotlinx.coroutines.delay(16) // ~60fps
            }
        } else {
            isAnimating = false
            animationProgress = 0f
        }
    }
    
    val showBall = gamePhase == GamePhase.WAITING_FOR_SERVE || (gamePhase == GamePhase.RALLY && isAnimating)
    val swingType = lastSwingType ?: SwingType.MEDIUM_FLAT
    
    Box(modifier = modifier.graphicsLayer { clip = false }) {
        // Paddle swing animation
        val swingSpec = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)
        val returnSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)
        
        val paddleRotation = remember { Animatable(-45f) }
        val paddleOffsetX = remember { Animatable(0f) }
        
        // Trigger paddle animation when player hits (new ballArrivalTimestamp means player swung)
        // Key on ballArrivalTimestamp - changes exactly once per hit
        LaunchedEffect(ballArrivalTimestamp) {
            if (gamePhase == GamePhase.RALLY && ballArrivalTimestamp > 0) {
                // Player just hit - animate paddle swing immediately
                launch {
                    paddleRotation.animateTo(0f, swingSpec)
                    paddleRotation.animateTo(-45f, returnSpec)
                }
                launch {
                    paddleOffsetX.animateTo(20f, swingSpec)
                    paddleOffsetX.animateTo(0f, returnSpec)
                }
            }
        }
        
        // Layer 1: Draw table line and brick wall
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Table line at bottom - grey horizontal line
            val tableY = height * 0.85f
            drawLine(
                color = Color.Gray.copy(alpha = 0.6f),
                start = Offset(width * 0.20f, tableY),
                end = Offset(width * 0.95f, tableY),
                strokeWidth = 3.dp.toPx()
            )
            
            // Brick wall on right side - multiple brick rows
            val wallX = width * 0.80f
            val wallWidth = width * 0.08f
            val wallTop = height * 0.20f
            val wallHeight = tableY - wallTop
            
            // Wall background
            drawRect(
                color = Color(0xFF8B4513).copy(alpha = 0.8f), // Brown
                topLeft = Offset(wallX, wallTop),
                size = Size(wallWidth, wallHeight)
            )
            
            // Brick pattern - horizontal lines
            val brickHeight = wallHeight / 8
            for (i in 1..7) {
                val y = wallTop + i * brickHeight
                drawLine(
                    color = Color(0xFF2F2F2F).copy(alpha = 0.6f),
                    start = Offset(wallX, y),
                    end = Offset(wallX + wallWidth, y),
                    strokeWidth = 2.dp.toPx()
                )
            }
            
            // Vertical brick lines (staggered)
            val brickWidth = wallWidth / 2
            for (row in 0..7) {
                val y = wallTop + row * brickHeight
                val offset = if (row % 2 == 0) 0f else brickWidth / 2
                
                var x = wallX + offset
                while (x < wallX + wallWidth) {
                    drawLine(
                        color = Color(0xFF2F2F2F).copy(alpha = 0.6f),
                        start = Offset(x, y),
                        end = Offset(x, y + brickHeight),
                        strokeWidth = 2.dp.toPx()
                    )
                    x += brickWidth
                }
            }
        }
        
        // Layer 2: Player paddle (left side)
        val paddleOffsetFromBottom = 30.dp
        
        Image(
            painter = painterResource(id = myAvatarResId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomStart)
                .offset(x = 8.dp + paddleOffsetX.value.dp, y = -paddleOffsetFromBottom)
                .graphicsLayer { rotationZ = paddleRotation.value }
        )
        
        // Rainbow hue animation for multiplier effects
        val infiniteTransition = rememberInfiniteTransition(label = "soloBallEffects")
        val rainbowHue by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rainbowHue"
        )
        
        // Track position history for streak trail
        val positionHistory = remember { mutableStateListOf<Offset>() }
        
        // Layer 3: Ball
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val tableY = height * 0.85f
            
            if (showBall) {
                val ballRadius = 6.dp.toPx()
                
                val ballPosition = calculateSoloBallPosition(
                    progress = animationProgress,
                    isServing = isServing && gamePhase == GamePhase.RALLY,
                    swingType = swingType,
                    width = width,
                    height = height,
                    tableY = tableY,
                    gamePhase = gamePhase
                )
                
                // Update position history for streak trail
                if (gamePhase == GamePhase.RALLY) {
                    positionHistory.add(ballPosition)
                    // Keep last 8 positions for trail
                    while (positionHistory.size > 8) {
                        positionHistory.removeAt(0)
                    }
                } else {
                    positionHistory.clear()
                }
                
                // Determine ball color and streak based on multiplier
                // 1-1.5: white ball, no tail
                // 1.5-2: orange ball, no tail
                // 2-2.5: orange ball with white tail
                // 2.5-3: rainbow ball with white tail
                // 3+: rainbow ball with rainbow tail
                val showOrangeBall = totalMultiplier >= 1.5f && totalMultiplier < 2.5f
                val showRainbowBall = totalMultiplier >= 2.5f
                val showWhiteTail = totalMultiplier >= 2.0f && totalMultiplier < 3.0f
                val showRainbowTail = totalMultiplier >= 3.0f
                
                // Draw streak trail if active (multiplier >= 2.0)
                if ((showWhiteTail || showRainbowTail) && positionHistory.size > 1) {
                    for (i in 0 until positionHistory.size - 1) {
                        val alpha = (i + 1).toFloat() / positionHistory.size * 0.6f
                        val trailRadius = ballRadius * (0.3f + 0.5f * (i.toFloat() / positionHistory.size))
                        val trailColor = if (showRainbowTail) {
                            // Rainbow tail - offset hue for each position
                            val offsetHue = (rainbowHue + i * 30f) % 360f
                            Color.hsv(offsetHue, 0.8f, 1f).copy(alpha = alpha)
                        } else {
                            // White tail
                            Color.White.copy(alpha = alpha)
                        }
                        drawCircle(
                            color = trailColor,
                            radius = trailRadius,
                            center = positionHistory[i]
                        )
                    }
                }
                
                // Draw ball with color based on multiplier
                val ballColor = when {
                    showRainbowBall -> Color.hsv(rainbowHue, 0.8f, 1f)
                    showOrangeBall -> Color(0xFFFF9100) // Orange (same as pulsing flame)
                    else -> Color.White
                }
                
                drawCircle(
                    color = ballColor,
                    radius = ballRadius,
                    center = ballPosition
                )
            } else {
                // Clear position history when ball not visible
                positionHistory.clear()
            }
        }
    }
}

/**
 * Calculate ball position for Solo Rally mode.
 */
private fun calculateSoloBallPosition(
    progress: Float,
    isServing: Boolean,
    swingType: SwingType,
    width: Float,
    height: Float,
    tableY: Float,
    gamePhase: GamePhase
): Offset {
    val paddleX = width * 0.10f
    val wallX = width * 0.85f
    val paddleY = height * 0.55f
    
    // Waiting for serve: ball at paddle
    if (gamePhase == GamePhase.WAITING_FOR_SERVE) {
        return Offset(paddleX, paddleY)
    }
    
    return if (isServing) {
        calculateSoloServePosition(progress, swingType, paddleX, wallX, paddleY, tableY, height, width)
    } else {
        calculateSoloRallyPosition(progress, swingType, paddleX, wallX, paddleY, tableY, height, width)
    }
}

/**
 * Solo serve: Paddle → Table (varies) → Wall (50%) → Table (varies) → Paddle (100%)
 * Smash serves: 15% / 50% / 80% for snappier feel
 * Other serves: 25% / 50% / 75% (standard)
 * All table bounces happen at midscreen for visual clarity.
 */
private fun calculateSoloServePosition(
    progress: Float,
    swingType: SwingType,
    paddleX: Float,
    wallX: Float,
    paddleY: Float,
    tableY: Float,
    height: Float,
    width: Float
): Offset {
    // Use centralized timing from GameEngine to match sound scheduling
    val tableBounce1 = com.air.pong.core.game.GameEngine.getSoloServeTable1Ratio(swingType)
    val wallBounce = com.air.pong.core.game.GameEngine.SOLO_SERVE_WALL
    val tableBounce2 = com.air.pong.core.game.GameEngine.getSoloServeTable2Ratio(swingType)
    
    // All table bounces at midscreen
    val midX = width * 0.5f
    
    // Wall hit Y position - upper portion of wall (75% toward top)
    val wallTop = height * 0.20f
    val wallY = wallTop + (tableY - wallTop) * 0.25f  // 25% down from wall top = 75% toward top
    
    // Arc heights by swing type
    val baseArc = when {
        swingType.isLob() -> height * 0.60f
        swingType.isSmash() -> height * 0.25f
        else -> height * 0.40f
    }
    
    return when {
        // Segment 1: Paddle to first table bounce (midscreen)
        progress < tableBounce1 -> {
            val t = progress / tableBounce1
            val x = lerp(paddleX, midX, t)
            val arc = safeArcHeight(baseArc * 0.5f, paddleY, tableY)
            val y = calculateParabolicY(t, paddleY, arc, tableY)
            Offset(x, y)
        }
        // Segment 2: First table bounce to upper wall
        progress < wallBounce -> {
            val t = (progress - tableBounce1) / (wallBounce - tableBounce1)
            val x = lerp(midX, wallX, t)
            val arc = safeArcHeight(baseArc * 0.6f, tableY, wallY)
            val y = calculateParabolicY(t, tableY, arc, wallY)
            Offset(x, y)
        }
        // Segment 3: Wall to second table bounce (descending - minimal upward arc)
        progress < tableBounce2 -> {
            val t = (progress - wallBounce) / (tableBounce2 - wallBounce)
            val x = lerp(wallX, midX, t)
            // Descent arc similar to rally
            val descentArc = when {
                swingType.isLob() -> height * 0.10f
                swingType.isSmash() -> height * 0.20f
                else -> height * 0.15f
            }
            val arc = safeArcHeight(descentArc, wallY, tableY)
            val y = calculateParabolicY(t, wallY, arc, tableY)
            Offset(x, y)
        }
        // Segment 4: Second table bounce to paddle
        else -> {
            val t = (progress - tableBounce2) / (1f - tableBounce2)
            val x = lerp(midX, paddleX, t)
            val arc = safeArcHeight(height * 0.20f, tableY, paddleY)
            val y = calculateParabolicY(t, tableY, arc, paddleY)
            Offset(x, y)
        }
    }
}

/**
 * Solo rally: Paddle → Wall (40%) → Table (75%) → Paddle (100%)
 * Ball hits upper portion of wall, then descends to table.
 * Shot type affects trajectory: lobs descend more steeply, smashes more perpendicular.
 */
private fun calculateSoloRallyPosition(
    progress: Float,
    swingType: SwingType,
    paddleX: Float,
    wallX: Float,
    paddleY: Float,
    tableY: Float,
    height: Float,
    width: Float
): Offset {
    // Wall/table bounce timing - use centralized constants from GameEngine
    // This ensures animation syncs with sound scheduling in GameViewModel
    val wallBounce = com.air.pong.core.game.GameEngine.getSoloWallBounceRatio(swingType)
    val tableBounce = com.air.pong.core.game.GameEngine.SOLO_RALLY_TABLE_BOUNCE_RATIO
    
    // Table bounce at midscreen
    val midX = width * 0.5f
    
    // Wall hit Y position - upper portion of wall (75% toward top)
    // wallTop is around height * 0.20f, tableY is around height * 0.85f
    val wallTop = height * 0.20f
    val wallY = wallTop + (tableY - wallTop) * 0.25f  // 25% down from wall top = 75% toward top
    
    // Arc heights by swing type
    val desiredOutgoingArc = when {
        swingType.isLob() -> height * 0.70f
        swingType.isSmash() -> height * 0.25f
        else -> height * 0.45f
    }
    
    return when {
        // Segment 1: Paddle to upper wall
        progress < wallBounce -> {
            val t = progress / wallBounce
            val x = lerp(paddleX, wallX, t)
            val arc = safeArcHeight(desiredOutgoingArc, paddleY, wallY)
            val y = calculateParabolicY(t, paddleY, arc, wallY)
            Offset(x, y)
        }
        // Segment 2: Wall to table bounce (descending from wall - minimal or no upward arc)
        // Lobs coming from higher up descend more steeply
        // Smashes coming lower descend more perpendicular/shallower
        progress < tableBounce -> {
            val t = (progress - wallBounce) / (tableBounce - wallBounce)
            val x = lerp(wallX, midX, t)
            // Small arc to make it look natural - NOT upward, just a gentle curve down
            // Lobs: steeper descent (less arc), Smash: shallower (more arc needed to curve)
            val descentArc = when {
                swingType.isLob() -> height * 0.10f   // Minimal arc, almost straight down
                swingType.isSmash() -> height * 0.20f // Some arc for perpendicular feel
                else -> height * 0.15f                // Moderate descent
            }
            val arc = safeArcHeight(descentArc, wallY, tableY)
            val y = calculateParabolicY(t, wallY, arc, tableY)
            Offset(x, y)
        }
        // Segment 3: Table bounce to paddle
        else -> {
            val t = (progress - tableBounce) / (1f - tableBounce)
            val x = lerp(midX, paddleX, t)
            val arc = safeArcHeight(height * 0.20f, tableY, paddleY)
            val y = calculateParabolicY(t, tableY, arc, paddleY)
            Offset(x, y)
        }
    }
}

/**
 * Calculate Y position along a parabolic arc.
 */
private fun calculateParabolicY(t: Float, startY: Float, arcHeight: Float, endY: Float): Float {
    val linearY = startY + (endY - startY) * t
    val parabolicOffset = 4 * arcHeight * t * (1 - t)
    return linearY - parabolicOffset
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * Calculate safe arc height that keeps ball within container bounds.
 * Prevents clipping at top of animation area.
 */
private fun safeArcHeight(desiredArc: Float, startY: Float, endY: Float, topMargin: Float = 15f): Float {
    // Peak of arc occurs at t=0.5 where parabolicOffset = arcHeight
    // At peak: peakY = (startY + endY) / 2 - arcHeight
    // For peakY >= topMargin: arcHeight <= (startY + endY) / 2 - topMargin
    val midY = (startY + endY) / 2
    val maxArc = midY - topMargin
    return minOf(desiredArc, maxArc.coerceAtLeast(0f))
}
