package com.air.pong.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.air.pong.core.game.GameEngine
import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.HitResult
import com.air.pong.core.game.getFlightTimeModifier
import com.air.pong.core.game.getServeFlightTimeModifier
import com.air.pong.core.game.isLob
import com.air.pong.core.game.isSmash
import com.air.pong.core.game.SwingSettings
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.ModeState
import com.air.pong.core.network.GameMessage
import com.air.pong.core.network.MessageCodec
import com.air.pong.core.network.NetworkMessageHandler
import com.air.pong.network.NearbyConnectionsAdapter
import com.air.pong.sensors.AccelerometerSensorProvider
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val gameEngine = GameEngine()
    private val networkAdapter = NearbyConnectionsAdapter(application.applicationContext)
    private val sensorProvider = AccelerometerSensorProvider(application.applicationContext)
    private val messageHandler = NetworkMessageHandler(networkAdapter.observeMessages())
    private val audioManager = com.air.pong.audio.AudioManager(application.applicationContext)
    private val hapticManager = com.air.pong.haptics.HapticManager(application.applicationContext)
    private val sharedPrefs = application.getSharedPreferences("airrally_prefs", android.content.Context.MODE_PRIVATE)
    private val swingSettingsRepository = com.air.pong.data.SwingSettingsRepository(sharedPrefs)
    
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

    private val _swingSettingsVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val swingSettingsVersion = _swingSettingsVersion.asStateFlow()
    
    // Expose current swing settings for UI
    private val _swingSettings = kotlinx.coroutines.flow.MutableStateFlow(SwingSettings.createDefault())
    val swingSettings = _swingSettings.asStateFlow()
    
    private var lastSentSwingTimestamp: Long = 0L
    
    var isHost = false
        private set
    
    private val _isSettingsLocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSettingsLocked = _isSettingsLocked.asStateFlow()
    
    private var savedLocalSettings: SwingSettings? = null
    // Also save game engine settings
    private var savedFlightTime: Long = GameEngine.DEFAULT_FLIGHT_TIME
    private var savedDifficulty: Int = GameEngine.DEFAULT_DIFFICULTY
    private var savedRallyShrink: Boolean = true
    private var savedGameMode: GameMode = GameMode.RALLY
    
    // sharedPrefs moved to initialization section above swingSettingsRepository

    // Stats
    private val database = com.air.pong.data.db.AppDatabase.getDatabase(application)
    private val statsRepository = com.air.pong.data.StatsRepository(database.statsDao())
    
    val gameStats = statsRepository.allPoints
    val winCount = statsRepository.winCount
    val lossCount = statsRepository.lossCount
    val longestRally = statsRepository.longestRally
    val totalHits = statsRepository.totalHits

    // Rally High Score
    private val _rallyHighScore = kotlinx.coroutines.flow.MutableStateFlow(0)
    val rallyHighScore = _rallyHighScore.asStateFlow()
    private val _rallyLongestRally = kotlinx.coroutines.flow.MutableStateFlow(0)
    val rallyLongestRally = _rallyLongestRally.asStateFlow()
    private val _isNewHighScore = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isNewHighScore = _isNewHighScore.asStateFlow()
    
    // Partner New High Score (cleared on disconnect/new partner)
    private val _isPartnerNewHighScore = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isPartnerNewHighScore = _isPartnerNewHighScore.asStateFlow()
    
    // All-time Rally Stats
    private val _rallyTotalLinesCleared = kotlinx.coroutines.flow.MutableStateFlow(0)
    val rallyTotalLinesCleared = _rallyTotalLinesCleared.asStateFlow()
    private val _rallyHighestGridLevel = kotlinx.coroutines.flow.MutableStateFlow(0)
    val rallyHighestGridLevel = _rallyHighestGridLevel.asStateFlow()
    
    // Solo Rally Stats (separate from co-op Rally)
    private val _soloHighScore = kotlinx.coroutines.flow.MutableStateFlow(0)
    val soloHighScore = _soloHighScore.asStateFlow()
    private val _soloLongestRally = kotlinx.coroutines.flow.MutableStateFlow(0)
    val soloLongestRally = _soloLongestRally.asStateFlow()
    private val _soloTotalLinesCleared = kotlinx.coroutines.flow.MutableStateFlow(0)
    val soloTotalLinesCleared = _soloTotalLinesCleared.asStateFlow()
    private val _soloHighestGridLevel = kotlinx.coroutines.flow.MutableStateFlow(0)
    val soloHighestGridLevel = _soloHighestGridLevel.asStateFlow()
    private val _isSoloNewHighScore = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSoloNewHighScore = _isSoloNewHighScore.asStateFlow()
    
    // Solo Flight Time Adder (configurable, default 500ms)
    private val _soloFlightAdder = kotlinx.coroutines.flow.MutableStateFlow(GameEngine.DEFAULT_SOLO_FLIGHT_TIME_ADDER)
    val soloFlightAdder = _soloFlightAdder.asStateFlow()

    // Avatar Unlock Notification - true when new avatars have been unlocked but user hasn't visited appearance settings
    private val _hasNewAvatarUnlock = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasNewAvatarUnlock = _hasNewAvatarUnlock.asStateFlow()
    private var previousUnlockedCount = 0
    private var cachedWinCount = 0  // Cached win count for synchronous access

    // Guard against duplicate auto-miss sounds from race conditions
    private var autoMissSoundPlayed = false

    // Debug State
    var isDebugGameSession = false
        private set
        
    private val _isAutoPlayEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAutoPlayEnabled = _isAutoPlayEnabled.asStateFlow()

    init {
        com.air.pong.ui.AvatarUtils.initialize(application.applicationContext)

        // Load saved settings
        val savedFlightTime = sharedPrefs.getLong("flight_time", GameEngine.DEFAULT_FLIGHT_TIME)
        var savedDifficulty = sharedPrefs.getInt("difficulty", GameEngine.DEFAULT_DIFFICULTY)
        
        // Migration: If difficulty is small (legacy index), convert to ms
        if (savedDifficulty < 10) {
            savedDifficulty = when (savedDifficulty) {
                0 -> 1000 // Easy
                1 -> 800 // Medium
                2 -> 400 // Hard
                else -> GameEngine.DEFAULT_DIFFICULTY
            }
        }
        
        val savedDebugMode = sharedPrefs.getBoolean("debug_mode", false)
        val savedDebugTones = sharedPrefs.getBoolean("debug_tones", false)
        val savedPlayerName = sharedPrefs.getString("player_name", null) ?: fetchDeviceName()
        val savedMinSwingThreshold = sharedPrefs.getFloat("min_swing_threshold", GameEngine.DEFAULT_SWING_THRESHOLD)
        val savedRallyShrink = sharedPrefs.getBoolean("rally_shrink", true)
        val savedGameMode = GameMode.entries[sharedPrefs.getInt("game_mode", 1)] // Default to RALLY (1)
        
        // Avatar Logic - on first startup, pick randomly from BASE avatars only
        val savedAvatarIndex = sharedPrefs.getInt("avatar_index", -1)
        val initialAvatarIndex = if (savedAvatarIndex != -1 && savedAvatarIndex in AvatarUtils.avatarResources.indices) {
            savedAvatarIndex
        } else {
            // Random default - pick from BASE avatars only
            val baseAvatars = AvatarUtils.baseAvatars
            val randomBase = if (baseAvatars.isNotEmpty()) {
                baseAvatars[kotlin.random.Random.nextInt(baseAvatars.size)].index
            } else {
                // Fallback if no base avatars found
                0
            }
            sharedPrefs.edit().putInt("avatar_index", randomBase).apply()
            randomBase
        }
        _avatarIndex.value = initialAvatarIndex
        _playerName.value = savedPlayerName

        gameEngine.updateSettings(savedFlightTime, savedDifficulty, savedDebugMode, savedDebugTones, savedMinSwingThreshold, savedRallyShrink, savedGameMode)
        sensorProvider.setSwingThreshold(savedMinSwingThreshold)
        
        // Load Rally High Score and Longest Rally
        _rallyHighScore.value = sharedPrefs.getInt("rally_high_score", 0)
        _rallyLongestRally.value = sharedPrefs.getInt("rally_longest_rally", 0)
        _rallyTotalLinesCleared.value = sharedPrefs.getInt("rally_total_lines_cleared", 0)
        _rallyHighestGridLevel.value = sharedPrefs.getInt("rally_highest_grid_level", 0)
        
        // Load Solo Rally Stats (separate from co-op Rally)
        _soloHighScore.value = sharedPrefs.getInt("solo_rally_high_score", 0)
        _soloLongestRally.value = sharedPrefs.getInt("solo_rally_longest_rally", 0)
        _soloTotalLinesCleared.value = sharedPrefs.getInt("solo_rally_total_lines_cleared", 0)
        _soloHighestGridLevel.value = sharedPrefs.getInt("solo_rally_highest_grid_level", 0)
        
        // Load Solo Flight Time Adder
        val loadedAdder = sharedPrefs.getLong("solo_flight_adder", GameEngine.DEFAULT_SOLO_FLIGHT_TIME_ADDER)
        _soloFlightAdder.value = loadedAdder
        gameEngine.setSoloFlightAdder(loadedAdder)
        
        // Load unlock notification state
        _hasNewAvatarUnlock.value = sharedPrefs.getBoolean("has_new_avatar_unlock", false)
        previousUnlockedCount = countUnlockedAvatars()

        // Load Swing Settings from Repository and sync to GameEngine
        swingSettingsRepository.loadFromPrefs()
        _swingSettings.value = swingSettingsRepository.current
        gameEngine.updateSwingSettings(swingSettingsRepository.current)
        
        // Observe winCount to keep cachedWinCount updated for synchronous access
        viewModelScope.launch {
            winCount.collect { count ->
                cachedWinCount = count ?: 0
            }
        }

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
        
        // Observe Rally Sound Events (works for both Rally and Solo Rally modes)
        viewModelScope.launch {
            gameState.collect { state ->
                // Check both co-op Rally and Solo Rally sound events
                val soundEvent = state.rallyState?.soundEvent ?: state.soloRallyState?.soundEvent
                if (soundEvent != null) {
                    when (soundEvent) {
                        com.air.pong.core.game.RallySoundEvent.LINE_COMPLETE -> {
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LINE_COMPLETE, state.useDebugTones)
                        }
                        com.air.pong.core.game.RallySoundEvent.GRID_COMPLETE -> {
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GRID_COMPLETE, state.useDebugTones)
                        }
                    }
                    // Clear the sound event after playing
                    gameEngine.clearRallySoundEvent()
                }
            }
        }
        
        // Observe Connection State
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
                    // Determine host role from network adapter now that connection is established
                    isHost = networkAdapter.isHostRole()
                    gameEngine.setLocalPlayer(isHost)
                    
                    // If host, restore saved game mode and sync settings
                    if (isHost) {
                        gameEngine.updateSettings(
                            gameState.value.flightTime,
                            gameState.value.difficulty,
                            gameState.value.isDebugMode,
                            gameState.value.useDebugTones,
                            gameState.value.minSwingThreshold,
                            gameState.value.isRallyShrinkEnabled,
                            savedGameMode // Restore mode
                        )
                        
                        // Sync settings to the newly connected guest
                        val currentState = gameState.value
                        sendMessage(GameMessage.Settings(currentState.flightTime, currentState.difficulty, getFlattenedSwingSettings(), currentState.isRallyShrinkEnabled, currentState.gameMode))
                    } else {
                        // We are guest. Lock settings.
                        _isSettingsLocked.value = true
                    }
                } else if (state == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCONNECTED || 
                    state == com.air.pong.core.network.NetworkAdapter.ConnectionState.ERROR) {
                    if (gameState.value.gamePhase != GamePhase.IDLE && gameState.value.gamePhase != GamePhase.GAME_OVER) {
                         gameEngine.onPeerLeft()
                    }
                    _isOpponentInLobby.value = false
                    _connectingEndpointId.value = null
                    
                    // Clear partner new high score tracking on disconnect
                    _isPartnerNewHighScore.value = false
                    
                    // Unlock settings and restore if needed
                    _isSettingsLocked.value = false
                    
                    savedLocalSettings?.let {
                        swingSettingsRepository.updateCurrent(it)
                        gameEngine.updateSwingSettings(it)
                        val currentState = gameEngine.gameState.value
                        gameEngine.updateSettings(
                            savedFlightTime, 
                            savedDifficulty, 
                            currentState.isDebugMode, 
                            currentState.useDebugTones, 
                            currentState.minSwingThreshold, 
                            savedRallyShrink,
                            savedGameMode
                        )
                        _swingSettingsVersion.value++
                        savedLocalSettings = null
                    }
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
                    // Reset auto-miss sound guard for new point
                    autoMissSoundPlayed = false
                    
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
                        
                        // Force Sync on Game Over to ensure opponent sees final score/state
                        if (isHost) {
                            sendGameStateSync()
                        }
                    }


                }
        }
        // Periodic Game State Sync (Host Only)
        viewModelScope.launch {
            while (true) {
                if (isHost && networkAdapter.connectionState.value == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
                    sendGameStateSync()
                }
                kotlinx.coroutines.delay(500)
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
        // Don't record stats for debug/simulation sessions
        if (isDebugGameSession) return
        
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
        // Don't record stats for debug/simulation sessions
        if (isDebugGameSession) return
        
        viewModelScope.launch {
            statsRepository.recordGame(
                timestamp = System.currentTimeMillis(),
                opponentName = connectedPlayerName.value ?: "Opponent",
                myScore = if (isHost) state.player1Score else state.player2Score,
                opponentScore = if (isHost) state.player2Score else state.player1Score,
                didIWin = iWon
            )
            
            // Check for classic wins-based avatar unlocks
            // Small delay to allow Room flow to update win count
            kotlinx.coroutines.delay(100)
            checkForNewAvatarUnlocks()
        }
    }
       
    fun resetStats() {
        viewModelScope.launch {
            statsRepository.resetStats()
            
            // Reset Rally Mode Stats
            _rallyHighScore.value = 0
            _rallyLongestRally.value = 0
            _rallyTotalLinesCleared.value = 0
            _rallyHighestGridLevel.value = 0
            _isPartnerNewHighScore.value = false
            
            // Reset Solo Rally Mode Stats
            _soloHighScore.value = 0
            _soloLongestRally.value = 0
            _soloTotalLinesCleared.value = 0
            _soloHighestGridLevel.value = 0
            _isSoloNewHighScore.value = false
            
            sharedPrefs.edit().apply {
                putInt("rally_high_score", 0)
                putInt("rally_longest_rally", 0)
                putInt("rally_total_lines_cleared", 0)
                putInt("rally_highest_grid_level", 0)
                putInt("solo_rally_high_score", 0)
                putInt("solo_rally_longest_rally", 0)
                putInt("solo_rally_total_lines_cleared", 0)
                putInt("solo_rally_highest_grid_level", 0)
                apply()
            }
            
            // Recount unlocked avatars after stats reset
            previousUnlockedCount = countUnlockedAvatars()
        }
    }
    
    /**
     * Count how many avatars are currently unlocked based on stats.
     */
    private fun countUnlockedAvatars(): Int {
        val rallyScore = _rallyHighScore.value
        val rallyLength = _rallyLongestRally.value
        // For classic wins, we need to query synchronously - use SharedPreferences cache
        // Note: statsRepository stores in Room, but we track a cached value for sync access
        val classicWins = cachedWinCount
        
        return AvatarUtils.avatars.count { avatar ->
            AvatarUtils.isUnlocked(avatar, rallyScore, rallyLength, classicWins)
        }
    }
    
    /**
     * Called when user visits appearance settings screen - clears the unlock notification.
     */
    fun onAppearanceVisited() {
        _hasNewAvatarUnlock.value = false
        sharedPrefs.edit().putBoolean("has_new_avatar_unlock", false).apply()
        previousUnlockedCount = countUnlockedAvatars()
    }
    
    /**
     * Check if new avatars have been unlocked since last check.
     * Called after stat updates.
     */
    private fun checkForNewAvatarUnlocks() {
        val currentUnlocked = countUnlockedAvatars()
        if (currentUnlocked > previousUnlockedCount) {
            _hasNewAvatarUnlock.value = true
            sharedPrefs.edit().putBoolean("has_new_avatar_unlock", true).apply()
        }
        previousUnlockedCount = currentUnlocked
    }

    fun resetGameSettings() {
        // Reset only the main sliders (flight time, difficulty, swing threshold)
        // Do NOT reset the shrinking toggle - that's now in the advanced section
        val currentState = gameEngine.gameState.value
        updateSettings(
            GameEngine.DEFAULT_FLIGHT_TIME, 
            GameEngine.DEFAULT_DIFFICULTY, 
            false, 
            false, 
            GameEngine.DEFAULT_SWING_THRESHOLD, 
            currentState.isRallyShrinkEnabled, // Preserve current value
            GameMode.RALLY
        )
    }

    fun updateRallyShrink(isEnabled: Boolean) {
        val currentState = gameEngine.gameState.value
        gameEngine.updateSettings(currentState.flightTime, currentState.difficulty, currentState.isDebugMode, currentState.useDebugTones, currentState.minSwingThreshold, isEnabled, currentState.gameMode)
        sharedPrefs.edit().putBoolean("rally_shrink", isEnabled).apply()
        
        // Sync if host
        if (isHost && connectionState.value == com.air.pong.core.network.NetworkAdapter.ConnectionState.CONNECTED) {
            sendMessage(GameMessage.Settings(currentState.flightTime, currentState.difficulty, getFlattenedSwingSettings(), isEnabled, currentState.gameMode))
        }
    }
    
    fun hostGame() {
        isHost = true
        viewModelScope.launch {
            gameEngine.setLocalPlayer(true)
            networkAdapter.startAdvertising(playerName.value)
            // Ensure default mode or saved mode is set
            gameEngine.updateSettings(
                gameState.value.flightTime,
                gameState.value.difficulty,
                gameState.value.isDebugMode,
                gameState.value.useDebugTones,
                gameState.value.minSwingThreshold,
                gameState.value.isRallyShrinkEnabled,
                savedGameMode // Restore mode
            )
        }
    }
    
    fun joinGame() {
        isHost = false
        viewModelScope.launch {
            gameEngine.setLocalPlayer(false)
            networkAdapter.startDiscovery(playerName.value)
        }
    }
    
    /**
     * Unified connection method: Start both advertising and discovery.
     * The first connection that establishes determines who is host vs. joiner.
     */
    fun playWithFriend() {
        viewModelScope.launch {
            networkAdapter.startPlayWithFriend(playerName.value)
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
        
        // Start game logic with current mode
        gameEngine.startGame(gameState.value.gameMode)
        
        // Notify peer
        sendMessage(GameMessage.StartGame)
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }
    
    /**
     * Starts a Solo Rally game. No network setup needed.
     * Called directly from Main Menu.
     */
    fun startSoloRallyGame() {
        // Set to Solo Rally mode and update state
        isHost = true  // Always "host" in solo
        gameEngine.setLocalPlayer(true)
        
        // Update game mode to Solo Rally
        val currentState = gameState.value
        gameEngine.updateSettings(
            currentState.flightTime,
            currentState.difficulty,
            currentState.isDebugMode,
            currentState.useDebugTones,
            currentState.minSwingThreshold,
            currentState.isRallyShrinkEnabled,
            GameMode.SOLO_RALLY
        )
        
        // Start sensor
        sensorProvider.startListening()
        
        // Start game logic
        gameEngine.startGame(GameMode.SOLO_RALLY)
        
        // Play game start sound
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }
    
    private fun handleSwing(event: com.air.pong.core.sensors.SensorProvider.SwingEvent) {
        // Check phase BEFORE processing swing to know if it was a serve
        val wasServing = gameEngine.gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE
        
        val result = gameEngine.processSwing(event.timestamp, event.force, event.x, event.y, event.z, event.gx, event.gy, event.gz, event.gravX, event.gravY, event.gravZ)
        if (result != null) {
            if (result == HitResult.HIT) {
                val swingType = gameEngine.gameState.value.lastSwingType?.ordinal ?: 0
                val myOrdinal = if (isHost) 0 else 1
                sendMessage(GameMessage.ActionSwing(event.timestamp, event.force, swingType, myOrdinal))
                lastSentSwingTimestamp = event.timestamp
                
                if (wasServing) {
                    val serveSwingType = gameEngine.gameState.value.lastSwingType
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.SERVE, gameEngine.gameState.value.useDebugTones)
                    hapticManager.playHit(serveSwingType)
                    
                    // Calculate Dynamic Bounce Time for Serve
                    val swingType = serveSwingType ?: com.air.pong.core.game.SwingType.MEDIUM_FLAT
                    val baseFlight = gameEngine.gameState.value.flightTime
                    val isSoloRally = gameEngine.gameState.value.gameMode == GameMode.SOLO_RALLY
                    
                    // Use Serve-Specific Flight Time (Smash travels slow-ish overall due to physics arc)
                    // For Solo Rally, GameEngine.getBaseFlightTime() already adds the solo adder
                    val baseFlightTime = if (isSoloRally) {
                        baseFlight + _soloFlightAdder.value
                    } else {
                        baseFlight
                    }
                    val actualFlightTime = (baseFlightTime * swingType.getServeFlightTimeModifier(swingSettingsRepository.current)).toLong()
                    
                    if (isSoloRally) {
                        // Solo Rally Serve Timing: 25% table, 50% wall, 75% table
                        val tableBounce1Delay = (actualFlightTime * 0.25f).toLong().coerceAtLeast(GameEngine.MIN_BOUNCE_DELAY_MS)
                        val wallBounceDelay = (actualFlightTime * 0.50f).toLong()
                        val tableBounce2Delay = (actualFlightTime * 0.75f).toLong()
                        
                        viewModelScope.launch {
                            // First table bounce (outgoing)
                            kotlinx.coroutines.delay(tableBounce1Delay)
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                            
                            // Wall bounce
                            kotlinx.coroutines.delay(wallBounceDelay - tableBounce1Delay)
                            audioManager.playWallBounce()
                            
                            // Second table bounce (return) - this is when hit window opens
                            kotlinx.coroutines.delay(tableBounce2Delay - wallBounceDelay)
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                            gameEngine.onBounce()
                        }
                        
                        // Schedule Auto-Miss Check for Solo Rally Serve
                        // Use ballArrivalTimestamp from GameEngine (authoritative) to calculate delay
                        val expectedArrival = gameEngine.gameState.value.ballArrivalTimestamp
                        val timeToArrival = expectedArrival - System.currentTimeMillis()
                        val (_, endWindow) = gameEngine.getHitWindowBounds()
                        val autoMissDelay = (timeToArrival + endWindow + GameEngine.AUTO_MISS_BUFFER_MS).coerceAtLeast(0)
                        
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(autoMissDelay)
                            if (gameEngine.gameState.value.ballArrivalTimestamp == expectedArrival) {
                                if (gameEngine.checkAutoMiss()) {
                                    // Guard against duplicate sounds
                                    if (!autoMissSoundPlayed) {
                                        autoMissSoundPlayed = true
                                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.MISS_NO_SWING, gameEngine.gameState.value.useDebugTones)
                                        hapticManager.playMiss()
                                        viewModelScope.launch {
                                            kotlinx.coroutines.delay(1000)
                                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Multiplayer: Standard serve timing
                        // Timing Heuristics:
                        // SMASH: Fast Down -> Bounces at 25% of flight
                        // LOB: High Arc -> Bounces at 30%
                        // FLAT: Standard -> Bounces at 30% of flight
                        val bounceRatio = when {
                            swingType.isSmash() -> 0.25f
                            swingType.isLob() -> 0.30f
                            else -> 0.30f 
                        }
                        
                        val bounceDelay = (actualFlightTime * bounceRatio).toLong().coerceAtLeast(GameEngine.MIN_BOUNCE_DELAY_MS)

                        viewModelScope.launch {
                            kotlinx.coroutines.delay(bounceDelay)
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                            gameEngine.onBounce()
                        }
                    }
                } else {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD, gameEngine.gameState.value.useDebugTones)
                    
                    // Play swing type sound effect (lob pop or smash whiz)
                    val currentSwingType = gameEngine.gameState.value.lastSwingType
                    if (currentSwingType?.isLob() == true) {
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BALL_POP_UP, gameEngine.gameState.value.useDebugTones)
                    } else if (currentSwingType?.isSmash() == true) {
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BALL_WHIZ, gameEngine.gameState.value.useDebugTones)
                    }
                    
                    hapticManager.playHit(currentSwingType)
                    
                    val isSoloRally = gameEngine.gameState.value.gameMode == GameMode.SOLO_RALLY
                    
                    if (isSoloRally) {
                        // Solo Rally Rally Hit Timing: wall bounce varies by shot type, 75% table
                        // Must match SoloBallBounceAnimation.calculateSoloRallyPosition timing
                        val swingType = gameEngine.gameState.value.lastSwingType ?: com.air.pong.core.game.SwingType.MEDIUM_FLAT
                        val baseFlight = gameEngine.gameState.value.flightTime
                        val baseFlightTime = baseFlight + _soloFlightAdder.value
                        val actualFlightTime = (baseFlightTime * swingType.getFlightTimeModifier(swingSettingsRepository.current)).toLong()
                        
                        // Wall bounce timing - use centralized constants from GameEngine
                        val wallBounceRatio = GameEngine.getSoloWallBounceRatio(swingType)
                        val wallBounceDelay = (actualFlightTime * wallBounceRatio).toLong().coerceAtLeast(GameEngine.MIN_BOUNCE_DELAY_MS)
                        val tableBounceDelay = (actualFlightTime * GameEngine.SOLO_RALLY_TABLE_BOUNCE_RATIO).toLong()
                        
                        viewModelScope.launch {
                            // Wall bounce
                            kotlinx.coroutines.delay(wallBounceDelay)
                            audioManager.playWallBounce()
                            
                            // Table bounce (return) - this is when hit window opens
                            kotlinx.coroutines.delay(tableBounceDelay - wallBounceDelay)
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                            gameEngine.onBounce()
                        }
                        
                        // Schedule Auto-Miss Check for Solo Rally Rally Hit
                        // Use ballArrivalTimestamp from GameEngine (authoritative) to calculate delay
                        val expectedArrival = gameEngine.gameState.value.ballArrivalTimestamp
                        val timeToArrival = expectedArrival - System.currentTimeMillis()
                        val (_, endWindow) = gameEngine.getHitWindowBounds()
                        val autoMissDelay = (timeToArrival + endWindow + GameEngine.AUTO_MISS_BUFFER_MS).coerceAtLeast(0)
                        
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(autoMissDelay)
                            if (gameEngine.gameState.value.ballArrivalTimestamp == expectedArrival) {
                                if (gameEngine.checkAutoMiss()) {
                                    // Guard against duplicate sounds
                                    if (!autoMissSoundPlayed) {
                                        autoMissSoundPlayed = true
                                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.MISS_NO_SWING, gameEngine.gameState.value.useDebugTones)
                                        hapticManager.playMiss()
                                        viewModelScope.launch {
                                            kotlinx.coroutines.delay(1000)
                                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.LOSE_POINT, gameEngine.gameState.value.useDebugTones)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Multiplayer: Schedule Bounce on Opponent's side
                        val swingType = gameEngine.gameState.value.lastSwingType ?: com.air.pong.core.game.SwingType.MEDIUM_FLAT
                        val flightTime = (gameEngine.gameState.value.flightTime * swingType.getFlightTimeModifier(swingSettingsRepository.current)).toLong()
                        val bounceDelay = flightTime - GameEngine.BOUNCE_OFFSET_MS
                        
                        if (bounceDelay > 0) {
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(bounceDelay)
                                // We don't play bounce sound for opponent side bounce (too far), but we log it.
                                gameEngine.onBounce()
                            }
                        }
                    }
                }

                // Auto-Play Logic for Debug Mode (Opponent's Turn)
                // Handled by SimulatedGameEngine
                simulatedGameEngine.onLocalSwing(HitResult.HIT)
            } else if (result == HitResult.PENDING) {
                // Delayed Miss Logic
                // 1. Play HIT sound and haptic immediately (simulating contact)
                val currentSwingType = gameEngine.gameState.value.lastSwingType
                if (wasServing) {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.SERVE, gameEngine.gameState.value.useDebugTones)
                } else {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD, gameEngine.gameState.value.useDebugTones)
                    
                    // Play swing type sound effect (lob pop or smash whiz) for rally hits only
                    if (currentSwingType?.isLob() == true) {
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BALL_POP_UP, gameEngine.gameState.value.useDebugTones)
                    } else if (currentSwingType?.isSmash() == true) {
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BALL_WHIZ, gameEngine.gameState.value.useDebugTones)
                    }
                }
                
                hapticManager.playHit(currentSwingType)
                
                // 1.5. If Serving (even if Pending Miss), play the Bounce sound
                // This makes faulty serves (Net/Out) still sound like they bounced on my side first.
                if (wasServing) {
                    // Calculate Dynamic Bounce Time for Serve
                    val swingType = gameEngine.gameState.value.lastSwingType ?: com.air.pong.core.game.SwingType.MEDIUM_FLAT
                    val baseFlight = gameEngine.gameState.value.flightTime
                    
                    // Use Serve-Specific Flight Time
                    val actualFlightTime = (baseFlight * swingType.getServeFlightTimeModifier(swingSettingsRepository.current)).toLong()
                    
                    val bounceRatio = when {
                        swingType.isSmash() -> 0.25f
                        swingType.isLob() -> 0.30f
                        else -> 0.30f 
                    }
                    
                    val bounceDelay = (actualFlightTime * bounceRatio).toLong().coerceAtLeast(GameEngine.MIN_BOUNCE_DELAY_MS)

                    viewModelScope.launch {
                        kotlinx.coroutines.delay(bounceDelay)
                        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                        gameEngine.onBounce()
                    }
                }
                
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
                    
                    // If this was a whiff, schedule the ball hitting the ground sound
                    if (result == HitResult.MISS_EARLY || result == HitResult.MISS_LATE) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(400) // Ball hits ground after passing player
                            audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.MISS_TABLE, gameEngine.gameState.value.useDebugTones)
                        }
                    }
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
                gameEngine.startGame(gameEngine.gameState.value.gameMode) // Ensure we stick to the synced mode
                audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
            }
            is GameMessage.Settings -> {
                // If this is the first time we receive authoritative settings, backup our local ones
                if (savedLocalSettings == null) {
                    savedLocalSettings = swingSettingsRepository.current
                    val state = gameEngine.gameState.value
                    savedFlightTime = state.flightTime
                    savedDifficulty = state.difficulty
                    savedRallyShrink = state.isRallyShrinkEnabled
                    savedGameMode = state.gameMode
                }

                val currentDebug = gameEngine.gameState.value.isDebugMode
                val currentUseDebugTones = gameEngine.gameState.value.useDebugTones
                val currentMinThreshold = gameEngine.gameState.value.minSwingThreshold
                gameEngine.updateSettings(msg.flightTime, msg.difficulty, currentDebug, currentUseDebugTones, currentMinThreshold, msg.isRallyShrinkEnabled, msg.gameMode)
                
                // Update Swing Settings from Host
                if (msg.swingSettings.size == 36) {
                    updateSwingSettingsFromList(msg.swingSettings)
                    _swingSettingsVersion.value++
                } else if (msg.swingSettings.size == 27) {
                    // Legacy support or partial update? For now, just ignore or log warning.
                    // Ideally we could map the 27 to the first 27 and leave flight defaults.
                    updateSwingSettingsFromListLegacy(msg.swingSettings)
                    _swingSettingsVersion.value++
                }
            }
            is GameMessage.ActionSwing -> {
                // Fix for Loopback/Premature Swing Bug:
                // Fix for Loopback/Premature Swing Bug:
                // Use Source Player ID to filter out our own messages definitively.
                val myPlayerOrdinal = if (isHost) com.air.pong.core.game.Player.PLAYER_1.ordinal else com.air.pong.core.game.Player.PLAYER_2.ordinal
                
                android.util.Log.d("GameViewModel", "Rx ActionSwing: Source=${msg.sourcePlayerOrdinal}, MyOrdinal=$myPlayerOrdinal, Time=${msg.timestamp}")

                if (msg.sourcePlayerOrdinal == -1) {
                     android.util.Log.e("GameViewModel", "Ignored ActionSwing with INVALID Source ID (-1). Protocol Mismatch or Corruption.")
                     return
                }

                if (msg.sourcePlayerOrdinal == myPlayerOrdinal) {
                     android.util.Log.d("GameViewModel", "Ignored Loopback ActionSwing from myself.")
                     return
                }
                
                // Legacy Fix: Also check timestamp just in case (redundant but safe)
                if (msg.timestamp == lastSentSwingTimestamp) {
                     android.util.Log.w("GameViewModel", "Ignored Loopback ActionSwing by Timestamp matching.")
                     return
                }

                // NOTE: We no longer need the "Time to Expected Arrival" check for loopback,
                // but we might keep it for other validation if strictly necessary.
                // For now, let's remove the confusing 400ms filter.

                // Fix for Clock Synchronization:
                // We cannot rely on msg.timestamp because devices have different clocks.
                // Instead, we assume the swing happened recently (Network Latency ago).
                // We estimate latency to be ~50ms for Bluetooth.
                val estimatedLatency = 50L
                val localSwingTime = System.currentTimeMillis() - estimatedLatency
                
                gameEngine.onOpponentHit(localSwingTime, msg.swingType)
                
                // Schedule Bounce on My Side (Receiver)
                val timeToArrival = gameEngine.gameState.value.ballArrivalTimestamp - System.currentTimeMillis()
                // Use minimum floor to ensure bounce always plays with perceptible delay for fast shots
                val bounceDelay = (timeToArrival - GameEngine.BOUNCE_OFFSET_MS).coerceAtLeast(GameEngine.MIN_BOUNCE_DELAY_MS)
                
                viewModelScope.launch {
                    kotlinx.coroutines.delay(bounceDelay)
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.BOUNCE, gameEngine.gameState.value.useDebugTones)
                    gameEngine.onBounce()
                }
                
                // Schedule Auto-Miss Check
                // Use getHitWindowBounds() to get the scaled window that checkAutoMiss uses
                val (_, endWindow) = gameEngine.getHitWindowBounds()
                val autoMissDelay = timeToArrival + endWindow + GameEngine.AUTO_MISS_BUFFER_MS // Buffer for late swings
                val expectedArrival = gameEngine.gameState.value.ballArrivalTimestamp // Capture for stale check
                
                if (autoMissDelay > 0) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(autoMissDelay)
                        // Check if we missed - but only if this is still the same ball!
                        // If ballArrivalTimestamp changed, a new rally started and this check is stale.
                        if (gameEngine.gameState.value.ballArrivalTimestamp == expectedArrival) {
                            if (gameEngine.checkAutoMiss()) {
                                // We missed!
                                sendMessage(GameMessage.Result(HitResult.MISS_TIMEOUT, null))
                                
                                // Guard against duplicate sounds from race conditions
                                if (!autoMissSoundPlayed) {
                                    autoMissSoundPlayed = true
                                    
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
            is GameMessage.GameStateSync -> {
                gameEngine.syncState(msg)
            }
            else -> {
                // Handle other messages
            }
        }
    }
    
    fun rematch() {
        // Reset high score flag when starting a new game
        _isNewHighScore.value = false
        
        sensorProvider.startListening()
        gameEngine.rematch(isInitiator = true)
        sendMessage(GameMessage.Rematch)
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.GAME_START, gameEngine.gameState.value.useDebugTones)
    }
    
    /**
     * Checks if the current Rally score is a new high score.
     * Also tracks longest rally, lines cleared, and grid level for Rally mode.
     * Should be called when navigating to game over screen in Rally mode.
     */
    fun checkRallyHighScore(): Boolean {
        val state = gameEngine.gameState.value
        val currentScore = state.rallyState?.score ?: 0
        val currentLongestRally = state.longestRally
        val currentHighScore = _rallyHighScore.value
        val currentBestLongestRally = _rallyLongestRally.value
        
        var isNewRecord = false
        
        if (currentScore > currentHighScore) {
            _rallyHighScore.value = currentScore
            _isNewHighScore.value = true
            sharedPrefs.edit().putInt("rally_high_score", currentScore).apply()
            isNewRecord = true
        } else {
            _isNewHighScore.value = false
        }
        
        if (currentLongestRally > currentBestLongestRally) {
            _rallyLongestRally.value = currentLongestRally
            sharedPrefs.edit().putInt("rally_longest_rally", currentLongestRally).apply()
        }
        
        // Track all-time lines cleared (add this game's total)
        val rallyState = state.rallyState
        val linesThisGame = (rallyState?.totalLinesCleared ?: 0) + (rallyState?.partnerTotalLinesCleared ?: 0)
        val newTotalLines = _rallyTotalLinesCleared.value + linesThisGame
        _rallyTotalLinesCleared.value = newTotalLines
        sharedPrefs.edit().putInt("rally_total_lines_cleared", newTotalLines).apply()
        
        // Track highest grid level ever achieved
        val myHighestTier = rallyState?.highestTier ?: 0
        val partnerHighestTier = rallyState?.opponentHighestTier ?: 0
        val highestThisGame = maxOf(myHighestTier, partnerHighestTier)
        if (highestThisGame > _rallyHighestGridLevel.value) {
            _rallyHighestGridLevel.value = highestThisGame
            sharedPrefs.edit().putInt("rally_highest_grid_level", highestThisGame).apply()
        }
        
        // Partner high score tracking - for now just track if score is new record
        // In a full implementation, we'd need to receive partner's high score from network
        // For now, just reset this flag; it would be set by network message
        _isPartnerNewHighScore.value = false
        
        // Check if any new avatars were unlocked from this game
        checkForNewAvatarUnlocks()
        
        return isNewRecord
    }
    
    fun resetNewHighScoreFlag() {
        _isNewHighScore.value = false
    }
    
    /**
     * Checks if the current Solo Rally score is a new high score.
     * Also tracks longest rally, lines cleared, and grid level for Solo Rally mode.
     * Should be called when navigating to game over screen in Solo Rally mode.
     * Note: Solo Rally stats do NOT contribute to avatar unlocks.
     */
    fun checkSoloRallyHighScore(): Boolean {
        val state = gameEngine.gameState.value
        // Solo Rally uses SoloRallyState which extends ModeState
        val soloState = state.soloRallyState
        val currentScore = soloState?.score ?: 0
        val currentLongestRally = state.longestRally
        val currentHighScore = _soloHighScore.value
        val currentBestLongestRally = _soloLongestRally.value
        
        var isNewRecord = false
        
        if (currentScore > currentHighScore) {
            _soloHighScore.value = currentScore
            _isSoloNewHighScore.value = true
            sharedPrefs.edit().putInt("solo_rally_high_score", currentScore).apply()
            isNewRecord = true
        } else {
            _isSoloNewHighScore.value = false
        }
        
        if (currentLongestRally > currentBestLongestRally) {
            _soloLongestRally.value = currentLongestRally
            sharedPrefs.edit().putInt("solo_rally_longest_rally", currentLongestRally).apply()
        }
        
        // Track all-time lines cleared (add this game's total - solo only, no partner)
        val linesThisGame = soloState?.totalLinesCleared ?: 0
        val newTotalLines = _soloTotalLinesCleared.value + linesThisGame
        _soloTotalLinesCleared.value = newTotalLines
        sharedPrefs.edit().putInt("solo_rally_total_lines_cleared", newTotalLines).apply()
        
        // Track highest grid level ever achieved
        val highestThisGame = soloState?.highestTier ?: 0
        if (highestThisGame > _soloHighestGridLevel.value) {
            _soloHighestGridLevel.value = highestThisGame
            sharedPrefs.edit().putInt("solo_rally_highest_grid_level", highestThisGame).apply()
        }
        
        // Note: Solo Rally stats do NOT contribute to avatar unlocks
        // This is intentional to keep solo mode as a practice/casual experience
        
        return isNewRecord
    }
    
    fun resetSoloNewHighScoreFlag() {
        _isSoloNewHighScore.value = false
    }
    
    private fun sendMessage(msg: GameMessage) {
        if (isDebugGameSession) return // Do not send network messages in debug mode
        if (gameState.value.gameMode == GameMode.SOLO_RALLY) return // Solo mode has no network peer

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
    
    private var isSwingTesting = false

    fun onResume() {
        val phase = gameState.value.gamePhase
        // Resume sensors if we are in a game state (active or paused) OR if we are testing swings
        if (phase == GamePhase.RALLY || phase == GamePhase.WAITING_FOR_SERVE || phase == GamePhase.PAUSED || phase == GamePhase.POINT_SCORED || isSwingTesting) {
             sensorProvider.startListening()
        }

        if (phase == GamePhase.PAUSED) {
            gameEngine.onResume()
            sendMessage(GameMessage.Resume)
        }
    }
    
    fun updateSettings(flightTime: Long, difficulty: Int, isDebugMode: Boolean, useDebugTones: Boolean, minSwingThreshold: Float, isRallyShrinkEnabled: Boolean = true, gameMode: GameMode = GameMode.RALLY) {
        gameEngine.updateSettings(flightTime, difficulty, isDebugMode, useDebugTones, minSwingThreshold, isRallyShrinkEnabled, gameMode)
        sensorProvider.setSwingThreshold(minSwingThreshold)
        
        // Only sync synced settings (Flight Time, Difficulty, Mode)
        sendMessage(GameMessage.Settings(flightTime, difficulty, getFlattenedSwingSettings(), isRallyShrinkEnabled, gameMode))
        
        // Save to SharedPreferences
        sharedPrefs.edit().apply {
            putLong("flight_time", flightTime)
            putInt("difficulty", difficulty)
            putBoolean("debug_mode", isDebugMode)
            putBoolean("debug_tones", useDebugTones)
            putFloat("min_swing_threshold", minSwingThreshold)
            putInt("game_mode", gameMode.ordinal)
            apply()
        }
        
        // Save Swing Settings via repository
        swingSettingsRepository.saveToPrefs()
    }
    
    fun setSoloFlightAdder(adderMs: Long) {
        _soloFlightAdder.value = adderMs
        gameEngine.setSoloFlightAdder(adderMs)
        sharedPrefs.edit().putLong("solo_flight_adder", adderMs).apply()
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
            sendMessage(GameMessage.Settings(state.flightTime, state.difficulty, getFlattenedSwingSettings(), state.isRallyShrinkEnabled, state.gameMode))
            sendGameStateSync() // Initial State Sync
        }
    }

    private fun sendGameStateSync() {
        if (!isHost) return
        val state = gameState.value
        
        // Get Rally data from modeState (or use defaults for Classic mode)
        val rallyState = state.modeState as? ModeState.RallyState
        
        // Encode Bitmasks from modeState
        val rallyGridBitmask = encodeGridBitmask(rallyState?.grid ?: emptyList())
        val rallyLinesBitmask = encodeLinesBitmask(rallyState?.linesCompleted ?: emptyList())
        val opponentRallyGridBitmask = encodeGridBitmask(rallyState?.opponentGrid ?: emptyList())
        val opponentRallyLinesBitmask = encodeLinesBitmask(rallyState?.opponentLinesCompleted ?: emptyList())
        
        // Encode Tier Bitmasks from modeState
        val cellTiersBitmask = encodeTiersBitmask(rallyState?.cellTiers ?: emptyList())
        val opponentTiersBitmask = encodeTiersBitmask(rallyState?.opponentCellTiers ?: emptyList())
        
        val msg = GameMessage.GameStateSync(
            player1Score = state.player1Score,
            player2Score = state.player2Score,
            currentPhase = state.gamePhase,
            servingPlayer = state.servingPlayer,
            gameMode = state.gameMode,
            rallyScore = rallyState?.score ?: 0,
            rallyLives = rallyState?.lives ?: 3,
            rallyGridBitmask = rallyGridBitmask,
            rallyLinesBitmask = rallyLinesBitmask,
            opponentRallyGridBitmask = opponentRallyGridBitmask,
            opponentRallyLinesBitmask = opponentRallyLinesBitmask,
            longestRally = state.longestRally,
            cellTiersBitmask = cellTiersBitmask,
            opponentTiersBitmask = opponentTiersBitmask,
            highestTierAchieved = (rallyState?.highestTier ?: 0).toByte(),
            opponentHighestTier = (rallyState?.opponentHighestTier ?: 0).toByte()
        )
        sendMessage(msg)
    }
    
    // Bitmask Helpers
    private fun encodeGridBitmask(grid: List<Boolean>): Short {
        var mask = 0
        grid.forEachIndexed { index, checked ->
            if (checked) mask = mask or (1 shl index)
        }
        return mask.toShort()
    }
    
    private fun encodeLinesBitmask(lines: List<Boolean>): Byte {
        var mask = 0
        lines.forEachIndexed { index, completed ->
            if (completed) mask = mask or (1 shl index)
        }
        return mask.toByte()
    }
    
    private fun encodeTiersBitmask(tiers: List<Int>): Int {
        // Each cell tier encoded in 3 bits (0-7)
        var mask = 0
        tiers.forEachIndexed { index, tier ->
            mask = mask or ((tier and 0b111) shl (index * 3))
        }
        return mask
    }

    fun notifyBusy() {
        sendMessage(GameMessage.PlayerBusy)
        // We don't change our local state, but we tell the opponent we are busy
    }

    private fun getFlattenedSwingSettings(): List<Int> = swingSettingsRepository.getFlattenedSettings()

    private fun updateSwingSettingsFromListLegacy(list: List<Int>) {
        swingSettingsRepository.updateFromLegacyFlattenedSettings(list)
        _swingSettings.value = swingSettingsRepository.current
        gameEngine.updateSwingSettings(swingSettingsRepository.current)
    }

    private fun updateSwingSettingsFromList(list: List<Int>) {
        swingSettingsRepository.updateFromFlattenedSettings(list)
        _swingSettings.value = swingSettingsRepository.current
        gameEngine.updateSwingSettings(swingSettingsRepository.current)
    }
    
    /**
     * Update swing settings using a transformer function.
     * The transformer receives the current settings and returns a modified copy.
     */
    fun updateSwingSettingsProperty(transform: (SwingSettings) -> SwingSettings) {
        val updated = transform(_swingSettings.value)
        swingSettingsRepository.updateCurrent(updated)
        _swingSettings.value = updated
        gameEngine.updateSwingSettings(updated)
    }
    
    fun updateSwingSettings() {
        swingSettingsRepository.saveToPrefs()
        _swingSettings.value = swingSettingsRepository.current
        gameEngine.updateSwingSettings(swingSettingsRepository.current)
        
        // Sync if connected
        val state = gameState.value
        sendMessage(GameMessage.Settings(state.flightTime, state.difficulty, getFlattenedSwingSettings(), state.isRallyShrinkEnabled, state.gameMode))
    }
    
    fun resetSwingSettings() {
        swingSettingsRepository.resetToDefaults()
        _swingSettings.value = swingSettingsRepository.current
        gameEngine.updateSwingSettings(swingSettingsRepository.current)
        
        // Sync settings after reset
        val state = gameState.value
        sendMessage(GameMessage.Settings(state.flightTime, state.difficulty, getFlattenedSwingSettings(), state.isRallyShrinkEnabled, state.gameMode))
        _swingSettingsVersion.value++
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
        isSwingTesting = true
        sensorProvider.startListening()
    }

    fun stopSensorTesting() {
        isSwingTesting = false
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

    fun playVsSound() {
        audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.VS, gameEngine.gameState.value.useDebugTones)
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
        _isOpponentInLobby.value = true
        networkAdapter.setConnectedEndpointName("Simulated Opponent")
        
        // Force CLASSIC mode for simulation (simulation was built for classic mode)
        gameEngine.startGame(GameMode.CLASSIC)
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
        gameEngine.setLocalPlayer(true)
        networkAdapter.setConnectedEndpointName("Simulated Opponent")
        
        // Force CLASSIC mode for simulation (simulation was built for classic mode)
        val currentState = gameState.value
        gameEngine.updateSettings(
            currentState.flightTime,
            currentState.difficulty,
            currentState.isDebugMode,
            currentState.useDebugTones,
            currentState.minSwingThreshold,
            currentState.isRallyShrinkEnabled,
            GameMode.CLASSIC
        )
        
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


    fun setGameMode(mode: GameMode) {
        // Both players can change the game mode - it syncs via network
        val currentState = gameState.value
        updateSettings(
            currentState.flightTime,
            currentState.difficulty,
            currentState.isDebugMode,
            currentState.useDebugTones,
            currentState.minSwingThreshold,
            currentState.isRallyShrinkEnabled,
            mode
        )
    }
}
