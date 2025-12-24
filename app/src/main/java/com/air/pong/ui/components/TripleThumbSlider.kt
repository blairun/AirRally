package com.air.pong.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.air.pong.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A custom slider with three draggable thumbs for Soft, Medium, and Hard thresholds.
 * 
 * @param softValue Current value for the soft threshold
 * @param mediumValue Current value for the medium threshold
 * @param hardValue Current value for the hard threshold
 * @param onSoftChange Callback when soft threshold changes
 * @param onMediumChange Callback when medium threshold changes
 * @param onHardChange Callback when hard threshold changes
 * @param onValueChangeFinished Callback when any value change is finished (finger lifted)
 * @param valueRange The range of valid values (default 10..60)
 * @param minGap Minimum gap between adjacent thresholds (default 5)
 * @param enabled Whether the slider is enabled
 */
@Composable
fun TripleThumbSlider(
    softValue: Float,
    mediumValue: Float,
    hardValue: Float,
    onSoftChange: (Float) -> Unit,
    onMediumChange: (Float) -> Unit,
    onHardChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 10f..60f,
    minGap: Float = 5f,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 12.dp.toPx() }
    val trackHeight = with(density) { 4.dp.toPx() }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    
    // Track which thumb is being dragged (0=none, 1=soft, 2=medium, 3=hard)
    var draggingThumb by remember { mutableIntStateOf(0) }
    
    // Store current values for use in gesture handler (avoids stale closure)
    val currentSoft by rememberUpdatedState(softValue)
    val currentMedium by rememberUpdatedState(mediumValue)
    val currentHard by rememberUpdatedState(hardValue)
    
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) { // Use Unit as key to avoid recomposition issues
                    if (!enabled) return@pointerInput
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val range = valueRange.endInclusive - valueRange.start
                            
                            // Calculate positions of each thumb using current state
                            val softPos = ((currentSoft - valueRange.start) / range) * width
                            val mediumPos = ((currentMedium - valueRange.start) / range) * width
                            val hardPos = ((currentHard - valueRange.start) / range) * width
                            
                            // Find closest thumb to touch point
                            val touchX = offset.x
                            val softDist = abs(touchX - softPos)
                            val mediumDist = abs(touchX - mediumPos)
                            val hardDist = abs(touchX - hardPos)
                            
                            // Only start drag if within reasonable distance to a thumb
                            val maxDist = thumbRadiusPx * 3
                            draggingThumb = when {
                                softDist <= mediumDist && softDist <= hardDist && softDist < maxDist -> 1
                                mediumDist <= hardDist && mediumDist < maxDist -> 2
                                hardDist < maxDist -> 3
                                else -> 0
                            }
                        },
                        onDragEnd = {
                            if (draggingThumb != 0) {
                                onValueChangeFinished()
                            }
                            draggingThumb = 0
                        },
                        onDragCancel = {
                            draggingThumb = 0
                        },
                        onDrag = { change, _ ->
                            if (draggingThumb == 0) return@detectDragGestures
                            
                            val width = size.width.toFloat()
                            val range = valueRange.endInclusive - valueRange.start
                            val newValue = valueRange.start + (change.position.x / width) * range
                            
                            // Snap to integer values
                            val snappedValue = newValue.roundToInt().toFloat()
                            
                            when (draggingThumb) {
                                1 -> { // Soft - can go as low as needed, just stay below medium - gap
                                    val minVal = valueRange.start
                                    val maxVal = currentMedium - minGap
                                    val clampedValue = snappedValue.coerceIn(minVal, maxVal)
                                    onSoftChange(clampedValue)
                                }
                                2 -> { // Medium - must stay between soft + gap and hard - gap
                                    val minVal = currentSoft + minGap
                                    val maxVal = currentHard - minGap
                                    val clampedValue = snappedValue.coerceIn(minVal, maxVal)
                                    onMediumChange(clampedValue)
                                }
                                3 -> { // Hard - can go from medium + gap to max
                                    val minVal = currentMedium + minGap
                                    val maxVal = valueRange.endInclusive
                                    val clampedValue = snappedValue.coerceIn(minVal, maxVal)
                                    onHardChange(clampedValue)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2
                val range = valueRange.endInclusive - valueRange.start
                
                // Calculate thumb positions
                val softPos = ((softValue - valueRange.start) / range) * width
                val mediumPos = ((mediumValue - valueRange.start) / range) * width
                val hardPos = ((hardValue - valueRange.start) / range) * width
                
                val trackColor = if (enabled) surfaceVariant else disabledColor.copy(alpha = 0.2f)
                
                // Draw inactive track only (no active track between thumbs)
                drawLine(
                    color = trackColor,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = trackHeight
                )
                
                // Draw thumbs (just dots, no connecting line)
                val thumbColor = if (enabled) primaryColor else disabledColor
                val activeThumbRadius = thumbRadiusPx * 1.3f
                
                // Soft thumb
                drawCircle(
                    color = thumbColor,
                    radius = if (draggingThumb == 1) activeThumbRadius else thumbRadiusPx,
                    center = Offset(softPos, centerY)
                )
                
                // Medium thumb
                drawCircle(
                    color = thumbColor,
                    radius = if (draggingThumb == 2) activeThumbRadius else thumbRadiusPx,
                    center = Offset(mediumPos, centerY)
                )
                
                // Hard thumb
                drawCircle(
                    color = thumbColor,
                    radius = if (draggingThumb == 3) activeThumbRadius else thumbRadiusPx,
                    center = Offset(hardPos, centerY)
                )
            }
        }
        
        // Labels row - positioned absolutely based on thumb positions
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val totalWidth = maxWidth
            val range = valueRange.endInclusive - valueRange.start
            val softFraction = (softValue - valueRange.start) / range
            val mediumFraction = (mediumValue - valueRange.start) / range
            val hardFraction = (hardValue - valueRange.start) / range
            
            val labelColor = if (enabled) MaterialTheme.colorScheme.onSurface else disabledColor
            
            // Soft label - offset to stay visible
            val softOffset = (totalWidth * softFraction).coerceIn(24.dp, totalWidth - 24.dp)
            Text(
                text = stringResource(R.string.slider_soft_fmt, softValue.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .offset(x = softOffset - 24.dp)
                    .width(48.dp)
            )
            
            // Medium label
            val mediumOffset = (totalWidth * mediumFraction).coerceIn(24.dp, totalWidth - 24.dp)
            Text(
                text = stringResource(R.string.slider_med_fmt, mediumValue.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .offset(x = mediumOffset - 24.dp)
                    .width(48.dp)
            )
            
            // Hard label
            val hardOffset = (totalWidth * hardFraction).coerceIn(24.dp, totalWidth - 24.dp)
            Text(
                text = stringResource(R.string.slider_hard_fmt, hardValue.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .offset(x = hardOffset - 24.dp)
                    .width(48.dp)
            )
        }
    }
}
