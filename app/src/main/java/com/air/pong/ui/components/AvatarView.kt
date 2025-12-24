package com.air.pong.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import com.air.pong.core.game.GameEvent
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animation types for avatar reactions.
 */
enum class AvatarAnimation {
    NONE,
    HAPPY_BOUNCE,
    SAD_SHAKE,
    SPIN
}

/**
 * Determines the avatar outline color and animation based on game state.
 * 
 * @param isMe Whether this is the local player's avatar
 * @param gameState Current game state
 * @return Pair of outline color and animation type
 */
@Composable
fun getAvatarState(isMe: Boolean, gameState: GameState): Pair<Color, AvatarAnimation> {
    if (gameState.gamePhase != GamePhase.POINT_SCORED) {
        return Color.White to AvatarAnimation.NONE
    }

    // RALLY MODE OVERRIDE: No winner/loser, just cooperative play.
    if (gameState.gameMode == GameMode.RALLY) {
        return Color.White to AvatarAnimation.NONE
    }

    // Find the PointScored event to see who won
    // We look at the last event. If it's PointScored, use it.
    val lastPointEvent = gameState.eventLog.lastOrNull { it is GameEvent.PointScored } as? GameEvent.PointScored
    
    val didIWin = lastPointEvent?.isYou == true
    
    return if (isMe) {
        if (didIWin) {
            Color(0xFF4CAF50) to AvatarAnimation.HAPPY_BOUNCE // Green, Happy
        } else {
            Color(0xFFF44336) to AvatarAnimation.SAD_SHAKE // Red, Sad
        }
    } else {
        // Opponent
        if (didIWin) {
            Color(0xFFF44336) to AvatarAnimation.NONE // Red (They lost)
        } else {
            Color(0xFF4CAF50) to AvatarAnimation.NONE // Green (They won)
        }
    }
}

/**
 * Displays an avatar image with animated effects based on game events.
 * 
 * @param avatarResId Resource ID for the avatar image
 * @param outlineColor Color for the circular border
 * @param animationType Animation to apply (bounce, shake, spin, or none)
 * @param modifier Modifier for positioning and sizing
 */
@Composable
fun AvatarView(
    avatarResId: Int,
    outlineColor: Color,
    animationType: AvatarAnimation,
    modifier: Modifier = Modifier
) {
    // Animation State
    val offsetY = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(animationType) {
        if (animationType == AvatarAnimation.HAPPY_BOUNCE) {
            // Reset other states
            rotation.snapTo(0f)
            offsetX.snapTo(0f)
            
            // Happy Bounce: Up and down
            // Loop indefinitely
            launch {
                while (true) {
                    // Initial jump
                    offsetY.animateTo(-30f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                    offsetY.animateTo(0f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
                    // Second jump
                    offsetY.animateTo(-20f, animationSpec = tween(250))
                    offsetY.animateTo(0f, animationSpec = tween(250))
                     // Third jump
                    offsetY.animateTo(-10f, animationSpec = tween(200))
                    offsetY.animateTo(0f, animationSpec = tween(200))
                    
                    // Pause between cycles
                    delay(500)
                }
            }
        } else if (animationType == AvatarAnimation.SAD_SHAKE) {
            // Reset other states
            rotation.snapTo(0f)
            offsetY.snapTo(0f)
            
            // Sad Shake: Sideways
            launch {
                val duration = 50
                val shakeOffset = 10f
                // Shake sequence
                repeat(3) {
                    offsetX.animateTo(-shakeOffset, animationSpec = tween(duration))
                    offsetX.animateTo(shakeOffset, animationSpec = tween(duration * 2))
                }
                offsetX.animateTo(0f, animationSpec = tween(duration))
            }
        } else if (animationType == AvatarAnimation.SPIN) {
            // Reset other states
            offsetY.snapTo(0f)
            offsetX.snapTo(0f)
            
            // Spin: Rotate 360 degrees continuously
            launch {
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
        } else {
            // Reset
            offsetY.snapTo(0f)
            offsetX.snapTo(0f)
            rotation.snapTo(0f)
        }
    }

    val outlineWidth = 6.dp // Avatar outline

    Image(
        painter = painterResource(id = avatarResId),
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .offset(x = offsetX.value.dp, y = offsetY.value.dp)
            .rotate(rotation.value)
            .clip(CircleShape)
            .border(outlineWidth, outlineColor, CircleShape)
    )
}

/**
 * Displays an avatar image with an optional background ring and animated effects.
 * The ring moves with shake/bounce but does NOT rotate with spin animation.
 * 
 * @param avatarResId Resource ID for the avatar image
 * @param ringResId Resource ID for the ring image, or null for no ring
 * @param outlineColor Color for the circular border
 * @param animationType Animation to apply (bounce, shake, spin, or none)
 * @param modifier Modifier for positioning and sizing
 * @param avatarSize Size of the avatar (ring will be larger based on RING_SIZE_RATIO)
 */
@Composable
fun AvatarViewWithRing(
    avatarResId: Int,
    ringResId: Int?,
    outlineColor: Color,
    animationType: AvatarAnimation,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 80.dp,
    forceRingSizing: Boolean = false  // When true, reserve ring space even without a ring
) {
    // If forceRingSizing or has ring, use ring-sized container for consistent alignment
    val hasRing = ringResId != null
    val useRingLayout = hasRing || forceRingSizing
    val ringSize = avatarSize * com.air.pong.ui.RingUtils.RING_SIZE_RATIO
    
    // Animation State
    val offsetY = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(animationType) {
        if (animationType == AvatarAnimation.HAPPY_BOUNCE) {
            rotation.snapTo(0f)
            offsetX.snapTo(0f)
            
            launch {
                while (true) {
                    offsetY.animateTo(-30f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                    offsetY.animateTo(0f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
                    offsetY.animateTo(-20f, animationSpec = tween(250))
                    offsetY.animateTo(0f, animationSpec = tween(250))
                    offsetY.animateTo(-10f, animationSpec = tween(200))
                    offsetY.animateTo(0f, animationSpec = tween(200))
                    delay(500)
                }
            }
        } else if (animationType == AvatarAnimation.SAD_SHAKE) {
            rotation.snapTo(0f)
            offsetY.snapTo(0f)
            
            launch {
                val duration = 50
                val shakeOffset = 10f
                repeat(3) {
                    offsetX.animateTo(-shakeOffset, animationSpec = tween(duration))
                    offsetX.animateTo(shakeOffset, animationSpec = tween(duration * 2))
                }
                offsetX.animateTo(0f, animationSpec = tween(duration))
            }
        } else if (animationType == AvatarAnimation.SPIN) {
            offsetY.snapTo(0f)
            offsetX.snapTo(0f)
            
            launch {
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
        } else {
            offsetY.snapTo(0f)
            offsetX.snapTo(0f)
            rotation.snapTo(0f)
        }
    }

    val outlineWidth = 6.dp

    // Container with offset for shake/bounce (ring moves with avatar)
    // Use ring size for container when forceRingSizing to maintain consistent alignment
    val containerSize = if (useRingLayout) ringSize else avatarSize
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(containerSize)
            .offset(x = offsetX.value.dp, y = offsetY.value.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        // Ring layer (behind avatar, does NOT rotate)
        if (ringResId != null) {
            Image(
                painter = painterResource(id = ringResId),
                contentDescription = "Background ring",
                modifier = Modifier.size(ringSize),
                contentScale = ContentScale.Fit
            )
        }
        
        // Avatar layer (on top, with rotation)
        Image(
            painter = painterResource(id = avatarResId),
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .rotate(rotation.value)
                .clip(CircleShape)
                .border(outlineWidth, outlineColor, CircleShape)
        )
    }
}
