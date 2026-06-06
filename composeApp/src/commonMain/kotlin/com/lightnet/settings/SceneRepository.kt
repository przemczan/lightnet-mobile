package com.lightnet.settings

import com.lightnet.api.http.model.SceneJson
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SceneRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val listSerializer = ListSerializer(SceneJson.serializer())

    fun getAll(): List<SceneJson> =
        settings.getStringOrNull(KEY)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            ?: emptyList()

    fun save(scene: SceneJson) {
        val name = scene.name?.trim().takeIf { !it.isNullOrBlank() } ?: return
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) list[idx] = scene else list.add(scene)
        settings.putString(KEY, json.encodeToString(listSerializer, list))
    }

    fun delete(name: String) {
        val list = getAll().filter { it.name != name }
        settings.putString(KEY, json.encodeToString(listSerializer, list))
    }

    companion object {
        private const val KEY = "app_scenes"
    }
}
