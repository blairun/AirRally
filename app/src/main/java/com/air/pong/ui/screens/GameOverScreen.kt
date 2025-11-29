package com.air.pong.ui.screens

import androidx.compose.foundation.layout.*
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
import com.air.pong.R
import com.air.pong.core.game.Player
import com.air.pong.ui.GameViewModel

@Composable
fun GameOverScreen(
    viewModel: GameViewModel,
    onNavigateToSettings: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    val gameState by viewModel.gameState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Disconnect if system back button is pressed
    androidx.activity.compose.BackHandler {
        viewModel.disconnect()
        onReturnToMenu()
    }
    
    // Navigate back if disconnected
    LaunchedEffect(connectionState) {
        if (connectionState == com.air.pong.core.network.NetworkAdapter.ConnectionState.DISCONNECTED) {
            onReturnToMenu()
        }
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
        
        Text(
            text = "${gameState.player1Score} - ${gameState.player2Score}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = { viewModel.rematch() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
        ) {
            Text(stringResource(R.string.play_again))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onNavigateToSettings() },
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
}
