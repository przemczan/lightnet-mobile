package com.lightnet.api.http.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

object AnimationType {
    const val SOLID = "SOLID"
    const val FADE = "FADE"
    const val TRANSITION = "TRANSITION"
    const val BREATHE = "BREATHE"
    const val PULSE = "PULSE"
    const val BLINK = "BLINK"
    const val HUE_CYCLE = "HUE_CYCLE"
    const val STROBE = "STROBE"
    const val REACTIVE = "REACTIVE"
}

object RunnerType {
    const val WAVE = "WAVE"
    const val RIPPLE = "RIPPLE"
    const val CHASE = "CHASE"
}

@Serializable
data class SceneColors(
    val primary: String? = null,
    val secondary: String? = null,
    val tertiary: String? = null,
)

@Serializable
data class SceneStep(
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
data class SceneLayer(
    val group: Int,
    val panels: PanelTarget,
    val palette: String? = null,
    val sequence: List<SceneStep>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SceneJson(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schemaVersion: Int = 1,
    val name: String? = null,
    val loop: Boolean? = null,
    val speed: Float? = null,
    val colors: SceneColors? = null,
    val palette: String? = null,
    val layers: List<SceneLayer>,
)

@Serializable
data class SceneInfo(
    val name: String,
    val size: Int? = null,
)

@Serializable
data class SceneStatus(
    val playing: Boolean,
    val scene: String? = null,
    val loop: Boolean? = null,
    val layers: Int? = null,
    val speed: Float? = null,
)
