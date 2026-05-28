package com.lightnet.discovery

import com.russhwolf.settings.Settings

// Persists the saved device list using multiplatform-settings.
// Settings is injected by the platform entry point:
//   Android: SharedPreferencesSettings(context.getSharedPreferences("lightnet", MODE_PRIVATE))
//   iOS:     NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
class DeviceRepository(private val settings: Settings) {

    private var count: Int
        get() = settings.getInt(KEY_COUNT, 0)
        set(v) = settings.putInt(KEY_COUNT, v)

    fun getAll(): List<SavedDevice> = (0 until count).mapNotNull { i ->
        val name = settings.getStringOrNull("${i}_name") ?: return@mapNotNull null
        val host = settings.getStringOrNull("${i}_host") ?: return@mapNotNull null
        val port = settings.getIntOrNull("${i}_port")   ?: return@mapNotNull null
        SavedDevice(name, host, port)
    }

    fun add(device: SavedDevice) {
        if (getAll().any { it.name == device.name }) return
        val i = count
        settings.putString("${i}_name", device.name)
        settings.putString("${i}_host", device.host)
        settings.putInt("${i}_port",   device.port)
        count = i + 1
    }

    fun remove(name: String) {
        val remaining = getAll().filter { it.name != name }
        clearAll()
        remaining.forEach { add(it) }
    }

    private fun clearAll() {
        repeat(count) { i ->
            settings.remove("${i}_name")
            settings.remove("${i}_host")
            settings.remove("${i}_port")
        }
        count = 0
    }

    private companion object {
        const val KEY_COUNT = "device_count"
    }
}
