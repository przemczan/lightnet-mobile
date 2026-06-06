package com.lightnet.api.http.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

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

/** Runner directionality source tokens (scene-authoring §8). */
object RunnerSourceToken {
    const val ROOT = "root"
    const val LEAVES = "leaves"
    const val ALL = "all"
    fun panel(index: Int) = "panel:$index"
}

/**
 * A layer's `group`. The firmware accepts either a name string or a number 1–254; named
 * groups are required for `startAfter` (numeric groups are never interned). This serializer
 * round-trips both wire forms into a [String], so `"group": 1` loads as `"1"` and the editor
 * always re-saves a (named) string — playback-equivalent.
 */
internal object GroupIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("GroupId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive(value))
    }

    override fun deserialize(decoder: Decoder): String =
        (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content
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
    val duration: Int? = null,
    val loop: Boolean? = null,
    val pingpong: Boolean? = null,
    val params: List<Int>? = null,
    // Runner directionality (WAVE/RIPPLE/CHASE) — never combine with `params`.
    val source: String? = null,
    val reverse: Boolean? = null,
    val waveWidth: Int? = null,
    val rippleWidth: Int? = null,
)

@Serializable
data class SceneLayer(
    @Serializable(with = GroupIdSerializer::class) val group: String,
    val panels: PanelTarget = PanelTarget.All,
    val palette: String? = null,
    val sequence: List<SceneStep>,
    val startAfter: String? = null,
    val async: Boolean? = null,
    val fallback: PanelTarget? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SceneJson(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schemaVersion: Int = 2,
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
