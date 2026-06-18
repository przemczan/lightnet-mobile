package com.lightnet.api.http.model

/** Matches firmware `EntryId.hpp` — FNV-1a → 8-char lowercase base36. */
object EntryIds {
    const val USER_COLORS: String = "00voc1rp"

    fun deterministicId(seed: String): String {
        var h = 2166136261u
        for (b in seed.encodeToByteArray()) {
            h = (h xor b.toUInt()) * 16777619u
        }
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        var v = h
        return buildString(8) {
            repeat(8) {
                insert(0, digits[(v % 36u).toInt()])
                v /= 36u
            }
        }
    }

    fun demoPaletteId(displayName: String) = deterministicId("demo:palette:$displayName")

    fun demoSceneId(displayName: String) = deterministicId("demo:scene:$displayName")

    fun isUserColors(id: String) = id == USER_COLORS
}
