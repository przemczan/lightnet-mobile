package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class AnimationPlayRequest(
    val group: Int,
    val panels: PanelTarget,
    val type: String? = null,
    val runner: String? = null,
    val color: ColorRef? = null,
    val colorFrom: ColorRef? = null,
    val colorTo: ColorRef? = null,
    val brightnessFrom: Int? = null,
    val brightnessTo: Int? = null,
    val duration: Int? = null,
    val loop: Boolean? = null,
    val pingpong: Boolean? = null,
    val params: List<Int>? = null,
)

@Serializable
data class AnimationTriggerRequest(
    val group: Int,
    val value: Int,
)
