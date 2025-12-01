package com.air.pong.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.air.pong.R

@Composable
fun GameParamsSettingsScreen(
    flightTime: Float,
    onFlightTimeChange: (Float) -> Unit,
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    minSwingThreshold: Float,
    onSwingThresholdChange: (Float) -> Unit,
    onSettingsChange: () -> Unit,
    onResetDefaults: () -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Defaults Button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onResetDefaults) {
                Text(stringResource(R.string.defaults))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Flight Time
        val flightTimeLabel = when {
            flightTime <= 600 -> stringResource(R.string.difficulty_hard)
            flightTime <= 900 -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.flight_time_label, flightTime.toInt(), flightTimeLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_flight_time_title), context.getString(R.string.info_flight_time_text))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }

        Slider(
            value = flightTime,
            onValueChange = {
                // Snap to 100ms
                onFlightTimeChange((it / 100).toInt() * 100f)
            },
            valueRange = 500f..1200f,
            steps = 6,
            onValueChangeFinished = onSettingsChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty (Hit Window)
        val difficultyLabel = when {
            difficulty <= 300 -> stringResource(R.string.difficulty_hard)
            difficulty <= 500 -> stringResource(R.string.difficulty_medium)
            else -> stringResource(R.string.difficulty_easy)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hit_window_label, difficulty, difficultyLabel), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_hit_window_title), context.getString(R.string.info_hit_window_text))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = difficulty.toFloat(),
            onValueChange = {
                // Snap to 50ms
                val snapped = (it / 50).toInt() * 50
                onDifficultyChange(snapped)
            },
            valueRange = 200f..700f,
            steps = 9,
            onValueChangeFinished = onSettingsChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Swing Sensitivity (Local)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.swing_sensitivity_label, minSwingThreshold), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                onShowInfo(context.getString(R.string.info_sensitivity_title), context.getString(R.string.info_sensitivity_text))
            }) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }
        }
        Slider(
            value = minSwingThreshold,
            onValueChange = {
                // Snap to 1.0
                onSwingThresholdChange(kotlin.math.round(it))
            },
            valueRange = 10.0f..24.0f,
            steps = 13,
            onValueChangeFinished = onSettingsChange
        )
    }
}
