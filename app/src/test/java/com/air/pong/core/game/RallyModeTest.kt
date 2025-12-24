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
        gameEngine.updateSwingSettings(SwingSettings.createDefault())
        gameEngine.setLocalPlayer(isHost = true) // Assume Player 1 (Host)
        // Start in RALLY mode
        gameEngine.startGame(GameMode.RALLY)
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
    fun `test rally mode initialization`() {
        val state = gameEngine.gameState.value
        assertEquals(GameMode.RALLY, state.gameMode)
        assertEquals(0, state.rallyState?.score ?: -1)
        assertEquals(GameEngine.RALLY_STARTING_LIVES, state.rallyState?.lives ?: -1)
        assertTrue(state.rallyState?.grid?.all { !it } ?: false)
        assertTrue(state.rallyState?.opponentGrid?.all { !it } ?: false)
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
        val serveResult = gameEngine.processSwing(serveTime, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Serve should succeed", HitResult.HIT, serveResult)
        
        // Verify serve did NOT update grid (as expected)
        // NOTE: Serves now award tier-based points (tier 0 = 1 point), but do NOT mark grid cells
        var state = gameEngine.gameState.value
        assertTrue("Grid should be empty after serve", state.rallyState?.grid?.all { !it } ?: false)
        assertEquals("Score should be tier-0 points after serve", 1, state.rallyState?.score ?: -1)
        
        // 2. Opponent returns (Rally=2) - sets up MY turn
        val opponentHitTime = 2000L
        gameEngine.onOpponentHit(opponentHitTime, SwingType.MEDIUM_FLAT.ordinal)
        
        state = gameEngine.gameState.value
        
        // Verify P2 Grid IS updated (opponent hit DOES update their grid)
        assertTrue("P2 Grid Index $mediumFlatIndex should be marked after their hit", state.rallyState?.opponentGrid?.get(mediumFlatIndex) ?: false)
        
        // Verify Score Added (tier-0 points: 1 pt from opponent hit)
        // Total = 1 (serve) + 1 (opponent) = 2
        assertEquals("Score after serve + opponent hit", 2, state.rallyState?.score ?: -1)
        
        // 3. I hit in rally (Rally=3) - THIS should update MY grid
        // Ball arrives at: opponentHitTime + flightTime = 2000 + 700 = 2700
        // Hit window starts at arrival - 200 = 2500
        // Hit window ends at arrival + difficulty (default 600) = 2700 + 600 - 200 = 3100
        // Swing at the arrival time for a valid hit
        val ballArrival = state.ballArrivalTimestamp
        val rallyHitResult = gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals("Rally hit should succeed", HitResult.HIT, rallyHitResult)
        
        state = gameEngine.gameState.value
        
        // Verify P1 Grid updated
        assertTrue("P1 Grid Index $mediumFlatIndex should be marked after rally hit", state.rallyState?.grid?.get(mediumFlatIndex) ?: false)
        
        // Verify Score Added to SHARED total
        // Total = 1 (serve) + 1 (opponent) + 1 (my hit) = 3 pts at tier 0
        assertEquals("Total should be 3 (1 from serve + 1 from opponent + 1 from me)", 3, state.rallyState?.score ?: -1)
    }

    @Test
    fun `test longest rally update in rally mode`() {
        // NOTE: longestRally is only updated on LOCAL processSwing rally hits,
        // not on serves (which just set currentRallyLength = 1).
        
        // Disable risk to prevent random failures
        disableRisk()
        
        // 1. Serve (Rally=1) - longestRally stays 0 (not updated on serve)
        val serveResult = gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
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
        val hitResult = gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
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
        gameEngine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // Serve (Rally=1)
        gameEngine.onOpponentHit(2000L, SwingType.MEDIUM_FLAT.ordinal) // Opponent hits (Rally=2, marks their grid)
        
        // Get the ball arrival time for proper timing
        val ballArrival = gameEngine.gameState.value.ballArrivalTimestamp
        gameEngine.processSwing(ballArrival, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) // I hit in rally (Rally=3, marks my grid)
        
        // Check state is dirty
        var state = gameEngine.gameState.value
        assertTrue("Rally score should be > 0", (state.rallyState?.score ?: 0) > 0)
        assertTrue("My rally grid should have marks", state.rallyState?.grid?.any { it } ?: false)
        assertTrue("Opponent rally grid should have marks", state.rallyState?.opponentGrid?.any { it } ?: false)
        
        // Trigger Rematch
        gameEngine.rematch(isInitiator = true)
        
        state = gameEngine.gameState.value
        
        // Verify Reset
        assertEquals(0, state.rallyState?.score ?: -1)
        assertEquals(GameEngine.RALLY_STARTING_LIVES, state.rallyState?.lives ?: -1)
        assertTrue("Rally Grid should be cleared", state.rallyState?.grid?.all { !it } ?: false)
        assertTrue("Opponent Rally Grid should be cleared", state.rallyState?.opponentGrid?.all { !it } ?: false)
        assertEquals(0, state.longestRally)
        assertEquals(0, state.currentRallyLength)
    }
}
