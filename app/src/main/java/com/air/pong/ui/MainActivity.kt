package com.air.pong.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.air.pong.ui.screens.GameScreen
import com.air.pong.ui.screens.LobbyScreen
import com.air.pong.ui.screens.MainMenuScreen
import com.air.pong.ui.screens.GameOverScreen
import com.air.pong.utils.PermissionsManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.air.pong.ui.dialogs.IncompatibleDeviceDialog

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var permissionsManager: PermissionsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsManager.checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during gameplay - Moved to GameScreen
        // window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Check for required sensors
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
        
        if (!hasGyroscope || !hasGravity) {
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        IncompatibleDeviceDialog(
                            missingSensors = buildList {
                                if (!hasGyroscope) add("Gyroscope")
                                if (!hasGravity) add("Gravity")
                            },
                            onExit = { finish() }
                        )
                    }
                }
            }
            return
        }
        
        permissionsManager = PermissionsManager(this)
        
        // Check permissions on start
        permissionsManager.checkPermissions()
        if (!permissionsManager.hasPermissions()) {
            requestPermissionLauncher.launch(permissionsManager.getRequiredPermissions().toTypedArray())
        }

        setContent {
            val sharedPrefs = remember { getSharedPreferences("airrally_prefs", MODE_PRIVATE) }
            val themePref = sharedPrefs.getString("theme_mode", com.air.pong.ui.theme.ThemeMode.SYSTEM.name)
            var themeMode by remember { mutableStateOf(com.air.pong.ui.theme.ThemeMode.valueOf(themePref ?: com.air.pong.ui.theme.ThemeMode.SYSTEM.name)) }

            com.air.pong.ui.theme.AirRallyTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Safety warning state
                    var showSafetyWarning by remember { 
                        mutableStateOf(!sharedPrefs.getBoolean("safety_warning_accepted", false))
                    }
                    
                    if (showSafetyWarning) {
                        com.air.pong.ui.dialogs.SafetyWarningDialog(
                            onDismiss = {
                                // User declined - exit app
                                finish()
                            },
                            onAccept = {
                                // Mark as accepted
                                sharedPrefs.edit().putBoolean("safety_warning_accepted", true).apply()
                                showSafetyWarning = false
                            }
                        )
                    }
                    
                    NavHost(navController = navController, startDestination = "main_menu") {
                        composable("main_menu") {
                            MainMenuScreen(
                                viewModel = viewModel,
                                permissionsManager = permissionsManager,
                                onNavigateToLobby = {
                                    navController.navigate("lobby")
                                },
                                onNavigateToSettings = { screenType ->
                                    navController.navigate("settings?screen=${screenType.name}")
                                }
                            )
                        }
                        
                        composable(
                            route = "settings?screen={screen}",
                            arguments = listOf(
                                androidx.navigation.navArgument("screen") {
                                    type = androidx.navigation.NavType.StringType
                                    defaultValue = com.air.pong.ui.screens.SettingsScreenType.Main.name
                                }
                            )
                        ) { backStackEntry ->
                            val screenName = backStackEntry.arguments?.getString("screen")
                            val initialScreen = try {
                                com.air.pong.ui.screens.SettingsScreenType.valueOf(screenName ?: com.air.pong.ui.screens.SettingsScreenType.Main.name)
                            } catch (e: IllegalArgumentException) {
                                com.air.pong.ui.screens.SettingsScreenType.Main
                            }

                            com.air.pong.ui.screens.SettingsScreen(
                                viewModel = viewModel,
                                currentTheme = themeMode,
                                onThemeChange = { newTheme ->
                                    themeMode = newTheme
                                    sharedPrefs.edit().putString("theme_mode", newTheme.name).apply()
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToGame = {
                                    navController.navigate("game")
                                },
                                onNavigateToGameOver = {
                                    navController.navigate("game_over")
                                },
                                initialScreen = initialScreen
                            )
                        }
                        
                        composable("lobby") {
                            LobbyScreen(
                                viewModel = viewModel,
                                onNavigateToGame = {
                                    navController.navigate("game")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateBack = {
                                    viewModel.disconnect()
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("game") {
                            GameScreen(
                                viewModel = viewModel,
                                onGameOver = {
                                    navController.navigate("game_over") {
                                        popUpTo("main_menu") { inclusive = false }
                                    }
                                },
                                onStopDebug = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("game_over") {
                            val gameState by viewModel.gameState.collectAsState()
                            
                            LaunchedEffect(gameState.gamePhase) {
                                if (gameState.gamePhase == com.air.pong.core.game.GamePhase.WAITING_FOR_SERVE) {
                                    navController.navigate("game") {
                                        popUpTo("game") { inclusive = true }
                                    }
                                }
                            }

                            GameOverScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onReturnToDebug = {
                                    navController.popBackStack()
                                },
                                onReturnToMenu = {
                                    viewModel.disconnect()
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        if (::permissionsManager.isInitialized) {
            permissionsManager.checkPermissions()
        }
        
        // Check Volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val volumePercent = currentVolume.toFloat() / maxVolume.toFloat()
        
        if (volumePercent < 0.5f) {
            android.widget.Toast.makeText(this, "Turn up volume for best experience! 🔊", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}
