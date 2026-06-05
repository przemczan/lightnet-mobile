package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType

// Enables/disables MIRROR_BATCH streaming for this client. Mirroring is opt-in: the
// controller sends nothing until enabled, and on enable replays a one-shot snapshot of the
// current animation state so the preview is correct immediately.
// Payload: enabled(u8) = 1 byte
class SetMirrorMessage(private val enabled: Boolean) : Message(MessageType.SET_MIRROR) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU8(if (enabled) 1 else 0)
    }
}
