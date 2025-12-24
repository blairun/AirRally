package com.air.pong.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Displays two avatars overlapping for Rally mode.
 * The active player's avatar animates to the front.
 * Avatars spin when players use spin shots.
 * 
 * Layout: Your avatar on left, partner's avatar peeking (~1/3 visible) from behind on right.
 * When it's your turn, your avatar is in front.
 * When it's partner's turn, partner avatar animates to front.
 * 
 * Spin Animation:
 * - Left avatar spins clockwise (toward center)
 * - Right avatar spins counter-clockwise (toward center)
 * - Spin speed increases with more distinct spin types used
 * 
 * @param myAvatarResId Resource ID for local player's avatar
 * @param partnerAvatarResId Resource ID for partner's avatar  
 * @param isMyTurn Whether it's currently the local player's turn
 * @param mySpinTypeCount Number of distinct spin types I've used (0-8)
 * @param myAvatarSpinEndTime System time when my avatar should stop spinning (0 = not spinning)
 * @param myIsSpinningIndefinitely Whether my avatar spins forever (achieved 10 types)
 * @param modifier Modifier for positioning
 */
@Composable
fun RallyAvatarPair(
    myAvatarResId: Int,
    partnerAvatarResId: Int,
    isMyTurn: Boolean,
    mySpinTypeCount: Int = 0,
    myAvatarSpinEndTime: Long = 0L,
    myIsSpinningIndefinitely: Boolean = false,
    partnerSpinTypeCount: Int = 0,
    partnerAvatarSpinEndTime: Long = 0L,
    partnerIsSpinningIndefinitely: Boolean = false,
    myHasShield: Boolean = false,
    myRingResId: Int? = null,
    partnerRingResId: Int? = null,
    modifier: Modifier = Modifier
) {
    // Animation states for z-order transition
    val myZIndex = remember { Animatable(if (isMyTurn) 1f else 0f) }
    val partnerZIndex = remember { Animatable(if (isMyTurn) 0f else 1f) }
    
    // Scale animation - active avatar is slightly larger
    val myScale = remember { Animatable(if (isMyTurn) 1.0f else 0.88f) }
    val partnerScale = remember { Animatable(if (isMyTurn) 0.88f else 1.0f) }
    
    // X offset animation - active avatar slides slightly forward (toward center)
    val myOffsetX = remember { Animatable(if (isMyTurn) 8f else 0f) }
    val partnerOffsetX = remember { Animatable(if (isMyTurn) 0f else -8f) }
    
    // Spin animation state
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        // Update current time periodically to check spin timer
        while (true) {
            currentTime.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(100)
        }
    }
    
    // Determine if my avatar should be spinning
    val isMyAvatarSpinning = myIsSpinningIndefinitely || 
        (myAvatarSpinEndTime > 0L && currentTime.value < myAvatarSpinEndTime)
    
    // Spin rotation animation
    // Speed increases with more spin types: base 3s per rotation, down to 0.5s at 8 types
    val spinDurationMs = if (mySpinTypeCount > 0) {
        (3000 - (mySpinTypeCount * 300)).coerceAtLeast(500)
    } else 3000
    
    // Use Animatable instead of infiniteRepeatable for proper state change handling
    val myRotation = remember { Animatable(0f) }
    
    // Left avatar spins clockwise (positive rotation)
    LaunchedEffect(isMyAvatarSpinning, spinDurationMs) {
        if (isMyAvatarSpinning) {
            // Continuous spin animation using manual loop
            while (true) {
                myRotation.animateTo(
                    targetValue = myRotation.value + 360f,
                    animationSpec = tween(durationMillis = spinDurationMs, easing = LinearEasing)
                )
            }
        } else {
            // Reset rotation when not spinning
            myRotation.snapTo(0f)
        }
    }
    // Determine if partner avatar should be spinning (synced via network)
    val isPartnerAvatarSpinning = partnerIsSpinningIndefinitely || 
        (partnerAvatarSpinEndTime > 0L && currentTime.value < partnerAvatarSpinEndTime)
    
    // Partner spin speed - also increases with spin type count
    val partnerSpinDurationMs = if (partnerSpinTypeCount > 0) {
        (3000 - (partnerSpinTypeCount * 300)).coerceAtLeast(500)
    } else 3000
    
    // Right avatar spins counter-clockwise (negative rotation)
    val partnerRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isPartnerAvatarSpinning, partnerSpinDurationMs) {
        if (isPartnerAvatarSpinning) {
            while (true) {
                partnerRotation.animateTo(
                    targetValue = partnerRotation.value - 360f, // Counter-clockwise
                    animationSpec = tween(durationMillis = partnerSpinDurationMs, easing = LinearEasing)
                )
            }
        } else {
            partnerRotation.snapTo(0f)
        }
    }
    
    // Animate when turn changes
    LaunchedEffect(isMyTurn) {
        coroutineScope {
            if (isMyTurn) {
                // My turn - bring my avatar to front
                launch { myZIndex.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerZIndex.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { myScale.animateTo(1.0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerScale.animateTo(0.88f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { myOffsetX.animateTo(8f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerOffsetX.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
            } else {
                // Partner's turn - bring partner avatar to front
                launch { myZIndex.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerZIndex.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { myScale.animateTo(0.88f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerScale.animateTo(1.0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { myOffsetX.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
                launch { partnerOffsetX.animateTo(-8f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
            }
        }
    }
    
    // Dynamically shrink avatars when either player has a ring, to maintain consistent overall size
    // When no rings: avatarSize = 100.dp, total = 100.dp
    // When rings: avatarSize shrinks so ring (avatar * RING_SIZE_RATIO) = 100.dp
    val hasAnyRing = myRingResId != null || partnerRingResId != null
    val baseSize = 100.dp
    val avatarSize = if (hasAnyRing) {
        // Shrink avatar so the ring fills the same space as avatar would without ring
        baseSize / com.air.pong.ui.RingUtils.RING_SIZE_RATIO
    } else {
        baseSize
    }
    val ringSize = baseSize // Ring always fills the base size space
    val overlap = 60.dp // How much avatars overlap (partner peeks ~1/3)
    val outlineWidth = 5.dp
    
    // Active player gets a brighter outline
    val myOutlineColor = if (isMyTurn) Color.White else Color.White.copy(alpha = 0.6f)
    val partnerOutlineColor = if (!isMyTurn) Color.White else Color.White.copy(alpha = 0.6f)
    
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // My avatar (left side, positioned at -overlap/2)
        Box(
            modifier = Modifier
                .offset(x = (-overlap / 2) + myOffsetX.value.dp)
                .zIndex(myZIndex.value)
                .scale(myScale.value),
            contentAlignment = Alignment.Center
        ) {
            // Ring layer (behind shield and avatar, does NOT rotate)
            if (myRingResId != null) {
                Image(
                    painter = painterResource(id = myRingResId),
                    contentDescription = "My Ring",
                    modifier = Modifier.size(ringSize),
                    contentScale = ContentScale.Fit
                )
            }
            // Avatar image
            Image(
                painter = painterResource(id = myAvatarResId),
                contentDescription = "Your Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .graphicsLayer { rotationZ = myRotation.value }
                    .clip(CircleShape)
                    .border(outlineWidth, myOutlineColor, CircleShape)
            )
            // Shield overlay (rendered ON TOP of avatar for force field effect)
            if (myHasShield) {
                ShieldOverlay(
                    size = avatarSize + 16.dp,
                    rotation = myRotation.value
                )
            }
        }
        
        // Partner avatar (right side, positioned at +overlap/2, peeks from behind)
        Box(
            modifier = Modifier
                .offset(x = (overlap / 2) + partnerOffsetX.value.dp)
                .zIndex(partnerZIndex.value)
                .scale(partnerScale.value),
            contentAlignment = Alignment.Center
        ) {
            // Ring layer (behind avatar, does NOT rotate)
            if (partnerRingResId != null) {
                Image(
                    painter = painterResource(id = partnerRingResId),
                    contentDescription = "Partner Ring",
                    modifier = Modifier.size(ringSize),
                    contentScale = ContentScale.Fit
                )
            }
            // Partner avatar image
            Image(
                painter = painterResource(id = partnerAvatarResId),
                contentDescription = "Partner Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .graphicsLayer {
                        rotationZ = partnerRotation.value
                    }
                    .clip(CircleShape)
                    .border(outlineWidth, partnerOutlineColor, CircleShape)
            )
        }
    }
}

