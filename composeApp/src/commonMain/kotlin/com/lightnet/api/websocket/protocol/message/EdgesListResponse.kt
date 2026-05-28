package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel

class EdgesListResponse(private val edges: List<PanelEdgeInfoModel>) : Message(MessageType.EDGES_LIST) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeU16Le(edges.size)
        for (edge in edges) {
            writer.writeU16Le(edge.panelId)
            writer.writeU16Le(edge.edgeIndex)
            writer.writeU16Le(edge.connectedPanelId)
            writer.writeU16Le(edge.connectedEdgeIndex)
        }
    }
}
