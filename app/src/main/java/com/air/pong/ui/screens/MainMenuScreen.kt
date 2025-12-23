package com.air.pong.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.air.pong.ui.GameViewModel
import com.air.pong.utils.PermissionsManager
import com.air.pong.core.network.NetworkAdapter
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.air.pong.R
import com.air.pong.ui.components.HomeBackgroundAnimation
import com.air.pong.ui.dialogs.TutorialDialog

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable

@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    permissionsManager: PermissionsManager,
    onNavigateToLobby: () -> Unit,
    onNavigateToSettings: (com.air.pong.ui.screens.SettingsScreenType, Boolean) -> Unit,
    onNavigateToGame: () -> Unit = {}
) {
    val permissionState by permissionsManager.permissionState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    val connectedPlayerName by viewModel.connectedPlayerName.collectAsState()
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()
    val connectingEndpointId by viewModel.connectingEndpointId.collectAsState()
    
    // Track if we are navigating to Lobby or Settings to prevent disconnect on ON_STOP
    var isNavigatingToLobby by remember { mutableStateOf(false) }
    var isNavigatingToSettings by remember { mutableStateOf(false) }
    
    // Handle Lifecycle events
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // If we are connected and NOT navigating to lobby or settings, disconnect
                if (connectionState == NetworkAdapter.ConnectionState.CONNECTED && 
                    !isNavigatingToLobby && !isNavigatingToSettings) {
                    viewModel.disconnect()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Reset navigation flags when we return to this screen
                isNavigatingToLobby = false
                isNavigatingToSettings = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Disconnect if system back button is pressed while connected
    if (connectionState == NetworkAdapter.ConnectionState.CONNECTED) {
        androidx.activity.compose.BackHandler {
            viewModel.disconnect()
        }
    }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
    
        // hasNewAvatarUnlock state hoisted here; UI rendered after Share button
        val hasNewAvatarUnlock by viewModel.hasNewAvatarUnlock.collectAsState()
        
        // Tutorial state
        val showTutorialBanner by viewModel.showTutorialBanner.collectAsState(initial = false)
        val showTutorialFromSettings by viewModel.showTutorialFromSettings.collectAsState()
        // Initialize based on settings flag so dialog shows immediately (prevents main menu flash)
        var showTutorialDialog by remember { mutableStateOf(viewModel.showTutorialFromSettings.value) }
        
        // Handle tutorial request from settings - just ensure dialog is shown
        // Do NOT clear the flag here - it must remain true until stopSensorTesting() runs
        // to prevent the sensor from being stopped during navigation
        LaunchedEffect(showTutorialFromSettings) {
            if (showTutorialFromSettings) {
                showTutorialDialog = true
            }
        }

        // Ambient Background Animation
        HomeBackgroundAnimation(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        val avatarIndex by viewModel.avatarIndex.collectAsState()
        val avatarResId = com.air.pong.ui.AvatarUtils.avatarResources.getOrElse(avatarIndex) { com.air.pong.ui.AvatarUtils.avatarResources.firstOrNull() ?: 0 }
        val ringIndex by viewModel.ringIndex.collectAsState()
        val ringResId = if (ringIndex >= 0) com.air.pong.ui.RingUtils.getRingByIndex(ringIndex)?.resId else null
        
        if (avatarResId != 0) {
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable { 
                        isNavigatingToSettings = true
                        onNavigateToSettings(com.air.pong.ui.screens.SettingsScreenType.Appearance, false) 
                    }
            ) {
                com.air.pong.ui.components.AvatarWithRing(
                    avatarResId = avatarResId,
                    ringResId = ringResId,
                    size = 100.dp
                )
            }
        }

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge
        )
        
        val taglines = remember {
            listOf(
                R.string.tagline_1,
                R.string.tagline_2,
                R.string.tagline_3,
                R.string.tagline_4,
                R.string.tagline_5,
                R.string.tagline_6,
                R.string.tagline_7,
                R.string.tagline_8,
                R.string.tagline_9,
                R.string.tagline_10
            )
        }
        val selectedTaglineId = rememberSaveable { taglines.random() }
        
        Text(
            text = stringResource(selectedTaglineId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
        

        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (permissionState != PermissionsManager.PermissionState.GRANTED) {
            Text(stringResource(R.string.bluetooth_permission_required), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            // In a real app, we'd have a button to request permissions here
            // For now, we assume MainActivity handles the request on start
        }
        
        if (connectionState == NetworkAdapter.ConnectionState.CONNECTED) {
            // Auto-navigate to Lobby when connected
            LaunchedEffect(Unit) {
                isNavigatingToLobby = true
                onNavigateToLobby()
            }
            
            Text(
                text = stringResource(R.string.connected_to, connectedPlayerName ?: stringResource(R.string.unknown)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(stringResource(R.string.redirecting_lobby), textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.disconnect))
            }
        } else {
            Button(
                onClick = { viewModel.playWithFriend() },
                enabled = permissionState == PermissionsManager.PermissionState.GRANTED && 
                          connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.play_with_friend))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Play Solo Rally button - always available (no network required)
        Button(
            onClick = { 
                viewModel.startSoloRallyGame()
                onNavigateToGame()
            },
            enabled = connectionState == NetworkAdapter.ConnectionState.DISCONNECTED,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.play_solo_rally))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = {
                isNavigatingToSettings = true
                onNavigateToSettings(com.air.pong.ui.screens.SettingsScreenType.Main, false)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.settings))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Connection state UI (ADVERTISING, DISCOVERING, CONNECTING, ERROR)
        when {
            connectionState == NetworkAdapter.ConnectionState.ADVERTISING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.waiting_for_friends), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.cancelConnection() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
            connectionState == NetworkAdapter.ConnectionState.DISCOVERING -> {
            
            // Auto-connect logic with tie-breaker
            var autoConnectSeconds by remember { mutableIntStateOf(3) }
            val myName = viewModel.playerName.collectAsState().value
            
            LaunchedEffect(discoveredEndpoints, connectionState) {
                // Only run auto-connect logic while still DISCOVERING
                if (connectionState != NetworkAdapter.ConnectionState.DISCOVERING) return@LaunchedEffect
                
                if (discoveredEndpoints.size == 1) {
                    val endpoint = discoveredEndpoints.first()
                    
                    // Tie-breaker: Device with alphabetically LATER name connects first (3s)
                    // Device with alphabetically EARLIER name waits longer (8s) as fallback only
                    val shouldAutoConnect = myName > endpoint.name
                    
                    if (shouldAutoConnect) {
                        // Primary initiator: short countdown
                        autoConnectSeconds = 3
                        while (autoConnectSeconds > 0 && connectionState == NetworkAdapter.ConnectionState.DISCOVERING) {
                            kotlinx.coroutines.delay(1000)
                            autoConnectSeconds--
                        }
                        // Only connect if still DISCOVERING (not already connecting/connected)
                        if (connectionState == NetworkAdapter.ConnectionState.DISCOVERING &&
                            discoveredEndpoints.size == 1 && discoveredEndpoints.first().name == endpoint.name) {
                            viewModel.connectToEndpoint(endpoint.id)
                        }
                    } else {
                        // Fallback: wait longer, only connect if other device hasn't initiated
                        autoConnectSeconds = 10
                        while (autoConnectSeconds > 0 && connectionState == NetworkAdapter.ConnectionState.DISCOVERING) {
                            kotlinx.coroutines.delay(1000)
                            autoConnectSeconds--
                        }
                        // CRITICAL: Only connect if still DISCOVERING (other device hasn't connected to us)
                        if (connectionState == NetworkAdapter.ConnectionState.DISCOVERING &&
                            discoveredEndpoints.size == 1 && discoveredEndpoints.first().name == endpoint.name) {
                            viewModel.connectToEndpoint(endpoint.id)
                        }
                    }
                } else {
                    // Reset if 0 or >1 endpoints
                    autoConnectSeconds = 3
                }
            }
            
            if (discoveredEndpoints.isEmpty()) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.looking_for_games), textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.available_games), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                
                discoveredEndpoints.forEach { endpoint ->
                    OutlinedButton(
                        onClick = { viewModel.connectToEndpoint(endpoint.id) },
                        enabled = connectingEndpointId == null, // Enable unless already connecting
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.connect_to, endpoint.name), textAlign = TextAlign.Center)
                            when {
                                connectingEndpointId == endpoint.id -> {
                                    Text(
                                        stringResource(R.string.connecting), 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                discoveredEndpoints.size == 1 && autoConnectSeconds > 0 && connectingEndpointId == null -> {
                                    Text(
                                        stringResource(R.string.auto_connecting, autoConnectSeconds), 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { viewModel.cancelConnection() }) {
                Text(stringResource(R.string.cancel))
            }
            }
            connectionState == NetworkAdapter.ConnectionState.CONNECTING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.connecting), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.cancelConnection() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
            connectionState == NetworkAdapter.ConnectionState.ERROR -> {
                val errorMessage by viewModel.errorMessage.collectAsState(initial = "Unknown Error")
                Text(
                    text = stringResource(R.string.error_prefix, errorMessage ?: "Unknown"),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }


    // Share Button (Top Right)
    val shareText = stringResource(R.string.share_game_content)
    IconButton(
        onClick = { shareGame(context, shareText) },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .statusBarsPadding() // Ensure it doesn't overlap with status bar
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = stringResource(R.string.cd_share_game),
            tint = MaterialTheme.colorScheme.primary
        )
    }
    
    // Tutorial banner - show if not completed and not dismissed (only when avatar banner not showing)
    if (showTutorialBanner && !hasNewAvatarUnlock) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 105.dp) // Offset down to cover play buttons
                .padding(horizontal = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tutorial_banner_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.startSensorForTutorial()
                                showTutorialDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.tutorial_banner_go))
                        }
                        TextButton(
                            onClick = { viewModel.dismissTutorialBanner() }
                        ) {
                            Text(stringResource(R.string.tutorial_banner_dismiss))
                        }
                    }
                }
            }
        }
    }
    
    // New unlock notification banner - rendered last so it's on top and clickable
    // Handles avatar, ring, or combined unlocks
    val hasNewRingUnlock by viewModel.hasNewRingUnlock.collectAsState()
    
    if (hasNewAvatarUnlock || hasNewRingUnlock) {
        val bannerText = when {
            hasNewAvatarUnlock && hasNewRingUnlock -> stringResource(R.string.new_unlocks_both)
            hasNewAvatarUnlock -> stringResource(R.string.new_avatar_unlocked)
            else -> stringResource(R.string.new_ring_unlocked)
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                .clickable { 
                    isNavigatingToSettings = true
                    // Scroll to rings section only if ring-only unlock (not avatar or both)
                    val scrollToRings = !hasNewAvatarUnlock && hasNewRingUnlock
                    onNavigateToSettings(com.air.pong.ui.screens.SettingsScreenType.Appearance, scrollToRings)
                }
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
    
    // Tutorial Dialog
    if (showTutorialDialog) {
        TutorialDialog(
            swingEvents = viewModel.getSwingEventsFlow(),
            onPlayBounceSound = { viewModel.playTutorialBounceSound() },
            onPlayVibration = { viewModel.playTutorialHitVibration() },
            onComplete = {
                // Don't set showTutorialDialog = false here - it causes a flash of main menu
                // The dialog will be disposed when we navigate away
                viewModel.stopSensorForTutorial()
                // Start Solo Rally game after tutorial
                viewModel.startSoloRallyGame()
                onNavigateToGame()
            },
            onRetry = {
                // Just restart the dialog, sensor still listening
            },
            onDismiss = {
                showTutorialDialog = false
                viewModel.stopSensorForTutorial()
            },
            onMarkComplete = {
                // Mark tutorial as completed when reaching final screen
                viewModel.markTutorialCompleted()
            }
        )
    }
    }
}


fun shareGame(context: android.content.Context, shareText: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
