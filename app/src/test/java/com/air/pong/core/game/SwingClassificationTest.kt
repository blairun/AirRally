package com.air.pong.core.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SwingClassificationTest {

    @Test
    fun `test swing classification thresholds`() {
        val minThreshold = 14.0f
        
        // Soft: force <= minThreshold + 9.0 (23.0)
        assertEquals(SwingType.SOFT_FLAT, classifySwing(15.0f, 0f, minThreshold))
        assertEquals(SwingType.SOFT_FLAT, classifySwing(23.0f, 0f, minThreshold))
        
        // Medium: force > 23.0 && force <= minThreshold + 30.0 (44.0)
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(23.1f, 0f, minThreshold))
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(30.0f, 0f, minThreshold))
        assertEquals(SwingType.MEDIUM_FLAT, classifySwing(44.0f, 0f, minThreshold))
        
        // Hard: force > 44.0
        assertEquals(SwingType.HARD_FLAT, classifySwing(44.1f, 0f, minThreshold))
        assertEquals(SwingType.HARD_FLAT, classifySwing(50.0f, 0f, minThreshold))
    }
    
    @Test
    fun `test flight time modifiers`() {
        val settings = SwingSettings.createDefault()
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, SwingType.SOFT_FLAT.getFlightTimeModifier(settings), 0.0f)
        assertEquals(SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT, SwingType.MEDIUM_FLAT.getFlightTimeModifier(settings), 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_FLAT_FLIGHT, SwingType.HARD_FLAT.getFlightTimeModifier(settings), 0.0f)
    }
}
