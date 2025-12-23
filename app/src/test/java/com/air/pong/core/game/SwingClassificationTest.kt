package com.air.pong.core.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SwingClassificationTest {

    @Test
    fun `test swing classification thresholds`() {
        // Independent thresholds (new system)
        val softThreshold = 14.0f
        val mediumThreshold = 23.0f
        val hardThreshold = 44.0f
        
        // Soft: force <= mediumThreshold
        assertEquals(SwingType.SOFT_FLAT, classifySwing(15.0f, 0f, softThreshold, mediumThreshold, hardThreshold))
        assertEquals(SwingType.SOFT_FLAT, classifySwing(23.0f, 0f, softThreshold, mediumThreshold, hardThreshold))
        
        // Medium: force > mediumThreshold && force <= hardThreshold
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(23.1f, 0f, softThreshold, mediumThreshold, hardThreshold))
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(30.0f, 0f, softThreshold, mediumThreshold, hardThreshold))
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(44.0f, 0f, softThreshold, mediumThreshold, hardThreshold))
        
        // Hard: force > hardThreshold
        assertEquals(SwingType.HARD_FLAT, classifySwing(44.1f, 0f, softThreshold, mediumThreshold, hardThreshold))
        assertEquals(SwingType.HARD_FLAT, classifySwing(50.0f, 0f, softThreshold, mediumThreshold, hardThreshold))
    }
    
    @Test
    fun `test flight time modifiers`() {
        val settings = SwingSettings.createDefault()
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, SwingType.SOFT_FLAT.getFlightTimeModifier(settings), 0.0f)
        assertEquals(SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT, SwingType.MEDIUM_FLAT.getFlightTimeModifier(settings), 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_FLAT_FLIGHT, SwingType.HARD_FLAT.getFlightTimeModifier(settings), 0.0f)
    }
}
