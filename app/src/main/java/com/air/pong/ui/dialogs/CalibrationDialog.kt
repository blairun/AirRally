package com.air.pong.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.air.pong.R
import com.air.pong.core.game.GameEngine
import com.air.pong.core.sensors.SensorProvider.SwingEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Calibration phases for the wizard flow.
 */
enum class CalibrationPhase {
    INSTRUCTIONS,
    SOFT_1, SOFT_2, SOFT_3,
    MEDIUM_1, MEDIUM_2, MEDIUM_3,
    HARD_1, HARD_2, HARD_3,
    CALCULATING,
    SUCCESS,
    FAILURE
}

/**
 * Result of calibration: either success with new thresholds or failure with a reason.
 */
sealed class CalibrationResult {
    data class Success(val softThreshold: Float, val mediumThreshold: Float, val hardThreshold: Float) : CalibrationResult()
    data class Failure(val reason: String) : CalibrationResult()
}

/**
 * Full-screen calibration dialog that guides the user through 9 swings
 * (3 soft, 3 medium, 3 hard) to calibrate their swing thresholds.
 */
@Composable
fun CalibrationDialog(
    swingEvents: Flow<SwingEvent>,
    onPlayBounceSound: () -> Unit,
    onResult: (CalibrationResult) -> Unit,
    onDismiss: (accepted: Boolean) -> Unit  // true if user clicked Accept, false if cancelled/X
) {
    var phase by remember { mutableStateOf(CalibrationPhase.INSTRUCTIONS) }
    var calibrationResult by remember { mutableStateOf<CalibrationResult?>(null) }
    
    // Collected swing forces
    val softSwings = remember { mutableStateListOf<Float>() }
    val mediumSwings = remember { mutableStateListOf<Float>() }
    val hardSwings = remember { mutableStateListOf<Float>() }
    
    // For showing checkmark feedback briefly
    var showCheckmark by remember { mutableStateOf(false) }
    
    // Listen for swing events during active phases
    LaunchedEffect(phase) {
        if (phase.isSwingingPhase()) {
            swingEvents.collect { event ->
                // Record the swing force
                when {
                    phase.isSoftPhase() -> softSwings.add(event.force)
                    phase.isMediumPhase() -> mediumSwings.add(event.force)
                    phase.isHardPhase() -> hardSwings.add(event.force)
                }
                
                // Play feedback sound
                onPlayBounceSound()
                
                // Show checkmark briefly
                showCheckmark = true
                delay(300)
                showCheckmark = false
                delay(200) // Brief pause before next phase
                
                // Advance to next phase
                phase = phase.next()
            }
        }
    }
    
    // Separate effect to handle CALCULATING phase
    LaunchedEffect(phase) {
        if (phase == CalibrationPhase.CALCULATING) {
            delay(500) // Brief "calculating" display
            val result = calculateThresholds(softSwings, mediumSwings, hardSwings)
            calibrationResult = result
            phase = if (result is CalibrationResult.Success) {
                CalibrationPhase.SUCCESS
            } else {
                CalibrationPhase.FAILURE
            }
            onResult(result)
        }
    }
    
    Dialog(
        onDismissRequest = {
            // Only allow dismiss via cancel button, not back press during swinging
            if (phase == CalibrationPhase.INSTRUCTIONS || phase == CalibrationPhase.SUCCESS || phase == CalibrationPhase.FAILURE) {
                onDismiss(false)  // Back press = cancel
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = phase == CalibrationPhase.INSTRUCTIONS || phase == CalibrationPhase.SUCCESS || phase == CalibrationPhase.FAILURE,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Cancel button in top-right (only during instructions or result phases)
                if (phase == CalibrationPhase.INSTRUCTIONS || phase == CalibrationPhase.SUCCESS || phase == CalibrationPhase.FAILURE) {
                    IconButton(
                        onClick = { onDismiss(false) },  // X button = cancel
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (phase) {
                        CalibrationPhase.INSTRUCTIONS -> InstructionsContent(
                            onStart = { phase = CalibrationPhase.SOFT_1 }
                        )
                        
                        CalibrationPhase.CALCULATING -> {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.calibration_calculating),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        CalibrationPhase.SUCCESS -> {
                            val result = calibrationResult as? CalibrationResult.Success
                            if (result != null) {
                                SuccessContent(
                                    softThreshold = result.softThreshold,
                                    mediumThreshold = result.mediumThreshold,
                                    hardThreshold = result.hardThreshold,
                                    onAccept = { onDismiss(true) }  // Accept button = accept
                                )
                            }
                        }
                        
                        CalibrationPhase.FAILURE -> {
                            FailureContent(onClose = { onDismiss(false) })  // Close = cancel, keep old values
                        }
                        
                        else -> SwingingContent(
                            phase = phase,
                            showCheckmark = showCheckmark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionsContent(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.calibration_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = stringResource(R.string.calibration_instructions),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text(stringResource(R.string.start))
        }
    }
}

@Composable
private fun SwingingContent(
    phase: CalibrationPhase,
    showCheckmark: Boolean
) {
    val intensity = when {
        phase.isSoftPhase() -> stringResource(R.string.calibration_swing_soft)
        phase.isMediumPhase() -> stringResource(R.string.calibration_swing_medium)
        else -> stringResource(R.string.calibration_swing_hard)
    }
    
    val swingNumber = when (phase) {
        CalibrationPhase.SOFT_1, CalibrationPhase.MEDIUM_1, CalibrationPhase.HARD_1 -> 1
        CalibrationPhase.SOFT_2, CalibrationPhase.MEDIUM_2, CalibrationPhase.HARD_2 -> 2
        else -> 3
    }
    
    val intensityColor = when {
        phase.isSoftPhase() -> Color(0xFF4CAF50)  // Green
        phase.isMediumPhase() -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Intensity label (large)
        Text(
            text = intensity,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = intensityColor
        )
        
        // Progress indicator
        Text(
            text = stringResource(R.string.calibration_swing_progress, swingNumber),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Checkmark feedback - fixed size Box to prevent layout shifts
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // Use alpha animation for checkmark visibility
            val alpha by animateFloatAsState(
                targetValue = if (showCheckmark) 1f else 0f,
                animationSpec = tween(durationMillis = 150),
                label = "checkmark_alpha"
            )
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }
    }
}

@Composable
private fun SuccessContent(
    softThreshold: Float,
    mediumThreshold: Float,
    hardThreshold: Float,
    onAccept: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(72.dp)
        )
        
        Text(
            text = stringResource(R.string.calibration_success),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        
        // New thresholds display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThresholdRow(label = stringResource(R.string.calibration_swing_soft), value = softThreshold, color = Color(0xFF4CAF50))
            ThresholdRow(label = stringResource(R.string.calibration_swing_medium), value = mediumThreshold, color = Color(0xFFFF9800))
            ThresholdRow(label = stringResource(R.string.calibration_swing_hard), value = hardThreshold, color = Color(0xFFF44336))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text(stringResource(R.string.accept))
        }
    }
}

@Composable
private fun ThresholdRow(label: String, value: Float, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = String.format("%.1f", value),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun FailureContent(onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = Color(0xFFF44336),
            modifier = Modifier.size(72.dp)
        )
        
        Text(
            text = stringResource(R.string.calibration_failed),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF44336)
        )
        
        Text(
            text = stringResource(R.string.calibration_failed_overlap),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text(stringResource(R.string.close))
        }
    }
}

/**
 * Calculates thresholds from collected swings.
 * 
 * Strategy: Try to find valid thresholds by testing combinations.
 * Start with the minimum values from each group (most lenient thresholds).
 * If gaps are too small, try using higher values from the group.
 * 
 * For example, if min(medium) is too close to min(soft), try the 2nd or 3rd
 * lowest medium swing instead.
 * 
 * Validates that thresholds maintain MIN_THRESHOLD_GAP between each level.
 */
private fun calculateThresholds(
    softSwings: List<Float>,
    mediumSwings: List<Float>,
    hardSwings: List<Float>
): CalibrationResult {
    if (softSwings.isEmpty()) return CalibrationResult.Failure("No soft swings recorded")
    if (mediumSwings.isEmpty()) return CalibrationResult.Failure("No medium swings recorded")
    if (hardSwings.isEmpty()) return CalibrationResult.Failure("No hard swings recorded")
    
    val gap = GameEngine.MIN_THRESHOLD_GAP
    
    // Sort each group to try alternatives (ascending order - lowest first)
    val sortedSoft = softSwings.sorted()
    val sortedMedium = mediumSwings.sorted()
    val sortedHard = hardSwings.sorted()
    
    // Try all combinations to find valid thresholds
    // Start with the lowest values (most lenient) and work up if needed
    for (softIdx in sortedSoft.indices) {
        val softThreshold = sortedSoft[softIdx].coerceAtLeast(10f)
        
        for (mediumIdx in sortedMedium.indices) {
            val mediumThreshold = sortedMedium[mediumIdx]
            
            // Check soft-to-medium gap
            if (mediumThreshold - softThreshold < gap) {
                continue // Try next medium value
            }
            
            for (hardIdx in sortedHard.indices) {
                val hardThreshold = sortedHard[hardIdx]
                
                // Check medium-to-hard gap
                if (hardThreshold - mediumThreshold >= gap) {
                    // Valid combination found! Clamp values to slider range (10-60)
                    val clampedSoft = softThreshold.coerceIn(10f, 60f)
                    val clampedHard = hardThreshold.coerceIn(10f, 60f)
                    val clampedMedium = mediumThreshold.coerceIn(clampedSoft + gap, clampedHard - gap)
                    
                    // Verify clamped values still maintain gaps
                    if (clampedMedium - clampedSoft >= gap && clampedHard - clampedMedium >= gap) {
                        return CalibrationResult.Success(
                            softThreshold = clampedSoft,
                            mediumThreshold = clampedMedium,
                            hardThreshold = clampedHard
                        )
                    }
                }
            }
        }
    }
    
    // No valid combination found
    return CalibrationResult.Failure("overlap")
}

// Extension functions for CalibrationPhase
private fun CalibrationPhase.isSwingingPhase(): Boolean = this in listOf(
    CalibrationPhase.SOFT_1, CalibrationPhase.SOFT_2, CalibrationPhase.SOFT_3,
    CalibrationPhase.MEDIUM_1, CalibrationPhase.MEDIUM_2, CalibrationPhase.MEDIUM_3,
    CalibrationPhase.HARD_1, CalibrationPhase.HARD_2, CalibrationPhase.HARD_3
)

private fun CalibrationPhase.isSoftPhase(): Boolean = this in listOf(
    CalibrationPhase.SOFT_1, CalibrationPhase.SOFT_2, CalibrationPhase.SOFT_3
)

private fun CalibrationPhase.isMediumPhase(): Boolean = this in listOf(
    CalibrationPhase.MEDIUM_1, CalibrationPhase.MEDIUM_2, CalibrationPhase.MEDIUM_3
)

private fun CalibrationPhase.isHardPhase(): Boolean = this in listOf(
    CalibrationPhase.HARD_1, CalibrationPhase.HARD_2, CalibrationPhase.HARD_3
)

private fun CalibrationPhase.next(): CalibrationPhase = when (this) {
    CalibrationPhase.INSTRUCTIONS -> CalibrationPhase.SOFT_1
    CalibrationPhase.SOFT_1 -> CalibrationPhase.SOFT_2
    CalibrationPhase.SOFT_2 -> CalibrationPhase.SOFT_3
    CalibrationPhase.SOFT_3 -> CalibrationPhase.MEDIUM_1
    CalibrationPhase.MEDIUM_1 -> CalibrationPhase.MEDIUM_2
    CalibrationPhase.MEDIUM_2 -> CalibrationPhase.MEDIUM_3
    CalibrationPhase.MEDIUM_3 -> CalibrationPhase.HARD_1
    CalibrationPhase.HARD_1 -> CalibrationPhase.HARD_2
    CalibrationPhase.HARD_2 -> CalibrationPhase.HARD_3
    CalibrationPhase.HARD_3 -> CalibrationPhase.CALCULATING
    else -> this // SUCCESS, FAILURE, CALCULATING stay as-is
}
