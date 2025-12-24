package com.air.pong.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.air.pong.R
import com.air.pong.core.sensors.SensorProvider.SwingEvent
import com.air.pong.core.game.SwingType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Tutorial phases for the guided learning flow.
 * Grid positions: 0=SoftLob, 1=MedLob, 2=HardLob
 *                 3=SoftFlat, 4=MedFlat, 5=HardFlat
 *                 6=SoftSmash, 7=MedSmash, 8=HardSmash
 */
enum class TutorialPhase {
    HOLD_PHONE,           // How to hold the phone - any swing to continue
    
    // Horizontal line (middle row: cells 3, 4, 5)
    SWING_SOFT_FLAT,      // Cell 3 (left middle)
    SWING_MED_FLAT,       // Cell 4 (center)
    SWING_HARD_FLAT,      // Cell 5 (right middle)
    LINE_CLEARED_H,       // Horizontal line cleared - clear center cell 4, blink cells 3,4,5
    
    // Vertical line (middle column: cells 1, 4, 7)
    SWING_MED_LOB,        // Cell 1 (top center)
    SWING_MED_SMASH,      // Cell 7 (bottom center)  
    SWING_MED_FLAT_2,     // Cell 4 (center)
    LINE_CLEARED_V,       // Vertical line cleared - blink all 5 cells
    
    // Combined Bonus + Complete screen
    BONUS_COMPLETE        // Bonuses overview + Play Now / Retry / Main Menu
}

/**
 * Full-screen tutorial dialog that guides the user through gameplay basics.
 */
@Composable
fun TutorialDialog(
    swingEvents: Flow<SwingEvent>,
    onPlayBounceSound: () -> Unit,
    onPlayVibration: () -> Unit = {},  // Vibration feedback for tutorial hits
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onMarkComplete: () -> Unit = {} // Called when reaching final screen
) {
    var phase by remember { mutableStateOf(TutorialPhase.HOLD_PHONE) }
    var showCheckmark by remember { mutableStateOf(false) }
    var wrongSwingMessage by remember { mutableStateOf<String?>(null) }
    
    // Track grid state for visualization
    // Grid: 0=SoftLob, 1=MedLob, 2=HardLob, 3=SoftFlat, 4=MedFlat, 5=HardFlat, 6=SoftSmash, 7=MedSmash, 8=HardSmash
    var gridCells by remember { mutableStateOf(List(9) { false }) }
    var blinkingCells by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    // Mark tutorial complete when reaching final screen
    LaunchedEffect(phase) {
        if (phase == TutorialPhase.BONUS_COMPLETE) {
            onMarkComplete()
        }
    }
    
    // Listen for swing events during swinging phases
    LaunchedEffect(phase) {
        if (phase.isSwingingPhase()) {
            swingEvents.collect { event ->
                // Use default thresholds for swing classification in tutorial
                val swingType = com.air.pong.core.game.classifySwing(
                    event.force,
                    event.gravZ,
                    com.air.pong.core.game.GameEngine.DEFAULT_SOFT_THRESHOLD,
                    com.air.pong.core.game.GameEngine.DEFAULT_MEDIUM_THRESHOLD,
                    com.air.pong.core.game.GameEngine.DEFAULT_HARD_THRESHOLD
                )
                val requiredType = phase.getRequiredSwingType()
                
                if (phase == TutorialPhase.HOLD_PHONE) {
                    // Any swing works for hold phone
                    onPlayBounceSound()
                    onPlayVibration()
                    showCheckmark = true
                    delay(300)
                    showCheckmark = false
                    delay(200)
                    phase = phase.next()
                } else if (requiredType != null && swingType == requiredType) {
                    // Correct swing!
                    onPlayBounceSound()
                    onPlayVibration()
                    wrongSwingMessage = null
                    
                    val cellIndex = phase.getCellIndex()
                    if (cellIndex >= 0) {
                        gridCells = gridCells.toMutableList().also { it[cellIndex] = true }
                    }
                    
                    showCheckmark = true
                    delay(400)
                    showCheckmark = false
                    delay(300)
                    
                    phase = phase.next()
                } else if (requiredType != null) {
                    // Wrong swing - show helpful feedback
                    wrongSwingMessage = getWrongSwingMessage(requiredType, swingType)
                    delay(1500)
                    wrongSwingMessage = null
                }
            }
        }
    }
    
    // Auto-advance for line cleared phases with blinking animation
    LaunchedEffect(phase) {
        when (phase) {
            TutorialPhase.LINE_CLEARED_H -> {
                // Blink horizontal line (cells 3, 4, 5)
                blinkingCells = setOf(3, 4, 5)
                delay(4000)
                blinkingCells = emptySet()
                // After horizontal clear: keep cells 3 and 5, clear cell 4 for vertical line
                gridCells = gridCells.toMutableList().also { 
                    it[4] = false  // Clear center for vertical line clear
                }
                phase = phase.next()
            }
            TutorialPhase.LINE_CLEARED_V -> {
                // Blink all 5 cells that form the cross pattern (3, 5 from horizontal + 1, 4, 7 from vertical)
                blinkingCells = setOf(1, 3, 4, 5, 7)
                delay(4000) // Show longer for double line
                blinkingCells = emptySet()
                phase = phase.next()
            }
            else -> { /* Wait for user action */ }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true, // Always allow back to exit
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Close button - always visible
                IconButton(
                    onClick = onDismiss,
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
                
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (phase) {
                        TutorialPhase.HOLD_PHONE -> HoldPhoneContent()
                        
                        TutorialPhase.SWING_SOFT_FLAT,
                        TutorialPhase.SWING_MED_FLAT,
                        TutorialPhase.SWING_HARD_FLAT,
                        TutorialPhase.SWING_MED_LOB,
                        TutorialPhase.SWING_MED_SMASH,
                        TutorialPhase.SWING_MED_FLAT_2 -> SwingingContent(
                            phase = phase,
                            showCheckmark = showCheckmark,
                            gridCells = gridCells,
                            wrongSwingMessage = wrongSwingMessage
                        )
                        
                        TutorialPhase.LINE_CLEARED_H -> LineClearedContent(
                            isSingle = true,
                            gridCells = gridCells,
                            blinkingCells = blinkingCells
                        )
                        TutorialPhase.LINE_CLEARED_V -> LineClearedContent(
                            isSingle = false,
                            gridCells = gridCells,
                            blinkingCells = blinkingCells
                        )
                        
                        TutorialPhase.BONUS_COMPLETE -> BonusCompleteContent(
                            onPlayNow = onComplete,
                            onRetry = {
                                gridCells = List(9) { false }
                                blinkingCells = emptySet()
                                phase = TutorialPhase.HOLD_PHONE
                                onRetry()
                            },
                            onMainMenu = onDismiss
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generate helpful feedback message based on what was wrong with the swing.
 */
private fun getWrongSwingMessage(required: SwingType, actual: SwingType): String {
    val reqIntensity = when {
        required.name.startsWith("SOFT") -> "soft"
        required.name.startsWith("HARD") -> "hard"
        else -> "medium"
    }
    val reqType = when {
        required.name.endsWith("LOB") -> "lob"
        required.name.endsWith("SMASH") -> "smash"
        else -> "flat"
    }
    
    val actIntensity = when {
        actual.name.startsWith("SOFT") -> "soft"
        actual.name.startsWith("HARD") -> "hard"
        else -> "medium"
    }
    val actType = when {
        actual.name.endsWith("LOB") -> "lob"
        actual.name.endsWith("SMASH") -> "smash"
        else -> "flat"
    }
    
    val intensityFix = when {
        reqIntensity == "soft" && actIntensity != "soft" -> "softer"
        reqIntensity == "hard" && actIntensity != "hard" -> "harder"
        reqIntensity == "medium" && actIntensity == "soft" -> "harder"
        reqIntensity == "medium" && actIntensity == "hard" -> "softer"
        else -> null
    }
    
    val typeFix = when {
        reqType == "flat" && actType != "flat" -> "flatter"
        reqType == "lob" && actType != "lob" -> "tilted up"
        reqType == "smash" && actType != "smash" -> "tilted down"
        else -> null
    }
    
    return when {
        intensityFix != null && typeFix != null -> "Try again, $intensityFix and $typeFix"
        intensityFix != null -> "Try again, $intensityFix"
        typeFix != null -> "Try again, $typeFix"
        else -> "Try again!"
    }
}

@Composable
private fun HoldPhoneContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tutorial_hold_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = stringResource(R.string.tutorial_hold_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "🏓",
            fontSize = 64.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.tutorial_hold_swing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwingingContent(
    phase: TutorialPhase,
    showCheckmark: Boolean,
    gridCells: List<Boolean>,
    wrongSwingMessage: String?
) {
    val (instruction, color) = when (phase) {
        TutorialPhase.SWING_SOFT_FLAT -> stringResource(R.string.tutorial_swing_soft) to Color(0xFF4CAF50)
        TutorialPhase.SWING_MED_FLAT -> stringResource(R.string.tutorial_swing_medium) to Color(0xFFFF9800)
        TutorialPhase.SWING_HARD_FLAT -> stringResource(R.string.tutorial_swing_hard) to Color(0xFFF44336)
        TutorialPhase.SWING_MED_LOB -> stringResource(R.string.tutorial_swing_lob) to Color(0xFF2196F3)
        TutorialPhase.SWING_MED_SMASH -> stringResource(R.string.tutorial_swing_smash) to Color(0xFF9C27B0)
        TutorialPhase.SWING_MED_FLAT_2 -> stringResource(R.string.tutorial_swing_medium) to Color(0xFFFF9800)
        else -> "" to Color.White
    }
    
    val swingNumber = when (phase) {
        TutorialPhase.SWING_SOFT_FLAT -> 1
        TutorialPhase.SWING_MED_FLAT -> 2
        TutorialPhase.SWING_HARD_FLAT -> 3
        TutorialPhase.SWING_MED_LOB -> 4
        TutorialPhase.SWING_MED_SMASH -> 5
        TutorialPhase.SWING_MED_FLAT_2 -> 6
        else -> 0
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mini grid visualization with labels
        TutorialGrid(
            cells = gridCells,
            highlightIndex = phase.getCellIndex(),
            blinkingCells = emptySet(),
            showLabels = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Instruction text - fixed height to prevent grid from shifting when text changes
        Box(
            modifier = Modifier.height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = instruction,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
        
        // Progress
        Text(
            text = stringResource(R.string.tutorial_swing_progress, swingNumber, 6),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Feedback area
        Box(
            modifier = Modifier.height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (wrongSwingMessage != null) {
                Text(
                    text = wrongSwingMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
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
                        .size(48.dp)
                        .graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}

/**
 * 3x3 grid visualization for tutorial with labels.
 * Layout: Row 0 = Lobs (top), Row 1 = Flats (middle), Row 2 = Smashes (bottom)
 *         Col 0 = Soft (left), Col 1 = Medium (center), Col 2 = Hard (right)
 */
@Composable
private fun TutorialGrid(
    cells: List<Boolean>,
    highlightIndex: Int = -1,
    blinkingCells: Set<Int>,
    showLabels: Boolean = false
) {
    val cellSize = 44.dp
    val gap = 3.dp
    
    // Blinking animation for cleared lines
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )
    
    val rowLabels = listOf(
        stringResource(R.string.tutorial_grid_lob),
        stringResource(R.string.tutorial_grid_flat),
        stringResource(R.string.tutorial_grid_smash)
    )
    val colLabels = listOf(
        stringResource(R.string.tutorial_grid_soft),
        stringResource(R.string.tutorial_grid_med),
        stringResource(R.string.tutorial_grid_hard)
    )
    
    // Offset to center the grid cells visually (compensate for row labels)
    val labelOffset = if (showLabels) (-18).dp else 0.dp
    
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.offset(x = labelOffset)
    ) {
        // Column labels (only if showLabels)
        if (showLabels) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.padding(start = 40.dp)
            ) {
                colLabels.forEach { label ->
                    Box(
                        modifier = Modifier.size(cellSize),
                        contentAlignment = Alignment.BottomCenter  // Bottom justify
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,  // Bigger
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Row label
                if (showLabels) {
                    Box(
                        modifier = Modifier.width(40.dp),  // Slightly wider for bigger text
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = rowLabels[row],
                            style = MaterialTheme.typography.labelMedium,  // Bigger
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    val isMarked = cells.getOrElse(index) { false }
                    val isHighlight = index == highlightIndex
                    val isBlinking = index in blinkingCells
                    
                    val bgColor = when {
                        isBlinking -> Color(0xFF4CAF50).copy(alpha = blinkAlpha)
                        isMarked -> Color(0xFF4CAF50)
                        isHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .then(
                                if (isHighlight && !isMarked) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMarked && !isBlinking) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineClearedContent(
    isSingle: Boolean, 
    gridCells: List<Boolean>,
    blinkingCells: Set<Int>
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)  // Match SwingingContent
    ) {
        // Show the grid with blinking line
        TutorialGrid(
            cells = gridCells,
            blinkingCells = blinkingCells,
            showLabels = true
        )
        
        // Match SwingingContent's 12dp spacer
        Spacer(modifier = Modifier.height(12.dp))
        
        // Fixed height container to match SwingingContent's instruction box (90dp)
        // Contains icon + heading
        Box(
            modifier = Modifier.height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSingle) stringResource(R.string.tutorial_line_cleared) 
                           else stringResource(R.string.tutorial_multiline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Fixed height for description - increased to prevent text cutoff
        Box(
            modifier = Modifier.height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSingle) stringResource(R.string.tutorial_line_bonus)
                       else stringResource(R.string.tutorial_multiline_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        
        // Spacer to match feedback area - reduced to compensate for larger description box
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BonusCompleteContent(
    onPlayNow: () -> Unit,
    onRetry: () -> Unit,
    onMainMenu: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        // "You're Ready!" at top
        Text(
            text = stringResource(R.string.tutorial_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Grid Upgrades
        BonusRow(
            title = stringResource(R.string.tutorial_grid_upgrades_title),
            desc = stringResource(R.string.tutorial_grid_upgrades_desc)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bonus hint
        BonusRow(
            title = stringResource(R.string.tutorial_bonuses_title),
            desc = stringResource(R.string.tutorial_bonuses_desc)
        )
                
        Spacer(modifier = Modifier.height(12.dp))
        
        // Buttons
        Button(
            onClick = onPlayNow,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text(stringResource(R.string.tutorial_complete_play))
        }
        
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.tutorial_retry))
        }
        
        // TextButton(onClick = onMainMenu) {
        //     Text(stringResource(R.string.tutorial_main_menu))
        // }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Calibration tip at bottom
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.tutorial_calibration_desc),
                style = MaterialTheme.typography.bodyMedium,  // Bigger text
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun BonusRow(title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Extension functions
private fun TutorialPhase.isSwingingPhase(): Boolean = this in listOf(
    TutorialPhase.HOLD_PHONE,
    TutorialPhase.SWING_SOFT_FLAT,
    TutorialPhase.SWING_MED_FLAT,
    TutorialPhase.SWING_HARD_FLAT,
    TutorialPhase.SWING_MED_LOB,
    TutorialPhase.SWING_MED_SMASH,
    TutorialPhase.SWING_MED_FLAT_2
)

private fun TutorialPhase.getRequiredSwingType(): SwingType? = when (this) {
    TutorialPhase.HOLD_PHONE -> null // Any swing
    TutorialPhase.SWING_SOFT_FLAT -> SwingType.SOFT_FLAT
    TutorialPhase.SWING_MED_FLAT -> SwingType.MEDIUM_FLAT
    TutorialPhase.SWING_HARD_FLAT -> SwingType.HARD_FLAT
    TutorialPhase.SWING_MED_LOB -> SwingType.MEDIUM_LOB
    TutorialPhase.SWING_MED_SMASH -> SwingType.MEDIUM_SMASH
    TutorialPhase.SWING_MED_FLAT_2 -> SwingType.MEDIUM_FLAT
    else -> null
}

private fun TutorialPhase.getCellIndex(): Int = when (this) {
    // Grid layout: 0=SoftLob, 1=MedLob, 2=HardLob, 3=SoftFlat, 4=MedFlat, 5=HardFlat, 6=SoftSmash, 7=MedSmash, 8=HardSmash
    TutorialPhase.SWING_SOFT_FLAT -> 3  // Left middle
    TutorialPhase.SWING_MED_FLAT -> 4   // Center
    TutorialPhase.SWING_HARD_FLAT -> 5  // Right middle
    TutorialPhase.SWING_MED_LOB -> 1    // Top center
    TutorialPhase.SWING_MED_SMASH -> 7  // Bottom center
    TutorialPhase.SWING_MED_FLAT_2 -> 4 // Center
    else -> -1
}

private fun TutorialPhase.next(): TutorialPhase = when (this) {
    TutorialPhase.HOLD_PHONE -> TutorialPhase.SWING_SOFT_FLAT
    TutorialPhase.SWING_SOFT_FLAT -> TutorialPhase.SWING_MED_FLAT
    TutorialPhase.SWING_MED_FLAT -> TutorialPhase.SWING_HARD_FLAT
    TutorialPhase.SWING_HARD_FLAT -> TutorialPhase.LINE_CLEARED_H
    TutorialPhase.LINE_CLEARED_H -> TutorialPhase.SWING_MED_LOB
    TutorialPhase.SWING_MED_LOB -> TutorialPhase.SWING_MED_SMASH
    TutorialPhase.SWING_MED_SMASH -> TutorialPhase.SWING_MED_FLAT_2
    TutorialPhase.SWING_MED_FLAT_2 -> TutorialPhase.LINE_CLEARED_V
    TutorialPhase.LINE_CLEARED_V -> TutorialPhase.BONUS_COMPLETE
    TutorialPhase.BONUS_COMPLETE -> TutorialPhase.BONUS_COMPLETE
}
