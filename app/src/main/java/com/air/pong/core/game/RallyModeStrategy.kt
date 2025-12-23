package com.air.pong.core.game

/**
 * Strategy for Rally (co-op) game mode.
 * 
 * Rules:
 * - Players work together to keep the rally going
 * - 3 shared lives (either player missing loses a life)
 * - 3×3 grid per player, marked by swing type
 * - Line completions award bonuses (horizontal < vertical < diagonal)
 * - X-clear (both diagonals) = instant tier upgrade
 * - No net/out risk (cooperative mode)
 * - Hit window shrinks 50ms per tier achieved
 */
class RallyModeStrategy : GameModeStrategy {
    
    override val modeName: String = "Rally"
    override val gameMode: GameMode = GameMode.RALLY
    override val hasRisk: Boolean = false
    
    override fun createInitialModeState(): ModeState = ModeState.RallyState.initial()
    
    override fun onServe(state: GameState, swingType: SwingType, spinType: SpinType): ModeState {
        // Get rally state from modeState - Strategy pattern ensures this is always RallyState
        val rallyState = state.modeState as? ModeState.RallyState ?: return ModeState.RallyState.initial()
        
        // Serves award tier points but don't mark grid
        val minTier = rallyState.minTier
        val baseServePoints = GameEngine.getPointsForTier(minTier)
        
        // Apply momentum multiplier AND spin multiplier (if avatar currently spinning)
        val momentumMultiplier = GameEngine.getMomentumMultiplier(state.currentRallyLength)
        val currentTime = System.currentTimeMillis()
        val isAvatarSpinning = rallyState.isAvatarSpinningIndefinitely || rallyState.avatarSpinEndTime > currentTime
        val spinMultiplier = if (isAvatarSpinning) rallyState.spinMultiplierBonus else 0f
        val totalMultiplier = momentumMultiplier + spinMultiplier
        val servePoints = Math.round(baseServePoints * totalMultiplier)
        
        // Track spin on serve (for spin bonus)
        val newDistinctSpinTypes = if (spinType != SpinType.NONE) {
            rallyState.distinctSpinTypes + spinType
        } else {
            rallyState.distinctSpinTypes
        }
        
        // Calculate spin multiplier bonus (only increases for NEW unique types)
        val newSpinBonus = (newDistinctSpinTypes.size * SpinType.SPIN_BONUS_PER_TYPE).coerceAtMost(SpinType.MAX_SPIN_MULTIPLIER_BONUS)
        
        // Update avatar spin timing - ANY spin shot adds duration (not just new types)
        val newSpinEnd = if (spinType != SpinType.NONE) {
            // Any spin shot extends (or starts) spin duration
            val existingDuration = if (rallyState.avatarSpinEndTime > currentTime) {
                rallyState.avatarSpinEndTime - currentTime
            } else 0L
            currentTime + existingDuration + (SpinType.SPIN_DURATION_PER_SHOT_SECONDS * 1000L)
        } else {
            rallyState.avatarSpinEndTime
        }
        
        // Check for infinite spin (5 unique types while spinning = infinite for rest of game)
        val isInfiniteSpin = rallyState.isAvatarSpinningIndefinitely || 
            (newSpinEnd > currentTime && newDistinctSpinTypes.size >= SpinType.INFINITE_SPIN_THRESHOLD)
        
        // Detect if infinite spin was JUST achieved this serve (transition from not infinite to infinite)
        val justAchievedInfiniteSpin = !rallyState.isAvatarSpinningIndefinitely && isInfiniteSpin
        
        // Sound event for serve: only spin infinite is relevant
        val soundEvent: RallySoundEvent? = if (justAchievedInfiniteSpin) RallySoundEvent.SPIN_INFINITE else null
        
        return rallyState.copy(
            score = rallyState.score + servePoints,
            scorePoints = servePoints,
            linesJustCleared = 0, // Serves don't clear lines
            isServeHit = true,
            lastHitGridIndex = swingType.getGridIndex(), // Use swing type's grid cell for popup positioning
            distinctSpinTypes = newDistinctSpinTypes,
            spinMultiplierBonus = newSpinBonus,
            avatarSpinEndTime = if (isInfiniteSpin) Long.MAX_VALUE else newSpinEnd,
            isAvatarSpinningIndefinitely = isInfiniteSpin,
            lastBonusType = if (spinType != SpinType.NONE && newDistinctSpinTypes.size > rallyState.distinctSpinTypes.size) BonusType.SPIN else rallyState.lastBonusType,
            lastSpecialBonusTimestamp = if (spinType != SpinType.NONE && newDistinctSpinTypes.size > rallyState.distinctSpinTypes.size) currentTime else rallyState.lastSpecialBonusTimestamp,
            soundEvent = soundEvent
        )
    }
    
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean, spinType: SpinType): ModeState {
        // Get rally state from modeState - Strategy pattern ensures this is always RallyState
        val rallyState = state.modeState as? ModeState.RallyState ?: return ModeState.RallyState.initial()
        return updateGridState(
            state = rallyState, 
            swingType = swingType, 
            isOpponent = isOpponent, 
            rallyLength = state.currentRallyLength, 
            spinType = spinType,
            bonusSpinEnabled = state.bonusSpinEnabled,
            bonusCopyCatEnabled = state.bonusCopyCatEnabled,
            bonusSpecialSquaresEnabled = state.bonusSpecialSquaresEnabled,
            bonusPowerOutageEnabled = state.bonusPowerOutageEnabled
        )
    }
    
    override fun onMiss(state: GameState, whoMissed: Player, isHost: Boolean): MissResult {
        // Get rally state from modeState - Strategy pattern ensures this is always RallyState
        val rallyState = state.modeState as? ModeState.RallyState ?: return MissResult(
            updatedModeState = ModeState.RallyState.initial(),
            isGameOver = true,
            nextServer = Player.PLAYER_1,
            gameOverMessage = "Error: Invalid game state"
        )
        
        val newLives = rallyState.lives - 1
        val isGameOver = newLives <= 0
        
        // Rotate server after each miss
        val currentServer = state.servingPlayer
        val nextServer = if (currentServer == Player.PLAYER_1) Player.PLAYER_2 else Player.PLAYER_1
        
        // On miss: Reset spin session, but preserve infinite spin for rest of game
        // If infinite spin was achieved, preserve everything - otherwise reset
        val preserveInfiniteSpin = rallyState.isAvatarSpinningIndefinitely
        val preservePartnerInfiniteSpin = rallyState.isPartnerSpinningIndefinitely
        
        // Check if there was an active Copy Cat sequence that should flash on miss
        val hadActiveSequence = rallyState.copySequenceStartIndex >= 0
        
        val updatedState = rallyState.copy(
            lives = maxOf(0, newLives),
            soundEvent = null,
            // Reset spin session on miss (new rally = new spin opportunities)
            // BUT preserve distinctSpinTypes for infinite spin so speed stays fast
            distinctSpinTypes = if (preserveInfiniteSpin) rallyState.distinctSpinTypes else emptySet(),
            avatarSpinEndTime = if (preserveInfiniteSpin) Long.MAX_VALUE else 0L,
            spinMultiplierBonus = if (preserveInfiniteSpin) SpinType.MAX_SPIN_MULTIPLIER_BONUS else 0f,
            // isAvatarSpinningIndefinitely is NOT reset (preserved by copy)
            // Partner spin also resets unless they achieved infinite spin
            partnerDistinctSpinTypes = if (preservePartnerInfiniteSpin) rallyState.partnerDistinctSpinTypes else emptySet(),
            partnerAvatarSpinEndTime = if (preservePartnerInfiniteSpin) Long.MAX_VALUE else 0L,
            // isPartnerSpinningIndefinitely is NOT reset (preserved by copy)
            // === COPY CAT RESET ===
            // Clear sequence state on miss, but KEEP bonus/tier (ratchet system)
            // Also KEEP the cells for flash display, then flash will clear them after animation
            copyCatShotHistory = emptyList(),
            copySequenceStartIndex = -1,
            copyCount = 0,
            copyUniqueTypes = emptySet(),
            // Keep cells populated so flash can display them, they'll be cleared after flash
            copyCatLeaderCells = if (hadActiveSequence) rallyState.copyCatLeaderCells else emptyList(),
            copyCatFollowerCells = if (hadActiveSequence) rallyState.copyCatFollowerCells else emptyList(),
            copyCatNextExpectedCell = -1,
            isLocalPlayerLeader = false,
            isCopyCatBreakFlashing = hadActiveSequence,  // Flash if there was an active sequence
            // copyCatMultiplierBonus and copyCatTierAchieved are preserved (ratchet)
            // Reset bonus display on miss (unless infinite spin achieved)
            lastBonusType = if (preserveInfiniteSpin) rallyState.lastBonusType else null,
            lastSpecialBonusTimestamp = if (preserveInfiniteSpin) rallyState.lastSpecialBonusTimestamp else 0L,
            // === POWER OUTAGE RESET ===
            // On miss: reset cell hit counts and restore power (but no bonus awarded)
            cellHitCounts = List(9) { 0 },
            isPowerOutage = false,
            outageRecoveryUniqueCells = emptySet()
            // powerOutagesClearedCount and powerOutageWindowBonus are preserved (earned bonuses)
        )
        
        return MissResult(
            updatedModeState = updatedState,
            isGameOver = isGameOver,
            nextServer = nextServer,
            gameOverMessage = if (isGameOver) "Game Over! Score: ${rallyState.score}" else null
        )
    }
    
    override fun getHitWindowShrink(state: GameState): Long {
        if (!state.isRallyShrinkEnabled || state.currentRallyLength <= 1) return 0L
        
        val rallyState = state.modeState as? ModeState.RallyState ?: return 0L
        
        // Rally Mode: 50ms shrink per tier achieved globally
        return rallyState.globalHighestTier * GameEngine.RALLY_TIER_SHRINK_MS
    }
    
    override fun getDisplayScore(state: GameState): Int? {
        val rallyState = state.modeState as? ModeState.RallyState ?: return null
        return rallyState.score
    }
    
    override fun getLives(state: GameState): Int? {
        val rallyState = state.modeState as? ModeState.RallyState ?: return null
        return rallyState.lives
    }
    
    /**
     * Handles special square effects when a player swings but misses the timing window.
     * Even on early/late misses, landmine and banana effects should still apply.
     */
    override fun onMissedSwing(state: GameState, swingType: SwingType): MissedSwingResult {
        val rallyState = state.modeState as? ModeState.RallyState 
            ?: return MissedSwingResult(state.modeState)
        
        // Check if special squares are enabled
        if (!state.bonusSpecialSquaresEnabled) {
            return MissedSwingResult(rallyState)
        }
        
        val gridIndex = swingType.getGridIndex()
        val specialSquareHit = rallyState.specialSquares[gridIndex]
        
        return when (specialSquareHit) {
            SpecialSquareType.LANDMINE -> {
                // Landmine effect: Break infinite spin, play explosion sound
                // Note: Life loss is handled by the normal miss flow
                val updatedState = rallyState.copy(
                    lastHitGridIndex = gridIndex,
                    lastSpecialSquareHit = SpecialSquareType.LANDMINE,
                    // Break infinite spin - landmine is severe penalty
                    isAvatarSpinningIndefinitely = false,
                    distinctSpinTypes = emptySet(),
                    spinMultiplierBonus = 0f,
                    avatarSpinEndTime = 0L
                )
                MissedSwingResult(updatedState, RallySoundEvent.LANDMINE_HIT)
            }
            SpecialSquareType.BANANA -> {
                // Banana effect: Deduct points (clamped to 0), reduce golden multiplier
                val penalty = SpecialSquareType.BANANA_PENALTY_PER_TIER * 
                    (rallyState.cellTiers.minOrNull() ?: 1).coerceAtLeast(1)
                val newScore = (rallyState.score - penalty).coerceAtLeast(0)
                val newGoldenMultiplier = (rallyState.goldenMultiplierBonus - 0.1f).coerceAtLeast(0f)
                
                val updatedState = rallyState.copy(
                    score = newScore,
                    lastHitGridIndex = gridIndex,
                    lastSpecialSquareHit = SpecialSquareType.BANANA,
                    lastSpecialSquarePenalty = penalty,
                    goldenMultiplierBonus = newGoldenMultiplier,
                    penaltyHitThisTier = true
                )
                MissedSwingResult(updatedState, RallySoundEvent.BANANA_HIT)
            }
            else -> {
                // No special square at this cell, or golden (no penalty for golden)
                MissedSwingResult(rallyState)
            }
        }
    }
    
    /**
     * Updates the grid state after a hit.
     * Handles grid marking, line completion, tier upgrades, and bonus points.
     * Applies momentum multiplier based on current rally length.
     */
    private fun updateGridState(
        state: ModeState.RallyState,
        swingType: SwingType,
        isOpponent: Boolean,
        rallyLength: Int,
        spinType: SpinType,
        bonusSpinEnabled: Boolean = true,
        bonusCopyCatEnabled: Boolean = true,
        bonusSpecialSquaresEnabled: Boolean = true,
        bonusPowerOutageEnabled: Boolean = true
    ): ModeState.RallyState {
        val gridIndex = swingType.getGridIndex()
        
        // === COPY CAT PROCESSING ===
        // Only process if CopyCat bonus is enabled
        // isOpponent means it's the partner's hit (not local player)
        val copyCatUpdate = if (bonusCopyCatEnabled) {
            processCopyCat(state, swingType, isLocalPlayer = !isOpponent)
        } else {
            // Return a no-op update that preserves history but doesn't track sequences
            CopyCatUpdate(shotHistory = state.copyCatShotHistory)
        }

        
        // Get the correct grid data based on whether it's opponent or local
        val currentGrid = if (isOpponent) state.opponentGrid else state.grid
        val currentTiers = if (isOpponent) state.opponentCellTiers else state.cellTiers
        val currentHighestTier = if (isOpponent) state.opponentHighestTier else state.highestTier
        val currentLinesCompleted = if (isOpponent) state.opponentLinesCompleted else state.linesCompleted
        
        // === SPECIAL SQUARE HIT LOGIC ===
        // Special squares are ONLY tracked for the LOCAL player.
        // When isOpponent=true (partner's hit on their grid), we skip all special square logic
        // because their special squares are tracked independently on their device.
        // Also skip entirely if bonus is disabled.
        val specialSquareHit = if (isOpponent || !bonusSpecialSquaresEnabled) null else state.specialSquares[gridIndex]
        var newSpecialSquares = if (bonusSpecialSquaresEnabled) state.specialSquares.toMutableMap() else mutableMapOf()
        var hasShield = state.hasShield
        var goldenMultiplierBonus = state.goldenMultiplierBonus
        var landmineTriggered = false
        var lastSpecialSquareHit: SpecialSquareType? = null
        var bananaHitPenalty = 0
        // Track whether any penalty was hit this tier (for shield eligibility check)
        var penaltyHitThisTier = state.penaltyHitThisTier
        
        // Check for landmine FIRST - if triggered without shield, return early
        // Do NOT mark the cell, do NOT award points
        var shieldBroken = false
        if (specialSquareHit == SpecialSquareType.LANDMINE) {
            if (hasShield) {
                // Shield absorbs the landmine - consume shield
                hasShield = false
                shieldBroken = true
                // Mark that a penalty was hit - disqualifies from shield on tier clear
                penaltyHitThisTier = true
                // Landmine is neutralized but PERSISTS (can hit again if shield earned again)
                // Normal hit continues below
            } else {
                // Landmine kills the rally instantly - return early
                // Do NOT mark cell, do NOT award points
                // Mark penalty hit so shield won't be awarded when this grid eventually clears
                // Also break infinite spin if earned - landmine is severe penalty
                return state.copy(
                    lastHitGridIndex = gridIndex,
                    lastSpecialSquareHit = SpecialSquareType.LANDMINE,
                    landmineTriggered = true,
                    hasShield = hasShield,
                    soundEvent = RallySoundEvent.LANDMINE_HIT,
                    penaltyHitThisTier = true,
                    // Break infinite spin
                    isAvatarSpinningIndefinitely = false,
                    distinctSpinTypes = emptySet(),
                    spinMultiplierBonus = 0f,
                    avatarSpinEndTime = 0L
                )
            }
        }
        
        // 1. Mark Grid Cell (only if not killed by landmine)
        val newGrid = currentGrid.toMutableList()
        newGrid[gridIndex] = true
        
        // 2. Award points based on the cell's current tier
        val cellTier = currentTiers[gridIndex]
        var pointsPerHit = GameEngine.getPointsForTier(cellTier)
        
        when (specialSquareHit) {
            SpecialSquareType.BANANA -> {
                // Banana penalty: deduct 10 * minTier points
                // NOTE: Banana PERSISTS - do NOT remove it. Player can hit it multiple times.
                val penalty = SpecialSquareType.BANANA_PENALTY_PER_TIER * (currentTiers.minOrNull() ?: 1).coerceAtLeast(1)
                pointsPerHit = -penalty
                lastSpecialSquareHit = SpecialSquareType.BANANA
                bananaHitPenalty = penalty
                // Mark that a penalty was hit - disqualifies from shield on tier clear
                penaltyHitThisTier = true
                // Reduce golden multiplier by 0.1 (can't go below 0)
                goldenMultiplierBonus = (goldenMultiplierBonus - 0.1f).coerceAtLeast(0f)
                // Do NOT remove: newSpecialSquares.remove(gridIndex)
            }
            SpecialSquareType.GOLDEN -> {
                // Golden bonus: 3x cell points
                // Golden reverts to normal after the FIRST hit (not a repeating bonus)
                pointsPerHit *= SpecialSquareType.GOLDEN_POINTS_MULTIPLIER
                lastSpecialSquareHit = SpecialSquareType.GOLDEN
                newSpecialSquares.remove(gridIndex)
            }
            SpecialSquareType.LANDMINE -> {
                // Already handled above - if we get here, shield absorbed it
                // Normal hit continues
            }
            null -> { /* No special square */ }
        }
        
        var pointsToAdd = pointsPerHit
        
        // 3. Check for Line Completions
        val newLinesCompleted = currentLinesCompleted.toMutableList()
        val newTiers = currentTiers.toMutableList()
        var linesCompletedThisTurn = 0
        val linesCompletedIndices = mutableListOf<Int>()
        
        // Find the minimum tier across all cells
        val minTier = currentTiers.minOrNull() ?: 0
        val nextTier = minTier + 1
        
        // Collect all cells to clear
        val cellsToClear = mutableSetOf<Int>()
        
        LINES.forEachIndexed { lineIndex, indices ->
            val isLineComplete = indices.all { newGrid[it] }
            if (isLineComplete && !currentLinesCompleted[lineIndex]) {
                // New Line Completed!
                newLinesCompleted[lineIndex] = true
                linesCompletedThisTurn++
                linesCompletedIndices.add(lineIndex)
                
                // Determine bonus based on line type
                val bonus = when {
                    lineIndex >= 6 -> GameEngine.getDiagonalBonus(minTier)
                    lineIndex >= 3 -> GameEngine.getVerticalBonus(minTier)
                    else -> GameEngine.getHorizontalBonus(minTier)
                }
                pointsToAdd += bonus
                
                // === GOLDEN LINE CLEAR BONUS ===
                // Check if golden square was in this line (using original specialSquares, not newSpecialSquares)
                val goldenInLine = indices.any { state.specialSquares[it] == SpecialSquareType.GOLDEN }
                if (goldenInLine) {
                    // Award persistent multiplier based on lines cleared with golden
                    val goldenBonus = when (linesCompletedThisTurn) {
                        1 -> 0.1f
                        2 -> 0.2f // Additional (total 0.3)
                        else -> 0.2f // Additional (cap at 0.5)
                    }
                    goldenMultiplierBonus = (goldenMultiplierBonus + goldenBonus).coerceAtMost(SpecialSquareType.MAX_GOLDEN_MULTIPLIER_BONUS)
                }
                
                // Mark cells to clear
                indices.forEach { cellIndex -> cellsToClear.add(cellIndex) }
            }
        }
        
        // Clear marked cells and upgrade if eligible
        cellsToClear.forEach { cellIndex ->
            newGrid[cellIndex] = false
            // Triangular numbers have no limit, so always allow tier upgrade
            if (newTiers[cellIndex] == minTier) {
                newTiers[cellIndex] = nextTier
            }
        }
        
        // 4. Double-line clear bonus: When 2+ lines completed, award up to 2 bonus cells
        if (linesCompletedThisTurn >= 2) {
            val availableBonusCells = newTiers.indices.filter { idx ->
                !newGrid[idx] && newTiers[idx] == minTier
            }
            
            val bonusCellsToAward = availableBonusCells.shuffled().take(2)
            bonusCellsToAward.forEach { cellIndex ->
                val cellPoints = GameEngine.getPointsForTier(newTiers[cellIndex])
                pointsToAdd += cellPoints
                // Triangular numbers have no limit, so always allow tier upgrade
                if (newTiers[cellIndex] == minTier) {
                    newTiers[cellIndex] = nextTier
                }
            }
        }
        
        // Reset line completion tracking for cleared lines
        LINES.forEachIndexed { lineIndex, indices ->
            if (indices.any { !newGrid[it] }) {
                newLinesCompleted[lineIndex] = false
            }
        }
        
        // 5. Check for tier upgrade and award extra life
        var newLives = state.lives
        var newHighestTier = currentHighestTier
        var tierUpgraded = false
        var newGridUpgradesEarned = state.gridUpgradesEarned
        var newCopyCat9Completions = state.copyCat9Completions
        
        // === COPY CAT 9/9 EXTRA LIFE ===
        // Award an extra life for completing a full 9/9 Copy Cat sequence
        // Check that the sequence just reached 9 (previous count < 9)
        if (copyCatUpdate.copyCount >= 9 && state.copyCount < 9 && !copyCatUpdate.patternBroken) {
            newLives++
            newCopyCat9Completions++
        }
        
        // X-Clear: Both diagonals completed this turn
        val bothDiagonalsCompleted = linesCompletedIndices.contains(6) && linesCompletedIndices.contains(7)
        
        // Check if tier should upgrade
        // Exclude penalty squares from completion check - they persist and can't be hit for tier upgrade
        val allCellsUpgraded = newTiers.indices.all { index ->
            newTiers[index] >= nextTier || 
            newSpecialSquares[index] == SpecialSquareType.BANANA ||
            newSpecialSquares[index] == SpecialSquareType.LANDMINE
        }
        val shouldUpgradeTier = allCellsUpgraded || bothDiagonalsCompleted
        
        // Use GLOBAL highest tier
        val globalHighestTier = maxOf(state.highestTier, state.opponentHighestTier)
        
        if (shouldUpgradeTier) {
            newHighestTier = nextTier
            tierUpgraded = true
            
            // === SHIELD EARNING (LOCAL PLAYER ONLY) ===
            // Award shield if clearing grid with unhit penalty square (Banana or Landmine)
            // Requirement: penalty must exist AND player must NOT have hit it during this tier
            // Only for local player - opponent's shields are tracked on their device
            if (!isOpponent) {
                val hadPenalty = newSpecialSquares.values.any { it == SpecialSquareType.BANANA || it == SpecialSquareType.LANDMINE }
                if (hadPenalty && !penaltyHitThisTier) {
                    hasShield = true
                }
                // Reset penalty hit tracking for new tier
                penaltyHitThisTier = false
            }
            
            // Reset the entire grid
            for (i in 0 until 9) {
                newGrid[i] = false
                newTiers[i] = nextTier
            }
            for (i in 0 until 8) {
                newLinesCompleted[i] = false
            }
            
            // Extra life for first global completion of this tier
            if (nextTier > globalHighestTier) {
                newLives++
                newGridUpgradesEarned++
            }
            
            // === SPAWN SPECIAL SQUARES FOR NEW TIER (LOCAL PLAYER ONLY) ===
            // Only spawn for local player tier upgrade - opponent's special squares
            // are independent and tracked on their device
            // Also only spawn if special squares bonus is enabled
            if (!isOpponent && bonusSpecialSquaresEnabled) {
                newSpecialSquares = spawnSpecialSquares(nextTier).toMutableMap()
            }
        }
        
        // Sound event will be determined in local player branch based on spin, copy cat, and special square states
        
        // Apply momentum multiplier AND spin multiplier (if avatar currently spinning)
        val momentumMultiplier = GameEngine.getMomentumMultiplier(rallyLength)
        val currentTime = System.currentTimeMillis()
        val isAvatarSpinning = state.isAvatarSpinningIndefinitely || state.avatarSpinEndTime > currentTime
        val spinMultiplier = if (isAvatarSpinning) state.spinMultiplierBonus else 0f
        val totalMultiplier = momentumMultiplier + spinMultiplier
        val multipliedPoints = Math.round(pointsToAdd * totalMultiplier)
        
        return if (isOpponent) {
            // Track partner's spin for avatar animation - track UNIQUE spin types
            val newPartnerDistinctSpinTypes = if (spinType != SpinType.NONE) {
                state.partnerDistinctSpinTypes + spinType
            } else {
                state.partnerDistinctSpinTypes
            }
            
            // Update partner avatar spin timing - ANY spin shot adds duration
            val newPartnerSpinEnd = if (spinType != SpinType.NONE) {
                val existingDuration = if (state.partnerAvatarSpinEndTime > currentTime) {
                    state.partnerAvatarSpinEndTime - currentTime
                } else 0L
                currentTime + existingDuration + (SpinType.SPIN_DURATION_PER_SHOT_SECONDS * 1000L)
            } else {
                state.partnerAvatarSpinEndTime
            }
            
            // Check for partner infinite spin (5 unique types = infinite)
            val isPartnerInfiniteSpin = state.isPartnerSpinningIndefinitely || 
                (newPartnerSpinEnd > currentTime && newPartnerDistinctSpinTypes.size >= SpinType.INFINITE_SPIN_THRESHOLD)
            
            state.copy(
                opponentGrid = newGrid,
                opponentLinesCompleted = newLinesCompleted,
                opponentCellTiers = newTiers,
                opponentHighestTier = newHighestTier,
                score = (state.score + multipliedPoints).coerceAtLeast(0),
                lives = newLives,
                scorePoints = multipliedPoints,
                linesJustCleared = linesCompletedThisTurn,
                partnerTotalLinesCleared = state.partnerTotalLinesCleared + linesCompletedThisTurn,
                lastHitGridIndex = gridIndex,
                // Partner spin tracking
                partnerDistinctSpinTypes = newPartnerDistinctSpinTypes,
                partnerAvatarSpinEndTime = if (isPartnerInfiniteSpin) Long.MAX_VALUE else newPartnerSpinEnd,
                isPartnerSpinningIndefinitely = isPartnerInfiniteSpin,
                // Copy Cat tracking
                copyCatShotHistory = copyCatUpdate.shotHistory,
                copySequenceStartIndex = if (copyCatUpdate.patternBroken) -1 else copyCatUpdate.sequenceStartIndex,
                copyCount = if (copyCatUpdate.patternBroken) 0 else copyCatUpdate.copyCount,
                copyUniqueTypes = if (copyCatUpdate.patternBroken) emptySet() else copyCatUpdate.uniqueTypes,
                copyCatLeaderCells = if (copyCatUpdate.patternBroken) state.copyCatLeaderCells else copyCatUpdate.leaderCells,
                copyCatFollowerCells = if (copyCatUpdate.patternBroken) state.copyCatFollowerCells else copyCatUpdate.followerCells,
                copyCatNextExpectedCell = if (copyCatUpdate.patternBroken) -1 else copyCatUpdate.nextExpectedCell,
                isLocalPlayerLeader = if (copyCatUpdate.patternBroken) state.isLocalPlayerLeader else copyCatUpdate.isLocalPlayerLeader,
                copyCatMultiplierBonus = state.copyCatMultiplierBonus + copyCatUpdate.bonusAwarded,
                copyCatTierAchieved = maxOf(state.copyCatTierAchieved, copyCatUpdate.tierAwarded),
                isCopyCatBreakFlashing = copyCatUpdate.patternBroken,
                // Special Squares
                specialSquares = newSpecialSquares,
                hasShield = hasShield,
                goldenMultiplierBonus = goldenMultiplierBonus,
                lastSpecialSquareHit = lastSpecialSquareHit,
                lastSpecialSquarePenalty = bananaHitPenalty,
                landmineTriggered = landmineTriggered,
                penaltyHitThisTier = penaltyHitThisTier
            )
        } else {
            // Track spin bonus for local player (only if spin bonus is enabled)
            val newDistinctSpinTypes = if (bonusSpinEnabled && spinType != SpinType.NONE) {
                state.distinctSpinTypes + spinType
            } else {
                if (bonusSpinEnabled) state.distinctSpinTypes else emptySet()
            }
            val spinBonusChanged = bonusSpinEnabled && newDistinctSpinTypes.size > state.distinctSpinTypes.size
            val newSpinBonus = if (bonusSpinEnabled) {
                (newDistinctSpinTypes.size * SpinType.SPIN_BONUS_PER_TYPE).coerceAtMost(SpinType.MAX_SPIN_MULTIPLIER_BONUS)
            } else 0f
            
            // Update avatar spin timing - ANY spin shot adds duration (not just new types)
            // Only process if spin bonus is enabled
            val newSpinEnd = if (bonusSpinEnabled && spinType != SpinType.NONE) {
                // Any spin shot extends (or starts) spin duration
                val existingDuration = if (state.avatarSpinEndTime > currentTime) {
                    state.avatarSpinEndTime - currentTime
                } else 0L
                currentTime + existingDuration + (SpinType.SPIN_DURATION_PER_SHOT_SECONDS * 1000L)
            } else {
                if (bonusSpinEnabled) state.avatarSpinEndTime else 0L
            }
            
            // Check for infinite spin (5 unique types while spinning = infinite for rest of game)
            // Only applicable if spin bonus is enabled
            val isInfiniteSpin = bonusSpinEnabled && (state.isAvatarSpinningIndefinitely || 
                (newSpinEnd > currentTime && newDistinctSpinTypes.size >= SpinType.INFINITE_SPIN_THRESHOLD))
            
            // Detect if infinite spin was JUST achieved this hit (transition from not infinite to infinite)
            val justAchievedInfiniteSpin = bonusSpinEnabled && !state.isAvatarSpinningIndefinitely && isInfiniteSpin
            
            // === POWER OUTAGE PROCESSING ===
            // Only process if power outage bonus is enabled
            // Update cell hit counts (only for local player)
            val newCellHitCounts = if (bonusPowerOutageEnabled) {
                state.cellHitCounts.toMutableList().apply {
                    this[gridIndex] = this[gridIndex] + 1
                }
            } else {
                state.cellHitCounts // Keep unchanged if disabled
            }
            
            // Determine power outage state (only if enabled)
            var newIsPowerOutage = state.isPowerOutage
            var newOutageRecoveryUniqueCells = state.outageRecoveryUniqueCells
            var newPowerOutagesClearedCount = state.powerOutagesClearedCount
            var newPowerOutageWindowBonus = state.powerOutageWindowBonus
            var justTriggeredOutage = false
            var justRestoredPower = false
            
            if (bonusPowerOutageEnabled) {
                if (state.isPowerOutage) {
                    // Currently in outage - track recovery progress
                    if (gridIndex in state.outageRecoveryUniqueCells) {
                        // Duplicate cell! Reset recovery progress (but stay in outage)
                        newOutageRecoveryUniqueCells = emptySet()
                    } else {
                        // New unique cell - add to recovery set
                        newOutageRecoveryUniqueCells = state.outageRecoveryUniqueCells + gridIndex
                        
                        // Check if recovery complete
                        if (newOutageRecoveryUniqueCells.size >= PowerOutageConstants.RECOVERY_UNIQUE_CELLS) {
                            // Power restored!
                            newIsPowerOutage = false
                            newOutageRecoveryUniqueCells = emptySet()
                            (newCellHitCounts as? MutableList)?.fill(0) // Reset all cell counts
                            newPowerOutagesClearedCount++
                            newPowerOutageWindowBonus += PowerOutageConstants.WINDOW_BONUS_MS
                            justRestoredPower = true
                        }
                    }
                } else {
                    // Not in outage - check if this hit triggers one
                    if (newCellHitCounts[gridIndex] >= PowerOutageConstants.TRIGGER_HITS) {
                        newIsPowerOutage = true
                        newOutageRecoveryUniqueCells = emptySet()
                        justTriggeredOutage = true
                    }
                }
            }
            
            // Determine sound event with priority:
            // 1. POWER_OUTAGE_RESTORE (just recovered from outage with 5 unique hits)
            // 2. POWER_OUTAGE_START (just triggered outage)
            // 3. SPIN_INFINITE (just achieved infinite spin)
            // 4. SHIELD_BREAK (shield consumed by landmine)
            // 5. GRID_COMPLETE (tier upgrade)
            // 6. COPY_CAT_BONUS (new tier awarded, or 9/9 completion which always plays)
            // 7. COPY_CAT_BREAK (pattern broken with 4+ hits)
            // 8. BANANA_HIT
            // 9. LINE_COMPLETE
            val soundEvent: RallySoundEvent? = when {
                justRestoredPower -> RallySoundEvent.POWER_OUTAGE_RESTORE
                justTriggeredOutage -> RallySoundEvent.POWER_OUTAGE_START
                justAchievedInfiniteSpin -> RallySoundEvent.SPIN_INFINITE
                shieldBroken -> RallySoundEvent.SHIELD_BREAK
                tierUpgraded -> RallySoundEvent.GRID_COMPLETE
                // Copy cat bonus: play for new tier OR for 9/9 (always play 9/9 since it awards life)
                copyCatUpdate.tierAwarded > 0 || copyCatUpdate.copyCount >= 9 -> RallySoundEvent.COPY_CAT_BONUS
                // Copy cat break: only play if pattern was 4+ hits long
                copyCatUpdate.patternBroken && state.copyCount >= 4 -> RallySoundEvent.COPY_CAT_BREAK
                lastSpecialSquareHit == SpecialSquareType.BANANA -> RallySoundEvent.BANANA_HIT
                linesCompletedThisTurn > 0 -> RallySoundEvent.LINE_COMPLETE
                else -> null
            }
            
            // Determine bonus type for display
            // Only track SPIN/COPYCAT/GOLD for dynamic label (these affect multiplier)
            // TIER and LINE are excluded - they don't contribute to the multiplier display
            // Fallback to null so old bonuses don't persist
            val goldenBonusChanged = goldenMultiplierBonus > state.goldenMultiplierBonus
            val newBonusType: BonusType? = when {
                copyCatUpdate.tierAwarded > 0 -> BonusType.COPYCAT
                goldenBonusChanged -> BonusType.GOLD
                spinBonusChanged -> BonusType.SPIN
                else -> null  // Don't preserve old bonus type
            }
            
            // Update timestamp only when a special bonus is actually earned
            val newSpecialBonusTimestamp = if (newBonusType != null) {
                currentTime
            } else {
                state.lastSpecialBonusTimestamp
            }
            
            state.copy(
                grid = newGrid,
                linesCompleted = newLinesCompleted,
                cellTiers = newTiers,
                highestTier = newHighestTier,
                score = (state.score + multipliedPoints).coerceAtLeast(0),
                lives = newLives,
                scorePoints = multipliedPoints,
                linesJustCleared = linesCompletedThisTurn,
                soundEvent = soundEvent,
                lastHitGridIndex = gridIndex,
                isServeHit = false,
                totalLinesCleared = state.totalLinesCleared + linesCompletedThisTurn,
                distinctSpinTypes = newDistinctSpinTypes,
                spinMultiplierBonus = newSpinBonus,
                avatarSpinEndTime = if (isInfiniteSpin) Long.MAX_VALUE else newSpinEnd,
                isAvatarSpinningIndefinitely = isInfiniteSpin,
                lastBonusType = newBonusType ?: state.lastBonusType,
                lastSpecialBonusTimestamp = newSpecialBonusTimestamp,
                // Copy Cat tracking
                copyCatShotHistory = copyCatUpdate.shotHistory,
                copySequenceStartIndex = if (copyCatUpdate.patternBroken) -1 else copyCatUpdate.sequenceStartIndex,
                copyCount = if (copyCatUpdate.patternBroken) 0 else copyCatUpdate.copyCount,
                copyUniqueTypes = if (copyCatUpdate.patternBroken) emptySet() else copyCatUpdate.uniqueTypes,
                copyCatLeaderCells = if (copyCatUpdate.patternBroken) state.copyCatLeaderCells else copyCatUpdate.leaderCells,
                copyCatFollowerCells = if (copyCatUpdate.patternBroken) state.copyCatFollowerCells else copyCatUpdate.followerCells,
                copyCatNextExpectedCell = if (copyCatUpdate.patternBroken) -1 else copyCatUpdate.nextExpectedCell,
                isLocalPlayerLeader = if (copyCatUpdate.patternBroken) state.isLocalPlayerLeader else copyCatUpdate.isLocalPlayerLeader,
                copyCatMultiplierBonus = state.copyCatMultiplierBonus + copyCatUpdate.bonusAwarded,
                copyCatTierAchieved = maxOf(state.copyCatTierAchieved, copyCatUpdate.tierAwarded),
                isCopyCatBreakFlashing = copyCatUpdate.patternBroken,
                // Special Squares
                specialSquares = newSpecialSquares,
                hasShield = hasShield,
                goldenMultiplierBonus = goldenMultiplierBonus,
                lastSpecialSquareHit = lastSpecialSquareHit,
                lastSpecialSquarePenalty = bananaHitPenalty,
                landmineTriggered = landmineTriggered,
                penaltyHitThisTier = penaltyHitThisTier,
                // Power Outage tracking
                cellHitCounts = newCellHitCounts,
                isPowerOutage = newIsPowerOutage,
                outageRecoveryUniqueCells = newOutageRecoveryUniqueCells,
                powerOutagesClearedCount = newPowerOutagesClearedCount,
                powerOutageWindowBonus = newPowerOutageWindowBonus,
                // Bonus tracking
                gridUpgradesEarned = newGridUpgradesEarned,
                copyCat9Completions = newCopyCat9Completions
            )
        }
    }
    
    companion object {
        // Lines: Rows (0-2, 3-5, 6-8), Cols (036, 147, 258), Diagonals (048, 246)
        private val LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
            listOf(0, 4, 8), listOf(2, 4, 6)                   // Diagonals (indices 6, 7)
        )
        
        // Grid cells organized by row and column for spatial constraints
        private val ROWS = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8))
        private val COLS = listOf(listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8))
        
        /**
         * Spawns special squares for a new tier using categorical distribution.
         * 
         * Categories (single roll):
         * - 20% Golden: Spawn 1 golden square
         * - 40% Penalty: Spawn penalty square(s)
         * - 40% Normal: No special squares
         * 
         * Within Penalty (sub-roll):
         * - 50% Banana only
         * - 25% Landmine only  
         * - 25% Both (must share row or column)
         * 
         * @param tier The tier being upgraded to (1+)
         * @return Map of grid index to special square type
         */
        fun spawnSpecialSquares(tier: Int): Map<Int, SpecialSquareType> {
            // No specials on first grid (tier 0)
            if (tier <= 0) return emptyMap()
            
            val result = mutableMapOf<Int, SpecialSquareType>()
            val random = kotlin.random.Random
            
            // Single categorical roll for spawn type
            val categoryRoll = random.nextFloat()
            
            when {
                categoryRoll < SpecialSquareType.GOLDEN_CATEGORY_CHANCE -> {
                    // Golden category (1/3)
                    val cell = if (random.nextFloat() < SpecialSquareType.GOLDEN_CENTER_CHANCE) {
                        4 // Center cell
                    } else {
                        (0..8).filter { it != 4 }.random(random)
                    }
                    result[cell] = SpecialSquareType.GOLDEN
                }
                categoryRoll < SpecialSquareType.GOLDEN_CATEGORY_CHANCE + SpecialSquareType.PENALTY_CATEGORY_CHANCE -> {
                    // Penalty category (1/3)
                    val penaltyRoll = random.nextFloat()
                    
                    when {
                        penaltyRoll < SpecialSquareType.PENALTY_BANANA_ONLY_CHANCE -> {
                            // Banana only (50% of penalty = 16.7% overall)
                            result[(0..8).random(random)] = SpecialSquareType.BANANA
                        }
                        penaltyRoll < SpecialSquareType.PENALTY_BANANA_ONLY_CHANCE + SpecialSquareType.PENALTY_LANDMINE_ONLY_CHANCE -> {
                            // Landmine only (25% of penalty = 8.3% overall)
                            result[(0..8).random(random)] = SpecialSquareType.LANDMINE
                        }
                        else -> {
                            // Both (25% of penalty = 8.3% overall)
                            val useRow = random.nextBoolean()
                            val lineGroup = if (useRow) ROWS else COLS
                            val selectedLine = lineGroup.random(random)
                            val shuffled = selectedLine.shuffled(random)
                            result[shuffled[0]] = SpecialSquareType.BANANA
                            result[shuffled[1]] = SpecialSquareType.LANDMINE
                        }
                    }
                }
                // else: Normal category (1/3) - no special squares
            }
            
            return result
        }
        
        /**
         * Process Copy Cat pattern detection for a new shot.
         * Updates shot history and detects/tracks copy sequences.
         * 
         * @param state Current RallyState
         * @param swingType The swing type just hit
         * @param isLocalPlayer True if local player hit, false if partner
         * @return Updated Copy Cat fields as a CopyCatUpdate
         */
        fun processCopyCat(
            state: ModeState.RallyState,
            swingType: SwingType,
            isLocalPlayer: Boolean
        ): CopyCatUpdate {
            val gridIndex = swingType.getGridIndex()
            
            // Add new shot to history
            val newHistory = state.copyCatShotHistory + (swingType to isLocalPlayer)
            
            // If no active sequence, check if we can start one
            if (state.copySequenceStartIndex == -1) {
                // Need at least 2 shots to detect a pattern start
                if (newHistory.size >= 2) {
                    val last = newHistory.last()
                    val prev = newHistory[newHistory.size - 2]
                    
                    // Pattern starts when consecutive shots from different players have same type
                    if (last.first == prev.first && last.second != prev.second) {
                        // Start of sequence! First player = leader
                        val leaderIsLocal = prev.second
                        return CopyCatUpdate(
                            shotHistory = newHistory,
                            sequenceStartIndex = newHistory.size - 2,
                            copyCount = 1,
                            uniqueTypes = setOf(swingType),
                            leaderCells = if (leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                            followerCells = if (!leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                            nextExpectedCell = -1, // Will be set on leader's next hit
                            isLocalPlayerLeader = leaderIsLocal,
                            tierAwarded = 0,
                            bonusAwarded = 0f
                        )
                    }
                }
                // No pattern started
                return CopyCatUpdate(shotHistory = newHistory)
            }
            
            // Active sequence - check if this continues or breaks the pattern
            val isLeaderHitting = state.isLocalPlayerLeader == isLocalPlayer
            
            if (isLeaderHitting) {
                // Leader hit - update next expected cell for follower
                val newLeaderCells = state.copyCatLeaderCells + (gridIndex to (state.copyCount + 1))
                return CopyCatUpdate(
                    shotHistory = newHistory,
                    sequenceStartIndex = state.copySequenceStartIndex,
                    copyCount = state.copyCount,
                    uniqueTypes = state.copyUniqueTypes,
                    leaderCells = if (state.isLocalPlayerLeader) newLeaderCells else state.copyCatLeaderCells,
                    followerCells = state.copyCatFollowerCells,
                    nextExpectedCell = gridIndex,
                    isLocalPlayerLeader = state.isLocalPlayerLeader,
                    tierAwarded = 0,
                    bonusAwarded = 0f
                )
            } else {
                // Follower hit - check if it matches the expected pattern
                val expectedIndex = state.copyCatNextExpectedCell
                
                if (expectedIndex != -1 && gridIndex == expectedIndex) {
                    // Match! Increment copy count
                    val newCopyCount = state.copyCount + 1
                    val newUniqueTypes = state.copyUniqueTypes + swingType
                    
                    // CHECK: Is the pattern still viable for reaching minimum tier?
                    // If we have too many repeats without enough unique types, break the pattern
                    if (!CopyCatConstants.canReachMinimumTier(newCopyCount, newUniqueTypes.size)) {
                        // Pattern is no longer viable - too many repeats! Break it.
                        return createPatternBreak(newHistory, swingType, gridIndex)
                    }
                    
                    // Check for tier bonus
                    val newTier = CopyCatConstants.calculateCopyCatTier(newCopyCount, newUniqueTypes.size)
                    val tierAwarded = if (newTier > state.copyCatTierAchieved) newTier else 0
                    val bonusToAdd = if (tierAwarded > 0) {
                        CopyCatConstants.multiplierForTier(tierAwarded) - 
                            CopyCatConstants.multiplierForTier(state.copyCatTierAchieved)
                    } else 0f
                    
                    val newFollowerCells = state.copyCatFollowerCells + (gridIndex to newCopyCount)
                    
                    return CopyCatUpdate(
                        shotHistory = newHistory,
                        sequenceStartIndex = state.copySequenceStartIndex,
                        copyCount = newCopyCount,
                        uniqueTypes = newUniqueTypes,
                        leaderCells = state.copyCatLeaderCells,
                        followerCells = if (!state.isLocalPlayerLeader) newFollowerCells else state.copyCatFollowerCells,
                        nextExpectedCell = -1, // Wait for leader's next hit
                        isLocalPlayerLeader = state.isLocalPlayerLeader,
                        tierAwarded = tierAwarded,
                        bonusAwarded = bonusToAdd
                    )
                } else {
                    // Mismatch! Break the sequence
                    // Check if we can immediately start a new sequence
                    if (newHistory.size >= 2) {
                        val last = newHistory.last()
                        val prev = newHistory[newHistory.size - 2]
                        
                        if (last.first == prev.first && last.second != prev.second) {
                            // Immediate new sequence start
                            val leaderIsLocal = prev.second
                            return CopyCatUpdate(
                                shotHistory = newHistory,
                                sequenceStartIndex = newHistory.size - 2,
                                copyCount = 1,
                                uniqueTypes = setOf(swingType),
                                leaderCells = if (leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                                followerCells = if (!leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                                nextExpectedCell = -1,
                                isLocalPlayerLeader = leaderIsLocal,
                                tierAwarded = 0,
                                bonusAwarded = 0f,
                                patternBroken = true
                            )
                        }
                    }
                    // Pattern broken, no new start
                    return CopyCatUpdate(
                        shotHistory = newHistory,
                        patternBroken = true
                    )
                }
            }
        }
        
        /**
         * Create a pattern break result, checking if we can immediately start a new sequence.
         */
        private fun createPatternBreak(
            newHistory: List<Pair<SwingType, Boolean>>,
            swingType: SwingType,
            gridIndex: Int
        ): CopyCatUpdate {
            // Check if we can immediately start a new sequence
            if (newHistory.size >= 2) {
                val last = newHistory.last()
                val prev = newHistory[newHistory.size - 2]
                
                if (last.first == prev.first && last.second != prev.second) {
                    // Immediate new sequence start
                    val leaderIsLocal = prev.second
                    return CopyCatUpdate(
                        shotHistory = newHistory,
                        sequenceStartIndex = newHistory.size - 2,
                        copyCount = 1,
                        uniqueTypes = setOf(swingType),
                        leaderCells = if (leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                        followerCells = if (!leaderIsLocal) listOf(gridIndex to 1) else emptyList(),
                        nextExpectedCell = -1,
                        isLocalPlayerLeader = leaderIsLocal,
                        tierAwarded = 0,
                        bonusAwarded = 0f,
                        patternBroken = true
                    )
                }
            }
            // Pattern broken, no new start
            return CopyCatUpdate(
                shotHistory = newHistory,
                patternBroken = true
            )
        }
    }
    
    /**
     * Result of Copy Cat processing for a single hit.
     */
    data class CopyCatUpdate(
        val shotHistory: List<Pair<SwingType, Boolean>> = emptyList(),
        val sequenceStartIndex: Int = -1,
        val copyCount: Int = 0,
        val uniqueTypes: Set<SwingType> = emptySet(),
        val leaderCells: List<Pair<Int, Int>> = emptyList(),
        val followerCells: List<Pair<Int, Int>> = emptyList(),
        val nextExpectedCell: Int = -1,
        val isLocalPlayerLeader: Boolean = false,
        val tierAwarded: Int = 0,
        val bonusAwarded: Float = 0f,
        val patternBroken: Boolean = false
    )
}
