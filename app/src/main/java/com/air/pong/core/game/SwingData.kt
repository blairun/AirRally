package com.air.pong.core.game

data class SwingData(
    val force: Float,
    val accelX: Float, val accelY: Float, val accelZ: Float,
    val gyroX: Float, val gyroY: Float, val gyroZ: Float,
    val gravX: Float, val gravY: Float, val gravZ: Float
)
