package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

class GetPanelsStatesMessage : Message(MessageType.GET_PANELS_STATES) {
    override fun encodePayload(writer: ByteWriter) = Unit
}
