package com.air.pong.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R

@Composable
fun HelpSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Safety
        Text(stringResource(R.string.safety_first), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.safety_strap), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.safety_area), style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Gameplay
        Text(stringResource(R.string.gameplay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.gameplay_hold), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.gameplay_yellow), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.gameplay_green), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.gameplay_red), style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Game Modes
        Text(stringResource(R.string.game_modes_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
       
        Spacer(modifier = Modifier.height(8.dp))
        
        // Rally Mode
        Text(stringResource(R.string.mode_rally_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.mode_rally_goal), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_lives), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_grid), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_scoring), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_lines), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_xclear), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_rally_life), style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Classic Mode
        Text(stringResource(R.string.mode_classic_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.mode_classic_rules), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_classic_serve), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mode_classic_scoring), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Shot Types
        Text(stringResource(R.string.shot_types_risks), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_types_desc), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_flat), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_flat_desc), style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_lob), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_lob_desc), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_lob_safe), style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.shot_smash), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_smash_desc), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_smash_risk), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.shot_smash_net), style = MaterialTheme.typography.bodyMedium)
    }
}
