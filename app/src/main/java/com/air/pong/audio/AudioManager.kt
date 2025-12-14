package com.air.pong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.air.pong.R

/**
 * Manages audio playback for game events.
 * Uses SoundPool for low-latency playback of short sound effects.
 */
class AudioManager(context: Context) {

    private val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<SoundEvent, Int>()
    private var isLoaded = false
    
    private val winnerSoundResourceIds = mutableListOf<Int>()
    private var currentMediaPlayer: android.media.MediaPlayer? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isLoaded = true
            }
        }

        // Load sounds
        soundMap[SoundEvent.SERVE] = soundPool.load(context, R.raw.serve, 1)
        soundMap[SoundEvent.BOUNCE] = soundPool.load(context, R.raw.bounce, 1)
        soundMap[SoundEvent.HIT_SOFT] = soundPool.load(context, R.raw.hit_soft, 1)
        soundMap[SoundEvent.HIT_MEDIUM] = soundPool.load(context, R.raw.hit_medium, 1)
        soundMap[SoundEvent.HIT_HARD] = soundPool.load(context, R.raw.hit_hard, 1)
        soundMap[SoundEvent.BALL_POP_UP] = soundPool.load(context, R.raw.ball_pop_up, 1)
        soundMap[SoundEvent.BALL_WHIZ] = soundPool.load(context, R.raw.ball_whiz, 1)
        soundMap[SoundEvent.MISS_WHIFF] = soundPool.load(context, R.raw.miss_whiff, 1)
        soundMap[SoundEvent.HIT_NET] = soundPool.load(context, R.raw.hit_net, 1)
        soundMap[SoundEvent.MISS_TABLE] = soundPool.load(context, R.raw.miss_table, 1)
        soundMap[SoundEvent.MISS_NO_SWING] = soundPool.load(context, R.raw.miss_no_swing, 1)
        soundMap[SoundEvent.WIN_POINT] = soundPool.load(context, R.raw.win_point, 1)
        soundMap[SoundEvent.LOSE_POINT] = soundPool.load(context, R.raw.lose_point, 1)
        soundMap[SoundEvent.LINE_COMPLETE] = soundPool.load(context, R.raw.line_complete, 1)
        soundMap[SoundEvent.GRID_COMPLETE] = soundPool.load(context, R.raw.grid_complete, 1)
        soundMap[SoundEvent.VS] = soundPool.load(context, R.raw.vs, 1)
        soundMap[SoundEvent.WALL_BOUNCE] = soundPool.load(context, R.raw.bounce_wall, 1)
        
        loadWinnerSounds(context)
    }
    
    
    private fun loadWinnerSounds(context: Context) {
        val packageName = context.packageName
        android.util.Log.d("AudioManager", "Loading winner sounds from package: $packageName")
        
        // Try to load winner_1 up to winner_20
        for (i in 1..20) {
            val resourceName = "winner_$i"
            val resourceId = context.resources.getIdentifier(resourceName, "raw", packageName)
            if (resourceId != 0) {
                android.util.Log.d("AudioManager", "Found winner sound: $resourceName (ID: $resourceId)")
                winnerSoundResourceIds.add(resourceId)
            }
        }
        android.util.Log.d("AudioManager", "Found ${winnerSoundResourceIds.size} winner sounds")
    }
    
    fun playRandomWinSound(context: Context) {
        android.util.Log.d("AudioManager", "Attempting to play random winner sound. Count: ${winnerSoundResourceIds.size}")
        if (winnerSoundResourceIds.isNotEmpty()) {
            val randomResourceId = winnerSoundResourceIds.random()
            try {
                // Release previous player if exists
                currentMediaPlayer?.release()
                
                currentMediaPlayer = android.media.MediaPlayer.create(context, randomResourceId)
                currentMediaPlayer?.setOnCompletionListener { mp -> 
                    mp.release()
                    if (currentMediaPlayer == mp) {
                        currentMediaPlayer = null
                    }
                }
                currentMediaPlayer?.start()
                android.util.Log.d("AudioManager", "Started playing winner sound ID: $randomResourceId")
            } catch (e: Exception) {
                android.util.Log.e("AudioManager", "Error playing winner sound", e)
            }
        } else {
            android.util.Log.w("AudioManager", "No winner sounds available to play")
        }
    }
    
    enum class SoundEvent {
        HIT_SOFT,
        HIT_MEDIUM,
        HIT_HARD,
        SERVE,
        BOUNCE,
        BALL_POP_UP,
        BALL_WHIZ,
        MISS_WHIFF,
        MISS_NO_SWING,
        HIT_NET,
        MISS_TABLE,
        WIN_POINT,
        LOSE_POINT,
        LINE_COMPLETE,
        GRID_COMPLETE,
        VS,
        WALL_BOUNCE,
        GAME_START,
        GAME_OVER
    }

    fun play(event: SoundEvent, useDebugTones: Boolean = false) {
        if (useDebugTones) {
            playDebugTone(event)
        } else {
            playSoundFile(event)
        }
    }

    private fun playSoundFile(event: SoundEvent) {
        if (!isLoaded) return
        val soundId = soundMap[event] ?: return
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }
    
    /**
     * Plays the wall bounce sound at 50% volume for Solo Rally mode.
     */
    fun playWallBounce() {
        if (!isLoaded) return
        val soundId = soundMap[SoundEvent.WALL_BOUNCE] ?: return
        if (soundId != 0) {
            soundPool.play(soundId, SOLO_WALL_BOUNCE_VOLUME, SOLO_WALL_BOUNCE_VOLUME, 1, 0, 1f)
        }
    }
    
    companion object {
        private const val SOLO_WALL_BOUNCE_VOLUME = 0.5f
    }

    private fun playDebugTone(event: SoundEvent) {
        try {
            when (event) {
                SoundEvent.HIT_SOFT, SoundEvent.HIT_MEDIUM, SoundEvent.HIT_HARD -> 
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_DTMF_1, 100)
                SoundEvent.SERVE -> toneGenerator.startTone(android.media.ToneGenerator.TONE_DTMF_0, 150)
                SoundEvent.BOUNCE -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 50)
                SoundEvent.BALL_POP_UP -> toneGenerator.startTone(android.media.ToneGenerator.TONE_DTMF_2, 100)
                SoundEvent.BALL_WHIZ -> toneGenerator.startTone(android.media.ToneGenerator.TONE_DTMF_3, 100)
                SoundEvent.MISS_WHIFF, SoundEvent.MISS_TABLE, SoundEvent.HIT_NET, SoundEvent.MISS_NO_SWING -> 
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_SUP_ERROR, 300)
                SoundEvent.WIN_POINT -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                SoundEvent.LOSE_POINT -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500)
                SoundEvent.LINE_COMPLETE -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
                SoundEvent.GRID_COMPLETE -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE, 500)
                SoundEvent.VS -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                SoundEvent.WALL_BOUNCE -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 50) // Same as bounce
                SoundEvent.GAME_START -> toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 500)
                SoundEvent.GAME_OVER -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 1000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun release() {
        toneGenerator.release()
        soundPool.release()
    }
}
