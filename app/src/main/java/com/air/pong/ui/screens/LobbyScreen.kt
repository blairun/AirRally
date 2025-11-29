package com.air.pong.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.air.pong.core.game.GamePhase
import com.air.pong.ui.GameViewModel

@Composable
fun LobbyScreen(
    viewModel: GameViewModel,
    onNavigateToGame: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val connectedPlayerName by viewModel.connectedPlayerName.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val isOpponentInLobby by viewModel.isOpponentInLobby.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Disconnect if system back button is pressed
    androidx.activity.compose.BackHandler {
        viewModel.disconnect()
    }
    
    LaunchedEffect(Unit) {
        viewModel.notifyInLobby()
    }
    
    // Navigate back if disconnected
    LaunchedEffect(connectionState) {
        if (connectionState == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCONNECTED) {
            onNavigateBack()
        }
    }
    
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
            onNavigateToGame()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LOBBY",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Connected to: ${connectedPlayerName ?: "Unknown"}",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (viewModel.isHost) {
            Button(
                onClick = { viewModel.startGame() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isOpponentInLobby
            ) {
                Text(if (isOpponentInLobby) "START GAME" else "WAITING FOR OPPONENT...")
            }
        } else {
            Text("Waiting for host to start...")
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { 
                onNavigateToSettings()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("SETTINGS")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { 
                viewModel.disconnect()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("LEAVE")
        }
    }
}
