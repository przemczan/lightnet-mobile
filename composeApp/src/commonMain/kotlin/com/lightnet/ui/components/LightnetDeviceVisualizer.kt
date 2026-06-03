package com.lightnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.geometry.GeometryUtils
import com.lightnet.ui.toColorRgb
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PaintMode { Paint, Erase, Stamp }

@Composable
fun LightnetDeviceVisualizer(
    panels: List<LightnetDevicePanel>,
    modifier: Modifier = Modifier,
    powerOn: Boolean = true,
    brightness: Float = 255f,
    paintMode: PaintMode = PaintMode.Paint,
    paintColor: Color = Color.White,
    interactive: Boolean = true,
    showPanelIds: Boolean = false,
    selectionMode: Boolean = false,
    selectedPanels: Set<Int> = emptySet(),
    onSelectionChange: (Set<Int>) -> Unit = {},
    onEnterSelectionMode: (firstPanelIndex: Int) -> Unit = {},
    onTapWhileOff: (() -> Unit)? = null,
    rotationDegrees: Float = 0f,
    rotateMode: Boolean = false,
    onRotate: (deltaDegrees: Float) -> Unit = {},
    config: PanelVisualConfig = PanelVisualConfig(),
) {
    val states = panels.map { it.state.collectAsState() }
    val textMeasurer = rememberTextMeasurer()

    // Stable refs for gesture handler — changes don't restart the gesture block.
    val currentPaintMode         = rememberUpdatedState(paintMode)
    val currentPaintColor        = rememberUpdatedState(paintColor)
    val currentInteractive       = rememberUpdatedState(interactive)
    val currentSelectionMode     = rememberUpdatedState(selectionMode)
    val currentSelectedPanels    = rememberUpdatedState(selectedPanels)
    val currentOnSelectionChange = rememberUpdatedState(onSelectionChange)
    val currentOnEnterSelection  = rememberUpdatedState(onEnterSelectionMode)
    val currentOnTapWhileOff     = rememberUpdatedState(onTapWhileOff)
    val currentOnRotate          = rememberUpdatedState(onRotate)

    BoxWithConstraints(modifier) {
        if (panels.isEmpty()) return@BoxWithConstraints

        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        // Unrotated panel vertices (sorted by edge index) in layout space. For a closed polygon the
        // set of edge start points {x1,y1} already covers every vertex, so this drives fit + hit-test.
        val rawPolygons = remember(panels) {
            panels.map { panel ->
                panel.layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { GeometryUtils.Point(it.value.x1, it.value.y1) }
            }
        }

        // Rotation pivot — centre of the unrotated bounding box, so any angle rotates in place.
        val pivot = remember(rawPolygons) {
            val all = rawPolygons.flatten()
            val cx = (all.minOf { it.x } + all.maxOf { it.x }) / 2.0
            val cy = (all.minOf { it.y } + all.maxOf { it.y }) / 2.0
            GeometryUtils.Point(cx, cy)
        }

        // Vertices rotated about the pivot — the single source of truth for fit, hit-test and render.
        val polygons = remember(rawPolygons, pivot, rotationDegrees) {
            if (rotationDegrees == 0f) rawPolygons
            else {
                val rad = rotationDegrees.toDouble() * PI / 180.0
                val c = cos(rad); val s = sin(rad)
                rawPolygons.map { poly ->
                    poly.map { p ->
                        val dx = p.x - pivot.x; val dy = p.y - pivot.y
                        GeometryUtils.Point(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
                    }
                }
            }
        }

        // Fit the rotated bounding box into the view (re-fits live as the angle changes).
        val allPts = polygons.flatten()
        val minX = allPts.minOf { it.x }.toFloat()
        val minY = allPts.minOf { it.y }.toFloat()
        val maxX = allPts.maxOf { it.x }.toFloat()
        val maxY = allPts.maxOf { it.y }.toFloat()

        val pad = 32f
        val scale = minOf(
            (viewW - pad * 2) / (maxX - minX).coerceAtLeast(1f),
            (viewH - pad * 2) / (maxY - minY).coerceAtLeast(1f),
        )
        val offsetX = -minX + pad / scale
        val offsetY = -minY + pad / scale

        // Entrance animation: per-panel displacement that decays to zero on appearance.
        val screenXCenters = remember(polygons) {
            polygons.map { poly -> if (poly.isEmpty()) 0f else (poly.sumOf { it.x } / poly.size).toFloat() }
        }
        val entrancePlan = rememberEntrancePlan(panels, config, viewW, viewH, screenXCenters)
        val animOffsets = panels.indices.map { i ->
            entrancePlan.startOffsets[i] * entrancePlan.animatables[i].value
        }
        val animScales = panels.indices.map { i -> entrancePlan.scaleAnimatables[i].value }

        // Per-panel screen-space centers, used as scale pivots for the PopUp animation.
        val panelScreenCenters = remember(polygons, scale, offsetX, offsetY) {
            polygons.map { poly ->
                if (poly.isEmpty()) Offset.Zero
                else Offset(
                    x = poly.sumOf { (it.x.toFloat() + offsetX) * scale.toDouble() }.toFloat() / poly.size,
                    y = poly.sumOf { (it.y.toFloat() + offsetY) * scale.toDouble() }.toFloat() / poly.size,
                )
            }
        }

        // Per-panel screen-space geometry, shared by the shadow layer and the main canvas.
        // Rebuilt only when layout/scale/angle or shape params change — never per animation frame.
        val rendered = remember(polygons, scale, offsetX, offsetY, config.panelPadding, config.cornerRadius) {
            polygons.indices.mapNotNull { i ->
                val rawPoints = polygons[i].map { p ->
                    Offset(
                        x = (p.x.toFloat() + offsetX) * scale,
                        y = (p.y.toFloat() + offsetY) * scale,
                    )
                }
                if (rawPoints.size < 3) return@mapNotNull null
                val points = if (config.panelPadding > 0f) shrinkPolygon(rawPoints, config.panelPadding * scale) else rawPoints
                PanelRender(i, points, buildPanelPath(points, config.cornerRadius * scale))
            }
        }

        fun hitTest(sx: Float, sy: Float): Int? {
            val lx = (sx / scale - offsetX).toDouble()
            val ly = (sy / scale - offsetY).toDouble()
            return polygons.indices.firstOrNull { i -> GeometryUtils.isInsidePolygon(lx, ly, polygons[i]) }
        }

        fun applyPaint(idx: Int) {
            when (currentPaintMode.value) {
                PaintMode.Paint, PaintMode.Stamp -> {
                    panels[idx].setColor(currentPaintColor.value.toColorRgb())
                    panels[idx].toggle(on = true)
                }
                PaintMode.Erase -> panels[idx].toggle(on = false)
            }
        }

        val gestureModifier = if (rotateMode) {
            // Rotate mode overrides paint/select: horizontal swipe spins the view (works powered off).
            Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnRotate.value(dragAmount * config.rotateSensitivity)
                }
            }
        } else if (!powerOn && currentOnTapWhileOff.value != null) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnTapWhileOff.value?.invoke()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                }
            }
        } else if (powerOn && (interactive || selectionMode)) {
            Modifier.pointerInput(panels, scale, offsetX, offsetY) {
                val visitedInStroke = mutableSetOf<Int>()

                coroutineScope {
                val cs = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var hasMoved = false
                    var longPressTriggered = false
                    visitedInStroke.clear()

                    val longPressJob = if (!currentSelectionMode.value && currentInteractive.value) {
                        cs.launch {
                            delay(500L)
                            if (!hasMoved) {
                                longPressTriggered = true
                                hitTest(down.position.x, down.position.y)?.let { idx ->
                                    currentOnEnterSelection.value(idx)
                                }
                            }
                        }
                    } else null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        if ((change.position - down.position).getDistance() > 4f) {
                            hasMoved = true
                            longPressJob?.cancel()
                        }

                        if (hasMoved && currentInteractive.value && !currentSelectionMode.value) {
                            val idx = hitTest(change.position.x, change.position.y)
                            if (idx != null && visitedInStroke.add(idx)) applyPaint(idx)
                            change.consume()
                        }
                    }

                    longPressJob?.cancel()

                    if (!hasMoved && !longPressTriggered) {
                        val idx = hitTest(down.position.x, down.position.y)
                        when {
                            currentSelectionMode.value && idx != null -> {
                                val cur = currentSelectedPanels.value
                                currentOnSelectionChange.value(if (idx in cur) cur - idx else cur + idx)
                            }
                            currentInteractive.value && idx != null -> applyPaint(idx)
                        }
                    }
                }
                } // coroutineScope
            }
        } else Modifier


        val shadow = config.shadow

        // NativeBlur shadows live on their own layer so `Modifier.blur` can turn the sharp
        // silhouettes into a real Gaussian blur (Android 31+ / iOS; a no-op on older Android).
        if (shadow.enabled && shadow.implementation == ShadowImplementation.NativeBlur) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur((shadow.nativeBlur.radius * scale).dp, BlurredEdgeTreatment.Unbounded)
            ) {
                rendered.forEach { r ->
                    translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                        scale(animScales[r.index], pivot = panelScreenCenters[r.index]) {
                            drawNativeBlurShape(r.path, shadow, scale)
                        }
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // Pass 1 — in-canvas drop shadows beneath every panel body, so a panel never casts onto
            // a neighbour. (Layered / Feathered only; NativeBlur is handled by the layer above.)
            rendered.forEach { r ->
                translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                    scale(animScales[r.index], pivot = panelScreenCenters[r.index]) {
                        drawInCanvasShadow(r.points, r.path, shadow, config.cornerRadius * scale, scale)
                    }
                }
            }

            // Pass 2 — panel bodies and overlays.
            rendered.forEach { r ->
                val panel = panels[r.index]
                val state = states[r.index].value
                translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                    scale(animScales[r.index], pivot = panelScreenCenters[r.index]) {
                        drawPanelBackground(r.path, config)

                        if (powerOn) {
                            val brightnessScale = (brightness / 255f).coerceIn(0f, 1f)
                            drawPanelActiveColor(
                                r.path,
                                Color(
                                    red   = state.color.r / 255f * brightnessScale,
                                    green = state.color.g / 255f * brightnessScale,
                                    blue  = state.color.b / 255f * brightnessScale,
                                ),
                            )
                        }

                        drawPanelBorder(r.path, config, scale)

                        // After the fill so the rim darkens the lit colour, giving a recessed look.
                        drawInnerShadow(r.path, config.innerShadow, scale)

                        if (selectionMode) drawPanelSelection(r.path, isSelected = r.index in selectedPanels, scale = scale)

                        if (showPanelIds) drawPanelLabel(panel.info.id.toString(), r.points, textMeasurer)
                    }
                }
            }
        }
    }
}

/** Resolved per-panel render geometry, built once per layout. */
private data class PanelRender(val index: Int, val points: List<Offset>, val path: Path)

// ── Per-layer draw helpers ────────────────────────────────────────────────────
// One function per panel-body layer so a single layer can be tweaked in isolation.
// (Drop-shadow techniques live in VisualizerShadows.kt; geometry in VisualizerGeometry.kt.)

private fun DrawScope.drawPanelBackground(path: Path, config: PanelVisualConfig) {
    drawPath(path, color = config.backgroundColor, style = Fill)
}

private fun DrawScope.drawPanelBorder(path: Path, config: PanelVisualConfig, scale: Float) {
    if (config.borderWidth > 0f) {
        drawPath(path, color = config.borderColor, style = Stroke(width = config.borderWidth * scale))
    }
}

private fun DrawScope.drawPanelActiveColor(path: Path, color: Color) {
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawPanelSelection(path: Path, isSelected: Boolean, scale: Float) {
    if (isSelected) {
        drawPath(path, color = Color.White.copy(alpha = 0.25f), style = Fill)
        drawPath(path, color = Color.White, style = Stroke(width = 2.5f * scale))
    } else {
        drawPath(path, color = Color.Black.copy(alpha = 0.55f), style = Fill)
    }
}

private fun DrawScope.drawPanelLabel(label: String, points: List<Offset>, textMeasurer: TextMeasurer) {
    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
    val measured = textMeasurer.measure(
        label,
        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
    val shadowMeasured = textMeasurer.measure(
        label,
        style = TextStyle(color = Color.Black.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
    val tx = cx - measured.size.width / 2f
    val ty = cy - measured.size.height / 2f
    drawText(shadowMeasured, topLeft = Offset(tx + 1f, ty + 1f))
    drawText(measured, topLeft = Offset(tx, ty))
}
