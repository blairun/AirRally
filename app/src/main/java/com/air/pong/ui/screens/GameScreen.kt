package com.air.pong.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.zIndex
import com.air.pong.R
import com.air.pong.core.game.GamePhase
import com.air.pong.ui.GameViewModel
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.BallState
import com.air.pong.core.game.SwingType
import com.air.pong.core.game.getFlightTimeModifier
import com.air.pong.core.game.getServeFlightTimeModifier
import com.air.pong.ui.components.ActionFeedPanel
import com.air.pong.ui.components.AnimatedScoreCounter
import com.air.pong.ui.components.AvatarView
import com.air.pong.ui.components.DebugGameControls
import com.air.pong.ui.components.PointsPopupWithGridIndex
import com.air.pong.ui.components.RallyGrid
import com.air.pong.ui.components.getAvatarState
import com.air.pong.ui.components.VsIntroOverlay
import com.air.pong.ui.components.ScrollingTipsDisplay
import com.air.pong.ui.components.MomentumBadge
import com.air.pong.ui.components.BallBounceAnimation
import com.air.pong.ui.components.SoloBallBounceAnimation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha

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
    // Use remember with keys to synchronously reset to false when turn changes during RALLY
    // This prevents brief flash of "HIT!" when partner hits (before LaunchedEffect runs)
    val shouldStartAsFalse = gameState.gamePhase == GamePhase.RALLY && gameState.isMyTurn
    var hasBounced by remember(gameState.ballArrivalTimestamp, gameState.isMyTurn) { 
        mutableStateOf(!shouldStartAsFalse) 
    }

    LaunchedEffect(gameState.ballArrivalTimestamp, gameState.isMyTurn, gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.RALLY && gameState.isMyTurn) {
            // Bounce happens 200ms before arrival
            val bounceTime = gameState.ballArrivalTimestamp - 200
            val delayMs = bounceTime - System.currentTimeMillis()
            
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }
            hasBounced = true
        }
    }

    // Background colors: Solo Rally uses only Yellow/Green (no opponent turn = no red)
    val isSoloRally = gameState.gameMode == GameMode.SOLO_RALLY
    
    val targetColor = when {
        // Solo Rally: Use ballState (BOUNCED_MY_SIDE) for accurate timing with bounce sound
        isSoloRally && gameState.gamePhase == GamePhase.RALLY && gameState.ballState == BallState.BOUNCED_MY_SIDE -> Color(0xFF4CAF50) // Green (Hit window open)
        isSoloRally && gameState.gamePhase == GamePhase.RALLY -> Color(0xFFFFEB3B) // Yellow (Ball traveling)
        isSoloRally -> Color(0xFF4CAF50) // Green (Serving / other states)
        // Co-op/Classic modes: Original logic using hasBounced
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
        // Avatar indices needed for both main content and overlay
        val myAvatarIndex by viewModel.avatarIndex.collectAsState()
        val opponentAvatarIndex by viewModel.opponentAvatarIndex.collectAsState()
        
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Layer
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Score Section (Top)
                val opponentName by viewModel.connectedPlayerName.collectAsState()
                
                if (gameState.gameMode == GameMode.RALLY || gameState.gameMode == GameMode.SOLO_RALLY) {
                    // Rally Mode (Co-op or Solo): LIVES and SCORE row, then avatar(s) below
                    val isSoloRally = gameState.gameMode == GameMode.SOLO_RALLY
                    
                    // Get lives and score from appropriate state
                    val lives = if (isSoloRally) {
                        gameState.soloRallyState?.lives ?: 0
                    } else {
                        gameState.rallyState?.lives ?: 0
                    }
                    val score = if (isSoloRally) {
                        gameState.soloRallyState?.score ?: 0
                    } else {
                        gameState.rallyState?.score ?: 0
                    }
                    
                    // Rally Mode Header - Table layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        // Row 1: Labels (LIVES | STREAK | SCORE) - all same size
                        // STREAK label only visible when multiplier is active
                        val streakActive = com.air.pong.core.game.GameEngine.getMomentumMultiplier(gameState.currentRallyLength) > 1.0f
                        // Animate STREAK label alpha to match MomentumBadge fade timings
                        val streakLabelAlpha by animateFloatAsState(
                            targetValue = if (streakActive) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = if (streakActive) 600 else 500
                            ),
                            label = "streakLabelFade"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "LIVES",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                "STREAK",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = streakLabelAlpha),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                "SCORE",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        
                        // Row 2: Values (lives number | score number) with MomentumBadge as overlay
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Lives and Score row (determines height)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Lives value - large number
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lives.toString(),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Spacer for streak column (maintains layout)
                                Box(modifier = Modifier.weight(1f))
                                
                                // Score value - large number that shrinks if needed
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Use fontSize that shrinks for large scores
                                    val scoreText = score.toString()
                                    val fontSize = when {
                                        scoreText.length > 4 -> MaterialTheme.typography.headlineLarge.fontSize
                                        scoreText.length > 3 -> MaterialTheme.typography.displayMedium.fontSize
                                        else -> MaterialTheme.typography.displayLarge.fontSize
                                    }
                                    Text(
                                        text = scoreText,
                                        fontSize = fontSize,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            
                            // MomentumBadge as overlay in center (can overflow below)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                MomentumBadge(rallyLength = gameState.currentRallyLength)
                            }
                        }
                    }
                    
                    // Avatar display: Single centered avatar for Solo, overlapping pair for Co-op
                    val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSoloRally) {
                            // Solo Rally: Single centered avatar
                            AvatarView(
                                avatarResId = myAvatarResId,
                                outlineColor = Color.White,
                                animationType = com.air.pong.ui.components.AvatarAnimation.NONE,
                                modifier = Modifier.size(100.dp)
                            )
                        } else {
                            // Co-op Rally: Overlapping avatars
                            val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                            com.air.pong.ui.components.RallyAvatarPair(
                                myAvatarResId = myAvatarResId,
                                partnerAvatarResId = oppAvatarResId,
                                isMyTurn = gameState.isMyTurn
                            )
                        }
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
                
                // Rally Mode (Co-op or Solo): Instruction text right below avatars, then grid fills remaining space
                if (gameState.gameMode == GameMode.RALLY || gameState.gameMode == GameMode.SOLO_RALLY) {
                    val isSoloRally = gameState.gameMode == GameMode.SOLO_RALLY
                    
                    // Get opponent avatar for animation (Co-op only)
                    val oppAvatarResIdForAnimation = if (!isSoloRally) {
                        com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { 
                            com.air.pong.ui.AvatarUtils.avatarResources.first() 
                        }
                    } else {
                        com.air.pong.ui.AvatarUtils.avatarResources.first() // Placeholder for Solo
                    }
                    
                    // Track if current ball flight is from a serve
                    // Rally length is 0 when serve just happened, 1+ during rally
                    val isServeBall = gameState.currentRallyLength <= 1
                    
                    // Instruction text (compact, below avatars) - fixed height container
                    // zIndex ensures this Box (including ball animation) renders above avatars
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(top = 8.dp)
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background animation (Co-op Rally only)
                        if (!isSoloRally) {
                            // Animated opacity: 80% during active gameplay, fade to 0% during stoppage
                            val targetOpacity = if (gameState.gamePhase == GamePhase.RALLY) 0.8f else 0f
                            val animationOpacity by animateFloatAsState(
                                targetValue = targetOpacity,
                                animationSpec = tween(
                                    durationMillis = if (targetOpacity > 0f) 300 else 500
                                ),
                                label = "AnimationFade"
                            )
                            
                            // Calculate actual flight time with swing type modifier
                            val swingSettings by viewModel.swingSettings.collectAsState()
                            val swingType = gameState.lastSwingType ?: SwingType.MEDIUM_FLAT
                            val actualFlightTimeMs = (gameState.flightTime * if (isServeBall) {
                                swingType.getServeFlightTimeModifier(swingSettings)
                            } else {
                                swingType.getFlightTimeModifier(swingSettings)
                            }).toLong()
                            
                            val oppAvatarResIdForAnimation = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { 
                                com.air.pong.ui.AvatarUtils.avatarResources.first() 
                            }
                            
                            BallBounceAnimation(
                                gamePhase = gameState.gamePhase,
                                isMyTurn = gameState.isMyTurn,
                                lastSwingType = gameState.lastSwingType,
                                flightTimeMs = actualFlightTimeMs,
                                ballArrivalTimestamp = gameState.ballArrivalTimestamp,
                                myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { 
                                    com.air.pong.ui.AvatarUtils.avatarResources.first() 
                                },
                                partnerAvatarResId = oppAvatarResIdForAnimation,
                                isServing = isServeBall,
                                modifier = Modifier.fillMaxSize().alpha(animationOpacity)
                            )
                        } else {
                            // Solo Rally: Show wall animation
                            val targetOpacity = if (gameState.gamePhase == GamePhase.RALLY) 0.8f else 0f
                            val animationOpacity by animateFloatAsState(
                                targetValue = targetOpacity,
                                animationSpec = tween(
                                    durationMillis = if (targetOpacity > 0f) 300 else 500
                                ),
                                label = "SoloAnimationFade"
                            )
                            
                            // Calculate actual flight time with swing type modifier
                            // Match GameEngine.getBaseFlightTime() which includes solo adder
                            val swingSettings by viewModel.swingSettings.collectAsState()
                            val soloFlightAdder by viewModel.soloFlightAdder.collectAsState()
                            val swingType = gameState.lastSwingType ?: SwingType.MEDIUM_FLAT
                            val baseFlightTime = gameState.flightTime + soloFlightAdder
                            val actualFlightTimeMs = (baseFlightTime * if (isServeBall) {
                                swingType.getServeFlightTimeModifier(swingSettings)
                            } else {
                                swingType.getFlightTimeModifier(swingSettings)
                            }).toLong()
                            
                            SoloBallBounceAnimation(
                                gamePhase = gameState.gamePhase,
                                lastSwingType = gameState.lastSwingType,
                                flightTimeMs = actualFlightTimeMs,
                                ballArrivalTimestamp = gameState.ballArrivalTimestamp,
                                myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { 
                                    com.air.pong.ui.AvatarUtils.avatarResources.first() 
                                },
                                isServing = isServeBall,
                                modifier = Modifier.fillMaxSize().alpha(animationOpacity)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            if (gameState.gamePhase == GamePhase.WAITING_FOR_SERVE) {
                                // Solo Rally: player always serves. Co-op Rally: check isMyTurn
                                if (isSoloRally || gameState.isMyTurn) {
                                    Text(
                                        stringResource(R.string.swing_to_serve_prompt),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.partner_serving),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                ScrollingTipsDisplay(
                                    gameMode = gameState.gameMode,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
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
                                // During rally - match Classic mode behavior:
                                // Show HIT! when my turn and bounced, WAIT when opponent's turn
                                val showHit = if (isSoloRally) {
                                    // Solo Rally: use ballState (consistent with background color timing)
                                    gameState.ballState == BallState.BOUNCED_MY_SIDE
                                } else {
                                    // Co-op Rally: match Classic mode - isMyTurn AND hasBounced
                                    gameState.isMyTurn && hasBounced
                                }
                                
                                if (isSoloRally) {
                                    // Solo Rally: only show HIT! (no opponent, no WAIT needed)
                                    if (showHit) {
                                        Text(
                                            stringResource(R.string.hit),
                                            style = MaterialTheme.typography.displayLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // Co-op Rally: only show HIT! when my turn and bounced
                                    if (showHit) {
                                        Text(
                                            stringResource(R.string.hit),
                                            style = MaterialTheme.typography.displayLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    // No WAIT text - colors and animation guide players
                                }
                            }
                        }
                    }
                    
                    // Track points popup state
                    var showPointsPopup by remember { mutableStateOf(false) }
                    var popupPoints by remember { mutableIntStateOf(0) }
                    var popupLinesCleared by remember { mutableIntStateOf(0) }
                    
                    // Get grid/score data from appropriate state
                    val gridState = if (isSoloRally) {
                        gameState.soloRallyState?.grid ?: List(9) { false }
                    } else {
                        gameState.rallyState?.grid ?: List(9) { false }
                    }
                    val cellTiers = if (isSoloRally) {
                        gameState.soloRallyState?.cellTiers ?: List(9) { 0 }
                    } else {
                        gameState.rallyState?.cellTiers ?: List(9) { 0 }
                    }
                    // Score points for popup animation
                    val scorePoints = if (isSoloRally) {
                        gameState.soloRallyState?.scorePoints ?: 0
                    } else {
                        gameState.rallyState?.scorePoints ?: 0
                    }
                    val linesJustCleared = if (isSoloRally) {
                        gameState.soloRallyState?.linesJustCleared ?: 0
                    } else {
                        gameState.rallyState?.linesJustCleared ?: 0
                    }
                    val currentScore = if (isSoloRally) {
                        gameState.soloRallyState?.score ?: 0
                    } else {
                        gameState.rallyState?.score ?: 0
                    }
                    val lastHitGridIndex = if (isSoloRally) {
                        gameState.soloRallyState?.lastHitGridIndex ?: -1
                    } else {
                        gameState.rallyState?.lastHitGridIndex ?: -1
                    }
                    
                    // Trigger popup when points are earned (scorePoints > 0)
                    LaunchedEffect(scorePoints, currentScore) {
                        if (scorePoints > 0) {
                            popupPoints = scorePoints
                            popupLinesCleared = linesJustCleared
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
                            gridState = gridState,
                            cellTiers = cellTiers,
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
                            linesCleared = popupLinesCleared,
                            gridIndex = lastHitGridIndex,
                            gridWidth = gridWidth,
                            gridHeight = gridHeight,
                            isVisible = showPointsPopup,
                            onAnimationComplete = { showPointsPopup = false }
                        )
                    }
                } else {
                    // Classic Mode: Use Box with center alignment for proper vertical centering
                    // Track if current ball flight is from a serve (same logic as Rally)
                    val isServeBall = gameState.currentRallyLength <= 1
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background animation (Classic Mode)
                        // Animated opacity: 80% during active gameplay, fade to 0% during stoppage
                        val targetOpacity = if (gameState.gamePhase == GamePhase.RALLY) 0.8f else 0f
                        val animationOpacity by animateFloatAsState(
                            targetValue = targetOpacity,
                            animationSpec = tween(
                                durationMillis = if (targetOpacity > 0f) 300 else 500
                            ),
                            label = "ClassicAnimationFade"
                        )
                        
                        // Calculate actual flight time with swing type modifier
                        val swingSettings by viewModel.swingSettings.collectAsState()
                        val swingType = gameState.lastSwingType ?: SwingType.MEDIUM_FLAT
                        val actualFlightTimeMs = (gameState.flightTime * if (isServeBall) {
                            swingType.getServeFlightTimeModifier(swingSettings)
                        } else {
                            swingType.getFlightTimeModifier(swingSettings)
                        }).toLong()
                        
                        // Get avatar resources for animation
                        val myAvatarResIdForAnim = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { 
                            com.air.pong.ui.AvatarUtils.avatarResources.first() 
                        }
                        val oppAvatarResIdForAnim = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { 
                            com.air.pong.ui.AvatarUtils.avatarResources.first() 
                        }
                        
                        // Animation container with fixed height (taller than rally mode since there's more screen space)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BallBounceAnimation(
                                gamePhase = gameState.gamePhase,
                                isMyTurn = gameState.isMyTurn,
                                lastSwingType = gameState.lastSwingType,
                                flightTimeMs = actualFlightTimeMs,
                                ballArrivalTimestamp = gameState.ballArrivalTimestamp,
                                myAvatarResId = myAvatarResIdForAnim,
                                partnerAvatarResId = oppAvatarResIdForAnim,
                                isServing = isServeBall,
                                modifier = Modifier.fillMaxSize().alpha(animationOpacity)
                            )
                        }
                        
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
                                // Classic mode: only show HIT! when my turn and bounced
                                if (gameState.isMyTurn && hasBounced) {
                                    Text(
                                        stringResource(R.string.hit),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                // No WAIT text - colors guide players
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
                    gameMode = gameState.gameMode
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
