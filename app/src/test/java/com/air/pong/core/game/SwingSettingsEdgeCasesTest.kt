package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Test

/**
 * Edge case tests for SwingSettings data class.
 * Tests boundary values, copy behavior, and serialization.
 */
class SwingSettingsEdgeCasesTest {

    @Test
    fun `test createDefault returns valid settings`() {
        val settings = SwingSettings.createDefault()
        
        // Verify all fields have reasonable default values
        assertTrue(settings.softFlatFlight > 0)
        assertTrue(settings.mediumFlatFlight > 0)
        assertTrue(settings.hardFlatFlight > 0)
        
        // Risk percentages should be 0-100
        assertTrue(settings.softFlatNetRisk in 0..100)
        assertTrue(settings.hardSmashNetRisk in 0..100)
        
        // Shrink percentages should be 0-100
        assertTrue(settings.softFlatShrink in 0..100)
        assertTrue(settings.hardSmashShrink in 0..100)
    }

    @Test
    fun `test copy preserves unchanged fields`() {
        val original = SwingSettings.createDefault()
        val modified = original.copy(softFlatNetRisk = 99)
        
        // Modified field changed
        assertEquals(99, modified.softFlatNetRisk)
        
        // All other fields unchanged
        assertEquals(original.softFlatFlight, modified.softFlatFlight, 0.0f)
        assertEquals(original.mediumFlatNetRisk, modified.mediumFlatNetRisk)
        assertEquals(original.hardSmashShrink, modified.hardSmashShrink)
        assertEquals(original.hardLobServeFlight, modified.hardLobServeFlight, 0.0f)
    }

    @Test
    fun `test copy multiple fields at once`() {
        val original = SwingSettings.createDefault()
        val modified = original.copy(
            softFlatNetRisk = 50,
            softFlatOutRisk = 25,
            softFlatShrink = 10,
            softFlatFlight = 2.0f
        )
        
        assertEquals(50, modified.softFlatNetRisk)
        assertEquals(25, modified.softFlatOutRisk)
        assertEquals(10, modified.softFlatShrink)
        assertEquals(2.0f, modified.softFlatFlight, 0.0f)
    }

    @Test
    fun `test settings with zero values`() {
        val settings = SwingSettings.createDefault().copy(
            softFlatNetRisk = 0,
            softFlatOutRisk = 0,
            softFlatShrink = 0
        )
        
        assertEquals(0, settings.softFlatNetRisk)
        assertEquals(0, settings.softFlatOutRisk)
        assertEquals(0, settings.softFlatShrink)
    }

    @Test
    fun `test settings with max values`() {
        val settings = SwingSettings.createDefault().copy(
            softFlatNetRisk = 100,
            softFlatOutRisk = 100,
            softFlatShrink = 100
        )
        
        assertEquals(100, settings.softFlatNetRisk)
        assertEquals(100, settings.softFlatOutRisk)
        assertEquals(100, settings.softFlatShrink)
    }

    @Test
    fun `test flight modifiers affect timing correctly`() {
        val settings = SwingSettings.createDefault()
        val baseFlightTime = 1000L
        
        // Soft should be slower (higher modifier)
        val softTime = (baseFlightTime * settings.softFlatFlight).toLong()
        val mediumTime = (baseFlightTime * settings.mediumFlatFlight).toLong()
        val hardTime = (baseFlightTime * settings.hardFlatFlight).toLong()
        
        assertTrue("Soft should be slower than medium", softTime > mediumTime)
        assertTrue("Medium should be slower than hard", mediumTime > hardTime)
    }

    @Test
    fun `test all swing types have settings`() {
        val settings = SwingSettings.createDefault()
        
        // All 9 swing types should have valid flight modifiers
        for (swingType in SwingType.entries) {
            val modifier = swingType.getFlightTimeModifier(settings)
            assertTrue("Flight modifier for $swingType should be positive", modifier > 0)
        }
        
        // All 9 swing types should have valid shrink percentages
        for (swingType in SwingType.entries) {
            val shrink = swingType.getWindowShrinkPercentage(settings)
            assertTrue("Shrink for $swingType should be 0-1", shrink in 0f..1f)
        }
    }

    @Test
    fun `test serve flight modifiers exist for all types`() {
        val settings = SwingSettings.createDefault()
        
        // Smash serve should use special slow modifier
        val smashServe = settings.hardSmashServeFlight
        val lobServe = settings.hardLobServeFlight
        
        assertTrue("Smash serve modifier should be positive", smashServe > 0)
        assertTrue("Lob serve modifier should be positive", lobServe > 0)
        
        // Smash serve should be slower than normal smash (uses lob timing)
        assertTrue("Smash serve should be slower than normal smash", 
            smashServe > settings.hardSmashFlight)
    }

    @Test
    fun `test settings equality`() {
        val settings1 = SwingSettings.createDefault()
        val settings2 = SwingSettings.createDefault()
        
        assertEquals(settings1, settings2)
        assertEquals(settings1.hashCode(), settings2.hashCode())
        
        val modified = settings1.copy(softFlatNetRisk = 99)
        assertNotEquals(settings1, modified)
    }

    @Test
    fun `test settings toString contains useful info`() {
        val settings = SwingSettings.createDefault()
        val str = settings.toString()
        
        // Data class toString should include field names and values
        assertTrue(str.contains("SwingSettings"))
        assertTrue(str.contains("softFlatFlight"))
    }

    @Test
    fun `test extreme flight modifiers`() {
        // Very slow (2x)
        val slowSettings = SwingSettings.createDefault().copy(softFlatFlight = 2.0f)
        assertEquals(2.0f, slowSettings.softFlatFlight, 0.0f)
        
        // Very fast (0.2x) 
        val fastSettings = SwingSettings.createDefault().copy(hardSmashFlight = 0.2f)
        assertEquals(0.2f, fastSettings.hardSmashFlight, 0.0f)
    }

    @Test
    fun `test all defaults match companion constants`() {
        val settings = SwingSettings.createDefault()
        
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, settings.softFlatFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT, settings.mediumFlatFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_FLAT_FLIGHT, settings.hardFlatFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_SOFT_LOB_FLIGHT, settings.softLobFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_SMASH_FLIGHT, settings.hardSmashFlight, 0.0f)
        assertEquals(SwingSettings.DEFAULT_HARD_SMASH_SHRINK, settings.hardSmashShrink)
    }
}
