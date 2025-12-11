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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.core.game.GamePhase
import com.air.pong.ui.GameViewModel
import com.air.pong.core.game.GameMode
import com.air.pong.ui.components.ActionFeedPanel
import com.air.pong.ui.components.AnimatedScoreCounter
import com.air.pong.ui.components.AvatarView
import com.air.pong.ui.components.DebugGameControls
import com.air.pong.ui.components.PointsPopupWithGridIndex
import com.air.pong.ui.components.RallyGrid
import com.air.pong.ui.components.getAvatarState
import com.air.pong.ui.components.VsIntroOverlay
import com.air.pong.ui.components.ScrollingTipsDisplay
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit,
    onStopDebug: () -> Unit = {}
) {
    com.air.pong.ui.components.KeepScreenOn()

    val gameState by viewModel.gameState.collectAsState()
    
    // VS Intro overlay state (only for Classic mode)
    var showVsIntro by remember { mutableStateOf(gameState.gameMode == GameMode.CLASSIC) }
    
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.GAME_OVER) {
            if (viewModel.isDebugGameSession) {
                // In Debug Mode, we don't go to GameOver screen.
                // We just stop the game (reset to IDLE) and go back to Debug Menu.
                viewModel.stopDebugGame()
                onStopDebug()
            } else {
                onGameOver()
            }
        }
    }
    
    // Exit confirmation dialog state
    var showExitConfirmation by remember { mutableStateOf(false) }
    
    // Handle Back Press - show confirmation dialog instead of immediate forfeit
    androidx.activity.compose.BackHandler {
        showExitConfirmation = true
    }
    
    // Exit confirmation dialog
    if (showExitConfirmation) {
        com.air.pong.ui.dialogs.ExitConfirmationDialog(
            onConfirm = {
                showExitConfirmation = false
                viewModel.forfeitGame()
                onGameOver()
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Layer
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Score Section (Top)
                val opponentName by viewModel.connectedPlayerName.collectAsState()
                val myAvatarIndex by viewModel.avatarIndex.collectAsState()
                val opponentAvatarIndex by viewModel.opponentAvatarIndex.collectAsState()
                
                if (gameState.gameMode == GameMode.RALLY) {
                    // Rally Mode: LIVES and SCORE row, then overlapping avatars below
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LIVES column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "LIVES", 
                                style = MaterialTheme.typography.titleMedium, 
                                color = Color.White
                            )
                            Text(
                                text = gameState.rallyLives.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // SCORE column  
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "SCORE", 
                                style = MaterialTheme.typography.titleMedium, 
                                color = Color.White
                            )
                            AnimatedScoreCounter(
                                targetScore = gameState.rallyScore
                            )
                        }
                    }
                    
                    // Overlapping avatars centered below LIVES/SCORE
                    val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                    val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.air.pong.ui.components.RallyAvatarPair(
                            myAvatarResId = myAvatarResId,
                            partnerAvatarResId = oppAvatarResId,
                            isMyTurn = gameState.isMyTurn
                        )
                    }
                } else {
                    // Classic Mode: Original two-column layout with avatars under each score
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // My Score Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
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
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            // My Avatar
                            val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                            
                            // Determine My Avatar State
                            val (myOutlineColor, myAnimation) = getAvatarState(
                                isMe = true,
                                gameState = gameState
                            )

                            AvatarView(
                                avatarResId = myAvatarResId,
                                outlineColor = myOutlineColor,
                                animationType = myAnimation,
                                modifier = Modifier.size(120.dp)
                            )
                        }
                        
                        // Small spacer for visual separation between the two weighted columns
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Opponent Score Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = opponentName ?: stringResource(R.string.opponent), 
                                style = MaterialTheme.typography.titleMedium, 
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (viewModel.isHost) gameState.player2Score.toString() else gameState.player1Score.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            // Opponent Avatar
                            val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                            
                            // Determine Opponent Avatar State
                            val (oppOutlineColor, oppAnimation) = getAvatarState(
                                isMe = false,
                                gameState = gameState
                            )

                            AvatarView(
                                avatarResId = oppAvatarResId,
                                outlineColor = oppOutlineColor,
                                animationType = oppAnimation,
                                modifier = Modifier.size(120.dp)
                            )
                        }
                    }
                }
                
                // Rally Mode: Instruction text right below avatars, then grid fills remaining space
                if (gameState.gameMode == GameMode.RALLY) {
                    // Instruction text (compact, below avatars) - fixed height container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (gameState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                                if (gameState.isMyTurn) {
                                    Text(
                                        stringResource(R.string.swing_to_serve_prompt),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    ScrollingTipsDisplay(
                                        gameMode = gameState.gameMode,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.partner_serving),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    ScrollingTipsDisplay(
                                        gameMode = gameState.gameMode,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                }
                            } else if (gameState.gamePhase == GamePhase.POINT_SCORED) {
                                Text(
                                    stringResource(R.string.one_life_down),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    stringResource(R.string.next_up),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                if (gameState.isMyTurn) {
                                    if (hasBounced) {
                                        Text(
                                            stringResource(R.string.hit),
                                            style = MaterialTheme.typography.displayLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.wait),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    
                    // Track points popup state
                    var showPointsPopup by remember { mutableStateOf(false) }
                    var popupPoints by remember { mutableIntStateOf(0) }
                    
                    // Trigger popup when points are earned (rallyScorePoints > 0)
                    LaunchedEffect(gameState.rallyScorePoints, gameState.rallyScore) {
                        if (gameState.rallyScorePoints > 0) {
                            popupPoints = gameState.rallyScorePoints
                            showPointsPopup = true
                        }
                    }
                    
                    // Track grid size for popup positioning
                    var gridWidth by remember { mutableFloatStateOf(0f) }
                    var gridHeight by remember { mutableFloatStateOf(0f) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RallyGrid(
                            gridState = gameState.rallyGrid,
                            cellTiers = gameState.rallyCellTiers,
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    gridWidth = coordinates.size.width.toFloat()
                                    gridHeight = coordinates.size.height.toFloat()
                                }
                        )
                        
                        // Points popup overlay - positioned over the hit cell
                        PointsPopupWithGridIndex(
                            points = popupPoints,
                            gridIndex = gameState.lastHitGridIndex,
                            gridWidth = gridWidth,
                            gridHeight = gridHeight,
                            isVisible = showPointsPopup,
                            onAnimationComplete = { showPointsPopup = false }
                        )
                    }
                } else {
                    // Classic Mode: Use Box with center alignment for proper vertical centering
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (gameState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                                if (gameState.isMyTurn) {
                                    Text(
                                        stringResource(R.string.swing_to_serve_prompt),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    ScrollingTipsDisplay(
                                        gameMode = gameState.gameMode,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.opponent_serving),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    ScrollingTipsDisplay(
                                        gameMode = gameState.gameMode,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                }
                            } else if (gameState.gamePhase == GamePhase.POINT_SCORED) {
                                Text(
                                    stringResource(R.string.point_scored),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    stringResource(R.string.get_ready),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                if (gameState.isMyTurn) {
                                    if (hasBounced) {
                                        Text(
                                            stringResource(R.string.hit),
                                            style = MaterialTheme.typography.displayLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.wait),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Debug Game Controls (Overlay layer)
            if (viewModel.isDebugGameSession) {
                DebugGameControls(
                    viewModel = viewModel,
                    onStopDebug = onStopDebug,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp)
                )
            }
            
            // Debug Overlay + Action Feed Panel (Bottom Sheet - overlays everything)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Debug Overlay ---
                if (gameState.isDebugMode) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Column 1: Game State
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.phase_fmt, gameState.gamePhase), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.difficulty_fmt, gameState.difficulty), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.flight_time_fmt, gameState.flightTime), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.hit_window_fmt, gameState.currentHitWindow), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Column 2: Timing
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.arrival_fmt, gameState.ballArrivalTimestamp), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.now_fmt, System.currentTimeMillis()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.delta_fmt, gameState.ballArrivalTimestamp - System.currentTimeMillis()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.last_swing), color = Color.Yellow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                gameState.lastSwingType?.let {
                                    Text(stringResource(R.string.type_fmt, it), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                                }
                                gameState.lastSwingData?.let {
                                    Text(stringResource(R.string.force_fmt, it.force), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.grav_z_fmt, "%.2f".format(it.gravZ)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                
                ActionFeedPanel(
                    events = gameState.eventLog,
                )
            }
        }
        
        // VS Intro Overlay (Classic mode only)
        if (showVsIntro && gameState.gameMode == GameMode.CLASSIC) {
            val myAvatarIndex by viewModel.avatarIndex.collectAsState()
            val opponentAvatarIndex by viewModel.opponentAvatarIndex.collectAsState()
            val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
            val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
            
            VsIntroOverlay(
                myAvatarResId = myAvatarResId,
                opponentAvatarResId = oppAvatarResId,
                onPlayVsSound = { viewModel.playVsSound() },
                onComplete = { showVsIntro = false }
            )
        }
    }
}
