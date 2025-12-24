package com.air.pong.core.game

/**
 * Types of special squares that can appear on the grid.
 * Special squares add risk/reward dynamics to Rally mode.
 */
enum class SpecialSquareType {
    /**
     * Banana Peel / Oil Spill
     * - Hitting subtracts 10 × current_grid_tier from score
     * - Clearing the grid with this square present awards a Shield
     * - Visual: Light gray fill, pulsing gray border, banana emoji
     */
    BANANA,
    
    /**
     * Land Mine / Lava
     * - Hitting ends the rally immediately (lose 1 life)
     * - Visual: Pinkish red fill, pulsing red border, skull emoji
     */
    LANDMINE,
    
    /**
     * Golden Square
     * - Hitting awards 3× face value points
     * - Clearing lines with this square awards persistent multiplier:
     *   - 1 line: +0.1
     *   - 2 lines: +0.2 additional (+0.3 total)
     *   - 3+ lines/X: +0.2 additional (+0.5 max)
     * - Reverts to normal square after clearing
     * - Visual: Gold fill, pulsing gold border, coin emoji
     */
    GOLDEN;
    
    companion object {
        /**
         * Categorical probabilities for special square spawning.
         * Uses a single roll to select category, ensuring exactly 1/3 each.
         */
        const val GOLDEN_CATEGORY_CHANCE = 0.20f    // 20% chance of golden square
        const val PENALTY_CATEGORY_CHANCE = 0.40f   // 40% chance of penalty square(s)
        // Remaining 40% = no special squares (normal grid)
        
        /**
         * Sub-probabilities within penalty category.
         */
        const val PENALTY_BANANA_ONLY_CHANCE = 0.50f    // 50% of penalties = banana only
        const val PENALTY_LANDMINE_ONLY_CHANCE = 0.25f  // 25% of penalties = landmine only
        // Remaining 25% = both banana and landmine
        
        /**
         * Probability that golden square spawns in center cell (index 4).
         */
        const val GOLDEN_CENTER_CHANCE = 0.33f
        
        /**
         * Maximum multiplier bonus from golden squares.
         */
        const val MAX_GOLDEN_MULTIPLIER_BONUS = 0.5f
        
        /**
         * Points multiplier when hitting a golden square (before line bonuses).
         */
        const val GOLDEN_POINTS_MULTIPLIER = 3
        
        /**
         * Penalty multiplier per grid tier for banana squares.
         */
        const val BANANA_PENALTY_PER_TIER = 10
    }
}
