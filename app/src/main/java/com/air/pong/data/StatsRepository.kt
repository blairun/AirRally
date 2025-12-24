package com.air.pong.data

import android.content.SharedPreferences
import com.air.pong.core.game.SwingType
import com.air.pong.data.db.PointEntity
import com.air.pong.data.db.StatsDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepository(
    private val statsDao: StatsDao,
    private val prefs: SharedPreferences? = null
) {
    private val gson = Gson()
    
    companion object {
        private const val PREF_CELL_USAGE_PREFIX = "cell_usage_"
    }

    val allPoints: Flow<List<PointEntity>> = statsDao.getAllPoints()
    val winCount: Flow<Int> = statsDao.getGameWinCount()
    val lossCount: Flow<Int> = statsDao.getGameLossCount()
    val longestRally: Flow<Int> = statsDao.getLongestRally().map { it ?: 0 }
    val totalHits: Flow<Int> = statsDao.getTotalHits().map { it ?: 0 }

    suspend fun recordPoint(
        timestamp: Long,
        opponentName: String,
        didIWin: Boolean,
        rallyLength: Int,
        myShots: List<SwingType>,
        endReason: String
    ) {
        val shotsJson = gson.toJson(myShots)
        val entity = PointEntity(
            timestamp = timestamp,
            opponentName = opponentName,
            didIWin = didIWin,
            rallyLength = rallyLength,
            myShotsCount = myShots.size,
            myShotsJson = shotsJson,
            endReason = endReason
        )
        statsDao.insertPoint(entity)
    }

    suspend fun recordGame(
        timestamp: Long,
        opponentName: String,
        myScore: Int,
        opponentScore: Int,
        didIWin: Boolean
    ) {
        val entity = com.air.pong.data.db.GameResultEntity(
            timestamp = timestamp,
            opponentName = opponentName,
            myScore = myScore,
            opponentScore = opponentScore,
            didIWin = didIWin
        )
        statsDao.insertGame(entity)
    }
    
    fun parseShots(json: String): List<SwingType> {
        val type = object : TypeToken<List<SwingType>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend fun resetStats() {
        statsDao.deleteAllPoints()
        statsDao.deleteAllGames()
    }
    
    // === CELL USAGE TRACKING (for special square placement weighting) ===
    
    /**
     * Increments the usage count for a grid cell (0-8).
     * Used to weight special square placement toward least-used cells.
     */
    fun incrementCellUsage(cellIndex: Int) {
        if (cellIndex !in 0..8) return
        prefs?.let { p ->
            val key = "$PREF_CELL_USAGE_PREFIX$cellIndex"
            val current = p.getInt(key, 0)
            p.edit().putInt(key, current + 1).apply()
        }
    }
    
    /**
     * Returns the usage counts for all 9 grid cells.
     * @return IntArray of size 9 with usage counts for cells 0-8
     */
    fun getCellUsageCounts(): IntArray {
        val counts = IntArray(9)
        prefs?.let { p ->
            for (i in 0..8) {
                counts[i] = p.getInt("$PREF_CELL_USAGE_PREFIX$i", 0)
            }
        }
        return counts
    }
    
    /**
     * Returns the indices of the least-used cells, sorted from least to most used.
     * Useful for weighted random selection of special square placement.
     */
    fun getLeastUsedCellIndices(): List<Int> {
        val counts = getCellUsageCounts()
        return counts.indices.sortedBy { counts[it] }
    }
}

