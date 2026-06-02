package com.lightnet.settings

import com.russhwolf.settings.Settings

object AppPreferences {
    private var settings: Settings? = null
    private val devicePrefsCache = mutableMapOf<String, DevicePreferences>()

    fun init(settings: Settings) {
        this.settings = settings
    }

    fun forDevice(deviceKey: String): DevicePreferences =
        devicePrefsCache.getOrPut(deviceKey) {
            DevicePreferences(
                requireNotNull(settings) { "AppPreferences not initialized" },
                deviceKey,
            )
        }
}
