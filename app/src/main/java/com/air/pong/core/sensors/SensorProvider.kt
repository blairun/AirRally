package com.air.pong.core.sensors

import com.air.pong.core.game.SpinType
import kotlinx.coroutines.flow.Flow

/**
 * Interface for sensor data.
 * Allows testing game logic with fake sensor inputs.
 */
interface SensorProvider {
    val swingEvents: Flow<SwingEvent>
    
    fun startListening()
    fun stopListening()
    
    data class SwingEvent(
        val timestamp: Long,
        val force: Float,
        val x: Float,
        val y: Float,
        val z: Float,
        val gx: Float = 0f,
        val gy: Float = 0f,
        val gz: Float = 0f,
        val peakGx: Float = 0f,  // Peak gyro during swing (for spin detection)
        val peakGy: Float = 0f,
        val peakGz: Float = 0f,
        val gravX: Float = 0f,
        val gravY: Float = 0f,
        val gravZ: Float = 0f
    ) {
        /**
         * Computed spin type based on peak gyroscope readings.
         * Classifies the spin applied during the swing motion.
         */
        val spinType: SpinType
            get() = SpinType.fromGyro(peakGy, peakGz)
    }
}

