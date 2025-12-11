package com.air.pong.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.ui.AvatarUtils
import com.air.pong.ui.theme.ThemeMode

@Composable
fun AppearanceSettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    avatarIndex: Int,
    onAvatarChange: (Int) -> Unit,
    // Stats for unlock checks
    rallyHighScore: Int = 0,
    longestRally: Int = 0,
    classicWins: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Theme Section
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
        
        // Player Name
        OutlinedTextField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            label = { Text(stringResource(R.string.player_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Avatar Section - Base Avatars (Always Unlocked)
        Text(
            stringResource(R.string.avatar),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        AvatarGrid(
            avatars = AvatarUtils.baseAvatars,
            selectedIndex = avatarIndex,
            onAvatarChange = onAvatarChange,
            rallyHighScore = rallyHighScore,
            longestRally = longestRally,
            classicWins = classicWins
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Rally Score Unlocks
        if (AvatarUtils.rallyScoreAvatars.isNotEmpty()) {
            Text(
                stringResource(R.string.rally_score_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            AvatarGrid(
                avatars = AvatarUtils.rallyScoreAvatars,
                selectedIndex = avatarIndex,
                onAvatarChange = onAvatarChange,
                rallyHighScore = rallyHighScore,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Rally Length Unlocks
        if (AvatarUtils.rallyLengthAvatars.isNotEmpty()) {
            Text(
                stringResource(R.string.rally_length_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            AvatarGrid(
                avatars = AvatarUtils.rallyLengthAvatars,
                selectedIndex = avatarIndex,
                onAvatarChange = onAvatarChange,
                rallyHighScore = rallyHighScore,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Classic Wins Unlocks
        if (AvatarUtils.classicWinsAvatars.isNotEmpty()) {
            Text(
                stringResource(R.string.classic_wins_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            AvatarGrid(
                avatars = AvatarUtils.classicWinsAvatars,
                selectedIndex = avatarIndex,
                onAvatarChange = onAvatarChange,
                rallyHighScore = rallyHighScore,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App Icon Section
        Text(
            stringResource(R.string.app_icon),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val context = androidx.compose.ui.platform.LocalContext.current
        val currentIcon = androidx.compose.runtime.remember { 
            androidx.compose.runtime.mutableStateOf(com.air.pong.ui.utils.IconManager.getCurrentIcon(context)) 
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            com.air.pong.ui.utils.IconManager.AppIcon.values().forEach { icon ->
                FilterChip(
                    selected = currentIcon.value == icon,
                    onClick = { 
                        com.air.pong.ui.utils.IconManager.setIcon(context, icon)
                        currentIcon.value = icon
                    },
                    label = { Text(icon.displayName) },
                    leadingIcon = {
                        val iconRes = if (icon == com.air.pong.ui.utils.IconManager.AppIcon.DEFAULT) 
                            R.drawable.ic_launcher_foreground_fixed 
                        else 
                            R.drawable.ic_launcher_foreground
                        
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AvatarGrid(
    avatars: List<AvatarUtils.AvatarInfo>,
    selectedIndex: Int,
    onAvatarChange: (Int) -> Unit,
    rallyHighScore: Int,
    longestRally: Int,
    classicWins: Int
) {
    // Grayscale color matrix for locked avatars
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    
    avatars.chunked(4).forEach { rowAvatars ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rowAvatars.forEach { avatar ->
                val isUnlocked = AvatarUtils.isUnlocked(avatar, rallyHighScore, longestRally, classicWins)
                val isSelected = avatar.index == selectedIndex
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp)
                ) {
                    IconButton(
                        onClick = { if (isUnlocked) onAvatarChange(avatar.index) },
                        enabled = isUnlocked,
                        modifier = Modifier
                            .size(64.dp)
                            .padding(4.dp)
                            .then(
                                if (isSelected && isUnlocked) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                } else Modifier
                            )
                    ) {
                        Image(
                            painter = painterResource(id = avatar.resId),
                            contentDescription = "Avatar ${avatar.index}",
                            contentScale = ContentScale.Crop,
                            colorFilter = if (!isUnlocked) ColorFilter.colorMatrix(grayscaleMatrix) else null,
                            alpha = if (!isUnlocked) 0.5f else 1f,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape)
                        )
                    }
                    
                    // Show unlock hint for locked avatars
                    if (!isUnlocked) {
                        Text(
                            text = AvatarUtils.getNextUnlockHint(avatar),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // Fill remaining slots with empty space to maintain grid alignment
            repeat(4 - rowAvatars.size) {
                Spacer(modifier = Modifier.width(72.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
