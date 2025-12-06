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

    fun startGame(mode: GameMode? = null) {
        // Use provided mode, or fall back to current mode (which was likely synced via Settings)
        // Only default to CLASSIC if absolutely nothing is set (though enum handles that)
        val targetMode = mode ?: _gameState.value.gameMode
        
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
                lastSwingData = null,
                currentPointShots = emptyList(),
                currentRallyLength = 0,
                longestRally = 0,
                // Rally Mode Init
                gameMode = targetMode,
                rallyScore = 0,
                rallyLives = RALLY_STARTING_LIVES,
                rallyGrid = List(9) { false },
                rallyLinesCompleted = List(8) { false },
                rallyScorePoints = 0,
                rallyBonusMultiplier = 1,
                // Opponent State
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false }
            )
        }
    }

    companion object {
        const val DEFAULT_FLIGHT_TIME = 700L
        const val DEFAULT_SWING_THRESHOLD = 14.0f
        const val SERVE_ROTATION = 2
        const val SCORE_LIMIT = 11
        const val BOUNCE_OFFSET_MS = 200L // Time before arrival that bounce sound plays
        
        const val DEFAULT_DIFFICULTY = 600
        const val MIN_DIFFICULTY = 200
        const val MAX_DIFFICULTY = 1000

        // Rally Mode Constants
        const val RALLY_HIT_POINTS = 10
        const val RALLY_LINE_BONUS = 500
        const val RALLY_BLACKOUT_BONUS_LIFE = 1
        const val RALLY_STARTING_LIVES = 3
        
        const val MIN_REACTION_TIME_MS = 200L // Minimum time after launch before a hit is valid
        
        const val MIN_FLIGHT_TIME = 400L
        const val MAX_FLIGHT_TIME = 1500L
        const val FLIGHT_TIME_HARD_THRESHOLD = 600L
        const val FLIGHT_TIME_MEDIUM_THRESHOLD = 900L
    }
    
    /**
     * Returns the current hit window size in ms.
     * Starts with Base Difficulty and shrinks based on Rally Logic.
     */
    fun getHitWindow(): Long {
        return _gameState.value.currentHitWindow
    }
    
    fun syncState(msg: com.air.pong.core.network.GameMessage.GameStateSync) {
        _gameState.update {
            it.copy(
                player1Score = msg.player1Score,
                player2Score = msg.player2Score,
                gamePhase = msg.currentPhase,
                servingPlayer = msg.servingPlayer,
                // Rally Sync - SHARED STATE ONLY
                gameMode = msg.gameMode,
                rallyScore = msg.rallyScore,
                rallyLives = msg.rallyLives,
                // Only sync OPPONENT's grid (sender's "my grid" = my "opponent grid")
                // Keep my own grid locally authoritative - don't overwrite!
                opponentRallyGrid = decodeGridBitmask(msg.rallyGridBitmask),
                opponentRallyLinesCompleted = decodeLinesBitmask(msg.rallyLinesBitmask),
                longestRally = msg.longestRally
            )
        }
    }
    
    private fun decodeGridBitmask(mask: Short): List<Boolean> {
        return List(9) { i ->
            ((mask.toInt() shr i) and 1) == 1
        }
    }
    
    private fun decodeLinesBitmask(mask: Byte): List<Boolean> {
        return List(8) { i ->
            ((mask.toInt() shr i) and 1) == 1
        }
    }

    private fun checkRisk(swingType: SwingType): HitResult {
        var (netRisk, outRisk) = swingType.getRiskPercentages()
        
        // Rally Mode: Halve the risk percentages (partners are placing shots more nicely)
        if (_gameState.value.gameMode == GameMode.RALLY) {
            netRisk /= 2
            outRisk /= 2
        }
        
        val roll = Random.nextInt(100) // 0 to 99
        
        return when {
            roll < netRisk -> HitResult.MISS_NET
            roll < (netRisk + outRisk) -> HitResult.MISS_OUT
            else -> HitResult.HIT
        }
    }

    private fun getCurrentFlightTime(): Long {
        val state = _gameState.value
        val type = state.lastSwingType ?: return state.flightTime
        
        // If Rally Length is 1, the LAST shot was a serve.
        val isServe = state.currentRallyLength == 1
        
        val modifier = if (isServe) type.getServeFlightTimeModifier() else type.getFlightTimeModifier()
        return (state.flightTime * modifier).toLong()
    }

    /**
     * Returns the start and end offsets (in ms) relative to ballArrivalTimestamp for the valid hit window.
     */
    private fun getHitWindowBounds(): Pair<Long, Long> {
        val baseHitWindow = getHitWindow()
        
        // Scale the hit window for slow shots (Lobs/Smash Serves)
        // If the ball is in unit-time > 1.0 (slower than base), we expand the window proportionally.
        // This helps with the "Miss Late" feeling on floaty balls.
        val currentFlightTime = getCurrentFlightTime()
        val baseFlightTime = _gameState.value.flightTime
        val scale = if (baseFlightTime > 0) (currentFlightTime.toFloat() / baseFlightTime.toFloat()) else 1.0f
        
        // Only scale UP. Don't shrink fast balls (that's handled by HitWindowShrink separately).
        val effectiveScale = scale.coerceAtLeast(1.0f)
        val totalWindow = (baseHitWindow * effectiveScale).toLong()
        
        // Ideal window starts at BOUNCE_OFFSET_MS before arrival
        val idealStartWindow = -BOUNCE_OFFSET_MS
        
        // Safety Check: Cannot hit in first 200ms of flight
        val earliestValidStart = MIN_REACTION_TIME_MS - currentFlightTime
        
        // Actual start is the later of the two
        val actualStartWindow = kotlin.math.max(idealStartWindow, earliestValidStart)
        val actualEndWindow = actualStartWindow + totalWindow
        
        return actualStartWindow to actualEndWindow
    }

    /**
     * Checks if a swing at the given timestamp hits the ball.
     */
    fun checkHitTiming(swingTimestamp: Long): HitResult {
        // Note: swingTimestamp is expected to be in the local time domain.
        val arrival = _gameState.value.ballArrivalTimestamp
        val delta = swingTimestamp - arrival
        
        // Use the shared bounds logic which includes scaling for slow shots
        val (startWindow, endWindow) = getHitWindowBounds()
        
        return when {
            delta < startWindow -> HitResult.MISS_EARLY
            delta <= endWindow -> {
                 // Check for "Too Early" relative to launch (prevent hitting instantly after opponent)
                 val flightTime = getCurrentFlightTime()
                 val timeSinceLaunch = flightTime + delta 
                 if (timeSinceLaunch < MIN_REACTION_TIME_MS) { // First 200ms are unhittable
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

        // Prevent swinging if we are already waiting for a pending miss resolution
        if (_gameState.value.pendingMiss != null) {
            return null
        }

        val swingType = classifySwing(force, gravZ, _gameState.value.minSwingThreshold)
        
        // CHECK TIMING BEFORE UPDATING STATS
        // We must check timing against the INCOMING shot (lastSwingType = Opponent's).
        // If we update state first, lastSwingType becomes MY swing, and we check timing against my own swing characteristics!
        val timingResult = checkHitTiming(timestamp)

        // Store raw values for debug
        _gameState.update {
            val newState = it.copy(
                lastSwingType = swingType,
                lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ)
            )
            // Update hit window for the NEXT person (which is based on MY swing now)
            newState.copy(currentHitWindow = newState.calculateHitWindow())
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
             
             // Calculate Serve Flight Time (includes Lob/Smash modifiers)
             val baseFlightTime = currentState.flightTime
             val serveModifier = swingType.getServeFlightTimeModifier()
             val serveFlightTime = (baseFlightTime * serveModifier).toLong()

             if (riskResult != HitResult.HIT) {
                 // Fault on serve!
                 // Calculate delay for natural feel
                 val delay = if (riskResult == HitResult.MISS_NET) {
                     (serveFlightTime * 0.5).toLong() // Hits net halfway
                 } else {
                     (serveFlightTime * 1.2).toLong() // Goes out (full flight + bit more)
                 }

                 _gameState.update {
                     it.copy(
                         pendingMiss = PendingMiss(riskResult, System.currentTimeMillis(), delay),
                         // We still update lastSwingType so UI can show the swing
                         lastSwingType = swingType,
                         lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ),
                         currentPointShots = it.currentPointShots + swingType // Add to stats even if miss
                     )
                 }
                 return HitResult.PENDING
             }



             val nextArrival = timestamp + serveFlightTime
             _gameState.update {
                 val newState = it.copy(
                     ballArrivalTimestamp = nextArrival,
                     ballState = BallState.IN_AIR,
                     gamePhase = GamePhase.RALLY,
                     isMyTurn = false,
                     lastEvent = "You Served!",
                     eventLog = (it.eventLog + GameEvent.YouServed(swingType)).takeLast(50),
                     currentPointShots = it.currentPointShots + swingType,
                     currentRallyLength = 1
                 )
                 newState.copy(currentHitWindow = newState.calculateHitWindow())
             }
             return HitResult.HIT
        }

        // RALLY Logic
        // timingResult is already calculated above
        
        if (timingResult == HitResult.HIT) {
            // Timing was good. Now check Risk.
            val riskResult = checkRisk(swingType)
            
            if (riskResult == HitResult.HIT) {
                // Success!
                val nextArrival = timestamp + currentState.flightTime 
                _gameState.update {
                    if (it.gamePhase != GamePhase.RALLY && it.gamePhase != GamePhase.WAITING_FOR_SERVE) {
                        return@update it
                    }
                    
                    val newState = it.copy(
                        gamePhase = GamePhase.RALLY,
                        isMyTurn = false,
                        ballArrivalTimestamp = nextArrival,
                        lastEvent = "You Hit!",
                        eventLog = (it.eventLog + GameEvent.YouHit(swingType)).takeLast(50),
                        currentPointShots = it.currentPointShots + swingType,
                        currentRallyLength = it.currentRallyLength + 1,
                        ballState = BallState.IN_AIR,
                        longestRally = kotlin.math.max(it.longestRally, it.currentRallyLength + 1) // Track longest rally (local hit)
                    )
                    // Recalculate window for opponent based on new rally length
                    val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow())
                    
                    // RALLY MODE LOGIC
                    if (currentState.gameMode == GameMode.RALLY) {
                        updateRallyState(stateWithWindow, swingType, isOpponent = false)
                    } else {
                        stateWithWindow
                    }
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
                        lastSwingData = SwingData(force, x, y, z, gx, gy, gz, gravX, gravY, gravZ),
                        currentPointShots = it.currentPointShots + swingType // Add to stats even if miss
                    )
                }
                return HitResult.PENDING
            }
        } else {
            // Timing Miss
            val event = if (timingResult == HitResult.MISS_EARLY) GameEvent.WhiffEarly else GameEvent.MissLate
            _gameState.update {
                it.copy(eventLog = (it.eventLog + event).takeLast(50))
            }
            handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
            return timingResult
        }
    }

    fun onBounce() {
        // This prevents "Ball Bounced" from appearing after the point has ended.
        val phase = _gameState.value.gamePhase
        if (phase != GamePhase.RALLY && phase != GamePhase.WAITING_FOR_SERVE) {
            return
        }
        
        // Prevent duplicate bounce events (e.g. if called multiple times rapidly)
        if (_gameState.value.eventLog.lastOrNull() == GameEvent.BallBounced) {
            return
        }
        
        _gameState.update {
            it.copy(
                ballState = if (it.isMyTurn) BallState.BOUNCED_MY_SIDE else BallState.BOUNCED_OPP_SIDE,
                eventLog = (it.eventLog + GameEvent.BallBounced).takeLast(50)
            )
        }
    }

    fun onOpponentHit(timestamp: Long, swingTypeOrdinal: Int) {
         val swingType = try {
             SwingType.values().getOrElse(swingTypeOrdinal) { SwingType.MEDIUM_FLAT }
         } catch (e: Exception) {
             SwingType.MEDIUM_FLAT
         }
         
         // Adjust flight time based on SwingType
         // CRITICAL FIX: Check if this was a serve. 
         // If we are in WAITING_FOR_SERVE phase, the incoming hit IS a serve.
         // We must use serve modifiers (especially for Smash which differs wildly: 0.7 vs 2.1).
         val isServe = _gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE
         val baseFlightTime = _gameState.value.flightTime
         val modifier = if (isServe) swingType.getServeFlightTimeModifier() else swingType.getFlightTimeModifier()
         
         val actualFlightTime = (baseFlightTime * modifier).toLong()
         val nextArrival = timestamp + actualFlightTime
         
         _gameState.update {
             val newState = it.copy(
                 gamePhase = GamePhase.RALLY,
                 isMyTurn = true, // Now it's my turn to hit
                 ballArrivalTimestamp = nextArrival,
                 lastEvent = "Opponent Hit!",
                 eventLog = (it.eventLog + GameEvent.OpponentHit(swingType)).takeLast(50),
                 ballState = BallState.IN_AIR,
                 lastSwingType = swingType, // Store this so we can calculate window shrink later
                 currentRallyLength = it.currentRallyLength + 1
             )
             val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow())
             
             // RALLY MODE: Update opponent's grid and calculate their bonuses
             if (it.gameMode == GameMode.RALLY) {
                 updateRallyState(stateWithWindow, swingType, isOpponent = true)
             } else {
                 stateWithWindow
             }
         }
    }

    /**
     * Called when I miss locally (Early, Late, etc).
     */
    fun onLocalMiss(reason: HitResult) {
        val event = when (reason) {
            HitResult.MISS_EARLY -> GameEvent.WhiffEarly
            HitResult.MISS_LATE -> GameEvent.MissLate
            else -> GameEvent.MissLate // Default
        }
        
        _gameState.update {
            it.copy(eventLog = (it.eventLog + event).takeLast(50))
        }
        handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
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
            it.copy(eventLog = (it.eventLog + event).takeLast(50))
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
        val (_, endWindow) = getHitWindowBounds()
        
        // If we are past the arrival time + window, it's a miss
        if (currentTime > state.ballArrivalTimestamp + endWindow) {
            _gameState.update {
                it.copy(eventLog = (it.eventLog + GameEvent.MissNoSwing).takeLast(50))
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
            // Concurrency Guard: If point already scored, ignore
            if (state.gamePhase == GamePhase.POINT_SCORED || state.gamePhase == GamePhase.GAME_OVER) {
                return@update state
            }
            
            val p1Score = if (whoMissed == Player.PLAYER_2) state.player1Score + 1 else state.player1Score
            val p2Score = if (whoMissed == Player.PLAYER_1) state.player2Score + 1 else state.player2Score
            
            if (state.gameMode == GameMode.RALLY) {
                 val newLives = state.rallyLives - 1
                 if (newLives <= 0) {
                     state.copy(
                         rallyLives = 0,
                         gamePhase = GamePhase.GAME_OVER,
                         lastEvent = "Game Over! Score: ${state.rallyScore}"
                     )
                 } else {
                     // Rally Mode: Lose life, reset rally, switch server?
                     // Let's rotate server same as Classic for fairness
                     val totalPoints = p1Score + p2Score // Track total misses nicely? Or just use rotation
                     // Simply rotate serve every miss for Rally mode to keep it moving
                     val nextServer = if (state.servingPlayer == Player.PLAYER_1) Player.PLAYER_2 else Player.PLAYER_1
                     
                     state.copy(
                         rallyLives = newLives,
                         servingPlayer = nextServer,
                         isMyTurn = (isHost && nextServer == Player.PLAYER_1) || (!isHost && nextServer == Player.PLAYER_2),
                         eventLog = (state.eventLog + GameEvent.PointScored(false)).takeLast(50), // No "winner" in rally
                         gamePhase = GamePhase.POINT_SCORED,
                         lastEvent = "Life Lost! $newLives Left",

                         rallyGrid = state.rallyGrid, // Keep grid! (Bingo style)
                         rallyLinesCompleted = state.rallyLinesCompleted,
                         opponentRallyGrid = state.opponentRallyGrid,
                         opponentRallyLinesCompleted = state.opponentRallyLinesCompleted,
                         longestRally = kotlin.math.max(state.longestRally, state.currentRallyLength) // Ensure stats updated on miss
                     )
                 }
            } else {
                // CLASSIC MODE LOGIC
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
                        eventLog = (state.eventLog + GameEvent.PointScored(iWon)).takeLast(50),
                        gamePhase = GamePhase.POINT_SCORED, // Enter cooldown
                        longestRally = kotlin.math.max(state.longestRally, state.currentRallyLength)
                    )
                }
            }
        }
    }

    fun startNextServe() {
        _gameState.update { state ->
            if (state.gamePhase == GamePhase.POINT_SCORED) {
                state.copy(
                    gamePhase = GamePhase.WAITING_FOR_SERVE,
                    lastEvent = "Your Serve", // Or "Opponent Serving" - UI handles this text usually but good to reset
                    currentPointShots = emptyList(),
                    currentRallyLength = 0
                ).apply { 
                    // Refresh hit window (reset to base)
                    // Note: copy() isn't mutable, so we need to use the result of calculateHitWindow
                }.let { 
                    it.copy(currentHitWindow = it.calculateHitWindow())
                }
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

    fun updateSettings(flightTime: Long, difficulty: Int, isDebugMode: Boolean, useDebugTones: Boolean, minSwingThreshold: Float, isRallyShrinkEnabled: Boolean, gameMode: GameMode = GameMode.RALLY) {
        _gameState.update {
            val newState = it.copy(
                flightTime = flightTime,
                difficulty = difficulty,
                isDebugMode = isDebugMode,
                useDebugTones = useDebugTones,
                minSwingThreshold = minSwingThreshold,
                isRallyShrinkEnabled = isRallyShrinkEnabled,
                gameMode = gameMode
            )
            // Recalculate because difficulty or enabled flag changed
            newState.copy(currentHitWindow = newState.calculateHitWindow())
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
            
            val newState = state.copy(
                gamePhase = GamePhase.WAITING_FOR_SERVE,
                player1Score = 0,
                player2Score = 0,
                servingPlayer = newServingPlayer,
                isMyTurn = (isHost && newServingPlayer == Player.PLAYER_1) || (!isHost && newServingPlayer == Player.PLAYER_2),
                lastEvent = "Rematch Started",
                eventLog = emptyList(),
                lastSwingType = null,
                lastSwingData = null,
                currentPointShots = emptyList(),
                currentRallyLength = 0,
                longestRally = 0,
                // Reset Rally Mode Stats too!
                rallyScore = 0,
                rallyLives = RALLY_STARTING_LIVES,
                rallyGrid = List(9) { false },
                rallyLinesCompleted = List(8) { false },
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false },
                rallyScorePoints = 0
            )
            // Reset hit window
            newState.copy(currentHitWindow = newState.calculateHitWindow())
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
            it.copy(eventLog = (it.eventLog + event).takeLast(50))
        }
        handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
        return pending.type
    }

    fun clearDebugData() {
        _gameState.update {
            it.copy(
                lastSwingType = null,
                lastSwingData = null
            )
        }
    }

    fun resetGame() {
        _gameState.update {
            val newState = it.copy(
                gamePhase = GamePhase.IDLE,
                player1Score = 0,
                player2Score = 0,
                lastEvent = "Game Reset",
                eventLog = emptyList(),
                lastSwingType = null,
                lastSwingData = null,
                currentPointShots = emptyList(),
                currentRallyLength = 0,
                longestRally = 0,
                // Rally Reset
                rallyScore = 0,
                rallyLives = RALLY_STARTING_LIVES,
                rallyGrid = List(9) { false },
                rallyLinesCompleted = List(8) { false },
                rallyScorePoints = 0,
                rallyBonusMultiplier = 1,
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false }
            )
            newState.copy(currentHitWindow = newState.calculateHitWindow())
        }
    }

    fun forceGameOver(p1Score: Int, p2Score: Int) {
        _gameState.update {
            it.copy(
                gamePhase = GamePhase.GAME_OVER,
                player1Score = p1Score,
                player2Score = p2Score,
                lastEvent = "Debug Game Over"
            )
        }
    }
    
    private fun updateRallyState(state: GameState, swingType: SwingType, isOpponent: Boolean): GameState {
        // 1. Mark Grid (My Grid or Opponent Grid)
        val gridIndex = swingType.getGridIndex()
        val currentGrid = if (isOpponent) state.opponentRallyGrid else state.rallyGrid
        val newGrid = currentGrid.toMutableList()
        val wasMarked = newGrid[gridIndex]
        newGrid[gridIndex] = true
        
        // 2. Check Lines
        // Lines: Rows (0-2, 3-5, 6-8), Cols (036, 147, 258), Diagonals (048, 246)
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
            listOf(0, 4, 8), listOf(2, 4, 6)                   // Diagonals
        )
        
        var pointsToAdd = RALLY_HIT_POINTS
        // var bonusMultiplier = 1 // Not used yet
        val currentLinesCompleted = if (isOpponent) state.opponentRallyLinesCompleted else state.rallyLinesCompleted
        var newLinesCompleted = currentLinesCompleted.toMutableList()
        var linesCompletedThisTurn = 0
        
        lines.forEachIndexed { index, indices ->
            val isLineComplete = indices.all { newGrid[it] }
            if (isLineComplete && !currentLinesCompleted[index]) {
                // New Line Completed!
                newLinesCompleted[index] = true
                pointsToAdd += RALLY_LINE_BONUS
                linesCompletedThisTurn++
            }
        }
        
        // 3. Blackout Check (All Lines)
        var newLives = state.rallyLives

        val allLinesCompleted = newLinesCompleted.all { it }
        val allLinesPreviouslyCompleted = currentLinesCompleted.all { it }
        
        if (allLinesCompleted && !allLinesPreviouslyCompleted) {
            // New Blackout!
            if (newLives < 3) {
                 newLives++ // Extra Life!
                 // Reset Grid logic if we wanted to allow multiple blackouts
                 newGrid.fill(false) // Clear grid
                 newLinesCompleted.fill(false) // Clear lines
                 pointsToAdd += 1000 // Huge bonus
            }
        }

        return if (isOpponent) {
             state.copy(
                 opponentRallyGrid = newGrid,
                 opponentRallyLinesCompleted = newLinesCompleted,
                 rallyScore = state.rallyScore + pointsToAdd,
                 rallyLives = newLives,
                 rallyScorePoints = pointsToAdd // Show points added by opponent too? Maybe distinct UI event later.
             )
        } else {
             state.copy(
                 rallyGrid = newGrid,
                 rallyLinesCompleted = newLinesCompleted,
                 rallyScore = state.rallyScore + pointsToAdd,
                 rallyLives = newLives,
                 rallyScorePoints = pointsToAdd
             )
        }
    }
}
