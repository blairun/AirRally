package com.air.pong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_history")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val opponentName: String,
    val myScore: Int,
    val opponentScore: Int,
    val didIWin: Boolean
)
