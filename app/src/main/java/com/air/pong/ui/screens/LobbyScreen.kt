package com.air.pong.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.air.pong.R
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
    val isHost = viewModel.isHost
    
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
            text = stringResource(R.string.lobby_title),
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Avatars Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Player
            val myAvatarIndex by viewModel.avatarIndex.collectAsState()
            val myName by viewModel.playerName.collectAsState()
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val avatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = avatarResId),
                    contentDescription = "My Avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = myName ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // VS Text
            Text(
                text = "VS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Opponent Player
            if (isOpponentInLobby) {
                val opponentAvatarIndex by viewModel.opponentAvatarIndex.collectAsState()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val avatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = avatarResId),
                        contentDescription = "Opponent Avatar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape) // Red border for opponent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = connectedPlayerName ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Placeholder for opponent
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.waiting_for_opponent),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (isHost) {
            Button(
                onClick = { viewModel.startGame() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isOpponentInLobby
            ) {
                Text(if (isOpponentInLobby) stringResource(R.string.start_game) else stringResource(R.string.waiting_for_opponent))
            }
        } else {
            Text(stringResource(R.string.waiting_for_host))
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                viewModel.notifyBusy()
                onNavigateToSettings()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.settings))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { 
                viewModel.disconnect()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.leave))
        }
    }
}
