package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

/** Mirrors the controller's reply to [PingMessage] — used by [com.lightnet.api.websocket.MockConnector]. */
class PongResponse : Message(MessageType.PONG) {
    override fun encodePayload(writer: ByteWriter) = Unit
}
