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
import kotlinx.serialization.json.JsonElement
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

    // Modifier layers — transform the colour composited below them (scene-authoring §5.1).
    const val MOD_BRIGHTNESS = "MOD_BRIGHTNESS"
    const val MOD_SATURATION = "MOD_SATURATION"
    const val MOD_HUE_SHIFT = "MOD_HUE_SHIFT"
    const val MOD_INVERT = "MOD_INVERT"
}

object RunnerType {
    const val WAVE = "WAVE"
    const val RIPPLE = "RIPPLE"
    const val CHASE = "CHASE"
    const val WHEEL = "WHEEL"
}

/** What a runner's sweep modulates (scene-authoring §7.3 `animates`). Default is `color`. */
object RunnerTarget {
    const val COLOR = "color"
    const val BRIGHTNESS = "brightness"
    const val SATURATION = "saturation"
    const val HUE = "hue"
    const val INVERT = "invert"

    val all = listOf(COLOR, BRIGHTNESS, SATURATION, HUE, INVERT)
}

/** Layer blend modes (firmware ComposeMode). Runner layers default to `max`, others to `opaque`. */
object BlendMode {
    const val OPAQUE = "opaque"
    const val ADD = "add"
    const val MAX = "max"
    const val MULTIPLY = "multiply"
    const val SCREEN = "screen"
    const val DARKEN = "darken"
    const val OVERLAY = "overlay"
    const val DIFFERENCE = "difference"
    const val SUBTRACT = "subtract"

    val all = listOf(OPAQUE, ADD, MAX, MULTIPLY, SCREEN, DARKEN, OVERLAY, DIFFERENCE, SUBTRACT)
}

/** Runner directionality source tokens (scene-authoring §8). */
object RunnerSourceToken {
    const val ROOT = "root"
    const val LEAVES = "leaves"
    const val ALL = "all"
    const val GEOMETRIC = "geometric"
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

/**
 * A layer's `async` field. The firmware accepts `true`/`false` (legacy) as well as the
 * string tokens `"loop"` (async, blocks scene) and `"free"` (async, non-blocking).
 * Reads all three forms; writes `true` for "loop" (wire-compat with older firmware)
 * and the string `"free"` for free-running.
 */
internal object AsyncValueSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AsyncValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        val el: JsonElement = if (value == "free") JsonPrimitive("free") else JsonPrimitive(true)
        (encoder as JsonEncoder).encodeJsonElement(el)
    }

    override fun deserialize(decoder: Decoder): String {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        return if (el is JsonPrimitive && el.isString) el.content else "loop"
    }
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
    // Modifier steps (MOD_*): animate a 0–255 scalar from → to.
    val from: Int? = null,
    val to: Int? = null,
    // Runner directionality (WAVE/RIPPLE/CHASE) — never combine with `params`.
    val source: String? = null,
    val directionality: String? = null,  // "topology" (default) or "geometric"
    val angle: Int? = null,              // geometric sweep direction in degrees
    val reverse: Boolean? = null,
    val waveWidth: Int? = null,
    val rippleWidth: Int? = null,
    // WAVE/RIPPLE/CHASE: replay as a continuous train of evenly-spaced sweeps (colour-only).
    val repeat: Boolean? = null,
    // Number of waves/rings/chases in flight simultaneously (waves per duration). 0 or 1 = one wave.
    val repeatCount: Int? = null,
    // WHEEL-only: blade thickness in degrees (shares the wire slot with waveWidth/rippleWidth)
    // and number of evenly-spaced rotating blades, 1-6.
    val thickness: Int? = null,
    val lines: Int? = null,
    // What the runner's sweep modulates (RunnerTarget) and its peak intensity for non-color targets.
    val animates: String? = null,
    val amount: Int? = null,
    // Modifier envelope shape ("fall" default, "rise", "bell") — only meaningful for non-color animates.
    val shape: String? = null,
)

@Serializable
data class SceneLayer(
    @Serializable(with = GroupIdSerializer::class) val group: String,
    val panels: PanelTarget = PanelTarget.All,
    val palette: String? = null,
    val sequence: List<SceneStep>,
    val startAfter: String? = null,
    @Serializable(with = AsyncValueSerializer::class) val async: String? = null,
    val blend: String? = null,   // BlendMode — how this layer composites over the layers below
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
    val background: String? = null,  // compositor base colour (#RRGGBB), default black
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
