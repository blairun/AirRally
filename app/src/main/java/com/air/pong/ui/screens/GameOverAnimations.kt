package com.air.pong.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.air.pong.ui.AvatarUtils
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Data class for a single particle
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    var scale: Float = 1f,
    val avatarIndex: Int,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f
)

@Composable

fun GameOverBackground(
    iWon: Boolean,
    isGameFinished: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Load avatar bitmaps once, scaled to 64x64 for performance
    val avatarBitmaps = remember(context) {
        AvatarUtils.avatarResources.mapNotNull { resId ->
            try {
                // Decode bounds first
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeResource(context.resources, resId, options)

                // Calculate sample size
                val targetSize = 64
                var inSampleSize = 1
                if (options.outHeight > targetSize || options.outWidth > targetSize) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= targetSize && (halfWidth / inSampleSize) >= targetSize) {
                        inSampleSize *= 2
                    }
                }

                // Decode with sample size
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                
                android.graphics.BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    if (avatarBitmaps.isEmpty()) return

    if (isGameFinished) {
        if (iWon) {
            WinnerAnimation(avatarBitmaps, modifier)
        } else {
            LoserAnimation(avatarBitmaps, modifier)
        }
    }
}

@Composable
fun WinnerAnimation(
    bitmaps: List<ImageBitmap>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val particles = remember { mutableStateListOf<Particle>() }
    
    // Animation loop
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        
        while (isActive) {
            withFrameNanos { frameTime ->
                val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTime
                
                // Spawn new particles (approx 1 per second)
                // We use a random chance based on delta time to achieve average rate
                if (Random.nextFloat() < 1.0f * deltaSeconds) {
                    val angle = Random.nextFloat() * Math.PI.toFloat() // 0 to PI (upwards arc)
                    // Bias towards up (PI/2)
                    val biasedAngle = (Math.PI.toFloat() / 4) + (Random.nextFloat() * Math.PI.toFloat() / 2)
                    
                    val speed = with(density) { Random.nextInt(500, 800).dp.toPx() }
                    
                    particles.add(
                        Particle(
                            x = 0.5f, // Normalized coordinates (0..1)
                            y = 1.0f,
                            vx = (cos(biasedAngle) * speed).toFloat(),
                            vy = (-sin(biasedAngle) * speed).toFloat(), // Negative is up
                            scale = Random.nextFloat() * 0.5f + 0.5f, // 0.5 to 1.0
                            avatarIndex = Random.nextInt(bitmaps.size),
                            rotation = Random.nextFloat() * 360f,
                            rotationSpeed = Random.nextFloat() * 100f - 50f
                        )
                    )
                }

                // Update particles
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.x += p.vx * deltaSeconds / 1000f // Normalize X roughly? No, let's use pixels for velocity but store normalized position? 
                    // Actually, let's use pixels for everything to be easier.
                    // Wait, we need screen size. Canvas gives us size.
                    // Let's store position in pixels in the drawing phase or pass size to update?
                    // Simpler: Store position in pixels, but we don't know size until draw.
                    // Solution: Store normalized position (0..1) for X/Y, but velocity in normalized units/sec?
                    // Or just handle physics in DrawScope? No, state update should be separate.
                    // Let's use a BoxWithConstraints or just assume a standard size for physics and scale?
                    // No, let's do the logic inside Canvas's onDraw but that's not stateful across frames easily.
                    // Best approach: Use a fixed coordinate system (e.g. 1000x1000) or normalized 0..1.
                    
                    // Let's use normalized coordinates for position.
                    // Velocity: 0.5 per second means crossing half screen in 1 second.
                    
                    // Gravity
                    p.vy += 0.5f * deltaSeconds // Gravity down
                    
                    p.x += p.vx * deltaSeconds
                    p.y += p.vy * deltaSeconds
                    
                    // Fade out
                    p.alpha -= 0.2f * deltaSeconds
                    
                    // Remove dead particles
                    if (p.y > 1.2f || p.alpha <= 0f) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Spawn logic needs to happen here or use a side effect with size knowledge?
        // Actually, let's move the simulation to the Canvas draw loop for simplicity if we don't need recomposition?
        // But we need to persist state.
        // Let's stick to the LaunchedEffect but use normalized coordinates.
        // For the "Sprinkler", we need velocity in normalized units.
        // If screen is vertical, vy should be higher relative to vx to look right?
        // Let's just tweak values.
    }
    
    // Re-implementing with a cleaner loop that handles screen size
    ParticleSystem(
        bitmaps = bitmaps,
        modifier = modifier,
        spawnRate = 4f,
        isRain = false
    )
}

@Composable
fun LoserAnimation(
    bitmaps: List<ImageBitmap>,
    modifier: Modifier = Modifier
) {
    ParticleSystem(
        bitmaps = bitmaps,
        modifier = modifier,
        spawnRate = 4f, // Slightly more rain
        isRain = true
    )
}

@Composable
fun ParticleSystem(
    bitmaps: List<ImageBitmap>,
    modifier: Modifier = Modifier,
    spawnRate: Float,
    isRain: Boolean
) {
    val particles = remember { mutableStateListOf<Particle>() }
    var time by remember { mutableStateOf(0L) }
    
    // We use a custom frame loop driving a state change to trigger redraw
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                time = frameTime - startTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val dt = 0.016f // Assume 60fps for physics step to keep it simple or use real delta if we tracked it
        // Actually, inside DrawScope we can't easily update state that triggers recomposition without infinite loop risk if not careful.
        // But we can update the mutable list which is stable? No, modifying list triggers recomposition.
        // Better pattern: Use a standard animation loop that updates state, and Canvas just reads it.
        
        // Let's go back to LaunchedEffect updating the list.
    }
    
    // Real implementation
    val density = LocalDensity.current
    
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        
        while (isActive) {
            withFrameNanos { frameTime ->
                val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTime
                
                // Spawn
                if (Random.nextFloat() < spawnRate * deltaSeconds) {
                    val bitmapIndex = Random.nextInt(bitmaps.size)
                    
                    if (isRain) {
                        // Rain: Top, random X, straight down
                        particles.add(
                            Particle(
                                x = Random.nextFloat(), // 0..1
                                y = -0.1f, // Just above screen
                                vx = 0f,
                                vy = 0.3f + Random.nextFloat() * 0.3f, // Speed down (increased)
                                scale = 0.5f + Random.nextFloat() * 0.5f, // Smaller for rain
                                avatarIndex = bitmapIndex,
                                rotation = Random.nextFloat() * 360f, // Random rotation
                                alpha = 0.3f // Muted
                            )
                        )
                    } else {
                        // Sprinkler: Bottom center, shooting up/out
                        val angle = (Math.PI.toFloat() / 3) + (Random.nextFloat() * Math.PI.toFloat() / 3) // 60 to 120 degrees
                        // Increased speed to reach top of screen (1.2 to 1.8)
                        val speed = 1.2f + Random.nextFloat() * 0.6f
                        
                        particles.add(
                            Particle(
                                x = 0.5f,
                                y = 1.1f,
                                vx = (cos(angle) * speed * 0.5f).toFloat() * (if (Random.nextBoolean()) 1 else -1), // Spread X
                                vy = -speed, // Up
                                scale = 0.5f + Random.nextFloat() * 0.5f,
                                avatarIndex = bitmapIndex,
                                rotationSpeed = Random.nextFloat() * 200f - 100f,
                                alpha = 0.3f
                            )
                        )
                    }
                }
                
                // Update
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    
                    if (isRain) {
                        p.y += p.vy * deltaSeconds
                    } else {
                        // Sprinkler gravity
                        p.vy += 1.5f * deltaSeconds
                        p.x += p.vx * deltaSeconds
                        p.y += p.vy * deltaSeconds
                        p.rotation += p.rotationSpeed * deltaSeconds
                    }
                    
                    // Remove if out of bounds
                    if (p.y > 1.2f) {
                        iterator.remove()
                    }
                }
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Read time to force recomposition every frame for smooth animation
        val currentFrameTime = time
        
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        particles.forEach { p ->
            val bitmap = bitmaps[p.avatarIndex]
            val scaledWidth = bitmap.width * p.scale
            val scaledHeight = bitmap.height * p.scale
            
            // Calculate pixel position
            val px = p.x * canvasWidth
            val py = p.y * canvasHeight
            
            // Draw
            // We need to center the bitmap at px, py
            val topLeft = IntOffset(
                (px - scaledWidth / 2).toInt(),
                (py - scaledHeight / 2).toInt()
            )
            
            // Apply rotation if needed
            if (p.rotation != 0f) {
                rotate(degrees = p.rotation, pivot = Offset(px, py)) {
                    drawImage(
                        image = bitmap,
                        dstOffset = topLeft,
                        dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                        alpha = p.alpha
                    )
                }
            } else {
                drawImage(
                    image = bitmap,
                    dstOffset = topLeft,
                    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                    alpha = p.alpha
                )
            }
        }
    }
}
