package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.hehpollon.drivemood.domain.RhythmKind
import kotlin.math.PI
import kotlin.math.sin

/** Decorative state illustration. Its slow motion never represents measured signals or a route. */
@Composable
internal fun RhythmIllustration(kind: RhythmKind, modifier: Modifier = Modifier, markOnly: Boolean = false, animate: Boolean = true) {
    val colors = LocalMoodColors.current
    val changed = kind == RhythmKind.CHANGED
    val observed = kind in setOf(RhythmKind.STEADY, RhythmKind.MIXED, RhythmKind.CHANGED)
    val accent = when {
        changed -> colors.warm
        observed -> colors.rhythm
        else -> colors.secondaryText
    }
    val phase = if (animate && observed &&
        LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)) {
        rememberInfiniteTransition(label = "rhythm illustration").animateFloat(
            initialValue = 0f, targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
            label = "gentle ribbon phase",
        )
    } else null
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val middle = if (markOnly) .5f else .57f

        if (!markOnly) {
            // Flat color and generous shapes also work at a glance in the night palette.
            drawOval(colors.background.copy(alpha = .55f), Offset(w * .05f, h * .07f), Size(w * .9f, h * .86f))
            drawOval(if (changed) colors.sun.copy(alpha = .25f) else colors.sky,
                Offset(w * .12f, h * .11f), Size(w * .76f, h * .72f))
            val sun = Offset(w * .74f, h * .27f)
            drawCircle(colors.sun, unit * .086f, sun)
            if (colors == NightMoodColors) {
                drawCircle(colors.sky, unit * .076f, sun + Offset(unit * .039f, -unit * .02f))
            } else {
                drawRoundRect(colors.cloud, Offset(w * .23f, h * .26f), Size(w * .18f, h * .027f), CornerRadius(h * .03f))
                drawRoundRect(colors.cloud, Offset(w * .28f, h * .235f), Size(w * .075f, h * .05f), CornerRadius(h * .03f))
            }
            drawPath(Path().apply {
                moveTo(w * .08f, h * .71f)
                cubicTo(w * .29f, h * .57f, w * .39f, h * .76f, w * .57f, h * .7f)
                cubicTo(w * .72f, h * .62f, w * .84f, h * .66f, w * .92f, h * .73f)
                cubicTo(w * .73f, h * .96f, w * .25f, h * .96f, w * .08f, h * .71f)
                close()
            }, colors.distantHill)
            drawPath(Path().apply {
                moveTo(w * .18f, h * .82f)
                cubicTo(w * .39f, h * .7f, w * .55f, h * .87f, w * .83f, h * .81f)
                cubicTo(w * .65f, h * .98f, w * .36f, h * .96f, w * .18f, h * .82f)
                close()
            }, colors.nearHill)
        }

        if (observed) {
            // Read the animation in the draw phase, leaving text and layout out of frame updates.
            // Fixed ends and bounded control-point drift avoid scrolling or a travelling highlight.
            val travel = if (phase != null) minOf((if (markOnly) 1.5.dp else 4.dp).toPx(), h * .025f) else 0f
            val angle = phase?.value ?: 0f
            val upperDrift = sin(angle) * travel
            val lowerDrift = sin(angle + (PI * .65f).toFloat()) * travel
            val amplitude = when (kind) {
                RhythmKind.CHANGED -> if (markOnly) .36f else .17f
                RhythmKind.MIXED -> if (markOnly) .27f else .12f
                else -> if (markOnly) .18f else .075f
            }
            val upper = h * (middle - amplitude) + upperDrift
            val lower = h * (middle + amplitude) + lowerDrift
            val ribbon = Path().apply {
                moveTo(w * .08f, h * middle)
                cubicTo(w * .22f, h * middle, w * .23f, upper, w * .36f, upper)
                cubicTo(w * .51f, upper, w * .48f, lower, w * .64f, lower)
                cubicTo(w * .78f, lower, w * .78f, h * middle, w * .92f, h * middle)
            }
            val width = if (markOnly) 4.dp.toPx() else unit * .037f
            if (!markOnly) drawPath(ribbon, colors.road, style = Stroke(width * 3.2f, cap = StrokeCap.Round))
            drawPath(ribbon, accent, style = Stroke(width, cap = StrokeCap.Round))
        } else {
            // Waiting and excluded intervals must not imply an observed steady rhythm.
            listOf(.31f, .5f, .69f).forEach { x ->
                drawRoundRect(accent.copy(alpha = .65f), Offset(w * (x - .045f), h * middle),
                    Size(w * .09f, if (markOnly) 4.dp.toPx() else unit * .025f), CornerRadius(unit * .02f))
            }
        }
    }
}
