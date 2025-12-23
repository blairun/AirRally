package com.air.pong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.air.pong.core.game.SwingType
import com.air.pong.data.db.PointEntity
import com.air.pong.ui.AvatarUtils
import com.air.pong.ui.RingUtils
import com.air.pong.ui.GameViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: GameViewModel
) {
    val allPoints by viewModel.gameStats.collectAsState(initial = emptyList())
    val winCount by viewModel.winCount.collectAsState(initial = 0)
    val lossCount by viewModel.lossCount.collectAsState(initial = 0)
    val longestRally by viewModel.longestRally.collectAsState(initial = 0)
    val totalHits by viewModel.totalHits.collectAsState(initial = 0)
    val rallyHighScore by viewModel.rallyHighScore.collectAsState()
    val rallyLongestRally by viewModel.rallyLongestRally.collectAsState()

    val rallyTotalLinesCleared by viewModel.rallyTotalLinesCleared.collectAsState()
    val rallyHighestGridLevel by viewModel.rallyHighestGridLevel.collectAsState()

    // Solo Rally Stats
    val soloHighScore by viewModel.soloHighScore.collectAsState()
    val soloLongestRally by viewModel.soloLongestRally.collectAsState()
    val soloTotalLinesCleared by viewModel.soloTotalLinesCleared.collectAsState()
    val soloHighestGridLevel by viewModel.soloHighestGridLevel.collectAsState()

    var filter by remember { mutableStateOf(StatsFilter.ALL) }
    
    // Compute Heatmap
    var heatmap by remember { mutableStateOf<Map<SwingType, Int>>(emptyMap()) }
    
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allPoints, filter) {
        withContext(Dispatchers.Default) {
            val gson = Gson()
            val type = object : TypeToken<List<SwingType>>() {}.type
            
            val counts = mutableMapOf<SwingType, Int>()
            
            allPoints.forEach { point ->
                try {
                    val shots: List<SwingType> = gson.fromJson(point.myShotsJson, type)
                    
                    when (filter) {
                        StatsFilter.ALL -> {
                            shots.forEach { swing ->
                                counts[swing] = counts.getOrDefault(swing, 0) + 1
                            }
                        }
                        StatsFilter.WON -> {
                            // Classic Mode only: opponentName is set for multiplayer games
                            // Exclude Rally/Solo Rally which don't have traditional "wins"
                            if (point.opponentName.isNotEmpty() && point.didIWin) {
                                // Winning Shot = The last shot I made that wasn't returned
                                shots.lastOrNull()?.let { swing ->
                                    counts[swing] = counts.getOrDefault(swing, 0) + 1
                                }
                            }
                        }
                        StatsFilter.LOST -> {
                            // Classic Mode only: opponentName is set for multiplayer games
                            // Only include connected shots (NET/OUT), exclude WHIFF/TIMEOUT
                            if (point.opponentName.isNotEmpty() && !point.didIWin) {
                                // Losing Shot = Shot I hit that missed (NET or OUT only)
                                // Exclude WHIFF (didn't connect) and TIMEOUT (no swing)
                                val isConnectedMiss = point.endReason == "NET" || point.endReason == "OUT"
                                if (isConnectedMiss) {
                                    shots.lastOrNull()?.let { swing ->
                                        counts[swing] = counts.getOrDefault(swing, 0) + 1
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
            heatmap = counts
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.stats_reset_title)) },
            text = { Text(stringResource(R.string.stats_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetStats()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.stats_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.stats_reset_cancel))
                }
            }
        )
    }

    val NextUnlockWidth = 90.dp
    val ValueWidth = 90.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 2-Column Stats Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Total Hits (All Modes)
                StatRow(
                    label = stringResource(R.string.stats_total_hits),
                    value = "$totalHits",
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Rally Mode Section Header
                StatsSectionHeader(
                    title = stringResource(R.string.stats_rally_mode),
                    nextUnlockText = stringResource(R.string.stats_next_unlock),
                    nextUnlockWidth = NextUnlockWidth,
                    valueWidth = ValueWidth
                )
                
                StatRow(
                    label = stringResource(R.string.stats_rally_high_score),
                    value = "$rallyHighScore",
                    nextUnlock = getNextUnlockInfo(rallyHighScore, RingUtils.RALLY_SCORE_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_longest_rally),
                    value = "$rallyLongestRally",
                    nextUnlock = getNextUnlockInfo(rallyLongestRally, AvatarUtils.RALLY_LENGTH_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_lines_cleared),
                    value = "$rallyTotalLinesCleared",
                    nextUnlock = getNextUnlockInfo(rallyTotalLinesCleared, AvatarUtils.RALLY_LINES_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_highest_level),
                    value = "${com.air.pong.core.game.GameEngine.getPointsForTier(rallyHighestGridLevel)}pt",
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Solo Rally Mode Section Header
                StatsSectionHeader(
                    title = stringResource(R.string.stats_solo_rally_mode),
                    nextUnlockText = stringResource(R.string.stats_next_unlock),
                    nextUnlockWidth = NextUnlockWidth,
                    valueWidth = ValueWidth
                )
                
                StatRow(
                    label = stringResource(R.string.stats_rally_high_score),
                    value = "$soloHighScore",
                    nextUnlock = getNextUnlockInfo(soloHighScore, RingUtils.SOLO_SCORE_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_longest_rally),
                    value = "$soloLongestRally",
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_lines_cleared),
                    value = "$soloTotalLinesCleared",
                    nextUnlock = getNextUnlockInfo(soloTotalLinesCleared, AvatarUtils.SOLO_LINES_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_rally_highest_level),
                    value = "${com.air.pong.core.game.GameEngine.getPointsForTier(soloHighestGridLevel)}pt",
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Classic Mode Section Header
                StatsSectionHeader(
                    title = stringResource(R.string.stats_classic_mode),
                    nextUnlockText = stringResource(R.string.stats_next_unlock),
                    nextUnlockWidth = NextUnlockWidth,
                    valueWidth = ValueWidth
                )
                
                StatRow(
                    label = stringResource(R.string.stats_wins) + " / " + stringResource(R.string.stats_losses),
                    value = "$winCount / $lossCount",
                    nextUnlock = getNextUnlockInfo(winCount ?: 0, AvatarUtils.CLASSIC_WINS_THRESHOLDS),
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
                StatRow(
                    label = stringResource(R.string.stats_longest_rally),
                    value = "$longestRally",
                    valueWidth = ValueWidth,
                    nextUnlockWidth = NextUnlockWidth
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Heatmap Section
        Text(
            text = stringResource(R.string.stats_shot_distribution),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Filters
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == StatsFilter.ALL,
                onClick = { filter = StatsFilter.ALL },
                label = { Text(stringResource(R.string.stats_filter_all)) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Winning/Losing filters (Classic Mode only)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stats_classic_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == StatsFilter.WON,
                    onClick = { filter = StatsFilter.WON },
                    label = { Text(stringResource(R.string.stats_filter_won)) }
                )
                FilterChip(
                    selected = filter == StatsFilter.LOST,
                    onClick = { filter = StatsFilter.LOST },
                    label = { Text(stringResource(R.string.stats_filter_lost)) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 3x3 Grid
        val maxCount = heatmap.values.maxOrNull() ?: 1
        
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(0.8f)) // Corner (smaller weight to give more space to cols)
                Text(stringResource(R.string.heatmap_soft), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(stringResource(R.string.heatmap_med), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(stringResource(R.string.heatmap_hard), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            
            // Rows
            HeatmapRow(stringResource(R.string.heatmap_lob), listOf(SwingType.SOFT_LOB, SwingType.MEDIUM_LOB, SwingType.HARD_LOB), heatmap, maxCount)
            HeatmapRow(stringResource(R.string.heatmap_flat), listOf(SwingType.SOFT_FLAT, SwingType.MEDIUM_FLAT, SwingType.HARD_FLAT), heatmap, maxCount)
            HeatmapRow(stringResource(R.string.heatmap_smash), listOf(SwingType.SOFT_SMASH, SwingType.MEDIUM_SMASH, SwingType.HARD_SMASH), heatmap, maxCount)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Reset Button
        Button(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(stringResource(R.string.stats_reset_confirm))
        }
    }
}

@Composable
fun StatsSectionHeader(
    title: String,
    nextUnlockText: String?,
    nextUnlockWidth: androidx.compose.ui.unit.Dp,
    valueWidth: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        // Next Unlock Header
        if (nextUnlockText != null) {
            Text(
                text = nextUnlockText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(nextUnlockWidth),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        } else {
            Spacer(modifier = Modifier.width(nextUnlockWidth))
        }
        
        // Value Header Spacer (Empty for now as Values don't typically have a column header like "Value")
        Spacer(modifier = Modifier.width(valueWidth))
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HeatmapRow(label: String, types: List<SwingType>, heatmap: Map<SwingType, Int>, maxCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Row Label (Right Justified)
        Text(
            text = label,
            modifier = Modifier.weight(0.8f).padding(end = 8.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        
        // Cells
        types.forEach { type ->
            val count = heatmap[type] ?: 0
            val intensity = if (maxCount > 0) count.toFloat() / maxCount else 0f
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f + (intensity * 0.9f)),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    color = if (intensity > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * A simple 2-column row for displaying stat name and value.
 */
/**
 * Calculates the next unlock threshold and returns how many more are needed.
 * Returns null if all thresholds have been achieved.
 */
fun getNextUnlockInfo(currentValue: Int, thresholds: List<Int>): String? {
    val nextThreshold = thresholds.firstOrNull { it > currentValue }
    return nextThreshold?.let { "+${it - currentValue}" }
}

/**
 * A row for displaying stat name, optional next unlock info, and value.
 * When nextUnlock is provided, displays a 3-column layout.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    nextUnlock: String? = null,
    valueWidth: androidx.compose.ui.unit.Dp,
    nextUnlockWidth: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Next unlock column with fixed width and right alignment
        Box(
            modifier = Modifier.width(nextUnlockWidth),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (nextUnlock != null) {
                Text(
                    text = nextUnlock,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        // Value column with fixed width and right alignment
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(valueWidth),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
    }
}

enum class StatsFilter {
    ALL, WON, LOST
}

