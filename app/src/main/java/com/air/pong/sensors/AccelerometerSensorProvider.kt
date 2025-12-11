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

    // Peak Detection State
    private val PEAK_WINDOW_MS = 70L
    private var isCollectingPeak = false
    private var peakStartTime = 0L
    private var peakMagnitude = 0f
    private var pendingPeakEvent: SwingEvent? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]
                    
                    // Calculate magnitude of acceleration vector
                    val magnitude = sqrt(x*x + y*y + z*z)
                    val now = System.currentTimeMillis()

                    if (isCollectingPeak) {
                        // We are in the peak collection window
                        if (now - peakStartTime < PEAK_WINDOW_MS) {
                            // Still collecting, check for new peak
                            if (magnitude > peakMagnitude) {
                                peakMagnitude = magnitude
                                // Update the pending event with the new peak data
                                // IMPORTANT: Use peakStartTime as the timestamp to compensate for the detection delay.
                                // This ensures the game calculates flight time from the START of the swing,
                                // effectively "pulling back" the bounce sound by ~70ms.
                                pendingPeakEvent = SwingEvent(peakStartTime, magnitude, x, y, z, lastGyroX, lastGyroY, lastGyroZ, lastGravX, lastGravY, lastGravZ)
                                
                                // EARLY EXIT OPTIMIZATION:
                                // If the force is already "Hard" (> 44.0), we don't need to wait for the window to finish.
                                // We can emit immediately to reduce audio latency.
                                if (peakMagnitude > 44.0f) {
                                    // Use CURRENT gravity values to capture the wrist snap
                                    val eventToEmit = pendingPeakEvent!!.copy(
                                        gravX = lastGravX, 
                                        gravY = lastGravY, 
                                        gravZ = lastGravZ
                                    )
                                    _swingEvents.tryEmit(eventToEmit)
                                    isCollectingPeak = false
                                    lastSwingTime = now
                                    pendingPeakEvent = null
                                }
                            }
                        } else {
                            // Window expired, emit the peak we found
                            pendingPeakEvent?.let { peakEvent ->
                                // Use CURRENT gravity values to capture the wrist snap
                                val eventToEmit = peakEvent.copy(
                                    gravX = lastGravX, 
                                    gravY = lastGravY, 
                                    gravZ = lastGravZ
                                )
                                _swingEvents.tryEmit(eventToEmit)
                            }
                            
                            // Reset state and start debounce
                            isCollectingPeak = false
                            lastSwingTime = now
                            pendingPeakEvent = null
                            
                            // NOTE: We do not process the current sample as a new swing because we just finished one.
                            // The debounce (lastSwingTime = now) handles this.
                        }
                    } else {
                        // Not collecting, check for trigger
                        if (magnitude > swingThreshold) {
                            if (now - lastSwingTime > DEBOUNCE_MS) {
                                // Start collecting peak
                                isCollectingPeak = true
                                peakStartTime = now
                                peakMagnitude = magnitude
                                // Use peakStartTime here as well. Gravity will be updated on emit.
                                pendingPeakEvent = SwingEvent(peakStartTime, magnitude, x, y, z, lastGyroX, lastGyroY, lastGyroZ, lastGravX, lastGravY, lastGravZ)
                            }
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
