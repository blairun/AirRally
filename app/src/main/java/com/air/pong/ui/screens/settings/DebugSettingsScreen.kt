package com.air.pong.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.core.game.SwingType
import com.air.pong.core.game.SwingData
import com.air.pong.audio.AudioManager

@Composable
fun DebugSettingsScreen(
    isDebugMode: Boolean,
    onDebugModeChange: (Boolean) -> Unit,
    useDebugTones: Boolean,
    onUseDebugTonesChange: (Boolean) -> Unit,
    lastSwingType: SwingType?,
    lastSwingData: SwingData?,
    onPlayTestSound: (AudioManager.SoundEvent) -> Unit,
    onShowInfo: (String, String) -> Unit,
    onEnterDebugGame: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.use_debug_tones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    onShowInfo(context.getString(R.string.info_debug_tones_title), context.getString(R.string.info_debug_tones_text))
                }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                }
            }
            Switch(
                checked = useDebugTones,
                onCheckedChange = onUseDebugTonesChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.debug_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    onShowInfo(context.getString(R.string.info_debug_mode_title), context.getString(R.string.info_debug_mode_text))
                }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
                }
            }
            Switch(
                checked = isDebugMode,
                onCheckedChange = onDebugModeChange
            )
        }

        if (isDebugMode) {

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.sensor_hit_test),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (lastSwingType != null) {
                    Text(
                        text = lastSwingType.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    lastSwingData?.let { data ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.force_fmt, data.force), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.accel_fmt, data.accelX, data.accelY, data.accelZ), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.gyro_fmt, data.gyroX, data.gyroY, data.gyroZ), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.grav_fmt, data.gravX, data.gravY, data.gravZ), style = MaterialTheme.typography.labelSmall)

                        // Play sound based on force for testing
                        LaunchedEffect(data) {
                            val soundEvent = when {
                                data.force > 20.0f -> AudioManager.SoundEvent.HIT_HARD
                                data.force > 17.0f -> AudioManager.SoundEvent.HIT_MEDIUM
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
        }

                Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onEnterDebugGame,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enter Debug Game")
        }
    }
}
