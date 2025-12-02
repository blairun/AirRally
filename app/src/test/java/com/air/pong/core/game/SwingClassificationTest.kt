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
        assertEquals(1.3f, SwingType.SOFT_FLAT.getFlightTimeModifier(), 0.0f)
        assertEquals(1.0f, SwingType.MEDIUM_FLAT.getFlightTimeModifier(), 0.0f)
        assertEquals(0.7f, SwingType.HARD_FLAT.getFlightTimeModifier(), 0.0f)
    }
}
