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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A scene layer's `panels` value. The firmware grammar (scene-portability §3) is large —
 * explicit indices, graph/tag selector strings, and `any`/`all`/`not` composition objects.
 * This models the parts the editor edits directly and **round-trips everything else
 * untouched** so hand-authored scenes never lose data:
 *
 * - [All]      — `"all"`
 * - [Include]  — explicit 1-based indices `[1,3,5]`
 * - [Exclude]  — `{ "exclude": [2] }`
 * - [Selector] — any other selector string token (`"leaves"`, `"depth:1-2"`, `"tag:accent"`, …)
 * - [Raw]      — any composition object (`{ "any": … }` / `{ "all": … }` / `{ "not": … }`),
 *               preserved verbatim and shown read-only in the editor.
 */
@Serializable(with = PanelTargetSerializer::class)
sealed class PanelTarget {
    data object All : PanelTarget()
    data class Include(val indices: List<Int>) : PanelTarget()
    data class Exclude(val indices: List<Int>) : PanelTarget()
    data class Selector(val token: String) : PanelTarget()
    data class Raw(val element: JsonElement) : PanelTarget()
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
                is PanelTarget.Selector -> JsonPrimitive(value.token)
                is PanelTarget.Raw -> value.element
            }
        )
    }

    override fun deserialize(decoder: Decoder): PanelTarget {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString ->
                if (element.content == "all") PanelTarget.All else PanelTarget.Selector(element.content)
            element is JsonArray -> PanelTarget.Include(element.map { it.jsonPrimitive.int })
            element is JsonObject && element.keys == setOf("exclude") ->
                PanelTarget.Exclude(element["exclude"]!!.jsonArray.map { it.jsonPrimitive.int })
            element is JsonObject -> PanelTarget.Raw(element)
            else -> throw SerializationException("Unrecognised PanelTarget: $element")
        }
    }
}
