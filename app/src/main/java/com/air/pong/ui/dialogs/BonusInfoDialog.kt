package com.air.pong.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.components.BonusChipType

/**
 * Displays bonus info popup when a bonus chip is clicked during game stoppage.
 * Reuses the same info strings as the settings screen.
 * 
 * For toggleable bonuses (Spin, Copy Cat, Special Squares, Power Outage):
 * - Shows an enable/disable button in the title row
 * - Button toggles the bonus on/off
 * 
 * For always-on bonuses (Rally Momentum, Grid Upgrades):
 * - Shows "Enabled" text in gray (non-interactive)
 */
@Composable
fun BonusInfoDialog(
    chipType: BonusChipType,
    onDismiss: () -> Unit,
    isEnabled: Boolean = true,
    onToggle: (() -> Unit)? = null
) {
    val (titleRes, textRes) = when (chipType) {
        BonusChipType.RALLY -> Pair(R.string.info_rally_bonus_title, R.string.info_rally_bonus_text)
        BonusChipType.GRID_UPGRADES -> Pair(R.string.info_grid_upgrades_title, R.string.info_grid_upgrades_text)
        BonusChipType.SPIN -> Pair(R.string.info_bonus_spin_title, R.string.info_bonus_spin_text)
        BonusChipType.COPY_CAT -> Pair(R.string.info_bonus_copy_cat_title, R.string.info_bonus_copy_cat_text)
        BonusChipType.GOLD -> Pair(R.string.info_bonus_special_squares_title, R.string.info_bonus_special_squares_text)
        BonusChipType.POWER_OUTAGE -> Pair(R.string.info_bonus_power_outage_title, R.string.info_bonus_power_outage_text)
    }
    
    // Determine if this bonus is toggleable
    val isToggleable = chipType != BonusChipType.RALLY && chipType != BonusChipType.GRID_UPGRADES
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                
                if (isToggleable && onToggle != null) {
                    // Toggleable bonus: show enable/disable button
                    TextButton(onClick = onToggle) {
                        Text(
                            text = if (isEnabled) stringResource(R.string.disable) else stringResource(R.string.enable),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    // Always-on bonus: show grayed out "Enabled" text
                    Text(
                        text = stringResource(R.string.enabled),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )
                }
            }
        },
        text = { 
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            ) 
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
