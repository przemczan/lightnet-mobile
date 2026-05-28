package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteReader
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel

// Inbound only. Payload: length(u16) + PanelEdgeInfoModel[length]
// Each PanelEdgeInfoModel: panelId(u16) + edgeIndex(u16) + connectedPanelId(u16) + connectedEdgeIndex(u16) = 8 bytes
fun decodeEdgesList(payload: ByteArray): List<PanelEdgeInfoModel> {
    val reader = ByteReader(payload)
    val count = reader.readU16Le()
    return List(count) {
        PanelEdgeInfoModel(
            panelId            = reader.readU16Le(),
            edgeIndex          = reader.readU16Le(),
            connectedPanelId   = reader.readU16Le(),
            connectedEdgeIndex = reader.readU16Le(),
        )
    }
}
