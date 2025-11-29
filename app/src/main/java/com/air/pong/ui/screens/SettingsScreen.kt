package com.air.pong.ui.screens

// Settings Screen for game configuration and sensor testing

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.ui.GameViewModel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable

import com.air.pong.ui.theme.ThemeMode
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.air.pong.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit
) {
    val gameState by viewModel.gameState.collectAsState()
    val playerName by viewModel.playerName.collectAsState()
    val context = LocalContext.current
    
    // Local state for UI before saving
    var flightTime by remember { mutableFloatStateOf(gameState.flightTime.toFloat()) }
    var difficulty by remember { mutableIntStateOf(gameState.difficulty) }
    var isDebugMode by remember { mutableStateOf(gameState.isDebugMode) }
    
    // Update local state if external state changes (e.g. initial load)
    LaunchedEffect(gameState.flightTime, gameState.difficulty, gameState.isDebugMode) {
        flightTime = gameState.flightTime.toFloat()
        difficulty = gameState.difficulty
        isDebugMode = gameState.isDebugMode
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

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var aboutCardY by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Appearance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.appearance),
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { viewModel.updatePlayerName(it) },
                    label = { Text(stringResource(R.string.player_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Game Rules Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.game_parameters),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { 
                        viewModel.resetGameSettings()
                        // Local state update handled by LaunchedEffect observing gameState
                    }) {
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
                        infoTitle = context.getString(R.string.info_flight_time_title)
                        infoText = context.getString(R.string.info_flight_time_text)
                        showInfoDialog = true
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                    }
                }

                Slider(
                    value = flightTime,
                    onValueChange = { 
                        // Snap to 100ms
                        flightTime = (it / 100).toInt() * 100f
                    },
                    valueRange = 500f..1200f,
                    steps = 6, // (1200-500)/100 = 7 intervals -> 6 steps
                    onValueChangeFinished = {
                        viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones)
                    }
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
                        infoTitle = context.getString(R.string.info_hit_window_title)
                        infoText = context.getString(R.string.info_hit_window_text)
                        showInfoDialog = true
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { 
                        // Snap to 50ms
                        val snapped = (it / 50).toInt() * 50
                        difficulty = snapped
                    },
                    valueRange = 200f..700f,
                    steps = 9, // (700-200)/50 = 10 intervals -> 9 steps
                    onValueChangeFinished = {
                        viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Help & Guide Card
        var isHelpExpanded by remember { mutableStateOf(false) }
        var helpCardY by remember { mutableIntStateOf(0) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    helpCardY = coordinates.positionInParent().y.toInt()
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { 
                isHelpExpanded = !isHelpExpanded
                if (isHelpExpanded) {
                    coroutineScope.launch {
                        // Small delay to allow layout to update
                        kotlinx.coroutines.delay(100) 
                        scrollState.animateScrollTo(helpCardY)
                    }
                }
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.how_to_play_safety),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        if (isHelpExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand"
                    )
                }

                if (isHelpExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Safety
                    Text(stringResource(R.string.safety_first), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.safety_strap), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.safety_area), style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gameplay
                    Text(stringResource(R.string.gameplay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.gameplay_hold), style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hold phone with screen facing OUT (away from palm).", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                    // Text("  High Risk: Chance to hit Net or go Out.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    // Text("  Reward: Makes opponent's hit window smaller!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)

                    // Spacer(modifier = Modifier.height(16.dp))
                    
                    // Audio Feedback
                    // Text("🔊 Audio Feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // Spacer(modifier = Modifier.height(4.dp))
                    // Text("Sounds play where the action is:", style = MaterialTheme.typography.bodyMedium)
                    // Text("• You hear sounds for events on YOUR side.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hit Net? You hear it.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hit Out? You won't hear it land (it's far away!), but your opponent will.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Whiff? You hear the whoosh.", style = MaterialTheme.typography.bodyMedium)

                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Debug & Testing Card
        var debugCardY by remember { mutableIntStateOf(0) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    debugCardY = coordinates.positionInParent().y.toInt()
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.use_debug_tones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            infoTitle = context.getString(R.string.info_debug_tones_title)
                            infoText = context.getString(R.string.info_debug_tones_text)
                            showInfoDialog = true
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                        }
                    }
                    Switch(
                        checked = gameState.useDebugTones,
                        onCheckedChange = { 
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, it)
                        }
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
                            infoTitle = context.getString(R.string.info_debug_mode_title)
                            infoText = context.getString(R.string.info_debug_mode_text)
                            showInfoDialog = true
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                        }
                    }
                    Switch(
                        checked = isDebugMode,
                        onCheckedChange = { 
                            isDebugMode = it
                            viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones)
                            
                            if (isDebugMode) {
                                coroutineScope.launch {
                                    // Small delay to allow layout to update
                                    kotlinx.coroutines.delay(100) 
                                    scrollState.animateScrollTo(debugCardY)
                                }
                            }
                        }
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
                        if (gameState.lastSwingType != null) {
                            Text(
                                text = gameState.lastSwingType!!.name.replace("_", " "),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            gameState.lastSwingData?.let { data ->
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
                                     // We need access to AudioManager here, but it's in ViewModel.
                                     // We can expose a method in ViewModel to play test sound.
                                     viewModel.playTestSound(soundEvent)
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
        
        Spacer(modifier = Modifier.height(16.dp))

        // About Card
        var isAboutExpanded by remember { mutableStateOf(false) }
        val uriHandler = LocalUriHandler.current
        val linkColor = Color(0xFF448AFF)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    aboutCardY = coordinates.positionInParent().y.toInt()
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { 
                isAboutExpanded = !isAboutExpanded
                if (isAboutExpanded) {
                    coroutineScope.launch {
                        // Small delay to allow layout to update
                        kotlinx.coroutines.delay(100) 
                        scrollState.animateScrollTo(aboutCardY)
                    }
                }
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.about_airrally),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        if (isAboutExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand"
                    )
                }

                if (isAboutExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

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
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}

