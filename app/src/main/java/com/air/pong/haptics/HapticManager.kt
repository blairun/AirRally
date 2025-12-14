package com.air.pong.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.air.pong.core.game.SwingType

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

    /**
     * Play haptic feedback for a successful hit.
     * Vibration intensity varies based on shot force:
     * - Soft: 30ms at 50% amplitude (light tap)
     * - Medium: 50ms at 75% amplitude (normal feedback)
     * - Hard: 80ms at 100% amplitude (strong punch)
     */
    fun playHit(swingType: SwingType? = null) {
        if (!vibrator.hasVibrator()) return
        
        val (duration, amplitude) = when {
            swingType?.name?.startsWith("SOFT") == true -> 30L to 128
            swingType?.name?.startsWith("HARD") == true -> 80L to 255
            else -> 50L to 192 // MEDIUM or default
        }
        
        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }
    
    fun playMiss() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    
    fun playWin() {
         if (vibrator.hasVibrator()) {
             val timings = longArrayOf(0, 100, 50, 100, 50, 200)
             val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
             vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
         }
    }
}

