package com.air.pong.data

import android.content.SharedPreferences
import com.air.pong.core.game.SwingSettings

/**
 * Repository for managing SwingSettings persistence and state.
 * Holds the current SwingSettings instance and handles load/save operations.
 */
class SwingSettingsRepository(private val prefs: SharedPreferences) {

    /**
     * The current swing settings instance. Updated by load/update operations.
     */
    var current: SwingSettings = SwingSettings.createDefault()
        private set

    /**
     * Load swing settings from SharedPreferences.
     * Updates the [current] instance and returns it.
     */
    fun loadFromPrefs(): SwingSettings {
        current = SwingSettings(
            // Risk and Shrink settings
            softFlatNetRisk = prefs.getInt("softFlatNetRisk", SwingSettings.DEFAULT_SOFT_FLAT_NET_RISK),
            softFlatOutRisk = prefs.getInt("softFlatOutRisk", SwingSettings.DEFAULT_SOFT_FLAT_OUT_RISK),
            softFlatShrink = prefs.getInt("softFlatShrink", SwingSettings.DEFAULT_SOFT_FLAT_SHRINK),
            softFlatFlight = prefs.getFloat("softFlatFlight", SwingSettings.DEFAULT_SOFT_FLAT_FLIGHT),

            mediumFlatNetRisk = prefs.getInt("mediumFlatNetRisk", SwingSettings.DEFAULT_MEDIUM_FLAT_NET_RISK),
            mediumFlatOutRisk = prefs.getInt("mediumFlatOutRisk", SwingSettings.DEFAULT_MEDIUM_FLAT_OUT_RISK),
            mediumFlatShrink = prefs.getInt("mediumFlatShrink", SwingSettings.DEFAULT_MEDIUM_FLAT_SHRINK),
            mediumFlatFlight = prefs.getFloat("mediumFlatFlight", SwingSettings.DEFAULT_MEDIUM_FLAT_FLIGHT),

            hardFlatNetRisk = prefs.getInt("hardFlatNetRisk", SwingSettings.DEFAULT_HARD_FLAT_NET_RISK),
            hardFlatOutRisk = prefs.getInt("hardFlatOutRisk", SwingSettings.DEFAULT_HARD_FLAT_OUT_RISK),
            hardFlatShrink = prefs.getInt("hardFlatShrink", SwingSettings.DEFAULT_HARD_FLAT_SHRINK),
            hardFlatFlight = prefs.getFloat("hardFlatFlight", SwingSettings.DEFAULT_HARD_FLAT_FLIGHT),

            softLobNetRisk = prefs.getInt("softLobNetRisk", SwingSettings.DEFAULT_SOFT_LOB_NET_RISK),
            softLobOutRisk = prefs.getInt("softLobOutRisk", SwingSettings.DEFAULT_SOFT_LOB_OUT_RISK),
            softLobShrink = prefs.getInt("softLobShrink", SwingSettings.DEFAULT_SOFT_LOB_SHRINK),
            softLobFlight = prefs.getFloat("softLobFlight", SwingSettings.DEFAULT_SOFT_LOB_FLIGHT),

            mediumLobNetRisk = prefs.getInt("mediumLobNetRisk", SwingSettings.DEFAULT_MEDIUM_LOB_NET_RISK),
            mediumLobOutRisk = prefs.getInt("mediumLobOutRisk", SwingSettings.DEFAULT_MEDIUM_LOB_OUT_RISK),
            mediumLobShrink = prefs.getInt("mediumLobShrink", SwingSettings.DEFAULT_MEDIUM_LOB_SHRINK),
            mediumLobFlight = prefs.getFloat("mediumLobFlight", SwingSettings.DEFAULT_MEDIUM_LOB_FLIGHT),

            hardLobNetRisk = prefs.getInt("hardLobNetRisk", SwingSettings.DEFAULT_HARD_LOB_NET_RISK),
            hardLobOutRisk = prefs.getInt("hardLobOutRisk", SwingSettings.DEFAULT_HARD_LOB_OUT_RISK),
            hardLobShrink = prefs.getInt("hardLobShrink", SwingSettings.DEFAULT_HARD_LOB_SHRINK),
            hardLobFlight = prefs.getFloat("hardLobFlight", SwingSettings.DEFAULT_HARD_LOB_FLIGHT),

            softSmashNetRisk = prefs.getInt("softSmashNetRisk", SwingSettings.DEFAULT_SOFT_SMASH_NET_RISK),
            softSmashOutRisk = prefs.getInt("softSmashOutRisk", SwingSettings.DEFAULT_SOFT_SMASH_OUT_RISK),
            softSmashShrink = prefs.getInt("softSmashShrink", SwingSettings.DEFAULT_SOFT_SMASH_SHRINK),
            softSmashFlight = prefs.getFloat("softSmashFlight", SwingSettings.DEFAULT_SOFT_SMASH_FLIGHT),

            mediumSmashNetRisk = prefs.getInt("mediumSmashNetRisk", SwingSettings.DEFAULT_MEDIUM_SMASH_NET_RISK),
            mediumSmashOutRisk = prefs.getInt("mediumSmashOutRisk", SwingSettings.DEFAULT_MEDIUM_SMASH_OUT_RISK),
            mediumSmashShrink = prefs.getInt("mediumSmashShrink", SwingSettings.DEFAULT_MEDIUM_SMASH_SHRINK),
            mediumSmashFlight = prefs.getFloat("mediumSmashFlight", SwingSettings.DEFAULT_MEDIUM_SMASH_FLIGHT),

            hardSmashNetRisk = prefs.getInt("hardSmashNetRisk", SwingSettings.DEFAULT_HARD_SMASH_NET_RISK),
            hardSmashOutRisk = prefs.getInt("hardSmashOutRisk", SwingSettings.DEFAULT_HARD_SMASH_OUT_RISK),
            hardSmashShrink = prefs.getInt("hardSmashShrink", SwingSettings.DEFAULT_HARD_SMASH_SHRINK),
            hardSmashFlight = prefs.getFloat("hardSmashFlight", SwingSettings.DEFAULT_HARD_SMASH_FLIGHT),

            // Serve flight times (use defaults for now, not persisted)
            softLobServeFlight = SwingSettings.DEFAULT_SOFT_LOB_SERVE_FLIGHT,
            mediumLobServeFlight = SwingSettings.DEFAULT_MEDIUM_LOB_SERVE_FLIGHT,
            hardLobServeFlight = SwingSettings.DEFAULT_HARD_LOB_SERVE_FLIGHT,
            softSmashServeFlight = SwingSettings.DEFAULT_SOFT_SMASH_SERVE_FLIGHT,
            mediumSmashServeFlight = SwingSettings.DEFAULT_MEDIUM_SMASH_SERVE_FLIGHT,
            hardSmashServeFlight = SwingSettings.DEFAULT_HARD_SMASH_SERVE_FLIGHT
        )
        return current
    }

    /**
     * Save the current swing settings to SharedPreferences.
     */
    fun saveToPrefs() {
        saveToPrefs(current)
    }

    /**
     * Save the given swing settings to SharedPreferences and update [current].
     */
    fun saveToPrefs(settings: SwingSettings) {
        current = settings
        prefs.edit().apply {
            // Risk and Shrink settings
            putInt("softFlatNetRisk", settings.softFlatNetRisk)
            putInt("softFlatOutRisk", settings.softFlatOutRisk)
            putInt("softFlatShrink", settings.softFlatShrink)

            putInt("mediumFlatNetRisk", settings.mediumFlatNetRisk)
            putInt("mediumFlatOutRisk", settings.mediumFlatOutRisk)
            putInt("mediumFlatShrink", settings.mediumFlatShrink)

            putInt("hardFlatNetRisk", settings.hardFlatNetRisk)
            putInt("hardFlatOutRisk", settings.hardFlatOutRisk)
            putInt("hardFlatShrink", settings.hardFlatShrink)

            putInt("softLobNetRisk", settings.softLobNetRisk)
            putInt("softLobOutRisk", settings.softLobOutRisk)
            putInt("softLobShrink", settings.softLobShrink)

            putInt("mediumLobNetRisk", settings.mediumLobNetRisk)
            putInt("mediumLobOutRisk", settings.mediumLobOutRisk)
            putInt("mediumLobShrink", settings.mediumLobShrink)

            putInt("hardLobNetRisk", settings.hardLobNetRisk)
            putInt("hardLobOutRisk", settings.hardLobOutRisk)
            putInt("hardLobShrink", settings.hardLobShrink)

            putInt("softSmashNetRisk", settings.softSmashNetRisk)
            putInt("softSmashOutRisk", settings.softSmashOutRisk)
            putInt("softSmashShrink", settings.softSmashShrink)

            putInt("mediumSmashNetRisk", settings.mediumSmashNetRisk)
            putInt("mediumSmashOutRisk", settings.mediumSmashOutRisk)
            putInt("mediumSmashShrink", settings.mediumSmashShrink)

            putInt("hardSmashNetRisk", settings.hardSmashNetRisk)
            putInt("hardSmashOutRisk", settings.hardSmashOutRisk)
            putInt("hardSmashShrink", settings.hardSmashShrink)

            // Flight settings
            putFloat("softFlatFlight", settings.softFlatFlight)
            putFloat("mediumFlatFlight", settings.mediumFlatFlight)
            putFloat("hardFlatFlight", settings.hardFlatFlight)

            putFloat("softLobFlight", settings.softLobFlight)
            putFloat("mediumLobFlight", settings.mediumLobFlight)
            putFloat("hardLobFlight", settings.hardLobFlight)

            putFloat("softSmashFlight", settings.softSmashFlight)
            putFloat("mediumSmashFlight", settings.mediumSmashFlight)
            putFloat("hardSmashFlight", settings.hardSmashFlight)

            apply()
        }
    }

    /**
     * Update the current settings instance (without saving to prefs).
     * Use for receiving settings from network.
     */
    fun updateCurrent(settings: SwingSettings) {
        current = settings
    }

    /**
     * Reset to default settings and save.
     */
    fun resetToDefaults(): SwingSettings {
        current = SwingSettings.createDefault()
        saveToPrefs(current)
        return current
    }

    /**
     * Get flattened swing settings for network sync (36 integers).
     */
    fun getFlattenedSettings(): List<Int> {
        val s = current
        return listOf(
            s.softFlatNetRisk, s.softFlatOutRisk, s.softFlatShrink, (s.softFlatFlight * 100).toInt(),
            s.mediumFlatNetRisk, s.mediumFlatOutRisk, s.mediumFlatShrink, (s.mediumFlatFlight * 100).toInt(),
            s.hardFlatNetRisk, s.hardFlatOutRisk, s.hardFlatShrink, (s.hardFlatFlight * 100).toInt(),

            s.softLobNetRisk, s.softLobOutRisk, s.softLobShrink, (s.softLobFlight * 100).toInt(),
            s.mediumLobNetRisk, s.mediumLobOutRisk, s.mediumLobShrink, (s.mediumLobFlight * 100).toInt(),
            s.hardLobNetRisk, s.hardLobOutRisk, s.hardLobShrink, (s.hardLobFlight * 100).toInt(),

            s.softSmashNetRisk, s.softSmashOutRisk, s.softSmashShrink, (s.softSmashFlight * 100).toInt(),
            s.mediumSmashNetRisk, s.mediumSmashOutRisk, s.mediumSmashShrink, (s.mediumSmashFlight * 100).toInt(),
            s.hardSmashNetRisk, s.hardSmashOutRisk, s.hardSmashShrink, (s.hardSmashFlight * 100).toInt()
        )
    }

    /**
     * Update current settings from a flattened list (36 integers, from network sync).
     */
    fun updateFromFlattenedSettings(list: List<Int>) {
        if (list.size != 36) return

        var i = 0
        current = current.copy(
            softFlatNetRisk = list[i++], softFlatOutRisk = list[i++], softFlatShrink = list[i++], softFlatFlight = list[i++] / 100f,
            mediumFlatNetRisk = list[i++], mediumFlatOutRisk = list[i++], mediumFlatShrink = list[i++], mediumFlatFlight = list[i++] / 100f,
            hardFlatNetRisk = list[i++], hardFlatOutRisk = list[i++], hardFlatShrink = list[i++], hardFlatFlight = list[i++] / 100f,

            softLobNetRisk = list[i++], softLobOutRisk = list[i++], softLobShrink = list[i++], softLobFlight = list[i++] / 100f,
            mediumLobNetRisk = list[i++], mediumLobOutRisk = list[i++], mediumLobShrink = list[i++], mediumLobFlight = list[i++] / 100f,
            hardLobNetRisk = list[i++], hardLobOutRisk = list[i++], hardLobShrink = list[i++], hardLobFlight = list[i++] / 100f,

            softSmashNetRisk = list[i++], softSmashOutRisk = list[i++], softSmashShrink = list[i++], softSmashFlight = list[i++] / 100f,
            mediumSmashNetRisk = list[i++], mediumSmashOutRisk = list[i++], mediumSmashShrink = list[i++], mediumSmashFlight = list[i++] / 100f,
            hardSmashNetRisk = list[i++], hardSmashOutRisk = list[i++], hardSmashShrink = list[i++], hardSmashFlight = list[i++] / 100f
        )
    }

    /**
     * Update settings from a legacy flattened list (27 integers, without flight times).
     */
    fun updateFromLegacyFlattenedSettings(list: List<Int>) {
        if (list.size != 27) return

        var i = 0
        current = current.copy(
            softFlatNetRisk = list[i++], softFlatOutRisk = list[i++], softFlatShrink = list[i++],
            mediumFlatNetRisk = list[i++], mediumFlatOutRisk = list[i++], mediumFlatShrink = list[i++],
            hardFlatNetRisk = list[i++], hardFlatOutRisk = list[i++], hardFlatShrink = list[i++],

            softLobNetRisk = list[i++], softLobOutRisk = list[i++], softLobShrink = list[i++],
            mediumLobNetRisk = list[i++], mediumLobOutRisk = list[i++], mediumLobShrink = list[i++],
            hardLobNetRisk = list[i++], hardLobOutRisk = list[i++], hardLobShrink = list[i++],

            softSmashNetRisk = list[i++], softSmashOutRisk = list[i++], softSmashShrink = list[i++],
            mediumSmashNetRisk = list[i++], mediumSmashOutRisk = list[i++], mediumSmashShrink = list[i++],
            hardSmashNetRisk = list[i++], hardSmashOutRisk = list[i++], hardSmashShrink = list[i++]
        )
    }
}
