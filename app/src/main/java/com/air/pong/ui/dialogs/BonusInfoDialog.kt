package com.air.pong.ui.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.components.BonusChipType

/**
 * Displays bonus info popup when a bonus chip is clicked during game stoppage.
 * Reuses the same info strings as the settings screen.
 */
@Composable
fun BonusInfoDialog(
    chipType: BonusChipType,
    onDismiss: () -> Unit
) {
    val (titleRes, textRes) = when (chipType) {
        BonusChipType.RALLY -> Pair(R.string.info_rally_bonus_title, R.string.info_rally_bonus_text)
        BonusChipType.GRID_UPGRADES -> Pair(R.string.info_grid_upgrades_title, R.string.info_grid_upgrades_text)
        BonusChipType.SPIN -> Pair(R.string.info_bonus_spin_title, R.string.info_bonus_spin_text)
        BonusChipType.COPY_CAT -> Pair(R.string.info_bonus_copy_cat_title, R.string.info_bonus_copy_cat_text)
        BonusChipType.GOLD -> Pair(R.string.info_bonus_special_squares_title, R.string.info_bonus_special_squares_text)
        BonusChipType.POWER_OUTAGE -> Pair(R.string.info_bonus_power_outage_title, R.string.info_bonus_power_outage_text)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium
            ) 
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
