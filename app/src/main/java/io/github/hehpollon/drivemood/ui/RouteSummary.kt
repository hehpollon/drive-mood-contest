package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import io.github.hehpollon.drivemood.data.DriveMoodState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun RouteSummary(state: DriveMoodState, driving: Boolean, onNavigation: () -> Unit, modifier: Modifier = Modifier) {
    val route = state.route
    val colors = LocalMoodColors.current
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = route.active) {
        while (route.active) { value = System.currentTimeMillis(); delay(15_000) }
    }
    val seconds = route.remainingSeconds?.takeIf { it >= 0 && route.active }
    val minutes = seconds?.let { (it.toLong() + 59) / 60 }
    val distance = route.remainingMeters?.takeIf { it >= 0 && route.active }?.let {
        if (it < 1_000) "$it m" else String.format(Locale.KOREA, "%.1f km", it / 1_000.0)
    } ?: "확인 중"
    val arrival = seconds?.let { clockTime(now + it.toLong() * 1_000) } ?: "확인 중"

    BoxWithConstraints(modifier.fillMaxWidth().background(colors.calm, RoundedCornerShape(40.dp))
        .semantics { contentDescription = "경로 요약 카드" }) {
        val compact = maxWidth < 480.dp
        val stackedMetrics = maxWidth < 400.dp
        val roomy = maxHeight >= 760.dp
        val short = maxHeight < 560.dp || (stackedMetrics && maxHeight < 760.dp)
        val gap = if (short) 16.dp else 24.dp
        val numberSize = when {
            short -> 56.sp
            (minutes?.toString()?.length ?: 0) >= 5 -> 56.sp
            roomy && !compact -> 96.sp
            compact -> 72.sp
            else -> 80.sp
        }
        Column(Modifier.fillMaxSize().padding(if (short) 24.dp else if (compact) 28.dp else 36.dp),
            verticalArrangement = if (roomy) Arrangement.SpaceBetween else Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!short) Text(if (route.active) "목적지" else "경로 안내", fontSize = 24.sp, color = SecondaryTextColor)
                    Text(route.destination?.takeIf { it.isNotBlank() }
                        ?: if (driving) "목적지 확인 중" else "어디로 떠나볼까요",
                        fontSize = if (short) 32.sp else if (compact) 36.sp else 40.sp,
                        lineHeight = if (short) 42.sp else if (compact) 48.sp else 54.sp,
                        fontWeight = FontWeight.SemiBold, color = TextColor,
                        maxLines = if (roomy) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
                if (!short) DestinationPin(Modifier.size(if (compact) 72.dp else 96.dp))
            }

            if (route.active) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (short) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RemainingTime(minutes, numberSize, true, Modifier.weight(1.4f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("남은 거리", fontSize = 24.sp, color = SecondaryTextColor)
                            Text(distance, fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.SemiBold,
                                color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("도착까지", fontSize = 24.sp, color = SecondaryTextColor)
                        RemainingTime(minutes, numberSize, false)
                    }
                    if (!short && stackedMetrics) Column(Modifier.fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(24.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("남은 거리" to distance, "도착 예상" to arrival).forEachIndexed { index, (label, value) ->
                            if (index > 0) HorizontalDivider(color = colors.primary.copy(alpha = .12f))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(label, fontSize = 24.sp, color = SecondaryTextColor, modifier = Modifier.weight(1f))
                                Text(value, fontSize = 28.sp, color = TextColor, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else if (!short) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RouteMetric("남은 거리", distance, RouteGlyph.DISTANCE, Modifier.weight(1f), compact)
                        RouteMetric("도착 예상", arrival, RouteGlyph.CLOCK, Modifier.weight(1f), compact)
                    }
                }

                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp))
                    .padding(if (short) 16.dp else if (compact) 20.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RouteIcon(RouteGlyph.GUIDANCE, colors.primary, Modifier.size(28.dp))
                        Text(if (route.issue != null) "내비 정보 확인 중" else "다음 안내", fontSize = 24.sp, color = SecondaryTextColor)
                    }
                    Text(route.issue?.takeIf { it.isNotBlank() }
                        ?: route.nextInstruction?.takeIf { it.isNotBlank() } ?: "경로 안내를 기다리고 있어요",
                        fontSize = if (short) 24.sp else if (compact) 28.sp else 32.sp,
                        lineHeight = if (short) 34.sp else if (compact) 40.sp else 44.sp,
                        fontWeight = FontWeight.Medium, color = TextColor,
                        maxLines = if (short) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(if (state.vehicle.isDemo) "시연에서 가상 경로를 확인해요" else "차량 내비에서 목적지를 정해요",
                    fontSize = 28.sp, lineHeight = 40.sp, color = SecondaryTextColor)
            }
            if (!driving && !state.vehicle.isDemo) MoodButton("차량 내비 열기", state.canInteract && route.connected,
                secondary = true, modifier = Modifier.fillMaxWidth(), onClick = onNavigation)
        }
    }
}

@Composable
private fun RemainingTime(minutes: Long?, numberSize: TextUnit, short: Boolean, modifier: Modifier = Modifier) {
    if (minutes != null) Text(buildAnnotatedString {
        withStyle(SpanStyle(fontSize = numberSize, fontWeight = FontWeight.SemiBold)) { append(minutes.toString()) }
        withStyle(SpanStyle(fontSize = if (short) 24.sp else 28.sp, fontWeight = FontWeight.Medium)) { append("분 남음") }
    }, modifier = modifier, color = LocalMoodColors.current.primary, lineHeight = numberSize * 1.15f,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    else Text("시간 확인 중", modifier = modifier, fontSize = if (short) 28.sp else 36.sp, lineHeight = 48.sp, color = TextColor)
}

@Composable
private fun RouteMetric(label: String, value: String, glyph: RouteGlyph, modifier: Modifier, compact: Boolean) {
    val colors = LocalMoodColors.current
    Column(modifier.background(colors.surface, RoundedCornerShape(24.dp)).padding(if (compact) 18.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!compact) RouteIcon(glyph, SecondaryTextColor, Modifier.size(24.dp))
            Text(label, fontSize = 24.sp, color = SecondaryTextColor, maxLines = 1)
        }
        Text(value, fontSize = if (compact) 28.sp else 32.sp, lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Decorative destination marker, independent of route geometry or journey progress. */
@Composable
private fun DestinationPin(modifier: Modifier) {
    val colors = LocalMoodColors.current
    Canvas(modifier.clearAndSetSemantics {}) {
        val s = size.minDimension
        drawCircle(colors.nearHill.copy(alpha = .26f), s * .5f)
        drawCircle(colors.surface, s * .34f, Offset(s * .58f, s * .4f))
        drawOval(colors.primary.copy(alpha = .12f), Offset(s * .28f, s * .77f), androidx.compose.ui.geometry.Size(s * .44f, s * .09f))
        drawPath(Path().apply {
            moveTo(s * .5f, s * .78f)
            cubicTo(s * .42f, s * .67f, s * .25f, s * .49f, s * .25f, s * .36f)
            cubicTo(s * .25f, s * .04f, s * .75f, s * .04f, s * .75f, s * .36f)
            cubicTo(s * .75f, s * .49f, s * .58f, s * .67f, s * .5f, s * .78f)
            close()
        }, colors.primary)
        drawCircle(colors.calm, s * .087f, Offset(s * .5f, s * .35f))
    }
}

private enum class RouteGlyph { DISTANCE, CLOCK, GUIDANCE }

@Composable
private fun RouteIcon(glyph: RouteGlyph, color: Color, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val s = size.minDimension
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x * s, y * s), Offset(x2 * s, y2 * s), stroke.width, StrokeCap.Round)
        when (glyph) {
            RouteGlyph.CLOCK -> {
                drawCircle(color, s * .4f, style = stroke)
                line(.5f, .25f, .5f, .5f); line(.5f, .5f, .7f, .61f)
            }
            RouteGlyph.DISTANCE -> {
                line(.12f, .5f, .88f, .5f)
                line(.12f, .27f, .12f, .73f); line(.88f, .27f, .88f, .73f)
                line(.5f, .37f, .5f, .63f)
            }
            RouteGlyph.GUIDANCE -> {
                drawCircle(color, s * .11f, Offset(s * .2f, s * .78f), style = stroke)
                drawCircle(color, s * .11f, Offset(s * .8f, s * .22f), style = stroke)
                drawPath(Path().apply {
                    moveTo(s * .2f, s * .67f); lineTo(s * .2f, s * .43f)
                    quadraticTo(s * .2f, s * .22f, s * .4f, s * .22f)
                    lineTo(s * .69f, s * .22f)
                }, color, style = stroke)
            }
        }
    }
}
