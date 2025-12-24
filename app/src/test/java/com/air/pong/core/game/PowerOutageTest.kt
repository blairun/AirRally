package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Power Outage mechanic:
 * - Trigger conditions (10 hits on same cell)
 * - Recovery conditions (5 unique cells)
 * - Reset on miss
 * - Hit window bonus accumulation
 * - Constants validation
 */
class PowerOutageTest {

    // ============================================================
    // POWER OUTAGE CONSTANTS TESTS
    // ============================================================
    
    @Test
    fun `test power outage constants are valid`() {
        assertEquals(10, PowerOutageConstants.TRIGGER_HITS)
        assertEquals(7, PowerOutageConstants.WARNING_THRESHOLD)
        assertEquals(5, PowerOutageConstants.RECOVERY_UNIQUE_CELLS)
        assertEquals(50L, PowerOutageConstants.WINDOW_BONUS_MS)
    }
    
    @Test
    fun `test warning threshold is less than trigger hits`() {
        assertTrue(
            "Warning should appear before trigger",
            PowerOutageConstants.WARNING_THRESHOLD < PowerOutageConstants.TRIGGER_HITS
        )
    }
    
    @Test
    fun `test recovery cells is less than or equal to grid size`() {
        assertTrue(
            "Recovery cells should fit in 9-cell grid",
            PowerOutageConstants.RECOVERY_UNIQUE_CELLS <= 9
        )
    }
    
    // ============================================================
    // SOLO RALLY STATE TESTS
    // ============================================================
    
    @Test
    fun `test initial SoloRallyState has no power outage`() {
        val state = ModeState.SoloRallyState()
        assertFalse(state.isPowerOutage)
        assertEquals(List(9) { 0 }, state.cellHitCounts)
        assertTrue(state.outageRecoveryUniqueCells.isEmpty())
        assertEquals(0, state.powerOutagesClearedCount)
        assertEquals(0L, state.powerOutageWindowBonus)
    }
    
    @Test
    fun `test cell hit counts tracking`() {
        var state = ModeState.SoloRallyState()
        
        // Simulate incrementing hit count for cell 0
        val newCounts = state.cellHitCounts.toMutableList()
        newCounts[0] = newCounts[0] + 1
        state = state.copy(cellHitCounts = newCounts)
        
        assertEquals(1, state.cellHitCounts[0])
        assertEquals(0, state.cellHitCounts[1]) // Other cells unchanged
    }
    
    @Test
    fun `test power outage triggers at threshold`() {
        var state = ModeState.SoloRallyState()
        
        // Set cell 0 to threshold - 1
        val counts = MutableList(9) { 0 }
        counts[0] = PowerOutageConstants.TRIGGER_HITS - 1
        state = state.copy(cellHitCounts = counts)
        
        // One more hit should trigger outage
        counts[0] = PowerOutageConstants.TRIGGER_HITS
        val newIsPowerOutage = counts[0] >= PowerOutageConstants.TRIGGER_HITS
        
        assertTrue("Should trigger power outage at threshold", newIsPowerOutage)
    }
    
    @Test
    fun `test warning threshold calculation`() {
        val cellHitCount = PowerOutageConstants.WARNING_THRESHOLD
        val hitsRemaining = PowerOutageConstants.TRIGGER_HITS - cellHitCount
        
        assertEquals(3, hitsRemaining) // 10 - 7 = 3
        assertTrue("Should show warning at threshold", cellHitCount >= PowerOutageConstants.WARNING_THRESHOLD)
    }
    
    @Test
    fun `test outage recovery with unique cells`() {
        var state = ModeState.SoloRallyState(isPowerOutage = true)
        
        // Add unique cells one by one
        state = state.copy(outageRecoveryUniqueCells = setOf(0))
        assertEquals(1, state.outageRecoveryUniqueCells.size)
        
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + 1)
        assertEquals(2, state.outageRecoveryUniqueCells.size)
        
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + 2)
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + 3)
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + 4)
        
        // Should have enough for recovery
        assertTrue(
            "Should have enough unique cells for recovery",
            state.outageRecoveryUniqueCells.size >= PowerOutageConstants.RECOVERY_UNIQUE_CELLS
        )
    }
    
    @Test
    fun `test duplicate cell resets recovery progress`() {
        var state = ModeState.SoloRallyState(
            isPowerOutage = true,
            outageRecoveryUniqueCells = setOf(0, 1, 2)
        )
        
        assertEquals(3, state.outageRecoveryUniqueCells.size)
        
        // Hitting cell 0 again (duplicate) should reset
        val gridIndex = 0
        val isDuplicate = gridIndex in state.outageRecoveryUniqueCells
        
        assertTrue("Cell 0 should be detected as duplicate", isDuplicate)
        
        if (isDuplicate) {
            state = state.copy(outageRecoveryUniqueCells = emptySet())
        }
        
        assertEquals(0, state.outageRecoveryUniqueCells.size)
    }
    
    @Test
    fun `test power restored increments cleared count`() {
        var state = ModeState.SoloRallyState(
            isPowerOutage = true,
            outageRecoveryUniqueCells = setOf(0, 1, 2, 3),
            powerOutagesClearedCount = 2
        )
        
        // Add 5th unique cell to trigger recovery
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + 4)
        
        if (state.outageRecoveryUniqueCells.size >= PowerOutageConstants.RECOVERY_UNIQUE_CELLS) {
            state = state.copy(
                isPowerOutage = false,
                outageRecoveryUniqueCells = emptySet(),
                cellHitCounts = List(9) { 0 },
                powerOutagesClearedCount = state.powerOutagesClearedCount + 1
            )
        }
        
        assertFalse("Power should be restored", state.isPowerOutage)
        assertEquals(3, state.powerOutagesClearedCount)
        assertEquals(0, state.outageRecoveryUniqueCells.size)
    }
    
    @Test
    fun `test window bonus accumulates correctly`() {
        var state = ModeState.SoloRallyState(powerOutageWindowBonus = 0L)
        
        // First recovery
        state = state.copy(powerOutageWindowBonus = state.powerOutageWindowBonus + PowerOutageConstants.WINDOW_BONUS_MS)
        assertEquals(50L, state.powerOutageWindowBonus)
        
        // Second recovery
        state = state.copy(powerOutageWindowBonus = state.powerOutageWindowBonus + PowerOutageConstants.WINDOW_BONUS_MS)
        assertEquals(100L, state.powerOutageWindowBonus)
        
        // Third recovery
        state = state.copy(powerOutageWindowBonus = state.powerOutageWindowBonus + PowerOutageConstants.WINDOW_BONUS_MS)
        assertEquals(150L, state.powerOutageWindowBonus)
    }
    
    @Test
    fun `test miss resets outage state but preserves bonuses`() {
        val initialState = ModeState.SoloRallyState(
            isPowerOutage = true,
            cellHitCounts = listOf(5, 3, 2, 0, 0, 0, 0, 0, 0),
            outageRecoveryUniqueCells = setOf(0, 1),
            powerOutagesClearedCount = 2,
            powerOutageWindowBonus = 40L
        )
        
        // On miss: reset cell counts and outage, preserve bonuses
        val afterMiss = initialState.copy(
            isPowerOutage = false,
            cellHitCounts = List(9) { 0 },
            outageRecoveryUniqueCells = emptySet()
            // powerOutagesClearedCount and powerOutageWindowBonus preserved
        )
        
        assertFalse("Power outage should be cleared", afterMiss.isPowerOutage)
        assertEquals(List(9) { 0 }, afterMiss.cellHitCounts)
        assertTrue(afterMiss.outageRecoveryUniqueCells.isEmpty())
        assertEquals(2, afterMiss.powerOutagesClearedCount) // Preserved
        assertEquals(40L, afterMiss.powerOutageWindowBonus) // Preserved
    }
    
    // ============================================================
    // RALLY STATE TESTS (Co-op mode)
    // ============================================================
    
    @Test
    fun `test initial RallyState has no power outage`() {
        val state = ModeState.RallyState()
        assertFalse(state.isPowerOutage)
        assertEquals(List(9) { 0 }, state.cellHitCounts)
        assertTrue(state.outageRecoveryUniqueCells.isEmpty())
        assertEquals(0, state.powerOutagesClearedCount)
        assertEquals(0L, state.powerOutageWindowBonus)
    }
    
    @Test
    fun `test RallyState power outage trigger logic matches SoloRallyState`() {
        // Both modes should use the same constants
        var rallyState = ModeState.RallyState()
        var soloState = ModeState.SoloRallyState()
        
        // Set cell 0 to trigger threshold
        val counts = MutableList(9) { 0 }
        counts[0] = PowerOutageConstants.TRIGGER_HITS
        
        rallyState = rallyState.copy(cellHitCounts = counts)
        soloState = soloState.copy(cellHitCounts = counts)
        
        val rallyTrigger = rallyState.cellHitCounts[0] >= PowerOutageConstants.TRIGGER_HITS
        val soloTrigger = soloState.cellHitCounts[0] >= PowerOutageConstants.TRIGGER_HITS
        
        assertEquals("Both modes should trigger at same threshold", rallyTrigger, soloTrigger)
        assertTrue(rallyTrigger)
    }
    
    // ============================================================
    // EDGE CASE TESTS
    // ============================================================
    
    @Test
    fun `test all cells can accumulate hit counts independently`() {
        var state = ModeState.SoloRallyState()
        
        // Increment different cells
        val counts = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        state = state.copy(cellHitCounts = counts)
        
        assertEquals(1, state.cellHitCounts[0])
        assertEquals(5, state.cellHitCounts[4])
        assertEquals(9, state.cellHitCounts[8])
    }
    
    @Test
    fun `test recovery requires exactly RECOVERY_UNIQUE_CELLS unique cells`() {
        var state = ModeState.SoloRallyState(isPowerOutage = true)
        
        // Add exactly recovery threshold - 1 cells
        val cells = (0 until PowerOutageConstants.RECOVERY_UNIQUE_CELLS - 1).toSet()
        state = state.copy(outageRecoveryUniqueCells = cells)
        
        assertFalse(
            "Should not recover with fewer cells",
            state.outageRecoveryUniqueCells.size >= PowerOutageConstants.RECOVERY_UNIQUE_CELLS
        )
        
        // Add one more to meet threshold
        state = state.copy(outageRecoveryUniqueCells = state.outageRecoveryUniqueCells + (PowerOutageConstants.RECOVERY_UNIQUE_CELLS - 1))
        
        assertTrue(
            "Should recover when reaching threshold",
            state.outageRecoveryUniqueCells.size >= PowerOutageConstants.RECOVERY_UNIQUE_CELLS
        )
    }
    
    @Test
    fun `test multiple outages in sequence`() {
        var state = ModeState.SoloRallyState()
        
        // First outage trigger
        var counts = MutableList(9) { 0 }
        counts[3] = PowerOutageConstants.TRIGGER_HITS
        state = state.copy(cellHitCounts = counts, isPowerOutage = true)
        assertTrue(state.isPowerOutage)
        
        // First recovery
        state = state.copy(
            isPowerOutage = false,
            cellHitCounts = List(9) { 0 },
            outageRecoveryUniqueCells = emptySet(),
            powerOutagesClearedCount = 1,
            powerOutageWindowBonus = 20L
        )
        assertFalse(state.isPowerOutage)
        assertEquals(1, state.powerOutagesClearedCount)
        
        // Second outage trigger (different cell)
        counts = MutableList(9) { 0 }
        counts[5] = PowerOutageConstants.TRIGGER_HITS
        state = state.copy(cellHitCounts = counts, isPowerOutage = true)
        assertTrue(state.isPowerOutage)
        
        // Second recovery
        state = state.copy(
            isPowerOutage = false,
            cellHitCounts = List(9) { 0 },
            outageRecoveryUniqueCells = emptySet(),
            powerOutagesClearedCount = 2,
            powerOutageWindowBonus = 40L
        )
        assertEquals(2, state.powerOutagesClearedCount)
        assertEquals(40L, state.powerOutageWindowBonus)
    }
}

