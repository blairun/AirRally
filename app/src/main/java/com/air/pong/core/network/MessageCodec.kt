package com.air.pong.core.network

import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.Player
import com.air.pong.core.game.HitResult
import com.air.pong.core.game.GameMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes and decodes GameMessage objects to/from byte arrays.
 * Uses compact binary format for low latency over Bluetooth.
 * 
 * Message Format:
 * [Type: 1 byte][Payload: variable bytes]
 */
object MessageCodec {
    
    /**
     * Encodes a GameMessage into a byte array for transmission.
     */
    fun encode(message: GameMessage): ByteArray {
        return when (message) {
            is GameMessage.Handshake -> encodeHandshake(message)
            is GameMessage.Settings -> encodeSettings(message)
            is GameMessage.StartGame -> encodeSimple(MessageType.START_GAME)
            is GameMessage.ActionSwing -> encodeActionSwing(message)
            is GameMessage.Result -> encodeResult(message)
            is GameMessage.GameStateSync -> encodeGameStateSync(message)
            is GameMessage.Pause -> encodeSimple(MessageType.PAUSE)
            is GameMessage.Resume -> encodeSimple(MessageType.RESUME)
            is GameMessage.PeerLeft -> encodeSimple(MessageType.PEER_LEFT)
            is GameMessage.Rematch -> encodeSimple(MessageType.REMATCH)
            is GameMessage.PlayerReady -> encodeSimple(MessageType.PLAYER_READY)
            is GameMessage.PlayerProfile -> encodePlayerProfile(message)
            is GameMessage.PlayerBusy -> encodeSimple(MessageType.PLAYER_BUSY)
        }
    }
    
    /**
     * Decodes a byte array into a GameMessage.
     * @throws IllegalArgumentException if message type is unknown or data is corrupted
     */
    fun decode(data: ByteArray): GameMessage {
        if (data.isEmpty()) {
            throw IllegalArgumentException("Cannot decode empty byte array")
        }
        
        val type = data[0]
        val payload = data.sliceArray(1 until data.size)
        
        return when (type) {
            MessageType.HANDSHAKE -> decodeHandshake(payload)
            MessageType.SETTINGS -> decodeSettings(payload)
            MessageType.START_GAME -> GameMessage.StartGame
            MessageType.ACTION_SWING -> decodeActionSwing(payload)
            MessageType.RESULT -> decodeResult(payload)
            MessageType.GAME_STATE_SYNC -> decodeGameStateSync(payload)
            MessageType.PAUSE -> GameMessage.Pause
            MessageType.RESUME -> GameMessage.Resume
            MessageType.PEER_LEFT -> GameMessage.PeerLeft
            MessageType.REMATCH -> GameMessage.Rematch
            MessageType.PLAYER_READY -> GameMessage.PlayerReady
            MessageType.PLAYER_PROFILE -> decodePlayerProfile(payload)
            MessageType.PLAYER_BUSY -> GameMessage.PlayerBusy
            else -> throw IllegalArgumentException("Unknown message type: 0x${type.toString(16)}")
        }
    }
    
    // ========== Encoding Functions ==========
    
    private fun encodeSimple(type: Byte): ByteArray {
        return byteArrayOf(type)
    }
    
    private fun encodeHandshake(msg: GameMessage.Handshake): ByteArray {
        // [Type: 1][Version: 4]
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.HANDSHAKE)
        buffer.putInt(msg.versionCode)
        return buffer.array()
    }
    
    private fun encodeSettings(msg: GameMessage.Settings): ByteArray {
        // [Type: 1][FlightTime: 8][Difficulty: 4][ListSize: 4][Values: N*4][RallyShrink: 1][GameMode: 1][Bonus: 4]
        val listSize = msg.swingSettings.size
        val buffer = ByteBuffer.allocate(13 + 4 + (listSize * 4) + 1 + 1 + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.SETTINGS)
        buffer.putLong(msg.flightTime)
        buffer.putInt(msg.difficulty)
        buffer.putInt(listSize)
        msg.swingSettings.forEach { buffer.putInt(it) }
        buffer.put(if (msg.isRallyShrinkEnabled) 1.toByte() else 0.toByte())
        buffer.put(msg.gameMode.ordinal.toByte())
        // Bonus mechanics (4 booleans packed into 4 bytes)
        buffer.put(if (msg.bonusSpinEnabled) 1.toByte() else 0.toByte())
        buffer.put(if (msg.bonusCopyCatEnabled) 1.toByte() else 0.toByte())
        buffer.put(if (msg.bonusSpecialSquaresEnabled) 1.toByte() else 0.toByte())
        buffer.put(if (msg.bonusPowerOutageEnabled) 1.toByte() else 0.toByte())
        return buffer.array()
    }
    
    private fun encodeActionSwing(msg: GameMessage.ActionSwing): ByteArray {
        // [Type: 1][Timestamp: 8][Force: 4][SwingType: 4][SourcePlayer: 4][SpinType: 4]
        val buffer = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.ACTION_SWING)
        buffer.putLong(msg.timestamp)
        buffer.putFloat(msg.force)
        buffer.putInt(msg.swingType)
        buffer.putInt(msg.sourcePlayerOrdinal)
        buffer.putInt(msg.spinType)
        return buffer.array()
    }
    
    private fun encodeResult(msg: GameMessage.Result): ByteArray {
        // [Type: 1][HitResult: 1][ScoringPlayer: 1 (0xFF if null)]
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.RESULT)
        buffer.put(msg.hitOrMiss.ordinal.toByte())
        buffer.put(msg.scoringPlayer?.ordinal?.toByte() ?: 0xFF.toByte())
        return buffer.array()
    }
    

    private fun encodeGameStateSync(msg: GameMessage.GameStateSync): ByteArray {
        // [Type: 1][P1Score: 4][P2Score: 4][Phase: 1][ServingPlayer: 1]
        // [GameMode: 1][RallyScore: 4][RallyLives: 1][Grid: 2][Lines: 1][OppGrid: 2][OppLines: 1][LongestRally: 4]
        // [CellTiers: 4][OppTiers: 4][HighestTier: 1][OppHighestTier: 1]
        val buffer = ByteBuffer.allocate(11 + 1 + 4 + 1 + 2 + 1 + 2 + 1 + 4 + 4 + 4 + 1 + 1).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.GAME_STATE_SYNC)
        buffer.putInt(msg.player1Score)
        buffer.putInt(msg.player2Score)
        buffer.put(msg.currentPhase.ordinal.toByte())
        buffer.put(msg.servingPlayer.ordinal.toByte())
        // Rally Params
        buffer.put(msg.gameMode.ordinal.toByte())
        buffer.putInt(msg.rallyScore)
        buffer.put(msg.rallyLives.toByte())
        buffer.putShort(msg.rallyGridBitmask)
        buffer.put(msg.rallyLinesBitmask)
        buffer.putShort(msg.opponentRallyGridBitmask)
        buffer.put(msg.opponentRallyLinesBitmask)
        buffer.putInt(msg.longestRally)
        // Tier fields
        buffer.putInt(msg.cellTiersBitmask)
        buffer.putInt(msg.opponentTiersBitmask)
        buffer.put(msg.highestTierAchieved)
        buffer.put(msg.opponentHighestTier)
        return buffer.array()
    }

    private fun encodePlayerProfile(msg: GameMessage.PlayerProfile): ByteArray {
        // [Type: 1][NameLength: 4][NameBytes: N][AvatarIndex: 4][RingIndex: 4]
        val nameBytes = msg.name.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + nameBytes.size + 4 + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MessageType.PLAYER_PROFILE)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(msg.avatarIndex)
        buffer.putInt(msg.ringIndex)
        return buffer.array()
    }
    
    // ========== Decoding Functions ==========
    
    private fun decodeHandshake(payload: ByteArray): GameMessage.Handshake {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val versionCode = buffer.int
        return GameMessage.Handshake(versionCode)
    }
    
    private fun decodeSettings(payload: ByteArray): GameMessage.Settings {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val flightTime = buffer.long
        val difficulty = buffer.int
        
        val listSize = if (buffer.remaining() >= 4) buffer.int else 0
        val swingSettings = mutableListOf<Int>()
        for (i in 0 until listSize) {
            if (buffer.remaining() >= 4) {
                swingSettings.add(buffer.int)
            }
        }
        
        // Backward compatibility: If no more bytes, default to true
        val isRallyShrinkEnabled = if (buffer.remaining() >= 1) {
            buffer.get() == 1.toByte()
        } else {
            true 
        }

        val gameMode = if (buffer.remaining() >= 1) {
             GameMode.entries[buffer.get().toInt()]
        } else {
             GameMode.CLASSIC
        }
        
        // Bonus Mechanics (new in protocol v7, default to true for backward compat)
        val bonusSpinEnabled = if (buffer.remaining() >= 1) buffer.get() == 1.toByte() else true
        val bonusCopyCatEnabled = if (buffer.remaining() >= 1) buffer.get() == 1.toByte() else true
        val bonusSpecialSquaresEnabled = if (buffer.remaining() >= 1) buffer.get() == 1.toByte() else true
        val bonusPowerOutageEnabled = if (buffer.remaining() >= 1) buffer.get() == 1.toByte() else true
        
        return GameMessage.Settings(
            flightTime, difficulty, swingSettings, isRallyShrinkEnabled, gameMode,
            bonusSpinEnabled, bonusCopyCatEnabled, bonusSpecialSquaresEnabled, bonusPowerOutageEnabled
        )
    }
    
    private fun decodeActionSwing(payload: ByteArray): GameMessage.ActionSwing {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val timestamp = buffer.long
        val force = buffer.float
        val swingType = if (buffer.remaining() >= 4) buffer.int else 0
        val sourcePlayerOrdinal = if (buffer.remaining() >= 4) buffer.int else -1
        val spinType = if (buffer.remaining() >= 4) buffer.int else 0 // Default 0=NONE for backward compat
        return GameMessage.ActionSwing(timestamp, force, swingType, sourcePlayerOrdinal, spinType)
    }
    
    private fun decodeResult(payload: ByteArray): GameMessage.Result {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val hitResult = HitResult.entries[buffer.get().toInt()]
        val scoringPlayerByte = buffer.get()
        val scoringPlayer = if (scoringPlayerByte == 0xFF.toByte()) {
            null
        } else {
            Player.entries[scoringPlayerByte.toInt()]
        }
        return GameMessage.Result(hitResult, scoringPlayer)
    }

    private fun decodeGameStateSync(payload: ByteArray): GameMessage.GameStateSync {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val p1Score = buffer.int
        val p2Score = buffer.int
        val phase = GamePhase.entries[buffer.get().toInt()]
        val servingPlayer = Player.entries[buffer.get().toInt()]
        
        val gameMode = if (buffer.remaining() >= 1) GameMode.entries[buffer.get().toInt()] else GameMode.CLASSIC
        val rallyScore = if (buffer.remaining() >= 4) buffer.int else 0
        val rallyLives = if (buffer.remaining() >= 1) buffer.get().toInt() else 3
        val rallyGridBitmask = if (buffer.remaining() >= 2) buffer.short else 0
        val rallyLinesBitmask = if (buffer.remaining() >= 1) buffer.get() else 0
        val opponentRallyGridBitmask = if (buffer.remaining() >= 2) buffer.short else 0
        val opponentRallyLinesBitmask = if (buffer.remaining() >= 1) buffer.get() else 0
        val longestRally = if (buffer.remaining() >= 4) buffer.int else 0
        // Tier fields (new in protocol v4)
        val cellTiersBitmask = if (buffer.remaining() >= 4) buffer.int else 0
        val opponentTiersBitmask = if (buffer.remaining() >= 4) buffer.int else 0
        val highestTierAchieved = if (buffer.remaining() >= 1) buffer.get() else 0
        val opponentHighestTier = if (buffer.remaining() >= 1) buffer.get() else 0
        
        return GameMessage.GameStateSync(
            p1Score, p2Score, phase, servingPlayer, gameMode, rallyScore, rallyLives, 
            rallyGridBitmask, rallyLinesBitmask, opponentRallyGridBitmask, opponentRallyLinesBitmask, longestRally,
            cellTiersBitmask, opponentTiersBitmask, highestTierAchieved, opponentHighestTier
        )
    }

    private fun decodePlayerProfile(payload: ByteArray): GameMessage.PlayerProfile {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val nameLength = buffer.int
        val nameBytes = ByteArray(nameLength)
        buffer.get(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8)
        val avatarIndex = buffer.int
        val ringIndex = if (buffer.remaining() >= 4) buffer.int else -1
        return GameMessage.PlayerProfile(name, avatarIndex, ringIndex)
    }
}
