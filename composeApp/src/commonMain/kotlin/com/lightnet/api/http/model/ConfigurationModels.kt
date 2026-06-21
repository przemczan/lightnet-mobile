package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigurationResponse(
    val powerStateOnBoot: Int = 0,
    val logicalRoot: Int = 0,
)

@Serializable
data class ConfigurationRequest(
    val powerStateOnBoot: Int? = null,
    val logicalRoot: Int? = null,
)
