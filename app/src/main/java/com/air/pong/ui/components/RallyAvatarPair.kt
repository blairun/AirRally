package com.air.pong.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Displays two avatars overlapping for Rally mode.
 * The active player's avatar animates to the front.
 * 
 * Layout: Your avatar on left, partner's avatar peeking (~1/3 visible) from behind on right.
 * When it's your turn, your avatar is in front.
 * When it's partner's turn, partner avatar animates to front.
 * 
 * @param myAvatarResId Resource ID for local player's avatar
 * @param partnerAvatarResId Resource ID for partner's avatar  
 * @param isMyTurn Whether it's currently the local player's turn
 * @param modifier Modifier for positioning
 */
@Composable
fun RallyAvatarPair(
    myAvatarResId: Int,
    partnerAvatarResId: Int,
    isMyTurn: Boolean,
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
    
    val avatarSize = 100.dp
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
        Image(
            painter = painterResource(id = myAvatarResId),
            contentDescription = "Your Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .offset(x = (-overlap / 2) + myOffsetX.value.dp)
                .zIndex(myZIndex.value)
                .scale(myScale.value)
                .clip(CircleShape)
                .border(outlineWidth, myOutlineColor, CircleShape)
        )
        
        // Partner avatar (right side, positioned at +overlap/2, peeks from behind)
        Image(
            painter = painterResource(id = partnerAvatarResId),
            contentDescription = "Partner Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .offset(x = (overlap / 2) + partnerOffsetX.value.dp)
                .zIndex(partnerZIndex.value)
                .scale(partnerScale.value)
                .clip(CircleShape)
                .border(outlineWidth, partnerOutlineColor, CircleShape)
        )
    }
}
