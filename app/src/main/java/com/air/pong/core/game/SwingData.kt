package com.air.pong.core.game

data class SwingData(
    val force: Float,
    val accelX: Float, val accelY: Float, val accelZ: Float,
    val gyroX: Float, val gyroY: Float, val gyroZ: Float,
    val peakGyroX: Float = 0f, val peakGyroY: Float = 0f, val peakGyroZ: Float = 0f,
    val gravX: Float, val gravY: Float, val gravZ: Float,
    val spinType: SpinType = SpinType.NONE
)

