package com.air.pong.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.air.pong.R

@Composable
fun IncompatibleDeviceDialog(
    missingSensors: List<String>,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal */ },
        title = { Text(stringResource(R.string.device_incompatible)) },
        text = { 
            Text(stringResource(R.string.missing_sensors_text, missingSensors.joinToString(", ")))
        },
        confirmButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.exit))
            }
        }
    )
}
