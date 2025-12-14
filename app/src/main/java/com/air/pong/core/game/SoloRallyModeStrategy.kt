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
    
    override fun onServe(state: GameState, swingType: SwingType): ModeState {
        // Get solo rally state from modeState
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return ModeState.SoloRallyState.initial()
        
        // Serves award tier points but don't mark grid (same as co-op Rally)
        val minTier = soloState.cellTiers.minOrNull() ?: 0
        val baseServePoints = GameEngine.getPointsForTier(minTier)
        
        // Apply momentum multiplier (currentRallyLength will be 1 after serve, but we use pre-serve length)
        val multiplier = GameEngine.getMomentumMultiplier(state.currentRallyLength)
        val servePoints = Math.round(baseServePoints * multiplier)
        
        return soloState.copy(
            score = soloState.score + servePoints,
            scorePoints = servePoints,
            linesJustCleared = 0, // Serves don't clear lines
            isServeHit = true,
            lastHitGridIndex = -1 // -1 means serve (no grid cell)
        )
    }
    
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean): ModeState {
        // In Solo Rally, isOpponent is always false since there's no opponent
        // The "wall return" is handled by timing, not by opponent hit tracking
        val soloState = state.modeState as? ModeState.SoloRallyState ?: return ModeState.SoloRallyState.initial()
        return updateGridState(soloState, swingType, state.currentRallyLength)
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
        
        val updatedState = soloState.copy(
            lives = maxOf(0, newLives),
            soundEvent = null  // Clear sound event on miss
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
     * Updates the grid state after a hit.
     * Handles grid marking, line completion, tier upgrades, and bonus points.
     * Similar to RallyModeStrategy but simplified for single player (no opponent grid).
     * Applies momentum multiplier based on current rally length.
     */
    private fun updateGridState(
        state: ModeState.SoloRallyState,
        swingType: SwingType,
        rallyLength: Int
    ): ModeState.SoloRallyState {
        val gridIndex = swingType.getGridIndex()
        
        // 1. Mark Grid Cell
        val newGrid = state.grid.toMutableList()
        newGrid[gridIndex] = true
        
        // 2. Award points based on the cell's current tier
        val cellTier = state.cellTiers[gridIndex]
        val pointsPerHit = GameEngine.getPointsForTier(cellTier)
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
        
        // X-Clear: Both diagonals completed this turn
        val bothDiagonalsCompleted = linesCompletedIndices.contains(6) && linesCompletedIndices.contains(7)
        
        // Check if tier should upgrade
        val allCellsUpgraded = newTiers.all { it >= nextTier }
        val shouldUpgradeTier = allCellsUpgraded || bothDiagonalsCompleted
        
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
            
            // Extra life for completing a new tier (Solo Rally tracks its own highest)
            if (nextTier > state.highestTier) {
                newLives++
            }
        }
        
        // Determine sound event
        val soundEvent: RallySoundEvent? = when {
            tierUpgraded -> RallySoundEvent.GRID_COMPLETE
            linesCompletedThisTurn > 0 -> RallySoundEvent.LINE_COMPLETE
            else -> null
        }
        
        // Apply momentum multiplier to all points
        val multiplier = GameEngine.getMomentumMultiplier(rallyLength)
        val multipliedPoints = Math.round(pointsToAdd * multiplier)
        
        return state.copy(
            grid = newGrid,
            linesCompleted = newLinesCompleted,
            cellTiers = newTiers,
            highestTier = newHighestTier,
            score = state.score + multipliedPoints,
            scorePoints = multipliedPoints,
            linesJustCleared = linesCompletedThisTurn,
            lives = newLives,
            soundEvent = soundEvent,
            lastHitGridIndex = gridIndex,
            isServeHit = false,
            totalLinesCleared = state.totalLinesCleared + linesCompletedThisTurn
        )
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
