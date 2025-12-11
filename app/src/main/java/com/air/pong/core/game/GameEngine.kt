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

    // Swing settings instance - updated externally when settings change
    var swingSettings: SwingSettings = SwingSettings.createDefault()
        private set

    fun updateSwingSettings(settings: SwingSettings) {
        swingSettings = settings
    }

    private val SCORE_LIMIT = GameEngine.SCORE_LIMIT
    private val SERVE_ROTATION = GameEngine.SERVE_ROTATION
    
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
                rallyCellTiers = List(9) { 0 },
                highestTierAchieved = 0,
                rallyScorePoints = 0,
                rallyBonusMultiplier = 1,
                // Opponent State
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false },
                opponentCellTiers = List(9) { 0 },
                opponentHighestTier = 0,
                // Cumulative Lines Cleared
                totalLinesCleared = 0,
                partnerTotalLinesCleared = 0
            )
        }
    }

    companion object {
        const val DEFAULT_FLIGHT_TIME = 700L
        const val DEFAULT_SWING_THRESHOLD = 14.0f
        const val SERVE_ROTATION = 2
        const val SCORE_LIMIT = 11
        const val BOUNCE_OFFSET_MS = 200L // Time before arrival that bounce sound plays
        
        const val DEFAULT_DIFFICULTY = 800
        const val MIN_DIFFICULTY = 300
        const val MAX_DIFFICULTY = 1200
        const val MIN_HIT_WINDOW = 300L // Absolute floor for hit window (used by rally shrink and final floor)

        // Rally Mode Constants
        const val RALLY_STARTING_LIVES = 3
        const val RALLY_TIER_SHRINK_MS = 50L // Hit window shrinks by 50ms each time a new tier is reached
        
        // Happy number sequence for scoring tiers (per-shot points by tier index)
        // Happy numbers provide steady progression compared to Fibonacci's exponential growth
        val POINT_TIERS = listOf(1, 7, 10, 13, 19, 23, 28, 31, 32, 44, 49, 68, 70, 79, 82, 86, 91, 94, 97, 100)
        
        // Happy number sequence for line bonuses - shifted offsets for tier differentiation
        // Horizontal (lowest): Happy numbers offset by +1 from POINT_TIERS
        val HORIZONTAL_BONUSES = listOf(7, 10, 13, 19, 23, 28, 31, 32, 44, 49, 68, 70, 79, 82, 86, 91, 94, 97, 100, 103)
        
        // Vertical (mid): Happy numbers offset by +2 from POINT_TIERS  
        val VERTICAL_BONUSES = listOf(10, 13, 19, 23, 28, 31, 32, 44, 49, 68, 70, 79, 82, 86, 91, 94, 97, 100, 103, 109)
        
        // Diagonal (highest): Happy numbers offset by +3 from POINT_TIERS
        val DIAGONAL_BONUSES = listOf(13, 19, 23, 28, 31, 32, 44, 49, 68, 70, 79, 82, 86, 91, 94, 97, 100, 103, 109, 129)
        
        const val MIN_REACTION_TIME_MS = 200L // Minimum time after launch before a hit is valid
        
        const val MIN_FLIGHT_TIME = 400L
        const val MAX_FLIGHT_TIME = 1500L
        const val FLIGHT_TIME_HARD_THRESHOLD = 600L
        const val FLIGHT_TIME_MEDIUM_THRESHOLD = 900L
        
        // Cooldowns
        const val SERVE_COOLDOWN_MS = 1000L
        const val SWING_COOLDOWN_MS = 500L // Min time between swings to prevent spamming
        
        // Limits
        const val MAX_EVENT_LOG_SIZE = 50
        const val RALLY_SHRINK_PER_SHOT_MS = 10L // Hit window shrinks by this amount per shot in rally
        
        // Network/Timing
        const val MIN_BOUNCE_DELAY_MS = 100L
        const val AUTO_MISS_BUFFER_MS = 500L // Buffer time after hit window expires before auto-miss
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
                // Sync opponent's tiers
                opponentCellTiers = decodeTiersBitmask(msg.cellTiersBitmask),
                opponentHighestTier = msg.highestTierAchieved.toInt(),
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
    
    private fun decodeTiersBitmask(mask: Int): List<Int> {
        // Each cell tier encoded in 3 bits (0-7)
        return List(9) { i ->
            ((mask shr (i * 3)) and 0b111)
        }
    }

    private fun checkRisk(swingType: SwingType): HitResult {
        var (netRisk, outRisk) = swingType.getRiskPercentages(swingSettings)
        
        // Rally Mode: Reduce or eliminate the risk percentages (partners are placing shots more nicely)
        if (_gameState.value.gameMode == GameMode.RALLY) {
            // netRisk /= 4
            // outRisk /= 4
            netRisk = 0
            outRisk = 0
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
        
        val modifier = if (isServe) type.getServeFlightTimeModifier(swingSettings) else type.getFlightTimeModifier(swingSettings)
        return (state.flightTime * modifier).toLong()
    }

    /**
     * Returns the start and end offsets (in ms) relative to ballArrivalTimestamp for the valid hit window.
     */
    fun getHitWindowBounds(): Pair<Long, Long> {
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
            newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
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
             val serveModifier = swingType.getServeFlightTimeModifier(swingSettings)
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
                 // Rally Mode: Serves award tier points but don't mark grid
                 val minTier = if (it.gameMode == GameMode.RALLY) it.rallyCellTiers.minOrNull() ?: 0 else 0
                 val servePoints = if (it.gameMode == GameMode.RALLY) POINT_TIERS.getOrElse(minTier) { 1 } else 0
                 
                 val newState = it.copy(
                     ballArrivalTimestamp = nextArrival,
                     ballState = BallState.IN_AIR,
                     gamePhase = GamePhase.RALLY,
                     isMyTurn = false,
                     lastEvent = "You Served!",
                     eventLog = (it.eventLog + GameEvent.YouServed(swingType)).takeLast(MAX_EVENT_LOG_SIZE),
                     currentPointShots = it.currentPointShots + swingType,
                     currentRallyLength = 1,
                     // Rally mode serve scoring
                     rallyScore = it.rallyScore + servePoints,
                     rallyScorePoints = servePoints,
                     isServeHit = true,
                     lastHitGridIndex = -1 // -1 means serve (no grid cell)
                 )
                 newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
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
                        eventLog = (it.eventLog + GameEvent.YouHit(swingType)).takeLast(MAX_EVENT_LOG_SIZE),
                        currentPointShots = it.currentPointShots + swingType,
                        currentRallyLength = it.currentRallyLength + 1,
                        ballState = BallState.IN_AIR,
                        longestRally = kotlin.math.max(it.longestRally, it.currentRallyLength + 1) // Track longest rally (local hit)
                    )
                    // Recalculate window for opponent based on new rally length
                    val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
                    
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
                it.copy(eventLog = (it.eventLog + event).takeLast(MAX_EVENT_LOG_SIZE))
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
                eventLog = (it.eventLog + GameEvent.BallBounced).takeLast(MAX_EVENT_LOG_SIZE)
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
         val modifier = if (isServe) swingType.getServeFlightTimeModifier(swingSettings) else swingType.getFlightTimeModifier(swingSettings)
         
         val actualFlightTime = (baseFlightTime * modifier).toLong()
         val nextArrival = timestamp + actualFlightTime
         
         _gameState.update {
             val newState = it.copy(
                 gamePhase = GamePhase.RALLY,
                 isMyTurn = true, // Now it's my turn to hit
                 ballArrivalTimestamp = nextArrival,
                 lastEvent = "Opponent Hit!",
                 eventLog = (it.eventLog + GameEvent.OpponentHit(swingType)).takeLast(MAX_EVENT_LOG_SIZE),
                 ballState = BallState.IN_AIR,
                 lastSwingType = swingType, // Store this so we can calculate window shrink later
                 currentRallyLength = it.currentRallyLength + 1
             )
             val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
             
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
            it.copy(eventLog = (it.eventLog + event).takeLast(MAX_EVENT_LOG_SIZE))
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
            it.copy(eventLog = (it.eventLog + event).takeLast(MAX_EVENT_LOG_SIZE))
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
                it.copy(eventLog = (it.eventLog + GameEvent.MissNoSwing).takeLast(MAX_EVENT_LOG_SIZE))
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
                         eventLog = (state.eventLog + GameEvent.PointScored(false)).takeLast(MAX_EVENT_LOG_SIZE), // No "winner" in rally
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
                        eventLog = (state.eventLog + GameEvent.PointScored(iWon)).takeLast(MAX_EVENT_LOG_SIZE),
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
                    it.copy(currentHitWindow = it.calculateHitWindow(swingSettings))
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
            newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
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
                rallyCellTiers = List(9) { 0 },
                highestTierAchieved = 0,
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false },
                opponentCellTiers = List(9) { 0 },
                opponentHighestTier = 0,
                rallyScorePoints = 0,
                totalLinesCleared = 0,
                partnerTotalLinesCleared = 0
            )
            // Reset hit window
            newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
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
            it.copy(eventLog = (it.eventLog + event).takeLast(MAX_EVENT_LOG_SIZE))
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
    
    fun clearRallySoundEvent() {
        _gameState.update {
            it.copy(rallySoundEvent = null)
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
                rallyCellTiers = List(9) { 0 },
                highestTierAchieved = 0,
                rallyScorePoints = 0,
                rallyBonusMultiplier = 1,
                opponentRallyGrid = List(9) { false },
                opponentRallyLinesCompleted = List(8) { false },
                opponentCellTiers = List(9) { 0 },
                opponentHighestTier = 0,
                totalLinesCleared = 0,
                partnerTotalLinesCleared = 0
            )
            newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
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
        // Get the correct grid and tier data based on whether it's opponent or local
        val gridIndex = swingType.getGridIndex()
        val currentGrid = if (isOpponent) state.opponentRallyGrid else state.rallyGrid
        val currentTiers = if (isOpponent) state.opponentCellTiers else state.rallyCellTiers
        val currentHighestTier = if (isOpponent) state.opponentHighestTier else state.highestTierAchieved
        
        // 1. Mark Grid Cell
        val newGrid = currentGrid.toMutableList()
        newGrid[gridIndex] = true
        
        // 2. Award points based on the cell's current tier
        val cellTier = currentTiers[gridIndex]
        val pointsPerHit = POINT_TIERS.getOrElse(cellTier) { POINT_TIERS.last() }
        var pointsToAdd = pointsPerHit
        
        // 3. Check for Line Completions
        // Lines: Rows (0-2, 3-5, 6-8), Cols (036, 147, 258), Diagonals (048, 246)
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
            listOf(0, 4, 8), listOf(2, 4, 6)                   // Diagonals (indices 6, 7)
        )
        
        val currentLinesCompleted = if (isOpponent) state.opponentRallyLinesCompleted else state.rallyLinesCompleted
        val newLinesCompleted = currentLinesCompleted.toMutableList()
        val newTiers = currentTiers.toMutableList()
        var linesCompletedThisTurn = 0
        val linesCompletedIndices = mutableListOf<Int>()
        
        // Find the minimum tier across all cells (determines bonus level and upgrade eligibility)
        val minTier = currentTiers.minOrNull() ?: 0
        val nextTier = minTier + 1
        
        // Collect all cells to clear (may be from multiple lines)
        val cellsToClear = mutableSetOf<Int>()
        
        lines.forEachIndexed { lineIndex, indices ->
            val isLineComplete = indices.all { newGrid[it] }
            if (isLineComplete && !currentLinesCompleted[lineIndex]) {
                // New Line Completed! Award bonus based on minimum tier
                newLinesCompleted[lineIndex] = true
                linesCompletedThisTurn++
                linesCompletedIndices.add(lineIndex)
                
                // Determine bonus based on line type
                // Rows (indices 0-2) = Horizontal, Cols (indices 3-5) = Vertical, Diags (indices 6-7) = Diagonal
                val bonus = when {
                    lineIndex >= 6 -> DIAGONAL_BONUSES.getOrElse(minTier) { DIAGONAL_BONUSES.last() }
                    lineIndex >= 3 -> VERTICAL_BONUSES.getOrElse(minTier) { VERTICAL_BONUSES.last() }
                    else -> HORIZONTAL_BONUSES.getOrElse(minTier) { HORIZONTAL_BONUSES.last() }
                }
                pointsToAdd += bonus
                
                // Mark cells to clear
                indices.forEach { cellIndex ->
                    cellsToClear.add(cellIndex)
                }
            }
        }
        
        // Clear all marked cells and upgrade them if eligible
        cellsToClear.forEach { cellIndex ->
            newGrid[cellIndex] = false
            // Only upgrade if this cell is at the minimum tier
            if (newTiers[cellIndex] == minTier && minTier < POINT_TIERS.size - 1) {
                newTiers[cellIndex] = nextTier
            }
        }
        
        // 4. Double-line clear bonus: When 2+ lines completed, award up to 2 bonus cells
        if (linesCompletedThisTurn >= 2) {
            // Find all unmarked cells at the base tier (minTier)
            val availableBonusCells = newTiers.indices.filter { idx ->
                !newGrid[idx] && newTiers[idx] == minTier
            }
            
            // Select up to 2 random cells
            val bonusCellsToAward = availableBonusCells.shuffled().take(2)
            
            bonusCellsToAward.forEach { cellIndex ->
                // Award points for this cell (no line bonus, just tier points)
                val cellTier = newTiers[cellIndex]
                val cellPoints = POINT_TIERS.getOrElse(cellTier) { POINT_TIERS.last() }
                pointsToAdd += cellPoints
                
                // Mark cell as hit then immediately clear it (simulates hitting the cell)
                // Upgrade the cell tier
                if (newTiers[cellIndex] == minTier && minTier < POINT_TIERS.size - 1) {
                    newTiers[cellIndex] = nextTier
                }
            }
        }
        
        // Reset line completion tracking for cleared lines
        lines.forEachIndexed { lineIndex, indices ->
            val anyEmpty = indices.any { !newGrid[it] }
            if (anyEmpty) {
                newLinesCompleted[lineIndex] = false
            }
        }
        
        // 4. Check for tier upgrade and award extra life
        var newLives = state.rallyLives
        var newHighestTier = currentHighestTier
        var tierUpgraded = false
        
        // Special X-Clear: If both diagonals (indices 6 and 7) were completed this turn,
        // force tier upgrade even if not all cells are upgraded
        val bothDiagonalsCompleted = linesCompletedIndices.contains(6) && linesCompletedIndices.contains(7)
        
        // Check if tier should upgrade (normal path or X-clear path)
        val allCellsUpgraded = newTiers.all { it >= nextTier }
        val shouldUpgradeTier = allCellsUpgraded || bothDiagonalsCompleted
        
        // Use GLOBAL highest tier to determine if this is the first time ANYONE reached this tier
        // This ensures only the first player (of the two) to reach a tier gets the extra life
        val globalHighestTier = maxOf(state.highestTierAchieved, state.opponentHighestTier)
        
        // Only award life when:
        // 1. Tier should upgrade
        // 2. This is the first time GLOBALLY this tier is reached
        // 3. This is MY OWN grid (not opponent's) - to avoid sync race conditions
        if (shouldUpgradeTier && nextTier > globalHighestTier && !isOpponent) {
            // First time reaching this tier! Award extra life
            newLives++
            newHighestTier = nextTier
            tierUpgraded = true
            
            // Reset the entire grid and upgrade ALL cells to the new tier
            for (i in 0 until 9) {
                newGrid[i] = false
                newTiers[i] = nextTier
            }
            // Reset all line completions
            for (i in 0 until 8) {
                newLinesCompleted[i] = false
            }
        }
        
        // Determine sound event (only for local player's grid)
        val soundEvent: RallySoundEvent? = if (!isOpponent) {
            when {
                tierUpgraded -> RallySoundEvent.GRID_COMPLETE
                linesCompletedThisTurn > 0 -> RallySoundEvent.LINE_COMPLETE
                else -> null
            }
        } else null
        
        return if (isOpponent) {
            state.copy(
                opponentRallyGrid = newGrid,
                opponentRallyLinesCompleted = newLinesCompleted,
                opponentCellTiers = newTiers,
                opponentHighestTier = newHighestTier,
                rallyScore = state.rallyScore + pointsToAdd,
                rallyLives = newLives,
                rallyScorePoints = pointsToAdd,
                partnerTotalLinesCleared = state.partnerTotalLinesCleared + linesCompletedThisTurn
            )
        } else {
            state.copy(
                rallyGrid = newGrid,
                rallyLinesCompleted = newLinesCompleted,
                rallyCellTiers = newTiers,
                highestTierAchieved = newHighestTier,
                rallyScore = state.rallyScore + pointsToAdd,
                rallyLives = newLives,
                rallyScorePoints = pointsToAdd,
                rallySoundEvent = soundEvent,
                lastHitGridIndex = gridIndex,
                isServeHit = false,
                totalLinesCleared = state.totalLinesCleared + linesCompletedThisTurn
            )
        }
    }
}
