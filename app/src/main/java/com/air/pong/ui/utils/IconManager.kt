package com.air.pong.ui.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.air.pong.BuildConfig

object IconManager {
    private const val PACKAGE_NAME = "com.air.pong"
    private const val ALIAS_DEFAULT = "$PACKAGE_NAME.ui.MainActivityDefault"
    private const val ALIAS_CLASSIC = "$PACKAGE_NAME.ui.MainActivityClassic"

    enum class AppIcon(val aliasName: String, val displayName: String) {
        DEFAULT(ALIAS_DEFAULT, "Modern"),
        CLASSIC(ALIAS_CLASSIC, "Classic")
    }

    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager

        // Enable the selected alias
        pm.setComponentEnabledSetting(
            ComponentName(context, icon.aliasName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Disable the other aliases
        AppIcon.values().forEach {
            if (it != icon) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, it.aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    fun getCurrentIcon(context: Context): AppIcon {
        val pm = context.packageManager
        AppIcon.values().forEach {
            val state = pm.getComponentEnabledSetting(ComponentName(context, it.aliasName))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return it
            }
        }
        return AppIcon.DEFAULT // Default fallback
    }
}
