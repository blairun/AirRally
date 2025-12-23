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
    
    // Solo Rally flight time adder - configurable, default 500ms
    var soloFlightAdder: Long = DEFAULT_SOLO_FLIGHT_TIME_ADDER
        private set
    
    fun setSoloFlightAdder(adder: Long) {
        soloFlightAdder = adder
    }

    // Game Mode Strategy - delegates mode-specific logic
    private var modeStrategy: GameModeStrategy = RallyModeStrategy()
    
    /**
     * Gets the appropriate strategy for the given game mode.
     */
    private fun getStrategy(mode: GameMode): GameModeStrategy {
        return when (mode) {
            GameMode.CLASSIC -> ClassicModeStrategy()
            GameMode.RALLY -> RallyModeStrategy()
            GameMode.SOLO_RALLY -> SoloRallyModeStrategy()
        }
    }

    private val SCORE_LIMIT = GameEngine.SCORE_LIMIT
    private val SERVE_ROTATION = GameEngine.SERVE_ROTATION
    
    // Local player identity. Must be set before game starts.
    private var isHost = false
    private var lastPointEndedTimestamp: Long = 0L
    private var lastSwingTimestamp: Long = 0L
    
    // Automatic handedness detection via gravity X-axis on flat hits
    private var gravXEma: Float = 0f
    private var hasHandednessData: Boolean = false
    
    /**
     * Returns true if left-handed play is detected.
     * Uses EMA of gravX on flat hits: negative = left, positive = right.
     */
    val isLeftHanded: Boolean
        get() = gravXEma < 0f && hasHandednessData
    
    /**
     * Returns current handedness detection state for debug display.
     * Returns "Left", "Right", or "Unknown".
     */
    fun getDetectedHand(): String = when {
        !hasHandednessData -> "Unknown"
        gravXEma < 0f -> "Left"
        else -> "Right"
    }
    
    /**
     * Returns the current gravX EMA value for debug display.
     */
    fun getGravXEma(): Float = gravXEma
    
    fun setLocalPlayer(isHost: Boolean) {
        this.isHost = isHost
    }

    fun startGame(mode: GameMode? = null) {
        // Use provided mode, or fall back to current mode (which was likely synced via Settings)
        // Only default to CLASSIC if absolutely nothing is set (though enum handles that)
        val targetMode = mode ?: _gameState.value.gameMode
        
        // Set the strategy for this game mode
        modeStrategy = getStrategy(targetMode)
        val initialModeState = modeStrategy.createInitialModeState()
        
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
                // Strategy Pattern: Initialize mode state
                modeState = initialModeState,
                gameMode = targetMode
            )
        }
    }

    companion object {
        const val DEFAULT_FLIGHT_TIME = 700L
        const val DEFAULT_SOFT_THRESHOLD = 14.0f
        const val DEFAULT_MEDIUM_THRESHOLD = 23.0f
        const val DEFAULT_HARD_THRESHOLD = 44.0f
        const val MIN_THRESHOLD_GAP = 8.0f
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
        
        // Solo Rally Mode Constants
        const val DEFAULT_SOLO_FLIGHT_TIME_ADDER = 500L // Flight time is 500ms longer in Solo Rally
        
        // Solo Rally Timing Ratios
        // IMPORTANT: These are the single source of truth for wall/table bounce timing.
        // Used by: GameViewModel (sound scheduling), SoloBallBounceAnimation (visual animation)
        const val SOLO_RALLY_TABLE_BOUNCE_RATIO = 0.75f  // When ball bounces on table (hit window opens) - for rally hits
        const val SOLO_RALLY_WALL_BOUNCE_LOB = 0.55f     // Lobs take longer to reach wall
        const val SOLO_RALLY_WALL_BOUNCE_SMASH = 0.35f   // Smashes hit wall quickly  
        const val SOLO_RALLY_WALL_BOUNCE_FLAT = 0.40f    // Standard timing
        
        // Solo Serve Timing Ratios (serve has extra table bounce on outgoing side)
        // Smash serves: snappier paddle→table, longer wall→table return
        const val SOLO_SERVE_TABLE1_SMASH = 0.15f   // First table bounce (fast)
        const val SOLO_SERVE_TABLE1_DEFAULT = 0.25f // First table bounce (standard)
        const val SOLO_SERVE_WALL = 0.50f           // Wall bounce (unchanged)
        const val SOLO_SERVE_TABLE2_SMASH = 0.80f   // Second table bounce (longer return)
        const val SOLO_SERVE_TABLE2_DEFAULT = 0.75f // Second table bounce (standard)
        
        /**
         * Returns the first table bounce timing ratio for Solo Rally serve based on swing type.
         * Smash: 15% (snappier), Others: 25% (standard)
         */
        fun getSoloServeTable1Ratio(swingType: SwingType): Float = when {
            swingType.isSmash() -> SOLO_SERVE_TABLE1_SMASH
            else -> SOLO_SERVE_TABLE1_DEFAULT
        }
        
        /**
         * Returns the second table bounce timing ratio for Solo Rally serve based on swing type.
         * Smash: 80% (longer wall→table), Others: 75% (standard)
         */
        fun getSoloServeTable2Ratio(swingType: SwingType): Float = when {
            swingType.isSmash() -> SOLO_SERVE_TABLE2_SMASH
            else -> SOLO_SERVE_TABLE2_DEFAULT
        }
        
        /**
         * Returns the wall bounce timing ratio for Solo Rally based on swing type.
         * Lobs: 55% (slower arc), Smash: 35% (faster), Flat: 40% (standard)
         */
        fun getSoloWallBounceRatio(swingType: SwingType): Float = when {
            swingType.isLob() -> SOLO_RALLY_WALL_BOUNCE_LOB
            swingType.isSmash() -> SOLO_RALLY_WALL_BOUNCE_SMASH
            else -> SOLO_RALLY_WALL_BOUNCE_FLAT
        }
        
        // Triangular number sequence for scoring tiers
        // Formula: T(n) = n * (n + 1) / 2
        // This provides smooth, unlimited progression compared to hardcoded lists
        
        /**
         * Returns the nth triangular number: T(n) = n * (n + 1) / 2
         * T(1)=1, T(2)=3, T(3)=6, T(4)=10, T(5)=15, ...
         */
        fun getTriangularNumber(n: Int): Int = n * (n + 1) / 2
        
        /**
         * Returns points for hitting a cell at the given tier.
         * Uses T(tier + 1): Tier 0 = 1pt, Tier 1 = 3pt, Tier 2 = 6pt, etc.
         */
        fun getPointsForTier(tier: Int): Int = getTriangularNumber(tier + 1)
        
        /**
         * Returns horizontal line bonus for the given tier.
         * Uses T(tier + 2) - offset +1 from base points.
         */
        fun getHorizontalBonus(tier: Int): Int = getTriangularNumber(tier + 2)
        
        /**
         * Returns vertical line bonus for the given tier.
         * Uses T(tier + 3) - offset +2 from base points (more than horizontal).
         */
        fun getVerticalBonus(tier: Int): Int = getTriangularNumber(tier + 3)
        
        /**
         * Returns diagonal line bonus for the given tier.
         * Uses T(tier + 4) - offset +3 from base points (highest bonus).
         */
        fun getDiagonalBonus(tier: Int): Int = getTriangularNumber(tier + 4)
        
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
        
        // Automatic Handedness Detection Constants
        // Uses gravity X-axis on flat hits to determine which hand is holding the phone
        const val HANDEDNESS_GRAV_Z_MAX = 4.0f  // |gravZ| must be < 4 to be "flat" (perpendicular to ground)
        const val HANDEDNESS_GRAV_X_THRESHOLD = 5.0f  // |gravX| must be > 5 to count as a valid sample
        const val HANDEDNESS_EMA_ALPHA = 0.3f  // Smoothing factor for EMA (0.3 = moderate smoothing)
        
        /**
         * Returns the momentum multiplier based on current rally length.
         * Rally length 0-9: 1.0×, 10-19: 1.1×, 20-29: 1.2×, 30-39: 1.3×, 40-49: 1.4×, 50+: 1.5×
         * Used to reward sustained rallies with higher score multipliers.
         */
        fun getMomentumMultiplier(rallyLength: Int): Float = when {
            rallyLength >= 50 -> 1.5f
            rallyLength >= 40 -> 1.4f
            rallyLength >= 30 -> 1.3f
            rallyLength >= 20 -> 1.2f
            rallyLength >= 10 -> 1.1f
            else -> 1.0f
        }
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
            // Build RallyState from synced data if in Rally mode
            val updatedModeState = if (msg.gameMode == GameMode.RALLY) {
                val currentRallyState = it.modeState as? ModeState.RallyState ?: ModeState.RallyState()
                currentRallyState.copy(
                    score = msg.rallyScore,
                    lives = msg.rallyLives,
                    // Only sync OPPONENT's grid (sender's "my grid" = my "opponent grid")
                    // Keep my own grid locally authoritative - don't overwrite!
                    opponentGrid = decodeGridBitmask(msg.rallyGridBitmask),
                    opponentLinesCompleted = decodeLinesBitmask(msg.rallyLinesBitmask),
                    opponentCellTiers = decodeTiersBitmask(msg.cellTiersBitmask),
                    opponentHighestTier = msg.highestTierAchieved.toInt()
                )
            } else {
                it.modeState
            }
            
            it.copy(
                player1Score = msg.player1Score,
                player2Score = msg.player2Score,
                gamePhase = msg.currentPhase,
                servingPlayer = msg.servingPlayer,
                gameMode = msg.gameMode,
                longestRally = msg.longestRally,
                modeState = updatedModeState
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
        
        // Use strategy to determine if risk is enabled for this mode
        if (!modeStrategy.hasRisk) {
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
        val type = state.lastSwingType ?: return getBaseFlightTime(state)
        
        // If Rally Length is 1, the LAST shot was a serve.
        val isServe = state.currentRallyLength == 1
        
        val modifier = if (isServe) type.getServeFlightTimeModifier(swingSettings) else type.getFlightTimeModifier(swingSettings)
        return (getBaseFlightTime(state) * modifier).toLong()
    }
    
    /**
     * Returns the base flight time, applying Solo Rally adder if applicable.
     */
    private fun getBaseFlightTime(state: GameState): Long {
        return if (state.gameMode == GameMode.SOLO_RALLY) {
            state.flightTime + soloFlightAdder
        } else {
            state.flightTime
        }
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
    fun processSwing(timestamp: Long, force: Float, x: Float, y: Float, z: Float, gx: Float, gy: Float, gz: Float, peakGx: Float, peakGy: Float, peakGz: Float, gravX: Float, gravY: Float, gravZ: Float): HitResult? {
        // Anti-Spam Cooldown
        if (timestamp - lastSwingTimestamp < SWING_COOLDOWN_MS) {
            return null
        }
        lastSwingTimestamp = timestamp

        // Prevent swinging if we are already waiting for a pending miss resolution
        if (_gameState.value.pendingMiss != null) {
            return null
        }

        val swingType = classifySwing(force, gravZ, _gameState.value.softSwingThreshold, _gameState.value.mediumSwingThreshold, _gameState.value.hardSwingThreshold)
        
        // Update automatic handedness detection on flat hits
        if (swingType.isFlat()) {
            val absGravZ = kotlin.math.abs(gravZ)
            val absGravX = kotlin.math.abs(gravX)
            
            // Only sample when phone is roughly perpendicular to ground (flat swing)
            if (absGravZ < HANDEDNESS_GRAV_Z_MAX && absGravX > HANDEDNESS_GRAV_X_THRESHOLD) {
                if (!hasHandednessData) {
                    // First sample - initialize EMA
                    gravXEma = gravX
                    hasHandednessData = true
                } else {
                    // Update EMA: new = α×current + (1-α)×old
                    gravXEma = HANDEDNESS_EMA_ALPHA * gravX + (1 - HANDEDNESS_EMA_ALPHA) * gravXEma
                }
            }
        }
        
        // CHECK TIMING BEFORE UPDATING STATS
        // We must check timing against the INCOMING shot (lastSwingType = Opponent's).
        // If we update state first, lastSwingType becomes MY swing, and we check timing against my own swing characteristics!
        val timingResult = checkHitTiming(timestamp)

        // Store raw values for debug
        _gameState.update {
            val newState = it.copy(
                lastSwingType = swingType,
                lastSwingData = SwingData(force, x, y, z, gx, gy, gz, peakGx, peakGy, peakGz, gravX, gravY, gravZ, SpinType.fromGyro(peakGy, peakGz, isLeftHanded))
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
             // Use getBaseFlightTime() to include solo adder for Solo Rally mode
             val baseFlightTime = getBaseFlightTime(currentState)
             val serveModifier = swingType.getServeFlightTimeModifier(swingSettings)
             // Calculate spin type early to apply spin timing modifier
             val spinType = SpinType.fromGyro(peakGy, peakGz, isLeftHanded)
             val spinFlightModifier = spinType.getSpinFlightTimeModifier(timestamp)
             val serveFlightTime = (baseFlightTime * serveModifier * spinFlightModifier).toLong()

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
                         lastSwingData = SwingData(force, x, y, z, gx, gy, gz, peakGx, peakGy, peakGz, gravX, gravY, gravZ, SpinType.fromGyro(peakGy, peakGz, isLeftHanded)),
                         currentPointShots = it.currentPointShots + swingType // Add to stats even if miss
                     )
                 }
                 return HitResult.PENDING
             }



             val nextArrival = timestamp + serveFlightTime
             // spinType already calculated above for flight modifier
             _gameState.update {
                 // In Solo Rally, isMyTurn stays true since player is always hitting
                 val isSoloRally = it.gameMode == GameMode.SOLO_RALLY
                 val newState = it.copy(
                     ballArrivalTimestamp = nextArrival,
                     ballState = BallState.IN_AIR,
                     gamePhase = GamePhase.RALLY,
                     isMyTurn = isSoloRally,
                     lastEvent = "You Served!",
                     eventLog = (it.eventLog + GameEvent.YouServed(swingType, spinType)).takeLast(MAX_EVENT_LOG_SIZE),
                     currentPointShots = it.currentPointShots + swingType,
                     currentRallyLength = 1
                 )
                 val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings, spinType, timestamp))
                 
                 // Use Strategy Pattern for serve scoring
                 val updatedModeState = modeStrategy.onServe(stateWithWindow, swingType, spinType)
                 stateWithWindow.copy(modeState = updatedModeState)
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
                // Calculate actual flight time with swing and spin modifiers
                // Use getBaseFlightTime() to include solo adder for Solo Rally mode
                val spinType = SpinType.fromGyro(peakGy, peakGz, isLeftHanded)
                val spinFlightModifier = spinType.getSpinFlightTimeModifier(timestamp)
                val actualFlightTime = (getBaseFlightTime(currentState) * swingType.getFlightTimeModifier(swingSettings) * spinFlightModifier).toLong()
                val nextArrival = timestamp + actualFlightTime
                // spinType already calculated above for flight modifier
                _gameState.update {
                    if (it.gamePhase != GamePhase.RALLY && it.gamePhase != GamePhase.WAITING_FOR_SERVE) {
                        return@update it
                    }
                    
                    // In Solo Rally, isMyTurn stays true since player is always hitting
                    val isSoloRally = it.gameMode == GameMode.SOLO_RALLY
                    val newState = it.copy(
                        gamePhase = GamePhase.RALLY,
                        isMyTurn = isSoloRally,
                        ballArrivalTimestamp = nextArrival,
                        lastEvent = "You Hit!",
                        eventLog = (it.eventLog + GameEvent.YouHit(swingType, spinType)).takeLast(MAX_EVENT_LOG_SIZE),
                        currentPointShots = it.currentPointShots + swingType,
                        currentRallyLength = it.currentRallyLength + 1,
                        ballState = BallState.IN_AIR,
                        longestRally = kotlin.math.max(it.longestRally, it.currentRallyLength + 1) // Track longest rally (local hit)
                    )
                    // Recalculate window for opponent based on new rally length and spin
                    val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings, spinType, timestamp))
                    
                    // Use Strategy Pattern for mode-specific hit logic
                    val updatedModeState = modeStrategy.onHit(stateWithWindow, swingType, isOpponent = false, spinType)
                    stateWithWindow.copy(modeState = updatedModeState)
                }
                
                // Check if landmine was triggered - if so, end the rally
                val currentModeState = _gameState.value.modeState
                val landmineTriggered = when (currentModeState) {
                    is ModeState.RallyState -> currentModeState.landmineTriggered
                    is ModeState.SoloRallyState -> currentModeState.landmineTriggered
                    else -> false
                }
                if (landmineTriggered) {
                    // Add LandmineHit event to log
                    _gameState.update {
                        it.copy(eventLog = (it.eventLog + GameEvent.LandmineHit()).takeLast(MAX_EVENT_LOG_SIZE))
                    }
                    // End the rally by calling handleMiss
                    handleMiss(if (isHost) Player.PLAYER_1 else Player.PLAYER_2)
                    // Return MISS_LANDMINE so network layer sends miss message to partner
                    return HitResult.MISS_LANDMINE
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
                        lastSwingData = SwingData(force, x, y, z, gx, gy, gz, peakGx, peakGy, peakGz, gravX, gravY, gravZ, SpinType.fromGyro(peakGy, peakGz, isLeftHanded)),
                        currentPointShots = it.currentPointShots + swingType // Add to stats even if miss
                    )
                }
                return HitResult.PENDING
            }
        } else {
            // Timing Miss - but check for Shield first (Rally modes only)
            val currentState = _gameState.value
            val rallyState = currentState.modeState as? ModeState.RallyState
            val soloRallyState = currentState.modeState as? ModeState.SoloRallyState
            val hasShield = rallyState?.hasShield ?: soloRallyState?.hasShield ?: false
            
            if (hasShield && (timingResult == HitResult.MISS_EARLY || timingResult == HitResult.MISS_LATE)) {
                // Shield absorbs the timing miss! Consume shield and treat as hit.
                _gameState.update {
                    val updatedModeState = when (val state = it.modeState) {
                        is ModeState.RallyState -> state.copy(hasShield = false)
                        is ModeState.SoloRallyState -> state.copy(hasShield = false)
                        else -> state
                    }
                    it.copy(
                        modeState = updatedModeState,
                        eventLog = (it.eventLog + GameEvent.YouHit(swingType, SpinType.fromGyro(peakGy, peakGz, isLeftHanded))).takeLast(MAX_EVENT_LOG_SIZE)
                    )
                }
                
                // Continue as if it was a hit - calculate flight time and send ball
                val spinType = SpinType.fromGyro(peakGy, peakGz, isLeftHanded)
                val spinFlightModifier = spinType.getSpinFlightTimeModifier(timestamp)
                val actualFlightTime = (getBaseFlightTime(currentState) * swingType.getFlightTimeModifier(swingSettings) * spinFlightModifier).toLong()
                val nextArrival = timestamp + actualFlightTime
                
                _gameState.update {
                    if (it.gamePhase != GamePhase.RALLY && it.gamePhase != GamePhase.WAITING_FOR_SERVE) {
                        return@update it
                    }
                    val isSoloRally = it.gameMode == GameMode.SOLO_RALLY
                    val newState = it.copy(
                        gamePhase = GamePhase.RALLY,
                        isMyTurn = isSoloRally,
                        ballArrivalTimestamp = nextArrival,
                        lastEvent = "Shield Saved!",
                        currentPointShots = it.currentPointShots + swingType,
                        currentRallyLength = it.currentRallyLength + 1,
                        ballState = BallState.IN_AIR,
                        longestRally = kotlin.math.max(it.longestRally, it.currentRallyLength + 1)
                    )
                    val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings, spinType, timestamp))
                    val updatedModeState = modeStrategy.onHit(stateWithWindow, swingType, isOpponent = false, spinType)
                    
                    // Force SHIELD_BREAK sound since the shield just saved the rally (high priority)
                    val finalModeState = when (updatedModeState) {
                         is ModeState.RallyState -> updatedModeState.copy(soundEvent = com.air.pong.core.game.RallySoundEvent.SHIELD_BREAK)
                         is ModeState.SoloRallyState -> updatedModeState.copy(soundEvent = com.air.pong.core.game.RallySoundEvent.SHIELD_BREAK)
                         else -> updatedModeState
                    }
                    stateWithWindow.copy(modeState = finalModeState)
                }
                return HitResult.HIT
            }
            
            // No shield - actual timing miss
            // BUT: Still check for special square effects (landmine explosion, banana penalty)
            val missedSwingResult = modeStrategy.onMissedSwing(_gameState.value, swingType)
            
            val event = if (timingResult == HitResult.MISS_EARLY) GameEvent.WhiffEarly else GameEvent.MissLate
            _gameState.update {
                // Apply special square effects from missed swing
                val stateWithMissedSwingEffects = it.copy(modeState = missedSwingResult.updatedModeState)
                
                // If there was a sound event from special square, preserve it in the mode state
                val finalModeState = if (missedSwingResult.soundEvent != null) {
                    when (val modeState = stateWithMissedSwingEffects.modeState) {
                        is ModeState.RallyState -> modeState.copy(soundEvent = missedSwingResult.soundEvent)
                        is ModeState.SoloRallyState -> modeState.copy(soundEvent = missedSwingResult.soundEvent)
                        else -> modeState
                    }
                } else {
                    stateWithMissedSwingEffects.modeState
                }
                
                stateWithMissedSwingEffects.copy(
                    modeState = finalModeState,
                    eventLog = (it.eventLog + event).takeLast(MAX_EVENT_LOG_SIZE)
                )
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

    fun onOpponentHit(timestamp: Long, swingTypeOrdinal: Int, spinTypeOrdinal: Int = 0) {
         val swingType = try {
             SwingType.values().getOrElse(swingTypeOrdinal) { SwingType.MEDIUM_FLAT }
         } catch (e: Exception) {
             SwingType.MEDIUM_FLAT
         }
         
         val spinType = try {
             SpinType.entries.getOrElse(spinTypeOrdinal) { SpinType.NONE }
         } catch (e: Exception) {
             SpinType.NONE
         }
         
         // Adjust flight time based on SwingType and SpinType
         // CRITICAL FIX: Check if this was a serve. 
         // If we are in WAITING_FOR_SERVE phase, the incoming hit IS a serve.
         // We must use serve modifiers (especially for Smash which differs wildly: 0.7 vs 2.1).
         val isServe = _gameState.value.gamePhase == GamePhase.WAITING_FOR_SERVE
         val baseFlightTime = _gameState.value.flightTime
         val swingModifier = if (isServe) swingType.getServeFlightTimeModifier(swingSettings) else swingType.getFlightTimeModifier(swingSettings)
         val spinFlightModifier = spinType.getSpinFlightTimeModifier(timestamp)
         
         val actualFlightTime = (baseFlightTime * swingModifier * spinFlightModifier).toLong()
         val nextArrival = timestamp + actualFlightTime
         
         _gameState.update {
             val newState = it.copy(
                 gamePhase = GamePhase.RALLY,
                 isMyTurn = true, // Now it's my turn to hit
                 ballArrivalTimestamp = nextArrival,
                 lastEvent = "Opponent Hit!",
                 eventLog = (it.eventLog + GameEvent.OpponentHit(swingType, spinType)).takeLast(MAX_EVENT_LOG_SIZE),
                 ballState = BallState.IN_AIR,
                 lastSwingType = swingType, // Store this so we can calculate window shrink later
                 currentRallyLength = it.currentRallyLength + 1
             )
             val stateWithWindow = newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings, spinType, timestamp))
             
             // Use Strategy Pattern for mode-specific hit logic
             val updatedModeState = modeStrategy.onHit(stateWithWindow, swingType, isOpponent = true, spinType)
             stateWithWindow.copy(modeState = updatedModeState)
         }
         
         // Check if landmine was triggered by partner's hit - if so, end the rally
         val currentModeState = _gameState.value.modeState
         val landmineTriggered = when (currentModeState) {
             is ModeState.RallyState -> currentModeState.landmineTriggered
             is ModeState.SoloRallyState -> currentModeState.landmineTriggered
             else -> false
         }
         if (landmineTriggered) {
             // Add LandmineHit event to log
             _gameState.update {
                 it.copy(eventLog = (it.eventLog + GameEvent.LandmineHit()).takeLast(MAX_EVENT_LOG_SIZE))
             }
             // End the rally by calling handleMiss (partner caused the miss)
             handleMiss(if (isHost) Player.PLAYER_2 else Player.PLAYER_1)
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
        // Update Score using Strategy Pattern
        _gameState.update { state ->
            // Concurrency Guard: If point already scored, ignore
            if (state.gamePhase == GamePhase.POINT_SCORED || state.gamePhase == GamePhase.GAME_OVER) {
                return@update state
            }
            
            // Preserve sound event from current state (set by onMissedSwing for special squares)
            val currentSoundEvent = when (val modeState = state.modeState) {
                is ModeState.RallyState -> modeState.soundEvent
                is ModeState.SoloRallyState -> modeState.soundEvent
                else -> null
            }
            
            // Delegate to mode strategy for mode-specific miss handling
            val missResult = modeStrategy.onMiss(state, whoMissed, isHost)
            
            // Restore sound event from before onMiss (onMiss resets it to null)
            val finalModeState = if (currentSoundEvent != null) {
                when (val ms = missResult.updatedModeState) {
                    is ModeState.RallyState -> ms.copy(soundEvent = currentSoundEvent)
                    is ModeState.SoloRallyState -> ms.copy(soundEvent = currentSoundEvent)
                    else -> ms
                }
            } else {
                missResult.updatedModeState
            }
            
            // Update scores for Classic mode (Rally doesn't use these)
            val p1Score = if (whoMissed == Player.PLAYER_2) state.player1Score + 1 else state.player1Score
            val p2Score = if (whoMissed == Player.PLAYER_1) state.player2Score + 1 else state.player2Score
            
            val nextServer = missResult.nextServer
            val isMyTurn = (isHost && nextServer == Player.PLAYER_1) || (!isHost && nextServer == Player.PLAYER_2)
            
            // Determine win/loss for event log (Classic mode)
            val winner = if (whoMissed == Player.PLAYER_1) Player.PLAYER_2 else Player.PLAYER_1
            val iWon = (isHost && winner == Player.PLAYER_1) || (!isHost && winner == Player.PLAYER_2)
            
            // Build base state update
            var updatedState = state.copy(
                modeState = finalModeState,
                player1Score = p1Score,
                player2Score = p2Score,
                servingPlayer = nextServer,
                isMyTurn = isMyTurn,
                gamePhase = if (missResult.isGameOver) GamePhase.GAME_OVER else GamePhase.POINT_SCORED,
                lastEvent = missResult.gameOverMessage ?: if (missResult.isGameOver) "Game Over!" else if (state.gameMode == GameMode.RALLY) {
                    val lives = (missResult.updatedModeState as? ModeState.RallyState)?.lives ?: 0
                    "Life Lost! $lives Left"
                } else "Point Scored",
                eventLog = (state.eventLog + GameEvent.PointScored(iWon)).takeLast(MAX_EVENT_LOG_SIZE),
                longestRally = kotlin.math.max(state.longestRally, state.currentRallyLength)
            )
            
            updatedState
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

    fun updateSettings(flightTime: Long, difficulty: Int, isDebugMode: Boolean, useDebugTones: Boolean, softThreshold: Float, mediumThreshold: Float, hardThreshold: Float, isRallyShrinkEnabled: Boolean, gameMode: GameMode = GameMode.RALLY) {
        _gameState.update {
            val newState = it.copy(
                flightTime = flightTime,
                difficulty = difficulty,
                isDebugMode = isDebugMode,
                useDebugTones = useDebugTones,
                softSwingThreshold = softThreshold,
                mediumSwingThreshold = mediumThreshold,
                hardSwingThreshold = hardThreshold,
                isRallyShrinkEnabled = isRallyShrinkEnabled,
                gameMode = gameMode
            )
            // Recalculate because difficulty or enabled flag changed
            newState.copy(currentHitWindow = newState.calculateHitWindow(swingSettings))
        }
    }
    
    /**
     * Updates bonus mechanics enable flags.
     * Called when user changes settings or receives them from host.
     */
    fun updateBonusMechanics(
        spinEnabled: Boolean,
        copyCatEnabled: Boolean,
        specialSquaresEnabled: Boolean,
        powerOutageEnabled: Boolean
    ) {
        _gameState.update {
            it.copy(
                bonusSpinEnabled = spinEnabled,
                bonusCopyCatEnabled = copyCatEnabled,
                bonusSpecialSquaresEnabled = specialSquaresEnabled,
                bonusPowerOutageEnabled = powerOutageEnabled
            )
        }
    }

    fun rematch(isInitiator: Boolean) {
        // Update modeStrategy to match current game mode
        // This is critical when mode is changed on the game over screen before rematch
        modeStrategy = getStrategy(_gameState.value.gameMode)
        
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
            
            // Reset modeState for new game
            val newModeState = modeStrategy.createInitialModeState()
            
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
                modeState = newModeState
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
            when (val modeState = it.modeState) {
                is ModeState.RallyState -> it.copy(modeState = modeState.copy(soundEvent = null))
                is ModeState.SoloRallyState -> it.copy(modeState = modeState.copy(soundEvent = null))
                else -> it
            }
        }
    }
    
    /**
     * Clears the Copy Cat break flash animation state.
     * Called after the UI flash animation completes to hide the X indicators.
     */
    fun clearCopyCatBreakFlash() {
        _gameState.update {
            when (val modeState = it.modeState) {
                is ModeState.RallyState -> it.copy(
                    modeState = modeState.copy(
                        isCopyCatBreakFlashing = false,
                        copyCatLeaderCells = emptyList(),
                        copyCatFollowerCells = emptyList()
                    )
                )
                is ModeState.SoloRallyState -> it.copy(
                    modeState = modeState.copy(
                        isCopyCatBreakFlashing = false,
                        copyCatBreakCells = emptyList()
                    )
                )
                else -> it
            }
        }
    }

    fun resetGame() {
        val initialModeState = modeStrategy.createInitialModeState()
        
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
                modeState = initialModeState
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
}
