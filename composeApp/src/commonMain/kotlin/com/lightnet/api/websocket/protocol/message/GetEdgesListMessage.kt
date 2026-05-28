package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

class GetEdgesListMessage : Message(MessageType.GET_EDGES_LIST) {
    override fun encodePayload(writer: ByteWriter) = Unit
}
