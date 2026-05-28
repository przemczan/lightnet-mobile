package com.lightnet.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private const val SERVICE_TYPE = "_lightnet._tcp"

class NsdServiceDiscovery(context: Context) : ServiceDiscovery {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val devices: Flow<List<DiscoveredDevice>> = _devices

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun start() {
        _devices.value = emptyList()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    nsdManager.resolveService(serviceInfo, createResolveListener())
                } catch (_: IllegalArgumentException) {
                    // Another resolution already in progress; service will be re-discovered
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _devices.value = _devices.value.filter { it.name != serviceInfo.serviceName }
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stop() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
            discoveryListener = null
        }
        _devices.value = emptyList()
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress ?: return
            val device = DiscoveredDevice(
                name = serviceInfo.serviceName,
                host = host,
                port = serviceInfo.port,
            )
            _devices.value = (_devices.value + device).distinctBy { it.name }
        }
    }
}
