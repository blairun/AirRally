package com.air.pong.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * VS intro overlay shown at the start of Classic mode games.
 * Displays both avatars gliding toward center from random angles,
 * clashing twice with sound, then returning to their positions.
 * 
 * @param myAvatarResId Resource ID for local player's avatar
 * @param opponentAvatarResId Resource ID for opponent's avatar
 * @param onPlayVsSound Callback to play the VS sound effect
 * @param onComplete Callback when the animation sequence is finished
 */
@Composable
fun VsIntroOverlay(
    myAvatarResId: Int,
    opponentAvatarResId: Int,
    onPlayVsSound: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation state for avatar positions (0 = starting position, 1 = center clash position)
    val leftAvatarProgress = remember { Animatable(0f) }
    val rightAvatarProgress = remember { Animatable(0f) }
    
    // Rotation for clash effect
    val leftRotation = remember { Animatable(0f) }
    val rightRotation = remember { Animatable(0f) }
    
    // Offset for clash "bump" effect
    val clashBump = remember { Animatable(0f) }
    
    // VS text visibility
    var showVsText by remember { mutableStateOf(false) }
    
    // Random approach angles - generated once per overlay instance
    // Y offset range: -150 to 150 (can come from above or below)
    val leftStartYOffset = remember { Random.nextFloat() * 300f - 150f }
    val rightStartYOffset = remember { Random.nextFloat() * 300f - 150f }
    
    // Random approach rotation (tilted as they fly in): -45 to 45 degrees
    val leftApproachRotation = remember { Random.nextFloat() * 90f - 45f }
    val rightApproachRotation = remember { Random.nextFloat() * 90f - 45f }
    
    LaunchedEffect(Unit) {
        // Phase 1: Glide avatars toward center (300ms)
        launch { leftAvatarProgress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        launch { rightAvatarProgress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        // Straighten rotation as they approach
        launch { leftRotation.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        launch { rightRotation.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        
        // Phase 2: First clash (200ms)
        onPlayVsSound()
        delay(250)
        
        // Show VS text
        showVsText = true
        
        launch { 
            leftRotation.animateTo(20f, animationSpec = tween(100))
            leftRotation.animateTo(0f, animationSpec = tween(100))
        }
        launch { 
            rightRotation.animateTo(-20f, animationSpec = tween(100))
            rightRotation.animateTo(0f, animationSpec = tween(100))
        }
        launch {
            clashBump.animateTo(1f, animationSpec = tween(100))
            clashBump.animateTo(0f, animationSpec = tween(100))
        }
        delay(200)
        
        // Brief pause (200ms)
        delay(200)
        
        // Phase 3: Second clash (200ms)
        onPlayVsSound()
        launch { 
            leftRotation.animateTo(20f, animationSpec = tween(100))
            leftRotation.animateTo(0f, animationSpec = tween(100))
        }
        launch { 
            rightRotation.animateTo(-20f, animationSpec = tween(100))
            rightRotation.animateTo(0f, animationSpec = tween(100))
        }
        launch {
            clashBump.animateTo(1f, animationSpec = tween(100))
            clashBump.animateTo(0f, animationSpec = tween(100))
        }
        delay(250)
        
        // Brief hold (300ms)
        delay(300)
        
        // Phase 4: Return to original positions with tilted fly-out (300ms)
        showVsText = false
        launch { leftAvatarProgress.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        launch { rightAvatarProgress.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        // Tilt back to approach rotation as they fly out
        launch { leftRotation.animateTo(leftApproachRotation, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        launch { rightRotation.animateTo(rightApproachRotation, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        delay(300)
        
        // Complete
        onComplete()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        // Calculate offsets - avatars start far apart and move toward center
        // At progress=0: offset is large (far from center, with Y offset for angle)
        // At progress=1: offset is small (close to center, overlapping, Y=0)
        val maxXOffset = 150.dp
        val overlapOffset = 30.dp // How much they overlap at center
        
        // X offset interpolates from far to center
        val leftXOffset = maxXOffset * (1f - leftAvatarProgress.value) - (overlapOffset * leftAvatarProgress.value) + (12.dp * clashBump.value)
        val rightXOffset = maxXOffset * (1f - rightAvatarProgress.value) - (overlapOffset * rightAvatarProgress.value) + (12.dp * clashBump.value)
        
        // Y offset interpolates from random start position to center (0)
        val leftYOffset = leftStartYOffset * (1f - leftAvatarProgress.value)
        val rightYOffset = rightStartYOffset * (1f - rightAvatarProgress.value)
        
        // Combined rotation: approach tilt (fades in) + clash rotation (handled by animatable)
        val leftTotalRotation = leftApproachRotation * (1f - leftAvatarProgress.value) + leftRotation.value
        val rightTotalRotation = rightApproachRotation * (1f - rightAvatarProgress.value) + rightRotation.value
        
        // Column to stack avatars and VS text vertically
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatars in a Box (not Row) so they can overlap
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(140.dp)
            ) {
                // Left avatar (me)
                Image(
                    painter = painterResource(id = myAvatarResId),
                    contentDescription = "My Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = -leftXOffset, y = leftYOffset.dp)
                        .rotate(leftTotalRotation)
                        .clip(CircleShape)
                        .border(6.dp, Color(0xFF4CAF50), CircleShape)
                )
                
                // Right avatar (opponent)
                Image(
                    painter = painterResource(id = opponentAvatarResId),
                    contentDescription = "Opponent Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = rightXOffset, y = rightYOffset.dp)
                        .rotate(rightTotalRotation)
                        .clip(CircleShape)
                        .border(6.dp, Color(0xFFF44336), CircleShape)
                )
            }
            
            // VS text below avatars
            if (showVsText) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "VS",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

