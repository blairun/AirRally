package com.air.pong.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun IncompatibleDeviceDialog(
    missingSensors: List<String>,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Do nothing, force exit */ },
        title = { Text(text = "Device Incompatible") },
        text = { 
            Text(text = "This game requires specific sensors to function. Your device is missing: ${missingSensors.joinToString(", ")}.\n\nThe app will now close.") 
        },
        confirmButton = {
            TextButton(onClick = onExit) {
                Text("Exit")
            }
        }
    )
}
