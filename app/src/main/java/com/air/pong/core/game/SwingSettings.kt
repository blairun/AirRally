package com.air.pong.core.game

/**
 * Immutable data class holding all swing settings.
 * Use [SwingSettings.Companion.createDefault] to create a default instance.
 */
data class SwingSettings(
    // SOFT_FLAT
    val softFlatNetRisk: Int,
    val softFlatOutRisk: Int,
    val softFlatShrink: Int,
    val softFlatFlight: Float,

    // MEDIUM_FLAT
    val mediumFlatNetRisk: Int,
    val mediumFlatOutRisk: Int,
    val mediumFlatShrink: Int,
    val mediumFlatFlight: Float,

    // HARD_FLAT
    val hardFlatNetRisk: Int,
    val hardFlatOutRisk: Int,
    val hardFlatShrink: Int,
    val hardFlatFlight: Float,

    // SOFT_LOB
    val softLobNetRisk: Int,
    val softLobOutRisk: Int,
    val softLobShrink: Int,
    val softLobFlight: Float,

    // MEDIUM_LOB
    val mediumLobNetRisk: Int,
    val mediumLobOutRisk: Int,
    val mediumLobShrink: Int,
    val mediumLobFlight: Float,

    // HARD_LOB
    val hardLobNetRisk: Int,
    val hardLobOutRisk: Int,
    val hardLobShrink: Int,
    val hardLobFlight: Float,

    // SOFT_SMASH
    val softSmashNetRisk: Int,
    val softSmashOutRisk: Int,
    val softSmashShrink: Int,
    val softSmashFlight: Float,

    // MEDIUM_SMASH
    val mediumSmashNetRisk: Int,
    val mediumSmashOutRisk: Int,
    val mediumSmashShrink: Int,
    val mediumSmashFlight: Float,

    // HARD_SMASH
    val hardSmashNetRisk: Int,
    val hardSmashOutRisk: Int,
    val hardSmashShrink: Int,
    val hardSmashFlight: Float,

    // SERVE FLIGHT TIMES
    val softLobServeFlight: Float,
    val mediumLobServeFlight: Float,
    val hardLobServeFlight: Float,
    val softSmashServeFlight: Float,
    val mediumSmashServeFlight: Float,
    val hardSmashServeFlight: Float
) {
    companion object {
        // Constants for Defaults
        const val DEFAULT_SOFT_FLAT_NET_RISK = 1
        const val DEFAULT_SOFT_FLAT_OUT_RISK = 0
        const val DEFAULT_SOFT_FLAT_SHRINK = 0

        const val DEFAULT_MEDIUM_FLAT_NET_RISK = 1
        const val DEFAULT_MEDIUM_FLAT_OUT_RISK = 1
        const val DEFAULT_MEDIUM_FLAT_SHRINK = 20

        const val DEFAULT_HARD_FLAT_NET_RISK = 2
        const val DEFAULT_HARD_FLAT_OUT_RISK = 3
        const val DEFAULT_HARD_FLAT_SHRINK = 40

        const val DEFAULT_SOFT_LOB_NET_RISK = 0
        const val DEFAULT_SOFT_LOB_OUT_RISK = 1
        const val DEFAULT_SOFT_LOB_SHRINK = 10

        const val DEFAULT_MEDIUM_LOB_NET_RISK = 0
        const val DEFAULT_MEDIUM_LOB_OUT_RISK = 2
        const val DEFAULT_MEDIUM_LOB_SHRINK = 5

        const val DEFAULT_HARD_LOB_NET_RISK = 0
        const val DEFAULT_HARD_LOB_OUT_RISK = 4
        const val DEFAULT_HARD_LOB_SHRINK = 0

        const val DEFAULT_SOFT_SMASH_NET_RISK = 1
        const val DEFAULT_SOFT_SMASH_OUT_RISK = 1
        const val DEFAULT_SOFT_SMASH_SHRINK = 20

        const val DEFAULT_MEDIUM_SMASH_NET_RISK = 2
        const val DEFAULT_MEDIUM_SMASH_OUT_RISK = 2
        const val DEFAULT_MEDIUM_SMASH_SHRINK = 40

        const val DEFAULT_HARD_SMASH_NET_RISK = 5
        const val DEFAULT_HARD_SMASH_OUT_RISK = 5
        const val DEFAULT_HARD_SMASH_SHRINK = 50

        // Flight Time Modifiers
        const val DEFAULT_SOFT_FLAT_FLIGHT = 1.3f
        const val DEFAULT_MEDIUM_FLAT_FLIGHT = 1.0f
        const val DEFAULT_HARD_FLAT_FLIGHT = 0.7f

        const val DEFAULT_SOFT_LOB_FLIGHT = 1.5f
        const val DEFAULT_MEDIUM_LOB_FLIGHT = 1.7f
        const val DEFAULT_HARD_LOB_FLIGHT = 1.9f

        const val DEFAULT_SOFT_SMASH_FLIGHT = 0.8f
        const val DEFAULT_MEDIUM_SMASH_FLIGHT = 0.6f
        const val DEFAULT_HARD_SMASH_FLIGHT = 0.5f

        // Serve Flight Time Defaults
        const val DEFAULT_SOFT_LOB_SERVE_FLIGHT = 1.9f
        const val DEFAULT_MEDIUM_LOB_SERVE_FLIGHT = 2.2f
        const val DEFAULT_HARD_LOB_SERVE_FLIGHT = 2.5f

        const val DEFAULT_SOFT_SMASH_SERVE_FLIGHT = 1.8f
        const val DEFAULT_MEDIUM_SMASH_SERVE_FLIGHT = 2.1f
        const val DEFAULT_HARD_SMASH_SERVE_FLIGHT = 2.4f

        /**
         * Creates a SwingSettings instance with all default values.
         */
        fun createDefault(): SwingSettings = SwingSettings(
            softFlatNetRisk = DEFAULT_SOFT_FLAT_NET_RISK,
            softFlatOutRisk = DEFAULT_SOFT_FLAT_OUT_RISK,
            softFlatShrink = DEFAULT_SOFT_FLAT_SHRINK,
            softFlatFlight = DEFAULT_SOFT_FLAT_FLIGHT,

            mediumFlatNetRisk = DEFAULT_MEDIUM_FLAT_NET_RISK,
            mediumFlatOutRisk = DEFAULT_MEDIUM_FLAT_OUT_RISK,
            mediumFlatShrink = DEFAULT_MEDIUM_FLAT_SHRINK,
            mediumFlatFlight = DEFAULT_MEDIUM_FLAT_FLIGHT,

            hardFlatNetRisk = DEFAULT_HARD_FLAT_NET_RISK,
            hardFlatOutRisk = DEFAULT_HARD_FLAT_OUT_RISK,
            hardFlatShrink = DEFAULT_HARD_FLAT_SHRINK,
            hardFlatFlight = DEFAULT_HARD_FLAT_FLIGHT,

            softLobNetRisk = DEFAULT_SOFT_LOB_NET_RISK,
            softLobOutRisk = DEFAULT_SOFT_LOB_OUT_RISK,
            softLobShrink = DEFAULT_SOFT_LOB_SHRINK,
            softLobFlight = DEFAULT_SOFT_LOB_FLIGHT,

            mediumLobNetRisk = DEFAULT_MEDIUM_LOB_NET_RISK,
            mediumLobOutRisk = DEFAULT_MEDIUM_LOB_OUT_RISK,
            mediumLobShrink = DEFAULT_MEDIUM_LOB_SHRINK,
            mediumLobFlight = DEFAULT_MEDIUM_LOB_FLIGHT,

            hardLobNetRisk = DEFAULT_HARD_LOB_NET_RISK,
            hardLobOutRisk = DEFAULT_HARD_LOB_OUT_RISK,
            hardLobShrink = DEFAULT_HARD_LOB_SHRINK,
            hardLobFlight = DEFAULT_HARD_LOB_FLIGHT,

            softSmashNetRisk = DEFAULT_SOFT_SMASH_NET_RISK,
            softSmashOutRisk = DEFAULT_SOFT_SMASH_OUT_RISK,
            softSmashShrink = DEFAULT_SOFT_SMASH_SHRINK,
            softSmashFlight = DEFAULT_SOFT_SMASH_FLIGHT,

            mediumSmashNetRisk = DEFAULT_MEDIUM_SMASH_NET_RISK,
            mediumSmashOutRisk = DEFAULT_MEDIUM_SMASH_OUT_RISK,
            mediumSmashShrink = DEFAULT_MEDIUM_SMASH_SHRINK,
            mediumSmashFlight = DEFAULT_MEDIUM_SMASH_FLIGHT,

            hardSmashNetRisk = DEFAULT_HARD_SMASH_NET_RISK,
            hardSmashOutRisk = DEFAULT_HARD_SMASH_OUT_RISK,
            hardSmashShrink = DEFAULT_HARD_SMASH_SHRINK,
            hardSmashFlight = DEFAULT_HARD_SMASH_FLIGHT,

            softLobServeFlight = DEFAULT_SOFT_LOB_SERVE_FLIGHT,
            mediumLobServeFlight = DEFAULT_MEDIUM_LOB_SERVE_FLIGHT,
            hardLobServeFlight = DEFAULT_HARD_LOB_SERVE_FLIGHT,

            softSmashServeFlight = DEFAULT_SOFT_SMASH_SERVE_FLIGHT,
            mediumSmashServeFlight = DEFAULT_MEDIUM_SMASH_SERVE_FLIGHT,
            hardSmashServeFlight = DEFAULT_HARD_SMASH_SERVE_FLIGHT
        )
    }
}
