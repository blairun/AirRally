package com.air.pong.ui

import android.content.Context

/**
 * Utility object for managing background rings with unlock requirements.
 * Rings are donut-shaped graphics shown behind avatars.
 * 
 * Rings are categorized based on their file naming convention:
 * - ring_rallyscore_XX.png: Unlock via co-op rally high score
 * - ring_soloscore_XX.png: Unlock via solo rally high score
 */
object RingUtils {
    
    /**
     * The ratio of ring outer diameter to avatar diameter.
     * Ring size = Avatar size × RING_SIZE_RATIO
     * Using 1.5 makes the ring more visible around the avatar.
     */
    const val RING_SIZE_RATIO = 1.5f
    
    /**
     * Special index indicating no ring is selected.
     */
    const val RING_INDEX_NONE = -1
    
    enum class RingCategory {
        RALLY_SCORE,  // Unlock via co-op rally high score
        SOLO_SCORE    // Unlock via solo rally high score
    }
    
    data class RingInfo(
        val resId: Int,
        val index: Int,           // Global index for compatibility
        val category: RingCategory,
        val unlockThreshold: Int = 0
    )
    
    // Triangular: 0, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66, 78, 91, 105, 120, 136, 153, 171, 190, 210, 231, 253, 276, 300, 325, 351, 378, 406, 435, 465, 496, 528, 561, 595, 630, 666, 703, 741, 780, 820, 861, 903, 946, 990
    val RALLY_SCORE_THRESHOLDS = listOf(1000, 3000, 6000, 10000, 15000, 21000, 28000, 36000)
    val SOLO_SCORE_THRESHOLDS = listOf(1000, 6000, 15000, 28000, 45000, 66000, 91000, 120000)
    
    // For testing - uncomment these and comment the above to test with low thresholds
    // val RALLY_SCORE_THRESHOLDS = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    // val SOLO_SCORE_THRESHOLDS = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    
    private var _rings: List<RingInfo> = emptyList()
    val rings: List<RingInfo> get() = _rings
    
    // Legacy compatibility - returns all resource IDs in order
    val ringResources: List<Int> get() = _rings.map { it.resId }
    
    // Category-based getters
    val rallyScoreRings: List<RingInfo> get() = _rings.filter { it.category == RingCategory.RALLY_SCORE }
    val soloScoreRings: List<RingInfo> get() = _rings.filter { it.category == RingCategory.SOLO_SCORE }
    
    fun initialize(context: Context) {
        val ringList = mutableListOf<RingInfo>()
        var globalIndex = 0
        
        // Discover rally score rings (ring_rallyscore_XX)
        var i = 10 // Starting number from file naming (ring_rallyscore_10.png, etc.)
        var thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("ring_rallyscore_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = RALLY_SCORE_THRESHOLDS.getOrElse(thresholdIdx) { RALLY_SCORE_THRESHOLDS.last() }
            ringList.add(RingInfo(resId, globalIndex++, RingCategory.RALLY_SCORE, threshold))
            thresholdIdx++
            i++
        }
        
        // Discover solo score rings (ring_soloscore_XX)
        i = 20 // Starting number from file naming (ring_soloscore_20.png, etc.)
        thresholdIdx = 0
        while (true) {
            val resId = context.resources.getIdentifier("ring_soloscore_$i", "drawable", context.packageName)
            if (resId == 0) break
            val threshold = SOLO_SCORE_THRESHOLDS.getOrElse(thresholdIdx) { SOLO_SCORE_THRESHOLDS.last() }
            ringList.add(RingInfo(resId, globalIndex++, RingCategory.SOLO_SCORE, threshold))
            thresholdIdx++
            i++
        }
        
        _rings = ringList
    }
    
    /**
     * Check if a ring is unlocked based on player stats.
     */
    fun isUnlocked(ring: RingInfo, rallyHighScore: Int, soloHighScore: Int): Boolean {
        return when (ring.category) {
            RingCategory.RALLY_SCORE -> rallyHighScore >= ring.unlockThreshold
            RingCategory.SOLO_SCORE -> soloHighScore >= ring.unlockThreshold
        }
    }
    
    /**
     * Get the next unlock hint for a ring.
     */
    fun getNextUnlockHint(ring: RingInfo): String {
        return when (ring.category) {
            RingCategory.RALLY_SCORE -> "${ring.unlockThreshold}+ pts"
            RingCategory.SOLO_SCORE -> "${ring.unlockThreshold}+ pts"
        }
    }
    
    /**
     * Find ring by global index.
     */
    fun getRingByIndex(index: Int): RingInfo? = _rings.getOrNull(index)
    
    /**
     * Find ring by resource ID.
     */
    fun getRingByResId(resId: Int): RingInfo? = _rings.find { it.resId == resId }
}
