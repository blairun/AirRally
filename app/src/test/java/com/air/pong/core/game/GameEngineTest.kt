
package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        resetSwingSettings()
        gameEngine = GameEngine()
        gameEngine.setLocalPlayer(isHost = true) // Assume Player 1 is Host
        gameEngine.startGame()
    }

    private fun resetSwingSettings() {
        SwingSettings.softFlatNetRisk = SwingSettings.DEFAULT_SOFT_FLAT_NET_RISK
        SwingSettings.softFlatOutRisk = SwingSettings.DEFAULT_SOFT_FLAT_OUT_RISK
        SwingSettings.softFlatShrink = SwingSettings.DEFAULT_SOFT_FLAT_SHRINK
        SwingSettings.softFlatFlight = SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT

        SwingSettings.mediumFlatNetRisk = SwingSettings.DEFAULT_MEDIUM_FLAT_NET_RISK
        SwingSettings.mediumFlatOutRisk = SwingSettings.DEFAULT_MEDIUM_FLAT_OUT_RISK
        SwingSettings.mediumFlatShrink = SwingSettings.DEFAULT_MEDIUM_FLAT_SHRINK
        SwingSettings.mediumFlatFlight = SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT

        SwingSettings.hardFlatNetRisk = SwingSettings.DEFAULT_HARD_FLAT_NET_RISK
        SwingSettings.hardFlatOutRisk = SwingSettings.DEFAULT_HARD_FLAT_OUT_RISK
        SwingSettings.hardFlatShrink = SwingSettings.DEFAULT_HARD_FLAT_SHRINK
        SwingSettings.hardFlatFlight = SwingSettings.DEFAULT_HARD_FLAT_FLIGHT

        SwingSettings.softLobNetRisk = SwingSettings.DEFAULT_SOFT_LOB_NET_RISK
        SwingSettings.softLobOutRisk = SwingSettings.DEFAULT_SOFT_LOB_OUT_RISK
        SwingSettings.softLobShrink = SwingSettings.DEFAULT_SOFT_LOB_SHRINK
        SwingSettings.softLobFlight = SwingSettings.DEFAULT_SOFT_LOB_FLIGHT

        SwingSettings.mediumLobNetRisk = SwingSettings.DEFAULT_MEDIUM_LOB_NET_RISK
        SwingSettings.mediumLobOutRisk = SwingSettings.DEFAULT_MEDIUM_LOB_OUT_RISK
        SwingSettings.mediumLobShrink = SwingSettings.DEFAULT_MEDIUM_LOB_SHRINK
        SwingSettings.mediumLobFlight = SwingSettings.DEFAULT_MEDIUM_LOB_FLIGHT

        SwingSettings.hardLobNetRisk = SwingSettings.DEFAULT_HARD_LOB_NET_RISK
        SwingSettings.hardLobOutRisk = SwingSettings.DEFAULT_HARD_LOB_OUT_RISK
        SwingSettings.hardLobShrink = SwingSettings.DEFAULT_HARD_LOB_SHRINK
        SwingSettings.hardLobFlight = SwingSettings.DEFAULT_HARD_LOB_FLIGHT

        SwingSettings.softSmashNetRisk = SwingSettings.DEFAULT_SOFT_SMASH_NET_RISK
        SwingSettings.softSmashOutRisk = SwingSettings.DEFAULT_SOFT_SMASH_OUT_RISK
        SwingSettings.softSmashShrink = SwingSettings.DEFAULT_SOFT_SMASH_SHRINK
        SwingSettings.softSmashFlight = SwingSettings.DEFAULT_SOFT_SMASH_FLIGHT

        SwingSettings.mediumSmashNetRisk = SwingSettings.DEFAULT_MEDIUM_SMASH_NET_RISK
        SwingSettings.mediumSmashOutRisk = SwingSettings.DEFAULT_MEDIUM_SMASH_OUT_RISK
        SwingSettings.mediumSmashShrink = SwingSettings.DEFAULT_MEDIUM_SMASH_SHRINK
        SwingSettings.mediumSmashFlight = SwingSettings.DEFAULT_MEDIUM_SMASH_FLIGHT

        SwingSettings.hardSmashNetRisk = SwingSettings.DEFAULT_HARD_SMASH_NET_RISK
        SwingSettings.hardSmashOutRisk = SwingSettings.DEFAULT_HARD_SMASH_OUT_RISK
        SwingSettings.hardSmashShrink = SwingSettings.DEFAULT_HARD_SMASH_SHRINK
        SwingSettings.hardSmashFlight = SwingSettings.DEFAULT_HARD_SMASH_FLIGHT
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
        gameEngine.startNextServe()
        assertEquals(1, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)

        // 2-0, P2 serves
        gameEngine.handleMiss(Player.PLAYER_2) // P2 missed, P1 scores
        gameEngine.startNextServe()
        assertEquals(2, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 2-1, P2 serves
        gameEngine.handleMiss(Player.PLAYER_1) // P1 missed, P2 scores
        gameEngine.startNextServe()
        assertEquals(1, gameEngine.gameState.value.player2Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 2-2, P1 serves
        gameEngine.handleMiss(Player.PLAYER_1) // P1 missed, P2 scores
        gameEngine.startNextServe()
        assertEquals(2, gameEngine.gameState.value.player2Score)
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)
    }

    @Test
    fun `test deuce serve rotation`() {
        // Fast forward to 10-10
        repeat(10) { 
            gameEngine.handleMiss(Player.PLAYER_2) 
            gameEngine.startNextServe()
        } // P1: 10
        repeat(10) { 
            gameEngine.handleMiss(Player.PLAYER_1) 
            gameEngine.startNextServe()
        } // P2: 10
        
        assertEquals(10, gameEngine.gameState.value.player1Score)
        assertEquals(10, gameEngine.gameState.value.player2Score)
        
        // 10-10, P1 serves
        assertEquals(Player.PLAYER_1, gameEngine.gameState.value.servingPlayer)

        // 11-10, P2 serves (Deuce: rotate every 1)
        gameEngine.handleMiss(Player.PLAYER_2)
        gameEngine.startNextServe()
        assertEquals(11, gameEngine.gameState.value.player1Score)
        assertEquals(Player.PLAYER_2, gameEngine.gameState.value.servingPlayer)

        // 11-11, P1 serves
        gameEngine.handleMiss(Player.PLAYER_1)
        gameEngine.startNextServe()
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
        disableRisk()
        // Explicitly set flight time to avoid relying on default
        val testFlightTime = 1000L
        gameEngine.updateSettings(testFlightTime, 500, isDebugMode = false, useDebugTones = false, minSwingThreshold = GameEngine.DEFAULT_SWING_THRESHOLD, isRallyShrinkEnabled = true) // Difficulty 500ms (Medium)

        // P1 serves at T=1000. Arrival = 1000 + 1000 = 2000.
        // Note: Must start > 500ms to avoid cooldown check against initial 0
        val serveTime = 1000L
        val expectedArrival = serveTime + (testFlightTime * SwingSettings.softFlatFlight).toLong()
        
        gameEngine.processSwing(serveTime, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) 
        assertEquals(expectedArrival, gameEngine.gameState.value.ballArrivalTimestamp)
        
        // Test HIT window (Shifted: Start at -BOUNCE_OFFSET_MS, End at -BOUNCE_OFFSET_MS + 2*Window)
        // Difficulty 500 means "Half Window" = 500ms. Total Window = 1000ms.
        val halfWindow = 500L
        val totalWindow = halfWindow * 2
        val bounceOffset = GameEngine.BOUNCE_OFFSET_MS
        
        val startWindow = -bounceOffset
        val endWindow = -bounceOffset + totalWindow
        
        // Test Start of Window (Earliest valid hit)
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival + startWindow))
        
        // Test Middle (Arrival)
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival))
        
        // Test End of Window (Latest valid hit)
        assertEquals(HitResult.HIT, gameEngine.checkHitTiming(expectedArrival + endWindow))
        
        // Test TOO EARLY (< Start Window)
        assertEquals(HitResult.MISS_EARLY, gameEngine.checkHitTiming(expectedArrival + startWindow - 1))
        
        // Test TOO LATE (> End Window)
        assertEquals(HitResult.MISS_LATE, gameEngine.checkHitTiming(expectedArrival + endWindow + 1))
    }

    @Test
    fun `test swing classification`() {
        disableRisk()
        // Force > 20 -> HARD
        // Force > 17 -> MEDIUM
        // Else -> SOFT
        
        // GravZ > 3.0 -> LOB
        // GravZ < -3.0 -> SMASH
        // Else -> FLAT
        
        // 1. SOFT FLAT (Force=10, GravZ=0)
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 2. MEDIUM FLAT (Force=25, GravZ=0)
        gameEngine.processSwing(2000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 3. HARD FLAT (Force=45, GravZ=0)
        gameEngine.processSwing(3000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.HARD_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 4. SOFT LOB (Force=10, GravZ=6.0)
        gameEngine.processSwing(4000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 6.0f)
        assertEquals(SwingType.SOFT_LOB, gameEngine.gameState.value.lastSwingType)
        
        // 5. HARD SMASH (Force=45, GravZ=-6.0)
        gameEngine.processSwing(5000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
        assertEquals(SwingType.HARD_SMASH, gameEngine.gameState.value.lastSwingType)
    }

    @Test
    fun `test flight time modifiers`() {
        // Set base flight time to 1000ms explicitly (default is 700ms)
        val baseFlightTime = 1000L
        gameEngine.updateSettings(baseFlightTime, 500, isDebugMode = false, useDebugTones = false, minSwingThreshold = GameEngine.DEFAULT_SWING_THRESHOLD, isRallyShrinkEnabled = true)
        
        // 1. MEDIUM_FLAT (Default: 1.0x)
        gameEngine.onOpponentHit(0L, SwingType.MEDIUM_FLAT.ordinal)
        assertEquals((baseFlightTime * SwingSettings.mediumFlatFlight).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
        
        // 2. SOFT_LOB (Default: 1.5x)
        gameEngine.onOpponentHit(0L, SwingType.SOFT_LOB.ordinal)
        assertEquals((baseFlightTime * SwingSettings.softLobFlight).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
        
        // 3. HARD_SMASH (Default: 0.3x)
        gameEngine.onOpponentHit(0L, SwingType.HARD_SMASH.ordinal)
        assertEquals((baseFlightTime * SwingSettings.hardSmashFlight).toLong(), gameEngine.gameState.value.ballArrivalTimestamp)
    }

    @Test
    fun `test smash serve flight time exception`() {
        disableRisk()
        // Smash Serve should use LOB flight times (Slow) due to bounce physics
        val baseFlightTime = 1000L
        gameEngine.updateSettings(baseFlightTime, 500, false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)
        
        gameEngine.startGame() // Reset to serve phase
        
        // Serve HARD_SMASH (Force=45, Tilt=-6.0)
        
        val timestamp = 1000L
        val result = gameEngine.processSwing(timestamp, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
        assertEquals(HitResult.HIT, result)
        assertEquals(SwingType.HARD_SMASH, gameEngine.gameState.value.lastSwingType)
        
        // Use the current setting from SwingSettings
        val expectedModifier = SwingSettings.hardLobFlight
        
        val expectedDuration = (baseFlightTime * expectedModifier).toLong()
        val arrival = gameEngine.gameState.value.ballArrivalTimestamp
        
        println("DEBUG: SmashServe - Base: $baseFlightTime, Mod: $expectedModifier, Expected: $expectedDuration, ActualArrival-Timestamp: ${arrival - timestamp}")
        
        assertEquals("Expected flight duration $expectedDuration but got ${arrival - timestamp}", timestamp + expectedDuration, arrival)
    }
    
    @Test
    fun `test no window shrink on serve return`() {
        disableRisk()
        // Setup: Serve phase, Hard Smash Serve
        val baseWindow = 500L
        gameEngine.updateSettings(1000L, baseWindow.toInt(), false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)
        gameEngine.startGame()
        
        // Serve HARD_SMASH (Shrink is normally high, e.g. 50%)
        gameEngine.processSwing(1000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
        
        // Now check hit window for receiver. Receiver hasn't hit yet, so currentRallyLength is 1.
        assertEquals("Rally length should be 1 after serve", 1, gameEngine.gameState.value.currentRallyLength)
        
        // Should use Base Window (500)
        assertEquals("Should use base window on serve return", baseWindow, gameEngine.getHitWindow())
        
        // Let's transition to Rally (Length 2)
        // Opponent hits back HARD_SMASH.
        gameEngine.onOpponentHit(2000L, SwingType.HARD_SMASH.ordinal)
        assertEquals("Rally length should be 2 after opponent hit", 2, gameEngine.gameState.value.currentRallyLength)
        
        // Now it IS my turn. Incoming shot is HARD_SMASH.
        // Should have shrinkage.
        val shrinkPercent = SwingType.HARD_SMASH.getWindowShrinkPercentage()
        
        // Rally Length = 2. Rally Shrink = 20.
        val rallyShrink = 20L
        val adjustedBase = baseWindow - rallyShrink
        val shrunkWindow = (adjustedBase * (1.0f - shrinkPercent)).toLong()
        
        println("DEBUG: WindowShrink - Base: $baseWindow, RallyShrink: $rallyShrink, Shrink%: $shrinkPercent, ExpectedShrunk: $shrunkWindow, Actual: ${gameEngine.getHitWindow()}")
        
        assertTrue("Shrunk window $shrunkWindow should be < base window $baseWindow", shrunkWindow < baseWindow)
        
        assertEquals("Should use shrunk window in rally", shrunkWindow, gameEngine.getHitWindow())
    }

    @Test
    fun `test fast smash hit window`() {
        disableRisk()
        // Setup: Fast Smash that requires window shifting
        val baseFlightTime = 600L
        val difficulty = 500
        gameEngine.updateSettings(baseFlightTime, difficulty, false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)
        
        // Use current settings
        val flightModifier = SwingSettings.hardSmashFlight
        val shrinkPercent = SwingSettings.hardSmashShrink / 100f
        
        val flightDuration = (baseFlightTime * flightModifier).toLong() 
        
        // Pre-condition: We need to be in a rally (length > 1) for shrinking to apply
        gameEngine.startGame()
        // 1. I Serve (Rally=1) - Must be > 500ms to avoid cooldown
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        // 2. Opponent Returns (Rally=2)
        gameEngine.onOpponentHit(2000L, SwingType.MEDIUM_FLAT.ordinal)

        // Opponent hits Hard Smash at T=3000 (Rally=3)
        val hitTime = 3000L
        gameEngine.onOpponentHit(hitTime, SwingType.HARD_SMASH.ordinal)
        
        // Calculate expected window with Rally Shrink
        // Rally Length = 3. Shrink = 30ms.
        val rallyShrink = 30L
        val adjustedBaseWindow = (difficulty - rallyShrink).coerceAtLeast(200L)
        val halfWindow = (adjustedBaseWindow * (1.0f - shrinkPercent)).toLong()
        val totalWindow = halfWindow * 2
        
        val arrivalTime = hitTime + flightDuration
        assertEquals(arrivalTime, gameEngine.gameState.value.ballArrivalTimestamp)
        
        val safetyStart = hitTime + GameEngine.MIN_REACTION_TIME_MS
        val standardStart = arrivalTime - GameEngine.BOUNCE_OFFSET_MS
        val actualStart = maxOf(safetyStart, standardStart)
        val actualEnd = actualStart + totalWindow
        
        val checkTime = actualEnd // Check exactly at the end
        val result = gameEngine.checkHitTiming(checkTime)
        
        println("DEBUG: FastSmash - Hit: $hitTime, Arrival: $arrivalTime, SafetyStart: $safetyStart, StdStart: $standardStart, ActualStart: $actualStart, End: $actualEnd")
        println("DEBUG: FastSmash - Checking at $checkTime. Result: $result")
        
        // 1. Too Early (Safety Check) -> T=3150 (HitTime + 150)
        assertEquals("Too Early (Safety Check)", HitResult.MISS_EARLY, gameEngine.checkHitTiming(hitTime + 150))
        
        // 2. Valid Hit (Start of Window) -> T=3210 (HitTime + 210)
        assertEquals("Valid Hit (Start of Window)", HitResult.HIT, gameEngine.checkHitTiming(hitTime + 210))
        
        // 3. Valid Hit (Middle/Late) -> T=3500
        assertEquals("Valid Hit (Middle/Late)", HitResult.HIT, gameEngine.checkHitTiming(hitTime + 500))
        
        // 4. Valid Hit (End of Window)
        assertEquals("Valid Hit (End of Window)", HitResult.HIT, gameEngine.checkHitTiming(actualEnd))
        
        // 5. Too Late -> T=ActualEnd + 1
        assertEquals("Should be MISS_LATE when hitting after window", HitResult.MISS_LATE, gameEngine.checkHitTiming(actualEnd + 1))
    }

    @Test
    fun `test window shrink`() {
        disableRisk()
        // Base Window (Medium Difficulty)
        val testFlightTime = 1000L
        gameEngine.updateSettings(testFlightTime, 500, isDebugMode = false, useDebugTones = false, minSwingThreshold = GameEngine.DEFAULT_SWING_THRESHOLD, isRallyShrinkEnabled = true) // Difficulty 500ms
        val baseWindow = 500L
        
        // 1. Incoming SOFT_FLAT (Default 0% shrink)
        // Rally = 1 (Return of Serve). No Rally Shrink.
        gameEngine.onOpponentHit(0L, SwingType.SOFT_FLAT.ordinal)
        assertEquals((baseWindow * (1f - SwingSettings.softFlatShrink/100f)).toLong(), gameEngine.getHitWindow())
        
        // 2. Incoming MEDIUM_FLAT (Default 20% shrink)
        // Rally = 2. Rally Shrink = 20.
        gameEngine.onOpponentHit(0L, SwingType.MEDIUM_FLAT.ordinal)
        val base2 = baseWindow - 20
        assertEquals((base2 * (1f - SwingSettings.mediumFlatShrink/100f)).toLong(), gameEngine.getHitWindow())
        
        // 3. Incoming HARD_SMASH (Default 50% shrink)
        // Rally = 3. Rally Shrink = 30.
        gameEngine.onOpponentHit(0L, SwingType.HARD_SMASH.ordinal)
        val base3 = baseWindow - 30
        assertEquals((base3 * (1f - SwingSettings.hardSmashShrink/100f)).toLong(), gameEngine.getHitWindow())

        // 4. Incoming SOFT_SMASH (Default 20% shrink)
        // Rally = 4. Rally Shrink = 40.
        gameEngine.onOpponentHit(0L, SwingType.SOFT_SMASH.ordinal)
        val base4 = baseWindow - 40
        assertEquals((base4 * (1f - SwingSettings.softSmashShrink/100f)).toLong(), gameEngine.getHitWindow())
    }

    @Test
    fun `test risk logic`() {
        // 1. Safe Shot (SOFT_FLAT) - Should NEVER fail risk check
        // We temporarily set SOFT_FLAT risk to 0 for this test to ensure "Safe" behavior
        // logic, then restore it.
        val originalNet = SwingSettings.softFlatNetRisk
        val originalOut = SwingSettings.softFlatOutRisk
        
        SwingSettings.softFlatNetRisk = 0
        SwingSettings.softFlatOutRisk = 0
        
        try {
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
            
            // 2. Risky Shot (HARD_SMASH) - Should EVENTUALLY fail
            // HARD_SMASH: Force=25, GravZ=-4.0
            // Risk: 30% Net, 10% Out -> 40% Fail Rate
            
            var failures = 0
            repeat(100) { 
                // Create fresh engine to reset lastPointEndedTimestamp
                val engine = GameEngine()
                engine.setLocalPlayer(true)
                engine.startGame()
                
                // Use a valid timestamp > 500ms
                val result = engine.processSwing(1000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
                
                // It might return HIT or PENDING(MISS_NET/MISS_OUT)
                // If it returns NULL, that's bad (ignored swing)
                
                 var finalResult = result
                if (result == HitResult.PENDING) {
                    finalResult = engine.resolvePendingMiss()
                }
                
                if (finalResult == HitResult.MISS_NET || finalResult == HitResult.MISS_OUT) {
                    failures++
                }
            }
            
            // Statistically, 100 runs with 40% fail rate should produce at least 1 failure.
            assertTrue("Expected some failures due to risk, but got $failures", failures > 0)
        } finally {
            // Restore Settings
            SwingSettings.softFlatNetRisk = originalNet
            SwingSettings.softFlatOutRisk = originalOut
        }
    }

    @Test
    fun `test risk probabilities`() {
        val iterations = 10000
    
        // 1. MEDIUM_FLAT
        var mediumFlatNet = 0
        var mediumFlatOut = 0
        val mediumFlatNetRisk = SwingSettings.mediumFlatNetRisk
        val mediumFlatOutRisk = SwingSettings.mediumFlatOutRisk
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=25 (Medium), GravZ=0 (Flat)
            var result = engine.processSwing(1000L, 25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (result == HitResult.PENDING) {
                result = engine.resolvePendingMiss()
            }
            if (result == HitResult.MISS_NET) mediumFlatNet++
            if (result == HitResult.MISS_OUT) mediumFlatOut++
        }
        
        if (mediumFlatNetRisk == 0) {
            assertEquals("MEDIUM_FLAT should never hit net", 0, mediumFlatNet)
        } else {
             val expected = (iterations * mediumFlatNetRisk) / 100
             assertTrue("MEDIUM_FLAT Net Rate should be roughly $mediumFlatNetRisk%", mediumFlatNet in (expected - 200)..(expected + 200))
        }

        if (mediumFlatOutRisk == 0) {
            assertEquals("MEDIUM_FLAT should never hit out", 0, mediumFlatOut)
        } else {
             val expected = (iterations * mediumFlatOutRisk) / 100
             assertTrue("MEDIUM_FLAT Out Rate should be roughly $mediumFlatOutRisk%", mediumFlatOut in (expected - 200)..(expected + 200))
        }
        
        // 2. HARD_FLAT
        var hardFlatNet = 0
        var hardFlatOut = 0
        val hardFlatNetRisk = SwingSettings.hardFlatNetRisk
        val hardFlatOutRisk = SwingSettings.hardFlatOutRisk
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=45 (Hard), GravZ=0 (Flat)
            var result = engine.processSwing(1000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (result == HitResult.PENDING) {
                result = engine.resolvePendingMiss()
            }
            if (result == HitResult.MISS_NET) hardFlatNet++
            if (result == HitResult.MISS_OUT) hardFlatOut++
        }
        
        val expectedNetHardFlat = (iterations * hardFlatNetRisk) / 100
        assertTrue("HARD_FLAT Net Rate should be roughly $hardFlatNetRisk%", hardFlatNet in (expectedNetHardFlat - 200)..(expectedNetHardFlat + 200))

        val expectedOutHardFlat = (iterations * hardFlatOutRisk) / 100
        assertTrue("HARD_FLAT Out Rate should be roughly $hardFlatOutRisk%", hardFlatOut in (expectedOutHardFlat - 200)..(expectedOutHardFlat + 200))

        // 3. SOFT_SMASH
        var softSmashNet = 0
        var softSmashOut = 0
        val softSmashNetRisk = SwingSettings.softSmashNetRisk
        val softSmashOutRisk = SwingSettings.softSmashOutRisk
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=10 (Soft), GravZ=-6.0 (Smash)
            var result = engine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
            if (result == HitResult.PENDING) {
                result = engine.resolvePendingMiss()
            }
            if (result == HitResult.MISS_NET) softSmashNet++
            if (result == HitResult.MISS_OUT) softSmashOut++
        }
        
        val expectedNetSoftSmash = (iterations * softSmashNetRisk) / 100
        assertTrue("SOFT_SMASH Net Rate should be roughly $softSmashNetRisk%", softSmashNet in (expectedNetSoftSmash - 200)..(expectedNetSoftSmash + 200))
        
        if (softSmashOutRisk == 0) {
            assertEquals("SOFT_SMASH should never hit out", 0, softSmashOut)
        } else {
             val expected = (iterations * softSmashOutRisk) / 100
             assertTrue("SOFT_SMASH Out Rate should be roughly $softSmashOutRisk%", softSmashOut in (expected - 200)..(expected + 200))
        }

        // 4. HARD_SMASH
        var hardSmashNet = 0
        var hardSmashOut = 0
        val hardSmashNetRisk = SwingSettings.hardSmashNetRisk
        val hardSmashOutRisk = SwingSettings.hardSmashOutRisk
        
        repeat(iterations) {
            val engine = GameEngine()
            engine.setLocalPlayer(true)
            engine.startGame()
            // Force=45 (Hard), GravZ=-6.0 (Smash)
            var result = engine.processSwing(1000L, 45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -6.0f)
            if (result == HitResult.PENDING) {
                result = engine.resolvePendingMiss()
            }
            if (result == HitResult.MISS_NET) hardSmashNet++
            if (result == HitResult.MISS_OUT) hardSmashOut++
        }
        
        val expectedNetHardSmash = (iterations * hardSmashNetRisk) / 100
        assertTrue("HARD_SMASH Net Rate should be roughly $hardSmashNetRisk% (actual: $hardSmashNet)", hardSmashNet in (expectedNetHardSmash - 200)..(expectedNetHardSmash + 200))
        
        val expectedOutHardSmash = (iterations * hardSmashOutRisk) / 100
        assertTrue("HARD_SMASH Out Rate should be roughly $hardSmashOutRisk% (actual: $hardSmashOut)", hardSmashOut in (expectedOutHardSmash - 200)..(expectedOutHardSmash + 200))
    }
    @Test
    fun `test dynamic swing threshold`() {
        disableRisk()
        // Set low threshold (10.0f) - New Min
        gameEngine.updateSettings(1000L, 500, false, false, 10.0f, true)
        
        // 1. SOFT (Force=11.0) -> Should be valid SOFT (since > 10.0)
        // Note: We need to ensure we are in a valid state to swing (e.g. Waiting for Serve)
        gameEngine.startGame()
        gameEngine.processSwing(1000L, 11.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 2. MEDIUM (Force=20.0) -> > 10.0 + 9.0 = 19.0
        gameEngine.processSwing(2000L, 20.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // 3. HARD (Force=41.0) -> > 10.0 + 30.0 = 40.0
        gameEngine.processSwing(3000L, 41.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.HARD_FLAT, gameEngine.gameState.value.lastSwingType)
        
        // Set default threshold (14.0f)
        gameEngine.updateSettings(1000L, 500, false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)

        // 4. SOFT (Force=20.0) -> > 14.0 (but < 23.0)
        gameEngine.processSwing(4000L, 20.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)

        // 5. MEDIUM (Force=24.0) -> > 14.0 + 9.0 = 23.0
        gameEngine.processSwing(5000L, 24.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.MEDIUM_FLAT, gameEngine.gameState.value.lastSwingType)

        // 6. HARD (Force=45.0) -> > 14.0 + 30.0 = 44.0
        gameEngine.processSwing(6000L, 45.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.HARD_FLAT, gameEngine.gameState.value.lastSwingType)

        // Set high threshold (24.0f) - New Max
        gameEngine.updateSettings(1000L, 500, false, false, 24.0f, true)
        
        // 7. SOFT (Force=25.0) -> > 24.0 (but < 33.0)
        gameEngine.processSwing(7000L, 25.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SwingType.SOFT_FLAT, gameEngine.gameState.value.lastSwingType)
    }
    @Test
    fun `test rally shrink logic`() {
        disableRisk()
        // Setup: Base Window = 500ms
        val baseWindow = 500L
        gameEngine.updateSettings(1000L, baseWindow.toInt(), false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)
        
        // 1. Serve (Rally=0 -> 1)
        // No shrink on serve
        gameEngine.startGame()
        gameEngine.processSwing(1000L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        
        // 2. Opponent Return (P2)
        // P2 hits. P1 receives it.
        gameEngine.onOpponentHit(2000L, SwingType.SOFT_FLAT.ordinal)
        assertEquals(2, gameEngine.gameState.value.currentRallyLength)
        
        // Now P1 needs to hit. Window should be shrunk.
        // Base = 500. Shrink = 2 * 10 = 20. Result = 480.
        // Note: SwingType shrink also applies. SOFT_FLAT has 0% shrink.
        assertEquals(480L, gameEngine.getHitWindow())
        
        // 3. P1 Hits (Rally=3)
        // Note: SOFT_FLAT has 1.3x flight time. 1000 * 1.3 = 1300.
        // Arrival = 2000 + 1300 = 3300.
        // Window starts at -200 (3100).
        // We must hit AFTER 3100. Let's hit at 3200.
        gameEngine.processSwing(3200L, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(3, gameEngine.gameState.value.currentRallyLength)
        
        // 4. Opponent Return (P2)
        gameEngine.onOpponentHit(4000L, SwingType.SOFT_FLAT.ordinal)
        assertEquals(4, gameEngine.gameState.value.currentRallyLength)
        
        // Now P1 needs to hit. Window should be shrunk more.
        // Base = 500. Rally=4. Shrink = 4 * 10 = 40. Result = 460.
        assertEquals(460L, gameEngine.getHitWindow())
        
        // 5. Disable Rally Shrink
        gameEngine.updateSettings(1000L, baseWindow.toInt(), false, false, GameEngine.DEFAULT_SWING_THRESHOLD, false)
        
        // Should return to Base Window (500)
        assertEquals(500L, gameEngine.getHitWindow())
        
        // 6. Re-enable and test Min Window Floor
        gameEngine.updateSettings(1000L, baseWindow.toInt(), false, false, GameEngine.DEFAULT_SWING_THRESHOLD, true)
        
        // Simulate long rally (Rally=40) -> Shrink = 400. 500 - 400 = 100.
        // Floor is 200. Should return 200.
        // We need to hack the state to set rally length, or play 40 shots.
        // Let's play 40 shots.
        repeat(36) { // We are at 4. Need 36 more to get to 40.
             // I hit
             gameEngine.processSwing(5000L + it*1000, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
             // Opponent hits
             gameEngine.onOpponentHit(5500L + it*1000, SwingType.SOFT_FLAT.ordinal)
        }
        
        // Check Rally Length (should be large)
        assertTrue(gameEngine.gameState.value.currentRallyLength >= 40)
        
        // Check Window (Should be floored at 200)
        assertEquals(200L, gameEngine.getHitWindow())
    }
}
