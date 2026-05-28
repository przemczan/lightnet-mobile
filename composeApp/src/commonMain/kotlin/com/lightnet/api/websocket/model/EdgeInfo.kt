package com.lightnet.api.websocket.model

class EdgeInfo(
    val index: Int,
    val panel: PanelInfo,
    var connectedEdge: EdgeInfo? = null,
)
