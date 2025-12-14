package com.air.pong.data

import com.air.pong.core.game.SwingType
import com.air.pong.data.db.PointEntity
import com.air.pong.data.db.StatsDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepository(private val statsDao: StatsDao) {
    private val gson = Gson()

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
}
