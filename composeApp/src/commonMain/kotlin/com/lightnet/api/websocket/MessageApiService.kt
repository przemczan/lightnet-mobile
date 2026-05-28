package com.lightnet.api.websocket

import com.lightnet.api.websocket.protocol.MessageParser
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.message.Message
import com.lightnet.api.websocket.protocol.message.decodeEdgesList
import com.lightnet.api.websocket.protocol.message.decodePanelsStates
import com.lightnet.api.websocket.protocol.message.IncomingMessage
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import com.lightnet.api.websocket.protocol.model.PanelStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MessageApiService(
    private val connector: Connector,
    scope: CoroutineScope,
) {
    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 32)

    val edgesList: Flow<List<PanelEdgeInfoModel>> = _messages
        .filter { it.type == MessageType.EDGES_LIST }
        .map { decodeEdgesList(it.payload) }

    val panelsStates: Flow<List<PanelStateModel>> = _messages
        .filter { it.type == MessageType.PANELS_STATES }
        .map { decodePanelsStates(it.payload) }

    init {
        scope.launch {
            connector.incoming.collect { bytes ->
                val result = MessageParser.parse(bytes)
                if (result is MessageParser.Result.Success) _messages.emit(result.message)
            }
        }
    }

    fun send(message: Message) = connector.send(message.encode())
}
