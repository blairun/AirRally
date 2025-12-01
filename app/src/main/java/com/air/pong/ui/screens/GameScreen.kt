package com.air.pong.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.R
import com.air.pong.core.game.GameEvent
import com.air.pong.core.game.GamePhase
import com.air.pong.core.game.SwingType
import com.air.pong.ui.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit,
    onStopDebug: () -> Unit = {}
) {
    com.air.pong.ui.components.KeepScreenOn()

    val gameState by viewModel.gameState.collectAsState()
    
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.GAME_OVER) {
            if (gameState.isDebugMode) {
                // In Debug Mode, we don't go to GameOver screen.
                // We just stop the game (reset to IDLE) and go back to Debug Menu.
                viewModel.stopDebugGame()
                onStopDebug()
            } else {
                onGameOver()
            }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val minHeight = maxHeight
        
        // Main Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // Add padding for the bottom panel
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween 
        ) {
            // Ensure the column takes up at least the full screen height
            // so SpaceBetween works even when content is small
            Column(
                modifier = Modifier.heightIn(min = minHeight - 132.dp), // Subtract padding + bottom panel buffer
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Top Section: Score ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center, // Center the row content
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val opponentName by viewModel.connectedPlayerName.collectAsState()
                    
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
                    }
                    
                    // Small spacer for visual separation between the two weighted columns
                    Spacer(modifier = Modifier.width(16.dp))
                    
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
                    }
                }
                
                // --- Middle Section: Status Message ---
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
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                stringResource(R.string.swing_to_serve),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                stringResource(R.string.opponent_serving),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
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
                            // Only show HIT! if the ball has bounced (Green state)
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
                
                // --- Debug Overlay (Optional) ---
                if (gameState.isDebugMode) {
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
                                    Text(stringResource(R.string.force_fmt, it.force), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.grav_z_fmt, "%.2f".format(it.gravZ)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp)) // Placeholder
                }
            }
        }
        
        // Action Feed Panel (Bottom Sheet)
        ActionFeedPanel(
            events = gameState.eventLog,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Debug Game Controls
        if (viewModel.isDebugGameSession) {
            DebugGameControls(
                viewModel = viewModel,
                onStopDebug = onStopDebug,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp)
            )
        }
    }
}

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
            Text("Debug Controls", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                    Text("Simulate\nMy Hit", fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }

                // Auto-Play (Center)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto-Play", color = Color.White, style = MaterialTheme.typography.labelSmall)
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
                    Text("Simulate\nOpp Hit", fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    viewModel.stopDebugGame() 
                    onStopDebug()
                },
                modifier = Modifier.fillMaxWidth(0.5f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop Game")
            }
        }
    }
}

enum class PanelState {
    COLLAPSED,
    HALF_EXPANDED,
    FULL_EXPANDED
}

@Composable
fun ActionFeedPanel(
    events: List<GameEvent>,
    modifier: Modifier = Modifier
) {
    var panelState by remember { mutableStateOf(PanelState.COLLAPSED) }
    var hasChangedState by remember { mutableStateOf(false) }
    
    // Reverse events to show newest first
    val reversedEvents = remember(events) { events.reversed() }
    
    // Split into always visible and expandable
    val topEvents = reversedEvents.take(2)
    val moreEvents = reversedEvents.drop(2) 

    // Gesture Detector for Dragging (Applied to Header)
    val gestureModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { hasChangedState = false },
            onDragEnd = { hasChangedState = false },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (!hasChangedState) {
                    val threshold = 20f // Sensitivity threshold
                    if (dragAmount < -threshold) { // Dragging Up
                        when (panelState) {
                            PanelState.COLLAPSED -> {
                                panelState = PanelState.HALF_EXPANDED
                                hasChangedState = true
                            }
                            PanelState.HALF_EXPANDED -> {
                                panelState = PanelState.FULL_EXPANDED
                                hasChangedState = true
                            }
                            else -> {}
                        }
                    } else if (dragAmount > threshold) { // Dragging Down
                        when (panelState) {
                            PanelState.FULL_EXPANDED -> {
                                panelState = PanelState.HALF_EXPANDED
                                hasChangedState = true
                            }
                            PanelState.HALF_EXPANDED -> {
                                panelState = PanelState.COLLAPSED
                                hasChangedState = true
                            }
                            else -> {}
                        }
                    }
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Header Section (Draggable) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(gestureModifier) // Drag gesture works here
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle Bar
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (reversedEvents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_events_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // Render top events (always visible)
                    topEvents.forEachIndexed { index, event ->
                        val isMostRecent = index == 0
                        EventItem(event, isMostRecent)
                    }
                }
            }
            
            // --- Expanded Content Section ---
            androidx.compose.animation.AnimatedVisibility(
                visible = panelState != PanelState.COLLAPSED,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                // Determine height and content based on state
                val targetHeight = if (panelState == PanelState.HALF_EXPANDED) 150.dp else 400.dp
                val animatedHeight by androidx.compose.animation.core.animateDpAsState(targetValue = targetHeight, label = "PanelHeight")
                
                val displayEvents = if (panelState == PanelState.HALF_EXPANDED) {
                    moreEvents.take(4) // Show 4 more items (Total 6 visible)
                } else {
                    moreEvents // Show all
                }
                
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedHeight)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    userScrollEnabled = panelState == PanelState.FULL_EXPANDED
                ) {
                    items(displayEvents.size) { index ->
                        EventItem(displayEvents[index], isMostRecent = false)
                    }
                }
            }
            
            if (panelState == PanelState.COLLAPSED) {
                 Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun EventItem(event: GameEvent, isMostRecent: Boolean) {
    val eventText = getEventText(event)
    Text(
        text = eventText,
        style = if (isMostRecent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = if (isMostRecent) Color.White else Color.White.copy(alpha = 0.7f),
        fontWeight = if (isMostRecent) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
fun getEventText(event: GameEvent): String {
    return when (event) {
        is GameEvent.YouServed -> stringResource(R.string.event_you_served, stringResource(getSwingTypeStringId(event.swingType)))
        is GameEvent.YouHit -> stringResource(R.string.event_you_hit, stringResource(getSwingTypeStringId(event.swingType)))
        is GameEvent.OpponentHit -> stringResource(R.string.event_opponent_hit, stringResource(getSwingTypeStringId(event.swingType)))
        is GameEvent.BallBounced -> stringResource(R.string.event_ball_bounced)
        is GameEvent.FaultNet -> stringResource(R.string.event_fault_net)
        is GameEvent.FaultOut -> stringResource(R.string.event_fault_out)
        is GameEvent.HitNet -> stringResource(R.string.event_hit_net, stringResource(getSwingTypeStringId(event.swingType)))
        is GameEvent.HitOut -> stringResource(R.string.event_hit_out, stringResource(getSwingTypeStringId(event.swingType)))
        is GameEvent.WhiffEarly -> stringResource(R.string.event_whiff_early)
        is GameEvent.MissLate -> stringResource(R.string.event_miss_late)
        is GameEvent.MissNoSwing -> stringResource(R.string.event_miss_no_swing)
        is GameEvent.OpponentNet -> stringResource(R.string.event_opp_net)
        is GameEvent.OpponentOut -> stringResource(R.string.event_opp_out)
        is GameEvent.OpponentWhiff -> stringResource(R.string.event_opp_whiff)
        is GameEvent.OpponentMissNoSwing -> stringResource(R.string.event_opp_miss_no_swing)
        is GameEvent.OpponentMiss -> stringResource(R.string.event_opp_miss)
        is GameEvent.PointScored -> stringResource(R.string.event_point_to, if (event.isYou) stringResource(R.string.you) else stringResource(R.string.opponent))
        is GameEvent.RawMessage -> event.message
    }
}

@Composable
fun getSwingTypeStringId(swingType: SwingType): Int {
    return when (swingType) {
        SwingType.SOFT_FLAT -> R.string.swing_soft_flat
        SwingType.MEDIUM_FLAT -> R.string.swing_medium_flat
        SwingType.HARD_FLAT -> R.string.swing_hard_flat
        SwingType.SOFT_LOB -> R.string.swing_soft_lob
        SwingType.MEDIUM_LOB -> R.string.swing_medium_lob
        SwingType.HARD_LOB -> R.string.swing_hard_lob
        SwingType.SOFT_SPIKE -> R.string.swing_soft_spike
        SwingType.MEDIUM_SPIKE -> R.string.swing_medium_spike
        SwingType.HARD_SPIKE -> R.string.swing_hard_spike
    }
}
