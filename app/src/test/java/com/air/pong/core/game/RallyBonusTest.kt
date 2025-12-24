package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Rally Bonus mechanics:
 * - Spin detection (SpinType.fromGyro)
 * - Copy Cat tier calculations
 * - Special Squares spawning logic
 */
class RallyBonusTest {

    // ============================================================
    // SPIN TYPE DETECTION TESTS
    // ============================================================
    
    // Use thresholds from source code to avoid test maintenance if values change
    private val yThreshold = SpinType.Y_THRESHOLD
    private val zThreshold = SpinType.Z_THRESHOLD
    
    @Test
    fun `test spin type NONE when below all thresholds`() {
        assertEquals(SpinType.NONE, SpinType.fromGyro(0f, 0f))
        assertEquals(SpinType.NONE, SpinType.fromGyro(yThreshold - 0.1f, zThreshold - 0.1f))
        assertEquals(SpinType.NONE, SpinType.fromGyro(-yThreshold + 0.1f, -zThreshold + 0.1f))
    }
    
    @Test
    fun `test spin type TOP when Gy below negative threshold`() {
        // TOP = wrist rolls forward, Gy < -Y_THRESHOLD
        assertEquals(SpinType.TOP, SpinType.fromGyro(-yThreshold - 1f, 0f))
        assertEquals(SpinType.TOP, SpinType.fromGyro(-15f, 0f))
    }
    
    @Test
    fun `test spin type BACK when Gy above positive threshold`() {
        // BACK = wrist rolls backward, Gy > Y_THRESHOLD
        assertEquals(SpinType.BACK, SpinType.fromGyro(yThreshold + 1f, 0f))
        assertEquals(SpinType.BACK, SpinType.fromGyro(15f, 0f))
    }
    
    @Test
    fun `test spin type LEFT when Gz below negative threshold`() {
        assertEquals(SpinType.LEFT, SpinType.fromGyro(0f, -zThreshold - 1f))
        assertEquals(SpinType.LEFT, SpinType.fromGyro(0f, -12f))
    }
    
    @Test
    fun `test spin type RIGHT when Gz above positive threshold`() {
        assertEquals(SpinType.RIGHT, SpinType.fromGyro(0f, zThreshold + 1f))
        assertEquals(SpinType.RIGHT, SpinType.fromGyro(0f, 12f))
    }
    
    @Test
    fun `test spin type TOP_LEFT combined`() {
        assertEquals(SpinType.TOP_LEFT, SpinType.fromGyro(-yThreshold - 1f, -zThreshold - 1f))
    }
    
    @Test
    fun `test spin type TOP_RIGHT combined`() {
        assertEquals(SpinType.TOP_RIGHT, SpinType.fromGyro(-yThreshold - 1f, zThreshold + 1f))
    }
    
    @Test
    fun `test spin type BACK_LEFT combined`() {
        assertEquals(SpinType.BACK_LEFT, SpinType.fromGyro(yThreshold + 1f, -zThreshold - 1f))
    }
    
    @Test
    fun `test spin type BACK_RIGHT combined`() {
        assertEquals(SpinType.BACK_RIGHT, SpinType.fromGyro(yThreshold + 1f, zThreshold + 1f))
    }
    
    @Test
    fun `test spin type exactly at threshold returns NONE`() {
        // Exactly at threshold should NOT trigger (threshold is exclusive)
        assertEquals(SpinType.NONE, SpinType.fromGyro(-yThreshold, 0f))
        assertEquals(SpinType.NONE, SpinType.fromGyro(yThreshold, 0f))
        assertEquals(SpinType.NONE, SpinType.fromGyro(0f, -zThreshold))
        assertEquals(SpinType.NONE, SpinType.fromGyro(0f, zThreshold))
    }
    
    @Test
    fun `test spin type just past threshold triggers`() {
        // Just past threshold should trigger
        assertEquals(SpinType.TOP, SpinType.fromGyro(-yThreshold - 0.01f, 0f))
        assertEquals(SpinType.BACK, SpinType.fromGyro(yThreshold + 0.01f, 0f))
        assertEquals(SpinType.LEFT, SpinType.fromGyro(0f, -zThreshold - 0.01f))
        assertEquals(SpinType.RIGHT, SpinType.fromGyro(0f, zThreshold + 0.01f))
    }
    
    @Test
    fun `test allBonusSpinTypes excludes NONE`() {
        assertFalse(SpinType.allBonusSpinTypes.contains(SpinType.NONE))
        assertEquals(8, SpinType.allBonusSpinTypes.size)
    }
    
    // ============================================================
    // HANDEDNESS DETECTION TESTS
    // ============================================================
    
    @Test
    fun `test left-handed inverts TOP to BACK`() {
        // Right-handed: negative Gy = TOP
        assertEquals(SpinType.TOP, SpinType.fromGyro(-15f, 0f, isLeftHanded = false))
        // Left-handed: negative Gy = BACK (inverted)
        assertEquals(SpinType.BACK, SpinType.fromGyro(-15f, 0f, isLeftHanded = true))
    }
    
    @Test
    fun `test left-handed inverts BACK to TOP`() {
        // Right-handed: positive Gy = BACK
        assertEquals(SpinType.BACK, SpinType.fromGyro(15f, 0f, isLeftHanded = false))
        // Left-handed: positive Gy = TOP (inverted)
        assertEquals(SpinType.TOP, SpinType.fromGyro(15f, 0f, isLeftHanded = true))
    }
    
    @Test
    fun `test left-handed does not affect LEFT and RIGHT`() {
        // Z-axis (left/right) should be unaffected by handedness
        assertEquals(SpinType.LEFT, SpinType.fromGyro(0f, -12f, isLeftHanded = false))
        assertEquals(SpinType.LEFT, SpinType.fromGyro(0f, -12f, isLeftHanded = true))
        assertEquals(SpinType.RIGHT, SpinType.fromGyro(0f, 12f, isLeftHanded = false))
        assertEquals(SpinType.RIGHT, SpinType.fromGyro(0f, 12f, isLeftHanded = true))
    }
    
    @Test
    fun `test left-handed inverts Y component in combined spins`() {
        // TOP_LEFT right-handed becomes BACK_LEFT left-handed
        assertEquals(SpinType.TOP_LEFT, SpinType.fromGyro(-15f, -12f, isLeftHanded = false))
        assertEquals(SpinType.BACK_LEFT, SpinType.fromGyro(-15f, -12f, isLeftHanded = true))
        
        // TOP_RIGHT right-handed becomes BACK_RIGHT left-handed
        assertEquals(SpinType.TOP_RIGHT, SpinType.fromGyro(-15f, 12f, isLeftHanded = false))
        assertEquals(SpinType.BACK_RIGHT, SpinType.fromGyro(-15f, 12f, isLeftHanded = true))
        
        // BACK_LEFT right-handed becomes TOP_LEFT left-handed
        assertEquals(SpinType.BACK_LEFT, SpinType.fromGyro(15f, -12f, isLeftHanded = false))
        assertEquals(SpinType.TOP_LEFT, SpinType.fromGyro(15f, -12f, isLeftHanded = true))
        
        // BACK_RIGHT right-handed becomes TOP_RIGHT left-handed
        assertEquals(SpinType.BACK_RIGHT, SpinType.fromGyro(15f, 12f, isLeftHanded = false))
        assertEquals(SpinType.TOP_RIGHT, SpinType.fromGyro(15f, 12f, isLeftHanded = true))
    }
    
    @Test
    fun `test left-handed default parameter is false`() {
        // Calling without isLeftHanded parameter should behave same as right-handed
        assertEquals(SpinType.TOP, SpinType.fromGyro(-15f, 0f))
        assertEquals(SpinType.BACK, SpinType.fromGyro(15f, 0f))
    }

    // ============================================================
    // COPY CAT TIER CALCULATION TESTS
    // ============================================================
    
    @Test
    fun `test calculateCopyCatTier returns 0 when requirements not met`() {
        // Not enough copies
        assertEquals(0, CopyCatConstants.calculateCopyCatTier(4, 3))
        assertEquals(0, CopyCatConstants.calculateCopyCatTier(3, 5))
        
        // Not enough unique types
        assertEquals(0, CopyCatConstants.calculateCopyCatTier(5, 2))
        assertEquals(0, CopyCatConstants.calculateCopyCatTier(9, 2))
    }
    
    @Test
    fun `test calculateCopyCatTier tier 1 requirements`() {
        // Tier 1: 5 copies, 3+ unique types
        assertEquals(1, CopyCatConstants.calculateCopyCatTier(5, 3))
        assertEquals(1, CopyCatConstants.calculateCopyCatTier(5, 4)) // More unique is OK
    }
    
    @Test
    fun `test calculateCopyCatTier tier 2 requirements`() {
        // Tier 2: 6 copies, 4+ unique types
        assertEquals(2, CopyCatConstants.calculateCopyCatTier(6, 4))
        assertEquals(2, CopyCatConstants.calculateCopyCatTier(6, 5))
    }
    
    @Test
    fun `test calculateCopyCatTier tier 3 requirements`() {
        // Tier 3: 7 copies, 5+ unique types
        assertEquals(3, CopyCatConstants.calculateCopyCatTier(7, 5))
    }
    
    @Test
    fun `test calculateCopyCatTier tier 4 requirements`() {
        // Tier 4: 8 copies, 6+ unique types
        assertEquals(4, CopyCatConstants.calculateCopyCatTier(8, 6))
    }
    
    @Test
    fun `test calculateCopyCatTier tier 5 max requirements`() {
        // Tier 5: 9 copies, 7+ unique types
        assertEquals(5, CopyCatConstants.calculateCopyCatTier(9, 7))
        assertEquals(5, CopyCatConstants.calculateCopyCatTier(9, 9)) // Max unique
    }
    
    @Test
    fun `test calculateCopyCatTier returns highest achievable tier`() {
        // Exceeding requirements should still return correct tier
        // e.g., 7 copies with 7 unique types = tier 3 (not 5, since only 7 copies)
        assertEquals(3, CopyCatConstants.calculateCopyCatTier(7, 7))
    }
    
    @Test
    fun `test multiplierForTier returns correct values`() {
        assertEquals(0.0f, CopyCatConstants.multiplierForTier(0), 0.001f)
        assertEquals(0.1f, CopyCatConstants.multiplierForTier(1), 0.001f)
        assertEquals(0.2f, CopyCatConstants.multiplierForTier(2), 0.001f)
        assertEquals(0.3f, CopyCatConstants.multiplierForTier(3), 0.001f)
        assertEquals(0.4f, CopyCatConstants.multiplierForTier(4), 0.001f)
        assertEquals(0.5f, CopyCatConstants.multiplierForTier(5), 0.001f)
    }
    
    @Test
    fun `test multiplierForTier caps at max`() {
        // Even if impossible tier is passed, should cap at 0.5
        assertEquals(CopyCatConstants.MAX_COPY_CAT_MULTIPLIER_BONUS, 
            CopyCatConstants.multiplierForTier(10), 0.001f)
    }
    
    @Test
    fun `test canReachMinimumTier early copies always succeed`() {
        // At 1-2 copies, any unique count is OK
        assertTrue(CopyCatConstants.canReachMinimumTier(1, 0))
        assertTrue(CopyCatConstants.canReachMinimumTier(2, 1))
        assertTrue(CopyCatConstants.canReachMinimumTier(2, 2))
    }
    
    @Test
    fun `test canReachMinimumTier on pace returns true`() {
        // Need uniqueTypes >= copyCount - 2
        assertTrue(CopyCatConstants.canReachMinimumTier(3, 1))  // 1 >= 1 ✓
        assertTrue(CopyCatConstants.canReachMinimumTier(4, 2))  // 2 >= 2 ✓
        assertTrue(CopyCatConstants.canReachMinimumTier(5, 3))  // 3 >= 3 ✓
    }
    
    @Test
    fun `test canReachMinimumTier off pace returns false`() {
        // Need uniqueTypes >= copyCount - 2, but not met
        assertFalse(CopyCatConstants.canReachMinimumTier(3, 0))  // 0 < 1 ✗
        assertFalse(CopyCatConstants.canReachMinimumTier(4, 1))  // 1 < 2 ✗
        assertFalse(CopyCatConstants.canReachMinimumTier(5, 2))  // 2 < 3 ✗
    }
    
    // ============================================================
    // SPECIAL SQUARES SPAWNING TESTS
    // ============================================================
    
    @Test
    fun `test spawnSpecialSquares returns empty for tier 0`() {
        val result = RallyModeStrategy.spawnSpecialSquares(0)
        assertTrue("Tier 0 should have no special squares", result.isEmpty())
    }
    
    @Test
    fun `test spawnSpecialSquares returns empty for negative tier`() {
        val result = RallyModeStrategy.spawnSpecialSquares(-1)
        assertTrue("Negative tier should have no special squares", result.isEmpty())
    }
    
    @Test
    fun `test spawnSpecialSquares may return squares for tier 1+`() {
        // Run multiple times to verify it can produce squares
        var foundSquares = false
        repeat(100) {
            val result = RallyModeStrategy.spawnSpecialSquares(1)
            if (result.isNotEmpty()) {
                foundSquares = true
            }
        }
        assertTrue("Should produce some special squares over many runs", foundSquares)
    }
    
    @Test
    fun `test spawnSpecialSquares golden squares are valid indices`() {
        repeat(50) {
            val result = RallyModeStrategy.spawnSpecialSquares(1)
            result.forEach { (index, type) ->
                assertTrue("Index should be 0-8", index in 0..8)
                assertNotNull("Type should not be null", type)
            }
        }
    }
    
    @Test
    fun `test spawnSpecialSquares both penalty squares share row or column`() {
        // Row definitions matching RallyModeStrategy
        val rows = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8))
        val cols = listOf(listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8))
        
        repeat(500) {
            val result = RallyModeStrategy.spawnSpecialSquares(1)
            val hasBanana = result.values.contains(SpecialSquareType.BANANA)
            val hasLandmine = result.values.contains(SpecialSquareType.LANDMINE)
            
            if (hasBanana && hasLandmine) {
                val bananaIndex = result.entries.first { it.value == SpecialSquareType.BANANA }.key
                val landmineIndex = result.entries.first { it.value == SpecialSquareType.LANDMINE }.key
                
                // Check if they share a row or column
                val sharesRow = rows.any { row -> bananaIndex in row && landmineIndex in row }
                val sharesCol = cols.any { col -> bananaIndex in col && landmineIndex in col }
                
                assertTrue(
                    "Banana at $bananaIndex and Landmine at $landmineIndex should share row or column",
                    sharesRow || sharesCol
                )
            }
        }
    }
    
    @Test
    fun `test special square constants are valid`() {
        // Verify constants are reasonable
        assertTrue(SpecialSquareType.GOLDEN_CATEGORY_CHANCE > 0)
        assertTrue(SpecialSquareType.PENALTY_CATEGORY_CHANCE > 0)
        assertTrue(SpecialSquareType.GOLDEN_CENTER_CHANCE > 0)
        assertTrue(SpecialSquareType.GOLDEN_CENTER_CHANCE < 1)
        
        assertEquals(3, SpecialSquareType.GOLDEN_POINTS_MULTIPLIER)
        assertEquals(10, SpecialSquareType.BANANA_PENALTY_PER_TIER)
    }
    
    // ============================================================
    // SPIN BONUS CONSTANTS TESTS
    // ============================================================
    
    @Test
    fun `test spin bonus constants are valid`() {
        assertEquals(0.5f, SpinType.MAX_SPIN_MULTIPLIER_BONUS, 0.001f)
        assertEquals(0.1f, SpinType.SPIN_BONUS_PER_TYPE, 0.001f)
        assertEquals(15L, SpinType.SPIN_DURATION_PER_SHOT_SECONDS)
        assertEquals(5, SpinType.INFINITE_SPIN_THRESHOLD)
    }
    
    // ============================================================
    // SPIN TIMING EFFECTS TESTS
    // ============================================================
    
    @Test
    fun `test spin timing constants are valid`() {
        assertEquals(0.90f, SpinType.TOP_FLIGHT_MODIFIER, 0.001f)
        assertEquals(0.90f, SpinType.TOP_WINDOW_MODIFIER, 0.001f)
        assertEquals(1.10f, SpinType.BACK_FLIGHT_MODIFIER, 0.001f)
        assertEquals(0.95f, SpinType.BACK_WINDOW_MODIFIER, 0.001f)
        assertEquals(0.05f, SpinType.SIDE_MODIFIER_VARIANCE, 0.001f)
    }
    
    @Test
    fun `test NONE spin returns 1 for all modifiers`() {
        assertEquals(1.0f, SpinType.NONE.getSpinFlightTimeModifier(12345L), 0.001f)
        assertEquals(1.0f, SpinType.NONE.getSpinWindowModifier(12345L), 0.001f)
    }
    
    @Test
    fun `test TOP spin flight time modifier`() {
        // TOP spin should reduce flight time by 10%
        val modifier = SpinType.TOP.getSpinFlightTimeModifier(12345L)
        assertEquals(0.90f, modifier, 0.001f)
    }
    
    @Test
    fun `test TOP spin window modifier`() {
        // TOP spin should reduce hit window by 10%
        val modifier = SpinType.TOP.getSpinWindowModifier(12345L)
        assertEquals(0.90f, modifier, 0.001f)
    }
    
    @Test
    fun `test BACK spin flight time modifier`() {
        // BACK spin should increase flight time by 10%
        val modifier = SpinType.BACK.getSpinFlightTimeModifier(12345L)
        assertEquals(1.10f, modifier, 0.001f)
    }
    
    @Test
    fun `test BACK spin window modifier`() {
        // BACK spin should reduce hit window by 5%
        val modifier = SpinType.BACK.getSpinWindowModifier(12345L)
        assertEquals(0.95f, modifier, 0.001f)
    }
    
    @Test
    fun `test side spin modifiers are in expected range`() {
        // Side spins should be ±5% (0.95 or 1.05)
        val leftFlight = SpinType.LEFT.getSpinFlightTimeModifier(12345L)
        val rightFlight = SpinType.RIGHT.getSpinFlightTimeModifier(12345L)
        val leftWindow = SpinType.LEFT.getSpinWindowModifier(12345L)
        val rightWindow = SpinType.RIGHT.getSpinWindowModifier(12345L)
        
        assertTrue("LEFT flight should be ±5%", leftFlight == 0.95f || leftFlight == 1.05f)
        assertTrue("RIGHT flight should be ±5%", rightFlight == 0.95f || rightFlight == 1.05f)
        assertTrue("LEFT window should be ±5%", leftWindow == 0.95f || leftWindow == 1.05f)
        assertTrue("RIGHT window should be ±5%", rightWindow == 0.95f || rightWindow == 1.05f)
    }
    
    @Test
    fun `test side spin randomness is deterministic with same timestamp`() {
        // Same timestamp should produce same result
        val ts = 987654321L
        val first = SpinType.LEFT.getSpinFlightTimeModifier(ts)
        val second = SpinType.LEFT.getSpinFlightTimeModifier(ts)
        assertEquals("Same timestamp should produce same result", first, second, 0.001f)
    }
    
    @Test
    fun `test side spin flight and window can differ`() {
        // Flight and window use different seed offsets, so they may differ
        // Test multiple timestamps to find a case where they differ
        var foundDifference = false
        for (ts in 1L..100L) {
            val flight = SpinType.LEFT.getSpinFlightTimeModifier(ts)
            val window = SpinType.LEFT.getSpinWindowModifier(ts)
            if (flight != window) {
                foundDifference = true
                break
            }
        }
        // Note: It's okay if they happen to match sometimes, but over many seeds they should differ
        // This test verifies they CAN differ (independent randomness)
        assertTrue("Flight and window randomness should be independent", foundDifference)
    }
    
    @Test
    fun `test combined TOP_RIGHT modifier applies both effects`() {
        // TOP_RIGHT = TOP (0.9) * side (0.95 or 1.05)
        val ts = 12345L
        val flightMod = SpinType.TOP_RIGHT.getSpinFlightTimeModifier(ts)
        val windowMod = SpinType.TOP_RIGHT.getSpinWindowModifier(ts)
        
        // Should be in range 0.855 (0.9 * 0.95) to 0.945 (0.9 * 1.05)
        assertTrue("TOP_RIGHT flight should be 0.855 or 0.945", 
            flightMod in 0.85f..0.95f)
        assertTrue("TOP_RIGHT window should be 0.855 or 0.945", 
            windowMod in 0.85f..0.95f)
    }
    
    @Test
    fun `test combined BACK_LEFT modifier applies both effects`() {
        // BACK_LEFT = BACK flight (1.1) * side, BACK window (0.95) * side
        val ts = 54321L
        val flightMod = SpinType.BACK_LEFT.getSpinFlightTimeModifier(ts)
        val windowMod = SpinType.BACK_LEFT.getSpinWindowModifier(ts)
        
        // Flight: 1.1 * (0.95 or 1.05) = 1.045 or 1.155
        assertTrue("BACK_LEFT flight should be 1.045 or 1.155", 
            flightMod in 1.04f..1.16f)
        // Window: 0.95 * (0.95 or 1.05) = 0.9025 or 0.9975
        assertTrue("BACK_LEFT window should be ~0.90 or ~1.00", 
            windowMod in 0.90f..1.00f)
    }
    
    @Test
    fun `test hasTop returns true for TOP spins`() {
        assertTrue(SpinType.TOP.hasTop())
        assertTrue(SpinType.TOP_LEFT.hasTop())
        assertTrue(SpinType.TOP_RIGHT.hasTop())
        assertFalse(SpinType.BACK.hasTop())
        assertFalse(SpinType.LEFT.hasTop())
        assertFalse(SpinType.NONE.hasTop())
    }
    
    @Test
    fun `test hasBack returns true for BACK spins`() {
        assertTrue(SpinType.BACK.hasBack())
        assertTrue(SpinType.BACK_LEFT.hasBack())
        assertTrue(SpinType.BACK_RIGHT.hasBack())
        assertFalse(SpinType.TOP.hasBack())
        assertFalse(SpinType.RIGHT.hasBack())
        assertFalse(SpinType.NONE.hasBack())
    }
    
    @Test
    fun `test hasSide returns true for side spins`() {
        assertTrue(SpinType.LEFT.hasSide())
        assertTrue(SpinType.RIGHT.hasSide())
        assertTrue(SpinType.TOP_LEFT.hasSide())
        assertTrue(SpinType.TOP_RIGHT.hasSide())
        assertTrue(SpinType.BACK_LEFT.hasSide())
        assertTrue(SpinType.BACK_RIGHT.hasSide())
        assertFalse(SpinType.TOP.hasSide())
        assertFalse(SpinType.BACK.hasSide())
        assertFalse(SpinType.NONE.hasSide())
    }
}
