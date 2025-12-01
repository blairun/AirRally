package com.air.pong.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.GameViewModel
import com.air.pong.ui.theme.ThemeMode
import kotlinx.coroutines.launch

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

@Composable
fun AppearanceSettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = currentTheme == mode,
                    onClick = { onThemeChange(mode) },
                    label = { 
                        Text(
                            when(mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        ) 
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            label = { Text(stringResource(R.string.player_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun GameParamsSettingsScreen(
    flightTime: Float,
    onFlightTimeChange: (Float) -> Unit,
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    minSwingThreshold: Float,
    onSwingThresholdChange: (Float) -> Unit,
    onSettingsChange: () -> Unit,
    onResetDefaults: () -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Defaults Button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onResetDefaults) {
                Text(stringResource(R.string.defaults))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Flight Time
        val flightTimeLabel = when {
            flightTime <= 600 -> stringResource(R.string.difficulty_hard)
            flightTime <= 900 -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.flight_time_label, flightTime.toInt(), flightTimeLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_flight_time_title), context.getString(R.string.info_flight_time_text))
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
            valueRange = 500f..1200f,
            steps = 6,
            onValueChangeFinished = onSettingsChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty (Hit Window)
        val difficultyLabel = when {
            difficulty <= 300 -> stringResource(R.string.difficulty_hard)
            difficulty <= 500 -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hit_window_label, difficulty, difficultyLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_hit_window_title), context.getString(R.string.info_hit_window_text))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = difficulty.toFloat(),
            onValueChange = {
                // Snap to 50ms
                val snapped = (it / 50).toInt() * 50
                onDifficultyChange(snapped)
            },
            valueRange = 200f..700f,
            steps = 9,
            onValueChangeFinished = onSettingsChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Swing Sensitivity (Local)
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
    }
}

@Composable
fun HelpSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Safety
        Text(stringResource(R.string.safety_first), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.safety_strap), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.safety_area), style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Gameplay
        Text(stringResource(R.string.gameplay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.gameplay_hold), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.gameplay_yellow), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.gameplay_green), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.gameplay_red), style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Shot Types
        Text(stringResource(R.string.shot_types_risks), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_types_desc), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_flat), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_flat_desc), style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_lob), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_lob_desc), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_lob_safe), style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_spike), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_spike_desc), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_spike_risk), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_spike_net), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DebugSettingsScreen(
    isDebugMode: Boolean,
    onDebugModeChange: (Boolean) -> Unit,
    useDebugTones: Boolean,
    onUseDebugTonesChange: (Boolean) -> Unit,
    lastSwingType: com.air.pong.core.game.SwingType?,
    lastSwingData: com.air.pong.core.game.SwingData?,
    onPlayTestSound: (com.air.pong.audio.AudioManager.SoundEvent) -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.use_debug_tones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    onShowInfo(context.getString(R.string.info_debug_tones_title), context.getString(R.string.info_debug_tones_text))
                }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                }
            }
            Switch(
                checked = useDebugTones,
                onCheckedChange = onUseDebugTonesChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.debug_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    onShowInfo(context.getString(R.string.info_debug_mode_title), context.getString(R.string.info_debug_mode_text))
                }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                }
            }
            Switch(
                checked = isDebugMode,
                onCheckedChange = onDebugModeChange
            )
        }

        if (isDebugMode) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.sensor_hit_test),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (lastSwingType != null) {
                    Text(
                        text = lastSwingType.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    lastSwingData?.let { data ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.force_fmt, data.force), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.accel_fmt, data.accelX, data.accelY, data.accelZ), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.gyro_fmt, data.gyroX, data.gyroY, data.gyroZ), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.grav_fmt, data.gravX, data.gravY, data.gravZ), style = MaterialTheme.typography.labelSmall)

                        // Play sound based on force for testing
                        LaunchedEffect(data) {
                            val soundEvent = when {
                                data.force > 20.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD
                                data.force > 17.0f -> com.air.pong.audio.AudioManager.SoundEvent.HIT_MEDIUM
                                else -> com.air.pong.audio.AudioManager.SoundEvent.HIT_SOFT
                            }
                            onPlayTestSound(soundEvent)
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.swing_test_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AboutSettingsScreen() {
    val uriHandler = LocalUriHandler.current
    val linkColor = Color(0xFF448AFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium)
            Text(
                com.air.pong.BuildConfig.VERSION_NAME, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/releases") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Credits
        val creditsText = buildAnnotatedString {
            append(stringResource(R.string.created_by))
            withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.created_by_ai))
            }
            append(stringResource(R.string.created_by_r))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.credits), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = creditsText, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://blai.run") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // License
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.license), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.mit_license), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/blob/main/LICENSE") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Source Code
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.source_code), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.view_github_repo), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally") }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Problems?
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.problems), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.report_bug), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/issues") }
            )
        }
    }
}
