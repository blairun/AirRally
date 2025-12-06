package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.air.pong.core.game.GameEngine
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.Player
import com.air.pong.core.game.SwingType

class RallyModeTest {

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = GameEngine()
        gameEngine.setLocalPlayer(isHost = true) // Assume Player 1 (Host)
        // Start in RALLY mode
        gameEngine.startGame(GameMode.RALLY)
    }
    
    private fun disableRisk() {
        SwingSettings.softFlatNetRisk = 0
        SwingSettings.softFlatOutRisk = 0
        SwingSettings.mediumFlatNetRisk = 0
        SwingSettings.mediumFlatOutRisk = 0
        SwingSettings.hardFlatNetRisk = 0
        SwingSettings.hardFlatOutRisk = 0
        
        SwingSettings.softLobNetRisk = 0
        SwingSettings.softLobOutRisk = 0
        SwingSettings.mediumLobNetRisk = 0
        SwingSettings.mediumLobOutRisk = 0
        SwingSettings.hardLobNetRisk = 0
        SwingSettings.hardLobOutRisk = 0
        
        SwingSettings.softSmashNetRisk = 0
        SwingSettings.softSmashOutRisk = 0
        SwingSettings.mediumSmashNetRisk = 0
        SwingSettings.mediumSmashOutRisk = 0
        SwingSettings.hardSmashNetRisk = 0
        SwingSettings.hardSmashOutRisk = 0
    }

    @Test
    fun `test rally mode initialization`() {
        val state = gameEngine.gameState.value
        assertEquals(GameMode.RALLY, state.gameMode)
        assertEquals(0, state.rallyScore)
        assertEquals(GameEngine.RALLY_STARTING_LIVES, state.rallyLives)
        assertTrue(state.rallyGrid.all { !it })
        assertTrue(state.opponentRallyGrid.all { !it })
        assertEquals(0, state.longestRally)
    }

    @Test
    fun `test shared scoring with individual grids`() {
        // NOTE: Serves do NOT update rally grid - only RALLY phase hits do.
        // The grid tracks "bingo-style" accomplishments during the rally.
        
        // Grid index calculation: row*3 + col
        // Row: FLAT=0, LOB=1, SMASH=2
        // Col: SOFT=0, MEDIUM=1, HARD=2
        // MEDIUM_FLAT = row 0, col 1 = index 1
        val mediumFlatIndex = SwingType.MEDIUM_FLAT.getGridIndex() // Should be 1
        
        // Disable risk to prevent random serve failures
        disableRisk()
        
        // 1. Serve to start the rally (Rally=1)
        // This does NOT update the grid
        val serveTime = 1000L
        val serveResult = gameEngine.processSwing(serveTime, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Serve should succeed", HitResult.HIT, serveResult)
        
        // Verify serve did NOT update grid (as expected)
        var state = gameEngine.gameState.value
        assertTrue("Grid should be empty after serve", state.rallyGrid.all { !it })
        assertEquals("Score should be 0 after serve", 0, state.rallyScore)
        
        // 2. Opponent returns (Rally=2) - sets up MY turn
        val opponentHitTime = 2000L
        gameEngine.onOpponentHit(opponentHitTime, SwingType.MEDIUM_FLAT.ordinal)
        
        state = gameEngine.gameState.value
        
        // Verify P2 Grid IS updated (opponent hit DOES update their grid)
        assertTrue("P2 Grid Index $mediumFlatIndex should be marked after their hit", state.opponentRallyGrid[mediumFlatIndex])
        
        // Verify Score Added (10 pts from opponent hit)
        assertEquals("Opponent hit should add 10 to score", 10, state.rallyScore)
        
        // 3. I hit in rally (Rally=3) - THIS should update MY grid
        // Ball arrives at: opponentHitTime + flightTime = 2000 + 700 = 2700
        // Hit window starts at arrival - 200 = 2500
        // Hit window ends at arrival + difficulty (default 600) = 2700 + 600 - 200 = 3100
        // Swing at the arrival time for a valid hit
        val ballArrival = state.ballArrivalTimestamp
        val rallyHitResult = gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally hit should succeed", HitResult.HIT, rallyHitResult)
        
        state = gameEngine.gameState.value
        
        // Verify P1 Grid updated
        assertTrue("P1 Grid Index $mediumFlatIndex should be marked after rally hit", state.rallyGrid[mediumFlatIndex])
        
        // Verify Score Added to SHARED total (10 + 10 = 20)
        assertEquals("Total should be 20 (10 from opponent + 10 from me)", 20, state.rallyScore)
    }

    @Test
    fun `test longest rally update in rally mode`() {
        // NOTE: longestRally is only updated on LOCAL processSwing rally hits,
        // not on serves (which just set currentRallyLength = 1).
        
        // Disable risk to prevent random failures
        disableRisk()
        
        // 1. Serve (Rally=1) - longestRally stays 0 (not updated on serve)
        val serveResult = gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Serve should succeed", HitResult.HIT, serveResult)
        assertEquals("currentRallyLength should be 1 after serve", 1, gameEngine.gameState.value.currentRallyLength)
        // longestRally is NOT updated on serve, only on rally hits
        assertEquals("longestRally stays 0 after serve", 0, gameEngine.gameState.value.longestRally)
        
        // 2. Opponent Hit (Rally=2) - sets up my turn
        gameEngine.onOpponentHit(2000L, SwingType.MEDIUM_FLAT.ordinal)
        assertEquals("currentRallyLength should be 2", 2, gameEngine.gameState.value.currentRallyLength)
        
        // 3. I Hit (Rally=3) - THIS updates longestRally to max(0, 2+1) = 3
        // Use ballArrivalTimestamp for proper timing
        val ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        val hitResult = gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally hit should succeed", HitResult.HIT, hitResult)
        assertEquals("longestRally should be 3 after rally hit", 3, gameEngine.gameState.value.longestRally)
        
        // 4. Miss -> Point Ends (handleMiss also updates longestRally)
        gameEngine.handleMiss(Player.PLAYER_1)
        
        // longestRally should persist
        assertEquals("longestRally should be 3 after miss", 3, gameEngine.gameState.value.longestRally)
    }

    @Test
    fun `test rematch reset`() {
        // Disable risk to prevent random serve failures
        disableRisk()
        
        // Set up a proper rally: serve -> opponent returns -> I hit
        // This ensures both grids get populated
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // Serve (Rally=1)
        gameEngine.onOpponentHit(2000L, SwingType.MEDIUM_FLAT.ordinal) // Opponent hits (Rally=2, marks their grid)
        
        // Get the ball arrival time for proper timing
        val ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // I hit in rally (Rally=3, marks my grid)
        
        // Check state is dirty
        var state = gameEngine.gameState.value
        assertTrue("Rally score should be > 0", state.rallyScore > 0)
        assertTrue("My rally grid should have marks", state.rallyGrid.any { it })
        assertTrue("Opponent rally grid should have marks", state.opponentRallyGrid.any { it })
        
        // Trigger Rematch
        gameEngine.rematch(isInitiator = true)
        
        state = gameEngine.gameState.value
        
        // Verify Reset
        assertEquals(0, state.rallyScore)
        assertEquals(GameEngine.RALLY_STARTING_LIVES, state.rallyLives)
        assertTrue("Rally Grid should be cleared", state.rallyGrid.all { !it })
        assertTrue("Opponent Rally Grid should be cleared", state.opponentRallyGrid.all { !it })
        assertEquals(0, state.longestRally)
        assertEquals(0, state.currentRallyLength)
    }
}
