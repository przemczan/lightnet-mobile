package com.lightnet.discovery

data class DiscoveredDevice(
    val name: String,
    val host: String,  // resolved IP at discovery time
    val port: Int,
    val hostName: String? = null,  // mDNS hostname (e.g., lightnet-3F2A.local)
)
