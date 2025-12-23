package com.air.pong.core.game

/**
 * Types of bonuses that can be earned during Rally mode.
 * Used for the dynamic STREAK label display.
 */
enum class BonusType(val displayText: String) {
    /** Base rally streak (momentum multiplier active) */
    RALLY("Rally!"),
    
    /** Spin shot bonus earned */
    SPIN("Spin!"),
    
    /** Copy cat bonus earned */
    COPYCAT("Copycat!"),
    
    /** Golden square bonus earned */
    GOLD("Gold!"),
    
    /** Line completion bonus */
    LINE("Line!"),
    
    /** Tier upgrade achieved */
    TIER("Tier Up!"),
    
    /** Shield earned (from clearing grid with banana) */
    SHIELD("Shield!")
}
