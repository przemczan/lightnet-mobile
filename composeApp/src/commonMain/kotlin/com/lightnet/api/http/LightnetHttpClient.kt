package com.lightnet.api.http

import com.lightnet.api.http.model.AnimationPlayRequest
import com.lightnet.api.http.model.AnimationTriggerRequest
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.FirmwareFlashResponse
import com.lightnet.api.http.model.FirmwareStatusResponse
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PanelEdgeResponse
import com.lightnet.api.http.model.PanelStateResponse
import com.lightnet.api.http.model.LogicalRootRequest
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.SceneStatus
import com.lightnet.api.http.model.TopologyResponse
import com.lightnet.debug.DebugLog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LightnetApiException(val statusCode: Int, val error: String) :
    Exception("HTTP $statusCode: $error")

class LightnetHttpClient(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    // Stored on the request builder before execute() so readBodyForDebug can compute duration.
    private val startTimeAttr = AttributeKey<Long>("DebugHttpStartMs")

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }.also { c ->
        c.plugin(HttpSend).intercept { request ->
            request.attributes.put(startTimeAttr, System.currentTimeMillis())
            try {
                execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val url = request.url.build()
                val dur = System.currentTimeMillis() - (request.attributes.getOrNull(startTimeAttr) ?: 0L)
                DebugLog.logHttp(url.host, request.method.value, url.encodedPath, 0, dur, e.message)
                throw e
            }
        }
    }

    fun close() = client.close()

    // region Appearance

    suspend fun getAppearance(): AppearanceResponse =
        client.get("$baseUrl/api/appearance").bodyOrThrow()

    suspend fun setAppearance(request: AppearanceRequest) =
        client.patch("$baseUrl/api/appearance") { jsonBody(request) }.voidOrThrow()

    // endregion

    // region Palettes

    suspend fun getPalettes(): Map<String, PaletteJson> =
        client.get("$baseUrl/api/palettes").bodyOrThrow()

    suspend fun getPalette(name: String): PaletteJson =
        client.get("$baseUrl/api/palettes/$name").bodyOrThrow()

    suspend fun savePalette(palette: PaletteJson) =
        client.post("$baseUrl/api/palettes") { jsonBody(palette) }.voidOrThrow()

    suspend fun deletePalette(name: String) =
        client.delete("$baseUrl/api/palettes/$name").voidOrThrow()

    // endregion

    // region Scenes

    suspend fun getScenes(): List<SceneInfo> =
        client.get("$baseUrl/api/scenes").bodyOrThrow()

    suspend fun getScene(name: String): SceneJson =
        client.get("$baseUrl/api/scenes/$name").bodyOrThrow()

    suspend fun saveScene(scene: SceneJson) =
        client.post("$baseUrl/api/scenes") { jsonBody(scene) }.voidOrThrow()

    suspend fun deleteScene(name: String) =
        client.delete("$baseUrl/api/scenes/$name").voidOrThrow()

    suspend fun playSceneByName(name: String) =
        client.post("$baseUrl/api/scenes/$name/play").voidOrThrow()

    suspend fun playSceneInline(scene: SceneJson) =
        client.post("$baseUrl/api/scenes/play") { jsonBody(scene) }.voidOrThrow()

    suspend fun stopScene() =
        client.post("$baseUrl/api/scenes/stop").voidOrThrow()

    suspend fun setSceneSpeed(speed: Float) =
        client.post("$baseUrl/api/scenes/speed") { jsonBody(SceneSpeedValue(speed)) }.voidOrThrow()

    suspend fun getSceneStatus(): SceneStatus =
        client.get("$baseUrl/api/scenes/status").bodyOrThrow()

    // endregion

    // region Animations

    suspend fun playAnimation(request: AnimationPlayRequest) =
        client.post("$baseUrl/api/animations/play") { jsonBody(request) }.voidOrThrow()

    suspend fun triggerAnimation(group: Int, value: Int) =
        client.post("$baseUrl/api/animations/trigger") {
            jsonBody(AnimationTriggerRequest(group, value))
        }.voidOrThrow()

    // endregion

    // region Panels

    suspend fun getPanels(): List<PanelStateResponse> =
        client.get("$baseUrl/api/panels").bodyOrThrow()

    suspend fun getPanelEdges(): List<PanelEdgeResponse> =
        client.get("$baseUrl/api/panels/edges").bodyOrThrow()

    suspend fun setPanelOn(address: Int, on: Boolean) =
        client.put("$baseUrl/api/panels/$address/on") {
            jsonBody(IntValue(if (on) 1 else 0))
        }.voidOrThrow()

    suspend fun setPanelColor(address: Int, color: String) =
        client.put("$baseUrl/api/panels/$address/color") {
            jsonBody(ColorValue(color))
        }.voidOrThrow()

    // endregion

    // region App state

    suspend fun getAppState(): AppStateBody =
        client.get("$baseUrl/api/state").bodyOrThrow()

    suspend fun getPowerState(): Boolean = getAppState().isOn

    suspend fun setPowerState(on: Boolean) =
        client.post("$baseUrl/api/state/power") { jsonBody(PowerStateBody(on)) }.voidOrThrow()

    // endregion

    // region Topology

    suspend fun getTopology(): TopologyResponse =
        client.get("$baseUrl/api/topology").bodyOrThrow()

    suspend fun setLogicalRoot(root: Int) =
        client.put("$baseUrl/api/topology/root") { jsonBody(LogicalRootRequest(root)) }.voidOrThrow()

    // endregion

    // region Configuration

    suspend fun getConfiguration(): ConfigurationResponse =
        client.get("$baseUrl/api/configuration").bodyOrThrow()

    suspend fun setConfiguration(request: ConfigurationRequest) =
        client.patch("$baseUrl/api/configuration") { jsonBody(request) }.voidOrThrow()

    // endregion

    // region Firmware

    suspend fun uploadPanelFirmware(data: ByteArray): FirmwareFlashResponse =
        client.post("$baseUrl/api/firmware/panels") {
            contentType(ContentType.Application.OctetStream)
            setBody(data)
        }.bodyOrThrow()

    suspend fun getFirmwareStatus(): FirmwareStatusResponse =
        client.get("$baseUrl/api/firmware/status").bodyOrThrow()

    // endregion

    // region Private helpers

    @Serializable private data class PowerStateBody(val isOn: Boolean)
    @Serializable private data class SceneSpeedValue(val speed: Float)
    @Serializable private data class IntValue(val value: Int)
    @Serializable private data class ColorValue(val color: String)
    @Serializable private data class ErrorBody(val error: String? = null)

    private inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpResponse.throwIfError(bodyText: String) {
        if (status.value !in 200..299) {
            val error = runCatching { json.decodeFromString<ErrorBody>(bodyText).error }.getOrNull()
            throw LightnetApiException(status.value, error ?: status.description)
        }
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        val text = readBodyForDebug()
        throwIfError(text)
        return json.decodeFromString(text)
    }

    private suspend fun HttpResponse.voidOrThrow() {
        val text = readBodyForDebug()
        throwIfError(text)
    }

    private suspend fun HttpResponse.readBodyForDebug(): String {
        val startMs = call.request.attributes.getOrNull(startTimeAttr)
        val dur     = if (startMs != null) System.currentTimeMillis() - startMs else 0L
        val req     = call.request
        return try {
            val text = bodyAsText()
            DebugLog.logHttp(req.url.host, req.method.value, req.url.encodedPath, status.value, dur, text)
            text
        } catch (e: CancellationException) {
            DebugLog.logHttp(req.url.host, req.method.value, req.url.encodedPath, status.value, dur, "(cancelled)")
            throw e
        }
    }

    // endregion
}
