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
import androidx.compose.ui.res.stringResource
import com.air.pong.R

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
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge
        )
        
        val taglines = remember {
            listOf(
                R.string.tagline_1,
                R.string.tagline_2,
                R.string.tagline_3,
                R.string.tagline_4,
                R.string.tagline_5,
                R.string.tagline_6,
                R.string.tagline_7,
                R.string.tagline_8,
                R.string.tagline_9,
                R.string.tagline_10
            )
        }
        val selectedTaglineId = rememberSaveable { taglines.random() }
        
        Text(
            text = stringResource(selectedTaglineId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        

        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (permissionState != PermissionsManager.PermissionState.GRANTED) {
            Text(stringResource(R.string.bluetooth_permission_required))
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
                text = stringResource(R.string.connected_to, connectedPlayerName ?: stringResource(R.string.unknown)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(stringResource(R.string.redirecting_lobby))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.disconnect))
            }
        } else {
            Button(
                onClick = { viewModel.hostGame() },
                enabled = permissionState == PermissionsManager.PermissionState.GRANTED && 
                          connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.host_game))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.joinGame() },
                enabled = permissionState == PermissionsManager.PermissionState.GRANTED && 
                          connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.join_game))
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
            Text(stringResource(R.string.settings))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (connectionState == NetworkAdapter.ConnectionState.ADVERTISING) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.waiting_for_players))
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text(stringResource(R.string.cancel))
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
                Text(stringResource(R.string.looking_for_games))
            } else {
                Text(stringResource(R.string.available_games), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                discoveredEndpoints.forEach { endpoint ->
                    OutlinedButton(
                        onClick = { viewModel.connectToEndpoint(endpoint.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.connect_to, endpoint.name))
                            if (discoveredEndpoints.size == 1 && autoConnectSeconds > 0 && connectingEndpointId == null) {
                                Text(
                                    stringResource(R.string.auto_connecting, autoConnectSeconds), 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (connectingEndpointId == endpoint.id) {
                                Text(
                                    stringResource(R.string.connecting), 
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
                Text(stringResource(R.string.cancel))
            }
        } else if (connectionState == NetworkAdapter.ConnectionState.CONNECTING) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.connecting))
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text(stringResource(R.string.cancel))
            }
        } else if (connectionState == NetworkAdapter.ConnectionState.ERROR) {
            val errorMessage by viewModel.errorMessage.collectAsState(initial = "Unknown Error")
            Text(
                text = stringResource(R.string.error_prefix, errorMessage ?: "Unknown"),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.disconnect() }) {
                Text(stringResource(R.string.reset))
            }
        }
    }


    // Share Button (Top Right)
    val shareText = stringResource(R.string.share_game_content)
    IconButton(
        onClick = { shareGame(context, shareText) },
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


fun shareGame(context: android.content.Context, shareText: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
