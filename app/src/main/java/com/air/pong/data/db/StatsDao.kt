package com.air.pong.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Query("SELECT * FROM point_history ORDER BY timestamp DESC")
    fun getAllPoints(): Flow<List<PointEntity>>

    @Insert
    suspend fun insertPoint(point: PointEntity)

    @Query("SELECT COUNT(*) FROM point_history WHERE didIWin = 1")
    fun getWinCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM point_history WHERE didIWin = 0")
    fun getLossCount(): Flow<Int>

    @Query("SELECT MAX(rallyLength) FROM point_history")
    fun getLongestRally(): Flow<Int?>

    @Query("SELECT SUM(myShotsCount) FROM point_history")
    fun getTotalHits(): Flow<Int?>

    // Game History
    @Insert
    suspend fun insertGame(game: GameResultEntity)

    @Query("SELECT COUNT(*) FROM game_history WHERE didIWin = 1")
    fun getGameWinCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM game_history WHERE didIWin = 0")
    fun getGameLossCount(): Flow<Int>

    @Query("DELETE FROM point_history")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM game_history")
    suspend fun deleteAllGames()
}
