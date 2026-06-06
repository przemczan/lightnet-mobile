package com.lightnet.settings

import com.russhwolf.settings.Settings

object AppPreferences {
    private var settings: Settings? = null
    private val devicePrefsCache = mutableMapOf<String, DevicePreferences>()

    lateinit var scenes: SceneRepository
        private set

    fun init(settings: Settings) {
        this.settings = settings
        scenes = SceneRepository(settings)
    }

    fun forDevice(deviceKey: String): DevicePreferences =
        devicePrefsCache.getOrPut(deviceKey) {
            DevicePreferences(
                requireNotNull(settings) { "AppPreferences not initialized" },
                deviceKey,
            )
        }
}
