package com.air.pong.ui.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.theme.ThemeMode

@Composable
fun AppearanceSettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    avatarIndex: Int,
    onAvatarChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = currentTheme == mode,
                    onClick = { onThemeChange(mode) },
                    label = { 
                        Text(
                            when(mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        ) 
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            label = { Text(stringResource(R.string.player_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Avatar", // TODO: Add to strings.xml
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        com.air.pong.ui.AvatarUtils.avatarResources.chunked(4).forEachIndexed { rowIndex, rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowAvatars.forEachIndexed { colIndex, resId ->
                    val index = rowIndex * 4 + colIndex
                    val isSelected = index == avatarIndex
                    
                    IconButton(
                        onClick = { onAvatarChange(index) },
                        modifier = Modifier
                            .size(64.dp)
                            .padding(4.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                } else Modifier
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = resId),
                            contentDescription = "Avatar $index",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
