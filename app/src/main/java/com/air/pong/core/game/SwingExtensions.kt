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
        SwingType.SOFT_SPIKE -> 0.0f
        SwingType.MEDIUM_SPIKE -> 0.40f
        SwingType.HARD_SPIKE -> 0.60f
    }
}

/**
 * Returns the risk percentages (NetRisk, OutRisk) for the swing type.
 * Values are 0-100.
 */
fun SwingType.getRiskPercentages(): Pair<Int, Int> {
    return when (this) {
        SwingType.SOFT_FLAT -> 0 to 0
        SwingType.MEDIUM_FLAT -> 0 to 5
        SwingType.HARD_FLAT -> 5 to 15
        SwingType.SOFT_LOB -> 0 to 0
        SwingType.MEDIUM_LOB -> 0 to 5
        SwingType.HARD_LOB -> 0 to 20
        SwingType.SOFT_SPIKE -> 50 to 0
        SwingType.MEDIUM_SPIKE -> 15 to 5
        SwingType.HARD_SPIKE -> 30 to 10
    }
}

/**
 * Classifies a swing based on force and gravity Z (tilt).
 */
fun classifySwing(force: Float, gravZ: Float, minThreshold: Float): SwingType {
    // Classification based on Gravity Z (Tilt)
    // Gravity Z > 3.0 -> Screen Tilted Up -> LOB
    // Gravity Z < -3.0 -> Screen Tilted Down -> SPIKE
    // Else -> Screen Vertical -> FLAT
    
    val type = when {
        gravZ > 3.0f -> "LOB"
        gravZ < -3.0f -> "SPIKE"
        else -> "FLAT"
    }
    
    val intensity = when {
        force > (minThreshold + 8.0f) -> "HARD"   // e.g. 12+8 = 20
        force > (minThreshold + 5.0f) -> "MEDIUM" // e.g. 12+5 = 17
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
        SwingType.SOFT_FLAT -> 1.2f
        SwingType.MEDIUM_FLAT -> 1.0f
        SwingType.HARD_FLAT -> 0.8f
        SwingType.SOFT_LOB -> 1.4f
        SwingType.MEDIUM_LOB -> 1.5f
        SwingType.HARD_LOB -> 1.6f
        SwingType.SOFT_SPIKE -> 0.8f
        SwingType.MEDIUM_SPIKE -> 0.6f
        SwingType.HARD_SPIKE -> 0.4f
    }
}
