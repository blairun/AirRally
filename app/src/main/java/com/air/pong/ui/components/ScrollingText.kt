package com.air.pong.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.air.pong.core.game.GameMode

private const val TIP_SEPARATOR = "                     "
private const val TIPS_TO_SHOW = 8 // Number of tips to chain together
private const val FADE_IN_DURATION_MS = 500

/**
 * A composable that continuously displays random scrolling tips for the given game mode.
 * Uses a classic marquee/ticker approach - concatenates multiple tips into one long
 * scrolling string for smooth, continuous display.
 * 
 * Creates a seamless loop by duplicating the tips, so when the first set scrolls off,
 * the second set is already visible, and the animation loops back seamlessly.
 * 
 * Fades in when first displayed.
 */
@Composable
fun ScrollingTipsDisplay(
    gameMode: GameMode,
    isSolo: Boolean = false,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = Color.White
) {
    // Track container width for offset calculations
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var textWidth by remember { mutableFloatStateOf(0f) }
    
    // Fade-in animation
    val fadeAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FADE_IN_DURATION_MS,
                easing = EaseIn
            )
        )
    }
    
    // Get applicable tips for this mode
    val applicableTips = remember(gameMode, isSolo) {
        ScrollingTipsProvider.getTipsForMode(gameMode, isSolo)
    }
    
    // Build a list of tip resource IDs to show (doubled for seamless loop)
    val tipResIds: List<Int> = remember(gameMode, isSolo, applicableTips) {
        if (applicableTips.isEmpty()) emptyList()
        else {
            val shuffled = applicableTips.shuffled()
            // Create base set of tips
            val baseTips = List(TIPS_TO_SHOW) { i -> shuffled[i % shuffled.size] }
            // Double them for seamless looping
            baseTips + baseTips
        }
    }
    
    // Build display text from resource IDs (must be in Composable context)
    val displayText = buildString {
        tipResIds.forEachIndexed { index, resId ->
            if (index > 0) append(TIP_SEPARATOR)
            append(stringResource(resId))
        }
    }
    
    // Calculate the width of half the content (for seamless loop math)
    // We'll scroll exactly half the text width so the loop is seamless
    val halfTextWidth = textWidth / 2f
    
    // Calculate scroll duration based on half text length (we only scroll half)
    val scrollDurationMs = remember(displayText) {
        // ~80ms per character for comfortable reading speed
        ((displayText.length / 2) * 80).coerceIn(10000, 60000)
    }
    
    // Only animate when we have measurements
    val isReady = containerWidth > 0 && textWidth > 0
    
    // Infinite scrolling animation
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = scrollDurationMs,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marqueeProgress"
    )
    
    // Calculate offset:
    // - Start at right edge of container (containerWidth)
    // - Scroll left by (halfTextWidth * progress)
    // - When progress hits 1, we've scrolled exactly half the text
    // - Loop restarts at 0, which looks seamless because the second half is identical to the first
    val actualOffset = if (isReady) {
        containerWidth - (progress * halfTextWidth)
    } else {
        10000f // Off-screen until ready
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width.toFloat()
            }
    ) {
        Text(
            text = displayText,
            style = style,
            color = color.copy(alpha = color.alpha * fadeAlpha.value),
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .offset { IntOffset(actualOffset.toInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size.width > 0) {
                        textWidth = coordinates.size.width.toFloat()
                    }
                }
        )
    }
}
