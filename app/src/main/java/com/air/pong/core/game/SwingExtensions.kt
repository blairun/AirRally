package com.air.pong.core.game

/**
 * Returns the shrink percentage (0.0 to 1.0) for the hit window based on the swing type.
 * Harder shots shrink the opponent's window more.
 */
fun SwingType.getWindowShrinkPercentage(): Float {
    return when (this) {
        SwingType.SOFT_FLAT -> 0.0f
        SwingType.MEDIUM_FLAT -> 0.20f
        SwingType.HARD_FLAT -> 0.35f
        SwingType.SOFT_LOB -> 0.0f
        SwingType.MEDIUM_LOB -> 0.0f
        SwingType.HARD_LOB -> 0.0f
        SwingType.SOFT_SMASH -> 0.20f
        SwingType.MEDIUM_SMASH -> 0.40f
        SwingType.HARD_SMASH -> 0.60f
    }
}

/**
 * Returns the risk percentages (NetRisk, OutRisk) for the swing type.
 * Values are 0-100.
 */
fun SwingType.getRiskPercentages(): Pair<Int, Int> {
    return when (this) {
        SwingType.SOFT_FLAT -> 0 to 0
        SwingType.MEDIUM_FLAT -> 0 to 2
        SwingType.HARD_FLAT -> 5 to 10
        SwingType.SOFT_LOB -> 0 to 0
        SwingType.MEDIUM_LOB -> 0 to 5
        SwingType.HARD_LOB -> 0 to 15
        SwingType.SOFT_SMASH -> 10 to 0
        SwingType.MEDIUM_SMASH -> 15 to 5
        SwingType.HARD_SMASH -> 20 to 10
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
        SwingType.SOFT_FLAT -> 1.3f
        SwingType.MEDIUM_FLAT -> 1.0f
        SwingType.HARD_FLAT -> 0.7f
        SwingType.SOFT_LOB -> 1.5f
        SwingType.MEDIUM_LOB -> 1.5f
        SwingType.HARD_LOB -> 1.6f
        SwingType.SOFT_SMASH -> 0.8f
        SwingType.MEDIUM_SMASH -> 0.6f
        SwingType.HARD_SMASH -> 0.4f
    }
}
