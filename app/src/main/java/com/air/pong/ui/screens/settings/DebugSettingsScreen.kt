package com.air.pong.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.core.game.SwingType
import com.air.pong.core.game.SwingData
import com.air.pong.core.game.SpinType
import com.air.pong.audio.AudioManager
import com.air.pong.ui.components.SettingsToggle

@Composable
fun DebugSettingsScreen(
    isDebugMode: Boolean,
    onDebugModeChange: (Boolean) -> Unit,
    useDebugTones: Boolean,
    onUseDebugTonesChange: (Boolean) -> Unit,
    softThreshold: Float,
    mediumThreshold: Float,
    hardThreshold: Float,
    lastSwingType: SwingType?,
    lastSwingData: SwingData?,
    onPlayTestSound: (AudioManager.SoundEvent) -> Unit,
    onShowInfo: (String, String) -> Unit,
    onEnterDebugGame: () -> Unit,
    onEnterDebugEndGame: () -> Unit,
    onClearDebugData: () -> Unit,
    detectedHand: String = "Unknown",
    gravXEma: Float = 0f,
    isLeftHanded: Boolean = false
) {
    val context = LocalContext.current

    var isReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onClearDebugData()
        }
    }
    
    LaunchedEffect(Unit) {
        onClearDebugData()
        isReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        SettingsToggle(
            label = stringResource(R.string.game_debug_overlay),
            checked = isDebugMode,
            onCheckedChange = onDebugModeChange,
            onInfoClick = {
                onShowInfo(
                    context.getString(R.string.info_debug_mode_title), 
                    context.getString(R.string.info_debug_mode_text)
                )
            }
        )

        SettingsToggle(
            label = stringResource(R.string.use_debug_tones),
            checked = useDebugTones,
            onCheckedChange = onUseDebugTonesChange,
            onInfoClick = {
                onShowInfo(
                    context.getString(R.string.info_debug_tones_title), 
                    context.getString(R.string.info_debug_tones_text)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            stringResource(R.string.sensor_hit_test),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Fixed height container for sensor test to prevent layout jumps
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
                contentAlignment = Alignment.Center
        ) {
            if (isReady) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (lastSwingType != null) {
                        // Calculate spin type from peak gyro
                        val spinType = lastSwingData?.let { data ->
                            SpinType.fromGyro(data.peakGyroY, data.peakGyroZ, isLeftHanded)
                        }
                        
                        Text(
                            text = lastSwingType.name.replace("_", " ") + 
                                   (spinType?.let { " • ${it.name.replace("_", " ")}" } ?: ""),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        lastSwingData?.let { data ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.force_fmt, data.force), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.accel_fmt, data.accelX, data.accelY, data.accelZ), style = MaterialTheme.typography.labelSmall)
                            Text(stringResource(R.string.gyro_fmt, data.gyroX, data.gyroY, data.gyroZ), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "Peak Gyro: X=${String.format("%.1f", data.peakGyroX)}, Y=${String.format("%.1f", data.peakGyroY)}, Z=${String.format("%.1f", data.peakGyroZ)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(stringResource(R.string.grav_fmt, data.gravX, data.gravY, data.gravZ), style = MaterialTheme.typography.labelSmall)
                            
                            // Dynamic Thresholds Display - now uses actual independent thresholds
                            Text(
                                text = stringResource(
                                    R.string.soft_med_hard_thresholds, 
                                    softThreshold.toInt(), 
                                    mediumThreshold.toInt(), 
                                    hardThreshold.toInt()
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            
                            // Handedness detection display
                            Text(
                                text = "Detected Hand: $detectedHand (gravX EMA: ${String.format("%.1f", gravXEma)})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )

                            // Play sound based on force for testing - uses actual thresholds
                            LaunchedEffect(data) {
                                val soundEvent = when {
                                    data.force > hardThreshold -> AudioManager.SoundEvent.HIT_HARD
                                    data.force > mediumThreshold -> AudioManager.SoundEvent.HIT_MEDIUM
                                    else -> AudioManager.SoundEvent.HIT_SOFT
                                }
                                onPlayTestSound(soundEvent)
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.swing_test_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onEnterDebugGame,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_debug_game))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onEnterDebugEndGame,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_debug_end_game))
        }
    }
}
