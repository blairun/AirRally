package com.air.pong.core.game

/**
 * Sealed class hierarchy for mode-specific state.
 * Each game mode can define its own state container.
 */
sealed class ModeState {
    /**
     * Classic (1v1 competitive) mode state.
     * Classic mode uses player1Score/player2Score from GameState, so no additional state needed.
     */
    data object ClassicState : ModeState()
    
    /**
     * Rally (co-op) mode state.
     * Tracks grid, tiers, lives, scoring, and bonus mechanics for both players.
     */
    data class RallyState(
        val score: Int = 0,
        val lives: Int = GameEngine.RALLY_STARTING_LIVES,
        val scorePoints: Int = 0, // Points from current hit
        val linesJustCleared: Int = 0, // Lines cleared in current hit (for popup text)
        
        // Local Player Grid
        val grid: List<Boolean> = List(9) { false },
        val cellTiers: List<Int> = List(9) { 0 },
        val linesCompleted: List<Boolean> = List(8) { false },
        val highestTier: Int = 0,
        val totalLinesCleared: Int = 0,
        // Total lives earned from grid tier upgrades (for bonus chip display)
        val gridUpgradesEarned: Int = 0,
        
        // Opponent Grid (tracked locally for score calculation)
        val opponentGrid: List<Boolean> = List(9) { false },
        val opponentCellTiers: List<Int> = List(9) { 0 },
        val opponentLinesCompleted: List<Boolean> = List(8) { false },
        val opponentHighestTier: Int = 0,
        val partnerTotalLinesCleared: Int = 0,
        
        // Hit tracking
        val lastHitGridIndex: Int = -1,
        val isServeHit: Boolean = false,
        val soundEvent: RallySoundEvent? = null,
        
        // === SPIN BONUS TRACKING (local player) ===
        // Distinct spin types used this rally (resets when avatar stops spinning)
        val distinctSpinTypes: Set<SpinType> = emptySet(),
        // Current spin multiplier bonus (+0.1 per unique type, max 0.5)
        val spinMultiplierBonus: Float = 0f,
        // System time when avatar should stop spinning (0 = not spinning)
        val avatarSpinEndTime: Long = 0L,
        // If true, avatar spins indefinitely (5 types achieved)
        val isAvatarSpinningIndefinitely: Boolean = false,
        
        // === PARTNER SPIN TRACKING (synced via network) ===
        // Partner's distinct spin types (for animation speed and infinite spin detection)
        val partnerDistinctSpinTypes: Set<SpinType> = emptySet(),
        // System time when partner's avatar should stop spinning
        val partnerAvatarSpinEndTime: Long = 0L,
        // If true, partner's avatar spins indefinitely
        val isPartnerSpinningIndefinitely: Boolean = false,
        
        // === COPY CAT TRACKING ===
        // Rolling buffer of shots: (swingType, isLocalPlayer) for pattern detection
        val copyCatShotHistory: List<Pair<SwingType, Boolean>> = emptyList(),
        // Where current copy sequence started in shotHistory (-1 = inactive)
        val copySequenceStartIndex: Int = -1,
        // Current consecutive copies detected
        val copyCount: Int = 0,
        // Unique swing types in current copy sequence
        val copyUniqueTypes: Set<SwingType> = emptySet(),
        // Persistent copy cat multiplier bonus (max 0.5, survives miss, ratchets up)
        val copyCatMultiplierBonus: Float = 0f,
        // Highest copy cat tier achieved (to prevent re-claiming same tier)
        val copyCatTierAchieved: Int = 0,
        // Count of complete 9/9 sequences (for "🐱9/9+" display)
        val copyCat9Completions: Int = 0,
        // Grid indices with sequence numbers for leader 🐾 display: (gridIndex, seqNum)
        val copyCatLeaderCells: List<Pair<Int, Int>> = emptyList(),
        // Grid indices with sequence numbers for follower 🐱 display: (gridIndex, seqNum)
        val copyCatFollowerCells: List<Pair<Int, Int>> = emptyList(),
        // Next expected cell for follower (for X🐱 indicator), -1 = none
        val copyCatNextExpectedCell: Int = -1,
        // Whether local player is leader in the active sequence
        val isLocalPlayerLeader: Boolean = false,
        // True when showing ✖️ break animation on cells
        val isCopyCatBreakFlashing: Boolean = false,
        
        // === SPECIAL SQUARES ===
        // Map of grid index to special square type
        val specialSquares: Map<Int, SpecialSquareType> = emptyMap(),
        // Player has shield (forgives next early/late miss)
        val hasShield: Boolean = false,
        // Persistent golden square multiplier bonus (max 0.5)
        val goldenMultiplierBonus: Float = 0f,
        // Last special square hit this turn (for event logging), null = none
        val lastSpecialSquareHit: SpecialSquareType? = null,
        // Penalty from last banana hit (for display)
        val lastSpecialSquarePenalty: Int = 0,
        // True when landmine was hit without shield - signals GameEngine to end rally
        val landmineTriggered: Boolean = false,
        // True if any penalty square (banana/landmine) was hit during current tier
        // Resets on tier upgrade. Used to determine shield eligibility.
        val penaltyHitThisTier: Boolean = false,
        
        // === POWER OUTAGE TRACKING ===
        // Hit count per cell (0-8) during the game. Resets on miss or outage recovery.
        val cellHitCounts: List<Int> = List(9) { 0 },
        // True when local player is in power outage state
        val isPowerOutage: Boolean = false,
        // Set of unique cells hit consecutively during outage recovery attempt
        val outageRecoveryUniqueCells: Set<Int> = emptySet(),
        // Number of successful power outage recoveries (via 5 unique hits)
        val powerOutagesClearedCount: Int = 0,
        // Accumulated hit window bonus from clearing outages (capped by base difficulty)
        val powerOutageWindowBonus: Long = 0L,
        
        // === BONUS DISPLAY ===
        // Most recent bonus type earned (for dynamic STREAK label)
        val lastBonusType: BonusType? = null,
        // Timestamp when SPIN/COPYCAT/GOLD was last set (for 10s display priority)
        val lastSpecialBonusTimestamp: Long = 0L
    ) : ModeState() {
        
        /**
         * Returns the global highest tier achieved by either player.
         */
        val globalHighestTier: Int get() = maxOf(highestTier, opponentHighestTier)
        
        /**
         * Returns the minimum tier across all local cells.
         */
        val minTier: Int get() = cellTiers.minOrNull() ?: 0
        
        /**
         * Returns the total multiplier from all bonus sources.
         * Momentum multiplier is calculated separately based on rally length.
         */
        val totalBonusMultiplier: Float get() = spinMultiplierBonus + copyCatMultiplierBonus + goldenMultiplierBonus
        
        companion object {
            fun initial() = RallyState()
        }
    }
    
    /**
     * Solo Rally mode state.
     * Player plays against a simulated wall with pattern-based copy cat.
     */
    data class SoloRallyState(
        val score: Int = 0,
        val lives: Int = GameEngine.RALLY_STARTING_LIVES,
        val scorePoints: Int = 0, // Points from current hit (for popup animation)
        val linesJustCleared: Int = 0, // Lines cleared in current hit (for popup text)
        val grid: List<Boolean> = List(9) { false },
        val cellTiers: List<Int> = List(9) { 0 },
        val linesCompleted: List<Boolean> = List(8) { false },
        val highestTier: Int = 0,
        val totalLinesCleared: Int = 0,
        // Total lives earned from grid tier upgrades (for bonus chip display)
        val gridUpgradesEarned: Int = 0,
        val lastHitGridIndex: Int = -1,
        val isServeHit: Boolean = false,
        val soundEvent: RallySoundEvent? = null,
        
        // === SPIN BONUS TRACKING ===
        // Distinct spin types used this rally (resets when avatar stops spinning)
        val distinctSpinTypes: Set<SpinType> = emptySet(),
        // Current spin multiplier bonus (+0.1 per unique type, max 1.0)
        val spinMultiplierBonus: Float = 0f,
        // System time when avatar should stop spinning (0 = not spinning)
        val avatarSpinEndTime: Long = 0L,
        // If true, avatar spins indefinitely (10 types achieved while spinning)
        val isAvatarSpinningIndefinitely: Boolean = false,
        
        // === SOLO COPY CAT (CPU SEQUENCE CHALLENGE) ===
        // CPU-generated sequence of 9 grid indices for player to follow
        val copyCatSequence: List<Int> = emptyList(),
        // Current progress through sequence (0-8 = active, -1 = no active sequence)
        val copyCatProgress: Int = -1,
        // Hits before next sequence starts (10-20 range, counts down each hit)
        val copyCatCooldown: Int = 0,
        // Cells player has successfully copied: (gridIndex, seqNum)
        val copyCatCompletedCells: List<Pair<Int, Int>> = emptyList(),
        // Persistent copy cat multiplier bonus (max 0.5, survives miss, ratchets up)
        val copyCatMultiplierBonus: Float = 0f,
        // Highest copy cat tier achieved (to prevent re-claiming same tier)
        val copyCatTierAchieved: Int = 0,
        // Count of complete 9/9 sequences (for "🐱9/9+" display)
        val copyCat9Completions: Int = 0,
        // Cells to show ✖️ break animation on (preserved during flash)
        val copyCatBreakCells: List<Int> = emptyList(),
        // True when showing ✖️ break animation on cells
        val isCopyCatBreakFlashing: Boolean = false,
        
        // === SPECIAL SQUARES ===
        // Map of grid index to special square type
        val specialSquares: Map<Int, SpecialSquareType> = emptyMap(),
        // Player has shield (forgives next early/late miss)
        val hasShield: Boolean = false,
        // Persistent golden square multiplier bonus (max 0.5)
        val goldenMultiplierBonus: Float = 0f,
        // Last special square hit this turn (for event logging), null = none
        val lastSpecialSquareHit: SpecialSquareType? = null,
        // Penalty from last banana hit (for display)
        val lastSpecialSquarePenalty: Int = 0,
        // True when landmine was hit without shield - signals GameEngine to end rally
        val landmineTriggered: Boolean = false,
        // True if any penalty square (banana/landmine) was hit during current tier
        // Resets on tier upgrade. Used to determine shield eligibility.
        val penaltyHitThisTier: Boolean = false,
        
        // === POWER OUTAGE TRACKING ===
        // Hit count per cell (0-8) during the game. Resets on miss or outage recovery.
        val cellHitCounts: List<Int> = List(9) { 0 },
        // True when player is in power outage state
        val isPowerOutage: Boolean = false,
        // Set of unique cells hit consecutively during outage recovery attempt
        val outageRecoveryUniqueCells: Set<Int> = emptySet(),
        // Number of successful power outage recoveries (via 5 unique hits)
        val powerOutagesClearedCount: Int = 0,
        // Accumulated hit window bonus from clearing outages (capped by base difficulty)
        val powerOutageWindowBonus: Long = 0L,
        
        // === BONUS DISPLAY ===
        // Most recent bonus type earned (for dynamic STREAK label)
        val lastBonusType: BonusType? = null,
        // Timestamp when SPIN/COPYCAT/GOLD was last set (for 10s display priority)
        val lastSpecialBonusTimestamp: Long = 0L
    ) : ModeState() {
        
        /**
         * Returns the minimum tier across all cells.
         */
        val minTier: Int get() = cellTiers.minOrNull() ?: 0
        
        /**
         * Returns the total multiplier from all bonus sources.
         * Momentum multiplier is calculated separately based on rally length.
         */
        val totalBonusMultiplier: Float get() = spinMultiplierBonus + copyCatMultiplierBonus + goldenMultiplierBonus
        
        companion object {
            fun initial() = SoloRallyState()
        }
    }
}
