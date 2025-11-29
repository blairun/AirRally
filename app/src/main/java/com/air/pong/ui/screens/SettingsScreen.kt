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

import com.air.pong.ui.theme.ThemeMode

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
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Settings",
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
                    "Appearance",
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
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
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
                    label = { Text("Player Name") },
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
                Text(
                    "Game Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Flight Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Flight Time: ${flightTime.toLong()} ms", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = {
                        infoTitle = "Flight Time"
                        infoText = "Controls how long the ball stays in the air. Higher values mean a slower game, giving you more time to react.\n\n(Synced with opponent)"
                        showInfoDialog = true
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = flightTime,
                    onValueChange = { flightTime = it },
                    valueRange = 500f..1200f,
                    steps = 6,
                    onValueChangeFinished = {
                        viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Difficulty
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Difficulty", style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = {
                        infoTitle = "Difficulty"
                        infoText = "Determines the 'Hit Window' - the margin of error for your swing timing.\n\n" +
                                "Easy: ±${com.air.pong.core.game.GameEngine.WINDOW_EASY}ms\n" +
                                "Medium: ±${com.air.pong.core.game.GameEngine.WINDOW_MEDIUM}ms\n" +
                                "Hard: ±${com.air.pong.core.game.GameEngine.WINDOW_HARD}ms\n\n(Synced with opponent)"
                        showInfoDialog = true
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Easy" to 0, "Medium" to 1, "Hard" to 2).forEach { (label, value) ->
                        FilterChip(
                            selected = difficulty == value,
                            onClick = { 
                                difficulty = value
                                viewModel.updateSettings(flightTime.toLong(), difficulty, isDebugMode, gameState.useDebugTones)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Help & Guide Card
        var isHelpExpanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { isHelpExpanded = !isHelpExpanded }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "How to Play & Safety",
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
                    Text("Safety First", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Use a wrist strap.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Clear your play area.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Hold phone with screen facing OUT (away from palm).", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hold phone with screen facing OUT (away from palm).", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Shot Types
                    Text("Shot Types & Risks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Swing force and phone tilt change your shot:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Vertical = FLAT", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("  Standard shot. Safe and reliable.", style = MaterialTheme.typography.bodyMedium)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Screen Up = LOB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("  Slow, high arc. Safe, gives you time to recover.", style = MaterialTheme.typography.bodyMedium)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Screen Down = SPIKE", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("  Fast & Aggressive!", style = MaterialTheme.typography.bodyMedium)
                    Text("  Makes opponent's hit window shorter, but", style = MaterialTheme.typography.bodyMedium)
                    Text("  Higher chance to hit Net or go Out.", style = MaterialTheme.typography.bodyMedium)
                    // Text("  High Risk: Chance to hit Net or go Out.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    // Text("  Reward: Makes opponent's hit window smaller!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Audio Feedback
                    // Text("🔊 Audio Feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // Spacer(modifier = Modifier.height(4.dp))
                    // Text("Sounds play where the action is:", style = MaterialTheme.typography.bodyMedium)
                    // Text("• You hear sounds for events on YOUR side.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hit Net? You hear it.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Hit Out? You won't hear it land (it's far away!), but your opponent will.", style = MaterialTheme.typography.bodyMedium)
                    // Text("• Whiff? You hear the whoosh.", style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gameplay
                    Text("Gameplay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• YELLOW means Incoming (Get Ready).", style = MaterialTheme.typography.bodyMedium)
                    Text("• Swing when GREEN (After Bounce).", style = MaterialTheme.typography.bodyMedium)
                    Text("• RED means Opponent's Turn.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Debug & Testing Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Debug Tones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            infoTitle = "Debug Tones"
                            infoText = "If enabled, plays simple beep tones instead of the game sound effects."
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
                        Text("Debug Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            infoTitle = "Debug Mode"
                            infoText = "Enables an overlay in the game screen showing technical stats like Round Trip Time (RTT), exact game state, and raw sensor values.\n\n(Local only)"
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
                        }
                    )
                }

                if (isDebugMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sensor Hit Test",
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
                                Text("Force: %.1f".format(data.force), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Accel: X: %.1f, Y: %.1f, Z: %.1f".format(data.accelX, data.accelY, data.accelZ), style = MaterialTheme.typography.labelSmall)
                                Text("Gyro: X: %.1f, Y: %.1f, Z: %.1f".format(data.gyroX, data.gyroY, data.gyroZ), style = MaterialTheme.typography.labelSmall)
                                Text("Grav: X: %.1f, Y: %.1f, Z: %.1f".format(data.gravX, data.gravY, data.gravZ), style = MaterialTheme.typography.labelSmall)
                                
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
                                text = "Swing your phone to test!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

