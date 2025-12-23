package com.air.pong.core.game

/**
 * Strategy for Solo Rally (single-player practice) game mode.
 * 
 * Rules:
 * - Player practices against a simulated wall
 * - 3 starting lives (losing a life on each miss)
 * - 3×3 grid marked by swing type (same as co-op Rally)
 * - Line completions award bonuses (horizontal < vertical < diagonal)
 * - X-clear (both diagonals) = instant tier upgrade
 * - No net/out risk (cooperative with wall)
 * - Hit window shrinks 50ms per tier achieved
 * - Flight time is multiplied by SOLO_FLIGHT_TIME_MULTIPLIER (default 1.3×)
 * - Player is always the one hitting (isMyTurn always true after serve)
 */
class SoloRallyModeStrategy : GameModeStrategy {
    
    override val modeName: String = "Solo Rally"
    override val gameMode: GameMode = GameMode.SOLO_RALLY
    override val hasRisk: Boolean = false  // No net/out risk (cooperative with wall)
    
    override fun createInitialModeState(): ModeState = ModeState.SoloRallyState.initial()
    
    override fun onServe(state: GameState, swingType: SwingType, spinType: SpinType): ModeState {
        // Get solo rally state from modeState
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return ModeState.SoloRallyState.initial()
        
        // Serves award tier points but don't mark grid (same as co-op Rally)
        val minTier = soloState.cellTiers.minOrNull() ?: 0
        val baseServePoints = GameEngine.getPointsForTier(minTier)
        
        // Apply momentum multiplier AND spin multiplier (if avatar currently spinning)
        val momentumMultiplier = GameEngine.getMomentumMultiplier(state.currentRallyLength)
        val currentTime = System.currentTimeMillis()
        val isAvatarSpinning = soloState.isAvatarSpinningIndefinitely || soloState.avatarSpinEndTime > currentTime
        val spinMultiplier = if (isAvatarSpinning) soloState.spinMultiplierBonus else 0f
        val totalMultiplier = momentumMultiplier + spinMultiplier
        val servePoints = Math.round(baseServePoints * totalMultiplier)
        
        // Track spin on serve (for spin bonus)
        val newDistinctSpinTypes = if (spinType != SpinType.NONE) {
            soloState.distinctSpinTypes + spinType
        } else {
            soloState.distinctSpinTypes
        }
        
        // Calculate spin multiplier bonus (only increases for NEW unique types)
        val newSpinBonus = (newDistinctSpinTypes.size * SpinType.SPIN_BONUS_PER_TYPE).coerceAtMost(SpinType.MAX_SPIN_MULTIPLIER_BONUS)
        
        // Update avatar spin timing - ANY spin shot adds duration (not just new types)
        val newSpinEnd = if (spinType != SpinType.NONE) {
            // Any spin shot extends (or starts) spin duration
            val existingDuration = if (soloState.avatarSpinEndTime > currentTime) {
                soloState.avatarSpinEndTime - currentTime
            } else 0L
            currentTime + existingDuration + (SpinType.SPIN_DURATION_PER_SHOT_SECONDS * 1000L)
        } else {
            soloState.avatarSpinEndTime
        }
        
        // Check for infinite spin (5 unique types while spinning = infinite for rest of game)
        val isInfiniteSpin = soloState.isAvatarSpinningIndefinitely || 
            (newSpinEnd > currentTime && newDistinctSpinTypes.size >= SpinType.INFINITE_SPIN_THRESHOLD)
        
        // Detect if infinite spin was JUST achieved this serve (transition from not infinite to infinite)
        val justAchievedInfiniteSpin = !soloState.isAvatarSpinningIndefinitely && isInfiniteSpin
        
        // Sound event for serve: only spin infinite is relevant
        val soundEvent: RallySoundEvent? = if (justAchievedInfiniteSpin) RallySoundEvent.SPIN_INFINITE else null
        
        return soloState.copy(
            score = soloState.score + servePoints,
            scorePoints = servePoints,
            linesJustCleared = 0, // Serves don't clear lines
            isServeHit = true,
            lastHitGridIndex = swingType.getGridIndex(), // Use swing type's grid cell for popup positioning
            distinctSpinTypes = newDistinctSpinTypes,
            spinMultiplierBonus = newSpinBonus,
            avatarSpinEndTime = if (isInfiniteSpin) Long.MAX_VALUE else newSpinEnd,
            isAvatarSpinningIndefinitely = isInfiniteSpin,
            lastBonusType = if (spinType != SpinType.NONE && newDistinctSpinTypes.size > soloState.distinctSpinTypes.size) BonusType.SPIN else soloState.lastBonusType,
            lastSpecialBonusTimestamp = if (spinType != SpinType.NONE && newDistinctSpinTypes.size > soloState.distinctSpinTypes.size) currentTime else soloState.lastSpecialBonusTimestamp,
            soundEvent = soundEvent
        )
    }
    
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean, spinType: SpinType): ModeState {
        // In Solo Rally, isOpponent is always false since there's no opponent
        // The "wall return" is handled by timing, not by opponent hit tracking
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return ModeState.SoloRallyState.initial()
        return updateGridState(
            state = soloState, 
            swingType = swingType, 
            rallyLength = state.currentRallyLength, 
            spinType = spinType,
            bonusSpinEnabled = state.bonusSpinEnabled,
            bonusCopyCatEnabled = state.bonusCopyCatEnabled,
            bonusSpecialSquaresEnabled = state.bonusSpecialSquaresEnabled,
            bonusPowerOutageEnabled = state.bonusPowerOutageEnabled
        )
    }
    
    override fun onMiss(state: GameState, whoMissed: Player, isHost: Boolean): MissResult {
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return MissResult(
            updatedModeState = ModeState.SoloRallyState.initial(),
            isGameOver = true,
            nextServer = Player.PLAYER_1,
            gameOverMessage = "Error: Invalid game state"
        )
        
        val newLives = soloState.lives - 1
        val isGameOver = newLives <= 0
        
        // On miss: Reset spin session, but preserve infinite spin for rest of game
        // If infinite spin was achieved, preserve everything - otherwise reset
        val preserveInfiniteSpin = soloState.isAvatarSpinningIndefinitely
        val updatedState = soloState.copy(
            lives = maxOf(0, newLives),
            soundEvent = null,
            // Reset spin session on miss (new rally = new spin opportunities)
            // BUT preserve distinctSpinTypes for infinite spin so speed stays fast
            distinctSpinTypes = if (preserveInfiniteSpin) soloState.distinctSpinTypes else emptySet(),
            avatarSpinEndTime = if (preserveInfiniteSpin) Long.MAX_VALUE else 0L,
            spinMultiplierBonus = if (preserveInfiniteSpin) SpinType.MAX_SPIN_MULTIPLIER_BONUS else 0f,
            // isAvatarSpinningIndefinitely is NOT reset (preserved by copy)
            // === COPY CAT RESET ===
            // Break sequence on miss, set cooldown for next rally, KEEP bonus/tier (ratchet system)
            copyCatSequence = emptyList(),
            copyCatProgress = -1,
            copyCatCooldown = (CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MIN..CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MAX).random(),
            copyCatCompletedCells = emptyList(),
            copyCatBreakCells = if (soloState.copyCatProgress >= 0) {
                // Flash on: current target + any completed cells
                val targetCell = soloState.copyCatSequence.getOrNull(soloState.copyCatProgress) ?: -1
                (if (targetCell >= 0) listOf(targetCell) else emptyList()) + 
                    soloState.copyCatCompletedCells.map { it.first }
            } else emptyList(),
            isCopyCatBreakFlashing = soloState.copyCatProgress >= 0,  // Flash if there was an active sequence
            // copyCatMultiplierBonus and copyCatTierAchieved are preserved (ratchet)
            // Reset bonus display on miss (unless infinite spin achieved)
            lastBonusType = if (preserveInfiniteSpin) soloState.lastBonusType else null,
            lastSpecialBonusTimestamp = if (preserveInfiniteSpin) soloState.lastSpecialBonusTimestamp else 0L,
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
            nextServer = Player.PLAYER_1,  // Always player 1 in solo mode
            gameOverMessage = if (isGameOver) "Game Over! Score: ${soloState.score}" else null
        )
    }
    
    override fun getHitWindowShrink(state: GameState): Long {
        if (!state.isRallyShrinkEnabled || state.currentRallyLength <= 1) return 0L
        
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return 0L
        
        // Solo Rally Mode: 50ms shrink per tier achieved
        return soloState.highestTier * GameEngine.RALLY_TIER_SHRINK_MS
    }
    
    override fun getDisplayScore(state: GameState): Int? {
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return null
        return soloState.score
    }
    
    override fun getLives(state: GameState): Int? {
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return null
        return soloState.lives
    }
    
    /**
     * Handles special square effects when a player swings but misses the timing window.
     * Even on early/late misses, landmine and banana effects should still apply.
     */
    override fun onMissedSwing(state: GameState, swingType: SwingType): MissedSwingResult {
        val soloState = state.modeState as? ModeState.SoloRallyState 
            ?: return MissedSwingResult(state.modeState)
        
        // Check if special squares are enabled
        if (!state.bonusSpecialSquaresEnabled) {
            return MissedSwingResult(soloState)
        }
        
        val gridIndex = swingType.getGridIndex()
        val specialSquareHit = soloState.specialSquares[gridIndex]
        
        return when (specialSquareHit) {
            SpecialSquareType.LANDMINE -> {
                // Landmine effect: Break infinite spin, play explosion sound
                // Note: Life loss is handled by the normal miss flow
                val updatedState = soloState.copy(
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
                    (soloState.cellTiers.minOrNull() ?: 1).coerceAtLeast(1)
                val newScore = (soloState.score - penalty).coerceAtLeast(0)
                val newGoldenMultiplier = (soloState.goldenMultiplierBonus - 0.1f).coerceAtLeast(0f)
                
                val updatedState = soloState.copy(
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
                MissedSwingResult(soloState)
            }
        }
    }
    
    /**
     * Updates the grid state after a hit.
     * Handles grid marking, line completion, tier upgrades, and bonus points.
     * Similar to RallyModeStrategy but simplified for single player (no opponent grid).
     * Applies momentum multiplier based on current rally length.
     */
    private fun updateGridState(
        state: ModeState.SoloRallyState,
        swingType: SwingType,
        rallyLength: Int,
        spinType: SpinType,
        bonusSpinEnabled: Boolean = true,
        bonusCopyCatEnabled: Boolean = true,
        bonusSpecialSquaresEnabled: Boolean = true,
        bonusPowerOutageEnabled: Boolean = true
    ): ModeState.SoloRallyState {
        val gridIndex = swingType.getGridIndex()
        
        // === COPY CAT PROCESSING ===
        // Only process if CopyCat bonus is enabled
        val copyCatUpdate = if (bonusCopyCatEnabled) {
            processSoloCopyCat(state, swingType)
        } else {
            // Return a no-op update that just maintains cooldown state
            CopyCatSoloUpdate(
                sequence = emptyList(),
                progress = -1,
                cooldown = state.copyCatCooldown,
                completedCells = emptyList()
            )
        }
        
        // === SPECIAL SQUARE HIT LOGIC ===
        // Only process if special squares bonus is enabled
        val specialSquareHit = if (bonusSpecialSquaresEnabled) state.specialSquares[gridIndex] else null
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
        val newGrid = state.grid.toMutableList()
        newGrid[gridIndex] = true
        
        // 2. Award points based on the cell's current tier
        val cellTier = state.cellTiers[gridIndex]
        var pointsPerHit = GameEngine.getPointsForTier(cellTier)
        
        when (specialSquareHit) {
            SpecialSquareType.BANANA -> {
                // Banana penalty: deduct 10 * minTier points
                // NOTE: Banana PERSISTS - do NOT remove it. Player can hit it multiple times.
                val penalty = SpecialSquareType.BANANA_PENALTY_PER_TIER * (state.cellTiers.minOrNull() ?: 1).coerceAtLeast(1)
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
        val newLinesCompleted = state.linesCompleted.toMutableList()
        val newTiers = state.cellTiers.toMutableList()
        var linesCompletedThisTurn = 0
        val linesCompletedIndices = mutableListOf<Int>()
        
        // Find the minimum tier across all cells
        val minTier = state.cellTiers.minOrNull() ?: 0
        val nextTier = minTier + 1
        
        // Collect all cells to clear
        val cellsToClear = mutableSetOf<Int>()
        
        LINES.forEachIndexed { lineIndex, indices ->
            val isLineComplete = indices.all { newGrid[it] }
            if (isLineComplete && !state.linesCompleted[lineIndex]) {
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
                val goldenInLine = indices.any { state.specialSquares[it] == SpecialSquareType.GOLDEN }
                if (goldenInLine) {
                    val goldenBonus = when (linesCompletedThisTurn) {
                        1 -> 0.1f
                        2 -> 0.2f
                        else -> 0.2f
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
        var newHighestTier = state.highestTier
        var tierUpgraded = false
        var newGridUpgradesEarned = state.gridUpgradesEarned
        var newCopyCat9Completions = state.copyCat9Completions
        
        // === COPY CAT 9/9 EXTRA LIFE ===
        // Award an extra life for completing a full 9/9 Copy Cat sequence
        // In solo mode, progress 8 means the 9th cell was just hit (0-indexed)
        // Check that the sequence just completed (previous progress < 8)
        if (copyCatUpdate.progress >= 8 && state.copyCatProgress < 8 && !copyCatUpdate.patternBroken) {
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
        
        if (shouldUpgradeTier) {
            newHighestTier = nextTier
            tierUpgraded = true
            
            // === SHIELD EARNING ===
            // Award shield if clearing grid with unhit penalty square (Banana or Landmine)
            // Requirement: penalty must exist AND player must NOT have hit it during this tier
            val hadPenalty = newSpecialSquares.values.any { it == SpecialSquareType.BANANA || it == SpecialSquareType.LANDMINE }
            if (hadPenalty && !penaltyHitThisTier) {
                hasShield = true
            }
            // Reset penalty hit tracking for new tier
            penaltyHitThisTier = false
            
            // Reset the entire grid
            for (i in 0 until 9) {
                newGrid[i] = false
                newTiers[i] = nextTier
            }
            for (i in 0 until 8) {
                newLinesCompleted[i] = false
            }
            
            // Extra life for completing a new tier
            if (nextTier > state.highestTier) {
                newLives++
                newGridUpgradesEarned++
            }
            
            // === SPAWN SPECIAL SQUARES FOR NEW TIER ===
            // Only spawn if special squares bonus is enabled
            if (bonusSpecialSquaresEnabled) {
                newSpecialSquares = RallyModeStrategy.spawnSpecialSquares(nextTier).toMutableMap()
            }
        }
        
        
        // Sound event will be determined after spin calculations
        
        // Apply momentum multiplier AND spin multiplier (if avatar currently spinning)
        val momentumMultiplier = GameEngine.getMomentumMultiplier(rallyLength)
        val currentTime = System.currentTimeMillis()
        val isAvatarSpinning = state.isAvatarSpinningIndefinitely || state.avatarSpinEndTime > currentTime
        val spinMultiplier = if (isAvatarSpinning) state.spinMultiplierBonus else 0f
        val totalMultiplier = momentumMultiplier + spinMultiplier
        val multipliedPoints = Math.round(pointsToAdd * totalMultiplier)
        
        // Track spin bonus (only if spin bonus is enabled)
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
        // Update cell hit counts
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
        // 7. COPY_CAT_BREAK (pattern broken with 4+ hits - use previous progress since break resets it)
        // 8. BANANA_HIT
        // 9. LINE_COMPLETE
        val soundEvent: RallySoundEvent? = when {
            justRestoredPower -> RallySoundEvent.POWER_OUTAGE_RESTORE
            justTriggeredOutage -> RallySoundEvent.POWER_OUTAGE_START
            justAchievedInfiniteSpin -> RallySoundEvent.SPIN_INFINITE
            shieldBroken -> RallySoundEvent.SHIELD_BREAK
            tierUpgraded -> RallySoundEvent.GRID_COMPLETE
            // Copy cat bonus: play for new tier OR for 9/9 (always play 9/9 since it awards life)
            copyCatUpdate.tierAwarded > 0 || copyCatUpdate.progress >= 8 -> RallySoundEvent.COPY_CAT_BONUS
            // Copy cat break: only play if pattern was 4+ hits long (use previous progress)
            copyCatUpdate.patternBroken && state.copyCatProgress >= 3 -> RallySoundEvent.COPY_CAT_BREAK
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
        
        return state.copy(
            grid = newGrid,
            linesCompleted = newLinesCompleted,
            cellTiers = newTiers,
            highestTier = newHighestTier,
            score = (state.score + multipliedPoints).coerceAtLeast(0),
            scorePoints = multipliedPoints,
            linesJustCleared = linesCompletedThisTurn,
            lives = newLives,
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
            // Copy Cat tracking (CPU sequence)
            copyCatSequence = copyCatUpdate.sequence,
            copyCatProgress = copyCatUpdate.progress,
            copyCatCooldown = copyCatUpdate.cooldown,
            copyCatCompletedCells = copyCatUpdate.completedCells,
            copyCatMultiplierBonus = state.copyCatMultiplierBonus + copyCatUpdate.bonusAwarded,
            copyCatTierAchieved = maxOf(state.copyCatTierAchieved, copyCatUpdate.tierAwarded),
            copyCatBreakCells = copyCatUpdate.breakCells,
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
    
    companion object {
        // Lines: Rows (0-2, 3-5, 6-8), Cols (036, 147, 258), Diagonals (048, 246)
        private val LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
            listOf(0, 4, 8), listOf(2, 4, 6)                   // Diagonals (indices 6, 7)
        )
        
        /**
         * Process Copy Cat for Solo mode using CPU-generated sequences.
         * CPU generates a 9-cell target sequence that player must follow.
         * 
         * @param state Current SoloRallyState
         * @param swingType The swing type just hit (grid index derived from this)
         * @return Updated Copy Cat fields as a CopyCatSoloUpdate
         */
        fun processSoloCopyCat(
            state: ModeState.SoloRallyState,
            swingType: SwingType
        ): CopyCatSoloUpdate {
            val gridIndex = swingType.getGridIndex()
            
            // If no active sequence, handle cooldown
            if (state.copyCatProgress < 0) {
                val newCooldown = state.copyCatCooldown - 1
                
                if (newCooldown <= 0) {
                    // Start new sequence!
                    val newSequence = generateCopyCatSequence()
                    return CopyCatSoloUpdate(
                        sequence = newSequence,
                        progress = 0,
                        cooldown = 0,
                        completedCells = emptyList()
                    )
                } else {
                    // Still in cooldown
                    return CopyCatSoloUpdate(
                        sequence = emptyList(),
                        progress = -1,
                        cooldown = newCooldown,
                        completedCells = emptyList()
                    )
                }
            }
            
            // Active sequence - check if hit matches target
            val targetIndex = state.copyCatSequence.getOrNull(state.copyCatProgress) ?: -1
            
            if (gridIndex == targetIndex) {
                // Match! Advance progress
                val newProgress = state.copyCatProgress + 1
                val newCompletedCells = state.copyCatCompletedCells + (gridIndex to newProgress)
                
                // Check if sequence complete
                if (newProgress >= state.copyCatSequence.size) {
                    // Sequence complete! Calculate tier bonus
                    val uniqueTypes = state.copyCatSequence.map { SwingType.entries[it] }.toSet().size
                    val tier = CopyCatConstants.calculateCopyCatTier(newProgress, uniqueTypes)
                    val bonusToAdd = if (tier > state.copyCatTierAchieved) {
                        CopyCatConstants.multiplierForTier(tier) - 
                            CopyCatConstants.multiplierForTier(state.copyCatTierAchieved)
                    } else 0f
                    
                    // Reset with new cooldown
                    return CopyCatSoloUpdate(
                        sequence = emptyList(),
                        progress = -1,
                        cooldown = (CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MIN..CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MAX).random(),
                        completedCells = emptyList(),
                        tierAwarded = if (bonusToAdd > 0f) tier else 0,
                        bonusAwarded = bonusToAdd
                    )
                } else {
                    // Continue sequence - calculate tier bonus for intermediate progress
                    // Get unique types from all cells completed so far (including this one)
                    val completedIndices = newCompletedCells.map { it.first }
                    val uniqueTypes = completedIndices.map { SwingType.entries[it] }.toSet().size
                    
                    // Check if we've reached a new tier at this progress level
                    val tier = CopyCatConstants.calculateCopyCatTier(newProgress, uniqueTypes)
                    val tierAwarded = if (tier > state.copyCatTierAchieved) tier else 0
                    val bonusToAdd = if (tierAwarded > 0) {
                        CopyCatConstants.multiplierForTier(tierAwarded) - 
                            CopyCatConstants.multiplierForTier(state.copyCatTierAchieved)
                    } else 0f
                    
                    return CopyCatSoloUpdate(
                        sequence = state.copyCatSequence,
                        progress = newProgress,
                        cooldown = 0,
                        completedCells = newCompletedCells,
                        tierAwarded = tierAwarded,
                        bonusAwarded = bonusToAdd
                    )
                }
            } else {
                // Wrong cell hit - break sequence
                // Flash on: target cell + any completed cells
                val cellsToFlash = listOf(targetIndex) + state.copyCatCompletedCells.map { it.first }
                return CopyCatSoloUpdate(
                    sequence = emptyList(),
                    progress = -1,
                    cooldown = (CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MIN..CopyCatConstants.SOLO_COPYCAT_COOLDOWN_MAX).random(),
                    completedCells = emptyList(),
                    breakCells = cellsToFlash,
                    patternBroken = true  // Always flash when hitting wrong cell during active sequence
                )
            }
        }
        
        /**
         * Generate a CPU Copy Cat sequence of 9 grid indices.
         * Ensures the sequence meets tier requirements (7+ unique types for max tier).
         */
        private fun generateCopyCatSequence(): List<Int> {
            val random = kotlin.random.Random
            
            // Shuffle all 9 grid indices to get 7 unique types guaranteed
            val shuffledIndices = (0..8).shuffled(random).toMutableList()
            
            // Take all 9 unique, then we already have 9 unique types
            // But we want exactly 9 cells. Since there are only 9 grid indices,
            // shuffling them gives us 9 unique cells = 9 unique swing types = max tier possible
            return shuffledIndices
        }
    }
    
    /**
     * Result of Copy Cat processing for Solo mode.
     */
    data class CopyCatSoloUpdate(
        val sequence: List<Int> = emptyList(),
        val progress: Int = -1,
        val cooldown: Int = 0,
        val completedCells: List<Pair<Int, Int>> = emptyList(),
        val tierAwarded: Int = 0,
        val bonusAwarded: Float = 0f,
        val breakCells: List<Int> = emptyList(),
        val patternBroken: Boolean = false
    )
}

