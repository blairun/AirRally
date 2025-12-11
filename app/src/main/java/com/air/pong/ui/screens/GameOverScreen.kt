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
import com.air.pong.core.game.GameMode
import com.air.pong.ui.components.AvatarAnimation
import com.air.pong.ui.components.AvatarView

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
    // Also check for new high score in Rally mode
    LaunchedEffect(Unit) {
        viewModel.notifyInLobby()
        if (gameState.gameMode == GameMode.RALLY) {
            viewModel.checkRallyHighScore()
        }
    }
    
    val isNewHighScore by viewModel.isNewHighScore.collectAsState()
    val rallyHighScore by viewModel.rallyHighScore.collectAsState()

    val winner = if (gameState.player1Score > gameState.player2Score) Player.PLAYER_1 else Player.PLAYER_2
    val iWon = (viewModel.isHost && winner == Player.PLAYER_1) || (!viewModel.isHost && winner == Player.PLAYER_2)
    val isRally = gameState.gameMode == GameMode.RALLY
    
    // Check if game actually finished (reached 11 points and won by 2) OR Rally Lives 0
    val isGameFinished = if (isRally) {
        gameState.rallyLives <= 0
    } else {
        (gameState.player1Score >= 11 || gameState.player2Score >= 11) && 
        kotlin.math.abs(gameState.player1Score - gameState.player2Score) >= 2
    }

    val winnerName = when {
        isRally -> "TEAM"
        isGameFinished && iWon -> "YOU"
        isGameFinished && !iWon -> "OPPONENT"
        else -> "UNKNOWN"
    }
    
    val isOpponentInLobby by viewModel.isOpponentInLobby.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Animation
        GameOverBackground(
            iWon = if (isRally) true else iWon, // Rally is always "good" background? Or maybe neutral?
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
                text = if (isRally) stringResource(R.string.game_over) else if (winnerName == "YOU") stringResource(R.string.you_won) else if (winnerName == "OPPONENT") stringResource(R.string.you_lost) else stringResource(R.string.game_over),
                style = MaterialTheme.typography.displayMedium,
                color = if (isRally) MaterialTheme.colorScheme.primary else if (winnerName == "YOU") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isRally) {
                // 3-Column Grid Layout for Rally Mode Stats
                val tealHighlight = Color(0xFF00BCD4) // Teal for new high score
                val textStyle = MaterialTheme.typography.bodyLarge
                
                // Score (large, centered)
                Text(
                    text = gameState.rallyScore.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Grid header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "",
                        modifier = Modifier.weight(1.2f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.game_over_you),
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.game_over_partner),
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // High Score row (separate columns for you/partner)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = if (isNewHighScore) stringResource(R.string.game_over_new_high_score) else stringResource(R.string.game_over_high_score),
                        modifier = Modifier.weight(1.2f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        color = if (isNewHighScore) tealHighlight else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = rallyHighScore.toString(),
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (isNewHighScore) tealHighlight else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "-", // Partner's high score not tracked across devices
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Lines Cleared row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = stringResource(R.string.game_over_lines_cleared),
                        modifier = Modifier.weight(1.2f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = gameState.totalLinesCleared.toString(),
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = gameState.partnerTotalLinesCleared.toString(),
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Grid Level row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = stringResource(R.string.game_over_grid_level),
                        modifier = Modifier.weight(1.2f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    // Show tier as point value for clarity (1pt, 3pt, 8pt...)
                    val myTierPoints = com.air.pong.core.game.GameEngine.POINT_TIERS.getOrElse(gameState.highestTierAchieved) { 1 }
                    val partnerTierPoints = com.air.pong.core.game.GameEngine.POINT_TIERS.getOrElse(gameState.opponentHighestTier) { 1 }
                    Text(
                        text = "${myTierPoints}pt",
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "${partnerTierPoints}pt",
                        modifier = Modifier.weight(0.9f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Longest Rally row (merged 2nd+3rd columns) - LAST
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = stringResource(R.string.game_over_longest_rally),
                        modifier = Modifier.weight(1.2f),
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${gameState.longestRally}",
                        modifier = Modifier.weight(1.8f), // Span both columns
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Classic Mode - Original Layout
                Text(
                    text = stringResource(R.string.final_score),
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
            }

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
                    val mySize = if (isRally) 80.dp else if (iWon && isGameFinished) 120.dp else 80.dp
                    val myOutline = if (isRally) MaterialTheme.colorScheme.outline else if (iWon && isGameFinished) Color(0xFF4CAF50) else if (isGameFinished) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
                    val myAnim = if (isRally) AvatarAnimation.NONE else if (iWon && isGameFinished) AvatarAnimation.HAPPY_BOUNCE else if (isGameFinished) AvatarAnimation.SPIN else AvatarAnimation.NONE
                    
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
                    val oppSize = if (isRally) 80.dp else if (!iWon && isGameFinished) 120.dp else 80.dp
                    val oppOutline = if (isRally) MaterialTheme.colorScheme.outline else if (!iWon && isGameFinished) Color(0xFF4CAF50) else if (isGameFinished) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
                    val oppAnim = if (isRally) AvatarAnimation.NONE else if (!iWon && isGameFinished) AvatarAnimation.HAPPY_BOUNCE else if (isGameFinished) AvatarAnimation.SPIN else AvatarAnimation.NONE

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
                    Text(
                        text = if (isRally) stringResource(R.string.partner) else stringResource(R.string.opponent),
                        style = MaterialTheme.typography.labelMedium
                    )
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
            
            // Switch Mode Button
            OutlinedButton(
                onClick = { 
                    val newMode = if (gameState.gameMode == GameMode.RALLY) GameMode.CLASSIC else GameMode.RALLY
                    viewModel.setGameMode(newMode)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = viewModel.isHost && isOpponentInLobby
            ) {
                Text(
                    text = if (gameState.gameMode == GameMode.RALLY) 
                        "SWITCH TO ${stringResource(R.string.game_mode_classic).uppercase()}" 
                    else 
                        "SWITCH TO ${stringResource(R.string.game_mode_rally).uppercase()}"
                )
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
