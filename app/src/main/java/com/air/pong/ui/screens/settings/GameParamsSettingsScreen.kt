package com.air.pong.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.core.game.getWindowShrinkPercentage
import com.air.pong.core.game.getFlightTimeModifier
import com.air.pong.core.game.GameEngine
import com.air.pong.ui.components.SettingsToggle
import com.air.pong.ui.components.ExpandableSection
import com.air.pong.ui.components.SectionHeader




@Composable
fun FlightTimeVisualization(baseFlightTime: Float, refreshTrigger: Int, swingSettings: com.air.pong.core.game.SwingSettings) {
    // Explicitly remember the calculation based on the trigger to ensure it re-runs
    val calculationResult = remember(baseFlightTime, refreshTrigger, swingSettings) {
        val allTypes = com.air.pong.core.game.SwingType.values()
        
        // Find types with min and max flight modifiers
        // Note: Shortest time = Lowest Modifier
        //       Longest time = Highest Modifier
        
        val minFlightType = allTypes.minByOrNull { it.getFlightTimeModifier(swingSettings) } ?: com.air.pong.core.game.SwingType.HARD_SMASH
        val maxFlightType = allTypes.maxByOrNull { it.getFlightTimeModifier(swingSettings) } ?: com.air.pong.core.game.SwingType.HARD_LOB
        
        val minModifier = minFlightType.getFlightTimeModifier(swingSettings)
        val maxModifier = maxFlightType.getFlightTimeModifier(swingSettings)
        
        val minTime = (baseFlightTime * minModifier).toLong()
        val maxTime = (baseFlightTime * maxModifier).toLong()
        
        // Format names for display
        val shortestName = minFlightType.name.lowercase().replace("_", " ")
        val longestName = maxFlightType.name.lowercase().replace("_", " ")
        
        Quadruple(shortestName, minTime, longestName, maxTime)
    }

    val (shortestName, minTime, longestName, maxTime) = calculationResult

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Stacked layout for better localization support
        Column(modifier = Modifier.fillMaxWidth()) {
            AlignedParamRow(
                label = "shortest:",
                shotType = shortestName,
                timeValue = "${minTime}ms"
            )
            AlignedParamRow(
                label = "longest:",
                shotType = longestName,
                timeValue = "${maxTime}ms"
            )
        }
    }
}

@Composable
fun HitWindowVisualization(difficultyWindow: Int, refreshTrigger: Int, swingSettings: com.air.pong.core.game.SwingSettings) {
    // Difficulty is the base window size.
    // Start of window is always -200ms (BOUNCE_OFFSET_MS)
    // End of window = Start + (2 * HalfWindow)
    // HalfWindow = (Difficulty * ShrinkFactor)
    
    // We need to read the current SwingSettings to get the shrink factors.
    // The swingSettings is passed in from the parent.
    
    // Explicitly remember the calculation based on the trigger to ensure it re-runs
    val calculationResult = remember(difficultyWindow, refreshTrigger, swingSettings) {
        // Dynamic Logic: Find the SwingType with Max Shrink (Shortest Window) and Min Shrink (Longest Window)
        val allTypes = com.air.pong.core.game.SwingType.values()
        
        val maxShrinkType = allTypes.maxByOrNull { it.getWindowShrinkPercentage(swingSettings) } ?: com.air.pong.core.game.SwingType.HARD_SMASH
        val minShrinkType = allTypes.minByOrNull { it.getWindowShrinkPercentage(swingSettings) } ?: com.air.pong.core.game.SwingType.SOFT_LOB
        
        val maxShrink = maxShrinkType.getWindowShrinkPercentage(swingSettings)
        val minShrink = minShrinkType.getWindowShrinkPercentage(swingSettings)
        
        val startWindow = -GameEngine.BOUNCE_OFFSET_MS.toInt() // Fixed bounce offset
        
        // Shortest Window (Max Shrink)
        val shortestHalf = (difficultyWindow / 2.0f) * (1.0f - maxShrink)
        val shortestEnd = startWindow + (2 * shortestHalf).toInt()
        val shortestTotal = shortestEnd - startWindow
        
        // Longest Window (Min Shrink)
        val longestHalf = (difficultyWindow / 2.0f) * (1.0f - minShrink)
        val longestEnd = startWindow + (2 * longestHalf).toInt()
        val longestTotal = longestEnd - startWindow
        
        // Format names for display (e.g. HARD_SMASH -> hard smash)
        val shortestName = maxShrinkType.name.lowercase().replace("_", " ")
        val longestName = minShrinkType.name.lowercase().replace("_", " ")
        
        Quadruple(shortestName, shortestTotal, longestName, longestTotal)
    }

    val (shortestName, shortestTotal, longestName, longestTotal) = calculationResult

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Stacked layout for better localization support
        Column(modifier = Modifier.fillMaxWidth()) {
            AlignedParamRow(
                label = "shortest:",
                shotType = shortestName,
                timeValue = "${shortestTotal}ms"
            )
            AlignedParamRow(
                label = "longest:",
                shotType = longestName,
                timeValue = "${longestTotal}ms"
            )
        }
    }
}

@Composable
fun AlignedParamRow(
    label: String,
    shotType: String,
    timeValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Label (Fixed width)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(60.dp)
        )
        
        // Column 2: Shot Type (Fixed width for alignment, but not taking full space)
        Text(
            text = shotType,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(110.dp)
        )
        
        // Column 3: Time Value (Fixed width)
        Text(
            text = timeValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(50.dp)
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun GameParamsSettingsScreen(
    flightTime: Float,
    onFlightTimeChange: (Float) -> Unit,
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    minSwingThreshold: Float,
    onSwingThresholdChange: (Float) -> Unit,
    onSettingsChange: () -> Unit,
    onResetMainDefaults: () -> Unit,
    onResetSwingDefaults: () -> Unit,
    onShowInfo: (String, String) -> Unit,
    // New parameters for swing testing
    lastSwingType: com.air.pong.core.game.SwingType?,
    lastSwingData: com.air.pong.core.game.SwingData?,
    swingSettingsVersion: Int,
    swingSettings: com.air.pong.core.game.SwingSettings,
    onSwingSettingUpdate: ((com.air.pong.core.game.SwingSettings) -> com.air.pong.core.game.SwingSettings) -> Unit,
    onPlayTestSound: (com.air.pong.audio.AudioManager.SoundEvent) -> Unit,
    onClearDebugData: () -> Unit,
    isRallyShrinkEnabled: Boolean,
    onRallyShrinkChange: (Boolean) -> Unit,
    isReadOnly: Boolean = false // Default to false for preview/compatibility
) {
    val context = LocalContext.current
    
    // Pass scroll state down
    val scrollState = rememberScrollState()

    // Shared trigger to refresh visualizations when advanced settings change.
    // Also refreshed when swingSettingsVersion changes (from network).
    var advancedSettingsTrigger by remember { mutableIntStateOf(0) }
    
    // Track readiness to prevent sound on initial composition (fixes bounce sound bug)
    var isReady by remember { mutableStateOf(false) }
    
    // Watch for external updates
    LaunchedEffect(swingSettingsVersion) {
        advancedSettingsTrigger = swingSettingsVersion
    }

    // Cleanup on enter/exit - set isReady after clearing to prevent sound trigger on stale data
    DisposableEffect(Unit) {
        onClearDebugData()
        isReady = true
        onDispose {
            onClearDebugData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Defaults Button
        if (isReadOnly) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = "Some settings are currently controlled by the Host", 
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onResetMainDefaults) {
                    Text(stringResource(R.string.defaults))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Swing Sensitivity (Local) - Moved to Top
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.swing_sensitivity_label, minSwingThreshold), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_sensitivity_title), context.getString(R.string.info_sensitivity_text))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = minSwingThreshold,
            onValueChange = {
                // Snap to 1.0
                onSwingThresholdChange(kotlin.math.round(it))
            },
            valueRange = 10.0f..24.0f,
            steps = 13,
            onValueChangeFinished = onSettingsChange
        )

        // Swing Test Feedback
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (lastSwingType != null) {
                Text(
                    text = lastSwingType.name.replace("_", " "),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                // Play sound based on force (only after screen is ready to prevent stale data sound)
                if (isReady) {
                    lastSwingData?.let { data ->
                        LaunchedEffect(data) {
                            val soundEvent = when {
                                data.force > 20.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD
                                data.force > 17.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_MEDIUM
                                else -> com.air.pong.audio.AudioManager.SoundEvent.HIT_SOFT
                            }
                            onPlayTestSound(soundEvent)
                        }
                    }
                }
            } else {
                Text(
                    text = "Swing device to test",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Flight Time
        val flightTimeLabel = when {
            flightTime <= GameEngine.FLIGHT_TIME_HARD_THRESHOLD -> stringResource(R.string.difficulty_hard)
            flightTime <= GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.flight_time_label, flightTime.toInt(), flightTimeLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(
                    context.getString(R.string.info_flight_time_title), 
                    context.getString(
                        R.string.info_flight_time_text, 
                        GameEngine.MIN_FLIGHT_TIME, 
                        GameEngine.FLIGHT_TIME_HARD_THRESHOLD,
                        GameEngine.FLIGHT_TIME_HARD_THRESHOLD + 100,
                        GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD,
                        GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD + 100,
                        GameEngine.MAX_FLIGHT_TIME
                    )
                )
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }

        Slider(
            value = flightTime,
            onValueChange = {
                // Snap to 100ms
                onFlightTimeChange((it / 100).toInt() * 100f)
            },
            valueRange = GameEngine.MIN_FLIGHT_TIME.toFloat()..GameEngine.MAX_FLIGHT_TIME.toFloat(),
            steps = 6,
            onValueChangeFinished = onSettingsChange,
            enabled = !isReadOnly
        )
        
        FlightTimeVisualization(baseFlightTime = flightTime, refreshTrigger = advancedSettingsTrigger, swingSettings = swingSettings)

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty (Hit Window)
        val difficultyLabel = when {
            difficulty <= 500 -> stringResource(R.string.difficulty_hard)
            difficulty <= 900 -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hit_window_label, difficulty, difficultyLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_hit_window_title), context.getString(R.string.info_hit_window_text, GameEngine.MIN_DIFFICULTY, GameEngine.MAX_DIFFICULTY))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = difficulty.toFloat(),
            onValueChange = {
                // Snap to 100ms
                val snapped = (it / 100).toInt() * 100
                onDifficultyChange(snapped)
            },
            valueRange = GameEngine.MIN_DIFFICULTY.toFloat()..GameEngine.MAX_DIFFICULTY.toFloat(),
            steps = ((GameEngine.MAX_DIFFICULTY - GameEngine.MIN_DIFFICULTY) / 100) - 1, // Dynamic steps based on range
            onValueChangeFinished = onSettingsChange,
            enabled = !isReadOnly
        )
        
        HitWindowVisualization(difficultyWindow = difficulty, refreshTrigger = advancedSettingsTrigger, swingSettings = swingSettings)
        

        
        Spacer(modifier = Modifier.height(24.dp))
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SwingParamsGrid(
            scrollState = scrollState,
            settingsVersion = swingSettingsVersion,
            swingSettings = swingSettings,
            onSwingSettingUpdate = onSwingSettingUpdate,
            onSettingsChange = {
                onSettingsChange()
                advancedSettingsTrigger++ // Force refresh of visualizations
            }, 
            onShowInfo = onShowInfo, 
            onReset = onResetSwingDefaults,
            isReadOnly = isReadOnly,
            isRallyShrinkEnabled = isRallyShrinkEnabled,
            onRallyShrinkChange = onRallyShrinkChange
        )
    }
}

@Composable
fun SwingParamsGrid(
    scrollState: androidx.compose.foundation.ScrollState,
    settingsVersion: Int,
    swingSettings: com.air.pong.core.game.SwingSettings,
    onSwingSettingUpdate: ((com.air.pong.core.game.SwingSettings) -> com.air.pong.core.game.SwingSettings) -> Unit,
    onSettingsChange: () -> Unit, 
    onShowInfo: (String, String) -> Unit, 
    onReset: () -> Unit,
    isReadOnly: Boolean,
    isRallyShrinkEnabled: Boolean,
    onRallyShrinkChange: (Boolean) -> Unit
) {
    // Force recomposition when values change (local or remote)
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Watch for external updates from network
    LaunchedEffect(settingsVersion) {
        refreshTrigger = settingsVersion
    }

    // Read the state to subscribe to changes
    val trigger = refreshTrigger
    val settings = swingSettings
    
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-Scroll when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            // Wait a frame for layout to update
            kotlinx.coroutines.delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header / Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            // Title and Reset Row
            SectionHeader(
                title = "Swing Parameters",
                onInfoClick = {
                    onShowInfo(
                        "Swing Parameters",
                        "Fine-tune the risk and reward for each swing type.\n\n" +
                        "Flight: Flight time multiplier.\n" +
                        "Net%: Chance of hitting the net (losing point).\n" +
                        "Out%: Chance of hitting out of bounds (losing point).\n" +
                        "Shrink%: How much this shot shrinks the opponent's timing window (making it harder for them).\n\n" +
                        "Rally Mode: Net% and Out% are eliminated (set to 0%) since partners are cooperating.\n\n" +
                        "Note: Very fast shots (Low Flight Time) may shift the opponent's hit window slightly later to ensure it remains hittable."
                    )
                },
                actionLabel = if (!isReadOnly) "Defaults" else null,
                onActionClick = if (!isReadOnly) {
                    {
                        onReset()
                        refreshTrigger++
                        onSettingsChange() // Call first to sync swing params
                        onRallyShrinkChange(true) // Call last so it's not overwritten
                    }
                } else null,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Table Headers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Type", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                Text("Flight", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Net%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Out%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Shrink%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Soft Lob
            SwingParamRow(
                name = "Soft Lob",
                netVal = settings.softLobNetRisk,
                outVal = settings.softLobOutRisk,
                shrinkVal = settings.softLobShrink,
                flightVal = settings.softLobFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(softLobNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(softLobOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(softLobShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(softLobFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Med Lob
            SwingParamRow(
                name = "Med Lob",
                netVal = settings.mediumLobNetRisk,
                outVal = settings.mediumLobOutRisk,
                shrinkVal = settings.mediumLobShrink,
                flightVal = settings.mediumLobFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(mediumLobNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(mediumLobOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(mediumLobShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(mediumLobFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Hard Lob
            SwingParamRow(
                name = "Hard Lob",
                netVal = settings.hardLobNetRisk,
                outVal = settings.hardLobOutRisk,
                shrinkVal = settings.hardLobShrink,
                flightVal = settings.hardLobFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(hardLobNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(hardLobOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(hardLobShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(hardLobFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Soft Flat
            SwingParamRow(
                name = "Soft Flat",
                netVal = settings.softFlatNetRisk,
                outVal = settings.softFlatOutRisk,
                shrinkVal = settings.softFlatShrink,
                flightVal = settings.softFlatFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(softFlatNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(softFlatOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(softFlatShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(softFlatFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Med Flat
            SwingParamRow(
                name = "Med Flat",
                netVal = settings.mediumFlatNetRisk,
                outVal = settings.mediumFlatOutRisk,
                shrinkVal = settings.mediumFlatShrink,
                flightVal = settings.mediumFlatFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(mediumFlatNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(mediumFlatOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(mediumFlatShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(mediumFlatFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Hard Flat
            SwingParamRow(
                name = "Hard Flat",
                netVal = settings.hardFlatNetRisk,
                outVal = settings.hardFlatOutRisk,
                shrinkVal = settings.hardFlatShrink,
                flightVal = settings.hardFlatFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(hardFlatNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(hardFlatOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(hardFlatShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(hardFlatFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Soft Smash
            SwingParamRow(
                name = "Soft Smash",
                netVal = settings.softSmashNetRisk,
                outVal = settings.softSmashOutRisk,
                shrinkVal = settings.softSmashShrink,
                flightVal = settings.softSmashFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(softSmashNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(softSmashOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(softSmashShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(softSmashFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Med Smash
            SwingParamRow(
                name = "Med Smash",
                netVal = settings.mediumSmashNetRisk,
                outVal = settings.mediumSmashOutRisk,
                shrinkVal = settings.mediumSmashShrink,
                flightVal = settings.mediumSmashFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(mediumSmashNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(mediumSmashOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(mediumSmashShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(mediumSmashFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Hard Smash
            SwingParamRow(
                name = "Hard Smash",
                netVal = settings.hardSmashNetRisk,
                outVal = settings.hardSmashOutRisk,
                shrinkVal = settings.hardSmashShrink,
                flightVal = settings.hardSmashFlight,
                onNetChange = { v -> onSwingSettingUpdate { it.copy(hardSmashNetRisk = v) }; refreshTrigger++; onSettingsChange() },
                onOutChange = { v -> onSwingSettingUpdate { it.copy(hardSmashOutRisk = v) }; refreshTrigger++; onSettingsChange() },
                onShrinkChange = { v -> onSwingSettingUpdate { it.copy(hardSmashShrink = v) }; refreshTrigger++; onSettingsChange() },
                onFlightChange = { v -> onSwingSettingUpdate { it.copy(hardSmashFlight = v) }; refreshTrigger++; onSettingsChange() },
                isReadOnly = isReadOnly
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Shrinking Hit Window Toggle (moved to advanced section)
            SettingsToggle(
                label = "Shrinking Hit Window",
                checked = isRallyShrinkEnabled,
                onCheckedChange = onRallyShrinkChange,
                onInfoClick = {
                    onShowInfo(
                        "Shrinking Hit Window",
                        "Classic Mode: Hit window shrinks by 10ms per shot in a rally.\n\nRally Mode: Hit window shrinks by 50ms each time either player reaches a new tier (e.g., when someone first completes tier 3, then tier 8).\n\nMinimum window: ${GameEngine.MIN_HIT_WINDOW}ms"
                    )
                },
                enabled = !isReadOnly
            )
        }
    }
}

@Composable
fun SwingParamRow(
    name: String,
    netVal: Int,
    outVal: Int,
    shrinkVal: Int,
    flightVal: Float,
    onNetChange: (Int) -> Unit,
    onOutChange: (Int) -> Unit,
    onShrinkChange: (Int) -> Unit,
    onFlightChange: (Float) -> Unit,
    isReadOnly: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
        
        CompactFloatInput(value = flightVal, onValueChange = onFlightChange, modifier = Modifier.weight(1f), isReadOnly = isReadOnly)
        CompactNumberInput(value = netVal, onValueChange = onNetChange, modifier = Modifier.weight(1f), isReadOnly = isReadOnly)
        CompactNumberInput(value = outVal, onValueChange = onOutChange, modifier = Modifier.weight(1f), isReadOnly = isReadOnly)
        CompactNumberInput(value = shrinkVal, onValueChange = onShrinkChange, modifier = Modifier.weight(1f), isReadOnly = isReadOnly)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactNumberInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Fine Tune Value") },
            text = {
                Column {
                    Text("Current: $value%", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onValueChange(kotlin.math.round(it).toInt()) },
                        valueRange = 0f..100f
                        // steps removed to avoid visual dots
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Box(
        modifier = modifier.combinedClickable(
            enabled = !isReadOnly,
            onClick = {
                // Cycle values: 0, 5, 10... 100, 0
                val next = if (value >= 100) 0 else value + 5
                // Snap to nearest 5 if we were off-grid
                val snapped = ((next + 2) / 5) * 5
                onValueChange(if (snapped > 100) 0 else snapped)
            },
            onLongClick = {
                showDialog = true
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactFloatInput(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Flight Time Modifier") },
            text = {
                Column {
                    Text("Current: ${String.format("%.1fx", value)}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = value,
                        onValueChange = { 
                            // Snap to 0.1
                            val snapped = (it * 10).toInt() / 10f
                            onValueChange(snapped) 
                        },
                        valueRange = 0.2f..2.0f,
                        steps = 17 // (2.0 - 0.2) / 0.1 - 1 = 18 - 1 = 17 steps
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Box(
        modifier = modifier.combinedClickable(
            enabled = !isReadOnly,
            onClick = {
                // Cycle values: 0.2 -> 2.0 -> 0.2
                val next = if (value >= 2.0f) 0.2f else value + 0.1f
                // Snap to nearest 0.1
                val snapped = (next * 10).toInt() / 10f
                onValueChange(snapped)
            },
            onLongClick = {
                showDialog = true
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%.1fx", value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
