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
    
    SOFT_SPIKE,
    MEDIUM_SPIKE,
    HARD_SPIKE
}
