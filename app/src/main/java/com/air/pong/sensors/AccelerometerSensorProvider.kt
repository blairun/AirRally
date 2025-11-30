package com.air.pong.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.air.pong.core.sensors.SensorProvider
import com.air.pong.core.sensors.SensorProvider.SwingEvent
import com.air.pong.core.game.GameEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

/**
 * Concrete implementation of SensorProvider using the device's accelerometer.
 * Detects swings based on linear acceleration magnitude.
 */
class AccelerometerSensorProvider(context: Context) : SensorProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    
    private val _swingEvents = MutableSharedFlow<SwingEvent>(extraBufferCapacity = 10)
    override val swingEvents = _swingEvents.asSharedFlow()
    
    private var lastSwingTime = 0L
    
    // Threshold in m/s^2. 1g = 9.8m/s^2. Adjustable via settings
    private var swingThreshold = GameEngine.DEFAULT_SWING_THRESHOLD 
    private val DEBOUNCE_MS = 500L

    // Store latest gyro values
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f

    // Store latest gravity values
    private var lastGravX = 0f
    private var lastGravY = 0f
    private var lastGravZ = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]
                    
                    // Calculate magnitude of acceleration vector
                    val magnitude = sqrt(x*x + y*y + z*z)
                    
                    if (magnitude > swingThreshold) {
                        val now = System.currentTimeMillis()
                        if (now - lastSwingTime > DEBOUNCE_MS) {
                            lastSwingTime = now
                            // Emit the swing event with current gyro and gravity data
                            _swingEvents.tryEmit(SwingEvent(now, magnitude, x, y, z, lastGyroX, lastGyroY, lastGyroZ, lastGravX, lastGravY, lastGravZ))
                        }
                    }
                } else if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    lastGyroX = it.values[0]
                    lastGyroY = it.values[1]
                    lastGyroZ = it.values[2]
                } else if (it.sensor.type == Sensor.TYPE_GRAVITY) {
                    lastGravX = it.values[0]
                    lastGravY = it.values[1]
                    lastGravZ = it.values[2]
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }
    } 

    override fun startListening() {
        linearAccel?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gravity?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stopListening() {
        sensorManager.unregisterListener(sensorListener)
    }

    fun setSwingThreshold(threshold: Float) {
        this.swingThreshold = threshold
    }
}
