
package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = GameEngine()
        gameEngine.setLocalPlayer(isHost = true) // Assume Player 1 is Host
        gameEngine.startGame()
    }

    @Test
    fun `test initial state`() {
        val state = gameEngine.gameState.value
        assertEquals(0, state.player1Score)
        assertEquals(0, state.player2Score)
        assertEquals(Player.PLAYER_1, state.servingPlayer)
        assertEquals(GamePhase.WAITING_FOR_SERVE, state.gamePhase)
        assertTrue(state.isMyTurn) // Host serves first
    }

    @Test
    fun `test serve rotation normal`() {
        // 0-0, P1 serves
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)

        // 1-0, P1 serves
        gameEngine.handleMiss(Player.PLAYER_2) // P2 missed, P1 scores
        assertEquals(1, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)

        // 2-0, P2 serves
        gameEngine.handleMiss(Player.PLAYER_2) // P2 missed, P1 scores
        assertEquals(2, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 2-1, P2 serves
        gameEngine.handleMiss(Player.PLAYER_1) // P1 missed, P2 scores
        assertEquals(1, gameEngine.gameState.value.player2Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 2-2, P1 serves
        gameEngine.handleMiss(Player.PLAYER_1) // P1 missed, P2 scores
        assertEquals(2, gameEngine.gameState.value.player2Score)
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)
    }

    @Test
    fun `test deuce serve rotation`() {
        // Fast forward to 10-10
        repeat(10) { gameEngine.handleMiss(Player.PLAYER_2) } // P1: 10
        repeat(10) { gameEngine.handleMiss(Player.PLAYER_1) } // P2: 10
        
        assertEquals(10, gameEngine.gameState.value.player1Score)
        assertEquals(10, gameEngine.gameState.value.player2Score)
        
        // 10-10, P1 serves
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)

        // 11-10, P2 serves (Deuce: rotate every 1)
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(11, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 11-11, P1 serves
        gameEngine.handleMiss(Player.PLAYER_1)
        assertEquals(11, gameEngine.gameState.value.player2Score)
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)
    }

    @Test
    fun `test win condition`() {
        // 10-0
        // 10-0
        repeat(10) { 
            gameEngine.handleMiss(Player.PLAYER_2)
            gameEngine.startNextServe() // Skip cooldown
        }
        assertEquals(GamePhase.WAITING_FOR_SERVE, gameEngine.gameState.value.gamePhase)

        // 11-0, Game Over
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(11, gameEngine.gameState.value.player1Score)
        assertEquals(GamePhase.GAME_OVER, gameEngine.gameState.value.gamePhase)
    }

    @Test
    fun `test win by two`() {
        // 10-10
        repeat(10) { 
            gameEngine.handleMiss(Player.PLAYER_2)
            gameEngine.startNextServe()
        }
        repeat(10) { 
            gameEngine.handleMiss(Player.PLAYER_1)
            gameEngine.startNextServe()
        }

        // 11-10, Not Game Over
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(GamePhase.POINT_SCORED, gameEngine.gameState.value.gamePhase)
        gameEngine.startNextServe()
        assertEquals(GamePhase.WAITING_FOR_SERVE, gameEngine.gameState.value.gamePhase)

        // 11-11, Not Game Over
        gameEngine.handleMiss(Player.PLAYER_1)
        assertEquals(GamePhase.POINT_SCORED, gameEngine.gameState.value.gamePhase)
        gameEngine.startNextServe()
        assertEquals(GamePhase.WAITING_FOR_SERVE, gameEngine.gameState.value.gamePhase)

        // 12-11, Not Game Over
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(GamePhase.POINT_SCORED, gameEngine.gameState.value.gamePhase)
        gameEngine.startNextServe()
        assertEquals(GamePhase.WAITING_FOR_SERVE, gameEngine.gameState.value.gamePhase)

        // 13-11, Game Over
        gameEngine.handleMiss(Player.PLAYER_2)
        assertEquals(GamePhase.GAME_OVER, gameEngine.gameState.value.gamePhase)
    }

    @Test
    fun `test timing window`() {
        // Explicitly set flight time to avoid relying on default
        val testFlightTime = 1000L
        gameEngine.updateSettings(testFlightTime, 1, isDebugMode = false, useDebugTones = false) // Difficulty 1 = Medium

        // P1 serves at T=1000. Arrival = 1000 + 1000 = 2000.
        // Note: Must start > 500ms to avoid cooldown check against initial 0
        val serveTime = 1000L
        val expectedArrival = serveTime + testFlightTime
        
        gameEngine.processSwing(serveTime, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) 
        assertEquals(expectedArrival, gameEngine.gameState.value.ballArrivalTimestamp)
        
        // Test HIT window (Arrival +/- Window)
        val window = GameEngine.WINDOW_MEDIUM
        
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival - window))
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival))
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival + window))
        
        // Test EARLY (< Arrival - Window)
        assertEquals(HitResult.MISS_EARLY, gameEngine.checkHitTiming(expectedArrival - window - 1))
        
        // Test LATE (> Arrival + Window)
        assertEquals(HitResult.MISS_LATE, gameEngine.checkHitTiming(expectedArrival + window + 1))
    }

    @Test
    fun `test swing classification`() {
        // Force > 20 -> HARD
        // Force > 17 -> MEDIUM
        // Else -> SOFT
        
        // GravZ > 3.0 -> LOB
        // GravZ < -3.0 -> SPIKE
        // Else -> FLAT
        
        // 1. SOFT FLAT (Force=10, GravZ=0)
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 2. MEDIUM FLAT (Force=18, GravZ=0)
        gameEngine.processSwing(2000L, 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 3. HARD FLAT (Force=25, GravZ=0)
        gameEngine.processSwing(3000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.HARD_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 4. SOFT LOB (Force=10, GravZ=4.0)
        gameEngine.processSwing(4000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 4.0f)
        assertEquals(SwingType.SOFT_LOB, gameEngine.gameState.value.lastSwingType)
        
        // 5. HARD SPIKE (Force=25, GravZ=-4.0)
        gameEngine.processSwing(5000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -4.0f)
        assertEquals(SwingType.HARD_SPIKE, gameEngine.gameState.value.lastSwingType)
    }

    @Test
    fun `test flight time modifiers`() {
        // Set base flight time to 1000ms explicitly (default is 700ms)
        val baseFlightTime = 1000L
        gameEngine.updateSettings(baseFlightTime, 1, isDebugMode = false, useDebugTones = false)
        
        // 1. MEDIUM_FLAT (1.0x) -> 1000ms
        gameEngine.onOpponentHit(0L, SwingType.MEDIUM_FLAT.ordinal)
        assertEquals((baseFlightTime * 1.0f).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
        
        // 2. SOFT_LOB (1.4x) -> 1400ms
        gameEngine.onOpponentHit(0L, SwingType.SOFT_LOB.ordinal)
        assertEquals((baseFlightTime * 1.4f).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
        
        // 3. HARD_SPIKE (0.4x) -> 400ms
        gameEngine.onOpponentHit(0L, SwingType.HARD_SPIKE.ordinal)
        assertEquals((baseFlightTime * 0.4f).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
    }

    @Test
    fun `test window shrink`() {
        // Base Window (Medium Difficulty)
        val testFlightTime = 1000L
        gameEngine.updateSettings(testFlightTime, 1, isDebugMode = false, useDebugTones = false) // Difficulty 1 = Medium
        val baseWindow = GameEngine.WINDOW_MEDIUM
        
        // 1. Incoming SOFT_FLAT (0% shrink) -> 500ms
        gameEngine.onOpponentHit(0L, SwingType.SOFT_FLAT.ordinal)
        assertEquals(baseWindow, gameEngine.getHitWindow())
        
        // 2. Incoming MEDIUM_FLAT (20% shrink) -> 400ms
        gameEngine.onOpponentHit(0L, SwingType.MEDIUM_FLAT.ordinal)
        assertEquals((baseWindow * 0.8f).toLong(), gameEngine.getHitWindow())
        
        // 3. Incoming HARD_SPIKE (60% shrink) -> 200ms
        gameEngine.onOpponentHit(0L, SwingType.HARD_SPIKE.ordinal)
        assertEquals((baseWindow * 0.4f).toLong(), gameEngine.getHitWindow())
    }

    @Test
    fun `test risk logic`() {
        // 1. Safe Shot (SOFT_FLAT) - Should NEVER fail risk check
        // We need to be in RALLY or SERVING. Let's serve.
        // SOFT_FLAT: Force=10, GravZ=0
        
        // Run 100 times to be sure
        repeat(100) { index ->
            // Reset state to Waiting for Serve
            gameEngine.startGame() 
            
            // Increment timestamp to avoid cooldown (1000, 2000, 3000...)
            val timestamp = 1000L + (index * 1000L)
            val result = gameEngine.processSwing(timestamp, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            assertEquals(HitResult.HIT, result)
        }
        
        // 2. Risky Shot (HARD_SPIKE) - Should EVENTUALLY fail
        // HARD_SPIKE: Force=25, GravZ=-4.0
        // Risk: 30% Net, 10% Out -> 40% Fail Rate
        
        var failures = 0
        repeat(100) { 
            // Create fresh engine to reset lastPointEndedTimestamp
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            
            // Use a valid timestamp > 500ms
            val result = engine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -4.0f)
            
            assertNotNull("Swing was ignored (returned null)", result)
            
            if (result == HitResult.MISS_NET || result == HitResult.MISS_OUT) {
                failures++
            }
        }
        
        // Statistically, 100 runs with 40% fail rate should produce at least 1 failure.
        // It's technically possible (though unlikely) to have 0 failures, but for a unit test 
        // we might want to be careful. However, 0.6^100 is astronomically small.
        assertTrue("Expected some failures due to risk, but got $failures", failures > 0)
    }
    @Test
    fun `test risk probabilities`() {
        // Run a large number of simulations to verify probabilities
        val iterations = 10000
        
        // 1. MEDIUM_FLAT: 0% Net, 5% Out
        var mediumFlatNet = 0
        var mediumFlatOut = 0
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=18 (Medium), GravZ=0 (Flat)
            val result = engine.processSwing(1000L, 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (result == HitResult.MISS_NET) mediumFlatNet++
            if (result == HitResult.MISS_OUT) mediumFlatOut++
        }
        
        assertEquals("MEDIUM_FLAT should never hit net", 0, mediumFlatNet)
        // Expected ~500 (5%). Allow margin of error (400-600)
        assertTrue("MEDIUM_FLAT Out Rate should be ~5% (actual: $mediumFlatOut)", mediumFlatOut in 400..600)
        
        // 2. HARD_FLAT: 5% Net, 15% Out
        var hardFlatNet = 0
        var hardFlatOut = 0
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=25 (Hard), GravZ=0 (Flat)
            val result = engine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (result == HitResult.MISS_NET) hardFlatNet++
            if (result == HitResult.MISS_OUT) hardFlatOut++
        }
        
        // Expected Net ~500 (5%). Allow 400-600
        assertTrue("HARD_FLAT Net Rate should be ~5% (actual: $hardFlatNet)", hardFlatNet in 400..600)
        // Expected Out ~1500 (15%). Allow 1350-1650
        assertTrue("HARD_FLAT Out Rate should be ~15% (actual: $hardFlatOut)", hardFlatOut in 1350..1650)
    }
}
