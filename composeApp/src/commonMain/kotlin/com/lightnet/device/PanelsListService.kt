package com.lightnet.device

import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.EdgeInfo
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.protocol.message.GetEdgesListMessage
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PanelsListService(
    private val messageApiService: MessageApiService,
    private val scope: CoroutineScope,
) {
    // null = loading in progress; emptyList = loaded with 0 panels; non-empty = loaded with panels
    private val _panels = MutableStateFlow<List<PanelInfo>?>(null)
    val panels: StateFlow<List<PanelInfo>?> = _panels

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        _panels.value = null  // null is distinct from emptyList — StateFlow will always emit on completion
        loadJob = scope.launch {
            // Subscribe BEFORE sending to guarantee we don't miss the response
            val edgesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                messageApiService.edgesList.first()
            }
            messageApiService.send(GetEdgesListMessage())
            try {
                _panels.value = buildPanelTree(edgesDeferred.await())
            } catch (e: Exception) {
                _panels.value = emptyList()  // decode failure → treat as 0 panels, don't hang forever
                throw e  // still propagates for crash reporting
            }
        }
    }

    private fun buildPanelTree(edges: List<PanelEdgeInfoModel>): List<PanelInfo> {
        val rawPanels = edges
            .groupBy { it.panelId }
            .map { (panelId, panelEdges) ->
                RawPanelInfo(
                    panelId = panelId,
                    edges = panelEdges
                        .map { RawEdgeInfo(it.edgeIndex, it.panelId, it.connectedPanelId, it.connectedEdgeIndex) }
                        .sortedBy { it.index },
                )
            }
            .sortedBy { it.panelId }

        if (rawPanels.isEmpty()) return emptyList()

        val output = mutableListOf<PanelInfo>()
        buildPanel(rawPanels.first(), parentEdge = null, rawPanels, output)
        return output
    }

    private fun buildPanel(
        raw: RawPanelInfo,
        parentEdge: RawEdgeInfo?,
        allPanels: List<RawPanelInfo>,
        output: MutableList<PanelInfo>,
    ): PanelInfo {
        val panel = PanelInfo(id = raw.panelId)
        output.add(panel)

        for (rawEdge in raw.edges) {
            val edge = EdgeInfo(index = rawEdge.index, panel = panel)
            panel.edges.add(edge)

            val isParentConnection = parentEdge != null && parentEdge.nextEdgeIndex == rawEdge.index
            if (!isParentConnection && rawEdge.nextPanelId != 0) {
                val nextRaw = allPanels.first { it.panelId == rawEdge.nextPanelId }
                val nextPanel = buildPanel(nextRaw, rawEdge, allPanels, output)
                val nextRoot = nextPanel.edges.find { it.index == rawEdge.nextEdgeIndex }
                if (nextRoot != null) {
                    edge.connectedEdge = nextRoot
                    nextRoot.connectedEdge = edge
                }
            }
        }

        if (parentEdge != null) {
            panel.rootEdge = panel.edges.find { it.index == parentEdge.nextEdgeIndex }
        }

        return panel
    }

    private data class RawEdgeInfo(val index: Int, val panelId: Int, val nextPanelId: Int, val nextEdgeIndex: Int)
    private data class RawPanelInfo(val panelId: Int, val edges: List<RawEdgeInfo>)
}
