package com.lightnet.api.http.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable(with = PanelTargetSerializer::class)
sealed class PanelTarget {
    data object All : PanelTarget()
    data class Include(val indices: List<Int>) : PanelTarget()
    data class Exclude(val indices: List<Int>) : PanelTarget()
}

internal object PanelTargetSerializer : KSerializer<PanelTarget> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PanelTarget")

    override fun serialize(encoder: Encoder, value: PanelTarget) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(
            when (value) {
                is PanelTarget.All -> JsonPrimitive("all")
                is PanelTarget.Include -> buildJsonArray {
                    value.indices.forEach { add(JsonPrimitive(it)) }
                }
                is PanelTarget.Exclude -> buildJsonObject {
                    put("exclude", buildJsonArray { value.indices.forEach { add(JsonPrimitive(it)) } })
                }
            }
        )
    }

    override fun deserialize(decoder: Decoder): PanelTarget {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonPrimitive -> PanelTarget.All
            element is JsonArray -> PanelTarget.Include(element.map { it.jsonPrimitive.int })
            element is JsonObject && "exclude" in element ->
                PanelTarget.Exclude(element["exclude"]!!.jsonArray.map { it.jsonPrimitive.int })
            else -> throw SerializationException("Unrecognised PanelTarget: $element")
        }
    }
}
