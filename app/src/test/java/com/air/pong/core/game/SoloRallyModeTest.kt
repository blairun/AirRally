package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.air.pong.core.game.GameEngine
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.Player
import com.air.pong.core.game.SwingType

/**
 * Unit tests for Solo Rally mode.
 * 
 * Solo Rally behavior:
 * - Single player practices against simulated wall
 * - 3 starting lives (same as co-op Rally)
 * - 3×3 grid marked by swing type
 * - Line completions award bonuses
 * - No opponent grid (single player mode)
 * - isMyTurn always true after serve
 * - Hit window shrinks 50ms per tier achieved
 */
class SoloRallyModeTest {

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = GameEngine()
        gameEngine.updateSwingSettings(SwingSettings.createDefault())
        gameEngine.setLocalPlayer(isHost = true) // Always host in Solo
        // Start in SOLO_RALLY mode
        gameEngine.startGame(GameMode.SOLO_RALLY)
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

    @Test
    fun `test solo rally mode initialization`() {
        val state = gameEngine.gameState.value
        assertEquals(GameMode.SOLO_RALLY, state.gameMode)
        
        val soloState = state.modeState as? ModeState.SoloRallyState
        assertNotNull("modeState should be SoloRallyState", soloState)
        
        assertEquals("Score should start at 0", 0, soloState?.score)
        assertEquals("Lives should start at 3", GameEngine.RALLY_STARTING_LIVES, soloState?.lives)
        assertTrue("Grid should be empty", soloState?.grid?.all { !it } ?: false)
        assertEquals("Highest tier should be 0", 0, soloState?.highestTier)
        assertEquals(0, state.longestRally)
    }
    
    @Test
    fun `test solo rally has no opponent grid`() {
        val state = gameEngine.gameState.value
        val soloState = state.modeState as? ModeState.SoloRallyState
        assertNotNull(soloState)
        
        // Solo Rally should have a single grid (player's grid only)
        assertNotNull("Player grid should exist", soloState?.grid)
        assertEquals("Grid should have 9 cells", 9, soloState?.grid?.size)
        
        // SoloRallyState does not have an opponentGrid property
        // This is verified by the class structure (no opponentGrid field)
    }

    @Test
    fun `test solo rally serve awards points but not grid`() {
        // Note: Serves do NOT update grid - same as co-op Rally
        disableRisk()
        
        val serveTime = 1000L
        val serveResult = gameEngine.processSwing(serveTime, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Serve should succeed", HitResult.HIT, serveResult)
        
        val state = gameEngine.gameState.value
        val soloState = state.modeState as? ModeState.SoloRallyState
        
        // Grid should remain empty (serves don't mark grid)
        assertTrue("Grid should be empty after serve", soloState?.grid?.all { !it } ?: false)
        
        // Score should be tier-0 points (1 point) after serve
        assertEquals("Score should be 1 after serve", 1, soloState?.score)
    }

    @Test
    fun `test solo rally isMyTurn stays true after serve`() {
        disableRisk()
        
        // Before serve
        var state = gameEngine.gameState.value
        assertTrue("isMyTurn should be true before serve (host)", state.isMyTurn)
        
        // After serve
        val serveResult = gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(HitResult.HIT, serveResult)
        
        state = gameEngine.gameState.value
        assertTrue("isMyTurn should remain true after serve in Solo Rally", state.isMyTurn)
    }

    @Test
    fun `test solo rally hit marks grid`() {
        disableRisk()
        
        // Serve first
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        
        // Get ball arrival time
        val ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        
        // Hit (this should mark the grid)
        // gridIndex for MEDIUM_FLAT = row 0, col 1 = index 1
        val hitResult = gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Hit should succeed", HitResult.HIT, hitResult)
        
        val state = gameEngine.gameState.value
        val soloState = state.modeState as? ModeState.SoloRallyState
        
        val mediumFlatIndex = SwingType.MEDIUM_FLAT.getGridIndex()
        assertTrue("Grid should be marked at MEDIUM_FLAT index", soloState?.grid?.get(mediumFlatIndex) ?: false)
        
        // Score should increase (serve=1 + hit=1)
        assertEquals("Score should be 2", 2, soloState?.score)
    }

    @Test
    fun `test solo rally miss decreases lives`() {
        disableRisk()
        
        // Serve
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        
        // Verify initial lives
        var soloState = gameEngine.gameState.value.modeState as? ModeState.SoloRallyState
        assertEquals(GameEngine.RALLY_STARTING_LIVES, soloState?.lives)
        
        // Miss
        gameEngine.handleMiss(Player.PLAYER_1)
        
        soloState = gameEngine.gameState.value.modeState as? ModeState.SoloRallyState
        assertEquals("Lives should be 2 after miss", 2, soloState?.lives)
    }

    @Test
    fun `test solo rally game over when lives reach zero`() {
        disableRisk()
        
        // Lose all 3 lives
        gameEngine.handleMiss(Player.PLAYER_1)
        
        var state = gameEngine.gameState.value
        assertEquals("Should be in POINT_SCORED phase", GamePhase.POINT_SCORED, state.gamePhase)
        
        gameEngine.startNextServe()
        gameEngine.handleMiss(Player.PLAYER_1)
        
        state = gameEngine.gameState.value  
        assertEquals("Should still be in POINT_SCORED phase", GamePhase.POINT_SCORED, state.gamePhase)
        
        gameEngine.startNextServe()
        gameEngine.handleMiss(Player.PLAYER_1)
        
        state = gameEngine.gameState.value
        assertEquals("Game should be over after 3 misses", GamePhase.GAME_OVER, state.gamePhase)
        
        val soloState = state.modeState as? ModeState.SoloRallyState
        assertEquals("Lives should be 0", 0, soloState?.lives)
    }

    @Test
    fun `test solo rally hit window shrink per tier`() {
        // Solo Rally: 50ms shrink per tier achieved
        val state = gameEngine.gameState.value
        val soloState = state.modeState as? ModeState.SoloRallyState
        
        // Initial tier = 0, so no shrink
        val initialShrink = SoloRallyModeStrategy().getHitWindowShrink(state)
        assertEquals("Initial shrink should be 0", 0L, initialShrink)
    }

    @Test
    fun `test solo rally longest rally update`() {
        disableRisk()
        
        // Serve (Rally=1)
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally length should be 1 after serve", 1, gameEngine.gameState.value.currentRallyLength)
        
        // Hit at arrival (Rally=2)
        var ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally length should be 2", 2, gameEngine.gameState.value.currentRallyLength)
        assertEquals("longestRally should be 2", 2, gameEngine.gameState.value.longestRally)
        
        // Hit again (Rally=3)
        ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally length should be 3", 3, gameEngine.gameState.value.currentRallyLength)
        assertEquals("longestRally should be 3", 3, gameEngine.gameState.value.longestRally)
    }

    @Test
    fun `test solo rally rematch reset`() {
        disableRisk()
        
        // Build up some state
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // Serve
        val arrival = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(arrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // Hit
        
        var state = gameEngine.gameState.value
        var soloState = state.modeState as? ModeState.SoloRallyState
        assertTrue("Score should be > 0", (soloState?.score ?: 0) > 0)
        assertTrue("Grid should have marks", soloState?.grid?.any { it } ?: false)
        
        // Trigger Rematch
        gameEngine.rematch(isInitiator = true)
        
        state = gameEngine.gameState.value
        soloState = state.modeState as? ModeState.SoloRallyState
        
        // Verify Reset
        assertEquals("Score should be 0", 0, soloState?.score)
        assertEquals("Lives should be reset", GameEngine.RALLY_STARTING_LIVES, soloState?.lives)
        assertTrue("Grid should be cleared", soloState?.grid?.all { !it } ?: false)
        assertEquals("longestRally should be 0", 0, state.longestRally)
    }
    
    @Test
    fun `test solo rally has no risk`() {
        // SoloRallyModeStrategy.hasRisk should be false
        val strategy = SoloRallyModeStrategy()
        assertFalse("Solo Rally should have no risk", strategy.hasRisk)
    }
    
    @Test
    fun `test solo rally display score`() {
        val state = gameEngine.gameState.value
        val strategy = SoloRallyModeStrategy()
        
        // Initial score
        val displayScore = strategy.getDisplayScore(state)
        assertEquals("Display score should be 0 initially", 0, displayScore)
    }
    
    @Test
    fun `test solo rally lives getter`() {
        val state = gameEngine.gameState.value
        val strategy = SoloRallyModeStrategy()
        
        val lives = strategy.getLives(state)
        assertEquals("Lives should be 3 initially", GameEngine.RALLY_STARTING_LIVES, lives)
    }
}
