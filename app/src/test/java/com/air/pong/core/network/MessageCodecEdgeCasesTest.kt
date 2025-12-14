package com.air.pong.core.network

import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.HitResult
import com.air.pong.core.game.Player
import com.air.pong.core.game.GameMode
import org.junit.Assert.*
import org.junit.Test

/**
 * Edge case tests for MessageCodec.
 * Tests boundary conditions, backward compatibility, and error handling.
 */
class MessageCodecEdgeCasesTest {

    @Test
    fun `test Settings with empty swing settings list`() {
        // Edge case: Empty list should still encode/decode correctly
        val original = GameMessage.Settings(
            flightTime = 700L,
            difficulty = 500,
            swingSettings = emptyList()
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Settings)
        val decodedSettings = decoded as GameMessage.Settings
        assertEquals(original.flightTime, decodedSettings.flightTime)
        assertEquals(original.difficulty, decodedSettings.difficulty)
        assertTrue(decodedSettings.swingSettings.isEmpty())
    }

    @Test
    fun `test Settings with max values`() {
        // Boundary: Maximum values for all fields
        val original = GameMessage.Settings(
            flightTime = Long.MAX_VALUE,
            difficulty = Int.MAX_VALUE,
            swingSettings = List(100) { Int.MAX_VALUE }, // More than typical 27
            isRallyShrinkEnabled = true,
            gameMode = GameMode.RALLY
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Settings)
        val decodedSettings = decoded as GameMessage.Settings
        assertEquals(original.flightTime, decodedSettings.flightTime)
        assertEquals(original.difficulty, decodedSettings.difficulty)
        assertEquals(original.swingSettings.size, decodedSettings.swingSettings.size)
        assertEquals(original.isRallyShrinkEnabled, decodedSettings.isRallyShrinkEnabled)
        assertEquals(original.gameMode, decodedSettings.gameMode)
    }

    @Test
    fun `test Settings with negative values`() {
        // Edge case: Negative values (might occur in edge cases)
        val original = GameMessage.Settings(
            flightTime = -1L,
            difficulty = -100,
            swingSettings = List(27) { -it }
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Settings)
        val decodedSettings = decoded as GameMessage.Settings
        assertEquals(original.flightTime, decodedSettings.flightTime)
        assertEquals(original.difficulty, decodedSettings.difficulty)
    }

    @Test
    fun `test ActionSwing with zero timestamp`() {
        // Edge case: Zero timestamp
        val original = GameMessage.ActionSwing(
            timestamp = 0L,
            force = 0f,
            swingType = 0,
            sourcePlayerOrdinal = 0
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.ActionSwing)
        assertEquals(0L, (decoded as GameMessage.ActionSwing).timestamp)
    }

    @Test
    fun `test ActionSwing with max force`() {
        // Boundary: Maximum force value
        val original = GameMessage.ActionSwing(
            timestamp = System.currentTimeMillis(),
            force = Float.MAX_VALUE,
            swingType = 8, // HARD_SMASH ordinal
            sourcePlayerOrdinal = 1
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.ActionSwing)
        assertEquals(Float.MAX_VALUE, (decoded as GameMessage.ActionSwing).force, 0.0f)
    }

    @Test
    fun `test GameStateSync with all game phases`() {
        // Test all possible game phases encode/decode correctly
        for (phase in GamePhase.entries) {
            val original = GameMessage.GameStateSync(
                player1Score = 0,
                player2Score = 0,
                currentPhase = phase,
                servingPlayer = Player.PLAYER_1
            )
            val encoded = MessageCodec.encode(original)
            val decoded = MessageCodec.decode(encoded)
            
            assertTrue(decoded is GameMessage.GameStateSync)
            assertEquals(phase, (decoded as GameMessage.GameStateSync).currentPhase)
        }
    }

    @Test
    fun `test GameStateSync with all hit results in Result message`() {
        // Test all possible hit results
        for (result in HitResult.entries) {
            val player = if (result != HitResult.HIT && result != HitResult.PENDING) Player.PLAYER_1 else null
            val original = GameMessage.Result(
                hitOrMiss = result,
                scoringPlayer = player
            )
            val encoded = MessageCodec.encode(original)
            val decoded = MessageCodec.decode(encoded)
            
            assertTrue(decoded is GameMessage.Result)
            assertEquals(result, (decoded as GameMessage.Result).hitOrMiss)
        }
    }

    @Test
    fun `test GameStateSync with Rally mode data`() {
        // Rally mode sync with grid and lines data
        val original = GameMessage.GameStateSync(
            player1Score = 5,
            player2Score = 3,
            currentPhase = GamePhase.RALLY,
            servingPlayer = Player.PLAYER_2,
            gameMode = GameMode.RALLY,
            rallyScore = 1500,
            rallyLives = 2,
            rallyGridBitmask = 0b111_000_111.toShort(), // Corners filled
            rallyLinesBitmask = 0b00000011.toByte(), // Two lines complete
            opponentRallyGridBitmask = 0b000_111_000.toShort(),
            opponentRallyLinesBitmask = 0b00000001.toByte(),
            longestRally = 42
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.GameStateSync)
        val decodedSync = decoded as GameMessage.GameStateSync
        assertEquals(GameMode.RALLY, decodedSync.gameMode)
        assertEquals(1500, decodedSync.rallyScore)
        assertEquals(2, decodedSync.rallyLives)
        assertEquals(0b111_000_111.toShort(), decodedSync.rallyGridBitmask)
        assertEquals(0b00000011.toByte(), decodedSync.rallyLinesBitmask)
        assertEquals(42, decodedSync.longestRally)
    }

    @Test
    fun `test GameStateSync with max scores`() {
        // Boundary: Very high scores (edge case for long games)
        val original = GameMessage.GameStateSync(
            player1Score = 99,
            player2Score = 97,
            currentPhase = GamePhase.RALLY,
            servingPlayer = Player.PLAYER_1,
            longestRally = 500
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.GameStateSync)
        val decodedSync = decoded as GameMessage.GameStateSync
        assertEquals(99, decodedSync.player1Score)
        assertEquals(97, decodedSync.player2Score)
        assertEquals(500, decodedSync.longestRally)
    }

    @Test
    fun `test PlayerProfile with long name`() {
        // Edge case: Very long player name
        val longName = "A".repeat(100)
        val original = GameMessage.PlayerProfile(
            name = longName,
            avatarIndex = 5
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.PlayerProfile)
        val decodedProfile = decoded as GameMessage.PlayerProfile
        assertEquals(longName, decodedProfile.name)
        assertEquals(5, decodedProfile.avatarIndex)
    }

    @Test
    fun `test PlayerProfile with empty name`() {
        // Edge case: Empty player name
        val original = GameMessage.PlayerProfile(
            name = "",
            avatarIndex = 0
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.PlayerProfile)
        assertEquals("", (decoded as GameMessage.PlayerProfile).name)
    }

    @Test
    fun `test PlayerProfile with unicode name`() {
        // Edge case: Unicode characters in name
        val unicodeName = "Player 日本語 🎾"
        val original = GameMessage.PlayerProfile(
            name = unicodeName,
            avatarIndex = 3
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.PlayerProfile)
        assertEquals(unicodeName, (decoded as GameMessage.PlayerProfile).name)
    }
}
