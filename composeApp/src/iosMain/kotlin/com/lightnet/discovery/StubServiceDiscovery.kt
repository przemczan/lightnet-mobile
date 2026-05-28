package com.lightnet.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// Placeholder until Bonjour implementation is added in the iOS phase.
class StubServiceDiscovery : ServiceDiscovery {
    override val devices: Flow<List<DiscoveredDevice>> = MutableStateFlow(emptyList())
    override fun start() {}
    override fun stop() {}
}
