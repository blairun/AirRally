package com.air.pong.core.game

sealed class GameEvent {
    data class YouServed(val swingType: SwingType) : GameEvent()
    data class YouHit(val swingType: SwingType) : GameEvent()
    data class OpponentHit(val swingType: SwingType) : GameEvent()
    object BallBounced : GameEvent()
    
    // Faults/Misses
    object FaultNet : GameEvent()
    object FaultOut : GameEvent()
    data class HitNet(val swingType: SwingType) : GameEvent()
    data class HitOut(val swingType: SwingType) : GameEvent()
    object WhiffEarly : GameEvent()
    object MissLate : GameEvent()
    object MissNoSwing : GameEvent()
    
    // Opponent Misses
    object OpponentNet : GameEvent()
    object OpponentOut : GameEvent()
    object OpponentWhiff : GameEvent()
    object OpponentMissNoSwing : GameEvent()
    object OpponentMiss : GameEvent() // Generic
    
    data class PointScored(val isYou: Boolean) : GameEvent()
    
    // Fallback for any other strings
    data class RawMessage(val message: String) : GameEvent()
}
