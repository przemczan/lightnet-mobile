package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.model.PanelStateModel

class PanelsStatesResponse(private val states: List<PanelStateModel>) : Message(MessageType.PANELS_STATES) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU16Le(states.size)
        for (state in states) {
            writer.writeU16Le(state.panelId)
            writer.writeU8(if (state.on) 1 else 0)
            writer.writeU8(state.color.r)
            writer.writeU8(state.color.g)
            writer.writeU8(state.color.b)
        }
    }
}
