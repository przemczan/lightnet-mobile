package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigurationResponse(
    val powerStateOnBoot: Int,
)

@Serializable
data class ConfigurationRequest(
    val powerStateOnBoot: Int? = null,
)
