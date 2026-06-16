package com.lightnet.demo

import com.lightnet.api.websocket.Connector
import com.lightnet.api.websocket.ConnectorState
import com.lightnet.api.websocket.protocol.ByteReader
import com.lightnet.api.websocket.protocol.MessageParser
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.message.EdgesListResponse
import com.lightnet.api.websocket.protocol.message.PanelsStatesResponse
import com.lightnet.api.websocket.protocol.message.PongResponse
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import com.lightnet.api.websocket.protocol.model.PanelStateModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DemoConnector(
    panelCount: Int = 10,
    private val settings: Settings,
) : Connector {
    private var panelCount: Int = panelCount
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ConnectorState.IDLE)
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)

    private var edges: List<PanelEdgeInfoModel> = emptyList()
    private val panelStates = mutableMapOf<Int, PanelStateModel>()

    override val state: StateFlow<ConnectorState> = _state
    override val incoming: Flow<ByteArray> = _incoming

    override fun connect() {
        _state.value = ConnectorState.CONNECTING
        _state.value = ConnectorState.CONNECTED
    }

    override fun disconnect() {
        _state.value = ConnectorState.DISCONNECTED
    }

    /** Clear the persisted topology so the next [connect] generates a fresh layout. */
    fun resetLayout(newCount: Int) {
        panelCount = newCount
        edges = emptyList()
        panelStates.clear()
        settings.remove(KEY_PANEL_COUNT)
        settings.remove(KEY_EDGES)
        settings.remove(KEY_PANEL_STATES)
    }

    override fun close() {
        disconnect()
        scope.cancel()
    }

    override fun send(data: ByteArray) {
        val result = MessageParser.parse(data)
        if (result !is MessageParser.Result.Success) return
        scope.launch {
            when (result.message.type) {
                MessageType.GET_EDGES_LIST    -> {
                    if (edges.isEmpty()) {
                        loadOrGenerateTopology()
                        loadOrInitPanelStates()
                    }
                    _incoming.emit(EdgesListResponse(edges).encode())
                }
                MessageType.GET_PANELS_STATES -> _incoming.emit(PanelsStatesResponse(panelStates.values.toList()).encode())
                MessageType.TOGGLE            -> handleToggle(result.message.payload)
                MessageType.SET_COLOR         -> handleSetColor(result.message.payload)
                MessageType.PING              -> _incoming.emit(PongResponse().encode())
                else                          -> Unit
            }
        }
    }

    private suspend fun handleToggle(payload: ByteArray) {
        val reader = ByteReader(payload)
        val panelId = reader.readU8()
        val on = reader.readU8() != 0
        val updated = panelStates[panelId]?.copy(on = on) ?: return
        panelStates[panelId] = updated
        savePanelStates()
        _incoming.emit(PanelsStatesResponse(panelStates.values.toList()).encode())
    }

    private suspend fun handleSetColor(payload: ByteArray) {
        val reader = ByteReader(payload)
        val panelId = reader.readU8()
        val color = ColorRgbModel(reader.readU8(), reader.readU8(), reader.readU8())
        val updated = panelStates[panelId]?.copy(color = color) ?: return
        panelStates[panelId] = updated
        savePanelStates()
        _incoming.emit(PanelsStatesResponse(panelStates.values.toList()).encode())
    }

    private fun loadOrGenerateTopology() {
        // Only reuse persisted topology if the panel count matches; otherwise regenerate.
        val storedCount = settings.getIntOrNull(KEY_PANEL_COUNT)
        if (storedCount == panelCount) {
            val stored = settings.getStringOrNull(KEY_EDGES)
            if (stored != null) {
                edges = runCatching {
                    json.decodeFromString(edgesSerializer, stored).map { it.toModel() }
                }.getOrNull() ?: emptyList()
            }
        }
        if (edges.isEmpty()) {
            edges = DemoTopologyGenerator.generate(panelCount)
            saveEdges()
            settings.putInt(KEY_PANEL_COUNT, panelCount)
            settings.remove(KEY_PANEL_STATES) // reset states for new topology
        }
    }

    private fun loadOrInitPanelStates() {
        val stored = settings.getStringOrNull(KEY_PANEL_STATES)
        if (stored != null) {
            val loaded = runCatching {
                json.decodeFromString(statesSerializer, stored)
            }.getOrNull()
            if (loaded != null) {
                panelStates.clear()
                loaded.forEach { panelStates[it.panelId] = it.toModel() }
                return
            }
        }
        // Init all panels to white, off
        panelStates.clear()
        edges.map { it.panelId }.toSet().forEach { id ->
            panelStates[id] = PanelStateModel(id, on = false, color = ColorRgbModel(255, 255, 255))
        }
        savePanelStates()
    }

    private fun saveEdges() {
        settings.putString(KEY_EDGES, json.encodeToString(edgesSerializer, edges.map { it.toPersisted() }))
    }

    private fun savePanelStates() {
        settings.putString(KEY_PANEL_STATES, json.encodeToString(statesSerializer, panelStates.values.map { it.toPersisted() }))
    }

    // ── Serializable wrappers (avoid annotating shared protocol models) ───────

    @Serializable
    private data class PersistedEdge(
        val panelId: Int, val edgeIndex: Int,
        val connectedPanelId: Int, val connectedEdgeIndex: Int,
    ) {
        fun toModel() = PanelEdgeInfoModel(panelId, edgeIndex, connectedPanelId, connectedEdgeIndex)
    }

    @Serializable
    private data class PersistedState(
        val panelId: Int, val on: Boolean,
        val r: Int, val g: Int, val b: Int,
    ) {
        fun toModel() = PanelStateModel(panelId, on, ColorRgbModel(r, g, b))
    }

    private fun PanelEdgeInfoModel.toPersisted() =
        PersistedEdge(panelId, edgeIndex, connectedPanelId, connectedEdgeIndex)

    private fun PanelStateModel.toPersisted() =
        PersistedState(panelId, on, color.r, color.g, color.b)

    companion object {
        private const val KEY_PANEL_COUNT  = "demo_connector_panel_count"
        private const val KEY_EDGES        = "demo_connector_edges"
        private const val KEY_PANEL_STATES = "demo_connector_panel_states"

        private val json = Json { ignoreUnknownKeys = true }
        private val edgesSerializer  = ListSerializer(PersistedEdge.serializer())
        private val statesSerializer = ListSerializer(PersistedState.serializer())
    }
}
