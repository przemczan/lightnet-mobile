package com.lightnet.api.http

import com.lightnet.api.http.model.AnimationPlayRequest
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.FirmwareFlashResponse
import com.lightnet.api.http.model.FirmwareStatusResponse
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteMeta
import com.lightnet.api.http.model.PanelEdgeResponse
import com.lightnet.api.http.model.PanelStateResponse
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.TopologyResponse

interface DeviceHttpApi {
    suspend fun getAppearance(): AppearanceResponse
    suspend fun setAppearance(request: AppearanceRequest)

    suspend fun getPaletteMetas(): List<PaletteMeta>
    suspend fun getPalette(id: String): PaletteJson
    suspend fun savePalette(palette: PaletteJson): String
    suspend fun deletePalette(id: String)

    suspend fun getScenes(): List<SceneInfo>
    suspend fun getScene(id: String): SceneJson
    suspend fun saveScene(scene: SceneJson): String
    suspend fun deleteScene(id: String)
    suspend fun playSceneById(id: String)
    suspend fun playSceneInline(scene: SceneJson)
    suspend fun playLastScene()
    suspend fun stopScene()
    suspend fun setSceneSpeed(speed: Float)

    suspend fun playAnimation(request: AnimationPlayRequest)
    suspend fun triggerAnimation(group: Int, value: Int)

    suspend fun getPanels(): List<PanelStateResponse>
    suspend fun getPanelEdges(): List<PanelEdgeResponse>
    suspend fun setPanelOn(address: Int, on: Boolean)
    suspend fun setPanelColor(address: Int, color: String)

    suspend fun getAppState(): AppStateBody
    suspend fun setPowerState(on: Boolean)

    suspend fun getTopology(): TopologyResponse
    suspend fun setLogicalRoot(root: Int)

    suspend fun getConfiguration(): ConfigurationResponse
    suspend fun setConfiguration(request: ConfigurationRequest)

    suspend fun uploadPanelFirmware(data: ByteArray): FirmwareFlashResponse
    suspend fun getFirmwareStatus(): FirmwareStatusResponse

    fun close()
}

/** Meta list + full palette bodies (one GET per id). */
suspend fun DeviceHttpApi.loadAllPalettes(): List<PaletteJson> =
    getPaletteMetas().map { meta ->
        getPalette(meta.id).copy(id = meta.id, name = meta.name)
    }
