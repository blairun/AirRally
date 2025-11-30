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
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val gameEngine = GameEngine()
    private val networkAdapter = NearbyConnectionsAdapter(application.applicationContext)
    private val sensorProvider = AccelerometerSensorProvider(application.applicationContext)
    private val messageHandler = NetworkMessageHandler(networkAdapter.observeMessages())
    private val audioManager = com.air.pong.audio.AudioManager(application.applicationContext)
    private val hapticManager = com.air.pong.haptics.HapticManager(application.applicationContext)

    // State
    val gameState = gameEngine.gameState
    val connectionState = networkAdapter.connectionState
    val errorMessage = networkAdapter.errorMessage
    val connectedPlayerName = networkAdapter.connectedEndpointName
    val discoveredEndpoints = networkAdapter.discoveredEndpoints
    
    private val _playerName = kotlinx.coroutines.flow.MutableStateFlow(android.os.Build.MODEL)
    val playerName = _playerName.asStateFlow()
    
    private val _isOpponentInLobby = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isOpponentInLobby = _isOpponentInLobby.asStateFlow()
    
    private val _connectingEndpointId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val connectingEndpointId = _connectingEndpointId.asStateFlow()
    
    var isHost = false
        private set
    
    private val sharedPrefs = application.getSharedPreferences("airrally_prefs", android.content.Context.MODE_PRIVATE)

    init {
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
        val savedMinSwingThreshold = sharedPrefs.getFloat("min_swing_threshold", 16.0f)

        _playerName.value = savedPlayerName

        gameEngine.updateSettings(savedFlightTime, savedDifficulty, savedDebugMode, savedDebugTones, savedMinSwingThreshold)
        sensorProvider.setSwingThreshold(savedMinSwingThreshold)

        // Observe Network Messages
        viewModelScope.launch {
            messageHandler.observeGameMessages().collect { msg ->
                handleMessage(msg)
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
                        sendMessage(GameMessage.Settings(currentState.flightTime, currentState.difficulty))
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

        // Observe Game Phase for Cooldown
        viewModelScope.launch {
            gameState.collect { state ->
                if (state.gamePhase == GamePhase.POINT_SCORED) {
                    kotlinx.coroutines.delay(2000) // 2 second cooldown
                    gameEngine.startNextServe()
                }
            }
        }
    }
    
    fun resetGameSettings() {
        gameEngine.updateSettings(700L, 400, false, false, 16.0f)
        sensorProvider.setSwingThreshold(16.0f)
        // Also update shared prefs so it persists
        with(sharedPrefs.edit()) {
            putLong("flight_time", 700L)
            putInt("difficulty", 400)
            putBoolean("debug_mode", false)
            putBoolean("debug_tones", false)
            putFloat("min_swing_threshold", 16.0f)
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
                    }
                } else {
                    audioManager.play(com.air.pong.audio.AudioManager.SoundEvent.HIT_HARD, gameEngine.gameState.value.useDebugTones)
                    hapticManager.playHit()
                }
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
            }
            is GameMessage.Result -> {
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
                            android.util.Log.d("GameViewModel", "Game Over detected! Scheduling winner sound.")
                            kotlinx.coroutines.delay(1500) // Additional delay for winner sound (wait for screen transition)
                            android.util.Log.d("GameViewModel", "Playing winner sound now.")
                            audioManager.playRandomWinSound(getApplication<Application>().applicationContext)
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

        if (gameState.value.gamePhase == GamePhase.RALLY || gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE) {
            gameEngine.onPause()
            sendMessage(GameMessage.Pause)
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
        sendMessage(GameMessage.Settings(flightTime, difficulty))
        
        // Save to SharedPreferences
        sharedPrefs.edit().apply {
            putLong("flight_time", flightTime)
            putInt("difficulty", difficulty)
            putBoolean("debug_mode", isDebugMode)
            putBoolean("debug_tones", useDebugTones)
            putFloat("min_swing_threshold", minSwingThreshold)
            apply()
        }
    }

    fun updatePlayerName(name: String) {
        _playerName.value = name
        sharedPrefs.edit().putString("player_name", name).apply()
    }

    fun notifyInLobby() {
        sendMessage(GameMessage.PlayerReady)
        // If I am host, sync my settings to the guest
        if (isHost) {
            val state = gameState.value
            sendMessage(GameMessage.Settings(state.flightTime, state.difficulty))
        }
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
        audioManager.play(event, gameState.value.useDebugTones)
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
}
