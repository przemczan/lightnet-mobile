package com.lightnet.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow

class DemoSettings(private val settings: Settings) {
    val demoEnabled = MutableStateFlow(settings.getBoolean(KEY_ENABLED, false))
    val demoPanelCount = MutableStateFlow(settings.getInt(KEY_PANEL_COUNT, 10))

    fun setEnabled(enabled: Boolean) {
        demoEnabled.value = enabled
        settings.putBoolean(KEY_ENABLED, enabled)
    }

    fun setPanelCount(count: Int) {
        val clamped = count.coerceIn(1, 100)
        demoPanelCount.value = clamped
        settings.putInt(KEY_PANEL_COUNT, clamped)
    }

    companion object {
        const val KEY_ENABLED = "demo_enabled"
        const val KEY_PANEL_COUNT = "demo_panel_count"
    }
}
