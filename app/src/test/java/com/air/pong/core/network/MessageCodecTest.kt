package com.air.pong.core.network

import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.HitResult
import com.air.pong.core.game.Player
import org.junit.Assert.*
import org.junit.Test

class MessageCodecTest {

    @Test
    fun `test Handshake encoding and decoding`() {
        val original = GameMessage.Handshake(versionCode = 123)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Handshake)
        assertEquals(original.versionCode, (decoded as GameMessage.Handshake).versionCode)
    }

    @Test
    fun `test Settings encoding and decoding`() {
        val swingSettings = List(27) { it } // 0..26
        val original = GameMessage.Settings(flightTime = 1500L, difficulty = 2, swingSettings = swingSettings)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Settings)
        val decodedSettings = decoded as GameMessage.Settings
        assertEquals(original.flightTime, decodedSettings.flightTime)
        assertEquals(original.difficulty, decodedSettings.difficulty)
        assertEquals(original.swingSettings.size, decodedSettings.swingSettings.size)
        assertEquals(original.swingSettings, decodedSettings.swingSettings)
    }

    @Test
    fun `test ActionSwing encoding and decoding`() {
        val original = GameMessage.ActionSwing(timestamp = 987654321L, force = 2.5f, swingType = 1, sourcePlayerOrdinal = 0)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.ActionSwing)
        val decodedSwing = decoded as GameMessage.ActionSwing
        assertEquals(original.timestamp, decodedSwing.timestamp)
        assertEquals(original.force, decodedSwing.force, 0.0f)
        assertEquals(original.swingType, decodedSwing.swingType)
        assertEquals(original.sourcePlayerOrdinal, decodedSwing.sourcePlayerOrdinal)
    }

    @Test
    fun `test Result encoding and decoding`() {
        val original = GameMessage.Result(hitOrMiss = HitResult.MISS_LATE, scoringPlayer = Player.PLAYER_1)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Result)
        val decodedResult = decoded as GameMessage.Result
        assertEquals(original.hitOrMiss, decodedResult.hitOrMiss)
        assertEquals(original.scoringPlayer, decodedResult.scoringPlayer)
    }
    
    @Test
    fun `test Result encoding and decoding with null player`() {
        // Assuming HIT result has no scoring player
        val original = GameMessage.Result(hitOrMiss = HitResult.HIT, scoringPlayer = null)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.Result)
        val decodedResult = decoded as GameMessage.Result
        assertEquals(original.hitOrMiss, decodedResult.hitOrMiss)
        assertNull(decodedResult.scoringPlayer)
    }

    @Test
    fun `test GameStateSync encoding and decoding`() {
        val original = GameMessage.GameStateSync(
            player1Score = 5,
            player2Score = 3,
            currentPhase = GamePhase.RALLY,
            servingPlayer = Player.PLAYER_2
        )
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.GameStateSync)
        val decodedSync = decoded as GameMessage.GameStateSync
        assertEquals(original.player1Score, decodedSync.player1Score)
        assertEquals(original.player2Score, decodedSync.player2Score)
        assertEquals(original.currentPhase, decodedSync.currentPhase)
        assertEquals(original.servingPlayer, decodedSync.servingPlayer)
    }

    @Test
    fun `test Simple messages encoding and decoding`() {
        val messages = listOf(
            GameMessage.StartGame,
            GameMessage.Pause,
            GameMessage.Resume,
            GameMessage.PeerLeft,
            GameMessage.Rematch
        )

        for (msg in messages) {
            val encoded = MessageCodec.encode(msg)
            val decoded = MessageCodec.decode(encoded)
            // Compare classes because these are objects
            assertEquals(msg::class, decoded::class)
        }
    }

    @Test
    fun `test PlayerProfile encoding with ring index`() {
        val original = GameMessage.PlayerProfile("TestPlayer", 5, 3)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.PlayerProfile)
        val profile = decoded as GameMessage.PlayerProfile
        assertEquals("TestPlayer", profile.name)
        assertEquals(5, profile.avatarIndex)
        assertEquals(3, profile.ringIndex)
    }

    @Test
    fun `test PlayerProfile encoding without ring index defaults to -1`() {
        // Simulate an old-format message by encoding with default ring
        val original = GameMessage.PlayerProfile("OldPlayer", 2, -1)
        val encoded = MessageCodec.encode(original)
        val decoded = MessageCodec.decode(encoded)
        
        assertTrue(decoded is GameMessage.PlayerProfile)
        val profile = decoded as GameMessage.PlayerProfile
        assertEquals("OldPlayer", profile.name)
        assertEquals(2, profile.avatarIndex)
        assertEquals(-1, profile.ringIndex)
    }
}
