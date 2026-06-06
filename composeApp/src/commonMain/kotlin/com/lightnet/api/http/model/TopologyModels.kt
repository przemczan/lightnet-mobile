package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

/** `GET /api/topology` — per-device topology config (scene-portability §4.1, §5). */
@Serializable
data class TopologyResponse(
    val logicalRoot: Int = 0,
    val tags: Map<String, List<String>> = emptyMap(),
)

/** `PUT /api/topology/root` body. `logicalRoot` 0 resets to the physical root. */
@Serializable
data class LogicalRootRequest(
    val logicalRoot: Int,
)
