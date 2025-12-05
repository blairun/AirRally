package com.air.pong.core.game

import org.junit.Assert.*
import org.junit.Test

class SwingSettingsTest {

    @Test
    fun testSnapshotAndRestore() {
        // 1. Verify Defaults
        assertEquals(SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT, SwingSettings.softFlatFlight, 0.0f)
        
        // 2. Modify Settings
        SwingSettings.softFlatFlight = 99.9f
        SwingSettings.hardSmashNetRisk = 50
        
        // 3. Take Snapshot
        val snapshot = SwingSettings.getSnapshot()
        
        assertEquals(99.9f, snapshot.softFlatFlight, 0.0f)
        assertEquals(50, snapshot.hardSmashNetRisk)
        
        // 4. Modify Settings Again (Simulate new settings coming in or being changed)
        SwingSettings.softFlatFlight = 10.0f
        SwingSettings.hardSmashNetRisk = 0
        
        assertNotEquals(snapshot.softFlatFlight, SwingSettings.softFlatFlight)
        
        // 5. Restore Snapshot
        SwingSettings.restoreSnapshot(snapshot)
        
        // 6. Verify Restored Values
        assertEquals(99.9f, SwingSettings.softFlatFlight, 0.0f)
        assertEquals(50, SwingSettings.hardSmashNetRisk)
    }
}
