package com.air.pong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.air.pong.core.game.SwingType

@Composable
fun RallyGrid(
    gridState: List<Boolean>,
    modifier: Modifier = Modifier
) {
    if (gridState.size != 9) return

    BoxWithConstraints(
        modifier = modifier.padding(8.dp)
    ) {
        // Calculate the max size that fits as a square while respecting aspect ratio
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val gridSize = minOf(availableWidth, availableHeight)
        
        Column(
            modifier = Modifier
                .size(gridSize)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rows = Type (Flat, Lob, Smash)
            val rowNames = listOf("Flat", "Lob", "Smash")
            val colNames = listOf("Soft", "Med", "Hard")

            for (row in 0..2) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Cells
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val isMarked = gridState[index]
                        
                        val typeLabel = rowNames[row]
                        val forceLabel = colNames[col]
                        
                        RallyGridCell(
                            isMarked = isMarked,
                            topLabel = forceLabel,
                            bottomLabel = typeLabel,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RallyGridCell(
    isMarked: Boolean,
    topLabel: String,
    bottomLabel: String,
    modifier: Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isMarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 300),
        label = "CellColor"
    )
    
    val textColor = if (isMarked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor),
            // .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic text size based on cell height
        // Use roughly 20% of cell height for each line, capped at reasonable sizes
        val cellHeight = maxHeight
        val density = LocalDensity.current
        val cellHeightPx = with(density) { cellHeight.toPx() }
        
        // Calculate font size: roughly 22% of cell height, min 8sp, max 18sp
        val calculatedFontSize = (cellHeightPx * 0.22f / density.fontScale).coerceIn(8f, 18f)
        val fontSize = calculatedFontSize.sp
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = topLabel, 
                fontSize = fontSize,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
                )
                Text(
                text = bottomLabel, 
                fontSize = fontSize,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
