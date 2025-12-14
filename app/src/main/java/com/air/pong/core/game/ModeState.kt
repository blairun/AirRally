package com.air.pong.core.game

/**
 * Sealed class hierarchy for mode-specific state.
 * Each game mode can define its own state container.
 */
sealed class ModeState {
    /**
     * Classic (1v1 competitive) mode state.
     * Classic mode uses player1Score/player2Score from GameState, so no additional state needed.
     */
    data object ClassicState : ModeState()
    
    /**
     * Rally (co-op) mode state.
     * Tracks grid, tiers, lives, and scoring for both players.
     */
    data class RallyState(
        val score: Int = 0,
        val lives: Int = GameEngine.RALLY_STARTING_LIVES,
        val scorePoints: Int = 0, // Points from current hit
        val linesJustCleared: Int = 0, // Lines cleared in current hit (for popup text)
        
        // Local Player Grid
        val grid: List<Boolean> = List(9) { false },
        val cellTiers: List<Int> = List(9) { 0 },
        val linesCompleted: List<Boolean> = List(8) { false },
        val highestTier: Int = 0,
        val totalLinesCleared: Int = 0,
        
        // Opponent Grid (tracked locally for score calculation)
        val opponentGrid: List<Boolean> = List(9) { false },
        val opponentCellTiers: List<Int> = List(9) { 0 },
        val opponentLinesCompleted: List<Boolean> = List(8) { false },
        val opponentHighestTier: Int = 0,
        val partnerTotalLinesCleared: Int = 0,
        
        // Hit tracking
        val lastHitGridIndex: Int = -1,
        val isServeHit: Boolean = false,
        val soundEvent: RallySoundEvent? = null
    ) : ModeState() {
        
        /**
         * Returns the global highest tier achieved by either player.
         */
        val globalHighestTier: Int get() = maxOf(highestTier, opponentHighestTier)
        
        /**
         * Returns the minimum tier across all local cells.
         */
        val minTier: Int get() = cellTiers.minOrNull() ?: 0
        
        companion object {
            fun initial() = RallyState()
        }
    }
    
    /**
     * Solo Rally mode state (for future implementation).
     * Player plays against a simulated wall.
     */
    data class SoloRallyState(
        val score: Int = 0,
        val lives: Int = GameEngine.RALLY_STARTING_LIVES,
        val scorePoints: Int = 0, // Points from current hit (for popup animation)
        val linesJustCleared: Int = 0, // Lines cleared in current hit (for popup text)
        val grid: List<Boolean> = List(9) { false },
        val cellTiers: List<Int> = List(9) { 0 },
        val linesCompleted: List<Boolean> = List(8) { false },
        val highestTier: Int = 0,
        val totalLinesCleared: Int = 0,
        val lastHitGridIndex: Int = -1,
        val isServeHit: Boolean = false,
        val soundEvent: RallySoundEvent? = null
    ) : ModeState() {
        companion object {
            fun initial() = SoloRallyState()
        }
    }
}
