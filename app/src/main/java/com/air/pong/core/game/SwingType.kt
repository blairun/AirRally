package com.air.pong.core.game

/**
 * Classifies the type of swing based on sensor data.
 * Affects flight time and risk.
 */
enum class SwingType {
    SOFT_FLAT,
    MEDIUM_FLAT,
    HARD_FLAT,
    
    SOFT_LOB,
    MEDIUM_LOB,
    HARD_LOB,
    
    SOFT_SMASH,
    MEDIUM_SMASH,
    HARD_SMASH;

    /**
     * Maps the swing type to a 3x3 grid index (0-8).
     * Rows are Force (Soft, Medium, Hard).
     * Columns are Type (Flat, Lob, Smash).
     */
    fun getGridIndex(): Int {
        // Rows = Type (Lob, Flat, Smash)
        val row = when {
            name.endsWith("LOB") -> 0
            name.endsWith("FLAT") -> 1
            else -> 2 // SMASH
        }
        // Columns = Force (Soft, Medium, Hard)
        val col = when {
            name.startsWith("SOFT") -> 0
            name.startsWith("MEDIUM") -> 1
            else -> 2 // HARD
        }
        return row * 3 + col
    }
}
