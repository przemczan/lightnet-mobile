package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

class PingMessage : Message(MessageType.PING) {
    override fun encodePayload(writer: ByteWriter) = Unit
}
