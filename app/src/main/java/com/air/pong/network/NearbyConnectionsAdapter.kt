package com.air.pong.network

import android.content.Context
import com.air.pong.core.network.NetworkAdapter
import com.air.pong.utils.PermissionsManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bluetooth implementation using Google Nearby Connections API.
 * Handles advertising (hosting), discovery (joining), and message transmission.
 */
class NearbyConnectionsAdapter(
    private val context: Context,
    private val serviceId: String = "com.air.pong"
) : NetworkAdapter {
    
    private val connectionsClient = Nearby.getConnectionsClient(context)
    
    private val _connectionState = MutableStateFlow(NetworkAdapter.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<NetworkAdapter.ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _connectedEndpointName = MutableStateFlow<String?>(null)
    override val connectedEndpointName: StateFlow<String?> = _connectedEndpointName.asStateFlow()
    
    private val _discoveredEndpoints = MutableStateFlow<List<NetworkAdapter.DiscoveredEndpoint>>(emptyList())
    override val discoveredEndpoints: StateFlow<List<NetworkAdapter.DiscoveredEndpoint>> = _discoveredEndpoints.asStateFlow()
    
    private var timeoutJob: Job? = null
    private val scope = MainScope()
    
    private val _receivedMessages = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64 // Buffer up to 64 messages to handle bursts
    )
    
    private var connectedEndpointId: String? = null
    private var localPlayerName: String = "Player"
    
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    _receivedMessages.tryEmit(bytes)
                }
            }
        }
        
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // For BYTES payloads, transfer is immediate.
            // We only use small messages (< 1KB) so we don't need to track transfer progress.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept connections (in production, might want user confirmation)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            _connectedEndpointName.update { info.endpointName }
            _connectionState.update { NetworkAdapter.ConnectionState.CONNECTING }
            cancelTimeout()
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    _connectionState.update { NetworkAdapter.ConnectionState.CONNECTED }
                    
                    // Stop advertising/discovery to save battery
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                    cancelTimeout()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
                }
                else -> {
                    _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
                }
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            // Per PRD, we do not implement auto-reconnection.
            // On disconnect, we simply update state to DISCONNECTED which triggers navigation to Main Menu.
            _connectionState.update { NetworkAdapter.ConnectionState.DISCONNECTED }
            _connectedEndpointName.update { null }
        }
    }
    
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Add to list of discovered endpoints
            val newEndpoint = NetworkAdapter.DiscoveredEndpoint(endpointId, info.endpointName)
            _discoveredEndpoints.update { currentList ->
                // Avoid duplicates
                if (currentList.any { it.id == endpointId }) currentList
                else currentList + newEndpoint
            }
        }
        
        override fun onEndpointLost(endpointId: String) {
            // Remove from list
            _discoveredEndpoints.update { currentList ->
                currentList.filter { it.id != endpointId }
            }
        }
    }
    
    override suspend fun connectToEndpoint(endpointId: String) {
        connectionsClient.requestConnection(
            localPlayerName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            // Connection request sent
        }.addOnFailureListener {
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            cancelTimeout()
        }
    }
    
    override suspend fun startAdvertising(name: String) {
        if (!PermissionsManager(context).hasPermissions()) {
            _errorMessage.update { "Missing required permissions" }
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            return
        }

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
        
        connectionsClient.startAdvertising(
            name,
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _connectionState.update { NetworkAdapter.ConnectionState.ADVERTISING }
            // State will update to CONNECTING when someone initiates connection
            startTimeoutTimer()
        }.addOnFailureListener { e ->
            _errorMessage.update { "Advertising failed: ${e.message}" }
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
        }
    }
    
    override suspend fun startDiscovery(name: String) {
        if (!PermissionsManager(context).hasPermissions()) {
            _errorMessage.update { "Missing required permissions" }
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            return
        }

        localPlayerName = name
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
        
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            // Discovery started successfully
            _connectionState.update { NetworkAdapter.ConnectionState.DISCOVERING }
            startTimeoutTimer()
        }.addOnFailureListener { e ->
            _errorMessage.update { "Discovery failed: ${e.message}" }
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
        }
    }
    
    override suspend fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _connectedEndpointName.update { null }
        _connectionState.update { NetworkAdapter.ConnectionState.DISCONNECTED }
        _errorMessage.update { null }
        _discoveredEndpoints.update { emptyList() }
        cancelTimeout()
    }

    private fun startTimeoutTimer() {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(60000) // 1 minute timeout
            if (_connectionState.value == NetworkAdapter.ConnectionState.ADVERTISING ||
                _connectionState.value == NetworkAdapter.ConnectionState.DISCOVERING) {
                stopAll()
                _errorMessage.update { "Connection timed out" }
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
    
    override suspend fun sendMessage(message: ByteArray) {
        val endpointId =
            connectedEndpointId ?: throw IllegalStateException("Cannot send message: not connected")

        val payload = Payload.fromBytes(message)
        connectionsClient.sendPayload(endpointId, payload)
    }
    
    /**
     * Exposes received messages as a Flow for easier consumption.
     */
    fun observeMessages(): Flow<ByteArray> = _receivedMessages
}
