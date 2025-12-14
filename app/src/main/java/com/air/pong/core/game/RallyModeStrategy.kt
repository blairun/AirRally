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
    
    override fun onServe(state: GameState, swingType: SwingType): ModeState {
        // Get rally state from modeState - Strategy pattern ensures this is always RallyState
        val rallyState = state.modeState as? ModeState.RallyState ?: return ModeState.RallyState.initial()
        
        // Serves award tier points but don't mark grid
        val minTier = rallyState.minTier
        val baseServePoints = GameEngine.getPointsForTier(minTier)
        
        // Apply momentum multiplier (currentRallyLength will be 1 after serve, but we use pre-serve length)
        val multiplier = GameEngine.getMomentumMultiplier(state.currentRallyLength)
        val servePoints = Math.round(baseServePoints * multiplier)
        
        return rallyState.copy(
            score = rallyState.score + servePoints,
            scorePoints = servePoints,
            linesJustCleared = 0, // Serves don't clear lines
            isServeHit = true,
            lastHitGridIndex = -1 // -1 means serve (no grid cell)
        )
    }
    
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean): ModeState {
        // Get rally state from modeState - Strategy pattern ensures this is always RallyState
        val rallyState = state.modeState as? ModeState.RallyState ?: return ModeState.RallyState.initial()
        return updateGridState(rallyState, swingType, isOpponent, state.currentRallyLength)
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
        
        val updatedState = rallyState.copy(
            lives = maxOf(0, newLives),
            // Clear sound event on miss
            soundEvent = null
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
     * Updates the grid state after a hit.
     * Handles grid marking, line completion, tier upgrades, and bonus points.
     * Applies momentum multiplier based on current rally length.
     */
    private fun updateGridState(
        state: ModeState.RallyState,
        swingType: SwingType,
        isOpponent: Boolean,
        rallyLength: Int
    ): ModeState.RallyState {
        val gridIndex = swingType.getGridIndex()
        
        // Get the correct grid data based on whether it's opponent or local
        val currentGrid = if (isOpponent) state.opponentGrid else state.grid
        val currentTiers = if (isOpponent) state.opponentCellTiers else state.cellTiers
        val currentHighestTier = if (isOpponent) state.opponentHighestTier else state.highestTier
        val currentLinesCompleted = if (isOpponent) state.opponentLinesCompleted else state.linesCompleted
        
        // 1. Mark Grid Cell
        val newGrid = currentGrid.toMutableList()
        newGrid[gridIndex] = true
        
        // 2. Award points based on the cell's current tier
        val cellTier = currentTiers[gridIndex]
        val pointsPerHit = GameEngine.getPointsForTier(cellTier)
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
        
        // X-Clear: Both diagonals completed this turn
        val bothDiagonalsCompleted = linesCompletedIndices.contains(6) && linesCompletedIndices.contains(7)
        
        // Check if tier should upgrade
        val allCellsUpgraded = newTiers.all { it >= nextTier }
        val shouldUpgradeTier = allCellsUpgraded || bothDiagonalsCompleted
        
        // Use GLOBAL highest tier
        val globalHighestTier = maxOf(state.highestTier, state.opponentHighestTier)
        
        if (shouldUpgradeTier) {
            newHighestTier = nextTier
            tierUpgraded = true
            
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
            }
        }
        
        // Determine sound event (only for local player)
        val soundEvent: RallySoundEvent? = if (!isOpponent) {
            when {
                tierUpgraded -> RallySoundEvent.GRID_COMPLETE
                linesCompletedThisTurn > 0 -> RallySoundEvent.LINE_COMPLETE
                else -> null
            }
        } else null
        
        // Apply momentum multiplier to all points
        val multiplier = GameEngine.getMomentumMultiplier(rallyLength)
        val multipliedPoints = Math.round(pointsToAdd * multiplier)
        
        return if (isOpponent) {
            state.copy(
                opponentGrid = newGrid,
                opponentLinesCompleted = newLinesCompleted,
                opponentCellTiers = newTiers,
                opponentHighestTier = newHighestTier,
                score = state.score + multipliedPoints,
                lives = newLives,
                scorePoints = multipliedPoints,
                linesJustCleared = linesCompletedThisTurn,
                partnerTotalLinesCleared = state.partnerTotalLinesCleared + linesCompletedThisTurn,
                lastHitGridIndex = gridIndex // Track partner's hit cell for popup animation
            )
        } else {
            state.copy(
                grid = newGrid,
                linesCompleted = newLinesCompleted,
                cellTiers = newTiers,
                highestTier = newHighestTier,
                score = state.score + multipliedPoints,
                lives = newLives,
                scorePoints = multipliedPoints,
                linesJustCleared = linesCompletedThisTurn,
                soundEvent = soundEvent,
                lastHitGridIndex = gridIndex,
                isServeHit = false,
                totalLinesCleared = state.totalLinesCleared + linesCompletedThisTurn
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
    }
}
