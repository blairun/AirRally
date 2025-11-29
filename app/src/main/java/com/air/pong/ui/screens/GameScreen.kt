package com.air.pong.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.R
import com.air.pong.core.game.GamePhase
import com.air.pong.ui.GameViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit
) {
    val gameState by viewModel.gameState.collectAsState()
    
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.GAME_OVER) {
            onGameOver()
        }
    }
    
    // Handle Back Press
    androidx.activity.compose.BackHandler {
        // Forfeit the game (set state to GAME_OVER) so we don't auto-navigate back
        viewModel.forfeitGame()
        onGameOver() 
    }
    
    // State to track if the ball has bounced on my side
    var hasBounced by remember { mutableStateOf(false) }

    LaunchedEffect(gameState.ballArrivalTimestamp, gameState.isMyTurn, gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.RALLY && gameState.isMyTurn) {
            hasBounced = false
            // Bounce happens 200ms before arrival
            val bounceTime = gameState.ballArrivalTimestamp - 200
            val delayMs = bounceTime - System.currentTimeMillis()
            
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }
            hasBounced = true
        } else {
            // Reset for other states (like Serving, where we start ready)
            hasBounced = true 
        }
    }

    val targetColor = when {
        !gameState.isMyTurn -> Color(0xFFF44336) // Red (Opponent's Turn)
        gameState.gamePhase == GamePhase.RALLY && !hasBounced -> Color(0xFFFFEB3B) // Yellow (Incoming)
        else -> Color(0xFF4CAF50) // Green (My Turn / Serving)
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        label = "TurnColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Center content vertically if it fits
    ) {
        // Score
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.you), 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.White
                )
                Text(
                    text = if (viewModel.isHost) gameState.player1Score.toString() else gameState.player2Score.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(48.dp))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.opponent), 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.White
                )
                Text(
                    text = if (viewModel.isHost) gameState.player2Score.toString() else gameState.player1Score.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Center Message
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (gameState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                if (gameState.isMyTurn) {
                    Text(
                        stringResource(R.string.your_serve),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.swing_to_serve),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                } else {
                    Text(
                        stringResource(R.string.opponent_serving),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }
            } else if (gameState.gamePhase == GamePhase.POINT_SCORED) {
                Text(
                    stringResource(R.string.point_scored),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.get_ready),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                if (gameState.isMyTurn) {
                    // Only show HIT! if the ball has bounced (Green state)
                    if (hasBounced) {
                        Text(
                            stringResource(R.string.hit),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.wait),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Event Log
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.action_feed), 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    gameState.eventLog.forEachIndexed { index, event ->
                        val isMostRecent = index == gameState.eventLog.lastIndex
                        
                        Text(
                            text = event,
                            style = if (isMostRecent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (isMostRecent) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (isMostRecent) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Debug Overlay
            if (gameState.isDebugMode) {
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.debug_info), 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color.Green,
                            fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.phase_fmt, gameState.gamePhase), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.arrival_fmt, gameState.ballArrivalTimestamp), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.now_fmt, System.currentTimeMillis()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.delta_fmt, gameState.ballArrivalTimestamp - System.currentTimeMillis()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.difficulty_fmt, gameState.difficulty), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.flight_time_fmt, gameState.flightTime), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.last_swing), color = Color.Yellow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        gameState.lastSwingType?.let {
                             Text(stringResource(R.string.type_fmt, it), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        gameState.lastSwingData?.let {
                             Text(stringResource(R.string.force_fmt, "%.2f".format(it.force)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                             Text(stringResource(R.string.grav_z_fmt, "%.2f".format(it.gravZ)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
