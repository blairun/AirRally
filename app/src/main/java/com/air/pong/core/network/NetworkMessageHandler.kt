package com.air.pong.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull

/**
 * Handles incoming network messages and converts them to GameMessage objects.
 * Provides a typed interface for the game logic layer.
 */
class NetworkMessageHandler(
    private val rawMessageFlow: Flow<ByteArray>
) {
    
    /**
     * Decodes raw byte messages into typed GameMessage objects.
     * Filters out invalid messages and logs errors.
     */
    fun observeGameMessages(): Flow<GameMessage> {
        return rawMessageFlow.map { bytes ->
            try {
                MessageCodec.decode(bytes)
            } catch (e: Exception) {
                // Log error and skip invalid message
                // In production, we'd use proper logging
                println("Failed to decode message: ${e.message}")
                null
            }
        }.filterNotNull()
    }
    
    /**
     * Filter messages by type for specific handling.
     */
    inline fun <reified T : GameMessage> observeMessagesOfType(): Flow<T> {
        return observeGameMessages()
            .map { it as? T }
            .map { it ?: return@map null }
            .map { it!! }
    }
}
