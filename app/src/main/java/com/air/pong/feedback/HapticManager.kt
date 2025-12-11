package com.air.pong.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Manages haptic feedback (vibration) for game events.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    enum class HapticEvent {
        MISS,      // Missed the ball (whiff)
        NET,       // Hit the net
        TABLE_MISS, // Missed the table
        WIN,       // Won the point
        LOSE       // Lost the point
    }

    fun play(event: HapticEvent) {
        if (!vibrator.hasVibrator()) return

        val effect = when (event) {
            HapticEvent.MISS -> createWaveform(longArrayOf(0, 100, 50, 100), -1) // Double bump
            HapticEvent.NET -> createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE) // Single medium buzz
            HapticEvent.TABLE_MISS -> createWaveform(longArrayOf(0, 50, 50, 50, 50, 50), -1) // Triple short
            HapticEvent.WIN -> createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE) // Short blip
            HapticEvent.LOSE -> createOneShot(300, 255) // Long heavy buzz
        }

        vibrator.vibrate(effect)
    }

    private fun createOneShot(milliseconds: Long, amplitude: Int): VibrationEffect {
        return VibrationEffect.createOneShot(milliseconds, amplitude)
    }

    private fun createWaveform(timings: LongArray, repeat: Int): VibrationEffect {
         return VibrationEffect.createWaveform(timings, repeat)
    }
}
