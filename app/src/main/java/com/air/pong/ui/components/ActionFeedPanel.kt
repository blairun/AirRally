package com.air.pong.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.core.game.GameEvent
import com.air.pong.core.game.GameMode
import com.air.pong.core.game.SwingType

/**
 * Panel states for the collapsible action feed.
 */
enum class PanelState {
    COLLAPSED,
    HALF_EXPANDED,
    FULL_EXPANDED
}

/**
 * A collapsible panel at the bottom of the game screen showing recent events.
 * Supports drag gestures to expand/collapse.
 */
@Composable
fun ActionFeedPanel(
    events: List<GameEvent>,
    gameMode: GameMode = GameMode.CLASSIC,
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
                        EventItem(event, isMostRecent, gameMode)
                    }
                }
            }
            
            // --- Expanded Content Section ---
            AnimatedVisibility(
                visible = panelState != PanelState.COLLAPSED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // Determine height and content based on state
                val targetHeight = if (panelState == PanelState.HALF_EXPANDED) 150.dp else 400.dp
                val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "PanelHeight")
                
                val displayEvents = if (panelState == PanelState.HALF_EXPANDED) {
                    moreEvents.take(4) // Show 4 more items (Total 6 visible)
                } else {
                    moreEvents // Show all
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedHeight)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    userScrollEnabled = panelState == PanelState.FULL_EXPANDED
                ) {
                    items(displayEvents.size) { index ->
                        EventItem(displayEvents[index], isMostRecent = false, gameMode = gameMode)
                    }
                }
            }
            
            if (panelState == PanelState.COLLAPSED) {
                 Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * A single event item in the action feed.
 */
@Composable
fun EventItem(event: GameEvent, isMostRecent: Boolean, gameMode: GameMode = GameMode.CLASSIC) {
    val eventText = getEventText(event, gameMode)
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

/**
 * Converts a GameEvent to a human-readable localized string.
 */
@Composable
fun getEventText(event: GameEvent, gameMode: GameMode = GameMode.CLASSIC): String {
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
        is GameEvent.PointScored -> {
            // In Rally/Solo Rally modes, show "Life Lost!" instead of "Point to Opponent"
            if (gameMode == GameMode.RALLY || gameMode == GameMode.SOLO_RALLY) {
                stringResource(R.string.event_life_lost)
            } else {
                stringResource(R.string.event_point_to, if (event.isYou) stringResource(R.string.you) else stringResource(R.string.opponent))
            }
        }
        is GameEvent.RawMessage -> event.message
    }
}

/**
 * Gets the string resource ID for a SwingType.
 */
@Composable
fun getSwingTypeStringId(swingType: SwingType): Int {
    return when (swingType) {
        SwingType.SOFT_FLAT -> R.string.swing_soft_flat
        SwingType.MEDIUM_FLAT -> R.string.swing_medium_flat
        SwingType.HARD_FLAT -> R.string.swing_hard_flat
        SwingType.SOFT_LOB -> R.string.swing_soft_lob
        SwingType.MEDIUM_LOB -> R.string.swing_medium_lob
        SwingType.HARD_LOB -> R.string.swing_hard_lob
        SwingType.SOFT_SMASH -> R.string.swing_soft_smash
        SwingType.MEDIUM_SMASH -> R.string.swing_medium_smash
        SwingType.HARD_SMASH -> R.string.swing_hard_smash
    }
}
