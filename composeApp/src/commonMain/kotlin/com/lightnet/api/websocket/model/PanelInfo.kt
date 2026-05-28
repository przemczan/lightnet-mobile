package com.lightnet.api.websocket.model

class PanelInfo(
    val id: Int,
    val edges: MutableList<EdgeInfo> = mutableListOf(),
    var rootEdge: EdgeInfo? = null,
)
