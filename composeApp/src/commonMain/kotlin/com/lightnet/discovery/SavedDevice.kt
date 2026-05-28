package com.lightnet.discovery

data class SavedDevice(
    val name: String,
    val host: String,
    val port: Int,
)

fun SavedDevice.toDiscovered() = DiscoveredDevice(name, host, port)
