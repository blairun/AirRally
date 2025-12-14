package com.air.pong.core.game

import kotlin.math.abs

/**
 * Strategy for Classic (1v1 competitive) game mode.
 * 
 * Rules:
 * - First to 11 points wins (must win by 2)
 * - Serve rotates every 2 points (every 1 at deuce)
 * - Aggressive shots have net/out risk
 * - Hit window shrinks 10ms per shot in rally
 */
class ClassicModeStrategy : GameModeStrategy {
    
    override val modeName: String = "Classic"
    override val gameMode: GameMode = GameMode.CLASSIC
    override val hasRisk: Boolean = true
    
    override fun createInitialModeState(): ModeState = ModeState.ClassicState
    
    override fun onServe(state: GameState, swingType: SwingType): ModeState {
        // Classic mode doesn't track mode-specific state for serves
        return ModeState.ClassicState
    }
    
    override fun onHit(state: GameState, swingType: SwingType, isOpponent: Boolean): ModeState {
        // Classic mode doesn't track mode-specific state for hits
        return ModeState.ClassicState
    }
    
    override fun onMiss(state: GameState, whoMissed: Player, isHost: Boolean): MissResult {
        val p1Score = if (whoMissed == Player.PLAYER_2) state.player1Score + 1 else state.player1Score
        val p2Score = if (whoMissed == Player.PLAYER_1) state.player2Score + 1 else state.player2Score
        
        // Check Win (Win by 2)
        val scoreDiff = abs(p1Score - p2Score)
        val isGameOver = (p1Score >= SCORE_LIMIT || p2Score >= SCORE_LIMIT) && scoreDiff >= 2
        
        // Determine next server
        val totalPoints = p1Score + p2Score
        val isDeuce = p1Score >= 10 && p2Score >= 10
        val serveChangeInterval = if (isDeuce) 1 else SERVE_ROTATION
        val rotationIndex = totalPoints / serveChangeInterval
        val nextServer = if (rotationIndex % 2 == 0) Player.PLAYER_1 else Player.PLAYER_2
        
        return MissResult(
            updatedModeState = ModeState.ClassicState,
            isGameOver = isGameOver,
            nextServer = nextServer,
            gameOverMessage = if (isGameOver) "Game Over!" else null
        )
    }
    
    override fun getHitWindowShrink(state: GameState): Long {
        // Classic Mode: 10ms shrink per shot in rally
        return if (state.isRallyShrinkEnabled && state.currentRallyLength > 1) {
            state.currentRallyLength * GameEngine.RALLY_SHRINK_PER_SHOT_MS
        } else {
            0L
        }
    }
    
    override fun getDisplayScore(state: GameState): Int? = null // Uses player1Score/player2Score
    
    override fun getLives(state: GameState): Int? = null // Score-based, no lives
    
    companion object {
        private const val SCORE_LIMIT = 11
        private const val SERVE_ROTATION = 2
    }
}
