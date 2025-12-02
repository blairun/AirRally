package com.air.pong.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import com.air.pong.R
import com.air.pong.core.game.Player
import com.air.pong.ui.GameViewModel

@Composable
fun GameOverScreen(
    viewModel: GameViewModel,
    onNavigateToSettings: () -> Unit,
    onReturnToDebug: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    val gameState by viewModel.gameState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Disconnect if system back button is pressed
    androidx.activity.compose.BackHandler {
        viewModel.disconnect()
        onReturnToMenu()
    }
    
    // Navigate back if disconnected (unless in debug mode)
    LaunchedEffect(connectionState) {
        if (!viewModel.isDebugGameSession && connectionState == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCONNECTED) {
            onReturnToMenu()
        }
    }
    
    // Notify that we are in the lobby (available) when we enter this screen
    LaunchedEffect(Unit) {
        viewModel.notifyInLobby()
    }

    val winner = if (gameState.player1Score > gameState.player2Score) Player.PLAYER_1 else Player.PLAYER_2
    val iWon = (viewModel.isHost && winner == Player.PLAYER_1) || (!viewModel.isHost && winner == Player.PLAYER_2)
    
    // Check if game actually finished (reached 11 points and won by 2)
    val isGameFinished = (gameState.player1Score >= 11 || gameState.player2Score >= 11) && 
                         kotlin.math.abs(gameState.player1Score - gameState.player2Score) >= 2

    val winnerName = when {
        isGameFinished && iWon -> "YOU"
        isGameFinished && !iWon -> "OPPONENT"
        else -> "UNKNOWN"
    }
    
    val isOpponentInLobby by viewModel.isOpponentInLobby.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Animation
        GameOverBackground(
            iWon = iWon,
            isGameFinished = isGameFinished,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (winnerName == "YOU") stringResource(R.string.you_won) else if (winnerName == "OPPONENT") stringResource(R.string.you_lost) else stringResource(R.string.game_over),
                style = MaterialTheme.typography.displayMedium,
                color = if (winnerName == "YOU") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (winnerName == "YOU" || winnerName == "OPPONENT") stringResource(R.string.final_score) else stringResource(R.string.score),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${gameState.player1Score} - ${gameState.player2Score}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Longest Rally: ${gameState.longestRally} hits",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val myAvatarIndex by viewModel.avatarIndex.collectAsState()
                val opponentAvatarIndex by viewModel.opponentAvatarIndex.collectAsState()
                val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mySize = if (iWon && isGameFinished) 120.dp else 80.dp
                    val myOutline = if (iWon && isGameFinished) Color(0xFF4CAF50) else if (isGameFinished) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
                    val myAnim = if (iWon && isGameFinished) AvatarAnimation.HAPPY_BOUNCE else if (isGameFinished) AvatarAnimation.SPIN else AvatarAnimation.NONE
                    
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .then(if (iWon && isGameFinished) Modifier.clickable { viewModel.playWinSound() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarView(
                            avatarResId = myAvatarResId,
                            outlineColor = myOutline,
                            animationType = myAnim,
                            modifier = Modifier.size(mySize)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("YOU", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(48.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val oppSize = if (!iWon && isGameFinished) 120.dp else 80.dp
                    val oppOutline = if (!iWon && isGameFinished) Color(0xFF4CAF50) else if (isGameFinished) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
                    val oppAnim = if (!iWon && isGameFinished) AvatarAnimation.HAPPY_BOUNCE else if (isGameFinished) AvatarAnimation.SPIN else AvatarAnimation.NONE

                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarView(
                            avatarResId = oppAvatarResId,
                            outlineColor = oppOutline,
                            animationType = oppAnim,
                            modifier = Modifier.size(oppSize)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.opponent), style = MaterialTheme.typography.labelMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = { viewModel.rematch() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                enabled = isOpponentInLobby
            ) {
                Text(if (isOpponentInLobby) stringResource(R.string.play_again) else stringResource(R.string.waiting_for_opponent))
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
                    onReturnToMenu() 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.main_menu))
            }
        }

        // Debug Overlay (Floating)
        if (viewModel.isDebugGameSession) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DEBUG MODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { viewModel.simulateRandomEndGame() }) {
                            Text("Change Score")
                        }
                        OutlinedButton(
                            onClick = { 
                                viewModel.stopDebugEndGame()
                                onReturnToDebug() 
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Text("Return")
                        }
                    }
                }
            }
        }
    }
}
