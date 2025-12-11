package com.air.pong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.core.game.SwingType
import kotlinx.coroutines.delay

@Composable
fun RallyGrid(
    gridState: List<Boolean>,
    cellTiers: List<Int> = List(9) { 0 },
    modifier: Modifier = Modifier
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
                        
                        RallyGridCell(
                            isMarked = isMarked,
                            topLabel = forceLabel,
                            bottomLabel = typeLabel,
                            tierIndex = tierIndex,
                            isNeededForTier = isNeededForTier,
                            tierJustCompleted = tierJustCompleted,
                            modifier = Modifier.weight(1f).fillMaxHeight()
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
    modifier: Modifier
) {
    // Get the point value from GameEngine's POINT_TIERS constant
    val tierPoints = com.air.pong.core.game.GameEngine.POINT_TIERS.getOrElse(tierIndex) { 1 }
    
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
    val baseColor = when {
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
    
    // Border logic - thick border animation takes priority, then standard pulse
    val borderColor = when {
        showThickBorder && !isMarked -> Color(0xFFFFB300).copy(alpha = thickBorderAlpha.value)
        isNeededForTier && !isMarked -> Color(0xFFFFB300).copy(alpha = pulseAlpha)
        else -> Color.Transparent
    }
    val borderWidth = when {
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

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
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
