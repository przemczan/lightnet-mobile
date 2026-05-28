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

@Serializable
data class ColorsResponse(
    val primary: String,
    val secondary: String,
    val tertiary: String,
)

@Serializable
data class ColorsRequest(
    val primary: String? = null,
    val secondary: String? = null,
    val tertiary: String? = null,
)
