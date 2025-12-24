package com.air.pong.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * Background animation showing ball bouncing between paddles.
 * Syncs with game timing to help players understand ball trajectory.
 * 
 * The animation visualizes:
 * - WAITING_FOR_SERVE: Ball stationary near server's paddle
 * - SERVE: Ball bounces on server's side, arcs over net, bounces on receiver's side
 * - RALLY: Ball travels to opponent, bounces on their side
 */
@Composable
fun BallBounceAnimation(
    gamePhase: GamePhase,
    isMyTurn: Boolean,
    lastSwingType: SwingType?,
    flightTimeMs: Long,
    ballArrivalTimestamp: Long,
    myAvatarResId: Int,
    partnerAvatarResId: Int,
    isServing: Boolean,  // True if the current ball flight is from a serve
    bounceOffsetMs: Long = 200L,  // Time before arrival when bounce sound plays
    modifier: Modifier = Modifier,
    baseOpacity: Float = 0.3f,  // Base opacity, can be overridden for active gameplay
    totalMultiplier: Float = 1.0f  // Total multiplier for rainbow/streak effects
) {
    // Calculate animation progress based on real time vs arrival
    var animationProgress by remember { mutableFloatStateOf(0f) }
    // Track if animation is actively running to prevent double-ball glitch
    var isAnimating by remember { mutableStateOf(false) }
    
    // Update progress continuously during active phases
    // Key on isMyTurn to ensure animation restarts from 0% when turn changes (after a hit)
    LaunchedEffect(gamePhase, ballArrivalTimestamp, flightTimeMs, isMyTurn) {
        if (gamePhase == GamePhase.RALLY && ballArrivalTimestamp > 0) {
            val startTime = ballArrivalTimestamp - flightTimeMs
            val now = System.currentTimeMillis()
            // Only start animating if we're past the start time (prevents flash at 0%)
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
                kotlinx.coroutines.delay(16) // ~60fps update
            }
        } else {
            isAnimating = false
            animationProgress = 0f
        }
    }
    
    // Determine if ball should be visible and animating
    // Only show ball during serve wait, or during rally when animation is active (prevents double-ball flash)
    val showBall = gamePhase == GamePhase.WAITING_FOR_SERVE || (gamePhase == GamePhase.RALLY && isAnimating)
    
    // Default swing type for animation when none is set
    val swingType = lastSwingType ?: SwingType.MEDIUM_FLAT
    
    Box(modifier = modifier.graphicsLayer { clip = false }) {
        // Paddle swing animation state
        // Swing animation spec with smooth easing
        val swingSpec = tween<Float>(
            durationMillis = 100,
            easing = FastOutSlowInEasing
        )
        val returnSpec = tween<Float>(
            durationMillis = 250,
            easing = FastOutSlowInEasing
        )
        
        // Animate left paddle (player) - starts at -45deg, swings to 0deg (fully upright)
        val leftPaddleRotation = remember { Animatable(-45f) }
        val leftPaddleOffsetX = remember { Animatable(0f) }
        
        // Trigger left paddle animation when I just hit (isMyTurn becomes false)
        // !isMyTurn means ball is going AWAY from me, so I just hit it
        LaunchedEffect(isMyTurn, gamePhase) {
            if (gamePhase == GamePhase.RALLY && !isMyTurn) {
                // I just hit - animate left paddle (me)
                launch {
                    leftPaddleRotation.animateTo(0f, swingSpec)
                    leftPaddleRotation.animateTo(-45f, returnSpec)
                }
                launch {
                    leftPaddleOffsetX.animateTo(20f, swingSpec)
                    leftPaddleOffsetX.animateTo(0f, returnSpec)
                }
            }
        }
        
        // Animate right paddle (partner) - starts at 45deg, swings to 0deg (fully upright)
        val rightPaddleRotation = remember { Animatable(45f) }
        val rightPaddleOffsetX = remember { Animatable(0f) }
        
        // Trigger right paddle animation when partner just hit (isMyTurn becomes true)
        // isMyTurn means ball is coming TO me, so partner just hit it
        LaunchedEffect(isMyTurn, gamePhase) {
            if (gamePhase == GamePhase.RALLY && isMyTurn) {
                // Partner just hit - animate right paddle (partner)
                launch {
                    rightPaddleRotation.animateTo(0f, swingSpec)
                    rightPaddleRotation.animateTo(45f, returnSpec)
                }
                launch {
                    rightPaddleOffsetX.animateTo(-20f, swingSpec)
                    rightPaddleOffsetX.animateTo(0f, returnSpec)
                }
            }
        }
        
        // Layer 1: Draw the table and net (background layer)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Table line at bottom - gray horizontal line
            val tableY = height * 0.85f
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(width * 0.1f, tableY),
                end = Offset(width * 0.9f, tableY),
                strokeWidth = 3.dp.toPx()
            )
            
            // Net at center - white vertical rectangle
            val netX = width * 0.5f
            val netHeight = height * 0.15f
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(netX, tableY),
                end = Offset(netX, tableY - netHeight),
                strokeWidth = 4.dp.toPx()
            )
        }
        
        // Layer 2: Paddle avatars (middle layer)
        // Paddle offset from bottom: positions paddles just above the table line
        // The ball's paddleY in calculateBallPosition should roughly match this visual position
        val paddleOffsetFromBottom = 30.dp
        
        // Left paddle (player - animated)
        Image(
            painter = painterResource(id = myAvatarResId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomStart)
                .offset(x = 8.dp + leftPaddleOffsetX.value.dp, y = -paddleOffsetFromBottom)
                .graphicsLayer { rotationZ = leftPaddleRotation.value }
        )
        
        // Right paddle (partner - animated)
        Image(
            painter = painterResource(id = partnerAvatarResId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp + rightPaddleOffsetX.value.dp, y = -paddleOffsetFromBottom)
                .graphicsLayer { rotationZ = rightPaddleRotation.value }
        )
        
        // Rainbow hue animation for multiplier effects
        val infiniteTransition = rememberInfiniteTransition(label = "ballEffects")
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
        
        // Layer 3: Draw the ball (top layer - always visible)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val tableY = height * 0.85f
            
            if (showBall) {
                val ballRadius = 6.dp.toPx()
                
                // Calculate bounce progress based on actual timing (200ms before arrival)
                // For fast shots (SMASH), bounce happens very close to arrival so we use a low minimum
                val bounceProgressForRally = if (flightTimeMs > 0) {
                    ((flightTimeMs - bounceOffsetMs).toFloat() / flightTimeMs.toFloat()).coerceIn(0.1f, 0.95f)
                } else {
                    0.75f
                }
                
                val ballPosition = calculateBallPosition(
                    progress = animationProgress,
                    isServing = isServing && gamePhase == GamePhase.RALLY,
                    swingType = swingType,
                    isMyTurn = isMyTurn,
                    width = width,
                    height = height,
                    tableY = tableY,
                    gamePhase = gamePhase,
                    bounceProgress = bounceProgressForRally
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
 * Calculate ball position based on animation progress and swing type.
 * Returns the center point of the ball.
 */
private fun calculateBallPosition(
    progress: Float,
    isServing: Boolean,
    swingType: SwingType,
    isMyTurn: Boolean,
    width: Float,
    height: Float,
    tableY: Float,
    gamePhase: GamePhase,
    bounceProgress: Float = 0.75f
): Offset {
    // Paddle positions - X coordinates for ball start/end
    val leftPaddleX = width * 0.10f   // Closer to left edge for overlap with ball
    val rightPaddleX = width * 0.90f  // Closer to right edge for overlap with ball
    // Ball Y position at paddle - positioned toward TOP of paddle head (not the handle)
    // This is ~60% down from top, which visually aligns with upper portion of 32dp paddle at -33dp offset
    val paddleY = height * 0.55f
    
    // Determine start and end based on whose turn
    // IMPORTANT: isMyTurn means it's MY turn to hit, so ball is coming TO me (from opponent)
    // Ball should START from opponent and END at me
    val startX = if (isMyTurn) rightPaddleX else leftPaddleX  // Ball comes FROM opponent
    val endX = if (isMyTurn) leftPaddleX else rightPaddleX    // Ball goes TO me
    
    // In WAITING_FOR_SERVE, ball is stationary at server's paddle
    // isMyTurn during WAITING_FOR_SERVE means it's MY turn to SERVE (I am the server)
    if (gamePhase == GamePhase.WAITING_FOR_SERVE) {
        // Server is the person whose turn it is - ball should be at THEIR paddle
        val serverX = if (isMyTurn) leftPaddleX else rightPaddleX
        return Offset(serverX, paddleY)
    }
    
    // Calculate bounce timings based on swing type
    if (isServing) {
        return calculateServePosition(
            progress = progress,
            swingType = swingType,
            startX = startX,
            endX = endX,
            paddleY = paddleY,
            tableY = tableY,
            width = width,
            height = height
        )
    } else {
        return calculateRallyPosition(
            progress = progress,
            swingType = swingType,
            startX = startX,
            endX = endX,
            paddleY = paddleY,
            tableY = tableY,
            height = height,
            bounceProgress = bounceProgress
        )
    }
}

/**
 * Calculate ball position during serve.
 * Serve has TWO bounces: one on server's side, one on receiver's side.
 */
private fun calculateServePosition(
    progress: Float,
    swingType: SwingType,
    startX: Float,
    endX: Float,
    paddleY: Float,
    tableY: Float,
    width: Float,
    height: Float
): Offset {
    // Bounce timings vary by swing type
    val (firstBounceProgress, arcType) = when {
        swingType.isSmash() -> 0.25f to ArcType.SMASH_SERVE  // Fast down, then high
        swingType.isLob() -> 0.30f to ArcType.LOB_SERVE      // High arc first
        else -> 0.30f to ArcType.FLAT_SERVE                  // Standard
    }
    
    val netX = width * 0.5f
    val secondBounceProgress = 0.75f  // Bounce on receiver's side
    
    // First segment: Paddle to first bounce (server's side of table)
    if (progress < firstBounceProgress) {
        val segmentProgress = progress / firstBounceProgress
        val bounceX = (startX + netX) / 2  // Bounce point is between paddle and net
        
        return when (arcType) {
            ArcType.LOB_SERVE -> {
                // High arc upward first
                val x = lerp(startX, bounceX, segmentProgress)
                val desiredArc = height * 0.70f
                val arcHeight = safeArcHeight(desiredArc, paddleY, tableY) // prevents ball from going off screen / out of box
                val y = calculateParabolicY(segmentProgress, paddleY, arcHeight, tableY)
                Offset(x, y)
            }
            ArcType.SMASH_SERVE -> {
                // Smash has a flatter arc
                val x = lerp(startX, bounceX, segmentProgress)
                val desiredArc = height * 0.30f
                val arcHeight = safeArcHeight(desiredArc, paddleY, tableY)
                val y = calculateParabolicY(segmentProgress, paddleY, arcHeight, tableY)
                Offset(x, y)
            }
            else -> {
                // Standard arc
                val x = lerp(startX, bounceX, segmentProgress)
                val desiredArc = height * 0.40f
                val arcHeight = safeArcHeight(desiredArc, paddleY, tableY)
                val y = calculateParabolicY(segmentProgress, paddleY, arcHeight, tableY)
                Offset(x, y)
            }
        }
    }
    
    // Second segment: First bounce to second bounce (cross net to receiver's side)
    if (progress < secondBounceProgress) {
        val segmentProgress = (progress - firstBounceProgress) / (secondBounceProgress - firstBounceProgress)
        val bounceX = (startX + netX) / 2  // First bounce point
        val secondBounceX = (netX + endX) / 2  // Second bounce point
        
        return when (arcType) {
            ArcType.SMASH_SERVE -> {
                // After smash, ball continues with high arc over net
                val x = lerp(bounceX, secondBounceX, segmentProgress)
                val desiredArc = height * 0.50f
                val arcHeight = safeArcHeight(desiredArc, tableY, tableY)
                val y = calculateParabolicY(segmentProgress, tableY, arcHeight, tableY)
                Offset(x, y)
            }
            ArcType.LOB_SERVE -> {
                // After lob, high arc crossing net
                val x = lerp(bounceX, secondBounceX, segmentProgress)
                val desiredArc = height * 0.50f
                val arcHeight = safeArcHeight(desiredArc, tableY, tableY)
                val y = calculateParabolicY(segmentProgress, tableY, arcHeight, tableY)
                Offset(x, y)
            }
            else -> {
                // Standard arc over net
                val x = lerp(bounceX, secondBounceX, segmentProgress)
                val desiredArc = height * 0.35f
                val arcHeight = safeArcHeight(desiredArc, tableY, tableY)
                val y = calculateParabolicY(segmentProgress, tableY, arcHeight, tableY)
                Offset(x, y)
            }
        }
    }
    
    // Third segment: Second bounce to receiver's paddle
    val segmentProgress = (progress - secondBounceProgress) / (1f - secondBounceProgress)
    val secondBounceX = (netX + endX) / 2
    val x = lerp(secondBounceX, endX, segmentProgress)
    val desiredArc = height * 0.15f
    val arcHeight = safeArcHeight(desiredArc, tableY, paddleY)
    val y = calculateParabolicY(segmentProgress, tableY, arcHeight, paddleY)
    
    return Offset(x, y)
}

/**
 * Calculate ball position during rally.
 * Rally has ONE visible bounce on receiver's side (timing based on bounceProgress).
 */
private fun calculateRallyPosition(
    progress: Float,
    swingType: SwingType,
    startX: Float,
    endX: Float,
    paddleY: Float,
    tableY: Float,
    height: Float,
    bounceProgress: Float  // Dynamic bounce progress based on actual timing
): Offset {
    // Arc height varies by swing type - capped to stay within container
    val desiredArcHeight = when {
        swingType.isLob() -> height * 0.80f     // Very high lob
        swingType.isSmash() -> height * 0.30f   // Smash has flatter arc
        else -> height * 0.50f                  // Flat shots have moderate arc
    }
    val arcHeight = safeArcHeight(desiredArcHeight, paddleY, tableY)
    
    // Bounce point is between net and receiver
    val netX = (startX + endX) / 2
    val bounceX = (netX + endX) / 2
    
    if (progress < bounceProgress) {
        // Traveling to bounce point
        val segmentProgress = progress / bounceProgress
        val x = lerp(startX, bounceX, segmentProgress)
        val y = calculateParabolicY(segmentProgress, paddleY, arcHeight, tableY)
        return Offset(x, y)
    } else {
        // Bounce to receiver's paddle
        val segmentProgress = (progress - bounceProgress) / (1f - bounceProgress)
        val x = lerp(bounceX, endX, segmentProgress)
        val desiredAfterBounceArc = height * 0.25f
        val afterBounceArc = safeArcHeight(desiredAfterBounceArc, tableY, paddleY)
        val y = calculateParabolicY(segmentProgress, tableY, afterBounceArc, paddleY)
        return Offset(x, y)
    }
}


/**
 * Calculate Y position along a parabolic arc.
 * @param t Progress from 0 to 1
 * @param startY Starting Y position
 * @param arcHeight Height of arc (positive = arc goes UP above the line, negative = arc goes down)
 * @param endY Ending Y position
 */
private fun calculateParabolicY(t: Float, startY: Float, arcHeight: Float, endY: Float): Float {
    // Parabola: y = startY + (endY - startY) * t - 4 * arcHeight * t * (1 - t)
    // In Canvas, Y increases downward, so we SUBTRACT arcHeight to go UP
    val linearY = startY + (endY - startY) * t
    val parabolicOffset = 4 * arcHeight * t * (1 - t)
    return linearY - parabolicOffset  // Subtract to arc UPWARD (toward smaller Y values)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * Calculate safe arc height that keeps ball within container bounds.
 * @param desiredArc The desired arc height
 * @param startY Starting Y position
 * @param endY Ending Y position  
 * @param topMargin Minimum Y value (top of container + ball radius margin)
 * @return Arc height capped to keep peak within bounds
 */
private fun safeArcHeight(desiredArc: Float, startY: Float, endY: Float, topMargin: Float = 15f): Float {
    // Peak of arc occurs at t=0.5 where parabolicOffset = arcHeight
    // At peak: peakY = (startY + endY) / 2 - arcHeight
    // For peakY >= topMargin: arcHeight <= (startY + endY) / 2 - topMargin
    val midY = (startY + endY) / 2
    val maxArc = midY - topMargin
    return minOf(desiredArc, maxArc.coerceAtLeast(0f))
}

private enum class ArcType {
    LOB_SERVE,    // High arc up first
    SMASH_SERVE,  // Fast down, then high arc
    FLAT_SERVE    // Standard trajectory
}
