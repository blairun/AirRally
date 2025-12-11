package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Test

class SwingSettingsTest {

    @Test
    fun testDataClassPatternWithCopy() {
        // 1. Create default settings
        val defaults = SwingSettings.createDefault()
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, defaults.softFlatFlight, 0.0f)
        
        // 2. Modify settings using copy()
        val modified = defaults.copy(
            softFlatFlight = 99.9f,
            hardSmashNetRisk = 50
        )
        
        assertEquals(99.9f, modified.softFlatFlight, 0.0f)
        assertEquals(50, modified.hardSmashNetRisk)
        
        // 3. Original is unmodified (immutable data class)
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, defaults.softFlatFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_SMASH_NET_RISK, defaults.hardSmashNetRisk)
        
        // 4. Modify again to create another copy
        val modifiedAgain = modified.copy(
            softFlatFlight = 10.0f,
            hardSmashNetRisk = 0
        )
        
        assertEquals(10.0f, modifiedAgain.softFlatFlight, 0.0f)
        assertEquals(0, modifiedAgain.hardSmashNetRisk)
        
        // 5. Previous modification is still intact
        assertEquals(99.9f, modified.softFlatFlight, 0.0f)
        assertEquals(50, modified.hardSmashNetRisk)
    }
}
