package com.air.pong.core.game

/**
 * Returns the shrink percentage (0.0 to 1.0) for the hit window based on the swing type.
 * Harder shots shrink the opponent's window more.
 */
fun SwingType.getWindowShrinkPercentage(settings: SwingSettings): Float {
    return when (this) {
        SwingType.SOFT_FLAT -> settings.softFlatShrink / 100f
        SwingType.MEDIUM_FLAT -> settings.mediumFlatShrink / 100f
        SwingType.HARD_FLAT -> settings.hardFlatShrink / 100f
        SwingType.SOFT_LOB -> settings.softLobShrink / 100f
        SwingType.MEDIUM_LOB -> settings.mediumLobShrink / 100f
        SwingType.HARD_LOB -> settings.hardLobShrink / 100f
        SwingType.SOFT_SMASH -> settings.softSmashShrink / 100f
        SwingType.MEDIUM_SMASH -> settings.mediumSmashShrink / 100f
        SwingType.HARD_SMASH -> settings.hardSmashShrink / 100f
    }
}

/**
 * Returns the risk percentages (NetRisk, OutRisk) for the swing type.
 * Values are 0-100.
 */
fun SwingType.getRiskPercentages(settings: SwingSettings): Pair<Int, Int> {
    return when (this) {
        SwingType.SOFT_FLAT -> settings.softFlatNetRisk to settings.softFlatOutRisk
        SwingType.MEDIUM_FLAT -> settings.mediumFlatNetRisk to settings.mediumFlatOutRisk
        SwingType.HARD_FLAT -> settings.hardFlatNetRisk to settings.hardFlatOutRisk
        SwingType.SOFT_LOB -> settings.softLobNetRisk to settings.softLobOutRisk
        SwingType.MEDIUM_LOB -> settings.mediumLobNetRisk to settings.mediumLobOutRisk
        SwingType.HARD_LOB -> settings.hardLobNetRisk to settings.hardLobOutRisk
        SwingType.SOFT_SMASH -> settings.softSmashNetRisk to settings.softSmashOutRisk
        SwingType.MEDIUM_SMASH -> settings.mediumSmashNetRisk to settings.mediumSmashOutRisk
        SwingType.HARD_SMASH -> settings.hardSmashNetRisk to settings.hardSmashOutRisk
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
fun SwingType.getFlightTimeModifier(settings: SwingSettings): Float {
    return when (this) {
        SwingType.SOFT_FLAT -> settings.softFlatFlight
        SwingType.MEDIUM_FLAT -> settings.mediumFlatFlight
        SwingType.HARD_FLAT -> settings.hardFlatFlight
        SwingType.SOFT_LOB -> settings.softLobFlight
        SwingType.MEDIUM_LOB -> settings.mediumLobFlight
        SwingType.HARD_LOB -> settings.hardLobFlight
        SwingType.SOFT_SMASH -> settings.softSmashFlight
        SwingType.MEDIUM_SMASH -> settings.mediumSmashFlight
        SwingType.HARD_SMASH -> settings.hardSmashFlight
    }
}

/**
 * Returns the flight time modifier for a SERVE.
 * Special Rule: Smash Serves use LOB flight times (slow) because of the physics (bounce -> high arc).
 */
fun SwingType.getServeFlightTimeModifier(settings: SwingSettings): Float {
    return when {
        this.isLob() -> when (this) {
            SwingType.SOFT_LOB -> settings.softLobServeFlight
            SwingType.MEDIUM_LOB -> settings.mediumLobServeFlight
            SwingType.HARD_LOB -> settings.hardLobServeFlight
            else -> this.getFlightTimeModifier(settings)
        }
        this.isSmash() -> when (this) {
            SwingType.SOFT_SMASH -> settings.softSmashServeFlight
            SwingType.MEDIUM_SMASH -> settings.mediumSmashServeFlight
            SwingType.HARD_SMASH -> settings.hardSmashServeFlight
            else -> this.getFlightTimeModifier(settings)
        }
        else -> this.getFlightTimeModifier(settings)
    }
}

fun SwingType.isLob(): Boolean = this.name.contains("LOB")
fun SwingType.isSmash(): Boolean = this.name.contains("SMASH")
fun SwingType.isFlat(): Boolean = this.name.contains("FLAT")
