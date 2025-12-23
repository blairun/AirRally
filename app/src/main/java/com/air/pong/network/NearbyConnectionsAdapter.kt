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
    
    // Track connection role: true if we accepted the connection (host), false if we requested it (joiner)
    private var isHost: Boolean = false
    private var pendingConnectionEndpointId: String? = null
    
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
            android.util.Log.d("NearbyConnections", "Connection initiated: endpoint=$endpointId, name=${info.endpointName}, localName=$localPlayerName")
            // Auto-accept connections (in production, might want user confirmation)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            _connectedEndpointName.update { info.endpointName }
            _connectionState.update { NetworkAdapter.ConnectionState.CONNECTING }
            
            // Deterministic host selection: If we requested connection to this specific endpoint,
            // we are the joiner. Otherwise, we are the host (they requested connection to us).
            isHost = (pendingConnectionEndpointId != endpointId)
            android.util.Log.d("NearbyConnections", "Role determined: isHost=$isHost (pending=$pendingConnectionEndpointId, received=$endpointId)")
            
            cancelTimeout()
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            android.util.Log.d("NearbyConnections", "Connection result: endpoint=$endpointId, status=${result.status.statusCode}")
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    android.util.Log.d("NearbyConnections", "Connection successful! isHost=$isHost")
                    connectedEndpointId = endpointId
                    _connectionState.update { NetworkAdapter.ConnectionState.CONNECTED }
                    
                    // Stop advertising/discovery to save battery
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                    
                    // Clear discovered endpoints list now that we're connected
                    _discoveredEndpoints.update { emptyList() }
                    
                    cancelTimeout()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    android.util.Log.w("NearbyConnections", "Connection rejected by endpoint $endpointId")
                    _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
                }
                else -> {
                    android.util.Log.e("NearbyConnections", "Connection failed: status=${result.status.statusCode}, message=${result.status.statusMessage}")
                    _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
                }
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            isHost = false
            pendingConnectionEndpointId = null
            // Per PRD, we do not implement auto-reconnection.
            // On disconnect, we simply update state to DISCONNECTED which triggers navigation to Main Menu.
            _connectionState.update { NetworkAdapter.ConnectionState.DISCONNECTED }
            _connectedEndpointName.update { null }
        }
    }
    
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Filter out self-discovery: don't show our own advertisement
            if (info.endpointName == localPlayerName) {
                android.util.Log.d("NearbyConnections", "Filtered self-discovery: $endpointId")
                return
            }
            
            // Add to list of discovered endpoints
            val newEndpoint = NetworkAdapter.DiscoveredEndpoint(endpointId, info.endpointName)
            _discoveredEndpoints.update { currentList ->
                // Enhanced deduplication: check both ID and name
                // This prevents the same phone from appearing twice with different IDs
                val alreadyExists = currentList.any { 
                    it.id == endpointId || it.name == info.endpointName 
                }
                if (alreadyExists) {
                    android.util.Log.d("NearbyConnections", "Duplicate endpoint filtered: $endpointId (${info.endpointName})")
                    currentList
                } else {
                    android.util.Log.d("NearbyConnections", "New endpoint found: $endpointId (${info.endpointName})")
                    currentList + newEndpoint
                }
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
        // Mark which endpoint we're connecting to so we can identify role in onConnectionInitiated
        pendingConnectionEndpointId = endpointId
        
        connectionsClient.requestConnection(
            localPlayerName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            // Connection request sent
        }.addOnFailureListener {
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            pendingConnectionEndpointId = null
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
        
        // Clear any stale discovered endpoints from previous discovery sessions
        _discoveredEndpoints.update { emptyList() }
        
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
    
    override suspend fun startPlayWithFriend(name: String) {
        if (!PermissionsManager(context).hasPermissions()) {
            _errorMessage.update { "Missing required permissions" }
            _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            return
        }

        localPlayerName = name
        
        // Clear any stale discovered endpoints from previous sessions
        _discoveredEndpoints.update { emptyList() }
        
        // Immediately set state to DISCOVERING so UI is responsive
        _connectionState.update { NetworkAdapter.ConnectionState.DISCOVERING }
        
        // Start both advertising and discovery simultaneously
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
            
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
        
        // Track failures - if both fail, we have an error
        var advertisingFailed = false
        var discoveryFailed = false
        
        // Start advertising
        connectionsClient.startAdvertising(
            name,
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            android.util.Log.d("NearbyConnections", "Advertising started successfully")
            // Start timeout once at least one mode is active
            startTimeoutTimer()
        }.addOnFailureListener { e ->
            android.util.Log.e("NearbyConnections", "Advertising failed: ${e.message}")
            advertisingFailed = true
            // Only set error if both failed
            if (discoveryFailed) {
                _errorMessage.update { "Connection failed: ${e.message}" }
                _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            }
        }
        
        // Start discovery
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            android.util.Log.d("NearbyConnections", "Discovery started successfully")
            // Start timeout once at least one mode is active
            startTimeoutTimer()
        }.addOnFailureListener { e ->
            android.util.Log.e("NearbyConnections", "Discovery failed: ${e.message}")
            discoveryFailed = true
            // Only set error if both failed
            if (advertisingFailed) {
                _errorMessage.update { "Connection failed: ${e.message}" }
                _connectionState.update { NetworkAdapter.ConnectionState.ERROR }
            }
        }
    }
    
    override suspend fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        isHost = false
        pendingConnectionEndpointId = null
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

    fun setConnectedEndpointName(name: String?) {
        _connectedEndpointName.update { name }
    }
    
    override fun isHostRole(): Boolean = isHost
}
