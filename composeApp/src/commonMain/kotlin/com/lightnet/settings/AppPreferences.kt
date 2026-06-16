package com.lightnet.settings

import com.russhwolf.settings.Settings

object AppPreferences {
    private var _settings: Settings? = null
    private val devicePrefsCache = mutableMapOf<String, DevicePreferences>()

    val settings: Settings get() = requireNotNull(_settings) { "AppPreferences not initialized" }

    lateinit var scenes: SceneRepository
        private set

    lateinit var demo: DemoSettings
        private set

    fun init(settings: Settings) {
        _settings = settings
        scenes = SceneRepository(settings)
        demo = DemoSettings(settings)
    }

    fun forDevice(deviceKey: String): DevicePreferences =
        devicePrefsCache.getOrPut(deviceKey) {
            DevicePreferences(settings, deviceKey)
        }
}
