package com.air.pong.core.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * The Brain of the application.
 * Manages the State Machine and enforces Game Rules.
 */
class GameEngine {

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val SCORE_LIMIT = 11
    private val SERVE_ROTATION = 2
    private val SERVE_COOLDOWN_MS = 1000L
    private val SWING_COOLDOWN_MS = 500L // Min time between swings to prevent spamming
    
    // Local player identity. Must be set before game starts.
    private var isHost = false
    private var lastPointEndedTimestamp: Long = 0L
    private var lastSwingTimestamp: Long = 0L
    
    fun setLocalPlayer(isHost: Boolean) {
        this.isHost = isHost
    }

    fun startGame() {
        _gameState.update {
            it.copy(
                gamePhase = GamePhase.WAITING_FOR_SERVE,
                player1Score = 0,
                player2Score = 0,
                servingPlayer = Player.PLAYER_1,
                isMyTurn = isHost, // Host serves first (Player 1)
                lastEvent = "Game Started",
                eventLog = emptyList(),
                lastSwingType = null,
                lastSwingData = null
            )
        }
    }

    companion object {
        const val BOUNCE_OFFSET_MS = 200L
    }

    /**
     * Returns the hit window in milliseconds based on difficulty (which is now the window size in ms) AND incoming swing type.
     */
    fun getHitWindow(): Long {
        // Difficulty is now the base window size in ms (200-700)
        val baseWindow = _gameState.value.difficulty.toLong()
        
        // Apply Window Shrink based on the LAST swing type (the incoming shot)
        // If lastSwingType is null (e.g. first serve), no shrink.
        val shrinkPercentage = getWindowShrinkPercentage(_gameState.value.lastSwingType)
        val shrinkFactor = 1.0f - shrinkPercentage
        
        return (baseWindow * shrinkFactor).roundToLong()
    }
    
    private fun getWindowShrinkPercentage(swingType: SwingType?): Float {
        if (swingType == null) return 0f
        return when (swingType) {
            SwingType.SOFT_FLAT -> 0.0f
            SwingType.MEDIUM_FLAT -> 0.20f
            SwingType.HARD_FLAT -> 0.35f
            SwingType.SOFT_LOB -> 0.0f
            SwingType.MEDIUM_LOB -> 0.0f
            SwingType.HARD_LOB -> 0.0f
            SwingType.SOFT_SPIKE -> 0.0f
            SwingType.MEDIUM_SPIKE -> 0.40f
            SwingType.HARD_SPIKE -> 0.60f
        }
    }

    /**
     * Checks if a swing at the given timestamp hits the ball.
     */
    fun checkHitTiming(swingTimestamp: Long): HitResult {
        // Note: swingTimestamp is expected to be in the local time domain.
        val arrival = _gameState.value.ballArrivalTimestamp
        val delta = swingTimestamp - arrival
        val halfWindow = getHitWindow()
        
        // Shift the window so it starts at the bounce (BOUNCE_OFFSET_MS before arrival)
        // Original Window: [-halfWindow, +halfWindow]
        // New Window Start: -BOUNCE_OFFSET_MS
        // New Window End: -BOUNCE_OFFSET_MS + (2 * halfWindow)
        
        val startWindow = -BOUNCE_OFFSET_MS
        val endWindow = startWindow + (2 * halfWindow)
        
        return when {
            delta < startWindow -> HitResult.MISS_EARLY
            delta <= endWindow -> {
                 // Check for "Too Early" relative to launch (prevent hitting instantly after opponent)
                 val flightTime = _gameState.value.flightTime
                 val timeSinceLaunch = flightTime + delta 
                 if (timeSinceLaunch < 200) { // First 200ms are unhittable
                     HitResult.MISS_EARLY
                 } else {
                     HitResult.HIT
                 }
            }
            else -> HitResult.MISS_LATE
        }
    }

    /**
     * Processes a local swing.
     * Returns the result (HIT/MISS) so the network layer can notify the opponent.
     * Returns null if the swing was invalid (e.g. not my turn).
     */
    fun processSwing(timestamp: Long, force: Float, x: Float, y: Float, z: Float, gx: Float, gy: Float, gz: Float, gravX: Float, gravY: Float, gravZ: Float): HitResult? {
        // Anti-Spam Cooldown
        if (timestamp - lastSwingTimestamp < SWING_COOLDOWN_MS) {
            return null
        }
        lastSwingTimestamp = timestamp

        val swingType = classifySwing(force, gravZ)
        
        // Store raw values for debug
        _gameState.update {
            it.copy(
                lastSwingType = swingType,
                lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ)
            )
        }
        
        val currentState = _gameState.value
        
        // Can only swing during RALLY or if serving (WAITING_FOR_SERVE)
        if (currentState.gamePhase != GamePhase.WAITING_FOR_SERVE && currentState.gamePhase != GamePhase.RALLY) {
            return null
        }
        
        // If it's not my turn, I shouldn't be swinging
        if (!currentState.isMyTurn) {
            return null
        }
        
        // If Serving
        if (currentState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
             if (timestamp - lastPointEndedTimestamp < SERVE_COOLDOWN_MS) {
                 return null 
             }

             // Risk logic applied to serves too
             val riskResult = checkRisk(swingType)
             if (riskResult != HitResult.HIT) {
                 // Fault on serve!
                 // Calculate delay for natural feel
                 val flightTime = currentState.flightTime
                 val delay = if (riskResult == HitResult.MISS_NET) {
                     (flightTime * 0.5).toLong() // Hits net halfway
                 } else {
                     (flightTime * 1.2).toLong() // Goes out (full flight + bit more)
                 }

                 _gameState.update {
                     it.copy(
                         pendingMiss = PendingMiss(riskResult, System.currentTimeMillis(), delay),
                         // We still update lastSwingType so UI can show the swing
                         lastSwingType = swingType,
                         lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ)
                     )
                 }
                 return HitResult.PENDING
             }

             val nextArrival = timestamp + currentState.flightTime
             _gameState.update {
                 it.copy(
                     gamePhase = GamePhase.RALLY,
                     isMyTurn = false,
                     ballArrivalTimestamp = nextArrival,
                     lastEvent = "You Served!",
                     eventLog = (it.eventLog + GameEvent.YouServed(swingType)).takeLast(5),
                     ballState = BallState.IN_AIR
                 )
             }
             return HitResult.HIT
        }
        
        // If Rallying
        val timingResult = checkHitTiming(timestamp)
        
        if (timingResult == HitResult.HIT) {
            // Timing was good. Now check Risk.
            val riskResult = checkRisk(swingType)
            
            if (riskResult == HitResult.HIT) {
                // Success!
                val nextArrival = timestamp + currentState.flightTime 
                _gameState.update {
                    it.copy(
                        gamePhase = GamePhase.RALLY,
                        isMyTurn = false,
                        ballArrivalTimestamp = nextArrival,
                        lastEvent = "You Hit!",
                        eventLog = (it.eventLog + GameEvent.YouHit(swingType)).takeLast(5),
                        ballState = BallState.IN_AIR
                    )
                }
                return HitResult.HIT
            } else {
                // Failed Risk Check (Net or Out)
                // Calculate delay for natural feel
                val flightTime = currentState.flightTime
                val delay = if (riskResult == HitResult.MISS_NET) {
                    (flightTime * 0.5).toLong() // Hits net halfway
                } else {
                    (flightTime * 1.2).toLong() // Goes out (full flight + bit more)
                }

                _gameState.update {
                    it.copy(
                        pendingMiss = PendingMiss(riskResult, System.currentTimeMillis(), delay),
                        // We still update lastSwingType so UI can show the swing
                        lastSwingType = swingType,
                        lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ)
                    )
                }
                return HitResult.PENDING
            }
        } else {
            // Timing Miss
            val event = if (timingResult == HitResult.MISS_EARLY) GameEvent.WhiffEarly else GameEvent.MissLate
            _gameState.update {
                it.copy(eventLog = (it.eventLog + event).takeLast(5))
            }
            handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
            return timingResult
        }
    }
    
    private fun checkRisk(swingType: SwingType): HitResult {
        val (netRisk, outRisk) = getRiskPercentages(swingType)
        val roll = Random.nextInt(100) // 0 to 99
        
        return when {
            roll < netRisk -> HitResult.MISS_NET
            roll < (netRisk + outRisk) -> HitResult.MISS_OUT
            else -> HitResult.HIT
        }
    }
    
    private fun getRiskPercentages(swingType: SwingType): Pair<Int, Int> {
        // Returns (NetRisk, OutRisk)
        return when (swingType) {
            SwingType.SOFT_FLAT -> 0 to 0
            SwingType.MEDIUM_FLAT -> 0 to 5
            SwingType.HARD_FLAT -> 5 to 15
            SwingType.SOFT_LOB -> 0 to 0
            SwingType.MEDIUM_LOB -> 0 to 5
            SwingType.HARD_LOB -> 0 to 20
            SwingType.SOFT_SPIKE -> 50 to 0
            SwingType.MEDIUM_SPIKE -> 15 to 5
            SwingType.HARD_SPIKE -> 30 to 10
        }
    }
    
    /**
     * Called when opponent sends an ActionSwing (they hit the ball).
     */
    fun onOpponentHit(timestamp: Long, swingTypeOrdinal: Int) {
         val swingType = try {
             SwingType.entries[swingTypeOrdinal]
         } catch (e: Exception) {
             SwingType.MEDIUM_FLAT
         }
         
         // Adjust flight time based on SwingType
         val baseFlightTime = _gameState.value.flightTime
         val modifier = when (swingType) {
             SwingType.SOFT_FLAT -> 1.2f
             SwingType.MEDIUM_FLAT -> 1.0f
             SwingType.HARD_FLAT -> 0.8f
             SwingType.SOFT_LOB -> 1.4f
             SwingType.MEDIUM_LOB -> 1.5f
             SwingType.HARD_LOB -> 1.6f
             SwingType.SOFT_SPIKE -> 0.8f
             SwingType.MEDIUM_SPIKE -> 0.6f
             SwingType.HARD_SPIKE -> 0.4f
         }
         
         val actualFlightTime = (baseFlightTime * modifier).toLong()
         val nextArrival = timestamp + actualFlightTime
         
         _gameState.update {
             it.copy(
                 gamePhase = GamePhase.RALLY,
                 isMyTurn = true, // Now it's my turn
                 ballArrivalTimestamp = nextArrival,
                 lastEvent = "Opponent Hit: $swingType",
                 eventLog = (it.eventLog + GameEvent.OpponentHit(swingType)).takeLast(5),
                 ballState = BallState.IN_AIR,
                 lastSwingType = swingType // Store this so we can calculate window shrink later
             )
         }
    }
    
    fun onBounce() {
        // Only log bounce if we are in a valid phase (Rally or Waiting for Serve)
        // This prevents "Ball Bounced" from appearing after the point has ended.
        val phase = _gameState.value.gamePhase
        if (phase != GamePhase.RALLY && phase != GamePhase.WAITING_FOR_SERVE) {
            return
        }
        
        _gameState.update {
            it.copy(
                ballState = if (it.isMyTurn) BallState.BOUNCED_MY_SIDE else BallState.BOUNCED_OPP_SIDE,
                eventLog = (it.eventLog + GameEvent.BallBounced).takeLast(5)
            )
        }
    }

    /**
     * Called when opponent sends a Result(MISS) (they missed).
     */
    fun onOpponentMiss(reason: HitResult = HitResult.MISS_LATE) {
        val event = when (reason) {
            HitResult.MISS_NET -> GameEvent.OpponentNet
            HitResult.MISS_OUT -> GameEvent.OpponentOut
            HitResult.MISS_EARLY, HitResult.MISS_LATE -> GameEvent.OpponentWhiff
            HitResult.MISS_TIMEOUT -> GameEvent.OpponentMissNoSwing
            else -> GameEvent.OpponentMiss
        }
        
        _gameState.update {
            it.copy(eventLog = (it.eventLog + event).takeLast(5))
        }
        handleMiss(if (isHost) Player.PLAYER_2 else Player.PLAYER_1)
    }

    /**
     * Checks if the current turn has expired without a swing.
     * Returns true if a miss was processed.
     */
    fun checkAutoMiss(): Boolean {
        val state = _gameState.value
        // If there is a pending miss, we have already swung and are just waiting for the result.
        if (state.pendingMiss != null) {
            return false
        }

        if (state.gamePhase != GamePhase.RALLY || !state.isMyTurn) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val window = getHitWindow()
        // If we are past the arrival time + window, it's a miss
        if (currentTime > state.ballArrivalTimestamp + window) {
            _gameState.update {
                it.copy(eventLog = (it.eventLog + GameEvent.MissNoSwing).takeLast(5))
            }
            handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
            return true
        }
        return false
    }

    fun handleMiss(whoMissed: Player) {
        lastPointEndedTimestamp = System.currentTimeMillis()
        // Update Score
        _gameState.update { state ->
            val p1Score = if (whoMissed == Player.PLAYER_2) state.player1Score + 1 else state.player1Score
            val p2Score = if (whoMissed == Player.PLAYER_1) state.player2Score + 1 else state.player2Score
            
            // Check Win (Win by 2)
            val scoreDiff = abs(p1Score - p2Score)
            if ((p1Score >= SCORE_LIMIT || p2Score >= SCORE_LIMIT) && scoreDiff >= 2) {
                 state.copy(
                     player1Score = p1Score,
                     player2Score = p2Score,
                     gamePhase = GamePhase.GAME_OVER,
                     lastEvent = "Game Over!"
                 )
            } else {
                // Rotate Serve
                val totalPoints = p1Score + p2Score
                val isDeuce = p1Score >= 10 && p2Score >= 10
                
                val serveChangeInterval = if (isDeuce) 1 else SERVE_ROTATION
                
                val rotationIndex = totalPoints / serveChangeInterval
                val nextServer = if (rotationIndex % 2 == 0) Player.PLAYER_1 else Player.PLAYER_2
                
                val winner = if (whoMissed == Player.PLAYER_1) Player.PLAYER_2 else Player.PLAYER_1
                val iWon = (isHost && winner == Player.PLAYER_1) || (!isHost && winner == Player.PLAYER_2)
                val winnerName = if (iWon) "You" else "Opponent"

                state.copy(
                    player1Score = p1Score,
                    player2Score = p2Score,
                    servingPlayer = nextServer,
                    isMyTurn = (isHost && nextServer == Player.PLAYER_1) || (!isHost && nextServer == Player.PLAYER_2),
                    eventLog = (state.eventLog + GameEvent.PointScored(iWon)).takeLast(5),
                    gamePhase = GamePhase.POINT_SCORED // Enter cooldown
                )
            }
        }
    }

    fun startNextServe() {
        _gameState.update { state ->
            if (state.gamePhase == GamePhase.POINT_SCORED) {
                state.copy(
                    gamePhase = GamePhase.WAITING_FOR_SERVE,
                    lastEvent = "Your Serve" // Or "Opponent Serving" - UI handles this text usually but good to reset
                )
            } else {
                state
            }
        }
    }

    fun onPeerLeft() {
        _gameState.update {
            it.copy(
                gamePhase = GamePhase.GAME_OVER,
                lastEvent = "Peer Left"
            )
        }
    }

    fun onPause() {
        _gameState.update {
            it.copy(
                gamePhase = GamePhase.PAUSED,
                lastEvent = "Game Paused"
            )
        }
    }

    fun onResume() {
        _gameState.update {
            // Replay the point: Reset to WAITING_FOR_SERVE
            // We need to determine whose turn it is based on servingPlayer
            val isMyTurn = (isHost && it.servingPlayer == Player.PLAYER_1) || (!isHost && it.servingPlayer == Player.PLAYER_2)
            
            it.copy(
                gamePhase = GamePhase.WAITING_FOR_SERVE,
                isMyTurn = isMyTurn,
                lastEvent = "Resumed - Replaying Point"
            )
        }
    }

    fun updateSettings(flightTime: Long, difficulty: Int, isDebugMode: Boolean, useDebugTones: Boolean, minSwingThreshold: Float) {
        _gameState.update {
            it.copy(
                flightTime = flightTime,
                difficulty = difficulty,
                isDebugMode = isDebugMode,
                useDebugTones = useDebugTones,
                minSwingThreshold = minSwingThreshold
            )
        }
    }

    fun rematch(isInitiator: Boolean) {
        _gameState.update { state ->
            // Determine who should serve based on who initiated
            var newServingPlayer = if (isInitiator) (if (isHost) Player.PLAYER_1 else Player.PLAYER_2) else (if (isHost) Player.PLAYER_2 else Player.PLAYER_1)
            
            // Race Condition Handling:
            // If we are already in WAITING_FOR_SERVE, it means we also initiated a rematch locally.
            // If both players initiate, we need a tie-breaker.
            // Rule: Host (Player 1) always wins the serve in a tie.
            if (state.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                newServingPlayer = if (isHost) {
                    // I am Host and I initiated. I stay server.
                    Player.PLAYER_1
                } else {
                    // I am Client and I initiated, but Host also initiated (since I received a message).
                    // I yield to Host.
                    Player.PLAYER_1
                }
            }
            
            state.copy(
                gamePhase = GamePhase.WAITING_FOR_SERVE,
                player1Score = 0,
                player2Score = 0,
                servingPlayer = newServingPlayer,
                isMyTurn = (isHost && newServingPlayer == Player.PLAYER_1) || (!isHost && newServingPlayer == Player.PLAYER_2),
                lastEvent = "Rematch Started",
                eventLog = emptyList(),
                lastSwingType = null,
                lastSwingData = null
            )
        }
    }

    fun resolvePendingMiss(): HitResult? {
        val pending = _gameState.value.pendingMiss ?: return null
        
        // Clear pending state
        _gameState.update { it.copy(pendingMiss = null) }
        
        // Log event
        val event = if (pending.type == HitResult.MISS_NET) {
             if (_gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE) GameEvent.FaultNet else GameEvent.HitNet(_gameState.value.lastSwingType ?: SwingType.MEDIUM_FLAT)
        } else {
             if (_gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE) GameEvent.FaultOut else GameEvent.HitOut(_gameState.value.lastSwingType ?: SwingType.MEDIUM_FLAT)
        }
        
        _gameState.update {
            it.copy(eventLog = (it.eventLog + event).takeLast(5))
        }
        
        handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
        return pending.type
    }

    private fun classifySwing(force: Float, gravZ: Float): SwingType {
        // Classification based on Gravity Z (Tilt)
        // Gravity Z > 3.0 -> Screen Tilted Up -> LOB
        // Gravity Z < -3.0 -> Screen Tilted Down -> SPIKE
        // Else -> Screen Vertical -> FLAT
        
        val type = when {
            gravZ > 3.0f -> "LOB"
            gravZ < -3.0f -> "SPIKE"
            else -> "FLAT"
        }
        
        val minThreshold = _gameState.value.minSwingThreshold
        val intensity = when {
            force > (minThreshold + 8.0f) -> "HARD"   // e.g. 12+8 = 20
            force > (minThreshold + 5.0f) -> "MEDIUM" // e.g. 12+5 = 17
            else -> "SOFT"
        }
        
        return try {
            SwingType.valueOf("${intensity}_${type}")
        } catch (e: IllegalArgumentException) {
            SwingType.MEDIUM_FLAT
        }
    }
}
