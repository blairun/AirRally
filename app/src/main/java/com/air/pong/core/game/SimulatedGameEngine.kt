package com.air.pong.core.game

import com.air.pong.core.network.GameMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Handles the logic for a simulated opponent (bot) in Debug Mode.
 * Observes the GameState and emits GameMessages to simulate network traffic.
 */
class SimulatedGameEngine(
    private val gameState: StateFlow<GameState>,
    private val scope: CoroutineScope
) {

    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<GameMessage> = _messages.asSharedFlow()

    private var autoPlayJob: Job? = null
    private var isAutoPlayEnabled = false

    fun setAutoPlay(enabled: Boolean) {
        isAutoPlayEnabled = enabled
        if (enabled) {
            startAutoPlayLoop()
        } else {
            stopAutoPlayLoop()
        }
    }

    private fun startAutoPlayLoop() {
        stopAutoPlayLoop()
        
        // Launch a separate collector for phase changes
        autoPlayJob = scope.launch {
            var lastPhase: GamePhase? = null
            gameState.collect { state ->
                if (!isAutoPlayEnabled) return@collect
                
                if (state.gamePhase != lastPhase) {
                    lastPhase = state.gamePhase
                    onGamePhaseChanged(state.gamePhase)
                }
            }
        }
    }
    
    fun onLocalSwing(result: HitResult) {
        if (!isAutoPlayEnabled) return
        
        if (result == HitResult.HIT) {
            scope.launch {
                // Wait for flight time + random delay to simulate reaction
                val arrivalTime = gameState.value.ballArrivalTimestamp
                val window = getHitWindow()

                // Safe window: +/- (window / 2) * 0.8
                val safeHalfWindow = (window / 2) * 0.8
                val randomOffset = (Random.nextDouble(-safeHalfWindow, safeHalfWindow)).toLong()

                val targetTime = arrivalTime + randomOffset
                val delay = targetTime - System.currentTimeMillis()

                if (delay > 0) {
                    delay(delay)
                }
                simulateOpponentSwing()
            }
        }
    }
    
    fun onGamePhaseChanged(phase: GamePhase) {
        if (!isAutoPlayEnabled) return
        
        if (phase == GamePhase.WAITING_FOR_SERVE) {
            scope.launch {
                delay(1000)
                if (!gameState.value.isMyTurn) {
                    simulateOpponentSwing()
                }
            }
        }
    }

    private fun stopAutoPlayLoop() {
        autoPlayJob?.cancel()
        autoPlayJob = null
    }

    fun simulateOpponentSwing() {
        scope.launch {
            // Simulate a swing happening "now" (minus latency)
            val now = System.currentTimeMillis()

            // Strategic Shot Selection
            // 60% Safe (Medium Flat), 20% Aggressive (Hard Smash), 20% Defensive (Lob)
            val roll = Random.nextFloat()
            val swingType = when {
                roll < 0.60f -> SwingType.MEDIUM_FLAT
                roll < 0.80f -> SwingType.HARD_SMASH
                else -> SwingType.SOFT_LOB // Or some other lob type
            }

            // Risk-Based Miss Logic
            // Use the actual risk percentages from the SwingType
            var (netRisk, outRisk) = swingType.getRiskPercentages(SwingSettings.createDefault())
            
            // Rally Mode: Reduce or eliminate the risk percentages (partners are placing shots more nicely)
            if (gameState.value.gameMode == GameMode.RALLY) {
                // netRisk /= 4
                // outRisk /= 4
                netRisk = 0
                outRisk = 0
            }
            
            val riskRoll = Random.nextInt(100)
            
            val missResult = when {
                riskRoll < netRisk -> HitResult.MISS_NET
                riskRoll < (netRisk + outRisk) -> HitResult.MISS_OUT
                else -> null // Hit!
            }

            if (missResult != null) {
                // It's a miss!
                // Calculate delay for the miss event
                val flightTime = gameState.value.flightTime
                val baseWindow = gameState.value.difficulty.toLong()
                val window = baseWindow 
                
                val delay = when (missResult) {
                    HitResult.MISS_NET -> 0L
                    HitResult.MISS_OUT -> flightTime
                    HitResult.MISS_TIMEOUT -> flightTime + window + GameEngine.BOUNCE_OFFSET_MS
                    else -> 0L
                }

                if (delay > 0) {
                    delay(delay)
                }

                _messages.emit(GameMessage.Result(missResult, null))
                return@launch
            }

            // Valid Hit Logic
            // Generate realistic force based on SwingType
            // Soft: 10-16, Medium: 17-20, Hard: 21-30
            val force = when {
                swingType.name.contains("SOFT") -> Random.nextDouble(10.0, 16.0).toFloat()
                swingType.name.contains("MEDIUM") -> Random.nextDouble(17.0, 20.0).toFloat()
                swingType.name.contains("HARD") -> Random.nextDouble(21.0, 30.0).toFloat()
                else -> 18.0f
            }

            _messages.emit(GameMessage.ActionSwing(now, force, swingType.ordinal, if (gameState.value.isMyTurn) 0 else 1))
        }
    }

    // We need a way to emit local swing events to the ViewModel
    private val _simulatedSwingEvents = MutableSharedFlow<com.air.pong.core.sensors.SensorProvider.SwingEvent>(extraBufferCapacity = 64)
    val simulatedSwingEvents: SharedFlow<com.air.pong.core.sensors.SensorProvider.SwingEvent> = _simulatedSwingEvents.asSharedFlow()

    fun simulateLocalSwing() {
         scope.launch {
            // Simulate a local swing
            val timestamp = System.currentTimeMillis()
            
            // Randomize Swing Type for variety
            val swingTypeRoll = Random.nextInt(3)
            val (targetForce, targetGravZ) = when (swingTypeRoll) {
                0 -> 18.0f to 0.0f // Flat (Medium)
                1 -> 15.0f to 7.0f // Lob (Soft/Medium)
                else -> 25.0f to -7.0f // Smash (Hard)
            }
            
            // Add some noise
            val force = targetForce + Random.nextDouble(-2.0, 2.0).toFloat()
            val gravZ = targetGravZ + Random.nextDouble(-1.0, 1.0).toFloat()
            
            val x = 0f
            val y = 0f
            val z = 0f
            val gx = 0f
            val gy = 0f
            val gz = 0f
            val gravX = 0f
            val gravY = 0f

            val event = com.air.pong.core.sensors.SensorProvider.SwingEvent(
                timestamp, force, x, y, z, gx, gy, gz, gravX, gravY, gravZ
            )
            
            _simulatedSwingEvents.emit(event)
         }
    }

    /**
     * Returns the hit window in milliseconds based on difficulty (which is now the window size in ms) AND incoming swing type.
     * Duplicated from GameEngine to avoid tight coupling or exposing GameEngine internals.
     */
    private fun getHitWindow(): Long {
        // Difficulty is now the base window size in ms (200-700)
        val baseWindow = gameState.value.difficulty.toLong()
        
        // Apply Window Shrink based on the LAST swing type (the incoming shot)
        // If lastSwingType is null (e.g. first serve), no shrink.
        val shrinkPercentage = gameState.value.lastSwingType?.getWindowShrinkPercentage(SwingSettings.createDefault()) ?: 0f
        val shrinkFactor = 1.0f - shrinkPercentage
        
        return (baseWindow * shrinkFactor).roundToLong()
    }
}
