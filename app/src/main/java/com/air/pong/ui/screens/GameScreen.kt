package com.air.pong.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.air.pong.ui.components.AvatarViewWithRing
import com.air.pong.ui.components.DebugGameControls
import com.air.pong.ui.components.PointsPopupWithGridIndex
import com.air.pong.ui.components.RallyGrid
import com.air.pong.ui.components.getAvatarState
import com.air.pong.ui.components.VsIntroOverlay
import com.air.pong.ui.components.ScrollingTipsDisplay
import com.air.pong.ui.components.MomentumBadge
import com.air.pong.ui.components.BallBounceAnimation
import com.air.pong.ui.components.SoloBallBounceAnimation
import com.air.pong.ui.components.CopyCatIndicator
import com.air.pong.ui.components.ShieldOverlay
import com.air.pong.ui.components.CopyCatIndicatorType
import com.air.pong.ui.components.BonusProgressIndicators
import com.air.pong.ui.components.BonusChipType
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
    
    // Auto-clear Copy Cat break flash after animation completes
    // The flash animation runs 3 pulses × 200ms each = 600ms total
    // We add 150ms buffer for the final hold + cleanup
    val isCopyCatBreakFlashing = remember(gameState.modeState) {
        val isSoloRally = gameState.gameMode == GameMode.SOLO_RALLY
        if (isSoloRally) {
            gameState.soloRallyState?.isCopyCatBreakFlashing ?: false
        } else {
            gameState.rallyState?.isCopyCatBreakFlashing ?: false
        }
    }
    
    LaunchedEffect(isCopyCatBreakFlashing) {
        if (isCopyCatBreakFlashing) {
            // Wait for animation to complete (3 pulses × 200ms + buffer)
            kotlinx.coroutines.delay(750)
            viewModel.clearCopyCatBreakFlash()
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
    
    // Bonus chip popup state - which chip's info popup is open (null = none)
    var showBonusPopup by remember { mutableStateOf<BonusChipType?>(null) }
    
    // Block local swings when popup is open
    LaunchedEffect(showBonusPopup) {
        viewModel.setInputBlocked(showBonusPopup != null)
    }
    
    // Auto-close popup when game phase changes to RALLY (partner served or player swung)
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.RALLY) {
            showBonusPopup = null
        }
    }
    
    // Bonus Info Dialog
    showBonusPopup?.let { chipType ->
        com.air.pong.ui.dialogs.BonusInfoDialog(
            chipType = chipType,
            onDismiss = { showBonusPopup = null }
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
                        // Row 1: Labels (LIVES | Dynamic Bonus Label | SCORE) - all same size
                        // Dynamic label only visible when total multiplier is active (> 1.0)
                        val currentTime = System.currentTimeMillis()
                        
                        // Calculate total multiplier for visibility check
                        val momentumMultiplier = com.air.pong.core.game.GameEngine.getMomentumMultiplier(gameState.currentRallyLength)
                        val effectiveSpinMultiplier = if (isSoloRally) {
                            val soloState = gameState.soloRallyState
                            val isSpinning = soloState?.isAvatarSpinningIndefinitely == true || 
                                (soloState?.avatarSpinEndTime ?: 0L) > currentTime
                            if (isSpinning) soloState?.spinMultiplierBonus ?: 0f else 0f
                        } else {
                            val rallyState = gameState.rallyState
                            val isSpinning = rallyState?.isAvatarSpinningIndefinitely == true || 
                                (rallyState?.avatarSpinEndTime ?: 0L) > currentTime
                            if (isSpinning) rallyState?.spinMultiplierBonus ?: 0f else 0f
                        }
                        val copyCatBonus = if (isSoloRally) {
                            gameState.soloRallyState?.copyCatMultiplierBonus ?: 0f
                        } else {
                            gameState.rallyState?.copyCatMultiplierBonus ?: 0f
                        }
                        val goldenBonus = if (isSoloRally) {
                            gameState.soloRallyState?.goldenMultiplierBonus ?: 0f
                        } else {
                            gameState.rallyState?.goldenMultiplierBonus ?: 0f
                        }
                        val totalMultiplier = momentumMultiplier + effectiveSpinMultiplier + copyCatBonus + goldenBonus
                        val streakActive = totalMultiplier > 1.0f
                        
                        // Get dynamic label from lastBonusType - shows most recent bonus earned
                        // Only SPIN/COPYCAT/GOLD affect the multiplier and should replace the label
                        val lastBonusType = if (isSoloRally) {
                            gameState.soloRallyState?.lastBonusType
                        } else {
                            gameState.rallyState?.lastBonusType
                        }
                        
                        // Get timestamp of last special bonus (SPIN/COPYCAT/GOLD)
                        val lastSpecialBonusTimestamp = if (isSoloRally) {
                            gameState.soloRallyState?.lastSpecialBonusTimestamp ?: 0L
                        } else {
                            gameState.rallyState?.lastSpecialBonusTimestamp ?: 0L
                        }
                        
                        // 10-second timeout: special bonuses only show for 10s after being earned
                        val specialBonusTimeoutMs = 10_000L
                        val timeSinceSpecialBonus = currentTime - lastSpecialBonusTimestamp
                        val isSpecialBonusFresh = lastSpecialBonusTimestamp > 0L && timeSinceSpecialBonus <= specialBonusTimeoutMs
                        
                        val streakLabelText = when {
                            // Special bonuses (SPIN/COPYCAT/GOLD) show only if earned within last 10 seconds
                            isSpecialBonusFresh && (lastBonusType == com.air.pong.core.game.BonusType.SPIN ||
                                lastBonusType == com.air.pong.core.game.BonusType.COPYCAT ||
                                lastBonusType == com.air.pong.core.game.BonusType.GOLD) -> lastBonusType!!.displayText
                            // After timeout or if no special bonus, show Rally! (generic streak indicator)
                            else -> "Rally!"
                        }
                        
                        // Animate label alpha to match MomentumBadge fade timings
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
                                streakLabelText,
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
                                    // Calculate pulsing color for low lives
                                    val pulseTransition = rememberInfiniteTransition(label = "livesPulse")
                                    val pulseColor by run {
                                        when (lives) {
                                            2 -> {
                                                // Pulse pink
                                                pulseTransition.animateColor(
                                                    initialValue = Color.White,
                                                    targetValue = Color(0xFFFF80AB), // Pink
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1000, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "livesColor"
                                                )
                                            }
                                            1 -> {
                                                // Pulse red (faster)
                                                pulseTransition.animateColor(
                                                    initialValue = Color.White,
                                                    targetValue = Color(0xFFFF5252), // Red
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(500, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "livesColor"
                                                )
                                            }
                                            else -> remember { mutableStateOf(Color.White) }
                                        }
                                    }

                                    Text(
                                        text = lives.toString(),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = pulseColor,
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
                                    AnimatedScoreCounter(
                                        targetScore = score,
                                        fontSize = fontSize
                                    )
                                }
                            }
                            
                            // MomentumBadge as overlay in center (can overflow below)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                // Calculate effective spin multiplier (only active when avatar spinning)
                                val currentTime = System.currentTimeMillis()
                                val effectiveSpinMultiplier = if (isSoloRally) {
                                    val soloState = gameState.soloRallyState
                                    val isSpinning = soloState?.isAvatarSpinningIndefinitely == true || 
                                        (soloState?.avatarSpinEndTime ?: 0L) > currentTime
                                    if (isSpinning) soloState?.spinMultiplierBonus ?: 0f else 0f
                                } else {
                                    val rallyState = gameState.rallyState
                                    val isSpinning = rallyState?.isAvatarSpinningIndefinitely == true || 
                                        (rallyState?.avatarSpinEndTime ?: 0L) > currentTime
                                    if (isSpinning) rallyState?.spinMultiplierBonus ?: 0f else 0f
                                }
                                // Copy Cat bonus (always active, ratchets up)
                                val copyCatBonus = if (isSoloRally) {
                                    gameState.soloRallyState?.copyCatMultiplierBonus ?: 0f
                                } else {
                                    gameState.rallyState?.copyCatMultiplierBonus ?: 0f
                                }
                                // Golden bonus (persistent from golden line clears)
                                val goldenBonus = if (isSoloRally) {
                                    gameState.soloRallyState?.goldenMultiplierBonus ?: 0f
                                } else {
                                    gameState.rallyState?.goldenMultiplierBonus ?: 0f
                                }
                                // Allow clicking during stoppage (same rules as chips)
                                val isMomentumClickable = gameState.gamePhase == GamePhase.WAITING_FOR_SERVE || 
                                                          gameState.gamePhase == GamePhase.POINT_SCORED
                                
                                MomentumBadge(
                                    rallyLength = gameState.currentRallyLength,
                                    spinMultiplier = effectiveSpinMultiplier + copyCatBonus + goldenBonus,
                                    enabled = isMomentumClickable,
                                    onClick = { showBonusPopup = BonusChipType.RALLY }
                                )
                            }
                        }
                    }
                    
                    // Avatar display: Single centered avatar for Solo, overlapping pair for Co-op
                    val myAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(myAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                    
                    // Calculate bonus indicator states
                    val currentTime = System.currentTimeMillis()
                    val bonusIsSpinning = if (isSoloRally) {
                        val soloState = gameState.soloRallyState
                        soloState?.isAvatarSpinningIndefinitely == true || 
                            (soloState?.avatarSpinEndTime ?: 0L) > currentTime
                    } else {
                        val rallyState = gameState.rallyState
                        rallyState?.isAvatarSpinningIndefinitely == true || 
                            (rallyState?.avatarSpinEndTime ?: 0L) > currentTime
                    }
                    val bonusSpinCount = if (isSoloRally) {
                        gameState.soloRallyState?.distinctSpinTypes?.size ?: 0
                    } else {
                        gameState.rallyState?.distinctSpinTypes?.size ?: 0
                    }
                    val bonusInfiniteSpin = if (isSoloRally) {
                        gameState.soloRallyState?.isAvatarSpinningIndefinitely ?: false
                    } else {
                        gameState.rallyState?.isAvatarSpinningIndefinitely ?: false
                    }
                    
                    // Copy cat state
                    val bonusCopyProgress = if (isSoloRally) {
                        // Solo: copyCatProgress is 0-8 for sequence index, add 1 for human count
                        val progress = gameState.soloRallyState?.copyCatProgress ?: -1
                        if (progress >= 0) progress + 1 else 0
                    } else {
                        // Co-op: copyCount is the number of successful copies
                        gameState.rallyState?.copyCount ?: 0
                    }
                    val bonusCopyTierAchieved = if (isSoloRally) {
                        gameState.soloRallyState?.copyCatTierAchieved ?: 0
                    } else {
                        gameState.rallyState?.copyCatTierAchieved ?: 0
                    }
                    val bonusSequenceActive = if (isSoloRally) {
                        (gameState.soloRallyState?.copyCatProgress ?: -1) >= 0
                    } else {
                        (gameState.rallyState?.copySequenceStartIndex ?: -1) >= 0
                    }
                    
                    // Golden state: convert multiplier to tier (0.1 = tier 1, 0.5 = tier 5)
                    val goldenBonus = if (isSoloRally) {
                        gameState.soloRallyState?.goldenMultiplierBonus ?: 0f
                    } else {
                        gameState.rallyState?.goldenMultiplierBonus ?: 0f
                    }
                    val bonusGoldenTier = (goldenBonus / 0.1f).toInt().coerceIn(0, 5)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Avatar in center (unchanged layout)
                        if (isSoloRally) {
                            // Solo Rally: Single avatar with spin animation and optional shield ring
                            val soloState = gameState.soloRallyState
                            val soloHasShield = soloState?.hasShield ?: false
                            
                            // Track current time for spin animation
                            val soloCurrentTime = remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    soloCurrentTime.value = System.currentTimeMillis()
                                    kotlinx.coroutines.delay(100)
                                }
                            }
                            
                            // Determine if avatar should be spinning
                            val isSoloAvatarSpinning = soloState?.isAvatarSpinningIndefinitely == true || 
                                (soloState?.avatarSpinEndTime ?: 0L) > soloCurrentTime.value
                            val soloSpinTypeCount = soloState?.distinctSpinTypes?.size ?: 0
                            
                            // Spin speed increases with more spin types: base 3s per rotation, down to 0.5s at 8 types
                            val spinDurationMs = if (soloSpinTypeCount > 0) {
                                (3000 - (soloSpinTypeCount * 300)).coerceAtLeast(500)
                            } else 3000
                            
                            // Spin rotation animation using Animatable
                            val soloRotation = remember { Animatable(0f) }
                            LaunchedEffect(isSoloAvatarSpinning, spinDurationMs) {
                                if (isSoloAvatarSpinning) {
                                    while (true) {
                                        soloRotation.animateTo(
                                            targetValue = soloRotation.value + 360f,
                                            animationSpec = tween(durationMillis = spinDurationMs, easing = LinearEasing)
                                        )
                                    }
                                } else {
                                    soloRotation.snapTo(0f)
                                }
                            }
                            
                            // Get ring resource for solo rally
                            val myRingIndex by viewModel.ringIndex.collectAsState()
                            val myRingResId = if (myRingIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(myRingIndex)?.resId else null
                            
                            // Dynamically shrink avatar when ring present to maintain consistent overall size
                            val baseSize = 100.dp
                            val hasRing = myRingResId != null
                            val avatarSize = if (hasRing) {
                                baseSize / com.air.pong.ui.RingUtils.RING_SIZE_RATIO
                            } else {
                                baseSize
                            }
                            val ringSize = baseSize // Ring always fills the base size space
                            val shieldSize = avatarSize + 16.dp // Slightly larger for overlay
                            
                            Box(contentAlignment = Alignment.Center) {
                                // Ring layer (behind shield and avatar, does NOT rotate)
                                if (myRingResId != null) {
                                    Image(
                                        painter = painterResource(id = myRingResId),
                                        contentDescription = "Background ring",
                                        modifier = Modifier.size(ringSize),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                // Avatar image with spin animation
                                Image(
                                    painter = painterResource(id = myAvatarResId),
                                    contentDescription = "Your Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .graphicsLayer { rotationZ = soloRotation.value }
                                        .clip(CircleShape)
                                        .border(5.dp, Color.White, CircleShape)
                                )
                                // Shield overlay (rendered ON TOP of avatar for force field effect)
                                if (soloHasShield) {
                                    ShieldOverlay(
                                        size = shieldSize,
                                        rotation = soloRotation.value
                                    )
                                }
                            }

                        } else {
                            // Co-op Rally: Overlapping avatars
                            val oppAvatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(opponentAvatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.first() }
                            val rallyState = gameState.rallyState
                            
                            // Get ring resources
                            val myRingIndex by viewModel.ringIndex.collectAsState()
                            val myRingResId = if (myRingIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(myRingIndex)?.resId else null
                            val oppRingIndex by viewModel.opponentRingIndex.collectAsState()
                            val oppRingResId = if (oppRingIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(oppRingIndex)?.resId else null
                            
                            com.air.pong.ui.components.RallyAvatarPair(
                                myAvatarResId = myAvatarResId,
                                partnerAvatarResId = oppAvatarResId,
                                isMyTurn = gameState.isMyTurn,
                                mySpinTypeCount = rallyState?.distinctSpinTypes?.size ?: 0,
                                myAvatarSpinEndTime = rallyState?.avatarSpinEndTime ?: 0L,
                                myIsSpinningIndefinitely = rallyState?.isAvatarSpinningIndefinitely ?: false,
                                partnerSpinTypeCount = rallyState?.partnerDistinctSpinTypes?.size ?: 0,
                                partnerAvatarSpinEndTime = rallyState?.partnerAvatarSpinEndTime ?: 0L,
                                partnerIsSpinningIndefinitely = rallyState?.isPartnerSpinningIndefinitely ?: false,
                                myHasShield = rallyState?.hasShield ?: false,
                                myRingResId = myRingResId,
                                partnerRingResId = oppRingResId
                            )
                        }
                        
                        // Bonus progress indicators as overlay (top-start, won't affect centering)
                        val bonusPowerOutagesCleared = if (isSoloRally) {
                            gameState.soloRallyState?.powerOutagesClearedCount ?: 0
                        } else {
                            gameState.rallyState?.powerOutagesClearedCount ?: 0
                        }
                        
                        // Additional state for new chip parameters
                        val bonusGridUpgradesEarned = if (isSoloRally) {
                            gameState.soloRallyState?.gridUpgradesEarned ?: 0
                        } else {
                            gameState.rallyState?.gridUpgradesEarned ?: 0
                        }
                        val bonusCopyCat9Completions = if (isSoloRally) {
                            gameState.soloRallyState?.copyCat9Completions ?: 0
                        } else {
                            gameState.rallyState?.copyCat9Completions ?: 0
                        }
                        
                        // Determine if chips should be clickable (only during game stoppage)
                        val isChipsClickable = gameState.gamePhase == GamePhase.WAITING_FOR_SERVE || 
                            gameState.gamePhase == GamePhase.POINT_SCORED
                        
                        // Determine if actively rallying (for rally chip display)
                        val isActivelyRallying = gameState.gamePhase == GamePhase.RALLY
                        
                        BonusProgressIndicators(
                            // Rally state
                            currentRallyLength = gameState.currentRallyLength,
                            longestRally = gameState.longestRally,
                            isActivelyRallying = isActivelyRallying,
                            
                            // Grid upgrades
                            gridUpgradesEarned = bonusGridUpgradesEarned,
                            
                            // Spin state
                            isSpinning = bonusIsSpinning,
                            spinCount = bonusSpinCount,
                            isInfiniteSpin = bonusInfiniteSpin,
                            
                            // Copy cat state
                            copyProgress = bonusCopyProgress,
                            copyTierAchieved = bonusCopyTierAchieved,
                            isSequenceActive = bonusSequenceActive,
                            copyCat9Completions = bonusCopyCat9Completions,
                            
                            // Golden state
                            goldenTier = bonusGoldenTier,
                            
                            // Power Outage state
                            powerOutagesClearedCount = bonusPowerOutagesCleared,
                            
                            // Clickability
                            isClickable = isChipsClickable,
                            onChipClick = { chipType -> showBonusPopup = chipType },
                            
                            modifier = Modifier.align(Alignment.TopStart)
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

                            val myRingResId = run {
                                val ringIdx = viewModel.ringIndex.collectAsState().value
                                if (ringIdx >= 0) com.air.pong.ui.RingUtils.getRingByIndex(ringIdx)?.resId else null
                            }
                            
                            // Get opponent ring ahead of time to determine if either has a ring
                            val oppRingResId = run {
                                val ringIdx = viewModel.opponentRingIndex.collectAsState().value
                                if (ringIdx >= 0) com.air.pong.ui.RingUtils.getRingByIndex(ringIdx)?.resId else null
                            }
                            
                            // Determine if either player has a ring to force consistent sizing
                            val eitherHasRing = myRingResId != null || oppRingResId != null
                            
                            AvatarViewWithRing(
                                avatarResId = myAvatarResId,
                                ringResId = myRingResId,
                                outlineColor = myOutlineColor,
                                animationType = myAnimation,
                                avatarSize = 100.dp,
                                forceRingSizing = eitherHasRing
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

                            // Get ring resources - use cached values for eitherHasRing
                            val myRingResId = run {
                                val ringIdx = viewModel.ringIndex.collectAsState().value
                                if (ringIdx >= 0) com.air.pong.ui.RingUtils.getRingByIndex(ringIdx)?.resId else null
                            }
                            val oppRingResId = run {
                                val ringIdx = viewModel.opponentRingIndex.collectAsState().value
                                if (ringIdx >= 0) com.air.pong.ui.RingUtils.getRingByIndex(ringIdx)?.resId else null
                            }
                            val eitherHasRing = myRingResId != null || oppRingResId != null
                            
                            AvatarViewWithRing(
                                avatarResId = oppAvatarResId,
                                ringResId = oppRingResId,
                                outlineColor = oppOutlineColor,
                                animationType = oppAnimation,
                                avatarSize = 100.dp,
                                forceRingSizing = eitherHasRing
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
                    
                    // Calculate total multiplier for ball animation effects
                    val ballAnimCurrentTime = System.currentTimeMillis()
                    val ballAnimMomentum = com.air.pong.core.game.GameEngine.getMomentumMultiplier(gameState.currentRallyLength)
                    val ballAnimSpinBonus = if (isSoloRally) {
                        val soloState = gameState.soloRallyState
                        val isSpinning = soloState?.isAvatarSpinningIndefinitely == true || 
                            (soloState?.avatarSpinEndTime ?: 0L) > ballAnimCurrentTime
                        if (isSpinning) soloState?.spinMultiplierBonus ?: 0f else 0f
                    } else {
                        val rallyState = gameState.rallyState
                        val isSpinning = rallyState?.isAvatarSpinningIndefinitely == true || 
                            (rallyState?.avatarSpinEndTime ?: 0L) > ballAnimCurrentTime
                        if (isSpinning) rallyState?.spinMultiplierBonus ?: 0f else 0f
                    }
                    val ballAnimCopyCatBonus = if (isSoloRally) {
                        gameState.soloRallyState?.copyCatMultiplierBonus ?: 0f
                    } else {
                        gameState.rallyState?.copyCatMultiplierBonus ?: 0f
                    }
                    val ballAnimGoldenBonus = if (isSoloRally) {
                        gameState.soloRallyState?.goldenMultiplierBonus ?: 0f
                    } else {
                        gameState.rallyState?.goldenMultiplierBonus ?: 0f
                    }
                    val ballTotalMultiplier = ballAnimMomentum + ballAnimSpinBonus + ballAnimCopyCatBonus + ballAnimGoldenBonus
                    
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
                                modifier = Modifier.fillMaxSize().alpha(animationOpacity),
                                totalMultiplier = ballTotalMultiplier
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
                                modifier = Modifier.fillMaxSize().alpha(animationOpacity),
                                totalMultiplier = ballTotalMultiplier
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
                    
                    // Trigger popup when points change (positive for hits, negative for banana)
                    LaunchedEffect(scorePoints, currentScore) {
                        if (scorePoints != 0) {
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
                        // Build Copy Cat indicators map
                        val copyCatIndicators = remember(gameState.modeState) {
                            buildCopyCatIndicators(gameState, isSoloRally)
                        }
                        
                        // Get special squares from state
                        val specialSquares = if (isSoloRally) {
                            gameState.soloRallyState?.specialSquares ?: emptyMap()
                        } else {
                            gameState.rallyState?.specialSquares ?: emptyMap()
                        }
                        
                        // Get power outage state
                        val cellHitCounts = if (isSoloRally) {
                            gameState.soloRallyState?.cellHitCounts ?: List(9) { 0 }
                        } else {
                            gameState.rallyState?.cellHitCounts ?: List(9) { 0 }
                        }
                        val isPowerOutage = if (isSoloRally) {
                            gameState.soloRallyState?.isPowerOutage ?: false
                        } else {
                            gameState.rallyState?.isPowerOutage ?: false
                        }
                        val outageRecoveryUniqueCells = if (isSoloRally) {
                            gameState.soloRallyState?.outageRecoveryUniqueCells ?: emptySet()
                        } else {
                            gameState.rallyState?.outageRecoveryUniqueCells ?: emptySet()
                        }
                        
                        RallyGrid(
                            gridState = gridState,
                            cellTiers = cellTiers,
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    gridWidth = coordinates.size.width.toFloat()
                                    gridHeight = coordinates.size.height.toFloat()
                                },
                            // Tap/swipe-to-hit accessibility feature
                            isTapToHitEnabled = viewModel.isTapToHitEnabled.collectAsState().value,
                            isPlayerTurn = gameState.isMyTurn || gameState.gamePhase == GamePhase.WAITING_FOR_SERVE,
                            onCellHit = { swingType, spinType -> viewModel.triggerTapToHit(swingType, spinType) },
                            copyCatIndicators = copyCatIndicators,
                            specialSquares = specialSquares,
                            // Power outage state
                            cellHitCounts = cellHitCounts,
                            isPowerOutage = isPowerOutage,
                            outageRecoveryUniqueCells = outageRecoveryUniqueCells
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
                        
                        // Power Outage Dark Overlay
                        // Shows during power outage to black out most UI, with recovery message above grid
                        val outageOverlayAlpha by animateFloatAsState(
                            targetValue = if (isPowerOutage) 0.92f else 0f,
                            animationSpec = tween(durationMillis = 300),
                            label = "outageOverlay"
                        )
                        
                        if (outageOverlayAlpha > 0f) {
                            // Recovery progress values
                            val recoveryCount = outageRecoveryUniqueCells.size
                            val recoveryNeeded = com.air.pong.core.game.PowerOutageConstants.RECOVERY_UNIQUE_CELLS
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = outageOverlayAlpha))
                                    .zIndex(100f)
                            ) {
                                // Text content at top, above the grid
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.power_outage_title),
                                        fontSize = 24.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Light bulb progress indicator - fixed width row
                                    Row(
                                        modifier = Modifier.width(200.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(recoveryNeeded) { index ->
                                            val isLit = index < recoveryCount
                                            Text(
                                                text = if (isLit) "💡" else "🔌",
                                                fontSize = 28.sp,
                                                modifier = Modifier.width(36.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Fixed-height container to prevent jumping
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.power_outage_hit_different, recoveryNeeded),
                                                fontSize = 16.sp,
                                                color = Color.White.copy(alpha = 0.9f),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = stringResource(R.string.power_outage_progress, recoveryCount, recoveryNeeded),
                                                fontSize = 20.sp,
                                                color = if (recoveryCount > 0) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f),
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
            
            // Get ring resources
            val myRingIndex by viewModel.ringIndex.collectAsState()
            val myRingResId = if (myRingIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(myRingIndex)?.resId else null
            val oppRingIndex by viewModel.opponentRingIndex.collectAsState()
            val oppRingResId = if (oppRingIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(oppRingIndex)?.resId else null
            
            VsIntroOverlay(
                myAvatarResId = myAvatarResId,
                opponentAvatarResId = oppAvatarResId,
                myRingResId = myRingResId,
                opponentRingResId = oppRingResId,
                onPlayVsSound = { viewModel.playVsSound() },
                onComplete = { showVsIntro = false }
            )
        }
    }
}

/**
 * Build Copy Cat indicator map from game state.
 * Maps grid indices (0-8) to their CopyCatIndicator if any.
 */
private fun buildCopyCatIndicators(
    gameState: com.air.pong.core.game.GameState,
    isSoloRally: Boolean
): Map<Int, CopyCatIndicator> {
    val indicators = mutableMapOf<Int, CopyCatIndicator>()
    
    if (isSoloRally) {
        // Solo mode: CPU sequence challenge
        val soloState = gameState.soloRallyState ?: return emptyMap()
        
        // Pattern break flash
        if (soloState.isCopyCatBreakFlashing) {
            soloState.copyCatBreakCells.forEach { gridIndex ->
                indicators[gridIndex] = CopyCatIndicator(-1, CopyCatIndicatorType.BREAK_FLASH)
            }
            return indicators
        }
        
        // Show 🐱 on completed cells (cells player has hit correctly)
        soloState.copyCatCompletedCells.forEach { (gridIndex, number) ->
            indicators[gridIndex] = CopyCatIndicator(number, CopyCatIndicatorType.FOLLOWER_CAT)
        }
        
        // Show 🎯 on next target cell (if sequence is active)
        if (soloState.copyCatProgress >= 0 && soloState.copyCatSequence.isNotEmpty()) {
            val nextTarget = soloState.copyCatSequence.getOrNull(soloState.copyCatProgress) ?: -1
            if (nextTarget >= 0) {
                indicators[nextTarget] = CopyCatIndicator(0, CopyCatIndicatorType.NEXT_EXPECTED)
            }
        }
    } else {
        // Co-op mode: show 🐾 for leader cells, 🐱 for follower cells
        val rallyState = gameState.rallyState ?: return emptyMap()
        
        // Pattern break flash
        if (rallyState.isCopyCatBreakFlashing) {
            val cellsToFlash = if (rallyState.isLocalPlayerLeader) {
                rallyState.copyCatLeaderCells
            } else {
                rallyState.copyCatFollowerCells
            }
            cellsToFlash.forEach { (gridIndex, _) ->
                indicators[gridIndex] = CopyCatIndicator(-1, CopyCatIndicatorType.BREAK_FLASH)
            }
            return indicators
        }
        
        // Normal display
        if (rallyState.isLocalPlayerLeader) {
            // Local player is leader - show 🐾 on their cells
            rallyState.copyCatLeaderCells.forEach { (gridIndex, number) ->
                indicators[gridIndex] = CopyCatIndicator(number, CopyCatIndicatorType.LEADER_PAW)
            }
        } else if (rallyState.copySequenceStartIndex >= 0) {
            // Local player is follower - show 🐱 on their copied cells
            rallyState.copyCatFollowerCells.forEach { (gridIndex, number) ->
                indicators[gridIndex] = CopyCatIndicator(number, CopyCatIndicatorType.FOLLOWER_CAT)
            }
            
            // Show X🐱 on next expected cell
            if (rallyState.copyCatNextExpectedCell >= 0) {
                indicators[rallyState.copyCatNextExpectedCell] = CopyCatIndicator(
                    0, CopyCatIndicatorType.NEXT_EXPECTED
                )
            }
        }
    }
    
    return indicators
}
