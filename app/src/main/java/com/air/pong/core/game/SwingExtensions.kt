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
 * Uses three independent thresholds for soft, medium, and hard classification.
 */
fun classifySwing(force: Float, gravZ: Float, softThreshold: Float, mediumThreshold: Float, hardThreshold: Float): SwingType {
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
        force > hardThreshold -> "HARD"
        force > mediumThreshold -> "MEDIUM"
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

/**
 * Spin types based on gyroscope rotation during swing.
 * Y axis (top/back) and Z axis (left/right) are evaluated independently.
 * Naming: "back spin" = backspin = underspin (wrist rolls backward)
 */
enum class SpinType {
    NONE,         // No significant spin
    TOP,          // Top spin only (wrist rolls forward, Gy < -10)
    BACK,         // Back spin only (wrist rolls backward, Gy > 10)
    RIGHT,        // Side spin right only
    LEFT,         // Side spin left only
    TOP_RIGHT,    // Top + right
    TOP_LEFT,     // Top + left
    BACK_RIGHT,   // Back + right
    BACK_LEFT;    // Back + left
    
    companion object {
        internal const val Y_THRESHOLD = 10.0f
        internal const val Z_THRESHOLD = 8.0f
        
        // Spin timing effect constants
        // TOP spin: faster flight, smaller window (aggressive)
        const val TOP_FLIGHT_MODIFIER = 0.90f      // -10% flight time
        const val TOP_WINDOW_MODIFIER = 0.90f      // -10% hit window
        
        // BACK spin: slower flight, slightly smaller window (defensive/tricky)
        const val BACK_FLIGHT_MODIFIER = 1.10f     // +10% flight time
        const val BACK_WINDOW_MODIFIER = 0.95f     // -5% hit window
        
        // Side spin: chaotic ±5% for both (random per shot)
        const val SIDE_MODIFIER_VARIANCE = 0.05f   // ±5%
        
        /**
         * Classifies spin type from peak gyroscope readings.
         * 
         * @param peakGy Peak Y-axis rotation (rad/s)
         * @param peakGz Peak Z-axis rotation (rad/s)
         * @param isLeftHanded If true, inverts Y-axis to correct for left-handed grip
         * @return The detected spin type
         */
        fun fromGyro(peakGy: Float, peakGz: Float, isLeftHanded: Boolean = false): SpinType {
            // For left-handed players, invert Y-axis to correct top/back spin
            val effectiveGy = if (isLeftHanded) -peakGy else peakGy
            
            val hasTop = effectiveGy < -Y_THRESHOLD    // Wrist rolls forward
            val hasBack = effectiveGy > Y_THRESHOLD    // Wrist rolls backward
            val hasLeft = peakGz < -Z_THRESHOLD
            val hasRight = peakGz > Z_THRESHOLD
            
            return when {
                hasTop && hasLeft -> TOP_LEFT
                hasTop && hasRight -> TOP_RIGHT
                hasBack && hasLeft -> BACK_LEFT
                hasBack && hasRight -> BACK_RIGHT
                hasTop -> TOP
                hasBack -> BACK
                hasLeft -> LEFT
                hasRight -> RIGHT
                else -> NONE
            }
        }
        
        /**
         * Returns all spin types that count toward the spin bonus.
         * Excludes NONE since it doesn't contribute to the bonus.
         */
        val allBonusSpinTypes: Set<SpinType> = entries.filter { it != NONE }.toSet()
        
        /**
         * Maximum spin multiplier bonus achievable (+0.1 per unique type, max 0.5).
         * 5 unique types = max bonus and infinite spin.
         */
        const val MAX_SPIN_MULTIPLIER_BONUS = 0.5f
        const val SPIN_BONUS_PER_TYPE = 0.1f
        
        /**
         * Seconds of avatar spin duration added per ANY spin shot (not just new types).
         */
        const val SPIN_DURATION_PER_SHOT_SECONDS = 15L
        
        /**
         * Number of unique spin types needed while avatar is spinning
         * to trigger infinite spin for the rest of the GAME (survives misses).
         */
        const val INFINITE_SPIN_THRESHOLD = 5
    }
    
    /**
     * Returns true if this spin type has a top spin component.
     */
    fun hasTop(): Boolean = this == TOP || this == TOP_LEFT || this == TOP_RIGHT
    
    /**
     * Returns true if this spin type has a back spin component.
     */
    fun hasBack(): Boolean = this == BACK || this == BACK_LEFT || this == BACK_RIGHT
    
    /**
     * Returns true if this spin type has a side spin component (left or right).
     */
    fun hasSide(): Boolean = this == LEFT || this == RIGHT || 
                             this == TOP_LEFT || this == TOP_RIGHT ||
                             this == BACK_LEFT || this == BACK_RIGHT
}

/**
 * Returns the flight time modifier for this spin type.
 * Applied multiplicatively after SwingType flight modifier.
 * 
 * - TOP: 0.90 (-10%, faster)
 * - BACK: 1.10 (+10%, slower)
 * - Side (LEFT/RIGHT): random ±5% based on timestamp seed
 * - Combos: multiplicative (e.g., TOP_RIGHT = TOP × side random)
 * 
 * @param timestamp Used as seed for deterministic randomness on side spins
 */
fun SpinType.getSpinFlightTimeModifier(timestamp: Long): Float {
    if (this == SpinType.NONE) return 1.0f
    
    var modifier = 1.0f
    
    // Apply top/back component
    if (hasTop()) {
        modifier *= SpinType.TOP_FLIGHT_MODIFIER
    } else if (hasBack()) {
        modifier *= SpinType.BACK_FLIGHT_MODIFIER
    }
    
    // Apply side spin randomness if present
    if (hasSide()) {
        // Use timestamp as seed for deterministic randomness
        // Different seed offset for flight vs window to make them independent
        val random = kotlin.random.Random(timestamp)
        val sideModifier = if (random.nextBoolean()) {
            1.0f + SpinType.SIDE_MODIFIER_VARIANCE  // +5%
        } else {
            1.0f - SpinType.SIDE_MODIFIER_VARIANCE  // -5%
        }
        modifier *= sideModifier
    }
    
    return modifier
}

/**
 * Returns the hit window modifier for this spin type.
 * Applied multiplicatively after SwingType window shrink.
 * 
 * - TOP: 0.90 (-10%, smaller window)
 * - BACK: 0.95 (-5%, slightly smaller window)
 * - Side (LEFT/RIGHT): random ±5% based on timestamp seed (independent from flight)
 * - Combos: multiplicative
 * 
 * @param timestamp Used as seed for deterministic randomness on side spins
 */
fun SpinType.getSpinWindowModifier(timestamp: Long): Float {
    if (this == SpinType.NONE) return 1.0f
    
    var modifier = 1.0f
    
    // Apply top/back component
    if (hasTop()) {
        modifier *= SpinType.TOP_WINDOW_MODIFIER
    } else if (hasBack()) {
        modifier *= SpinType.BACK_WINDOW_MODIFIER
    }
    
    // Apply side spin randomness if present
    if (hasSide()) {
        // Use timestamp + offset as seed to make window randomness independent from flight
        val random = kotlin.random.Random(timestamp + 12345L)
        val sideModifier = if (random.nextBoolean()) {
            1.0f + SpinType.SIDE_MODIFIER_VARIANCE  // +5%
        } else {
            1.0f - SpinType.SIDE_MODIFIER_VARIANCE  // -5%
        }
        modifier *= sideModifier
    }
    
    return modifier
}

/**
 * Copy Cat bonus constants and utilities.
 */
object CopyCatConstants {
    const val MAX_COPY_CAT_MULTIPLIER_BONUS = 0.5f
    const val COPY_CAT_BONUS_PER_TIER = 0.1f
    const val MAX_COPY_SEQUENCE_LENGTH = 9
    const val MIN_COPY_SEQUENCE_LENGTH = 5
    
    // Solo mode: Cooldown range between CPU sequences (10-20 hits)
    const val SOLO_COPYCAT_COOLDOWN_MIN = 10
    const val SOLO_COPYCAT_COOLDOWN_MAX = 20
    
    /**
     * Tier requirements: (copiesNeeded, uniqueTypesRequired)
     * Tier 1: 5 copies with 3+ unique types = +0.1
     * Tier 2: 6 copies with 4+ unique types = +0.2
     * ...
     * Tier 5: 9 copies with 7+ unique types = +0.5
     */
    val TIER_REQUIREMENTS = listOf(
        5 to 3,  // Tier 1
        6 to 4,  // Tier 2
        7 to 5,  // Tier 3
        8 to 6,  // Tier 4
        9 to 7   // Tier 5
    )
    
    /**
     * Calculate the Copy Cat tier based on copy count and unique types.
     * Returns 0 if requirements not met, 1-5 for achieved tier.
     */
    fun calculateCopyCatTier(copyCount: Int, uniqueTypes: Int): Int {
        for (tier in TIER_REQUIREMENTS.indices.reversed()) {
            val (copiesNeeded, uniqueNeeded) = TIER_REQUIREMENTS[tier]
            if (copyCount >= copiesNeeded && uniqueTypes >= uniqueNeeded) {
                return tier + 1  // 1-indexed tier
            }
        }
        return 0
    }
    
    /**
     * Calculate the multiplier bonus for a given tier.
     */
    fun multiplierForTier(tier: Int): Float {
        return (tier * COPY_CAT_BONUS_PER_TIER).coerceAtMost(MAX_COPY_CAT_MULTIPLIER_BONUS)
    }
    
    /**
     * Check if it's still possible to reach at least tier 1 given current progress.
     * Pattern should break if this returns false.
     * 
     * The formula: with N copies and U unique types, we need at least 3 unique types
     * by the time we hit 5 copies. If copyCount reaches the sequence max (9) without
     * enough unique types, we should also break.
     * 
     * Specifically: at copyCount C, we need uniqueTypes >= C - 2 to still be on track.
     * (Because tier 1 = 5 copies with 3 unique, tier 2 = 6 with 4, etc.)
     */
    fun canReachMinimumTier(copyCount: Int, uniqueTypes: Int): Boolean {
        // If we're at or below 2 copies, we can always still succeed
        if (copyCount <= 2) return true
        
        // At copyCount C, we need at least (C - 2) unique types to be "on pace"
        // for the tier requirements (5 copies = 3 unique, 6 = 4, etc.)
        val minimumUniqueNeeded = copyCount - 2
        
        return uniqueTypes >= minimumUniqueNeeded
    }
}

/**
 * Power Outage mechanic constants and utilities.
 * 
 * When a player hits the same grid cell too many times, a "power outage" occurs
 * that blacks out most UI elements. Player must hit 5 different cells consecutively
 * to restore power and earn a hit window bonus.
 */
object PowerOutageConstants {
    /** Number of hits on a single cell to trigger an outage */
    const val TRIGGER_HITS = 10
    
    /** Hit count threshold to start showing warning (🔌#) */
    const val WARNING_THRESHOLD = 7
    
    /** Number of consecutive unique cells needed to restore power */
    const val RECOVERY_UNIQUE_CELLS = 5
    
    /** Hit window bonus (ms) earned for successful power restoration */
    const val WINDOW_BONUS_MS = 50L
}
