package com.lightnet.api.websocket

import com.lightnet.api.websocket.protocol.ByteReader
import com.lightnet.api.websocket.protocol.MessageParser
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.message.EdgesListResponse
import com.lightnet.api.websocket.protocol.message.PanelsStatesResponse
import com.lightnet.api.websocket.protocol.message.PongResponse
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import com.lightnet.api.websocket.protocol.model.PanelStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MockConnector(
    private val panelCount: Int = 6,
    private val minEdges: Int = 3,
    private val maxEdges: Int = 3,
) : Connector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ConnectorState.IDLE)
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)

    private var generatedEdges: List<PanelEdgeInfoModel> = emptyList()
    private val panelStates = mutableMapOf<Int, PanelStateModel>()

    override val state: StateFlow<ConnectorState> = _state
    override val incoming: Flow<ByteArray> = _incoming

    override fun connect() {
        _state.value = ConnectorState.CONNECTED
    }

    override fun disconnect() {
        _state.value = ConnectorState.DISCONNECTED
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
                MessageType.GET_EDGES_LIST   -> respondWithEdgesList()
                MessageType.GET_PANELS_STATES -> respondWithPanelsStates()
                MessageType.TOGGLE            -> handleToggle(result.message.payload)
                MessageType.SET_COLOR         -> handleSetColor(result.message.payload)
                MessageType.PING              -> _incoming.emit(PongResponse().encode())
                else                          -> Unit
            }
        }
    }

    private suspend fun respondWithEdgesList() {
        generatedEdges = PanelsGenerator.generateEdges(panelCount, minEdges, maxEdges)
        val ids = generatedEdges.map { it.panelId }.toSet()
        ids.forEach { id ->
            panelStates[id] = PanelStateModel(id, false, ColorRgbModel(255, 255, 255))
        }
        _incoming.emit(EdgesListResponse(generatedEdges).encode())
    }

    private suspend fun respondWithPanelsStates() {
        _incoming.emit(PanelsStatesResponse(panelStates.values.toList()).encode())
    }

    private suspend fun handleToggle(payload: ByteArray) {
        val reader = ByteReader(payload)
        val panelId = reader.readU8()
        val on = reader.readU8() != 0
        panelStates[panelId] = panelStates[panelId]?.copy(on = on) ?: return
        respondWithPanelsStates()
    }

    private suspend fun handleSetColor(payload: ByteArray) {
        val reader = ByteReader(payload)
        val panelId = reader.readU8()
        val color = ColorRgbModel(reader.readU8(), reader.readU8(), reader.readU8())
        panelStates[panelId] = panelStates[panelId]?.copy(color = color) ?: return
        respondWithPanelsStates()
    }
}
