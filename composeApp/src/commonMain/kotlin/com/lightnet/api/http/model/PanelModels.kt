package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class PanelStateResponse(
    val address: Int,
    val on: Boolean,
    val color: String,
)

@Serializable
data class PanelEdgeResponse(
    val panel: Int,
    val edge: Int,
    val connectedPanel: Int,
    val connectedEdge: Int,
)
