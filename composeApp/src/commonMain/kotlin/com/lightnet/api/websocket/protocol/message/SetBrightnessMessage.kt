package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

// Payload: address(u8) + brightness(u8) = 2 bytes
class SetBrightnessMessage(private val panelId: Int, private val brightness: Int) : Message(MessageType.SET_BRIGHTNESS) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU8(panelId)
        writer.writeU8(brightness)
    }
}
