package com.air.pong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.core.game.SpinType
import com.air.pong.core.game.SwingType
import com.air.pong.core.game.SpecialSquareType
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Copy Cat indicator types for grid cell display.
 */
enum class CopyCatIndicatorType {
    /** 🐾 - shown on leader's cells (local player is leader) */
    LEADER_PAW,
    /** 🐱 - shown on follower's cells (local player copied correctly) */
    FOLLOWER_CAT,
    /** X🐱 - next cell follower should hit (leader just hit this) */
    NEXT_EXPECTED,
    /** ✖️ - pattern break animation */
    BREAK_FLASH
}

/**
 * Copy Cat indicator data for a single grid cell.
 */
data class CopyCatIndicator(
    /** Sequence number (1, 2, 3...) or -1 for break flash */
    val number: Int,
    /** Type of indicator to show */
    val type: CopyCatIndicatorType
)

@Composable
fun RallyGrid(
    gridState: List<Boolean>,
    cellTiers: List<Int> = List(9) { 0 },
    modifier: Modifier = Modifier,
    // Tap/swipe-to-hit accessibility feature
    isTapToHitEnabled: Boolean = false,
    isPlayerTurn: Boolean = false,
    onCellHit: ((SwingType, SpinType) -> Unit)? = null,
    // Copy Cat indicators per cell (indexed 0-8)
    copyCatIndicators: Map<Int, CopyCatIndicator> = emptyMap(),
    // Special squares per cell (indexed 0-8)
    specialSquares: Map<Int, SpecialSquareType> = emptyMap(),
    // Power Outage state
    cellHitCounts: List<Int> = List(9) { 0 },
    isPowerOutage: Boolean = false,
    outageRecoveryUniqueCells: Set<Int> = emptySet()
) {
    if (gridState.size != 9) return

    // Track previous minimum tier to detect FULL tier upgrades (grid complete, all cells reset)
    var previousMinTier by remember { mutableIntStateOf(cellTiers.minOrNull() ?: 0) }
    var tierJustCompleted by remember { mutableStateOf(false) }
    
    // Detect when the MINIMUM tier increases - this means a full grid/tier upgrade occurred
    // (Line clears increase individual cell tiers, but the minTier only increases when ALL cells are upgraded)
    LaunchedEffect(cellTiers) {
        val currentMinTier = cellTiers.minOrNull() ?: 0
        if (currentMinTier > previousMinTier) {
            // Full tier upgrade - all cells now have higher minimum tier
            tierJustCompleted = true
        }
        previousMinTier = currentMinTier
    }

    BoxWithConstraints(
        modifier = modifier.padding(8.dp)
    ) {
        // Calculate the max size that fits as a square while respecting aspect ratio
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val gridSize = minOf(availableWidth, availableHeight)
        
        Column(
            modifier = Modifier
                .size(gridSize)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rows = Type (Lob, Flat, Smash) - matches SwingType.getGridIndex()
            val rowNames = listOf("Lob", "Flat", "Smash")
            val colNames = listOf("Soft", "Med", "Hard")

            // Find the minimum tier across all cells to determine which cells are "needed" for current tier
            val minTier = cellTiers.minOrNull() ?: 0

            for (row in 0..2) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Cells
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val isMarked = gridState[index]
                        val tierIndex = cellTiers.getOrElse(index) { 0 }
                        
                        val typeLabel = rowNames[row]
                        val forceLabel = colNames[col]
                        
                        // A cell is "needed" if it hasn't been hit AND it's at the minimum tier level
                        // These are the cells the player needs to complete to advance to the next tier
                        val isNeededForTier = !isMarked && tierIndex == minTier
                        
                        // Map grid index back to SwingType for tap handling
                        val swingType = SwingType.entries.find { it.getGridIndex() == index }
                        
                        RallyGridCell(
                            isMarked = isMarked,
                            topLabel = forceLabel,
                            bottomLabel = typeLabel,
                            tierIndex = tierIndex,
                            isNeededForTier = isNeededForTier,
                            tierJustCompleted = tierJustCompleted,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            // Only allow taps/swipes when tap-to-hit is enabled and it's player's turn
                            isClickable = isTapToHitEnabled && isPlayerTurn && swingType != null,
                            onHit = if (isTapToHitEnabled && isPlayerTurn && swingType != null) {
                                { spinType -> onCellHit?.invoke(swingType, spinType) }
                            } else null,
                            copyCatIndicator = copyCatIndicators[index],
                            specialSquare = specialSquares[index],
                            // Power outage state
                            cellHitCount = cellHitCounts.getOrElse(index) { 0 },
                            isPowerOutage = isPowerOutage,
                            isOutageRecoveryCell = index in outageRecoveryUniqueCells
                        )
                    }
                }
            }
        }
    }
    
    // Reset tier completion flag after a brief moment
    LaunchedEffect(tierJustCompleted) {
        if (tierJustCompleted) {
            delay(100)
            tierJustCompleted = false
        }
    }
}

@Composable
fun RallyGridCell(
    isMarked: Boolean,
    topLabel: String,
    bottomLabel: String,
    tierIndex: Int = 0,
    isNeededForTier: Boolean = true,
    tierJustCompleted: Boolean = false,
    modifier: Modifier,
    // Tap/swipe-to-hit accessibility parameters
    isClickable: Boolean = false,
    onHit: ((SpinType) -> Unit)? = null,
    // Copy Cat indicator
    copyCatIndicator: CopyCatIndicator? = null,
    // Special square on this cell
    specialSquare: SpecialSquareType? = null,
    // Power Outage state
    cellHitCount: Int = 0,
    isPowerOutage: Boolean = false,
    isOutageRecoveryCell: Boolean = false
) {
    // Swipe threshold in dp (converted to px at usage site)
    val swipeThresholdDp = 30.dp
    // Extra leeway time for swipes in ms (placeholder, currently 0)
    // If needed, this can be increased to give more time for swipe gestures
    @Suppress("unused")
    val swipeLeewayMs = 0L
    // Get the point value using triangular number formula
    val tierPoints = com.air.pong.core.game.GameEngine.getPointsForTier(tierIndex)
    
    // Track previous tier to detect when THIS cell was upgraded (line completed and cell cleared)
    var previousTier by remember { mutableStateOf(tierIndex) }
    var isAnimatingClear by remember { mutableStateOf(false) }
    
    // Enhanced line completion animation: pulse-fade-pulse effect
    // Animated alpha for the highlight effect (0 = normal, 1 = full highlight)
    val lineCompleteAlpha = remember { Animatable(0f) }
    
    // Detect when this cell's tier increases (it was just cleared and upgraded)
    LaunchedEffect(tierIndex) {
        if (tierIndex > previousTier) {
            // Cell was just upgraded - animate with pulse-fade-pulse (~600ms total)
            isAnimatingClear = true
            
            // Phase 1: Fade up to 60% (0-150ms)
            lineCompleteAlpha.animateTo(0.6f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 2: Fade back to 30% (150-300ms)
            lineCompleteAlpha.animateTo(0.3f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 3: Fade up to 100% - peak (300-450ms)
            lineCompleteAlpha.animateTo(1.0f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 4: Fade to final state (450-600ms)
            lineCompleteAlpha.animateTo(0f, animationSpec = tween(150, easing = EaseInOutSine))
            
            isAnimatingClear = false
        }
        previousTier = tierIndex
    }
    
    // Also animate when cell becomes marked (completing hit) - also use pulse-fade-pulse
    var previousMarked by remember { mutableStateOf(isMarked) }
    var isAnimatingHit by remember { mutableStateOf(false) }
    val hitAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(isMarked) {
        if (!previousMarked && isMarked) {
            // Cell was just hit - pulse-fade-pulse highlight (~600ms)
            isAnimatingHit = true
            
            // Phase 1: Fade up to 60% (0-150ms)
            hitAlpha.animateTo(0.6f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 2: Fade back to 30% (150-300ms)  
            hitAlpha.animateTo(0.3f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 3: Fade up to 100% - peak (300-450ms)
            hitAlpha.animateTo(1.0f, animationSpec = tween(150, easing = EaseInOutSine))
            // Phase 4: Fade to final state (450-600ms)
            hitAlpha.animateTo(0f, animationSpec = tween(150, easing = EaseInOutSine))
            
            isAnimatingHit = false
        }
        previousMarked = isMarked
    }
    
    // Tier upgrade border animation - thick pulsing borders on tier upgrade
    // Track if we're currently in the thick border animation
    var showThickBorder by remember { mutableStateOf(false) }
    val thickBorderAlpha = remember { Animatable(0f) }
    
    // Trigger thick border animation when tierIndex increases (cell was upgraded as part of full tier)
    var previousTierIndex by remember { mutableIntStateOf(tierIndex) }
    
    LaunchedEffect(tierIndex) {
        if (tierIndex > previousTierIndex) {
            // Cell just got upgraded - show thick border with 3 pulses
            showThickBorder = true
            
            // Three quick emphatic pulses with thick (5dp) border
            repeat(3) {
                thickBorderAlpha.animateTo(1.0f, animationSpec = tween(100, easing = FastOutSlowInEasing))
                thickBorderAlpha.animateTo(0.3f, animationSpec = tween(100, easing = FastOutSlowInEasing))
            }
            
            // Fade out completely then return to normal
            thickBorderAlpha.animateTo(0f, animationSpec = tween(100))
            showThickBorder = false
        }
        previousTierIndex = tierIndex
    }
    
    // Combined animation state
    val isAnimating = isAnimatingClear || isAnimatingHit
    val animationAlpha = maxOf(lineCompleteAlpha.value, hitAlpha.value)
    
    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val unmarkedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    
    // Pulsing glow for needed cells (standard thin border pulse)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    // Calculate background color with animation blending
    // Special squares have unique background colors
    val specialBaseColor = when (specialSquare) {
        SpecialSquareType.BANANA -> Color(0xFFE0E0E0) // Light gray
        SpecialSquareType.LANDMINE -> Color(0xFFFFCDD2) // Light red/pink
        SpecialSquareType.GOLDEN -> Color(0xFFFFF9C4) // Light gold/yellow
        null -> null
    }
    val baseColor = when {
        // Banana stays gray even when marked (hit) - only clears on tier upgrade
        specialSquare == SpecialSquareType.BANANA -> Color(0xFFE0E0E0)
        // Landmine stays red even when marked - only clears on tier upgrade
        specialSquare == SpecialSquareType.LANDMINE -> Color(0xFFFFCDD2)
        // Other special squares: show special color only when unmarked
        specialBaseColor != null && !isMarked -> specialBaseColor
        isMarked -> primaryColor
        else -> unmarkedColor
    }
    val backgroundColor = if (isAnimating && animationAlpha > 0f) {
        lerp(baseColor, highlightColor, animationAlpha)
    } else {
        baseColor
    }
    
    val textColor = when {
        isMarked || (isAnimating && animationAlpha > 0.5f) -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    // Border logic - special squares have their own borders
    val specialBorderColor = when (specialSquare) {
        SpecialSquareType.BANANA -> Color(0xFF9E9E9E) // Gray border
        SpecialSquareType.LANDMINE -> Color(0xFFE53935) // Red border  
        SpecialSquareType.GOLDEN -> Color(0xFFFFD700) // Gold border
        null -> null
    }
    
    val borderColor = when {
        specialBorderColor != null && !isMarked -> specialBorderColor.copy(alpha = pulseAlpha)
        showThickBorder && !isMarked -> Color(0xFFFFB300).copy(alpha = thickBorderAlpha.value)
        isNeededForTier && !isMarked -> Color(0xFFFFB300).copy(alpha = pulseAlpha)
        else -> Color.Transparent
    }
    val borderWidth = when {
        specialSquare != null && !isMarked -> 3.dp
        showThickBorder && !isMarked -> 5.dp
        isNeededForTier && !isMarked -> 2.dp
        else -> 0.dp
    }
    
    // Text styling: Normal for already-cleared cells (higher tier or marked), Bold for needed cells
    val labelFontWeight = when {
        isMarked -> FontWeight.Normal  // Currently marked - dimmed
        isNeededForTier -> FontWeight.ExtraBold  // Need this shot - emphasized
        else -> FontWeight.Normal  // Already cleared in previous tier - normal
    }
    
    // Text alpha: slightly dimmed for non-needed cells
    val textAlpha = when {
        isMarked -> 1f
        isNeededForTier -> 1f
        else -> 0.6f  // Cleared cells are dimmer
    }

    // Get density for converting dp to px
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { swipeThresholdDp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isClickable && onHit != null) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first touch
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPosition = down.position
                            var lastPosition = startPosition
                            
                            // Track pointer movement until release
                            var released = false
                            var cancelled = false
                            
                            while (!released && !cancelled) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id }
                                
                                if (pointer == null) {
                                    cancelled = true
                                } else if (!pointer.pressed) {
                                    // Finger lifted - record final position
                                    lastPosition = pointer.position
                                    released = true
                                } else {
                                    // Still dragging - update position
                                    lastPosition = pointer.position
                                }
                            }
                            
                            if (released) {
                                // Calculate delta from start to end position
                                val deltaX = lastPosition.x - startPosition.x
                                val deltaY = lastPosition.y - startPosition.y
                                
                                // Calculate spin type from swipe direction
                                // Timing is based on gesture END (when finger lifts)
                                val spinType = calculateSpinTypeFromSwipe(
                                    deltaX = deltaX,
                                    deltaY = deltaY,
                                    thresholdPx = swipeThresholdPx
                                )
                                
                                onHit(spinType)
                            }
                        }
                    }
                } else Modifier
            )
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(4.dp))
                } else Modifier
            )
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic text size based on cell height
        val cellHeight = maxHeight
        val density = LocalDensity.current
        val cellHeightPx = with(density) { cellHeight.toPx() }
        
        // Calculate font size: roughly 22% of cell height, min 8sp, max 18sp
        val calculatedFontSize = (cellHeightPx * 0.22f / density.fontScale).coerceIn(8f, 18f)
        val fontSize = calculatedFontSize.sp
        
        // Tier point overlay - larger and bolder text in top-left corner
        val tierFontSize = (cellHeightPx * 0.20f / density.fontScale).coerceIn(8f, 16f).sp
        Text(
            text = tierPoints.toString(),
            fontSize = tierFontSize,
            color = textColor.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
        )
        
        // Special square icon in top-right corner
        if (specialSquare != null) {
            val specialIcon = when (specialSquare) {
                SpecialSquareType.BANANA -> "🍌"
                SpecialSquareType.LANDMINE -> "💥"
                SpecialSquareType.GOLDEN -> "🪙"
            }
            val specialFontSize = (cellHeightPx * 0.25f / density.fontScale).coerceIn(10f, 18f).sp
            
            Text(
                text = specialIcon,
                fontSize = specialFontSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = topLabel, 
                fontSize = fontSize,
                color = textColor.copy(alpha = textAlpha),
                fontWeight = labelFontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = bottomLabel, 
                fontSize = fontSize,
                color = textColor.copy(alpha = textAlpha),
                fontWeight = labelFontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        
        // Copy Cat indicator in bottom-right corner
        if (copyCatIndicator != null) {
            val indicatorText = when (copyCatIndicator.type) {
                CopyCatIndicatorType.LEADER_PAW -> "${copyCatIndicator.number}🐾"
                CopyCatIndicatorType.FOLLOWER_CAT -> "${copyCatIndicator.number}🐱"
                CopyCatIndicatorType.NEXT_EXPECTED -> "🎯"
                CopyCatIndicatorType.BREAK_FLASH -> "✖️"
            }
            val indicatorFontSize = (cellHeightPx * 0.18f / density.fontScale).coerceIn(7f, 14f).sp
            
            // Break flash animation: 3 pulses for pattern break visibility
            val breakFlashAlpha = remember { Animatable(0f) }
            
            LaunchedEffect(copyCatIndicator.type) {
                if (copyCatIndicator.type == CopyCatIndicatorType.BREAK_FLASH) {
                    // Run 3 flash pulses (100ms fade in, 100ms fade out each)
                    repeat(3) {
                        breakFlashAlpha.animateTo(1f, animationSpec = tween(100, easing = FastOutSlowInEasing))
                        breakFlashAlpha.animateTo(0.3f, animationSpec = tween(100, easing = FastOutSlowInEasing))
                    }
                    // Hold at full visibility after animation
                    breakFlashAlpha.snapTo(1f)
                } else {
                    breakFlashAlpha.snapTo(1f)
                }
            }
            
            Text(
                text = indicatorText,
                fontSize = indicatorFontSize,
                color = when (copyCatIndicator.type) {
                    CopyCatIndicatorType.BREAK_FLASH -> Color.Red.copy(alpha = breakFlashAlpha.value)
                    CopyCatIndicatorType.NEXT_EXPECTED -> Color(0xFFFF9800) // Orange
                    else -> textColor
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            )
        }
        
        // Power Outage indicators in bottom-left corner
        // Show 💡 if this cell is part of outage recovery (player hit unique cells during outage)
        // Show 🔌# if this cell has 7+ hits (warning before outage triggers)
        val powerOutageWarningThreshold = com.air.pong.core.game.PowerOutageConstants.WARNING_THRESHOLD
        val powerOutageTrigger = com.air.pong.core.game.PowerOutageConstants.TRIGGER_HITS
        
        if (isOutageRecoveryCell) {
            // 💡 - Recovery progress indicator
            val lightbulbFontSize = (cellHeightPx * 0.25f / density.fontScale).coerceIn(10f, 18f).sp
            Text(
                text = "💡",
                fontSize = lightbulbFontSize,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
            )
        } else if (!isPowerOutage && cellHitCount >= powerOutageWarningThreshold) {
            // 🔌# - Warning indicator showing hits remaining until outage
            val hitsRemaining = powerOutageTrigger - cellHitCount
            val warningFontSize = (cellHeightPx * 0.18f / density.fontScale).coerceIn(8f, 14f).sp
            Text(
                text = "🔌$hitsRemaining",
                fontSize = warningFontSize,
                color = Color(0xFFE53935), // Red warning color
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
            )
        }
    }
}

// Color interpolation helper
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}

/**
 * Calculate SpinType from swipe gesture direction.
 * 
 * Direction mapping:
 * - Up → TOP (topspin)
 * - Down → BACK (backspin)  
 * - Left → LEFT (left sidespin)
 * - Right → RIGHT (right sidespin)
 * - Diagonals → TOP_RIGHT, TOP_LEFT, BACK_RIGHT, BACK_LEFT
 * - Tap (movement < threshold) → NONE
 */
private fun calculateSpinTypeFromSwipe(
    deltaX: Float,
    deltaY: Float,
    thresholdPx: Float
): SpinType {
    val absX = abs(deltaX)
    val absY = abs(deltaY)
    
    // If movement is too small in both directions, it's a tap → NONE
    if (absX < thresholdPx && absY < thresholdPx) {
        return SpinType.NONE
    }
    
    // Determine which directions exceeded threshold
    // Note: Y axis is inverted in screen coordinates (negative Y = up)
    val hasUp = deltaY < -thresholdPx
    val hasDown = deltaY > thresholdPx
    val hasLeft = deltaX < -thresholdPx
    val hasRight = deltaX > thresholdPx
    
    return when {
        hasUp && hasRight -> SpinType.TOP_RIGHT
        hasUp && hasLeft -> SpinType.TOP_LEFT
        hasDown && hasRight -> SpinType.BACK_RIGHT
        hasDown && hasLeft -> SpinType.BACK_LEFT
        hasUp -> SpinType.TOP
        hasDown -> SpinType.BACK
        hasLeft -> SpinType.LEFT
        hasRight -> SpinType.RIGHT
        else -> SpinType.NONE
    }
}
