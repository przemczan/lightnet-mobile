package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class AppearanceResponse(
    val brightness: Int,
    val baseColors: List<String>,
    val palette: String,
)

@Serializable
data class AppearanceRequest(
    val brightness: Int? = null,
    val baseColors: List<String>? = null,
    val palette: String? = null,
)

