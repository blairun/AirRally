package com.air.pong.core.game

/**
 * Returns the shrink percentage (0.0 to 1.0) for the hit window based on the swing type.
 * Harder shots shrink the opponent's window more.
 */
fun SwingType.getWindowShrinkPercentage(): Float {
    return when (this) {
        SwingType.SOFT_FLAT -> SwingSettings.softFlatShrink / 100f
        SwingType.MEDIUM_FLAT -> SwingSettings.mediumFlatShrink / 100f
        SwingType.HARD_FLAT -> SwingSettings.hardFlatShrink / 100f
        SwingType.SOFT_LOB -> SwingSettings.softLobShrink / 100f
        SwingType.MEDIUM_LOB -> SwingSettings.mediumLobShrink / 100f
        SwingType.HARD_LOB -> SwingSettings.hardLobShrink / 100f
        SwingType.SOFT_SMASH -> SwingSettings.softSmashShrink / 100f
        SwingType.MEDIUM_SMASH -> SwingSettings.mediumSmashShrink / 100f
        SwingType.HARD_SMASH -> SwingSettings.hardSmashShrink / 100f
    }
}

/**
 * Returns the risk percentages (NetRisk, OutRisk) for the swing type.
 * Values are 0-100.
 */
fun SwingType.getRiskPercentages(): Pair<Int, Int> {
    return when (this) {
        SwingType.SOFT_FLAT -> SwingSettings.softFlatNetRisk to SwingSettings.softFlatOutRisk
        SwingType.MEDIUM_FLAT -> SwingSettings.mediumFlatNetRisk to SwingSettings.mediumFlatOutRisk
        SwingType.HARD_FLAT -> SwingSettings.hardFlatNetRisk to SwingSettings.hardFlatOutRisk
        SwingType.SOFT_LOB -> SwingSettings.softLobNetRisk to SwingSettings.softLobOutRisk
        SwingType.MEDIUM_LOB -> SwingSettings.mediumLobNetRisk to SwingSettings.mediumLobOutRisk
        SwingType.HARD_LOB -> SwingSettings.hardLobNetRisk to SwingSettings.hardLobOutRisk
        SwingType.SOFT_SMASH -> SwingSettings.softSmashNetRisk to SwingSettings.softSmashOutRisk
        SwingType.MEDIUM_SMASH -> SwingSettings.mediumSmashNetRisk to SwingSettings.mediumSmashOutRisk
        SwingType.HARD_SMASH -> SwingSettings.hardSmashNetRisk to SwingSettings.hardSmashOutRisk
    }
}

/**
 * Classifies a swing based on force and gravity Z (tilt).
 */
fun classifySwing(force: Float, gravZ: Float, minThreshold: Float): SwingType {
    // Classification based on Gravity Z (Tilt)
    // Gravity Z > 5.0 -> Screen Tilted Up -> LOB
    // Gravity Z < -5.0 -> Screen Tilted Down -> SMASH
    // Else -> Screen Vertical -> FLAT
    
    val type = when {
        gravZ > 5.0f -> "LOB"
        gravZ < -5.0f -> "SMASH"
        else -> "FLAT"
    }
    
    val intensity = when {
        force > (minThreshold + 30.0f) -> "HARD"   // e.g. 14+30 = 44
        force > (minThreshold + 9.0f) -> "MEDIUM" // e.g. 14+9 = 23
        else -> "SOFT"
    }
    
    return try {
        SwingType.valueOf("${intensity}_${type}")
    } catch (e: IllegalArgumentException) {
        SwingType.MEDIUM_FLAT
    }
}

/**
 * Returns the flight time modifier for the swing type.
 */
fun SwingType.getFlightTimeModifier(): Float {
    return when (this) {
        SwingType.SOFT_FLAT -> SwingSettings.softFlatFlight
        SwingType.MEDIUM_FLAT -> SwingSettings.mediumFlatFlight
        SwingType.HARD_FLAT -> SwingSettings.hardFlatFlight
        SwingType.SOFT_LOB -> SwingSettings.softLobFlight
        SwingType.MEDIUM_LOB -> SwingSettings.mediumLobFlight
        SwingType.HARD_LOB -> SwingSettings.hardLobFlight
        SwingType.SOFT_SMASH -> SwingSettings.softSmashFlight
        SwingType.MEDIUM_SMASH -> SwingSettings.mediumSmashFlight
        SwingType.HARD_SMASH -> SwingSettings.hardSmashFlight
    }
}
