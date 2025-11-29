package com.air.pong.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.air.pong.ui.GameViewModel
import com.air.pong.utils.PermissionsManager
import com.air.pong.core.network.NetworkAdapter
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    permissionsManager: PermissionsManager,
    onNavigateToLobby: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val permissionState by permissionsManager.permissionState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    val connectedPlayerName by viewModel.connectedPlayerName.collectAsState()
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()
    val connectingEndpointId by viewModel.connectingEndpointId.collectAsState()
    
    // Track if we are navigating to Lobby or Settings to prevent disconnect on ON_STOP
    var isNavigatingToLobby by remember { mutableStateOf(false) }
    var isNavigatingToSettings by remember { mutableStateOf(false) }
    
    // Handle Lifecycle events
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // If we are connected and NOT navigating to lobby or settings, disconnect
                if (connectionState == NetworkAdapter.ConnectionState.CONNECTED && 
                    !isNavigatingToLobby && !isNavigatingToSettings) {
                    viewModel.disconnect()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Reset navigation flags when we return to this screen
                isNavigatingToLobby = false
                isNavigatingToSettings = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Disconnect if system back button is pressed while connected
    if (connectionState == NetworkAdapter.ConnectionState.CONNECTED) {
        androidx.activity.compose.BackHandler {
            viewModel.disconnect()
        }
    }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Text(
            text = "AirRally",
            style = MaterialTheme.typography.displayLarge
        )
        
        val taglines = remember {
            listOf(
                "Ping pong without any balls 👀",
                "ImaginAIRy ping pong",
                "No table, no paddle, no problem.",
                "It's all in the wrist.",
                "Don't let go of your phone!",
                "100% invisible, 100% real.",
                "The ball is a lie.",
                "It's a paddle battle!",
                "Pongogo!",
                "Swing like nobody's watching."
            )
        }
        val selectedTagline = rememberSaveable { taglines.random() }
        
        Text(
            text = selectedTagline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        

        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (permissionState != PermissionsManager.PermissionState.GRANTED) {
            Text("Bluetooth permissions required to play.")
            Spacer(modifier = Modifier.height(16.dp))
            // In a real app, we'd have a button to request permissions here
            // For now, we assume MainActivity handles the request on start
        }
        
        if (connectionState == NetworkAdapter.ConnectionState.CONNECTED) {
            // Auto-navigate to Lobby when connected
            LaunchedEffect(Unit) {
                isNavigatingToLobby = true
                onNavigateToLobby()
            }
            
            Text(
                text = "Connected to ${connectedPlayerName ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Redirecting to Lobby...")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("DISCONNECT")
            }
        } else {
            Button(
                onClick = { viewModel.hostGame() },
                enabled = permissionState == PermissionsManager.PermissionState.GRANTED && 
                          connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("HOST GAME")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.joinGame() },
                enabled = permissionState == PermissionsManager.PermissionState.GRANTED && 
                          connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("JOIN GAME")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = {
                isNavigatingToSettings = true
                onNavigateToSettings()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("SETTINGS")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (connectionState == NetworkAdapter.ConnectionState.ADVERTISING) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Waiting for players...")
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text("CANCEL")
            }
        } else if (connectionState == NetworkAdapter.ConnectionState.DISCOVERING) {
            
            // Auto-connect logic
            var autoConnectSeconds by remember { mutableIntStateOf(3) }
            
            LaunchedEffect(discoveredEndpoints) {
                if (discoveredEndpoints.size == 1) {
                    autoConnectSeconds = 3
                    while (autoConnectSeconds > 0) {
                        kotlinx.coroutines.delay(1000)
                        autoConnectSeconds--
                    }
                    // If still only 1 endpoint, connect
                    if (discoveredEndpoints.size == 1) {
                        viewModel.connectToEndpoint(discoveredEndpoints.first().id)
                    }
                } else {
                    // Reset if 0 or >1 endpoints
                    autoConnectSeconds = 3
                }
            }
            
            if (discoveredEndpoints.isEmpty()) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Looking for nearby games...")
            } else {
                Text("Available Games:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                discoveredEndpoints.forEach { endpoint ->
                    OutlinedButton(
                        onClick = { viewModel.connectToEndpoint(endpoint.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Connect to ${endpoint.name}")
                            if (discoveredEndpoints.size == 1 && autoConnectSeconds > 0 && connectingEndpointId == null) {
                                Text(
                                    "Auto-connecting in ${autoConnectSeconds}s...", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (connectingEndpointId == endpoint.id) {
                                Text(
                                    "Connecting...", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text("CANCEL")
            }
        } else if (connectionState == NetworkAdapter.ConnectionState.CONNECTING) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connecting...")
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text("CANCEL")
            }
        } else if (connectionState == NetworkAdapter.ConnectionState.ERROR) {
            val errorMessage by viewModel.errorMessage.collectAsState(initial = "Unknown Error")
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.disconnect() }) {
                Text("Reset")
            }
        }
    }


    // Share Button (Top Right)
    IconButton(
        onClick = { shareGame(context) },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .statusBarsPadding() // Ensure it doesn't overlap with status bar
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share Game",
            tint = MaterialTheme.colorScheme.primary
        )
    }
    }
}


fun shareGame(context: android.content.Context) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Check out AirRally - Ping Pong without the table! https://github.com/blairun/AirRally")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
