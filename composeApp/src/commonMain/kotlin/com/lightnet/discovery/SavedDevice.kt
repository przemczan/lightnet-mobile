package com.lightnet.discovery

data class SavedDevice(
    val name: String,
    /** Override IP/hostname entered by the user. Empty string means not set — use hostName or lastIP. */
    val host: String = "",
    val port: Int,
    /** mDNS hostname from discovery (e.g., lightnet-3F2A.local). Non-editable by the user. */
    val hostName: String? = null,
    /** Last successfully connected IP. Populated from discovery; updated when connecting via override IP. */
    val lastIP: String? = null,
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
