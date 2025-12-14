package com.air.pong.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Interface for Bluetooth interactions.
 * Decouples the Game Engine from the Android Nearby Connections API.
 */
interface NetworkAdapter {
    val connectionState: Flow<ConnectionState>
    val errorMessage: Flow<String?>
    val connectedEndpointName: Flow<String?>
    
    suspend fun startAdvertising(name: String)
    suspend fun startDiscovery(name: String)
    suspend fun startPlayWithFriend(name: String) // Starts both advertising and discovery
    suspend fun stopAll()
    suspend fun sendMessage(message: ByteArray)
    
    suspend fun connectToEndpoint(endpointId: String)
    
    fun isHostRole(): Boolean // Returns true if this device is the host (accepted connection)

    
    val discoveredEndpoints: Flow<List<DiscoveredEndpoint>>
    
    data class DiscoveredEndpoint(val id: String, val name: String)

    enum class ConnectionState {
        DISCONNECTED,
        ADVERTISING,
        DISCOVERING,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}
