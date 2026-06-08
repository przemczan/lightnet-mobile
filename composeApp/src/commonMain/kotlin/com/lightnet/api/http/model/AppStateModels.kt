package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

/** Response body for `GET /api/state`. */
@Serializable
data class AppStateBody(
    val isOn: Boolean,
    val lastPlayedScene: String = "",
)
