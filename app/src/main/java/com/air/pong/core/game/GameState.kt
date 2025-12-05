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
    val flightTime: Long = 700L, // Configurable flight time
    val difficulty: Int = 1, // 0: Easy, 1: Medium, 2: Hard
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
    val currentHitWindow: Long = 400L // Calculated hit window for the NEXT shot
)

/**
 * Calculates what the hit window SHOULD be based on the current state.
 * This logic was moved from GameEngine to be accessible for UI/Debug.
 */
fun GameState.calculateHitWindow(): Long {
    // Difficulty is the base window size in ms (200-700)
    val baseWindow = difficulty.toLong()
    
    // Window Shrink Logic:
    // Disabled for Return of Serve (Rally Length <= 1).
    // Only applies during rallied shots (Rally Length > 1).
    if (currentRallyLength <= 1) {
        return baseWindow
    }

    // 1. Rally Shrink
    var currentWindow = baseWindow
    if (isRallyShrinkEnabled) {
        val rallyShrink = (currentRallyLength * 10L)
        currentWindow = (currentWindow - rallyShrink).coerceAtLeast(200L)
    }

    // 2. Swing Type Shrink
    val shrinkPercentage = lastSwingType?.getWindowShrinkPercentage() ?: 0f
    val shrinkFactor = 1.0f - shrinkPercentage
    
    return (currentWindow * shrinkFactor).roundToLong()
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
