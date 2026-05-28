package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.model.ColorRgbModel

// Payload: address(u8) + r(u8) + g(u8) + b(u8) = 4 bytes
class SetColorMessage(private val panelId: Int, private val color: ColorRgbModel) : Message(MessageType.SET_COLOR) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU8(panelId)
        writer.writeU8(color.r)
        writer.writeU8(color.g)
        writer.writeU8(color.b)
    }
}
