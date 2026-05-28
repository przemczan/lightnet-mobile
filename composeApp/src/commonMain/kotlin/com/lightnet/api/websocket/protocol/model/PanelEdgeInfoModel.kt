package com.lightnet.api.websocket.protocol.model

data class PanelEdgeInfoModel(
    val panelId: Int,
    val edgeIndex: Int,
    val connectedPanelId: Int,
    val connectedEdgeIndex: Int,
)
