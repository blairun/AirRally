package com.air.pong.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages runtime permissions required for Bluetooth connectivity.
 * Handles differences between Android 12+ and older versions.
 */
class PermissionsManager(private val context: Context) {

    private val _permissionState = MutableStateFlow(PermissionState.UNKNOWN)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    enum class PermissionState {
        UNKNOWN,
        GRANTED,
        DENIED_NEEDS_RATIONALE,
        DENIED_PERMANENTLY
    }

    /**
     * Returns the list of permissions required for the current Android version.
     */
    fun getRequiredPermissions(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= 33 -> { // Android 13+
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { // Android 12
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> { // Android 10 (API 29) & 11 (API 30)
                // Android 10+ requires FINE_LOCATION for Bluetooth Scan.
                // We only request FINE, which implicitly covers COARSE if granted.
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            else -> { // Android 9 (API 28) and below
                // Older versions often only needed COARSE, but FINE covers both.
                // However, some devices explicitly demand COARSE.
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }

    /**
     * Checks if all required permissions are currently granted.
     */
    fun hasPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Updates the internal state based on current permissions.
     * Call this after a permission result or on resume.
     */
    fun checkPermissions() {
        val granted = hasPermissions()
        _permissionState.update { 
            if (granted) PermissionState.GRANTED else PermissionState.DENIED_NEEDS_RATIONALE 
        }
    }
}
