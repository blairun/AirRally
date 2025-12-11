package com.air.pong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "point_history")
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val opponentName: String,
    val didIWin: Boolean,
    val rallyLength: Int,
    val myShotsCount: Int,
    val myShotsJson: String, // JSON list of SwingType
    val endReason: String = "" // "WIN", "NET", "OUT", "WHIFF", "TIMEOUT", "OPPONENT_MISS"
)
