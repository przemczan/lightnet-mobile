package com.lightnet.api.http.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable(with = ColorRefSerializer::class)
sealed class ColorRef {
    data class Hex(val value: String) : ColorRef()
    data class Rgb(val r: Int, val g: Int, val b: Int) : ColorRef()
    data class PalettePosition(val position: Int) : ColorRef()
    data class BaseColorSlot(val slot: Int) : ColorRef()
}

internal object ColorRefSerializer : KSerializer<ColorRef> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ColorRef")

    override fun serialize(encoder: Encoder, value: ColorRef) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(
            when (value) {
                is ColorRef.Hex -> JsonPrimitive(value.value)
                is ColorRef.Rgb -> buildJsonObject {
                    put("r", value.r)
                    put("g", value.g)
                    put("b", value.b)
                }
                is ColorRef.PalettePosition -> buildJsonObject { put("palette", value.position) }
                is ColorRef.BaseColorSlot -> buildJsonObject { put("useColor", value.slot) }
            }
        )
    }

    override fun deserialize(decoder: Decoder): ColorRef {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonPrimitive -> ColorRef.Hex(element.content)
            element is JsonObject && "r" in element -> ColorRef.Rgb(
                element["r"]!!.jsonPrimitive.int,
                element["g"]!!.jsonPrimitive.int,
                element["b"]!!.jsonPrimitive.int,
            )
            element is JsonObject && "palette" in element ->
                ColorRef.PalettePosition(element["palette"]!!.jsonPrimitive.int)
            element is JsonObject && "useColor" in element ->
                ColorRef.BaseColorSlot(element["useColor"]!!.jsonPrimitive.int)
            else -> throw SerializationException("Unrecognised ColorRef: $element")
        }
    }
}
