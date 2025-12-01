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
                            if (point.didIWin) {
                                // Winning Shot = The last shot I made that wasn't returned
                                shots.lastOrNull()?.let { swing ->
                                    counts[swing] = counts.getOrDefault(swing, 0) + 1
                                }
                            }
                        }
                        StatsFilter.LOST -> {
                            if (!point.didIWin) {
                                // Losing Shot = The shot I missed (Net/Out/Whiff)
                                // If I lost by Timeout, endReason is TIMEOUT, and I didn't swing (or last swing was valid).
                                // We only count it if endReason implies a swing error.
                                val isSwingError = point.endReason == "NET" || point.endReason == "OUT" || point.endReason == "WHIFF"
                                if (isSwingError) {
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
            title = { Text("Reset Stats") },
            text = { Text("Are you sure you want to reset all game statistics? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetStats()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Summary Cards
        // Row 1: Wins / Losses
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Wins",
                value = "$winCount",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Losses",
                value = "$lossCount",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Row 2: Total Hits / Longest Rally
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Total Hits",
                value = "$totalHits",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Longest Rally",
                value = "$longestRally",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Heatmap Section
        Text(
            text = "Shot Distribution",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Filters
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == StatsFilter.ALL,
                onClick = { filter = StatsFilter.ALL },
                label = { Text("All") }
            )
            FilterChip(
                selected = filter == StatsFilter.WON,
                onClick = { filter = StatsFilter.WON },
                label = { Text("winning shots") }
            )
            FilterChip(
                selected = filter == StatsFilter.LOST,
                onClick = { filter = StatsFilter.LOST },
                label = { Text("losing shots") }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 3x3 Grid
        val maxCount = heatmap.values.maxOrNull() ?: 1
        
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(0.8f)) // Corner (smaller weight to give more space to cols)
                Text("SOFT", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("MED", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("HARD", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            
            // Rows
            HeatmapRow("LOB", listOf(SwingType.SOFT_LOB, SwingType.MEDIUM_LOB, SwingType.HARD_LOB), heatmap, maxCount)
            HeatmapRow("FLAT", listOf(SwingType.SOFT_FLAT, SwingType.MEDIUM_FLAT, SwingType.HARD_FLAT), heatmap, maxCount)
            HeatmapRow("SPIKE", listOf(SwingType.SOFT_SPIKE, SwingType.MEDIUM_SPIKE, SwingType.HARD_SPIKE), heatmap, maxCount)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Reset Button
        Button(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Reset")
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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

enum class StatsFilter {
    ALL, WON, LOST
}
