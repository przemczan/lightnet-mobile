package com.lightnet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-resolved entrance animation plan for one set of panels. All randomness is decided once when
 * the plan is built and stays stable across recompositions; it re-rolls only when a new panel set
 * appears.
 *
 * FromDirections / Rain use a single [clock] animatable (0 → [clockTotalMs] linearly) so that
 * only ONE state subscription drives recomposition — regardless of panel count. Each panel's
 * displacement factor is computed from the clock as a pure function, keeping frame rate constant
 * even with 100+ panels.
 *
 * PopUp uses per-panel spring [scaleAnimatables] (spring easing is stateful and cannot be
 * replicated from a shared clock).
 */
internal data class EntrancePlan(
    val style: PanelAnimationStyle,
    val startOffsets: List<Offset>,
    val staggerMs: List<Long>,
    /** Single linear clock for FromDirections / Rain. null for PopUp. */
    val clock: Animatable<Float, AnimationVector1D>?,
    val clockTotalMs: Int,
    /**
     * Fixed per-panel tween duration. 0 means variable: each panel runs from its stagger to
     * [clockTotalMs], so all panels settle at exactly the same instant (FromDirections).
     */
    val perPanelDurationMs: Int,
    /** Per-panel scale animatables for PopUp springs. Empty for other styles. */
    val scaleAnimatables: List<Animatable<Float, AnimationVector1D>>,
) {
    /** 1f = full off-screen offset; 0f = resting. Reads clock once — one state subscription total. */
    fun displacementFactor(index: Int): Float {
        val clockMs = clock?.value ?: return 0f
        val stagger = staggerMs[index]
        if (clockMs <= stagger) return 1f
        val duration = if (perPanelDurationMs > 0) perPanelDurationMs.toFloat()
                       else (clockTotalMs - stagger).coerceAtLeast(1).toFloat()
        val t = ((clockMs - stagger) / duration).coerceIn(0f, 1f)
        return 1f - FastOutSlowInEasing.transform(t)
    }

    /** 0f = invisible, 1f = full size. Only non-trivial for PopUp. */
    fun scaleFactor(index: Int): Float = scaleAnimatables.getOrNull(index)?.value ?: 1f
}

internal fun buildEntrancePlan(
    panelCount: Int,
    config: PanelVisualConfig,
    viewW: Float,
    viewH: Float,
    screenXCenters: List<Float>,
): EntrancePlan {
    val style = when (config.animationStyle) {
        PanelAnimationStyle.Random -> listOf(
            PanelAnimationStyle.FromDirections,
            PanelAnimationStyle.Rain,
            PanelAnimationStyle.PopUp,
        ).random()
        else -> config.animationStyle
    }

    return when (style) {
        PanelAnimationStyle.FromDirections -> {
            val dist = maxOf(viewW, viewH) * 1.2f
            val staggerMs = List(panelCount) { (Random.nextFloat() * 200f).toLong() }
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) {
                    val angle = Random.nextFloat() * 2f * PI
                    Offset(cos(angle) * dist, sin(angle) * dist)
                },
                staggerMs = staggerMs,
                clock = Animatable(0f),
                clockTotalMs = FROM_DIRECTIONS_TOTAL_MS,
                perPanelDurationMs = 0,
                scaleAnimatables = emptyList(),
            )
        }

        PanelAnimationStyle.Rain -> {
            val order = screenXCenters.indices.sortedBy { screenXCenters.getOrElse(it) { 0f } }
            val rank = IntArray(panelCount)
            order.forEachIndexed { position, panelIndex -> rank[panelIndex] = position }
            val panelMs = config.animationSpeedMs
            val staggerBudget = (RAIN_TOTAL_MS - panelMs).coerceAtLeast(0)
            val staggerPerPanel = if (panelCount <= 1) 0L else staggerBudget.toLong() / (panelCount - 1)
            val maxStagger = staggerPerPanel * (panelCount - 1)
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) { Offset(0f, -viewH * 1.1f) },
                staggerMs = List(panelCount) { rank[it] * staggerPerPanel },
                clock = Animatable(0f),
                clockTotalMs = (maxStagger + panelMs).toInt(),
                perPanelDurationMs = panelMs,
                scaleAnimatables = emptyList(),
            )
        }

        PanelAnimationStyle.PopUp -> {
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) { Offset.Zero },
                staggerMs = List(panelCount) { (Random.nextFloat() * 220f).toLong() },
                clock = null,
                clockTotalMs = 0,
                perPanelDurationMs = 0,
                scaleAnimatables = List(panelCount) { Animatable(0f) },
            )
        }

        PanelAnimationStyle.Random -> error("unreachable: Random resolved above")
    }
}

/**
 * Remembers an [EntrancePlan] keyed on the [panels] identity, and (re)runs the animation each
 * time that identity changes — i.e. on first appearance and on every reconnect.
 */
@Composable
internal fun rememberEntrancePlan(
    panels: List<*>,
    config: PanelVisualConfig,
    viewW: Float,
    viewH: Float,
    screenXCenters: List<Float>,
): EntrancePlan {
    val plan = remember(panels) {
        buildEntrancePlan(panels.size, config, viewW, viewH, screenXCenters)
    }

    LaunchedEffect(panels) {
        if (plan.style == PanelAnimationStyle.PopUp) {
            plan.scaleAnimatables.forEach { it.snapTo(0f) }
            plan.scaleAnimatables.forEachIndexed { i, anim ->
                launch {
                    if (plan.staggerMs[i] > 0) delay(plan.staggerMs[i])
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        } else {
            plan.clock?.let { clock ->
                clock.snapTo(0f)
                clock.animateTo(
                    targetValue = plan.clockTotalMs.toFloat(),
                    animationSpec = tween(durationMillis = plan.clockTotalMs, easing = LinearEasing),
                )
            }
        }
    }

    return plan
}

private const val PI = 3.1415927f
private const val FROM_DIRECTIONS_TOTAL_MS = 1000
private const val RAIN_TOTAL_MS = 1000
