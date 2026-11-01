package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hehpollon.drivemood.domain.WavePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin


@Composable
internal fun MoodPanel(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(SurfaceColor, RoundedCornerShape(20.dp))
        .then(if (accent != null) Modifier.border(1.dp, accent.copy(alpha = .25f), RoundedCornerShape(20.dp)) else Modifier)
        .padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}

@Composable
internal fun Eyebrow(text: String, color: Color = RhythmBlue) {
    Text(text, color = color, fontSize = 20.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun Badge(text: String, color: Color = RhythmBlue) {
    Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(color.copy(alpha = .1f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
internal fun MoodButton(text: String, enabled: Boolean = true, secondary: Boolean = false, modifier: Modifier = Modifier, prominent: Boolean = false, onClick: () -> Unit) {
    val colors = LocalMoodColors.current
    Button(onClick, enabled = enabled, modifier = modifier.sizeIn(minWidth = 112.dp, minHeight = if (prominent) 128.dp else 112.dp), shape = RoundedCornerShape(20.dp),
        border = if (secondary && enabled) BorderStroke(1.dp, colors.outline.copy(alpha = .55f)) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) colors.surface else colors.primary, contentColor = if (secondary) TextColor else colors.onPrimary,
            disabledContainerColor = RaisedColor, disabledContentColor = SecondaryTextColor),
        contentPadding = PaddingValues(horizontal = if (prominent) 32.dp else 24.dp, vertical = 24.dp)) {
        Text(text, fontSize = if (prominent) 32.sp else 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun Metric(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = SecondaryTextColor, fontSize = 24.sp)
        Text(value, color = TextColor, fontSize = 40.sp, fontWeight = FontWeight.Medium)
        if (detail != null) Text(detail, color = SecondaryTextColor, fontSize = 20.sp)
    }
}

/** Gaps remain gaps; no decorative or invented vehicle signal is drawn. */
@Composable
internal fun RhythmWave(points: List<WavePoint>, color: Color, modifier: Modifier = Modifier, breath: Float? = null) {
    val colors = LocalMoodColors.current
    Canvas(modifier.fillMaxWidth().height(165.dp)) {
        val mid = size.height / 2
        repeat(3) { row ->
            val y = size.height * (row + 1) / 4
            drawLine(colors.secondaryText.copy(alpha = .10f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (breath != null) {
            val path = Path()
            for (i in 0..180) {
                val x = size.width * i / 180f
                val y = mid - sin(i / 180f * 4 * PI + breath * 2 * PI).toFloat() * mid * .55f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors.secondaryText, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))))
        }
        if (points.isEmpty()) return@Canvas
        val end = points.last().at
        val start = (end - 89_000).coerceAtMost(points.first().at)
        val span = (end - start).coerceAtLeast(1).toFloat()
        val path = Path()
        var connected = false
        points.forEach { point ->
            val value = point.angle
            if (value == null || !value.isFinite()) connected = false else {
                val x = (point.at - start) / span * size.width
                val y = mid - (value / 25f).coerceIn(-1f, 1f) * mid * .85f
                if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                connected = true
                if (point.braking) drawCircle(colors.warm, 3.dp.toPx(), Offset(x, size.height - 7.dp.toPx()))
            }
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun ScoreChart(scores: List<Int>, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        repeat(3) { index ->
            val y = size.height * index / 2
            drawLine(colors.secondaryText.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y))
        }
        if (scores.isEmpty()) return@Canvas
        val path = Path()
        scores.forEachIndexed { index, score ->
            val x = if (scores.size == 1) size.width / 2 else size.width * index / (scores.size - 1)
            val y = size.height * (1 - score / 100f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(if (score < 55) colors.warm else colors.rhythm, 3.dp.toPx(), Offset(x, y))
        }
        drawPath(path, colors.rhythm, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

internal fun duration(seconds: Long): String = when {
    seconds >= 3_600 -> "${seconds / 3_600}시간 ${(seconds % 3_600) / 60}분"
    seconds >= 60 -> "${seconds / 60}분 ${seconds % 60}초"
    else -> "${seconds}초"
}
internal fun clockTime(epoch: Long): String = SimpleDateFormat("HH:mm", Locale.KOREAN).format(Date(epoch))
internal fun dateTime(epoch: Long): String = SimpleDateFormat("M월 d일 E요일 · HH:mm", Locale.KOREAN).format(Date(epoch))
internal fun temperature(value: Float?): String = value?.let { String.format(Locale.KOREAN, "%.1f°C", it) } ?: "미제공"
