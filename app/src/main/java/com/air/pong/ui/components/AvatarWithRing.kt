package com.air.pong.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.air.pong.ui.RingUtils

/**
 * Composable that renders an avatar with an optional background ring.
 * 
 * The ring:
 * - Is positioned behind the avatar
 * - Scales according to RingUtils.RING_SIZE_RATIO
 * - Moves with the avatar (shake/bounce animations)
 * - Does NOT rotate with the avatar (spin animations)
 * 
 * @param avatarResId Resource ID of the avatar drawable
 * @param ringResId Resource ID of the ring drawable, or null for no ring
 * @param size The size of the avatar (ring will be larger based on RING_SIZE_RATIO)
 * @param modifier Modifier applied to the container
 * @param avatarRotation Rotation angle for the avatar (ring stays fixed)
 * @param offsetX Horizontal offset for shake/bounce (applies to both)
 * @param offsetY Vertical offset for shake/bounce (applies to both)
 */
@Composable
fun AvatarWithRing(
    avatarResId: Int,
    ringResId: Int?,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarRotation: Float = 0f,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp
) {
    val ringSize = size * RingUtils.RING_SIZE_RATIO
    // Calculate the extra space needed for the ring
    val containerSize = ringSize
    
    Box(
        modifier = modifier
            .size(containerSize)
            .offset(x = offsetX, y = offsetY),
        contentAlignment = Alignment.Center
    ) {
        // Ring layer (behind avatar, no rotation)
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
            contentDescription = "Player avatar",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = avatarRotation
                }
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Simplified version without offset parameters for static displays.
 */
@Composable
fun AvatarWithRing(
    avatarResId: Int,
    ringResId: Int?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    AvatarWithRing(
        avatarResId = avatarResId,
        ringResId = ringResId,
        size = size,
        modifier = modifier,
        avatarRotation = 0f,
        offsetX = 0.dp,
        offsetY = 0.dp
    )
}
