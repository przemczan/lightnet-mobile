package com.lightnet.device

import com.lightnet.api.websocket.Connector
import com.lightnet.api.websocket.ConnectorState
import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

data class DeviceSnapshot(
    val panels: List<LightnetDevicePanel>,
    val layouts: List<PanelLayout>,
)

class LightnetDevice(
    val connector: Connector,
    private val edgeLength: Double = 100.0,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val messageApiService  = MessageApiService(connector, scope)
    private val panelsListService  = PanelsListService(messageApiService, scope)
    private val panelsStatesService = PanelsStatesService(messageApiService, panelsListService, scope)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _snapshot = MutableStateFlow<DeviceSnapshot?>(null)
    val snapshot: StateFlow<DeviceSnapshot?> = _snapshot

    init {
        scope.launch {
            connector.state.collect { cs ->
                _connectionState.value = when (cs) {
                    ConnectorState.IDLE          -> ConnectionState.IDLE
                    ConnectorState.CONNECTING    -> ConnectionState.CONNECTING
                    ConnectorState.CONNECTED     -> ConnectionState.CONNECTED
                    ConnectorState.DISCONNECTED,
                    ConnectorState.FAILED        -> ConnectionState.DISCONNECTED
                }
                if (cs == ConnectorState.CONNECTED) panelsListService.load()
                if (cs == ConnectorState.DISCONNECTED || cs == ConnectorState.FAILED) _snapshot.value = null
            }
        }
        scope.launch {
            panelsListService.panels.collect { panels ->
                // null = still loading; non-null (even emptyList) = response received
                _snapshot.value = panels?.let { buildSnapshot(it) }
            }
        }
    }

    fun load() {
        connector.disconnect()
        connector.connect()
        // panelsListService.load() is now triggered by the CONNECTED state transition above
    }

    fun close() {
        connector.close()
        scope.cancel()
    }

    private fun buildSnapshot(panelsList: List<PanelInfo>): DeviceSnapshot {
        val layouts = PanelsLayoutService.generateLayout(panelsList, edgeLength)
        val panels = panelsList.map { info ->
            LightnetDevicePanel(
                messageApiService = messageApiService,
                info              = info,
                layout            = layouts.first { it.panelId == info.id },
                panelsStates      = panelsStatesService.states,
                scope             = scope,
            )
        }
        return DeviceSnapshot(panels, layouts)
    }
}
