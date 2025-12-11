package com.air.pong.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.R
import com.air.pong.ui.GameViewModel

/**
 * Debug controls overlay for testing game mechanics.
 * Only shown during debug game sessions.
 * 
 * @param viewModel The game view model
 * @param onStopDebug Callback when debug mode is stopped
 * @param modifier Modifier for positioning
 */
@Composable
fun DebugGameControls(
    viewModel: GameViewModel,
    onStopDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAutoPlay by viewModel.isAutoPlayEnabled.collectAsState()
    
    Card(
        modifier = modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.debug_controls_title), 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Simulate Local Hit (Left)
                Button(
                    onClick = { viewModel.simulateLocalSwing() },
                    modifier = Modifier.height(48.dp).width(100.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.debug_simulate_my_hit), 
                        fontSize = 12.sp, 
                        textAlign = TextAlign.Center, 
                        lineHeight = 14.sp
                    )
                }

                // Auto-Play (Center)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.debug_auto_play), 
                        color = Color.White, 
                        style = MaterialTheme.typography.labelSmall
                    )
                    Switch(
                        checked = isAutoPlay,
                        onCheckedChange = { viewModel.setAutoPlay(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Simulate Opponent Hit (Right)
                Button(
                    onClick = { viewModel.simulateOpponentSwing() },
                    modifier = Modifier.height(48.dp).width(100.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.debug_simulate_opp_hit), 
                        fontSize = 12.sp, 
                        textAlign = TextAlign.Center, 
                        lineHeight = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { 
                    viewModel.stopDebugGame() 
                    onStopDebug()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Text(stringResource(R.string.debug_return))
            }
        }
    }
}
