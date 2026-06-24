package com.lightnet.api.http

import com.lightnet.api.http.model.PaletteJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteJsonTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun decodeFirmwarePaletteList() {
        val body = """
            [
              {"schemaVersion":1,"name":"Rainbow","builtin":true,"stops":[[0,"#FF0000"],[255,"#0000FF"]]},
              {"schemaVersion":1,"name":"Base colors","builtin":true,"stops":[[0,"#CF5B3C"],[128,"#2F6DB0"],[255,"#3C9A5F"]]},
              {"schemaVersion":1,"name":"custom","stops":[[0,"#111111"],[255,"#EEEEEE"]]}
            ]
        """.trimIndent()
        val palettes = json.decodeFromString<List<PaletteJson>>(body)
        assertEquals(3, palettes.size)
        assertEquals("Rainbow", palettes[0].name)
        assertEquals(2, palettes[0].stops.size)
    }

    @Test
    fun decodePaletteWithoutStopsField() {
        val body = """[{"schemaVersion":1,"name":"Empty","builtin":true}]"""
        val palettes = json.decodeFromString<List<PaletteJson>>(body)
        assertEquals(1, palettes.size)
        assertTrue(palettes[0].stops.isEmpty())
    }

    @Test
    fun decodePaletteListWithLegacyIdField() {
        val body = """[{"schemaVersion":1,"id":"abc12345","name":"Legacy","stops":[[0,"#FFFFFF"]]}]"""
        val palettes = json.decodeFromString<List<PaletteJson>>(body)
        assertEquals(1, palettes.size)
        assertEquals("Legacy", palettes[0].name)
    }
}
