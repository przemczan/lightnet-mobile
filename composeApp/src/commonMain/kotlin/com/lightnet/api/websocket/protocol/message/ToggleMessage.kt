package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

// Payload: address(u8) + on(u8) = 2 bytes
class ToggleMessage(private val panelId: Int, private val on: Boolean) : Message(MessageType.TOGGLE) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU8(panelId)
        writer.writeU8(if (on) 1 else 0)
    }
}
