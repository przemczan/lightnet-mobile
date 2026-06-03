package com.lightnet.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
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
 * Pre-resolved entrance animation for one set of panels. All randomness (the concrete style, the
 * start offsets, the stagger) is decided once when the plan is built, so it stays stable across
 * recompositions and only re-rolls when a new set of panels appears.
 *
 * Each [Animatable] runs `1f → 0f`; multiplying it by the matching start offset yields the panel's
 * current displacement from its final resting position.
 */
internal data class EntrancePlan(
    val style: PanelAnimationStyle,            // resolved — never Random
    val startOffsets: List<Offset>,
    val staggerMs: List<Long>,
    val animatables: List<Animatable<Float, AnimationVector1D>>,
    /** For PopUp style: per-panel scale factor (0→1). For other styles: all pre-snapped to 1f. */
    val scaleAnimatables: List<Animatable<Float, AnimationVector1D>>,
)

/**
 * Builds an [EntrancePlan]. [screenXCenters] are per-panel x centers in screen px, used to order the
 * Rain stagger left-to-right. [viewW]/[viewH] scale the off-screen start offsets.
 */
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

    val animatables = List(panelCount) { Animatable(1f) }

    return when (style) {
        PanelAnimationStyle.FromDirections -> {
            val dist = maxOf(viewW, viewH) * 1.2f
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) {
                    val angle = Random.nextFloat() * 2f * PI
                    Offset(cos(angle) * dist, sin(angle) * dist)
                },
                staggerMs = List(panelCount) { (Random.nextFloat() * 120f).toLong() },
                animatables = animatables,
                scaleAnimatables = List(panelCount) { Animatable(1f) },
            )
        }

        PanelAnimationStyle.Rain -> {
            // Stagger order: leftmost panels fall first.
            val order = screenXCenters.indices.sortedBy { screenXCenters.getOrElse(it) { 0f } }
            val rank = IntArray(panelCount)
            order.forEachIndexed { position, panelIndex -> rank[panelIndex] = position }
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) { Offset(0f, -viewH * 1.1f) },
                staggerMs = List(panelCount) { rank[it] * 40L },
                animatables = animatables,
                scaleAnimatables = List(panelCount) { Animatable(1f) },
            )
        }

        PanelAnimationStyle.PopUp -> {
            EntrancePlan(
                style = style,
                startOffsets = List(panelCount) { Offset.Zero },
                staggerMs = List(panelCount) { (Random.nextFloat() * 220f).toLong() },
                animatables = animatables,
                scaleAnimatables = List(panelCount) { Animatable(0f) },
            )
        }

        PanelAnimationStyle.Random -> error("unreachable: Random resolved above")
    }
}

/**
 * Remembers an [EntrancePlan] keyed on the [panels] identity, and (re)runs the staggered animation
 * each time that identity changes — i.e. on first appearance and on every reconnect.
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
            plan.animatables.forEach { it.snapTo(1f) }
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
            plan.animatables.forEach { it.snapTo(1f) }
            plan.animatables.forEachIndexed { i, anim ->
                launch {
                    if (plan.staggerMs[i] > 0) delay(plan.staggerMs[i])
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = config.animationSpeedMs, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    return plan
}

private const val PI = 3.1415927f
