package com.lightnet.discovery

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun generateDeviceId(): String = Uuid.random().toString()

data class SavedDevice(
    val id: String,
    val name: String,
    /** Override IP/hostname entered by the user. Empty string means not set — use hostName or lastIP. */
    val host: String = "",
    val port: Int,
    /** mDNS hostname from discovery (e.g., lightnet-3F2A.local). Non-editable by the user. */
    val hostName: String? = null,
    /** Last successfully connected IP. Populated from discovery; updated when connecting via override IP. */
    val lastIP: String? = null,
    /** Cached panel count from last successful connection. */
    val panelCount: Int? = null,
)

/** Effective host for HTTP/WebSocket connections (same priority as SocketConnector). */
val SavedDevice.effectiveHost: String
    get() = host.ifEmpty { lastIP ?: hostName ?: "" }

/** Display address for UI (most descriptive first). */
fun SavedDevice.displayAddress(): String {
    val addr = hostName ?: host.ifEmpty { lastIP } ?: return ""
    return "$addr:$port"
}

fun SavedDevice.toDiscovered() = DiscoveredDevice(name, host.ifEmpty { lastIP ?: "" }, port, hostName)

/**
 * Identity match between a discovered device and a saved one — used to flag a discovered
 * device as already added. Prefers the mDNS hostName when both sides have one; otherwise
 * falls back to IP (the saved override host, or its last known IP). The friendly name is
 * never used, since the user is free to rename a device.
 */
fun DiscoveredDevice.isSameAs(saved: SavedDevice): Boolean {
    if (hostName != null && saved.hostName != null && hostName.equals(saved.hostName, ignoreCase = true)) return true
    if (host.isNotEmpty()) {
        if (saved.lastIP != null && host == saved.lastIP) return true
        if (saved.host.isNotEmpty() && host == saved.host) return true
    }
    return false
}
