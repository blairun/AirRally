package com.air.pong.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.GameViewModel
import com.air.pong.ui.screens.settings.*
import com.air.pong.ui.theme.ThemeMode

enum class SettingsScreenType {
    Main,
    Appearance,
    GameParams,
    Stats,
    Help,
    Debug,
    About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,

    onNavigateBack: () -> Unit,
    onNavigateToGame: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onLaunchTutorial: () -> Unit = {},
    initialScreen: SettingsScreenType = SettingsScreenType.Main,
    scrollToRings: Boolean = false
) {
    val SettingsScreenTypeSaver = Saver<SettingsScreenType, String>(
        save = { it.name },
        restore = { SettingsScreenType.valueOf(it) }
    )
    var currentScreen by rememberSaveable(stateSaver = SettingsScreenTypeSaver) { mutableStateOf(initialScreen) }
    val gameState by viewModel.gameState.collectAsState()
    val playerName by viewModel.playerName.collectAsState()
    val avatarIndex by viewModel.avatarIndex.collectAsState()
    val context = LocalContext.current

    // Local state for UI before saving
    var flightTime by remember { mutableFloatStateOf(gameState.flightTime.toFloat()) }
    var difficulty by remember { mutableIntStateOf(gameState.difficulty) }
    var isDebugMode by remember { mutableStateOf(gameState.isDebugMode) }
    var softThreshold by remember { mutableFloatStateOf(gameState.softSwingThreshold) }
    var mediumThreshold by remember { mutableFloatStateOf(gameState.mediumSwingThreshold) }
    var hardThreshold by remember { mutableFloatStateOf(gameState.hardSwingThreshold) }

    // Update local state if external state changes (e.g. initial load)
    LaunchedEffect(gameState.flightTime, gameState.difficulty, gameState.isDebugMode, gameState.softSwingThreshold, gameState.mediumSwingThreshold, gameState.hardSwingThreshold) {
        flightTime = gameState.flightTime.toFloat()
        difficulty = gameState.difficulty
        isDebugMode = gameState.isDebugMode
        softThreshold = gameState.softSwingThreshold
        mediumThreshold = gameState.mediumSwingThreshold
        hardThreshold = gameState.hardSwingThreshold
    }

    // Manage Sensor Lifecycle for Hit Testing
    DisposableEffect(Unit) {
        viewModel.startSensorTesting()
        onDispose {
            viewModel.stopSensorTesting()
        }
    }

    // Info Dialog State
    var showInfoDialog by remember { mutableStateOf(false) }
    var infoTitle by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(infoTitle) },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Back Handler
    BackHandler(enabled = currentScreen != SettingsScreenType.Main) {
        currentScreen = SettingsScreenType.Main
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            SettingsScreenType.Main -> stringResource(R.string.settings)
                            SettingsScreenType.Appearance -> stringResource(R.string.appearance)
                            SettingsScreenType.GameParams -> stringResource(R.string.game_parameters)
                            SettingsScreenType.Stats -> stringResource(R.string.stats_title)
                            SettingsScreenType.Help -> stringResource(R.string.how_to_play_safety)
                            SettingsScreenType.Debug -> stringResource(R.string.debug_settings)
                            SettingsScreenType.About -> stringResource(R.string.about_airrally)
                        }
                    )
                },
                navigationIcon = {
                    if (currentScreen != SettingsScreenType.Main) {
                        IconButton(onClick = { currentScreen = SettingsScreenType.Main }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                             Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == SettingsScreenType.Main) {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                    }
                },
                label = "SettingsAnimation"
            ) { targetScreen ->
                when (targetScreen) {
                    SettingsScreenType.Main -> SettingsMainScreen(
                        onNavigate = { currentScreen = it }
                    )
                    SettingsScreenType.Appearance -> {
                        // Clear unlock notification when visiting appearance
                        LaunchedEffect(Unit) {
                            viewModel.onAppearanceVisited()
                        }
                        
                        val rallyLinesCleared by viewModel.rallyTotalLinesCleared.collectAsState()
                        val soloLinesCleared by viewModel.soloTotalLinesCleared.collectAsState()
                        val longestRally by viewModel.rallyLongestRally.collectAsState()
                        val classicWins by viewModel.winCount.collectAsState(initial = 0)
                        val ringIndex by viewModel.ringIndex.collectAsState()
                        val rallyHighScore by viewModel.rallyHighScore.collectAsState()
                        val soloHighScore by viewModel.soloHighScore.collectAsState()
                        
                        AppearanceSettingsScreen(
                            currentTheme = currentTheme,
                            onThemeChange = onThemeChange,
                            playerName = playerName,
                            onPlayerNameChange = { viewModel.updatePlayerName(it) },
                            avatarIndex = avatarIndex,
                            onAvatarChange = { viewModel.updateAvatarIndex(it) },
                            ringIndex = ringIndex,
                            onRingChange = { viewModel.updateRingIndex(it) },
                            rallyLinesCleared = rallyLinesCleared,
                            soloLinesCleared = soloLinesCleared,
                            longestRally = longestRally,
                            classicWins = classicWins ?: 0,
                            rallyHighScore = rallyHighScore,
                            soloHighScore = soloHighScore,
                            scrollToRings = scrollToRings
                        )
                    }
                    SettingsScreenType.GameParams -> GameParamsSettingsScreen(
                        flightTime = flightTime,
                        onFlightTimeChange = { flightTime = it },
                        difficulty = difficulty,
                        onDifficultyChange = { difficulty = it },
                        softThreshold = softThreshold,
                        mediumThreshold = mediumThreshold,
                        hardThreshold = hardThreshold,
                        onSoftThresholdChange = { softThreshold = it },
                        onMediumThresholdChange = { mediumThreshold = it },
                        onHardThresholdChange = { hardThreshold = it },
                        onSettingsChange = {
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones, softThreshold, mediumThreshold, hardThreshold, gameState.isRallyShrinkEnabled, gameState.gameMode)
                        },
                        onResetMainDefaults = viewModel::resetGameSettings,
                        onResetSwingDefaults = viewModel::resetSwingSettings,
                        onShowInfo = { title, text ->
                            infoTitle = title
                            infoText = text
                            showInfoDialog = true
                        },
                        lastSwingType = gameState.lastSwingType,
                        lastSwingData = gameState.lastSwingData,
                        swingSettingsVersion = viewModel.swingSettingsVersion.collectAsState().value,
                        swingSettings = viewModel.swingSettings.collectAsState().value,
                        onSwingSettingUpdate = viewModel::updateSwingSettingsProperty,
                        onPlayTestSound = { viewModel.playTestSound(it) },
                        onClearDebugData = { viewModel.clearDebugData() },
                        isRallyShrinkEnabled = gameState.isRallyShrinkEnabled,
                        onRallyShrinkChange = { viewModel.updateRallyShrink(it) },
                        soloFlightAdder = viewModel.soloFlightAdder.collectAsState().value,
                        onSoloFlightAdderChange = { viewModel.setSoloFlightAdder(it) },
                        isReadOnly = viewModel.isSettingsLocked.collectAsState().value,
                        // Calibration parameters
                        swingEvents = viewModel.swingEventsForCalibration,
                        onPlayBounceSound = { viewModel.playBounceSound() },
                        // Accessibility settings
                        isTapToHitEnabled = viewModel.isTapToHitEnabled.collectAsState().value,
                        onTapToHitChange = { viewModel.setTapToHitEnabled(it) },
                        // Bonus Mechanics
                        bonusSpinEnabled = viewModel.bonusSpinEnabled.collectAsState().value,
                        bonusCopyCatEnabled = viewModel.bonusCopyCatEnabled.collectAsState().value,
                        bonusSpecialSquaresEnabled = viewModel.bonusSpecialSquaresEnabled.collectAsState().value,
                        bonusPowerOutageEnabled = viewModel.bonusPowerOutageEnabled.collectAsState().value,
                        onBonusSpinChange = { viewModel.setBonusSpinEnabled(it) },
                        onBonusCopyCatChange = { viewModel.setBonusCopyCatEnabled(it) },
                        onBonusSpecialSquaresChange = { viewModel.setBonusSpecialSquaresEnabled(it) },
                        onBonusPowerOutageChange = { viewModel.setBonusPowerOutageEnabled(it) }
                    )
                    SettingsScreenType.Stats -> StatsScreen(
                        viewModel = viewModel
                    )
                    SettingsScreenType.Help -> HelpSettingsScreen(
                        onLaunchTutorial = {
                            // Navigate back to main menu and trigger tutorial
                            onLaunchTutorial()
                        }
                    )
                    SettingsScreenType.Debug -> DebugSettingsScreen(
                        isDebugMode = isDebugMode,
                        onDebugModeChange = {
                            isDebugMode = it
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones, softThreshold, mediumThreshold, hardThreshold, gameState.isRallyShrinkEnabled, gameState.gameMode)
                        },
                        useDebugTones = gameState.useDebugTones,
                        onUseDebugTonesChange = {
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, it, softThreshold, mediumThreshold, hardThreshold, gameState.isRallyShrinkEnabled, gameState.gameMode)
                        },
                        softThreshold = softThreshold,
                        mediumThreshold = mediumThreshold,
                        hardThreshold = hardThreshold,
                        lastSwingType = gameState.lastSwingType,
                        lastSwingData = gameState.lastSwingData,
                        onPlayTestSound = { viewModel.playTestSound(it) },
                        onShowInfo = { title, text ->
                            infoTitle = title
                            infoText = text
                            showInfoDialog = true
                        },
                        onEnterDebugGame = {
                            viewModel.startDebugGame()
                            onNavigateToGame()
                        },
                        onEnterDebugEndGame = {
                            viewModel.startDebugEndGame()
                            onNavigateToGameOver()
                        },
                        onClearDebugData = { viewModel.clearDebugData() },
                        detectedHand = viewModel.getDetectedHand(),
                        gravXEma = viewModel.getGravXEma(),
                        isLeftHanded = viewModel.getIsLeftHanded()
                    )
                    SettingsScreenType.About -> AboutSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsMainScreen(
    onNavigate: (SettingsScreenType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SettingsCategoryItem(
            title = stringResource(R.string.appearance),
            icon = Icons.Default.Face,
            onClick = { onNavigate(SettingsScreenType.Appearance) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.how_to_play_safety),
            icon = Icons.Default.Abc,
            onClick = { onNavigate(SettingsScreenType.Help) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.stats_title),
            icon = Icons.Default.AutoGraph,
            onClick = { onNavigate(SettingsScreenType.Stats) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.game_parameters),
            icon = Icons.Default.AppRegistration,
            onClick = { onNavigate(SettingsScreenType.GameParams) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.debug_settings),
            icon = Icons.Default.BugReport,
            onClick = { onNavigate(SettingsScreenType.Debug) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.about_airrally),
            icon = Icons.Default.Info,
            onClick = { onNavigate(SettingsScreenType.About) }
        )
    }
}

@Composable
fun SettingsCategoryItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate"
            )
        }
    }
}
