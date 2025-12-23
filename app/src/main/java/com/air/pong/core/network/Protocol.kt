package com.air.pong.core.network

import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.Player
import com.air.pong.core.game.HitResult
import com.air.pong.core.game.GameMode

/**
 * Defines all message types that can be sent between devices.
 * Uses a sealed class hierarchy for type safety.
 */
sealed class GameMessage {
    /**
     * First message exchanged after connection.
     * Verifies both devices are running compatible versions.
     */
    data class Handshake(val versionCode: Int) : GameMessage()
    
    /**
     * Sent by Host to Client with authoritative game settings.
     * Settings are locked once game starts.
     */
    data class Settings(
        val flightTime: Long,
        val difficulty: Int,
        val swingSettings: List<Int>, // Flattened list of 27 integers
        val isRallyShrinkEnabled: Boolean = true,
        val gameMode: GameMode = GameMode.CLASSIC,
        // Bonus Mechanics
        val bonusSpinEnabled: Boolean = true,
        val bonusCopyCatEnabled: Boolean = true,
        val bonusSpecialSquaresEnabled: Boolean = true,
        val bonusPowerOutageEnabled: Boolean = true
    ) : GameMessage()
    
    /**
     * Host initiates the game after both players are ready.
     */
    object StartGame : GameMessage()
    
    /**
     * Sent by the player who just swung.
     * Timestamp is relative to sender's clock.
     */
    data class ActionSwing(
        val timestamp: Long,
        val force: Float,
        val swingType: Int, // Ordinal of SwingType
        val sourcePlayerOrdinal: Int, // Ordinal of Player (0 or 1) - Used for loopback filtering
        val spinType: Int = 0 // Ordinal of SpinType (0=NONE for backward compatibility)
    ) : GameMessage()
    
    /**
     * Sent by the receiving device (Receiver Authoritative).
     * Indicates whether the ball was hit or missed.
     */
    data class Result(
        val hitOrMiss: HitResult,
        val scoringPlayer: Player?  // Non-null if miss (who scored the point)
    ) : GameMessage()
    
    /**
     * Periodic synchronization message to prevent desyncs.
     * Either device can send this.
     */
    data class GameStateSync(
        val player1Score: Int,
        val player2Score: Int,
        val currentPhase: GamePhase,
        val servingPlayer: Player,
        // Rally Mode Sync
        val gameMode: GameMode = GameMode.CLASSIC,
        val rallyScore: Int = 0,
        val rallyLives: Int = 3,
        val rallyGridBitmask: Short = 0,
        val rallyLinesBitmask: Byte = 0,
        val opponentRallyGridBitmask: Short = 0,
        val opponentRallyLinesBitmask: Byte = 0,
        val longestRally: Int = 0,
        // Tier sync (encoded as Int, 3 bits per cell)
        val cellTiersBitmask: Int = 0,
        val opponentTiersBitmask: Int = 0,
        val highestTierAchieved: Byte = 0,
        val opponentHighestTier: Byte = 0
    ) : GameMessage()
    
    /**
     * Sent when app is backgrounded.
     */
    data object Pause : GameMessage()
    
    /**
     * Sent when app resumes from pause.
     */
    data object Resume : GameMessage()
    
    /**
     * Sent on graceful disconnect.
     */
    data object PeerLeft : GameMessage()
    
    /**
     * Sent by Host to restart game from Score Screen.
     */
    data object Rematch : GameMessage()

    /**
     * Sent when a player enters the Lobby screen.
     */
    data object PlayerReady : GameMessage()

    /**
     * Sent to exchange player profile information (name, avatar, ring).
     */
    data class PlayerProfile(
        val name: String,
        val avatarIndex: Int,
        val ringIndex: Int = -1  // -1 = no ring selected
    ) : GameMessage()

    /**
     * Sent when a player leaves the Lobby (e.g. to Settings).
     */
    data object PlayerBusy : GameMessage()
}



/**
 * Message type identifiers for byte encoding.
 * Must remain stable across versions for compatibility.
 */
object MessageType {
    const val HANDSHAKE: Byte = 0x01
    const val SETTINGS: Byte = 0x02
    const val START_GAME: Byte = 0x03
    const val ACTION_SWING: Byte = 0x04
    const val RESULT: Byte = 0x05
    const val GAME_STATE_SYNC: Byte = 0x06
    const val PAUSE: Byte = 0x07
    const val RESUME: Byte = 0x08
    const val PEER_LEFT: Byte = 0x09
    const val REMATCH: Byte = 0x0A
    const val PLAYER_READY: Byte = 0x0B
    const val PLAYER_PROFILE: Byte = 0x0C
    const val PLAYER_BUSY: Byte = 0x0D
}

/**
 * Current protocol version.
 * Increment when making breaking changes to message format.
 */
const val PROTOCOL_VERSION = 7
