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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onNavigateBack: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(SettingsScreenType.Main) }
    val gameState by viewModel.gameState.collectAsState()
    val playerName by viewModel.playerName.collectAsState()
    val context = LocalContext.current

    // Local state for UI before saving
    var flightTime by remember { mutableFloatStateOf(gameState.flightTime.toFloat()) }
    var difficulty by remember { mutableIntStateOf(gameState.difficulty) }
    var isDebugMode by remember { mutableStateOf(gameState.isDebugMode) }
    var minSwingThreshold by remember { mutableFloatStateOf(gameState.minSwingThreshold) }

    // Update local state if external state changes (e.g. initial load)
    LaunchedEffect(gameState.flightTime, gameState.difficulty, gameState.isDebugMode, gameState.minSwingThreshold) {
        flightTime = gameState.flightTime.toFloat()
        difficulty = gameState.difficulty
        isDebugMode = gameState.isDebugMode
        minSwingThreshold = gameState.minSwingThreshold
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
                            SettingsScreenType.Stats -> "Game Stats"
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
                        // Optional: Show back button to exit settings entirely if desired, 
                        // but usually handled by system back or a specific exit button.
                        // Here we rely on the parent to handle exit if we are at root.
                        // But wait, we have onNavigateBack passed in.
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
                    SettingsScreenType.Appearance -> AppearanceSettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        playerName = playerName,
                        onPlayerNameChange = { viewModel.updatePlayerName(it) }
                    )
                    SettingsScreenType.GameParams -> GameParamsSettingsScreen(
                        flightTime = flightTime,
                        onFlightTimeChange = { flightTime = it },
                        difficulty = difficulty,
                        onDifficultyChange = { difficulty = it },
                        minSwingThreshold = minSwingThreshold,
                        onSwingThresholdChange = { minSwingThreshold = it },
                        onSettingsChange = {
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones, minSwingThreshold)
                        },
                        onResetDefaults = { viewModel.resetGameSettings() },
                        onShowInfo = { title, text ->
                            infoTitle = title
                            infoText = text
                            showInfoDialog = true
                        }
                    )
                    SettingsScreenType.Stats -> StatsScreen(
                        viewModel = viewModel
                    )
                    SettingsScreenType.Help -> HelpSettingsScreen()
                    SettingsScreenType.Debug -> DebugSettingsScreen(
                        isDebugMode = isDebugMode,
                        onDebugModeChange = {
                            isDebugMode = it
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones, minSwingThreshold)
                        },
                        useDebugTones = gameState.useDebugTones,
                        onUseDebugTonesChange = {
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, it, minSwingThreshold)
                        },
                        lastSwingType = gameState.lastSwingType,
                        lastSwingData = gameState.lastSwingData,
                        onPlayTestSound = { viewModel.playTestSound(it) },
                        onShowInfo = { title, text ->
                            infoTitle = title
                            infoText = text
                            showInfoDialog = true
                        }
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
            icon = Icons.Default.Face, // Assuming Face icon exists or similar
            onClick = { onNavigate(SettingsScreenType.Appearance) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.game_parameters),
            icon = Icons.Default.Settings,
            onClick = { onNavigate(SettingsScreenType.GameParams) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.how_to_play_safety),
            icon = Icons.Default.Info,
            onClick = { onNavigate(SettingsScreenType.Help) }
        )
        SettingsCategoryItem(
            title = "Game Stats",
            icon = Icons.Default.DateRange,
            onClick = { onNavigate(SettingsScreenType.Stats) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.debug_settings),
            icon = Icons.Default.Build,
            onClick = { onNavigate(SettingsScreenType.Debug) }
        )
        SettingsCategoryItem(
            title = stringResource(R.string.about_airrally),
            icon = Icons.Default.Person, // Or Info
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
                // Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                // Keeping it simple without icons for now if they are not readily available in Default set, 
                // or use text only to match previous style but cleaner.
                // Let's stick to text for now to be safe on imports, or use standard icons.
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
