package com.air.pong.core.game

import kotlin.math.roundToLong

/**
 * Represents the current state of the game.
 * This is the Source of Truth for the UI.
 */
data class GameState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val gamePhase: GamePhase = GamePhase.IDLE,
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val lastEvent: String = "",
    
    // Game Logic Fields
    val servingPlayer: Player = Player.PLAYER_1,
    val ballArrivalTimestamp: Long = 0L, // When the ball is expected to arrive
    val isMyTurn: Boolean = false, // Helper for UI
    val flightTime: Long = GameEngine.DEFAULT_FLIGHT_TIME, // Configurable flight time
    val difficulty: Int = GameEngine.DEFAULT_DIFFICULTY, // 0: Easy, 1: Medium, 2: Hard
    val isDebugMode: Boolean = false,
    val useDebugTones: Boolean = false,
    val isRallyShrinkEnabled: Boolean = true,
    
    val ballState: BallState = BallState.IDLE,
    val eventLog: List<GameEvent> = emptyList(),
    val lastSwingType: SwingType? = null,
    val lastSwingData: SwingData? = null,
    val pendingMiss: PendingMiss? = null,
    val minSwingThreshold: Float = GameEngine.DEFAULT_SWING_THRESHOLD,

    // Stats Tracking for current point
    val currentPointShots: List<SwingType> = emptyList(),
    val currentRallyLength: Int = 0,
    val longestRally: Int = 0,
    
    // Dynamic State
    val currentHitWindow: Long = GameEngine.DEFAULT_DIFFICULTY.toLong(), // Calculated hit window for the NEXT shot
    
    // Mode selection and mode-specific state (Strategy Pattern)
    val gameMode: GameMode = GameMode.RALLY,
    val modeState: ModeState = ModeState.ClassicState
) {
    /**
     * Helper property to access RallyState from modeState.
     * Returns null if the current mode is not Rally.
     */
    val rallyState: ModeState.RallyState?
        get() = modeState as? ModeState.RallyState
    
    /**
     * Helper property to access SoloRallyState from modeState.
     * Returns null if the current mode is not Solo Rally.
     */
    val soloRallyState: ModeState.SoloRallyState?
        get() = modeState as? ModeState.SoloRallyState
}

enum class RallySoundEvent {
    LINE_COMPLETE,
    GRID_COMPLETE
}

enum class GameMode {
    CLASSIC,
    RALLY,
    SOLO_RALLY
}

/**
 * Calculates what the hit window SHOULD be based on the current state.
 * This logic was moved from GameEngine to be accessible for UI/Debug.
 */
fun GameState.calculateHitWindow(settings: SwingSettings): Long {
    // Difficulty is the base window size in ms (300-1200)
    val baseWindow = difficulty.toLong()
    
    // Window Shrink Logic:
    // Disabled for Return of Serve (Rally Length <= 1).
    // Only applies during rallied shots (Rally Length > 1).
    if (currentRallyLength <= 1) {
        return baseWindow
    }

    var currentWindow = baseWindow
    
    // Rally & Solo Rally Mode: Tier-based shrink (instead of per-shot shrink)
    // Shrinks 50ms for each tier achieved globally (or locally for Solo)
    if ((gameMode == GameMode.RALLY || gameMode == GameMode.SOLO_RALLY) && isRallyShrinkEnabled) {
        val highestTier = when (val state = modeState) {
            is ModeState.RallyState -> state.globalHighestTier
            is ModeState.SoloRallyState -> state.highestTier
            else -> 0
        }
        val tierShrink = highestTier * GameEngine.RALLY_TIER_SHRINK_MS
        currentWindow = (currentWindow - tierShrink).coerceAtLeast(GameEngine.MIN_HIT_WINDOW)
    } else if (isRallyShrinkEnabled) {
        // Classic Mode: Per-shot shrink (10ms per shot)
        val rallyShrink = (currentRallyLength * GameEngine.RALLY_SHRINK_PER_SHOT_MS)
        currentWindow = (currentWindow - rallyShrink).coerceAtLeast(GameEngine.MIN_HIT_WINDOW)
    }

    // 2. Swing Type Shrink (percentage reduction based on incoming shot)
    val shrinkPercentage = lastSwingType?.getWindowShrinkPercentage(settings) ?: 0f
    val shrinkFactor = 1.0f - shrinkPercentage
    val shrunkWindow = (currentWindow * shrinkFactor).roundToLong()
    
    // 3. Absolute Floor - window can never be smaller than MIN_HIT_WINDOW
    return shrunkWindow.coerceAtLeast(GameEngine.MIN_HIT_WINDOW)
}

data class PendingMiss(
    val type: HitResult,
    val timestamp: Long,
    val delayMs: Long
)

enum class BallState {
    IDLE,
    SERVE,
    IN_AIR,
    BOUNCED_MY_SIDE,
    BOUNCED_OPP_SIDE
}

enum class ConnectionState {
    DISCONNECTED,
    ADVERTISING,
    DISCOVERING,
    CONNECTED
}

enum class GamePhase {
    IDLE,
    WAITING_FOR_SERVE,
    POINT_SCORED,
    RALLY,
    PAUSED,
    GAME_OVER
}

enum class Player {
    PLAYER_1, // Host
    PLAYER_2  // Client
}

/**
 * Result of a swing attempt.
 */
enum class HitResult {
    HIT,        // Ball was hit within timing window
    MISS_LATE,  // Swung too late or didn't swing
    MISS_EARLY, // Swung too early
    MISS_NET,   // Hit into the net (Risk check failed)
    MISS_OUT,   // Hit out of bounds (Risk check failed)
    MISS_TIMEOUT, // Didn't swing in time
    PENDING     // Hit was successful physically, but result (Net/Out) is delayed
}
