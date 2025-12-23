package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Edge case tests for GameEngine.
 * Tests cooldowns, boundary conditions, and unusual game states.
 */
class GameEngineEdgeCasesTest {

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = GameEngine()
        gameEngine.updateSwingSettings(SwingSettings.createDefault())
        gameEngine.setLocalPlayer(isHost = true)
        gameEngine.startGame(GameMode.CLASSIC)
    }

    private fun disableRisk() {
        val settings = gameEngine.swingSettings
        gameEngine.updateSwingSettings(settings.copy(
            softFlatNetRisk = 0, softFlatOutRisk = 0,
            mediumFlatNetRisk = 0, mediumFlatOutRisk = 0,
            hardFlatNetRisk = 0, hardFlatOutRisk = 0,
            softLobNetRisk = 0, softLobOutRisk = 0,
            mediumLobNetRisk = 0, mediumLobOutRisk = 0,
            hardLobNetRisk = 0, hardLobOutRisk = 0,
            softSmashNetRisk = 0, softSmashOutRisk = 0,
            mediumSmashNetRisk = 0, mediumSmashOutRisk = 0,
            hardSmashNetRisk = 0, hardSmashOutRisk = 0
        ))
    }

    // ===== COOLDOWN TESTS =====

    @Test
    fun `test swing ignored during cooldown`() {
        disableRisk()
        // First swing at T=1000
        val result1 = gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(HitResult.HIT, result1)
        
        // Second swing at T=1100 (only 100ms later, should be ignored due to 500ms cooldown)
        val result2 = gameEngine.processSwing(1100L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertNull(result2) // Should be ignored
    }

    @Test
    fun `test swing allowed after cooldown expires`() {
        disableRisk()
        // First swing at T=1000
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        
        // Opponent hits back
        gameEngine.onOpponentHit(1500L, SwingType.MEDIUM_FLAT.ordinal)
        
        // My swing at T=2000 (within hit window, after cooldown) - should work
        val expectedArrival = gameEngine.gameState.value.ballArrivalTimestamp
        val validHitTime = expectedArrival - 100 // Within window
        val result = gameEngine.processSwing(validHitTime, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(HitResult.HIT, result)
    }

    // ===== BOUNDARY TESTS =====

    @Test
    fun `test minimum hit window floor is enforced`() {
        disableRisk()
        gameEngine.updateSettings(1000L, 300, false, false, GameEngine.DEFAULT_SOFT_THRESHOLD, GameEngine.DEFAULT_MEDIUM_THRESHOLD, GameEngine.DEFAULT_HARD_THRESHOLD, true)
        gameEngine.startGame()
        
        // Play many shots to shrink window
        repeat(50) { i ->
            if (i % 2 == 0) {
                // I hit
                gameEngine.processSwing(1000L + i * 1000, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            } else {
                // Opponent hits
                gameEngine.onOpponentHit(1000L + i * 1000, SwingType.HARD_SMASH.ordinal)
            }
        }
        
        // Window should never be below MIN_HIT_WINDOW (200ms)
        assertTrue(gameEngine.getHitWindow() >= GameEngine.MIN_HIT_WINDOW)
    }

    @Test
    fun `test hit window at difficulty boundaries`() {
        disableRisk()
        
        // Test MIN difficulty
        gameEngine.updateSettings(1000L, GameEngine.MIN_DIFFICULTY, false, false, GameEngine.DEFAULT_SOFT_THRESHOLD, GameEngine.DEFAULT_MEDIUM_THRESHOLD, GameEngine.DEFAULT_HARD_THRESHOLD, false)
        assertEquals(GameEngine.MIN_DIFFICULTY.toLong(), gameEngine.getHitWindow())
        
        // Test MAX difficulty
        gameEngine.updateSettings(1000L, GameEngine.MAX_DIFFICULTY, false, false, GameEngine.DEFAULT_SOFT_THRESHOLD, GameEngine.DEFAULT_MEDIUM_THRESHOLD, GameEngine.DEFAULT_HARD_THRESHOLD, false)
        assertEquals(GameEngine.MAX_DIFFICULTY.toLong(), gameEngine.getHitWindow())
    }

    // ===== SCORE EDGE CASES =====

    @Test
    fun `test game does not end at 10-10`() {
        // Fast forward to 10-10 (deuce)
        repeat(10) {
            gameEngine.handleMiss(Player.PLAYER_2)
            gameEngine.startNextServe()
        }
        repeat(10) {
            gameEngine.handleMiss(Player.PLAYER_1)
            gameEngine.startNextServe()
        }
        
        assertEquals(10, gameEngine.gameState.value.player1Score)
        assertEquals(10, gameEngine.gameState.value.player2Score)
        assertNotEquals(GamePhase.GAME_OVER, gameEngine.gameState.value.gamePhase)
    }

    @Test
    fun `test win by two in extended deuce`() {
        // Fast forward to 10-10
        repeat(10) {
            gameEngine.handleMiss(Player.PLAYER_2)
            gameEngine.startNextServe()
        }
        repeat(10) {
            gameEngine.handleMiss(Player.PLAYER_1)
            gameEngine.startNextServe()
        }
        
        // Alternate until 15-14, then P1 wins at 16-14
        repeat(4) {
            gameEngine.handleMiss(Player.PLAYER_2)
            gameEngine.startNextServe()
            gameEngine.handleMiss(Player.PLAYER_1)
            gameEngine.startNextServe()
        }
        
        // 14-14 now
        assertEquals(14, gameEngine.gameState.value.player1Score)
        assertEquals(14, gameEngine.gameState.value.player2Score)
        
        // P1 scores twice to win
        gameEngine.handleMiss(Player.PLAYER_2)
        gameEngine.startNextServe()
        gameEngine.handleMiss(Player.PLAYER_2)
        
        assertEquals(16, gameEngine.gameState.value.player1Score)
        assertEquals(14, gameEngine.gameState.value.player2Score)
        assertEquals(GamePhase.GAME_OVER, gameEngine.gameState.value.gamePhase)
    }

    // ===== SWING TYPE EDGE CASES =====

    @Test
    fun `test swing at exact threshold boundaries`() {
        disableRisk()
        val threshold = GameEngine.DEFAULT_SOFT_THRESHOLD // 14.0f
        
        // Exactly at SOFT/MEDIUM boundary (threshold + 9 = 23)
        gameEngine.startGame()
        gameEngine.processSwing(1000L, 23.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // Just over SOFT/MEDIUM boundary
        gameEngine.processSwing(2000L, 23.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // Exactly at MEDIUM/HARD boundary (threshold + 30 = 44)
        gameEngine.processSwing(3000L, 44.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // Just over MEDIUM/HARD boundary
        gameEngine.processSwing(4000L, 44.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.HARD_FLAT, gameEngine.gameState.value.lastSwingType)
    }

    @Test
    fun `test swing type with extreme tilt values`() {
        disableRisk()
        
        // Extreme LOB (very high positive Z)
        gameEngine.startGame()
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 10.0f)
        assertTrue(gameEngine.gameState.value.lastSwingType?.isLob() == true)
        
        // Extreme SMASH (very low negative Z)
        gameEngine.processSwing(2000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -10.0f)
        assertTrue(gameEngine.gameState.value.lastSwingType?.isSmash() == true)
    }

    // ===== RALLY LENGTH EDGE CASES =====

    @Test
    fun `test rally length resets after point`() {
        disableRisk()
        gameEngine.startGame()
        
        // Serve + return = 2 shots
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        gameEngine.onOpponentHit(2000L, SwingType.SOFT_FLAT.ordinal)
        assertEquals(2, gameEngine.gameState.value.currentRallyLength)
        
        // Point ends
        gameEngine.handleMiss(Player.PLAYER_1)
        gameEngine.startNextServe()
        
        // Rally length should reset to 0
        assertEquals(0, gameEngine.gameState.value.currentRallyLength)
    }

    @Test
    fun `test longest rally tracking`() {
        // Simpler test: just verify longestRally is updated correctly
        disableRisk()
        gameEngine.startGame()
        
        // Serve
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(1, gameEngine.gameState.value.currentRallyLength)
        
        // Opponent returns
        gameEngine.onOpponentHit(3000L, SwingType.SOFT_FLAT.ordinal)
        assertEquals(2, gameEngine.gameState.value.currentRallyLength)
        
        // I return - need to be within hit window
        val arrivalTime = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(arrivalTime - 100, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(3, gameEngine.gameState.value.currentRallyLength)
        
        // End point - longestRally should be 3
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(3, gameEngine.gameState.value.longestRally)
        
        // Verify rally length resets after point
        gameEngine.startNextServe()
        assertEquals(0, gameEngine.gameState.value.currentRallyLength)
        
        // Longest rally should still be 3 after reset
        assertEquals(3, gameEngine.gameState.value.longestRally)
    }

    // ===== GAME MODE TESTS =====

    @Test
    fun `test mode persists through rematch`() {
        gameEngine.startGame(GameMode.RALLY)
        assertEquals(GameMode.RALLY, gameEngine.gameState.value.gameMode)
        
        // Rematch keeps same mode if not specified
        gameEngine.startGame()
        assertEquals(GameMode.RALLY, gameEngine.gameState.value.gameMode)
        
        // Explicit mode change
        gameEngine.startGame(GameMode.CLASSIC)
        assertEquals(GameMode.CLASSIC, gameEngine.gameState.value.gameMode)
    }

    // ===== SETTINGS UPDATE TESTS =====

    @Test
    fun `test swing settings update is applied`() {
        // Simpler test: just verify settings are applied to gameEngine
        val original = gameEngine.swingSettings
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, original.softFlatFlight, 0.01f)
        
        // Update settings
        val newSettings = original.copy(softFlatFlight = 2.0f)
        gameEngine.updateSwingSettings(newSettings)
        
        // Verify settings are applied
        assertEquals(2.0f, gameEngine.swingSettings.softFlatFlight, 0.01f)
        
        // Other fields should be unchanged
        assertEquals(original.mediumFlatFlight, gameEngine.swingSettings.mediumFlatFlight, 0.01f)
        assertEquals(original.hardSmashNetRisk, gameEngine.swingSettings.hardSmashNetRisk)
    }
}
