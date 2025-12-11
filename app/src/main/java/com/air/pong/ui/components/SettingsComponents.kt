package com.air.pong.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A slider setting with label, info button, and optional difficulty display.
 * 
 * @param label The setting label (can include value placeholder)
 * @param value Current slider value
 * @param onValueChange Called when user changes value
 * @param valueRange Min-max range for the slider
 * @param steps Number of discrete steps (or 0 for continuous)
 * @param snapTo Value to snap to (e.g., 100 for snapping to 100ms)
 * @param onValueChangeFinished Called when user finishes changing value
 * @param onInfoClick Called when info button is clicked
 * @param enabled Whether the slider is enabled
 */
@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    snapTo: Float = 0f,
    onValueChangeFinished: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = "Info", 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                val snapped = if (snapTo > 0f) {
                    (newValue / snapTo).toInt() * snapTo
                } else {
                    newValue
                }
                onValueChange(snapped)
            },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled
        )
    }
}

/**
 * A toggle setting with label, info button, and switch.
 * 
 * @param label The setting label
 * @param checked Current toggle state
 * @param onCheckedChange Called when user changes state
 * @param onInfoClick Called when info button is clicked
 * @param enabled Whether the toggle is enabled
 */
@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = "Info", 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * An expandable/collapsible section with header and content.
 * 
 * @param title Section title
 * @param expanded Current expanded state
 * @param onExpandedChange Called when user toggles section
 * @param content Content to show when expanded
 */
@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        if (expanded) {
            content()
        }
    }
}

/**
 * A section header with title, info button, and optional action button.
 * 
 * @param title Section title
 * @param onInfoClick Called when info button is clicked
 * @param actionLabel Optional action button label (e.g., "Defaults")
 * @param onActionClick Called when action button is clicked
 */
@Composable
fun SectionHeader(
    title: String,
    onInfoClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = "Info", 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}
