package com.air.pong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays a 2x3 grid of bonus indicator chips for Rally mode.
 * Each chip is uniformly sized and shows: emoji + value.
 * 
 * Grid layout (top-left to bottom-right):
 * | 🏓# (Rally)     | 🔢# (Grid Upgrades) |
 * | 💫#/5 (Spin)    | 🐱#/9 (Copy Cat)    |
 * | 🪙#/5 (Gold)    | 💡# (Power Outage)  |
 * 
 * Chips always show with faint background; text only appears when value > 0.
 * Chips are clickable only during game stoppage phases.
 */
@Composable
fun BonusProgressIndicators(
    // Rally state
    currentRallyLength: Int,
    longestRally: Int,
    isActivelyRallying: Boolean,
    
    // Grid upgrades
    gridUpgradesEarned: Int,
    
    // Spin state
    isSpinning: Boolean,
    spinCount: Int,
    isInfiniteSpin: Boolean,
    
    // Copy cat state
    copyProgress: Int,
    copyTierAchieved: Int,
    isSequenceActive: Boolean,
    copyCat9Completions: Int,
    
    // Golden state
    goldenTier: Int,
    
    // Power Outage state
    powerOutagesClearedCount: Int = 0,
    
    // Clickability
    isClickable: Boolean = false,
    onChipClick: (BonusChipType) -> Unit = {},
    
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Rally | Grid Upgrades
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Rally chip
            val rallyText = when {
                isActivelyRallying && currentRallyLength > 0 -> "🏓$currentRallyLength"
                !isActivelyRallying && longestRally > 0 -> "🏓$longestRally"
                else -> null
            }
            val rallyTextColor = if (isActivelyRallying) Color.White else Color.Gray
            BonusChip(
                text = rallyText,
                textColor = rallyTextColor,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.RALLY) }
            )
            
            // Grid Upgrades chip
            val gridText = if (gridUpgradesEarned > 0) "🔢$gridUpgradesEarned" else null
            BonusChip(
                text = gridText,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.GRID_UPGRADES) }
            )
        }
        
        // Row 2: Spin | Copy Cat
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Spin chip
            val spinText = when {
                isInfiniteSpin -> "♾️"
                isSpinning && spinCount > 0 -> "💫$spinCount"
                else -> null
            }
            BonusChip(
                text = spinText,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.SPIN) }
            )
            
            // Copy Cat chip
            // copyTierAchieved: 1 = 5 copies, 2 = 6 copies, ... 5 = 9 copies
            val copyCatText = when {
                copyCat9Completions > 0 -> "🐱9/9+"
                isSequenceActive && copyProgress > 0 -> "🐱$copyProgress/9"
                copyTierAchieved >= 1 -> {
                    val displayProgress = (copyTierAchieved + 4).coerceIn(5, 9)
                    "🐱$displayProgress/9"
                }
                else -> null
            }
            BonusChip(
                text = copyCatText,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.COPY_CAT) }
            )
        }
        
        // Row 3: Gold | Power Outage
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Gold chip
            val goldText = when {
                goldenTier >= 5 -> "🪙Max"
                goldenTier >= 1 -> "🪙$goldenTier"
                else -> null
            }
            BonusChip(
                text = goldText,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.GOLD) }
            )
            
            // Power Outage chip
            val powerText = if (powerOutagesClearedCount >= 1) "💡$powerOutagesClearedCount" else null
            BonusChip(
                text = powerText,
                isClickable = isClickable,
                onClick = { onChipClick(BonusChipType.POWER_OUTAGE) }
            )
        }
    }
}

/**
 * Individual bonus chip with fixed size and faint background.
 * Shows empty chip with faint outline when text is null.
 */
@Composable
private fun BonusChip(
    text: String?,
    textColor: Color = Color.White,
    isClickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    val chipModifier = Modifier
        .size(width = 52.dp, height = 28.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color.White.copy(alpha = 0.1f))
        .then(
            if (text != null) {
                Modifier // No border when has content
            } else {
                Modifier // No border when has no content
                // Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            }
        )
        .then(
            if (isClickable) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        )
    
    Box(
        modifier = chipModifier,
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
