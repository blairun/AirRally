package com.air.pong.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.air.pong.core.game.GameEngine
import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.HitResult
import com.air.pong.core.network.GameMessage
import com.air.pong.core.network.MessageCodec
import com.air.pong.core.network.NetworkMessageHandler
import com.air.pong.network.NearbyConnectionsAdapter
import com.air.pong.sensors.AccelerometerSensorProvider
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val gameEngine = GameEngine()
    private val networkAdapter = NearbyConnectionsAdapter(application.applicationContext)
    private val sensorProvider = AccelerometerSensorProvider(application.applicationContext)
    private val messageHandler = NetworkMessageHandler(networkAdapter.observeMessages())
    private val audioManager = com.air.pong.audio.AudioManager(application.applicationContext)
    private val hapticManager = com.air.pong.haptics.HapticManager(application.applicationContext)
    
    // Simulation
    private val simulatedGameEngine = com.air.pong.core.game.SimulatedGameEngine(gameEngine.gameState, viewModelScope)

    // State
    val gameState = gameEngine.gameState
    val connectionState = networkAdapter.connectionState
    val errorMessage = networkAdapter.errorMessage
    val connectedPlayerName = networkAdapter.connectedEndpointName
    val discoveredEndpoints = networkAdapter.discoveredEndpoints
    
    private val _playerName = kotlinx.coroutines.flow.MutableStateFlow(android.os.Build.MODEL)
    val playerName = _playerName.asStateFlow()
    
    private val _avatarIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    val avatarIndex = _avatarIndex.asStateFlow()
    
    private val _isOpponentInLobby = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isOpponentInLobby = _isOpponentInLobby.asStateFlow()
    
    private val _connectingEndpointId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val connectingEndpointId = _connectingEndpointId.asStateFlow()
    
    private val _opponentAvatarIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    val opponentAvatarIndex = _opponentAvatarIndex.asStateFlow()
    
    var isHost = false
        private set
    
    private val sharedPrefs = application.getSharedPreferences("airrally_prefs", android.content.Context.MODE_PRIVATE)

    // Stats
    private val database = com.air.pong.data.db.AppDatabase.getDatabase(application)
    private val statsRepository = com.air.pong.data.StatsRepository(database.statsDao())
    
    val gameStats = statsRepository.allPoints
    val winCount = statsRepository.winCount
    val lossCount = statsRepository.lossCount
    val longestRally = statsRepository.longestRally
    val totalHits = statsRepository.totalHits

    // Debug State
    var isDebugGameSession = false
        private set
        
    private val _isAutoPlayEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAutoPlayEnabled = _isAutoPlayEnabled.asStateFlow()

    init {
        com.air.pong.ui.AvatarUtils.initialize(application.applicationContext)

        // Load saved settings
        val savedFlightTime = sharedPrefs.getLong("flight_time", 700L)
        var savedDifficulty = sharedPrefs.getInt("difficulty", 400)
        
        // Migration: If difficulty is small (legacy index), convert to ms
        if (savedDifficulty < 10) {
            savedDifficulty = when (savedDifficulty) {
                0 -> 700 // Easy
                1 -> 500 // Medium
                2 -> 300 // Hard
                else -> 400
            }
        }
        
        val savedDebugMode = sharedPrefs.getBoolean("debug_mode", false)
        val savedDebugTones = sharedPrefs.getBoolean("debug_tones", false)
        val savedPlayerName = sharedPrefs.getString("player_name", null) ?: fetchDeviceName()
        val savedMinSwingThreshold = sharedPrefs.getFloat("min_swing_threshold", GameEngine.DEFAULT_SWING_THRESHOLD)
        
        // Avatar Logic
        val savedAvatarIndex = sharedPrefs.getInt("avatar_index", -1)
        val initialAvatarIndex = if (savedAvatarIndex != -1 && savedAvatarIndex in AvatarUtils.avatarResources.indices) {
            savedAvatarIndex
        } else {
            // Random default
            val random = kotlin.random.Random.nextInt(AvatarUtils.avatarResources.size)
            sharedPrefs.edit().putInt("avatar_index", random).apply()
            random
        }
        _avatarIndex.value = initialAvatarIndex

        _playerName.value = savedPlayerName

        gameEngine.updateSettings(savedFlightTime, savedDifficulty, savedDebugMode, savedDebugTones, savedMinSwingThreshold)
        sensorProvider.setSwingThreshold(savedMinSwingThreshold)

        // Load Swing Settings
        com.air.pong.core.game.SwingSettings.softFlatNetRisk = sharedPrefs.getInt("softFlatNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_FLAT_NET_RISK)
        com.air.pong.core.game.SwingSettings.softFlatOutRisk = sharedPrefs.getInt("softFlatOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_FLAT_OUT_RISK)
        com.air.pong.core.game.SwingSettings.softFlatShrink = sharedPrefs.getInt("softFlatShrink", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_FLAT_SHRINK)

        com.air.pong.core.game.SwingSettings.mediumFlatNetRisk = sharedPrefs.getInt("mediumFlatNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_FLAT_NET_RISK)
        com.air.pong.core.game.SwingSettings.mediumFlatOutRisk = sharedPrefs.getInt("mediumFlatOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_FLAT_OUT_RISK)
        com.air.pong.core.game.SwingSettings.mediumFlatShrink = sharedPrefs.getInt("mediumFlatShrink", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_FLAT_SHRINK)

        com.air.pong.core.game.SwingSettings.hardFlatNetRisk = sharedPrefs.getInt("hardFlatNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_FLAT_NET_RISK)
        com.air.pong.core.game.SwingSettings.hardFlatOutRisk = sharedPrefs.getInt("hardFlatOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_FLAT_OUT_RISK)
        com.air.pong.core.game.SwingSettings.hardFlatShrink = sharedPrefs.getInt("hardFlatShrink", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_FLAT_SHRINK)

        com.air.pong.core.game.SwingSettings.softLobNetRisk = sharedPrefs.getInt("softLobNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_LOB_NET_RISK)
        com.air.pong.core.game.SwingSettings.softLobOutRisk = sharedPrefs.getInt("softLobOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_LOB_OUT_RISK)
        com.air.pong.core.game.SwingSettings.softLobShrink = sharedPrefs.getInt("softLobShrink", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_LOB_SHRINK)

        com.air.pong.core.game.SwingSettings.mediumLobNetRisk = sharedPrefs.getInt("mediumLobNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_LOB_NET_RISK)
        com.air.pong.core.game.SwingSettings.mediumLobOutRisk = sharedPrefs.getInt("mediumLobOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_LOB_OUT_RISK)
        com.air.pong.core.game.SwingSettings.mediumLobShrink = sharedPrefs.getInt("mediumLobShrink", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_LOB_SHRINK)

        com.air.pong.core.game.SwingSettings.hardLobNetRisk = sharedPrefs.getInt("hardLobNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_LOB_NET_RISK)
        com.air.pong.core.game.SwingSettings.hardLobOutRisk = sharedPrefs.getInt("hardLobOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_LOB_OUT_RISK)
        com.air.pong.core.game.SwingSettings.hardLobShrink = sharedPrefs.getInt("hardLobShrink", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_LOB_SHRINK)

        com.air.pong.core.game.SwingSettings.softSmashNetRisk = sharedPrefs.getInt("softSmashNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_SMASH_NET_RISK)
        com.air.pong.core.game.SwingSettings.softSmashOutRisk = sharedPrefs.getInt("softSmashOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_SMASH_OUT_RISK)
        com.air.pong.core.game.SwingSettings.softSmashShrink = sharedPrefs.getInt("softSmashShrink", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_SMASH_SHRINK)

        com.air.pong.core.game.SwingSettings.mediumSmashNetRisk = sharedPrefs.getInt("mediumSmashNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_SMASH_NET_RISK)
        com.air.pong.core.game.SwingSettings.mediumSmashOutRisk = sharedPrefs.getInt("mediumSmashOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_SMASH_OUT_RISK)
        com.air.pong.core.game.SwingSettings.mediumSmashShrink = sharedPrefs.getInt("mediumSmashShrink", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_SMASH_SHRINK)

        com.air.pong.core.game.SwingSettings.hardSmashNetRisk = sharedPrefs.getInt("hardSmashNetRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_SMASH_NET_RISK)
        com.air.pong.core.game.SwingSettings.hardSmashOutRisk = sharedPrefs.getInt("hardSmashOutRisk", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_SMASH_OUT_RISK)
        com.air.pong.core.game.SwingSettings.hardSmashShrink = sharedPrefs.getInt("hardSmashShrink", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_SMASH_SHRINK)

        // Load Flight Settings
        com.air.pong.core.game.SwingSettings.softFlatFlight = sharedPrefs.getFloat("softFlatFlight", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT)
        com.air.pong.core.game.SwingSettings.mediumFlatFlight = sharedPrefs.getFloat("mediumFlatFlight", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT)
        com.air.pong.core.game.SwingSettings.hardFlatFlight = sharedPrefs.getFloat("hardFlatFlight", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_FLAT_FLIGHT)

        com.air.pong.core.game.SwingSettings.softLobFlight = sharedPrefs.getFloat("softLobFlight", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_LOB_FLIGHT)
        com.air.pong.core.game.SwingSettings.mediumLobFlight = sharedPrefs.getFloat("mediumLobFlight", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_LOB_FLIGHT)
        com.air.pong.core.game.SwingSettings.hardLobFlight = sharedPrefs.getFloat("hardLobFlight", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_LOB_FLIGHT)

        com.air.pong.core.game.SwingSettings.softSmashFlight = sharedPrefs.getFloat("softSmashFlight", com.air.pong.core.game.SwingSettings.DEFAULT_SOFT_SMASH_FLIGHT)
        com.air.pong.core.game.SwingSettings.mediumSmashFlight = sharedPrefs.getFloat("mediumSmashFlight", com.air.pong.core.game.SwingSettings.DEFAULT_MEDIUM_SMASH_FLIGHT)
        com.air.pong.core.game.SwingSettings.hardSmashFlight = sharedPrefs.getFloat("hardSmashFlight", com.air.pong.core.game.SwingSettings.DEFAULT_HARD_SMASH_FLIGHT)

        // Observe Network Messages
        viewModelScope.launch {
            messageHandler.observeGameMessages().collect { msg ->
                handleMessage(msg)
            }
        }
        
        // Observe Simulated Messages
        viewModelScope.launch {
            simulatedGameEngine.messages.collect { msg ->
                handleMessage(msg)
            }
        }
        
        // Observe Simulated Swing Events
        viewModelScope.launch {
            simulatedGameEngine.simulatedSwingEvents.collect { event ->
                handleSwing(event)
            }
        }

        // Observe Sensor Events
        viewModelScope.launch {
            sensorProvider.swingEvents.collect { event ->
                handleSwing(event)
            }
        }
        
        // Observe Connection State
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
                    if (isHost) {
                        // Sync settings to the newly connected guest
                        val currentState = gameState.value
                        sendMessage(GameMessage.Settings(currentState.flightTime, currentState.difficulty, getFlattenedSwingSettings()))
                    }
                } else if (state == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCONNECTED || 
                    state == com.air.pong.core.network.NetworkAdapter.ConnectionState.ERROR) {
                    if (gameState.value.gamePhase != GamePhase.IDLE && gameState.value.gamePhase != GamePhase.GAME_OVER) {
                         gameEngine.onPeerLeft()
                    }
                    _isOpponentInLobby.value = false
                    _connectingEndpointId.value = null
                } else if (state == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
                     _connectingEndpointId.value = null
                }
            }
        }

        // Observe Game Phase for Cooldown, Stats, and Auto-Serve
        viewModelScope.launch {
            gameState.collect { state ->
                if (state.gamePhase == GamePhase.POINT_SCORED) {
                    // Save Stats
                    val lastEvent = state.eventLog.lastOrNull()
                    if (lastEvent is com.air.pong.core.game.GameEvent.PointScored) {
                        savePoint(state, lastEvent.isYou)
                    }
                    
                    kotlinx.coroutines.delay(2000) // 2 second cooldown
                    gameEngine.startNextServe()
                } else if (state.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                    // Auto-Serve Logic handled by SimulatedGameEngine
                    simulatedGameEngine.onGamePhaseChanged(state.gamePhase)
                    
                    // Local Auto-Play (if enabled and my turn)
                    if (isDebugGameSession && _isAutoPlayEnabled.value && state.isMyTurn) {
                        kotlinx.coroutines.delay(1000)
                        simulateLocalSwing()
                    }
                } else if (state.gamePhase == GamePhase.GAME_OVER) {
                    // ...
                }
            }
        }
        
        // Dedicated Stats Observer
        viewModelScope.launch {
            gameState
                .map { it.gamePhase }
                .distinctUntilChanged()
                .collect { phase ->
                    if (phase == GamePhase.GAME_OVER) {
                        val state = gameState.value
                        // Determine winner of the game (and thus the last point)
                        val iWonGame = (isHost && state.player1Score > state.player2Score) || 
                                     (!isHost && state.player2Score > state.player1Score)
                        
                        // Save the last point (for heatmap/rally stats)
                        if (state.currentRallyLength > 0 || state.currentPointShots.isNotEmpty()) {
                            savePoint(state, iWonGame)
                        }
                        
                        // Save the Game Result (for Win/Loss stats)
                        // Only save if the game actually finished (reached score limit)
                        // or if we want to count forfeits? User said "wins/losses should refer to games that have been won or lost"
                        // Assuming "won or lost" implies a completed game or a valid forfeit.
                        // Let's save it if the score is significant or if it's a clear end.
                        // Ideally check if someone reached 11 (or win by 2).
                        
                        val p1 = state.player1Score
                        val p2 = state.player2Score
                        val isFinished = (p1 >= 11 || p2 >= 11) && kotlin.math.abs(p1 - p2) >= 2
                        
                        if (isFinished) {
                            saveGame(state, iWonGame)
                        }
                    }


                }
        }
    }
    
    private fun fetchDeviceName(): String {
        val context = getApplication<Application>().applicationContext
        
        // 1. Try Settings.Global.DEVICE_NAME (API 25+)
        try {
            val deviceName = android.provider.Settings.Global.getString(
                context.contentResolver,
                android.provider.Settings.Global.DEVICE_NAME
            )
            if (!deviceName.isNullOrBlank()) return deviceName
        } catch (e: Exception) {
            // Ignore
        }

        // 2. Try Bluetooth Adapter Name (requires permission, but we might have it or it might be cached)
        try {
            val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter != null && !adapter.name.isNullOrBlank()) {
                return adapter.name
            }
        } catch (e: SecurityException) {
            // Ignore if we don't have permission yet
        }

        // 3. Fallback to Model
        return android.os.Build.MODEL
    }

    private fun savePoint(state: com.air.pong.core.game.GameState, iWon: Boolean) {
        // Determine End Reason
        // Look at the event log. The last event is usually PointScored.
        // We want the event BEFORE that, or the one that caused the point to end.
        val events = state.eventLog
        val endReason = if (events.isNotEmpty()) {
            // Filter out PointScored and BallBounced to find the cause
            val relevantEvents = events.filter { 
                it !is com.air.pong.core.game.GameEvent.PointScored && 
                it !is com.air.pong.core.game.GameEvent.BallBounced 
            }
            
            val lastRelevant = relevantEvents.lastOrNull()
            when (lastRelevant) {
                is com.air.pong.core.game.GameEvent.HitNet -> "NET"
                is com.air.pong.core.game.GameEvent.HitOut -> "OUT"
                is com.air.pong.core.game.GameEvent.WhiffEarly, 
                is com.air.pong.core.game.GameEvent.MissLate -> "WHIFF"
                is com.air.pong.core.game.GameEvent.MissNoSwing -> "TIMEOUT"
                
                is com.air.pong.core.game.GameEvent.OpponentNet -> "OPPONENT_NET"
                is com.air.pong.core.game.GameEvent.OpponentOut -> "OPPONENT_OUT"
                is com.air.pong.core.game.GameEvent.OpponentWhiff -> "OPPONENT_WHIFF"
                is com.air.pong.core.game.GameEvent.OpponentMissNoSwing -> "OPPONENT_TIMEOUT"
                is com.air.pong.core.game.GameEvent.OpponentMiss -> "OPPONENT_MISS"
                
                else -> if (iWon) "WIN" else "LOSS"
            }
        } else {
            if (iWon) "WIN" else "LOSS"
        }

        viewModelScope.launch {
            statsRepository.recordPoint(
                timestamp = System.currentTimeMillis(),
                opponentName = connectedPlayerName.value ?: "Opponent",
                didIWin = iWon,
                rallyLength = state.currentRallyLength,
                myShots = state.currentPointShots,
                endReason = endReason
            )
        }
    }

    private fun saveGame(state: com.air.pong.core.game.GameState, iWon: Boolean) {
        viewModelScope.launch {
            statsRepository.recordGame(
                timestamp = System.currentTimeMillis(),
                opponentName = connectedPlayerName.value ?: "Opponent",
                myScore = if (isHost) state.player1Score else state.player2Score,
                opponentScore = if (isHost) state.player2Score else state.player1Score,
                didIWin = iWon
            )
        }
    }
    
    fun resetStats() {
        viewModelScope.launch {
            statsRepository.resetStats()
        }
    }

    fun resetGameSettings() {
        gameEngine.updateSettings(700L, 400, false, false, GameEngine.DEFAULT_SWING_THRESHOLD)
        sensorProvider.setSwingThreshold(GameEngine.DEFAULT_SWING_THRESHOLD)
        // Also update shared prefs so it persists
        with(sharedPrefs.edit()) {
            putLong("flight_time", 700L)
            putInt("difficulty", 400)
            putBoolean("debug_mode", false)
            putBoolean("debug_tones", false)
            putFloat("min_swing_threshold", GameEngine.DEFAULT_SWING_THRESHOLD)
            apply()
        }
    }


    
    fun hostGame() {
        isHost = true
        viewModelScope.launch {
            gameEngine.setLocalPlayer(true)
            networkAdapter.startAdvertising(playerName.value)
        }
    }
    
    fun joinGame() {
        isHost = false
        viewModelScope.launch {
            gameEngine.setLocalPlayer(false)
            networkAdapter.startDiscovery(playerName.value)
        }
    }
    
    fun connectToEndpoint(endpointId: String) {
        _connectingEndpointId.value = endpointId
        viewModelScope.launch {
            networkAdapter.connectToEndpoint(endpointId)
        }
    }
    
    fun startGame() {
        // Start sensor
        sensorProvider.startListening()
        
        // Start game logic
        gameEngine.startGame()
        
        // Notify peer
        sendMessage(GameMessage.StartGame)
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }
    
    private fun handleSwing(event: com.air.pong.core.sensors.SensorProvider.SwingEvent) {
        // Check phase BEFORE processing swing to know if it was a serve
        val wasServing = gameEngine.gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE
        
        val result = gameEngine.processSwing(event.timestamp, event.force, event.x, event.y, event.z, event.gx, event.gy, event.gz, event.gravX, event.gravY, event.gravZ)
        if (result != null) {
            if (result == HitResult.HIT) {
                val swingType = gameEngine.gameState.value.lastSwingType?.ordinal ?: 0
                sendMessage(GameMessage.ActionSwing(event.timestamp, event.force, swingType))
                
                if (wasServing) {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.SERVE, gameEngine.gameState.value.useDebugTones)
                    hapticManager.playHit()
                    
                    // Schedule Bounce on Server's side (approx 250ms after hit)
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(250)
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                        gameEngine.onBounce()
                    }
                } else {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD, gameEngine.gameState.value.useDebugTones)
                    hapticManager.playHit()
                    
                    // Schedule Bounce on Opponent's side
                    val flightTime = gameEngine.gameState.value.flightTime
                    val bounceDelay = flightTime - GameEngine.BOUNCE_OFFSET_MS
                    
                    if (bounceDelay > 0) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(bounceDelay)
                            // We don't play bounce sound for opponent side bounce (too far), but we log it.
                            gameEngine.onBounce()
                        }
                    }
                }

                // Auto-Play Logic for Debug Mode (Opponent's Turn)
                // Handled by SimulatedGameEngine
                simulatedGameEngine.onLocalSwing(HitResult.HIT)
            } else if (result == HitResult.PENDING) {
                // Delayed Miss Logic
                // 1. Play HIT sound and haptic immediately (simulating contact)
                if (wasServing) {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.SERVE, gameEngine.gameState.value.useDebugTones)
                } else {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD, gameEngine.gameState.value.useDebugTones)
                }
                hapticManager.playHit()
                
                // 2. Schedule the resolution (Net/Out)
                val pendingMiss = gameEngine.gameState.value.pendingMiss
                val delay = pendingMiss?.delayMs ?: 500L
                
                viewModelScope.launch {
                    kotlinx.coroutines.delay(delay)
                    val finalResult = gameEngine.resolvePendingMiss()
                    
                    if (finalResult != null) {
                        // Send Result to opponent (Miss)
                        sendMessage(GameMessage.Result(finalResult, null))
                        
                        // Play Miss Sound
                        val soundEvent = if (finalResult == HitResult.MISS_NET) {
                             com.air.pong.audio.AudioManager.SoundEvent.HIT_NET
                        } else {
                             // For Out, the ball lands on the opponent's side.
                             // We (the hitter) do NOT hear it land.
                             null
                        }
                        
                        if (soundEvent != null) {
                            audioManager.play(soundEvent, gameEngine.gameState.value.useDebugTones)
                        }
                        hapticManager.playMiss()
                        
                        // Play Lose Point sound after a delay
                        kotlinx.coroutines.delay(1000)
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
                    }
                }
            } else {
                sendMessage(GameMessage.Result(result, null)) 
                
                // Redundant call removed: gameEngine.onLocalMiss(result) is NOT needed because processSwing already logs the miss event.
                
                // Local Sound Logic (Spatial Audio):
                // We only play sounds for events happening on OUR side of the table.
                val soundEvent = when (result) {
                    // Scenario 1: Hit Net -> Ball hits net on MY side. I hear it.
                    HitResult.MISS_NET -> com.air.pong.audio.AudioManager.SoundEvent.HIT_NET
                    
                    // Scenario 2: Hit Out -> Ball flies over net and lands out on OPPONENT'S side. 
                    // I do NOT hear it land (too far away). Opponent will hear it.
                    HitResult.MISS_OUT -> null 
                    
                    // Scenario 3: Whiff -> I missed the ball on MY side. I hear the whoosh.
                    HitResult.MISS_EARLY, HitResult.MISS_LATE -> com.air.pong.audio.AudioManager.SoundEvent.MISS_WHIFF
                    
                    // Scenario 4: Timeout -> Ball passed me on MY side. I hear it pass.
                    else -> com.air.pong.audio.AudioManager.SoundEvent.MISS_NO_SWING
                }
                
                if (soundEvent != null) {
                    audioManager.play(soundEvent, gameEngine.gameState.value.useDebugTones)
                }
                
                hapticManager.playMiss()
                
                // Play Lose Point sound after a delay
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1000)
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
                }
            }
        }
    }
    
    private fun handleMessage(msg: GameMessage) {
        when (msg) {
            is GameMessage.StartGame -> {
                sensorProvider.startListening()
                gameEngine.startGame()
                audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
            }
            is GameMessage.Settings -> {
                val currentDebug = gameEngine.gameState.value.isDebugMode
                val currentUseDebugTones = gameEngine.gameState.value.useDebugTones
                val currentMinThreshold = gameEngine.gameState.value.minSwingThreshold
                gameEngine.updateSettings(msg.flightTime, msg.difficulty, currentDebug, currentUseDebugTones, currentMinThreshold)
                
                // Update Swing Settings from Host
                if (msg.swingSettings.size == 36) {
                    updateSwingSettingsFromList(msg.swingSettings)
                } else if (msg.swingSettings.size == 27) {
                    // Legacy support or partial update? For now, just ignore or log warning.
                    // Ideally we could map the 27 to the first 27 and leave flight defaults.
                    updateSwingSettingsFromListLegacy(msg.swingSettings)
                }
            }
            is GameMessage.ActionSwing -> {
                // Fix for Clock Synchronization:
                // We cannot rely on msg.timestamp because devices have different clocks.
                // Instead, we assume the swing happened recently (Network Latency ago).
                // We estimate latency to be ~50ms for Bluetooth.
                val estimatedLatency = 50L
                val localSwingTime = System.currentTimeMillis() - estimatedLatency
                
                gameEngine.onOpponentHit(localSwingTime, msg.swingType)
                
                // Schedule Bounce on My Side (Receiver)
                val timeToArrival = gameEngine.gameState.value.ballArrivalTimestamp - System.currentTimeMillis()
                val bounceDelay = timeToArrival - GameEngine.BOUNCE_OFFSET_MS
                
                if (bounceDelay > 0) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(bounceDelay)
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                        gameEngine.onBounce()
                    }
                } else {
                     gameEngine.onBounce()
                }
                
                // Schedule Auto-Miss Check
                val hitWindow = gameEngine.getHitWindow()
                val autoMissDelay = timeToArrival + hitWindow + 500 // 500ms buffer for late swings
                
                if (autoMissDelay > 0) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(autoMissDelay)
                        // Check if we missed
                        if (gameEngine.checkAutoMiss()) {
                            // We missed!
                            sendMessage(GameMessage.Result(HitResult.MISS_TIMEOUT, null))
                            
                            // Local Sound: I didn't swing, ball passed me.
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.MISS_NO_SWING, gameEngine.gameState.value.useDebugTones)
                            hapticManager.playMiss()
                            
                            // Play Lose Point sound after a delay
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(1000)
                                audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
                            }
                        }
                    }
                }

                // Auto-Play Logic for Local Player (Debug Mode)
                if (isDebugGameSession && _isAutoPlayEnabled.value) {
                     viewModelScope.launch {
                         // 5% chance to NOT swing (Timeout Miss)
                         if (kotlin.random.Random.nextFloat() < 0.05f) {
                             return@launch
                         }

                         // Wait for flight time + random delay to simulate reaction
                         // We want to hit near the arrival time.
                         
                         // Wait a bit for the state to update (it happens in processSwing before we get here)
                         val arrivalTime = gameEngine.gameState.value.ballArrivalTimestamp
                         val window = gameEngine.getHitWindow()
                         
                         // Safe window: +/- (window / 2) * 0.8
                         val safeHalfWindow = (window / 2) * 0.8
                         val randomOffset = (kotlin.random.Random.nextDouble(-safeHalfWindow, safeHalfWindow)).toLong()
                         
                         val targetTime = arrivalTime + randomOffset
                         val delay = targetTime - System.currentTimeMillis()
                         
                         if (delay > 0) {
                             kotlinx.coroutines.delay(delay)
                         }
                         simulateLocalSwing()
                     }
                }
            }
            is GameMessage.Result -> {
                // Deduplication: If we are already in POINT_SCORED or GAME_OVER, ignore this result
                // This prevents double sounds and double state updates if network sends duplicates
                // or if we processed a local miss and then received a remote confirmation.
                val currentPhase = gameEngine.gameState.value.gamePhase
                if (currentPhase == GamePhase.POINT_SCORED || currentPhase == GamePhase.GAME_OVER) {
                    return
                }

                if (msg.hitOrMiss != HitResult.HIT) {
                    gameEngine.onOpponentMiss(msg.hitOrMiss)
                    
                    // Remote Sound Logic (Spatial Audio):
                    // Opponent missed. We only play sound if the event happened on OUR side.
                    val soundEvent = when (msg.hitOrMiss) {
                        // Scenario 1: Opponent Hit Net -> Hits net on THEIR side. I don't hear it.
                        HitResult.MISS_NET -> null 
                        
                        // Scenario 2: Opponent Hit Out -> Ball flew over net and landed out on MY side. I hear it!
                        HitResult.MISS_OUT -> com.air.pong.audio.AudioManager.SoundEvent.MISS_TABLE 
                        
                        // Scenario 3: Opponent Whiffed -> Happened on THEIR side. I don't hear it.
                        HitResult.MISS_EARLY, HitResult.MISS_LATE -> null 
                        
                        // Scenario 4: Opponent Timeout -> Ball passed them on THEIR side. I don't hear it.
                        HitResult.MISS_TIMEOUT -> null 
                        
                        else -> null
                    }
                    
                    if (soundEvent != null) {
                        audioManager.play(soundEvent, gameEngine.gameState.value.useDebugTones)
                    }
                    
                    // Play Win Point sound after a delay
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(1000)
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.WIN_POINT, gameEngine.gameState.value.useDebugTones)
                        
                        // Check for Game Over and play Winner Sound
                        val currentPhase = gameEngine.gameState.value.gamePhase
                        android.util.Log.d("GameViewModel", "Checking for Game Over. Phase: $currentPhase")
                        
                        if (currentPhase == GamePhase.GAME_OVER) {
                            // Only play winner sound if the game actually finished (score limit reached)
                            // This prevents sound from playing on forfeit/back button
                            val state = gameEngine.gameState.value
                            val p1 = state.player1Score
                            val p2 = state.player2Score
                            val isFinished = (p1 >= 11 || p2 >= 11) && kotlin.math.abs(p1 - p2) >= 2
                            
                            if (isFinished) {
                                android.util.Log.d("GameViewModel", "Game Over detected! Scheduling winner sound.")
                                kotlinx.coroutines.delay(1500) // Additional delay for winner sound (wait for screen transition)
                                android.util.Log.d("GameViewModel", "Playing winner sound now.")
                                audioManager.playRandomWinSound(getApplication<Application>().applicationContext)
                            }
                        }
                    }
                }
            }
            is GameMessage.PeerLeft -> {
                gameEngine.onPeerLeft()
            }
            is GameMessage.Pause -> {
                gameEngine.onPause()
            }
            is GameMessage.Resume -> {
                gameEngine.onResume()
            }
            is GameMessage.Rematch -> {
                sensorProvider.startListening()
                gameEngine.rematch(isInitiator = false)
                audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
            }
            is GameMessage.PlayerReady -> {
                _isOpponentInLobby.value = true
                // Send my profile when opponent is ready (or when I join)
                sendMessage(GameMessage.PlayerProfile(playerName.value, _avatarIndex.value))
            }
            is GameMessage.PlayerProfile -> {
                networkAdapter.setConnectedEndpointName(msg.name)
                _opponentAvatarIndex.value = msg.avatarIndex
            }
            is GameMessage.PlayerBusy -> {
                _isOpponentInLobby.value = false
            }
            else -> {
                // Handle other messages
            }
        }
    }
    
    fun rematch() {
        sensorProvider.startListening()
        gameEngine.rematch(isInitiator = true)
        sendMessage(GameMessage.Rematch)
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }
    
    private fun sendMessage(msg: GameMessage) {
        if (isDebugGameSession) return // Do not send network messages in debug mode

        viewModelScope.launch {
            try {
                networkAdapter.sendMessage(MessageCodec.encode(msg))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            networkAdapter.stopAll()
        }
        isDebugGameSession = false
        _isAutoPlayEnabled.value = false
    }

    fun cancelConnection() {
        disconnect()
    }
    
    fun forfeitGame() {
        // Explicitly set state to GAME_OVER before navigating
        gameEngine.onPeerLeft() // Sets local state to GAME_OVER
        sendMessage(GameMessage.PeerLeft) // Notify opponent
    }
    
    fun onPause() {
        // Stop sensors to save battery
        sensorProvider.stopListening()

        val phase = gameState.value.gamePhase
        if (phase == GamePhase.RALLY || phase == GamePhase.WAITING_FOR_SERVE) {
            gameEngine.onPause()
            sendMessage(GameMessage.Pause)
        }
        
        // Stop Advertising/Discovery if we are just in the lobby (not connected yet)
        // This prevents the "ghost lobby" issue where the phone keeps advertising 
        // even after the user has backgrounded the app.
        val connState = connectionState.value
        if (connState == com.air.pong.core.network.NetworkAdapter.ConnectionState.ADVERTISING || 
            connState == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCOVERING) {
            disconnect()
        }
    }
    
    fun onResume() {
        val phase = gameState.value.gamePhase
        // Resume sensors if we are in a game state (active or paused)
        if (phase == GamePhase.RALLY || phase == GamePhase.WAITING_FOR_SERVE || phase == GamePhase.PAUSED || phase == GamePhase.POINT_SCORED) {
             sensorProvider.startListening()
        }

        if (phase == GamePhase.PAUSED) {
            gameEngine.onResume()
            sendMessage(GameMessage.Resume)
        }
    }
    
    fun updateSettings(flightTime: Long, difficulty: Int, isDebugMode: Boolean, useDebugTones: Boolean, minSwingThreshold: Float) {
        gameEngine.updateSettings(flightTime, difficulty, isDebugMode, useDebugTones, minSwingThreshold)
        sensorProvider.setSwingThreshold(minSwingThreshold)
        
        // Only sync synced settings (Flight Time, Difficulty)
        sendMessage(GameMessage.Settings(flightTime, difficulty, getFlattenedSwingSettings()))
        
        // Save to SharedPreferences
        sharedPrefs.edit().apply {
            putLong("flight_time", flightTime)
            putInt("difficulty", difficulty)
            putBoolean("debug_mode", isDebugMode)
            putBoolean("debug_tones", useDebugTones)
            putFloat("min_swing_threshold", minSwingThreshold)
            
            // Save Swing Settings
            putInt("softFlatNetRisk", com.air.pong.core.game.SwingSettings.softFlatNetRisk)
            putInt("softFlatOutRisk", com.air.pong.core.game.SwingSettings.softFlatOutRisk)
            putInt("softFlatShrink", com.air.pong.core.game.SwingSettings.softFlatShrink)
            
            putInt("mediumFlatNetRisk", com.air.pong.core.game.SwingSettings.mediumFlatNetRisk)
            putInt("mediumFlatOutRisk", com.air.pong.core.game.SwingSettings.mediumFlatOutRisk)
            putInt("mediumFlatShrink", com.air.pong.core.game.SwingSettings.mediumFlatShrink)
            
            putInt("hardFlatNetRisk", com.air.pong.core.game.SwingSettings.hardFlatNetRisk)
            putInt("hardFlatOutRisk", com.air.pong.core.game.SwingSettings.hardFlatOutRisk)
            putInt("hardFlatShrink", com.air.pong.core.game.SwingSettings.hardFlatShrink)
            
            putInt("softLobNetRisk", com.air.pong.core.game.SwingSettings.softLobNetRisk)
            putInt("softLobOutRisk", com.air.pong.core.game.SwingSettings.softLobOutRisk)
            putInt("softLobShrink", com.air.pong.core.game.SwingSettings.softLobShrink)
            
            putInt("mediumLobNetRisk", com.air.pong.core.game.SwingSettings.mediumLobNetRisk)
            putInt("mediumLobOutRisk", com.air.pong.core.game.SwingSettings.mediumLobOutRisk)
            putInt("mediumLobShrink", com.air.pong.core.game.SwingSettings.mediumLobShrink)
            
            putInt("hardLobNetRisk", com.air.pong.core.game.SwingSettings.hardLobNetRisk)
            putInt("hardLobOutRisk", com.air.pong.core.game.SwingSettings.hardLobOutRisk)
            putInt("hardLobShrink", com.air.pong.core.game.SwingSettings.hardLobShrink)
            
            putInt("softSmashNetRisk", com.air.pong.core.game.SwingSettings.softSmashNetRisk)
            putInt("softSmashOutRisk", com.air.pong.core.game.SwingSettings.softSmashOutRisk)
            putInt("softSmashShrink", com.air.pong.core.game.SwingSettings.softSmashShrink)
            
            putInt("mediumSmashNetRisk", com.air.pong.core.game.SwingSettings.mediumSmashNetRisk)
            putInt("mediumSmashOutRisk", com.air.pong.core.game.SwingSettings.mediumSmashOutRisk)
            putInt("mediumSmashShrink", com.air.pong.core.game.SwingSettings.mediumSmashShrink)
            
            putInt("hardSmashNetRisk", com.air.pong.core.game.SwingSettings.hardSmashNetRisk)
            putInt("hardSmashOutRisk", com.air.pong.core.game.SwingSettings.hardSmashOutRisk)
            putInt("hardSmashShrink", com.air.pong.core.game.SwingSettings.hardSmashShrink)
            
            putFloat("softFlatFlight", com.air.pong.core.game.SwingSettings.softFlatFlight)
            putFloat("mediumFlatFlight", com.air.pong.core.game.SwingSettings.mediumFlatFlight)
            putFloat("hardFlatFlight", com.air.pong.core.game.SwingSettings.hardFlatFlight)
            
            putFloat("softLobFlight", com.air.pong.core.game.SwingSettings.softLobFlight)
            putFloat("mediumLobFlight", com.air.pong.core.game.SwingSettings.mediumLobFlight)
            putFloat("hardLobFlight", com.air.pong.core.game.SwingSettings.hardLobFlight)
            
            putFloat("softSmashFlight", com.air.pong.core.game.SwingSettings.softSmashFlight)
            putFloat("mediumSmashFlight", com.air.pong.core.game.SwingSettings.mediumSmashFlight)
            putFloat("hardSmashFlight", com.air.pong.core.game.SwingSettings.hardSmashFlight)
            
            apply()
        }
    }

    fun updatePlayerName(name: String) {
        _playerName.value = name
        sharedPrefs.edit().putString("player_name", name).apply()
    }
    
    fun updateAvatarIndex(index: Int) {
        if (index in AvatarUtils.avatarResources.indices) {
            _avatarIndex.value = index
            sharedPrefs.edit().putInt("avatar_index", index).apply()
            
            // If connected, sync new avatar
            if (connectionState.value == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
                sendMessage(GameMessage.PlayerProfile(playerName.value, index))
            }
        }
    }

    fun notifyInLobby() {
        sendMessage(GameMessage.PlayerReady)
        sendMessage(GameMessage.PlayerProfile(playerName.value, _avatarIndex.value))
        // If I am host, sync my settings to the guest
        if (isHost) {
            val state = gameState.value
            sendMessage(GameMessage.Settings(state.flightTime, state.difficulty, getFlattenedSwingSettings()))
        }
    }

    fun notifyBusy() {
        sendMessage(GameMessage.PlayerBusy)
        // We don't change our local state, but we tell the opponent we are busy
    }

    private fun getFlattenedSwingSettings(): List<Int> {
        return listOf(
            com.air.pong.core.game.SwingSettings.softFlatNetRisk, com.air.pong.core.game.SwingSettings.softFlatOutRisk, com.air.pong.core.game.SwingSettings.softFlatShrink, (com.air.pong.core.game.SwingSettings.softFlatFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.mediumFlatNetRisk, com.air.pong.core.game.SwingSettings.mediumFlatOutRisk, com.air.pong.core.game.SwingSettings.mediumFlatShrink, (com.air.pong.core.game.SwingSettings.mediumFlatFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.hardFlatNetRisk, com.air.pong.core.game.SwingSettings.hardFlatOutRisk, com.air.pong.core.game.SwingSettings.hardFlatShrink, (com.air.pong.core.game.SwingSettings.hardFlatFlight * 100).toInt(),
            
            com.air.pong.core.game.SwingSettings.softLobNetRisk, com.air.pong.core.game.SwingSettings.softLobOutRisk, com.air.pong.core.game.SwingSettings.softLobShrink, (com.air.pong.core.game.SwingSettings.softLobFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.mediumLobNetRisk, com.air.pong.core.game.SwingSettings.mediumLobOutRisk, com.air.pong.core.game.SwingSettings.mediumLobShrink, (com.air.pong.core.game.SwingSettings.mediumLobFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.hardLobNetRisk, com.air.pong.core.game.SwingSettings.hardLobOutRisk, com.air.pong.core.game.SwingSettings.hardLobShrink, (com.air.pong.core.game.SwingSettings.hardLobFlight * 100).toInt(),
            
            com.air.pong.core.game.SwingSettings.softSmashNetRisk, com.air.pong.core.game.SwingSettings.softSmashOutRisk, com.air.pong.core.game.SwingSettings.softSmashShrink, (com.air.pong.core.game.SwingSettings.softSmashFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.mediumSmashNetRisk, com.air.pong.core.game.SwingSettings.mediumSmashOutRisk, com.air.pong.core.game.SwingSettings.mediumSmashShrink, (com.air.pong.core.game.SwingSettings.mediumSmashFlight * 100).toInt(),
            com.air.pong.core.game.SwingSettings.hardSmashNetRisk, com.air.pong.core.game.SwingSettings.hardSmashOutRisk, com.air.pong.core.game.SwingSettings.hardSmashShrink, (com.air.pong.core.game.SwingSettings.hardSmashFlight * 100).toInt()
        )
    }

    private fun updateSwingSettingsFromListLegacy(list: List<Int>) {
        if (list.size != 27) return
        
        var i = 0
        com.air.pong.core.game.SwingSettings.softFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softFlatShrink = list[i++]
        com.air.pong.core.game.SwingSettings.mediumFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumFlatShrink = list[i++]
        com.air.pong.core.game.SwingSettings.hardFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardFlatShrink = list[i++]
        
        com.air.pong.core.game.SwingSettings.softLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softLobShrink = list[i++]
        com.air.pong.core.game.SwingSettings.mediumLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumLobShrink = list[i++]
        com.air.pong.core.game.SwingSettings.hardLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardLobShrink = list[i++]
        
        com.air.pong.core.game.SwingSettings.softSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softSmashShrink = list[i++]
        com.air.pong.core.game.SwingSettings.mediumSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumSmashShrink = list[i++]
        com.air.pong.core.game.SwingSettings.hardSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardSmashShrink = list[i++]
    }

    private fun updateSwingSettingsFromList(list: List<Int>) {
        if (list.size != 36) return
        
        var i = 0
        com.air.pong.core.game.SwingSettings.softFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softFlatShrink = list[i++]; com.air.pong.core.game.SwingSettings.softFlatFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.mediumFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumFlatShrink = list[i++]; com.air.pong.core.game.SwingSettings.mediumFlatFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.hardFlatNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardFlatOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardFlatShrink = list[i++]; com.air.pong.core.game.SwingSettings.hardFlatFlight = list[i++] / 100f
        
        com.air.pong.core.game.SwingSettings.softLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softLobShrink = list[i++]; com.air.pong.core.game.SwingSettings.softLobFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.mediumLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumLobShrink = list[i++]; com.air.pong.core.game.SwingSettings.mediumLobFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.hardLobNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardLobOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardLobShrink = list[i++]; com.air.pong.core.game.SwingSettings.hardLobFlight = list[i++] / 100f
        
        com.air.pong.core.game.SwingSettings.softSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.softSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.softSmashShrink = list[i++]; com.air.pong.core.game.SwingSettings.softSmashFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.mediumSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.mediumSmashShrink = list[i++]; com.air.pong.core.game.SwingSettings.mediumSmashFlight = list[i++] / 100f
        com.air.pong.core.game.SwingSettings.hardSmashNetRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardSmashOutRisk = list[i++]; com.air.pong.core.game.SwingSettings.hardSmashShrink = list[i++]; com.air.pong.core.game.SwingSettings.hardSmashFlight = list[i++] / 100f
    }
    
    fun updateSwingSettings() {
        // Save to SharedPreferences
        with(sharedPrefs.edit()) {
            putInt("softFlatNetRisk", com.air.pong.core.game.SwingSettings.softFlatNetRisk)
            putInt("softFlatOutRisk", com.air.pong.core.game.SwingSettings.softFlatOutRisk)
            putInt("softFlatShrink", com.air.pong.core.game.SwingSettings.softFlatShrink)
            
            putInt("mediumFlatNetRisk", com.air.pong.core.game.SwingSettings.mediumFlatNetRisk)
            putInt("mediumFlatOutRisk", com.air.pong.core.game.SwingSettings.mediumFlatOutRisk)
            putInt("mediumFlatShrink", com.air.pong.core.game.SwingSettings.mediumFlatShrink)
            
            putInt("hardFlatNetRisk", com.air.pong.core.game.SwingSettings.hardFlatNetRisk)
            putInt("hardFlatOutRisk", com.air.pong.core.game.SwingSettings.hardFlatOutRisk)
            putInt("hardFlatShrink", com.air.pong.core.game.SwingSettings.hardFlatShrink)
            
            putInt("softLobNetRisk", com.air.pong.core.game.SwingSettings.softLobNetRisk)
            putInt("softLobOutRisk", com.air.pong.core.game.SwingSettings.softLobOutRisk)
            putInt("softLobShrink", com.air.pong.core.game.SwingSettings.softLobShrink)
            
            putInt("mediumLobNetRisk", com.air.pong.core.game.SwingSettings.mediumLobNetRisk)
            putInt("mediumLobOutRisk", com.air.pong.core.game.SwingSettings.mediumLobOutRisk)
            putInt("mediumLobShrink", com.air.pong.core.game.SwingSettings.mediumLobShrink)
            
            putInt("hardLobNetRisk", com.air.pong.core.game.SwingSettings.hardLobNetRisk)
            putInt("hardLobOutRisk", com.air.pong.core.game.SwingSettings.hardLobOutRisk)
            putInt("hardLobShrink", com.air.pong.core.game.SwingSettings.hardLobShrink)
            
            putInt("softSmashNetRisk", com.air.pong.core.game.SwingSettings.softSmashNetRisk)
            putInt("softSmashOutRisk", com.air.pong.core.game.SwingSettings.softSmashOutRisk)
            putInt("softSmashShrink", com.air.pong.core.game.SwingSettings.softSmashShrink)
            
            putInt("mediumSmashNetRisk", com.air.pong.core.game.SwingSettings.mediumSmashNetRisk)
            putInt("mediumSmashOutRisk", com.air.pong.core.game.SwingSettings.mediumSmashOutRisk)
            putInt("mediumSmashShrink", com.air.pong.core.game.SwingSettings.mediumSmashShrink)
            
            putInt("hardSmashNetRisk", com.air.pong.core.game.SwingSettings.hardSmashNetRisk)
            putInt("hardSmashOutRisk", com.air.pong.core.game.SwingSettings.hardSmashOutRisk)
            putInt("hardSmashShrink", com.air.pong.core.game.SwingSettings.hardSmashShrink)
            
            apply()
        }
        
        // Sync if connected
        val state = gameState.value
        sendMessage(GameMessage.Settings(state.flightTime, state.difficulty, getFlattenedSwingSettings()))
    }
    
    fun resetSwingSettings() {
        // Reset to defaults
        com.air.pong.core.game.SwingSettings.softFlatNetRisk = 0
        com.air.pong.core.game.SwingSettings.softFlatOutRisk = 0
        com.air.pong.core.game.SwingSettings.softFlatShrink = 0

        com.air.pong.core.game.SwingSettings.mediumFlatNetRisk = 0
        com.air.pong.core.game.SwingSettings.mediumFlatOutRisk = 2
        com.air.pong.core.game.SwingSettings.mediumFlatShrink = 20

        com.air.pong.core.game.SwingSettings.hardFlatNetRisk = 5
        com.air.pong.core.game.SwingSettings.hardFlatOutRisk = 10
        com.air.pong.core.game.SwingSettings.hardFlatShrink = 35

        com.air.pong.core.game.SwingSettings.softLobNetRisk = 0
        com.air.pong.core.game.SwingSettings.softLobOutRisk = 0
        com.air.pong.core.game.SwingSettings.softLobShrink = 0

        com.air.pong.core.game.SwingSettings.mediumLobNetRisk = 0
        com.air.pong.core.game.SwingSettings.mediumLobOutRisk = 5
        com.air.pong.core.game.SwingSettings.mediumLobShrink = 0

        com.air.pong.core.game.SwingSettings.hardLobNetRisk = 0
        com.air.pong.core.game.SwingSettings.hardLobOutRisk = 15
        com.air.pong.core.game.SwingSettings.hardLobShrink = 0

        com.air.pong.core.game.SwingSettings.softSmashNetRisk = 10
        com.air.pong.core.game.SwingSettings.softSmashOutRisk = 0
        com.air.pong.core.game.SwingSettings.softSmashShrink = 20

        com.air.pong.core.game.SwingSettings.mediumSmashNetRisk = 15
        com.air.pong.core.game.SwingSettings.mediumSmashOutRisk = 5
        com.air.pong.core.game.SwingSettings.mediumSmashShrink = 40

        com.air.pong.core.game.SwingSettings.hardSmashNetRisk = 20
        com.air.pong.core.game.SwingSettings.hardSmashOutRisk = 10
        com.air.pong.core.game.SwingSettings.hardSmashShrink = 60
        com.air.pong.core.game.SwingSettings.hardSmashFlight = 0.4f

        com.air.pong.core.game.SwingSettings.softFlatFlight = 1.3f
        com.air.pong.core.game.SwingSettings.mediumFlatFlight = 1.0f
        com.air.pong.core.game.SwingSettings.hardFlatFlight = 0.7f

        com.air.pong.core.game.SwingSettings.softLobFlight = 1.5f
        com.air.pong.core.game.SwingSettings.mediumLobFlight = 1.5f
        com.air.pong.core.game.SwingSettings.hardLobFlight = 1.6f

        com.air.pong.core.game.SwingSettings.softSmashFlight = 0.8f
        com.air.pong.core.game.SwingSettings.mediumSmashFlight = 0.6f
        
        updateSwingSettings()
    }



    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            networkAdapter.stopAll()
        }
        sensorProvider.stopListening()
        audioManager.release()
    }

    fun startSensorTesting() {
        sensorProvider.startListening()
    }

    fun stopSensorTesting() {
        // Only stop if we are NOT in a game
        if (gameState.value.gamePhase == GamePhase.IDLE || gameState.value.gamePhase == GamePhase.GAME_OVER) {
            sensorProvider.stopListening()
        }
    }

    fun playTestSound(event: com.air.pong.audio.AudioManager.SoundEvent) {
        audioManager.play(event, gameEngine.gameState.value.useDebugTones)
    }

    fun playWinSound() {
        audioManager.playRandomWinSound(getApplication<Application>().applicationContext)
    }

    fun setAutoPlay(enabled: Boolean) {
        _isAutoPlayEnabled.value = enabled
        simulatedGameEngine.setAutoPlay(enabled)
    }

    fun startDebugGame() {
        // Ensure we are disconnected from any real peers
        disconnect()
        
        isDebugGameSession = true
        isHost = true
        gameEngine.setLocalPlayer(true)
        gameEngine.setLocalPlayer(true)
        // _playerName.value = "Debug Player" // Removed to preserve original name
        _isOpponentInLobby.value = true
        _isOpponentInLobby.value = true
        // Mock opponent name
        networkAdapter.setConnectedEndpointName("Simulated Opponent")
        
        gameEngine.startGame()
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }



    fun stopDebugGame() {
        isDebugGameSession = false
        gameEngine.resetGame()
        _isOpponentInLobby.value = false
        networkAdapter.setConnectedEndpointName(null)
    }

    fun startDebugEndGame() {
        // Ensure we are disconnected from any real peers
        disconnect()
        
        isDebugGameSession = true
        isHost = true
        isDebugGameSession = true
        isHost = true
        // _playerName.value = "Debug Player" // Removed to preserve original name
        networkAdapter.setConnectedEndpointName("Simulated Opponent")
        networkAdapter.setConnectedEndpointName("Simulated Opponent")
        
        simulateRandomEndGame()
    }

    fun simulateRandomEndGame() {
        if (!isDebugGameSession) return
        
        // Generate random scores where one player has >= 11 and wins by >= 2
        val winner = if (kotlin.random.Random.nextBoolean()) 1 else 2
        val loserScore = kotlin.random.Random.nextInt(0, 10)
        val winnerScore = 11
        
        // Occasional deuce scenario
        val isDeuce = kotlin.random.Random.nextFloat() < 0.3f
        val finalP1Score: Int
        val finalP2Score: Int
        
        if (isDeuce) {
             val base = kotlin.random.Random.nextInt(10, 15)
             if (winner == 1) {
                 finalP1Score = base + 2
                 finalP2Score = base
             } else {
                 finalP1Score = base
                 finalP2Score = base + 2
             }
        } else {
            if (winner == 1) {
                finalP1Score = winnerScore
                finalP2Score = loserScore
            } else {
                finalP1Score = loserScore
                finalP2Score = winnerScore
            }
        }
        
        gameEngine.forceGameOver(finalP1Score, finalP2Score)
        
        // Play appropriate sound
        val iWon = (winner == 1) // Since we are always P1 in debug
        if (iWon) {
             audioManager.playRandomWinSound(getApplication<Application>().applicationContext)
        } else {
             audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
        }
    }

    fun stopDebugEndGame() {
        stopDebugGame()
    }

    fun simulateOpponentSwing() {
        if (!isDebugGameSession) return
        simulatedGameEngine.simulateOpponentSwing()
    }

    fun simulateLocalSwing() {
        if (!isDebugGameSession) return
        simulatedGameEngine.simulateLocalSwing()
    }

    fun clearDebugData() {
        gameEngine.clearDebugData()
    }


}
