package com.lightnet.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow

class DevicePreferences(private val settings: Settings, deviceKey: String) {
    private val prefix = "device_${deviceKey.replace(Regex("[^a-zA-Z0-9]"), "_")}"

    val visualizerBgColorEnabled = MutableStateFlow(
        settings.getBoolean("${prefix}_viz_bg_enabled", false)
    )
    val visualizerBgColor = MutableStateFlow<String?>(
        settings.getStringOrNull("${prefix}_viz_bg_color")
    )
    /** Visualizer view rotation in degrees. */
    val visualizerRotation = MutableStateFlow(
        settings.getFloat("${prefix}_viz_rotation", 0f)
    )

    fun setVisualizerRotation(degrees: Float) {
        visualizerRotation.value = degrees
        settings.putFloat("${prefix}_viz_rotation", degrees)
    }

    fun setVisualizerBgEnabled(enabled: Boolean) {
        visualizerBgColorEnabled.value = enabled
        settings.putBoolean("${prefix}_viz_bg_enabled", enabled)
    }

    fun setVisualizerBgColor(hex: String) {
        visualizerBgColor.value = hex
        settings.putString("${prefix}_viz_bg_color", hex)
    }
}
