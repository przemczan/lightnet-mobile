package com.lightnet.discovery

import kotlinx.coroutines.flow.Flow

interface ServiceDiscovery {
    val devices: Flow<List<DiscoveredDevice>>
    fun start()
    fun stop()
}
