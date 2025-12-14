package com.air.pong.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Animated points popup that shows earned points and animates toward the score display.
 * 
 * @param points The points to display (e.g. 14)
 * @param linesCleared Number of lines cleared with this hit (0 for no lines)
 * @param startOffset The starting position offset (from cell center)
 * @param targetOffset The target position offset (score area - relative to container)
 * @param onAnimationComplete Called when the animation is done
 */
@Composable
fun PointsPopup(
    points: Int,
    linesCleared: Int = 0,
    startOffset: Offset,
    targetOffset: Offset,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onAnimationComplete: () -> Unit = {}
) {
    if (!isVisible || points <= 0) return
    
    // Animation states
    var animationPhase by remember { mutableIntStateOf(0) }
    // 0 = show at source, 1 = fly to target, 2 = fade out
    
    // Calculate the delta to fly
    val deltaX = targetOffset.x - startOffset.x
    val deltaY = targetOffset.y - startOffset.y
    
    val progress by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (animationPhase == 0) 0 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "progress"
    )
    
    val alpha by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 1f
            1 -> 0.9f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 300),
        label = "alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 1.2f // Start slightly larger
            1 -> 0.6f // Shrink as it flies
            else -> 0.4f
        },
        animationSpec = tween(durationMillis = 400),
        label = "scale"
    )
    
    // Trigger animation sequence
    LaunchedEffect(points, isVisible) {
        if (isVisible && points > 0) {
            animationPhase = 0
            delay(250) // Show at source briefly
            animationPhase = 1
            delay(450) // Fly animation
            animationPhase = 2
            delay(150) // Fade out
            onAnimationComplete()
            animationPhase = 0
        }
    }
    
    // Calculate current position based on progress
    val currentX = startOffset.x + (deltaX * progress)
    val currentY = startOffset.y + (deltaY * progress)
    
    // Build display text: "+X" or "+X LINE!" or "+X LINES!"
    val displayText = when {
        linesCleared >= 2 -> "+$points LINES!"
        linesCleared == 1 -> "+$points LINE!"
        else -> "+$points"
    }
    
    Text(
        text = displayText,
        style = MaterialTheme.typography.displayMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .offset { IntOffset(currentX.toInt(), currentY.toInt()) }
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .alpha(alpha)
    )
}

/**
 * Simple version of PointsPopup that takes grid index and calculates position internally.
 * Position is calculated relative to a 3x3 grid layout.
 * 
 * @param points The points to display
 * @param linesCleared Number of lines cleared with this hit (0 for no lines)
 * @param gridIndex The grid cell index (0-8), or -1 for center (serve)
 * @param gridWidth Width of the grid in pixels
 * @param gridHeight Height of the grid in pixels  
 * @param targetOffsetY Distance to fly upward (toward score)
 * @param targetOffsetX Distance to fly horizontally (toward score)
 */
@Composable
fun PointsPopupWithGridIndex(
    points: Int,
    linesCleared: Int = 0,
    gridIndex: Int,
    gridWidth: Float,
    gridHeight: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onAnimationComplete: () -> Unit = {}
) {
    // Calculate start position based on grid cell
    val cellWidth = gridWidth / 3
    val cellHeight = gridHeight / 3
    
    val (row, col) = if (gridIndex >= 0 && gridIndex < 9) {
        Pair(gridIndex / 3, gridIndex % 3)
    } else {
        Pair(1, 1) // Center for serves
    }
    
    // Cell center position (relative to grid center)
    val startX = (col * cellWidth + cellWidth / 2) - (gridWidth / 2)
    val startY = (row * cellHeight + cellHeight / 2) - (gridHeight / 2)
    
    // Target: fly to top-right corner (score area) - needs to go higher above grid
    val targetX = gridWidth / 2 - 30f  // Right side
    val targetY = -gridHeight - 350f  // Way above the grid (toward score in top right)
    
    PointsPopup(
        points = points,
        linesCleared = linesCleared,
        startOffset = Offset(startX, startY),
        targetOffset = Offset(targetX, targetY),
        isVisible = isVisible,
        modifier = modifier,
        onAnimationComplete = onAnimationComplete
    )
}

/**
 * Animated score counter that counts up to a new value.
 * 
 * @param targetScore The score to animate to
 */
@Composable
fun AnimatedScoreCounter(
    targetScore: Int,
    modifier: Modifier = Modifier
) {
    var displayedScore by remember { mutableIntStateOf(targetScore) }
    var previousScore by remember { mutableIntStateOf(targetScore) }
    
    // Animate when score changes
    LaunchedEffect(targetScore) {
        if (targetScore > previousScore) {
            val startScore = previousScore
            val diff = targetScore - startScore
            
            // Count up quickly
            val steps = minOf(diff, 10) // Max 10 steps for visual speed
            val stepDelay = 50L // 50ms per step
            val stepSize = diff / steps
            
            for (i in 1..steps) {
                displayedScore = startScore + (stepSize * i)
                delay(stepDelay)
            }
            displayedScore = targetScore
        }
        previousScore = targetScore
    }
    
    Text(
        text = displayedScore.toString(),
        style = MaterialTheme.typography.displayLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}
