package com.air.pong.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpSettingsScreen(
    onLaunchTutorial: () -> Unit = {}
) {
    val tabs = listOf(
        R.string.help_tab_general,
        R.string.help_tab_rally,
        R.string.help_tab_solo,
        R.string.help_tab_classic
    )
    
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, titleRes ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(stringResource(titleRes)) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                when (page) {
                    0 -> GeneralHelpContent()
                    1 -> RallyHelpContent()
                    2 -> SoloHelpContent()
                    3 -> ClassicHelpContent()
                }
                
                // Tutorial button at bottom of each tab
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onLaunchTutorial,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.tutorial_button))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GeneralHelpContent() {
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

@Composable
private fun RallyHelpContent() {
    Text(stringResource(R.string.mode_rally_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(stringResource(R.string.mode_rally_goal), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_rally_lives), style = MaterialTheme.typography.bodyMedium)
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(stringResource(R.string.help_scoring_leveling), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.mode_rally_grid), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_rally_xclear), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_rally_life), style = MaterialTheme.typography.bodyMedium)

    Spacer(modifier = Modifier.height(8.dp))
    
    // New Bonus Section
    Text(stringResource(R.string.help_specials), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.help_spin_shots), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_copy_cat), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_penalty_squares), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_golden_squares), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_power_outage), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SoloHelpContent() {
    Text(stringResource(R.string.mode_solo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(stringResource(R.string.mode_solo_practice), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_solo_grid), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_solo_wall), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_solo_flight), style = MaterialTheme.typography.bodyMedium)
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(stringResource(R.string.help_bonuses_specials), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.help_solo_bonuses_intro), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_solo_spin), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_solo_copy_cat), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_solo_special), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.help_solo_outage), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ClassicHelpContent() {
    Text(stringResource(R.string.mode_classic_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(stringResource(R.string.mode_classic_rules), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_classic_serve), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.mode_classic_scoring), style = MaterialTheme.typography.bodyMedium)
}
