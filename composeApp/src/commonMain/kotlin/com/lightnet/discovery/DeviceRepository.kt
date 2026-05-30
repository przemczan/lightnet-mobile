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
        val port = settings.getIntOrNull("${i}_port")   ?: return@mapNotNull null
        val host     = settings.getStringOrNull("${i}_host")     ?: ""
        val hostName = settings.getStringOrNull("${i}_hostName")
        val lastIP   = settings.getStringOrNull("${i}_lastIP")
        // Discard entries that have no usable address at all (shouldn't happen normally).
        if (host.isEmpty() && hostName == null && lastIP == null) return@mapNotNull null
        SavedDevice(name, host, port, hostName, lastIP)
    }

    fun add(device: SavedDevice) {
        if (getAll().any { it.name == device.name }) return
        val i = count
        persist(i, device)
        count = i + 1
    }

    fun remove(name: String) {
        val remaining = getAll().filter { it.name != name }
        clearAll()
        remaining.forEach { add(it) }
    }

    /** Replace the device identified by [originalName], preserving list order. */
    fun update(originalName: String, updated: SavedDevice) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.name == originalName }
        if (index < 0) {
            add(updated)
            return
        }
        if (updated.name != originalName && list.any { it.name == updated.name }) return
        list[index] = updated
        clearAll()
        list.forEach { add(it) }
    }

    /** Silently update the cached IP for the given device without touching other fields. */
    fun updateLastIP(name: String, ip: String) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.name == name }
        if (index < 0) return
        list[index] = list[index].copy(lastIP = ip)
        clearAll()
        list.forEach { add(it) }
    }

    private fun persist(i: Int, device: SavedDevice) {
        settings.putString("${i}_name", device.name)
        settings.putString("${i}_host", device.host)
        settings.putInt("${i}_port",    device.port)
        if (device.hostName != null) settings.putString("${i}_hostName", device.hostName)
        if (device.lastIP   != null) settings.putString("${i}_lastIP",   device.lastIP)
    }

    private fun clearAll() {
        repeat(count) { i ->
            settings.remove("${i}_name")
            settings.remove("${i}_host")
            settings.remove("${i}_port")
            settings.remove("${i}_hostName")
            settings.remove("${i}_lastIP")
        }
        count = 0
    }

    private companion object {
        const val KEY_COUNT = "device_count"
    }
}
