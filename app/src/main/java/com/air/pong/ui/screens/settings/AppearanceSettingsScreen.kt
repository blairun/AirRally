package com.air.pong.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.air.pong.ui.RingUtils
import com.air.pong.ui.theme.ThemeMode
import com.air.pong.ui.components.AvatarWithRing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog

@Composable
fun AppearanceSettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    avatarIndex: Int,
    onAvatarChange: (Int) -> Unit,
    // Ring selection
    ringIndex: Int = RingUtils.RING_INDEX_NONE,
    onRingChange: (Int) -> Unit = {},
    // Stats for unlock checks
    rallyLinesCleared: Int = 0,
    soloLinesCleared: Int = 0,
    longestRally: Int = 0,
    classicWins: Int = 0,
    // Score stats for ring unlocks
    rallyHighScore: Int = 0,
    soloHighScore: Int = 0,
    // Auto-scroll to rings section (when ring-only unlock banner is clicked)
    scrollToRings: Boolean = false
) {
    val scrollState = rememberScrollState()
    
    // Ring preview dialog state
    val showRingPreview = remember { mutableStateOf(false) }
    val previewRingResId = remember { mutableStateOf(0) }
    
    // Ring Preview Dialog
    if (showRingPreview.value && previewRingResId.value != 0) {
        Dialog(onDismissRequest = { showRingPreview.value = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = previewRingResId.value),
                        contentDescription = stringResource(R.string.background_ring),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(200.dp)
                    )
                }
            }
        }
    }
    
    // Auto-scroll to bottom (rings section) when scrollToRings is true
    androidx.compose.runtime.LaunchedEffect(scrollToRings) {
        if (scrollToRings) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
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
            rallyLinesCleared = rallyLinesCleared,
            soloLinesCleared = soloLinesCleared,
            longestRally = longestRally,
            classicWins = classicWins
        )


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
                rallyLinesCleared = rallyLinesCleared,
                soloLinesCleared = soloLinesCleared,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Rally Lines Unlocks (Co-op mode lines cleared)
        if (AvatarUtils.rallyLinesAvatars.isNotEmpty()) {
            Text(
                stringResource(R.string.rally_lines_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            AvatarGrid(
                avatars = AvatarUtils.rallyLinesAvatars,
                selectedIndex = avatarIndex,
                onAvatarChange = onAvatarChange,
                rallyLinesCleared = rallyLinesCleared,
                soloLinesCleared = soloLinesCleared,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Solo Lines Unlocks (Solo mode lines cleared)
        if (AvatarUtils.soloLinesAvatars.isNotEmpty()) {
            Text(
                stringResource(R.string.solo_lines_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            AvatarGrid(
                avatars = AvatarUtils.soloLinesAvatars,
                selectedIndex = avatarIndex,
                onAvatarChange = onAvatarChange,
                rallyLinesCleared = rallyLinesCleared,
                soloLinesCleared = soloLinesCleared,
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
                rallyLinesCleared = rallyLinesCleared,
                soloLinesCleared = soloLinesCleared,
                longestRally = longestRally,
                classicWins = classicWins
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Background Ring Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.background_ring),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.ring_long_press_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // None option
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = ringIndex == RingUtils.RING_INDEX_NONE,
                onClick = { onRingChange(RingUtils.RING_INDEX_NONE) },
                label = { Text(stringResource(R.string.ring_none)) }
            )
        }
        
        // Rally Score Rings
        if (RingUtils.rallyScoreRings.isNotEmpty()) {
            Text(
                stringResource(R.string.ring_rally_score_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            RingGrid(
                rings = RingUtils.rallyScoreRings,
                selectedIndex = ringIndex,
                avatarResId = AvatarUtils.avatarResources.getOrElse(avatarIndex) { AvatarUtils.avatarResources.firstOrNull() ?: 0 },
                onRingChange = onRingChange,
                onRingLongPress = { resId ->
                    previewRingResId.value = resId
                    showRingPreview.value = true
                },
                rallyHighScore = rallyHighScore,
                soloHighScore = soloHighScore
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Solo Score Rings
        if (RingUtils.soloScoreRings.isNotEmpty()) {
            Text(
                stringResource(R.string.ring_solo_score_unlocks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            RingGrid(
                rings = RingUtils.soloScoreRings,
                selectedIndex = ringIndex,
                avatarResId = AvatarUtils.avatarResources.getOrElse(avatarIndex) { AvatarUtils.avatarResources.firstOrNull() ?: 0 },
                onRingChange = onRingChange,
                onRingLongPress = { resId ->
                    previewRingResId.value = resId
                    showRingPreview.value = true
                },
                rallyHighScore = rallyHighScore,
                soloHighScore = soloHighScore
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // App Icon Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.app_icon),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.icon_change_warning),
                style = MaterialTheme.typography.bodySmall
            )
        }

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
                            R.drawable.ic_launcher_foreground_raw 
                        else 
                            R.drawable.ic_launcher_foreground_classic
                        
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
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
    rallyLinesCleared: Int,
    soloLinesCleared: Int,
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
                val isUnlocked = AvatarUtils.isUnlocked(avatar, rallyLinesCleared, soloLinesCleared, longestRally, classicWins)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RingGrid(
    rings: List<RingUtils.RingInfo>,
    selectedIndex: Int,
    avatarResId: Int,
    onRingChange: (Int) -> Unit,
    onRingLongPress: (Int) -> Unit,
    rallyHighScore: Int,
    soloHighScore: Int
) {
    // Grayscale color matrix for locked rings
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    
    rings.chunked(4).forEach { rowRings ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rowRings.forEach { ring ->
                val isUnlocked = RingUtils.isUnlocked(ring, rallyHighScore, soloHighScore)
                val isSelected = ring.index == selectedIndex
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
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
                            .combinedClickable(
                                onClick = { if (isUnlocked) onRingChange(ring.index) },
                                onLongClick = { onRingLongPress(ring.resId) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Show avatar with ring preview
                        // Container is ~64dp, so avatar needs to be ~64/1.5 = 42dp for ring to fit
                        AvatarWithRing(
                            avatarResId = avatarResId,
                            ringResId = ring.resId,
                            size = 42.dp,
                            modifier = Modifier
                                .then(
                                    if (!isUnlocked) Modifier.alpha(0.5f) else Modifier
                                )
                        )
                    }
                    
                    // Show unlock hint for locked rings
                    if (!isUnlocked) {
                        Text(
                            text = RingUtils.getNextUnlockHint(ring),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // Fill remaining slots with empty space to maintain grid alignment
            repeat(4 - rowRings.size) {
                Spacer(modifier = Modifier.width(80.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
