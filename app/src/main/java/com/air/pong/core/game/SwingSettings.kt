package com.air.pong.core.game

/**
 * Singleton object to hold mutable swing settings.
 * These values are persisted in SharedPreferences and synced via network.
 */
object SwingSettings {
    // Defaults based on original hardcoded values in SwingExtensions.kt
    
    // Constants for Defaults
    const val DEFAULT_SOFT_FLAT_NET_RISK = 2
    const val DEFAULT_SOFT_FLAT_OUT_RISK = 1
    const val DEFAULT_SOFT_FLAT_SHRINK = 0

    const val DEFAULT_MEDIUM_FLAT_NET_RISK = 1
    const val DEFAULT_MEDIUM_FLAT_OUT_RISK = 3
    const val DEFAULT_MEDIUM_FLAT_SHRINK = 20

    const val DEFAULT_HARD_FLAT_NET_RISK = 3
    const val DEFAULT_HARD_FLAT_OUT_RISK = 5
    const val DEFAULT_HARD_FLAT_SHRINK = 40

    const val DEFAULT_SOFT_LOB_NET_RISK = 0
    const val DEFAULT_SOFT_LOB_OUT_RISK = 3
    const val DEFAULT_SOFT_LOB_SHRINK = 10

    const val DEFAULT_MEDIUM_LOB_NET_RISK = 0
    const val DEFAULT_MEDIUM_LOB_OUT_RISK = 5
    const val DEFAULT_MEDIUM_LOB_SHRINK = 5

    const val DEFAULT_HARD_LOB_NET_RISK = 0
    const val DEFAULT_HARD_LOB_OUT_RISK = 9
    const val DEFAULT_HARD_LOB_SHRINK = 0

    const val DEFAULT_SOFT_SMASH_NET_RISK = 3
    const val DEFAULT_SOFT_SMASH_OUT_RISK = 1
    const val DEFAULT_SOFT_SMASH_SHRINK = 20

    const val DEFAULT_MEDIUM_SMASH_NET_RISK = 6
    const val DEFAULT_MEDIUM_SMASH_OUT_RISK = 6
    const val DEFAULT_MEDIUM_SMASH_SHRINK = 40

    const val DEFAULT_HARD_SMASH_NET_RISK = 9
    const val DEFAULT_HARD_SMASH_OUT_RISK = 9
    const val DEFAULT_HARD_SMASH_SHRINK = 50

    // Flight Time Modifiers (x100 for integer storage if needed, but we store as Float here for runtime)
    // Storing as Float in memory for easy access, but will sync as Int (x100)
    const val DEFAULT_SOFT_FLAT_FLIGHT = 1.3f
    const val DEFAULT_MEDIUM_FLAT_FLIGHT = 1.0f
    const val DEFAULT_HARD_FLAT_FLIGHT = 0.7f

    const val DEFAULT_SOFT_LOB_FLIGHT = 1.5f
    const val DEFAULT_MEDIUM_LOB_FLIGHT = 1.7f
    const val DEFAULT_HARD_LOB_FLIGHT = 1.9f

    const val DEFAULT_SOFT_SMASH_FLIGHT = 0.8f
    const val DEFAULT_MEDIUM_SMASH_FLIGHT = 0.5f
    const val DEFAULT_HARD_SMASH_FLIGHT = 0.3f

    // Serve Flight Time Defaults (New for v2)
    // Lobs serves are slower/higher arc than rally lobs to allow rhythm
    const val DEFAULT_SOFT_LOB_SERVE_FLIGHT = 1.9f // was 1.5
    const val DEFAULT_MEDIUM_LOB_SERVE_FLIGHT = 2.2f // was 1.7
    const val DEFAULT_HARD_LOB_SERVE_FLIGHT = 2.5f // was 1.9

    // Smash serves use Lob rally times originally, but now have their own extended times
    // Original logic mapped Smash Serve -> Lob Rally Flight.
    // e.g. Hard Smash Serve used Hard Lob Flight (1.9).
    // Now we want even longer.
    const val DEFAULT_SOFT_SMASH_SERVE_FLIGHT = 1.8f
    const val DEFAULT_MEDIUM_SMASH_SERVE_FLIGHT = 2.1f
    const val DEFAULT_HARD_SMASH_SERVE_FLIGHT = 2.4f

    // SOFT_FLAT
    var softFlatNetRisk: Int = DEFAULT_SOFT_FLAT_NET_RISK
    var softFlatOutRisk: Int = DEFAULT_SOFT_FLAT_OUT_RISK
    var softFlatShrink: Int = DEFAULT_SOFT_FLAT_SHRINK
    var softFlatFlight: Float = DEFAULT_SOFT_FLAT_FLIGHT

    // MEDIUM_FLAT
    var mediumFlatNetRisk: Int = DEFAULT_MEDIUM_FLAT_NET_RISK
    var mediumFlatOutRisk: Int = DEFAULT_MEDIUM_FLAT_OUT_RISK
    var mediumFlatShrink: Int = DEFAULT_MEDIUM_FLAT_SHRINK
    var mediumFlatFlight: Float = DEFAULT_MEDIUM_FLAT_FLIGHT

    // HARD_FLAT
    var hardFlatNetRisk: Int = DEFAULT_HARD_FLAT_NET_RISK
    var hardFlatOutRisk: Int = DEFAULT_HARD_FLAT_OUT_RISK
    var hardFlatShrink: Int = DEFAULT_HARD_FLAT_SHRINK
    var hardFlatFlight: Float = DEFAULT_HARD_FLAT_FLIGHT

    // SOFT_LOB
    var softLobNetRisk: Int = DEFAULT_SOFT_LOB_NET_RISK
    var softLobOutRisk: Int = DEFAULT_SOFT_LOB_OUT_RISK
    var softLobShrink: Int = DEFAULT_SOFT_LOB_SHRINK
    var softLobFlight: Float = DEFAULT_SOFT_LOB_FLIGHT

    // MEDIUM_LOB
    var mediumLobNetRisk: Int = DEFAULT_MEDIUM_LOB_NET_RISK
    var mediumLobOutRisk: Int = DEFAULT_MEDIUM_LOB_OUT_RISK
    var mediumLobShrink: Int = DEFAULT_MEDIUM_LOB_SHRINK
    var mediumLobFlight: Float = DEFAULT_MEDIUM_LOB_FLIGHT

    // HARD_LOB
    var hardLobNetRisk: Int = DEFAULT_HARD_LOB_NET_RISK
    var hardLobOutRisk: Int = DEFAULT_HARD_LOB_OUT_RISK
    var hardLobShrink: Int = DEFAULT_HARD_LOB_SHRINK
    var hardLobFlight: Float = DEFAULT_HARD_LOB_FLIGHT

    // SOFT_SMASH
    var softSmashNetRisk: Int = DEFAULT_SOFT_SMASH_NET_RISK
    var softSmashOutRisk: Int = DEFAULT_SOFT_SMASH_OUT_RISK
    var softSmashShrink: Int = DEFAULT_SOFT_SMASH_SHRINK
    var softSmashFlight: Float = DEFAULT_SOFT_SMASH_FLIGHT

    // MEDIUM_SMASH
    var mediumSmashNetRisk: Int = DEFAULT_MEDIUM_SMASH_NET_RISK
    var mediumSmashOutRisk: Int = DEFAULT_MEDIUM_SMASH_OUT_RISK
    var mediumSmashShrink: Int = DEFAULT_MEDIUM_SMASH_SHRINK
    var mediumSmashFlight: Float = DEFAULT_MEDIUM_SMASH_FLIGHT

    // HARD_SMASH
    var hardSmashNetRisk: Int = DEFAULT_HARD_SMASH_NET_RISK
    var hardSmashOutRisk: Int = DEFAULT_HARD_SMASH_OUT_RISK
    var hardSmashShrink: Int = DEFAULT_HARD_SMASH_SHRINK
    var hardSmashFlight: Float = DEFAULT_HARD_SMASH_FLIGHT

    // SERVE FLIGHT TIMES
    
    // SOFT_LOB_SERVE
    var softLobServeFlight: Float = DEFAULT_SOFT_LOB_SERVE_FLIGHT
    
    // MEDIUM_LOB_SERVE
    var mediumLobServeFlight: Float = DEFAULT_MEDIUM_LOB_SERVE_FLIGHT
    
    // HARD_LOB_SERVE
    var hardLobServeFlight: Float = DEFAULT_HARD_LOB_SERVE_FLIGHT

    // SOFT_SMASH_SERVE
    var softSmashServeFlight: Float = DEFAULT_SOFT_SMASH_SERVE_FLIGHT
    
    // MEDIUM_SMASH_SERVE
    var mediumSmashServeFlight: Float = DEFAULT_MEDIUM_SMASH_SERVE_FLIGHT
    
    // HARD_SMASH_SERVE
    var hardSmashServeFlight: Float = DEFAULT_HARD_SMASH_SERVE_FLIGHT

    /**
     * Snapshot of all mutable swing settings.
     */
    data class Snapshot(
        val softFlatNetRisk: Int, val softFlatOutRisk: Int, val softFlatShrink: Int, val softFlatFlight: Float,
        val mediumFlatNetRisk: Int, val mediumFlatOutRisk: Int, val mediumFlatShrink: Int, val mediumFlatFlight: Float,
        val hardFlatNetRisk: Int, val hardFlatOutRisk: Int, val hardFlatShrink: Int, val hardFlatFlight: Float,
        val softLobNetRisk: Int, val softLobOutRisk: Int, val softLobShrink: Int, val softLobFlight: Float,
        val mediumLobNetRisk: Int, val mediumLobOutRisk: Int, val mediumLobShrink: Int, val mediumLobFlight: Float,
        val hardLobNetRisk: Int, val hardLobOutRisk: Int, val hardLobShrink: Int, val hardLobFlight: Float,
        val softSmashNetRisk: Int, val softSmashOutRisk: Int, val softSmashShrink: Int, val softSmashFlight: Float,
        val mediumSmashNetRisk: Int, val mediumSmashOutRisk: Int, val mediumSmashShrink: Int, val mediumSmashFlight: Float,
        val hardSmashNetRisk: Int, val hardSmashOutRisk: Int, val hardSmashShrink: Int, val hardSmashFlight: Float,
        val softLobServeFlight: Float, val mediumLobServeFlight: Float, val hardLobServeFlight: Float,
        val softSmashServeFlight: Float, val mediumSmashServeFlight: Float, val hardSmashServeFlight: Float
    )

    fun getSnapshot(): Snapshot {
        return Snapshot(
            softFlatNetRisk, softFlatOutRisk, softFlatShrink, softFlatFlight,
            mediumFlatNetRisk, mediumFlatOutRisk, mediumFlatShrink, mediumFlatFlight,
            hardFlatNetRisk, hardFlatOutRisk, hardFlatShrink, hardFlatFlight,
            softLobNetRisk, softLobOutRisk, softLobShrink, softLobFlight,
            mediumLobNetRisk, mediumLobOutRisk, mediumLobShrink, mediumLobFlight,
            hardLobNetRisk, hardLobOutRisk, hardLobShrink, hardLobFlight,
            softSmashNetRisk, softSmashOutRisk, softSmashShrink, softSmashFlight,
            mediumSmashNetRisk, mediumSmashOutRisk, mediumSmashShrink, mediumSmashFlight,
            hardSmashNetRisk, hardSmashOutRisk, hardSmashShrink, hardSmashFlight,
            softLobServeFlight, mediumLobServeFlight, hardLobServeFlight,
            softSmashServeFlight, mediumSmashServeFlight, hardSmashServeFlight
        )
    }

    fun restoreSnapshot(snapshot: Snapshot) {
        softFlatNetRisk = snapshot.softFlatNetRisk
        softFlatOutRisk = snapshot.softFlatOutRisk
        softFlatShrink = snapshot.softFlatShrink
        softFlatFlight = snapshot.softFlatFlight
        
        mediumFlatNetRisk = snapshot.mediumFlatNetRisk
        mediumFlatOutRisk = snapshot.mediumFlatOutRisk
        mediumFlatShrink = snapshot.mediumFlatShrink
        mediumFlatFlight = snapshot.mediumFlatFlight
        
        hardFlatNetRisk = snapshot.hardFlatNetRisk
        hardFlatOutRisk = snapshot.hardFlatOutRisk
        hardFlatShrink = snapshot.hardFlatShrink
        hardFlatFlight = snapshot.hardFlatFlight

        softLobNetRisk = snapshot.softLobNetRisk
        softLobOutRisk = snapshot.softLobOutRisk
        softLobShrink = snapshot.softLobShrink
        softLobFlight = snapshot.softLobFlight
        
        mediumLobNetRisk = snapshot.mediumLobNetRisk
        mediumLobOutRisk = snapshot.mediumLobOutRisk
        mediumLobShrink = snapshot.mediumLobShrink
        mediumLobFlight = snapshot.mediumLobFlight
        
        hardLobNetRisk = snapshot.hardLobNetRisk
        hardLobOutRisk = snapshot.hardLobOutRisk
        hardLobShrink = snapshot.hardLobShrink
        hardLobFlight = snapshot.hardLobFlight

        softSmashNetRisk = snapshot.softSmashNetRisk
        softSmashOutRisk = snapshot.softSmashOutRisk
        softSmashShrink = snapshot.softSmashShrink
        softSmashFlight = snapshot.softSmashFlight
        
        mediumSmashNetRisk = snapshot.mediumSmashNetRisk
        mediumSmashOutRisk = snapshot.mediumSmashOutRisk
        mediumSmashShrink = snapshot.mediumSmashShrink
        mediumSmashFlight = snapshot.mediumSmashFlight
        
        hardSmashNetRisk = snapshot.hardSmashNetRisk
        hardSmashOutRisk = snapshot.hardSmashOutRisk
        hardSmashShrink = snapshot.hardSmashShrink
        hardSmashFlight = snapshot.hardSmashFlight

        softLobServeFlight = snapshot.softLobServeFlight
        mediumLobServeFlight = snapshot.mediumLobServeFlight
        hardLobServeFlight = snapshot.hardLobServeFlight
        
        softSmashServeFlight = snapshot.softSmashServeFlight
        mediumSmashServeFlight = snapshot.mediumSmashServeFlight
        hardSmashServeFlight = snapshot.hardSmashServeFlight
    }
}
