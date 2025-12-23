package com.air.pong.ui

import android.content.Context

/**
 * Utility object for managing avatars with unlock requirements.
 * Avatars are categorized based on their file naming convention:
 * - avatar_base_XX.png: Available from start
 * - avatar_rallyscore_XX.png: Unlock via rally high score
 * - avatar_rallylength_XX.png: Unlock via rally length
 * - avatar_classicwins_XX.png: Unlock via classic mode wins
 */
object AvatarUtils {
    
    enum class AvatarCategory {
        BASE,          // Available from start
        // RALLY_SCORE,   // Unlock via rally high score (RESERVED FOR FUTURE USE)
        RALLY_LINES,   // Unlock via co-op rally lines cleared (cumulative)
        SOLO_LINES,    // Unlock via solo rally lines cleared (cumulative)
        RALLY_LENGTH,  // Unlock via rally length
        CLASSIC_WINS   // Unlock via classic mode wins
    }
    
    data class AvatarInfo(
        val resId: Int,
        val index: Int,          // Global index for compatibility
        val category: AvatarCategory,
        val unlockThreshold: Int = 0
    )
    
    // Triangular: 0, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66, 78, 91, 105, 120, 136, 153, 171, 190, 210, 231, 
    //253, 276, 300, 325, 351, 378, 406, 435, 465, 496, 528, 561, 595, 630, 666, 703, 741, 780, 820, 861, 903, 946, 990
    
    // Unlock thresholds for each category
    // RALLY_LINES: Cumulative lines cleared in co-op rally mode (higher thresholds since cumulative)
    val RALLY_LINES_THRESHOLDS = listOf(21, 55, 105, 171, 253, 351, 465, 595)
    
    // SOLO_LINES: Cumulative lines cleared in solo rally mode
    val SOLO_LINES_THRESHOLDS = listOf(21, 105, 253, 465, 741, 1081, 1485, 1953)
    
    // RALLY_LENGTH: In Co-op Rally mode
    val RALLY_LENGTH_THRESHOLDS = listOf(21, 36, 55, 78, 105, 136, 171, 210)
    
    // CLASSIC_WINS: In Classic mode
    val CLASSIC_WINS_THRESHOLDS = listOf(3, 10, 21, 36)
    
    // for testing
    // val RALLY_LINES_THRESHOLDS = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    // val SOLO_LINES_THRESHOLDS = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    // val RALLY_LENGTH_THRESHOLDS = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    // val CLASSIC_WINS_THRESHOLDS = listOf(1, 2, 3, 4)
    
    private var _avatars: List<AvatarInfo> = emptyList()
    val avatars: List<AvatarInfo> get() = _avatars
    
    // Legacy compatibility - returns all resource IDs in order
    val avatarResources: List<Int> get() = _avatars.map { it.resId }
    
    // Category-based getters
    val baseAvatars: List<AvatarInfo> get() = _avatars.filter { it.category == AvatarCategory.BASE }
    val rallyScoreAvatars: List<AvatarInfo> get() = emptyList() // Reserved for future use
    val rallyLinesAvatars: List<AvatarInfo> get() = _avatars.filter { it.category == AvatarCategory.RALLY_LINES }
    val soloLinesAvatars: List<AvatarInfo> get() = _avatars.filter { it.category == AvatarCategory.SOLO_LINES }
    val rallyLengthAvatars: List<AvatarInfo> get() = _avatars.filter { it.category == AvatarCategory.RALLY_LENGTH }
    val classicWinsAvatars: List<AvatarInfo> get() = _avatars.filter { it.category == AvatarCategory.CLASSIC_WINS }
    
    fun initialize(context: Context) {
        val avatarList = mutableListOf<AvatarInfo>()
        var globalIndex = 0
        
        // Discover base avatars (avatar_base_XX)
        var i = 1
        while (true) {
            val paddedNum = i.toString().padStart(2, '0')
            val resId = context.resources.getIdentifier("avatar_base_$paddedNum", "drawable", context.packageName)
            if (resId == 0) break
            avatarList.add(AvatarInfo(resId, globalIndex++, AvatarCategory.BASE, 0))
            i++
        }
        
        // Discover rally lines avatars (avatar_rallylines_XX) - co-op lines cleared
        i = 40 // Starting number from file naming
        var thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("avatar_rallylines_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = RALLY_LINES_THRESHOLDS.getOrElse(thresholdIdx) { RALLY_LINES_THRESHOLDS.last() }
            avatarList.add(AvatarInfo(resId, globalIndex++, AvatarCategory.RALLY_LINES, threshold))
            thresholdIdx++
            i++
        }
        
        // Discover solo lines avatars (avatar_sololines_XX) - solo mode lines cleared
        i = 50 // Starting number from file naming
        thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("avatar_sololines_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = SOLO_LINES_THRESHOLDS.getOrElse(thresholdIdx) { SOLO_LINES_THRESHOLDS.last() }
            avatarList.add(AvatarInfo(resId, globalIndex++, AvatarCategory.SOLO_LINES, threshold))
            thresholdIdx++
            i++
        }
        
        // Discover rally length avatars (avatar_rallylength_XX)
        i = 30 // Starting number from file naming
        thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("avatar_rallylength_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = RALLY_LENGTH_THRESHOLDS.getOrElse(thresholdIdx) { RALLY_LENGTH_THRESHOLDS.last() }
            avatarList.add(AvatarInfo(resId, globalIndex++, AvatarCategory.RALLY_LENGTH, threshold))
            thresholdIdx++
            i++
        }
        
        // Discover classic wins avatars (avatar_classicwins_XX)
        i = 20 // Starting number from file naming
        thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("avatar_classicwins_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = CLASSIC_WINS_THRESHOLDS.getOrElse(thresholdIdx) { CLASSIC_WINS_THRESHOLDS.last() }
            avatarList.add(AvatarInfo(resId, globalIndex++, AvatarCategory.CLASSIC_WINS, threshold))
            thresholdIdx++
            i++
        }
        
        _avatars = avatarList
    }
    
    /**
     * Check if an avatar is unlocked based on player stats.
     */
    fun isUnlocked(avatar: AvatarInfo, rallyLinesCleared: Int, soloLinesCleared: Int, longestRally: Int, classicWins: Int): Boolean {
        return when (avatar.category) {
            AvatarCategory.BASE -> true
            AvatarCategory.RALLY_LINES -> rallyLinesCleared >= avatar.unlockThreshold
            AvatarCategory.SOLO_LINES -> soloLinesCleared >= avatar.unlockThreshold
            AvatarCategory.RALLY_LENGTH -> longestRally >= avatar.unlockThreshold
            AvatarCategory.CLASSIC_WINS -> classicWins >= avatar.unlockThreshold
        }
    }
    
    /**
     * Get the next unlock hint for a category.
     */
    fun getNextUnlockHint(avatar: AvatarInfo): String {
        return when (avatar.category) {
            AvatarCategory.BASE -> ""
            AvatarCategory.RALLY_LINES -> "${avatar.unlockThreshold}+ lines"
            AvatarCategory.SOLO_LINES -> "${avatar.unlockThreshold}+ lines"
            AvatarCategory.RALLY_LENGTH -> "${avatar.unlockThreshold}+ hits"
            AvatarCategory.CLASSIC_WINS -> "${avatar.unlockThreshold}+ games"
        }
    }
    
    /**
     * Find avatar by global index.
     */
    fun getAvatarByIndex(index: Int): AvatarInfo? = _avatars.getOrNull(index)
    
    /**
     * Find avatar by resource ID.
     */
    fun getAvatarByResId(resId: Int): AvatarInfo? = _avatars.find { it.resId == resId }
}
