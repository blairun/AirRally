package com.air.pong.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.air.pong.core.sensors.SensorProvider.SwingEvent
import com.air.pong.ui.components.SettingsToggle
import com.air.pong.ui.components.ExpandableSection
import com.air.pong.ui.components.SectionHeader
import com.air.pong.ui.components.TripleThumbSlider
import com.air.pong.ui.dialogs.CalibrationDialog
import com.air.pong.ui.dialogs.CalibrationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch



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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameParamsSettingsScreen(
    flightTime: Float,
    onFlightTimeChange: (Float) -> Unit,
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    softThreshold: Float,
    mediumThreshold: Float,
    hardThreshold: Float,
    onSoftThresholdChange: (Float) -> Unit,
    onMediumThresholdChange: (Float) -> Unit,
    onHardThresholdChange: (Float) -> Unit,
    onSettingsChange: () -> Unit,
    onResetMainDefaults: () -> Unit,
    onResetSwingDefaults: () -> Unit,
    onShowInfo: (String, String) -> Unit,
    lastSwingType: com.air.pong.core.game.SwingType?,
    lastSwingData: com.air.pong.core.game.SwingData?,
    swingSettingsVersion: Int,
    swingSettings: com.air.pong.core.game.SwingSettings,
    onSwingSettingUpdate: ((com.air.pong.core.game.SwingSettings) -> com.air.pong.core.game.SwingSettings) -> Unit,
    onPlayTestSound: (com.air.pong.audio.AudioManager.SoundEvent) -> Unit,
    onClearDebugData: () -> Unit,
    isRallyShrinkEnabled: Boolean,
    onRallyShrinkChange: (Boolean) -> Unit,
    soloFlightAdder: Long,
    onSoloFlightAdderChange: (Long) -> Unit,
    isReadOnly: Boolean = false,
    swingEvents: Flow<SwingEvent>? = null,
    onPlayBounceSound: (() -> Unit)? = null,
    isTapToHitEnabled: Boolean = false,
    onTapToHitChange: (Boolean) -> Unit = {},
    // Bonus mechanics toggles
    bonusSpinEnabled: Boolean = true,
    bonusCopyCatEnabled: Boolean = true,
    bonusSpecialSquaresEnabled: Boolean = true,
    bonusPowerOutageEnabled: Boolean = true,
    onBonusSpinChange: (Boolean) -> Unit = {},
    onBonusCopyCatChange: (Boolean) -> Unit = {},
    onBonusSpecialSquaresChange: (Boolean) -> Unit = {},
    onBonusPowerOutageChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Tab setup
    val tabs = listOf(
        R.string.params_tab_main,
        R.string.params_tab_accessibility,
        R.string.params_tab_advanced
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Shared trigger to refresh visualizations when advanced settings change.
    var advancedSettingsTrigger by remember { mutableIntStateOf(0) }
    
    // Track readiness to prevent sound on initial composition
    var isReady by remember { mutableStateOf(false) }
    
    // Calibration dialog state
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var calibrationResult by remember { mutableStateOf<CalibrationResult?>(null) }
    
    LaunchedEffect(swingSettingsVersion) {
        advancedSettingsTrigger = swingSettingsVersion
    }

    LaunchedEffect(Unit) {
        onClearDebugData()
        kotlinx.coroutines.delay(300)
        isReady = true
    }
    
    DisposableEffect(Unit) {
        onDispose { onClearDebugData() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, titleRes ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(titleRes)) }
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                when (page) {
                    0 -> MainTabContent(
                        context = context,
                        scrollState = scrollState,
                        isReadOnly = isReadOnly,
                        flightTime = flightTime,
                        onFlightTimeChange = onFlightTimeChange,
                        difficulty = difficulty,
                        onDifficultyChange = onDifficultyChange,
                        softThreshold = softThreshold,
                        mediumThreshold = mediumThreshold,
                        hardThreshold = hardThreshold,
                        onSoftThresholdChange = onSoftThresholdChange,
                        onMediumThresholdChange = onMediumThresholdChange,
                        onHardThresholdChange = onHardThresholdChange,
                        onSettingsChange = onSettingsChange,
                        onResetMainDefaults = onResetMainDefaults,
                        onShowInfo = onShowInfo,
                        lastSwingType = lastSwingType,
                        lastSwingData = lastSwingData,
                        swingSettings = swingSettings,
                        advancedSettingsTrigger = advancedSettingsTrigger,
                        isReady = isReady,
                        showCalibrationDialog = showCalibrationDialog,
                        onShowCalibrationDialog = { showCalibrationDialog = it },
                        calibrationResult = calibrationResult,
                        onCalibrationResult = { calibrationResult = it },
                        swingEvents = swingEvents,
                        onPlayBounceSound = onPlayBounceSound,
                        onPlayTestSound = onPlayTestSound,
                        onClearDebugData = onClearDebugData,
                        onIsReadyChange = { isReady = it },
                        bonusSpinEnabled = bonusSpinEnabled,
                        bonusCopyCatEnabled = bonusCopyCatEnabled,
                        bonusSpecialSquaresEnabled = bonusSpecialSquaresEnabled,
                        bonusPowerOutageEnabled = bonusPowerOutageEnabled,
                        onBonusSpinChange = onBonusSpinChange,
                        onBonusCopyCatChange = onBonusCopyCatChange,
                        onBonusSpecialSquaresChange = onBonusSpecialSquaresChange,
                        onBonusPowerOutageChange = onBonusPowerOutageChange
                    )
                    1 -> AccessibilityTabContent(
                        context = context,
                        isTapToHitEnabled = isTapToHitEnabled,
                        onTapToHitChange = onTapToHitChange,
                        onShowInfo = onShowInfo
                    )
                    2 -> AdvancedTabContent(
                        context = context,
                        scrollState = scrollState,
                        settingsVersion = swingSettingsVersion,
                        swingSettings = swingSettings,
                        onSwingSettingUpdate = onSwingSettingUpdate,
                        onSettingsChange = {
                            onSettingsChange()
                            advancedSettingsTrigger++
                        },
                        onShowInfo = onShowInfo,
                        onReset = onResetSwingDefaults,
                        isReadOnly = isReadOnly,
                        isRallyShrinkEnabled = isRallyShrinkEnabled,
                        onRallyShrinkChange = onRallyShrinkChange,
                        soloFlightAdder = soloFlightAdder,
                        onSoloFlightAdderChange = onSoloFlightAdderChange
                    )
                }
            }
        }
    }
}

// ============================================================
// TAB CONTENT COMPOSABLES
// ============================================================

@Composable
private fun MainTabContent(
    context: android.content.Context,
    scrollState: androidx.compose.foundation.ScrollState,
    isReadOnly: Boolean,
    flightTime: Float,
    onFlightTimeChange: (Float) -> Unit,
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    softThreshold: Float,
    mediumThreshold: Float,
    hardThreshold: Float,
    onSoftThresholdChange: (Float) -> Unit,
    onMediumThresholdChange: (Float) -> Unit,
    onHardThresholdChange: (Float) -> Unit,
    onSettingsChange: () -> Unit,
    onResetMainDefaults: () -> Unit,
    onShowInfo: (String, String) -> Unit,
    lastSwingType: com.air.pong.core.game.SwingType?,
    lastSwingData: com.air.pong.core.game.SwingData?,
    swingSettings: com.air.pong.core.game.SwingSettings,
    advancedSettingsTrigger: Int,
    isReady: Boolean,
    showCalibrationDialog: Boolean,
    onShowCalibrationDialog: (Boolean) -> Unit,
    calibrationResult: CalibrationResult?,
    onCalibrationResult: (CalibrationResult?) -> Unit,
    swingEvents: Flow<SwingEvent>?,
    onPlayBounceSound: (() -> Unit)?,
    onPlayTestSound: (com.air.pong.audio.AudioManager.SoundEvent) -> Unit,
    onClearDebugData: () -> Unit,
    onIsReadyChange: (Boolean) -> Unit,
    bonusSpinEnabled: Boolean,
    bonusCopyCatEnabled: Boolean,
    bonusSpecialSquaresEnabled: Boolean,
    bonusPowerOutageEnabled: Boolean,
    onBonusSpinChange: (Boolean) -> Unit,
    onBonusCopyCatChange: (Boolean) -> Unit,
    onBonusSpecialSquaresChange: (Boolean) -> Unit,
    onBonusPowerOutageChange: (Boolean) -> Unit
) {
    // Synced settings banner for non-host
    if (isReadOnly) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.settings_synced_from_host), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Swing Thresholds
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.swing_thresholds_label), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onShowInfo(context.getString(R.string.info_thresholds_title), context.getString(R.string.info_thresholds_text)) }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        if (swingEvents != null && onPlayBounceSound != null) {
            TextButton(onClick = { onShowCalibrationDialog(true) }) { Text(stringResource(R.string.calibrate)) }
        }
    }

    // Calibration Dialog
    if (showCalibrationDialog && swingEvents != null && onPlayBounceSound != null) {
        CalibrationDialog(
            swingEvents = swingEvents,
            onPlayBounceSound = onPlayBounceSound,
            onResult = { onCalibrationResult(it) },
            onDismiss = { accepted ->
                if (accepted) {
                    (calibrationResult as? CalibrationResult.Success)?.let {
                        onSoftThresholdChange(it.softThreshold)
                        onMediumThresholdChange(it.mediumThreshold)
                        onHardThresholdChange(it.hardThreshold)
                        onSettingsChange()
                    }
                }
                onIsReadyChange(false)
                onClearDebugData()
                onShowCalibrationDialog(false)
                onCalibrationResult(null)
            }
        )
    }

    LaunchedEffect(showCalibrationDialog) {
        if (!showCalibrationDialog && !isReady) {
            kotlinx.coroutines.delay(300)
            onIsReadyChange(true)
        }
    }

    TripleThumbSlider(
        softValue = softThreshold, mediumValue = mediumThreshold, hardValue = hardThreshold,
        onSoftChange = onSoftThresholdChange, onMediumChange = onMediumThresholdChange, onHardChange = onHardThresholdChange,
        onValueChangeFinished = onSettingsChange, valueRange = 10f..60f, minGap = GameEngine.MIN_THRESHOLD_GAP, enabled = true
    )

    // Swing Test Feedback
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
        if (lastSwingType != null) {
            Text(lastSwingType.name.replace("_", " "), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (isReady && !showCalibrationDialog) {
                lastSwingData?.let { data ->
                    LaunchedEffect(data) {
                        val soundEvent = when { data.force > 20.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD; data.force > 17.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_MEDIUM; else -> com.air.pong.audio.AudioManager.SoundEvent.HIT_SOFT }
                        onPlayTestSound(soundEvent)
                    }
                }
            }
        } else {
            Text(stringResource(R.string.swing_device_to_test), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(8.dp))

    // Flight Time
    val flightTimeLabel = when { flightTime <= GameEngine.FLIGHT_TIME_HARD_THRESHOLD -> stringResource(R.string.difficulty_hard); flightTime <= GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD -> stringResource(R.string.difficulty_medium); else -> stringResource(R.string.difficulty_easy) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.flight_time_label, flightTime.toInt(), flightTimeLabel), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onShowInfo(context.getString(R.string.info_flight_time_title), context.getString(R.string.info_flight_time_text, GameEngine.MIN_FLIGHT_TIME, GameEngine.FLIGHT_TIME_HARD_THRESHOLD, GameEngine.FLIGHT_TIME_HARD_THRESHOLD + 100, GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD, GameEngine.FLIGHT_TIME_MEDIUM_THRESHOLD + 100, GameEngine.MAX_FLIGHT_TIME)) }) {
            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
        }
    }
    Slider(value = flightTime, onValueChange = { onFlightTimeChange((it / 100).toInt() * 100f) }, valueRange = GameEngine.MIN_FLIGHT_TIME.toFloat()..GameEngine.MAX_FLIGHT_TIME.toFloat(), steps = 10, onValueChangeFinished = onSettingsChange, enabled = !isReadOnly)
    FlightTimeVisualization(baseFlightTime = flightTime, refreshTrigger = advancedSettingsTrigger, swingSettings = swingSettings)

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(8.dp))

    // Hit Window
    val difficultyLabel = when { difficulty <= 500 -> stringResource(R.string.difficulty_hard); difficulty <= 900 -> stringResource(R.string.difficulty_medium); else -> stringResource(R.string.difficulty_easy) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.hit_window_label, difficulty, difficultyLabel), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onShowInfo(context.getString(R.string.info_hit_window_title), context.getString(R.string.info_hit_window_text, GameEngine.MIN_DIFFICULTY, GameEngine.MAX_DIFFICULTY)) }) {
            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
        }
    }
    Slider(value = difficulty.toFloat(), onValueChange = { onDifficultyChange((it / 100).toInt() * 100) }, valueRange = GameEngine.MIN_DIFFICULTY.toFloat()..GameEngine.MAX_DIFFICULTY.toFloat(), steps = ((GameEngine.MAX_DIFFICULTY - GameEngine.MIN_DIFFICULTY) / 100) - 1, onValueChangeFinished = onSettingsChange, enabled = !isReadOnly)
    HitWindowVisualization(difficultyWindow = difficulty, refreshTrigger = advancedSettingsTrigger, swingSettings = swingSettings)

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Spacer(modifier = Modifier.height(8.dp))

    // Bonus Mechanics Section
    BonusMechanicsSection(
        context = context,
        scrollState = scrollState,
        isReadOnly = isReadOnly,
        bonusSpinEnabled = bonusSpinEnabled,
        bonusCopyCatEnabled = bonusCopyCatEnabled,
        bonusSpecialSquaresEnabled = bonusSpecialSquaresEnabled,
        bonusPowerOutageEnabled = bonusPowerOutageEnabled,
        onBonusSpinChange = onBonusSpinChange,
        onBonusCopyCatChange = onBonusCopyCatChange,
        onBonusSpecialSquaresChange = onBonusSpecialSquaresChange,
        onBonusPowerOutageChange = onBonusPowerOutageChange,
        onShowInfo = onShowInfo
    )
    // Defaults button at bottom-left
    if (!isReadOnly) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = {
                onResetMainDefaults()
                // Reset bonus modes to defaults (disabled)
                onBonusSpinChange(false)
                onBonusCopyCatChange(false)
                onBonusSpecialSquaresChange(false)
                onBonusPowerOutageChange(false)
            }) { Text(stringResource(R.string.defaults)) }
        }
    }
}

@Composable
private fun AccessibilityTabContent(
    context: android.content.Context,
    isTapToHitEnabled: Boolean,
    onTapToHitChange: (Boolean) -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    // Tap-to-Hit Mode
    SettingsToggle(
        label = stringResource(R.string.tap_to_hit_mode),
        checked = isTapToHitEnabled,
        onCheckedChange = onTapToHitChange,
        onInfoClick = { onShowInfo(context.getString(R.string.info_tap_to_hit_title), context.getString(R.string.info_tap_to_hit_text)) },
        enabled = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Color Blind Mode (placeholder)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.color_blind_mode), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(" (${stringResource(R.string.coming_soon)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Switch(checked = false, onCheckedChange = {}, enabled = false)
    }

    // Enhanced Audio Cues (placeholder)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.enhanced_audio_cues), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(" (${stringResource(R.string.coming_soon)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Switch(checked = false, onCheckedChange = {}, enabled = false)
    }

    // Haptic Intensity (placeholder)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.haptic_intensity), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(" (${stringResource(R.string.coming_soon)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Slider(value = 0.5f, onValueChange = {}, valueRange = 0f..1f, enabled = false)
    }
}

@Composable
private fun AdvancedTabContent(
    context: android.content.Context,
    scrollState: androidx.compose.foundation.ScrollState,
    settingsVersion: Int,
    swingSettings: com.air.pong.core.game.SwingSettings,
    onSwingSettingUpdate: ((com.air.pong.core.game.SwingSettings) -> com.air.pong.core.game.SwingSettings) -> Unit,
    onSettingsChange: () -> Unit,
    onShowInfo: (String, String) -> Unit,
    onReset: () -> Unit,
    isReadOnly: Boolean,
    isRallyShrinkEnabled: Boolean,
    onRallyShrinkChange: (Boolean) -> Unit,
    soloFlightAdder: Long,
    onSoloFlightAdderChange: (Long) -> Unit
) {
    // Use SwingParamsGrid directly - it has its own expand/collapse
    SwingParamsGrid(
        scrollState = scrollState,
        settingsVersion = settingsVersion,
        swingSettings = swingSettings,
        onSwingSettingUpdate = onSwingSettingUpdate,
        onSettingsChange = onSettingsChange,
        onShowInfo = onShowInfo,
        onReset = onReset,
        isReadOnly = isReadOnly,
        isRallyShrinkEnabled = isRallyShrinkEnabled,
        onRallyShrinkChange = onRallyShrinkChange,
        soloFlightAdder = soloFlightAdder,
        onSoloFlightAdderChange = onSoloFlightAdderChange
    )
}

@Composable
private fun BonusMechanicsSection(
    context: android.content.Context,
    scrollState: androidx.compose.foundation.ScrollState,
    isReadOnly: Boolean,
    bonusSpinEnabled: Boolean,
    bonusCopyCatEnabled: Boolean,
    bonusSpecialSquaresEnabled: Boolean,
    bonusPowerOutageEnabled: Boolean,
    onBonusSpinChange: (Boolean) -> Unit,
    onBonusCopyCatChange: (Boolean) -> Unit,
    onBonusSpecialSquaresChange: (Boolean) -> Unit,
    onBonusPowerOutageChange: (Boolean) -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val allEnabled = bonusSpinEnabled && bonusCopyCatEnabled && bonusSpecialSquaresEnabled && bonusPowerOutageEnabled
    val noneEnabled = !bonusSpinEnabled && !bonusCopyCatEnabled && !bonusSpecialSquaresEnabled && !bonusPowerOutageEnabled
    
    // Determine tri-state: ON (all), OFF (none), or INDETERMINATE (partial)
    val toggleState = when {
        allEnabled -> androidx.compose.ui.state.ToggleableState.On
        noneEnabled -> androidx.compose.ui.state.ToggleableState.Off
        else -> androidx.compose.ui.state.ToggleableState.Indeterminate
    }

    // Auto-scroll when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            kotlinx.coroutines.delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Master checkbox row with caret
        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isReadOnly) { isExpanded = !isExpanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(
                state = toggleState,
                onClick = if (isReadOnly) null else {
                    {
                        // Toggle all: if any are off, turn all on; otherwise turn all off
                        val newState = !allEnabled
                        onBonusSpinChange(newState)
                        onBonusCopyCatChange(newState)
                        onBonusSpecialSquaresChange(newState)
                        onBonusPowerOutageChange(newState)
                    }
                }
            )
            Text(stringResource(R.string.bonus_mechanics), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onShowInfo(context.getString(R.string.info_bonus_mechanics_title), context.getString(R.string.info_bonus_mechanics_text)) }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
            Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (isExpanded) "Collapse" else "Expand")
        }

        // Expandable sub-options
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 12.dp)) {
                // Toggleable rows
                BonusCheckboxRow(stringResource(R.string.bonus_spin_multiplier), bonusSpinEnabled, onBonusSpinChange, isReadOnly) { onShowInfo(context.getString(R.string.info_bonus_spin_title), context.getString(R.string.info_bonus_spin_text)) }
                BonusCheckboxRow(stringResource(R.string.bonus_copy_cat), bonusCopyCatEnabled, onBonusCopyCatChange, isReadOnly) { onShowInfo(context.getString(R.string.info_bonus_copy_cat_title), context.getString(R.string.info_bonus_copy_cat_text)) }
                BonusCheckboxRow(stringResource(R.string.bonus_special_squares), bonusSpecialSquaresEnabled, onBonusSpecialSquaresChange, isReadOnly) { onShowInfo(context.getString(R.string.info_bonus_special_squares_title), context.getString(R.string.info_bonus_special_squares_text)) }
                BonusCheckboxRow(stringResource(R.string.bonus_power_outage), bonusPowerOutageEnabled, onBonusPowerOutageChange, isReadOnly) { onShowInfo(context.getString(R.string.info_bonus_power_outage_title), context.getString(R.string.info_bonus_power_outage_text)) }
                // Info-only rows (always checked, greyed out)
                BonusInfoRow(stringResource(R.string.rally_bonus_label)) { onShowInfo(context.getString(R.string.info_rally_bonus_title), context.getString(R.string.info_rally_bonus_text)) }
                BonusInfoRow(stringResource(R.string.grid_upgrades_label)) { onShowInfo(context.getString(R.string.info_grid_upgrades_title), context.getString(R.string.info_grid_upgrades_text)) }
            }
        }
    }
}

@Composable
private fun BonusInfoRow(label: String, onInfoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Checkbox(checked = true, onCheckedChange = null, enabled = false)
        // Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onInfoClick) { Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun BonusCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, isReadOnly: Boolean, onInfoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = !isReadOnly)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onInfoClick) { Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp)) }
    }
}


// ============================================================
// LEGACY COMPOSABLES (kept for compatibility)
// ============================================================

@Composable
fun AccessibilitySection(
    isTapToHitEnabled: Boolean,
    onTapToHitChange: (Boolean) -> Unit,
    onShowInfo: (String, String) -> Unit,
    isReadOnly: Boolean = false,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-Scroll when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            // Wait a frame for layout to update
            kotlinx.coroutines.delay(100)
            // Scroll to ensure the section is visible. 
            // Since this section is near the bottom (above advanced), scrolling to max might be too much if Advanced is closed, 
            // but effectively we just want to show this content. 
            // Given the layout, scrolling a bit down or to the bottom of this section would be ideal.
            // A simple approach used in SwingParamsGrid is scrollState.animateScrollTo(scrollState.maxValue)
            // But since this isn't the last item (SwingParamsGrid is below), we should be careful.
            // However, typically adding a bit of scroll offset helps.
            // Let's just scroll by a fixed amount for now or try to scroll to make it visible if we had coordinates.
            // Without coordinates, `animateScrollTo` with a value is best.
            // Let's scroll the scrollState by the estimated height of the opened section (~200dp).
            scrollState.animateScrollTo(scrollState.value + 1000) 
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
                text = stringResource(R.string.accessibility),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            // Tap-to-Hit Mode Toggle (functional)
            SettingsToggle(
                label = stringResource(R.string.tap_to_hit_mode),
                checked = isTapToHitEnabled,
                onCheckedChange = onTapToHitChange,
                onInfoClick = {
                    onShowInfo(
                        context.getString(R.string.info_tap_to_hit_title),
                        context.getString(R.string.info_tap_to_hit_text)
                    )
                },
                enabled = !isReadOnly
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Color Blind Mode (placeholder - grayed out)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.color_blind_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Text(
                        text = " (${stringResource(R.string.coming_soon)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = {},
                    enabled = false
                )
            }
            
            // Enhanced Audio Cues (placeholder - grayed out)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.enhanced_audio_cues),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Text(
                        text = " (${stringResource(R.string.coming_soon)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = {},
                    enabled = false
                )
            }
            
            // Haptic Intensity (placeholder slider - grayed out)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.haptic_intensity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Text(
                        text = " (${stringResource(R.string.coming_soon)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Slider(
                    value = 0.5f,
                    onValueChange = {},
                    valueRange = 0f..1f,
                    enabled = false
                )
            }
        }
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
    onRallyShrinkChange: (Boolean) -> Unit,
    soloFlightAdder: Long,
    onSoloFlightAdderChange: (Long) -> Unit
) {
    // Force recomposition when values change (local or remote)
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Watch for external updates from network
    LaunchedEffect(settingsVersion) {
        refreshTrigger = settingsVersion
    }

    // Read the state to subscribe to changes
    val trigger = refreshTrigger
    val settings = swingSettings

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Title with Info
        SectionHeader(
            title = stringResource(R.string.swing_parameters),
            onInfoClick = {
                onShowInfo(
                    context.getString(R.string.swing_parameters),
                    context.getString(R.string.info_swing_params_text)
                )
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

            // Table Headers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.swing_type), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.swing_flight), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.swing_net_percent), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.swing_out_percent), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.swing_shrink_percent), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
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
            
        // Spin Shots Info Row (info only, no slider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.spin_shots_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = {
                    onShowInfo(
                        context.getString(R.string.info_spin_shots_title),
                        context.getString(R.string.info_spin_shots_text)
                    )
                }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                }
            }
        }
            
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            
        // Shrinking Hit Window Toggle
        SettingsToggle(
            label = stringResource(R.string.shrinking_hit_window),
            checked = isRallyShrinkEnabled,
            onCheckedChange = onRallyShrinkChange,
            onInfoClick = {
                onShowInfo(
                    context.getString(R.string.info_shrinking_window_title),
                    context.getString(R.string.info_shrinking_window_text, GameEngine.MIN_HIT_WINDOW)
                )
            },
            enabled = !isReadOnly
        )
            
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

        // Solo Flight Time Adder
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.solo_flight_adder_fmt, soloFlightAdder), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(
                    context.getString(R.string.info_solo_flight_adder_title),
                    context.getString(R.string.info_solo_flight_adder_text)
                )
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = soloFlightAdder.toFloat(),
            onValueChange = { 
                // Snap to 100ms
                val snapped = ((it / 100).toInt() * 100).toLong()
                onSoloFlightAdderChange(snapped) 
            },
            valueRange = 200f..800f,
            steps = 5,  // 200, 300, 400, 500, 600, 700, 800 = 6 values = 5 steps
            enabled = !isReadOnly
        )
        
        // Defaults button at bottom-left
        if (!isReadOnly) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                TextButton(onClick = {
                    onReset()
                    refreshTrigger++
                    onSettingsChange()
                    onRallyShrinkChange(true)
                    onSoloFlightAdderChange(500L)
                }) { Text(stringResource(R.string.defaults)) }
            }
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
            title = { Text(stringResource(R.string.fine_tune_value)) },
            text = {
                Column {
                    Text(stringResource(R.string.current_percent, value), style = MaterialTheme.typography.bodyLarge)
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
                    Text(stringResource(R.string.done))
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
            title = { Text(stringResource(R.string.flight_time_modifier)) },
            text = {
                Column {
                    Text(stringResource(R.string.current_multiplier, value), style = MaterialTheme.typography.bodyLarge)
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
                    Text(stringResource(R.string.done))
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
