package com.air.pong.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.air.pong.R
import com.air.pong.BuildConfig

@Composable
fun AboutSettingsScreen() {
    val uriHandler = LocalUriHandler.current
    val linkColor = Color(0xFF448AFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium)
            Text(
                BuildConfig.VERSION_NAME, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/releases") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Credits
        val creditsText = buildAnnotatedString {
            append(stringResource(R.string.created_by))
            withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.created_by_ai))
            }
            append(stringResource(R.string.created_by_r))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.credits), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = creditsText, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://blai.run") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // License
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.license), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.license_name), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/blob/main/LICENSE") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Source Code
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.source_code), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.view_github_repo), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally") }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Problems?
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.problems), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.report_bug), 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold,
                color = linkColor,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/blairun/AirRally/issues") }
            )
        }
    }
}
