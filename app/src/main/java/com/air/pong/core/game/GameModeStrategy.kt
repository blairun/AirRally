package com.air.pong.core.game

/**
 * Strategy interface for game mode-specific behavior.
 * 
 * Each game mode (Classic, Rally, Solo Rally, etc.) implements this interface
 * to define its own scoring logic, win conditions, and state transitions.
 * 
 * The GameEngine delegates mode-specific decisions to the active strategy,
 * keeping the core engine focused on common behavior (timing, networking, etc.).
 */
interface GameModeStrategy {
    
    /**
     * Display name of this game mode.
     */
    val modeName: String
    
    /**
     * The GameMode enum value for this strategy.
     */
    val gameMode: GameMode
    
    /**
     * Whether this mode uses net/out risk mechanics.
     * Classic: true (aggressive shots can fail)
     * Rally: false (cooperative, no risk)
     */
    val hasRisk: Boolean
    
    /**
     * Creates the initial mode-specific state for a new game.
     */
    fun createInitialModeState(): ModeState
    
    /**
     * Called when a serve is hit successfully.
     * 
     * @param state Current game state
     * @param swingType Type of swing used for the serve
     * @return Updated ModeState after processing the serve
     */
    fun onServe(state: GameState, swingType: SwingType): ModeState
    
    /**
     * Called when a hit is made during rally.
     * 
     * @param state Current game state
     * @param swingType Type of swing used
     * @param isOpponent Whether this hit was made by the opponent
     * @return Updated ModeState after processing the hit
     */
    fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean): ModeState
    
    /**
     * Called when a player misses.
     * 
     * @param state Current game state
     * @param whoMissed Which player missed (PLAYER_1 or PLAYER_2)
     * @param isHost Whether the local player is the host
     * @return MissResult containing updated mode state and whether the game is over
     */
    fun onMiss(state: GameState, whoMissed: Player, isHost: Boolean): MissResult
    
    /**
     * Calculates the hit window shrink for this mode.
     * 
     * @param state Current game state
     * @return Amount to shrink the hit window by (in ms)
     */
    fun getHitWindowShrink(state: GameState): Long
    
    /**
     * Returns any score to display (used for Rally mode's combined score).
     * Classic mode returns null (uses player1Score/player2Score).
     */
    fun getDisplayScore(state: GameState): Int?
    
    /**
     * Returns the number of lives remaining (Rally mode).
     * Classic mode returns null (uses score-based win condition).
     */
    fun getLives(state: GameState): Int?
}

/**
 * Result of processing a miss in a game mode.
 */
data class MissResult(
    val updatedModeState: ModeState,
    val isGameOver: Boolean,
    val nextServer: Player,
    val gameOverMessage: String? = null
)
