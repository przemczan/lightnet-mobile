package com.lightnet.api.http.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = PaletteStopSerializer::class)
data class PaletteStop(val position: Int, val color: String)

internal object PaletteStopSerializer : KSerializer<PaletteStop> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PaletteStop")

    override fun serialize(encoder: Encoder, value: PaletteStop) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(buildJsonArray {
            add(JsonPrimitive(value.position))
            add(JsonPrimitive(value.color))
        })
    }

    override fun deserialize(decoder: Decoder): PaletteStop {
        val array = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        return PaletteStop(array[0].jsonPrimitive.int, array[1].jsonPrimitive.content)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PaletteJson(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schemaVersion: Int = 1,
    val name: String,
    val builtin: Boolean? = null,
    val stops: List<PaletteStop> = emptyList(),
)

/** Display label for palette pickers (userColors shows as "Base colors"). */
data class PaletteOption(val name: String)

fun paletteNamesEqual(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)

const val PALETTE_NAME_MAX_LEN = 30

/** Truncates palette name input to the firmware max length. */
fun sanitizePaletteName(input: String): String = input.take(PALETTE_NAME_MAX_LEN)

fun paletteNameValidationError(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required."
    if (trimmed.length > PALETTE_NAME_MAX_LEN) return "Name must be at most $PALETTE_NAME_MAX_LEN characters."
    return null
}

fun PaletteJson.isBuiltin(): Boolean =
    builtin == true || EntryIds.isUserColors(name)

@Serializable
data class PaletteUpdateBody(val stops: List<PaletteStop>)
