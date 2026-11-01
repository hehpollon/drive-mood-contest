package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Original, static brand illustration. It never encodes a route or measured vehicle signals. */
@Composable
internal fun JourneyLandscape(modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    val night = colors == NightMoodColors
    Canvas(modifier.clip(RoundedCornerShape(40.dp)).clearAndSetSemantics {}.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(colors.sky)

        val sun = Offset(w * .76f, h * .24f)
        val radius = size.minDimension * .105f
        drawCircle(colors.sun, radius, sun)
        if (night) {
            drawCircle(colors.sky, radius * .9f, sun + Offset(radius * .48f, -radius * .27f))
            listOf(Offset(w * .25f, h * .19f), Offset(w * .49f, h * .12f), Offset(w * .9f, h * .39f))
                .forEach { drawCircle(colors.cloud, 2.dp.toPx(), it) }
        } else {
            drawRoundRect(colors.cloud, Offset(w * .11f, h * .22f), Size(w * .24f, h * .035f), androidx.compose.ui.geometry.CornerRadius(h * .03f))
            drawRoundRect(colors.cloud, Offset(w * .19f, h * .19f), Size(w * .10f, h * .055f), androidx.compose.ui.geometry.CornerRadius(h * .03f))
        }

        drawPath(Path().apply {
            moveTo(0f, h * .52f)
            cubicTo(w * .19f, h * .34f, w * .36f, h * .42f, w * .52f, h * .5f)
            cubicTo(w * .73f, h * .34f, w * .86f, h * .4f, w, h * .47f)
            lineTo(w, h); lineTo(0f, h); close()
        }, colors.distantHill)
        drawPath(Path().apply {
            moveTo(0f, h * .68f)
            cubicTo(w * .21f, h * .47f, w * .42f, h * .53f, w * .61f, h * .65f)
            cubicTo(w * .77f, h * .51f, w * .93f, h * .55f, w, h * .58f)
            lineTo(w, h); lineTo(0f, h); close()
        }, colors.nearHill)
        drawPath(Path().apply {
            moveTo(0f, h * .86f)
            cubicTo(w * .27f, h * .75f, w * .37f, h * .92f, w * .61f, h * .87f)
            cubicTo(w * .84f, h * .7f, w * .93f, h * .72f, w, h * .76f)
            lineTo(w, h); lineTo(0f, h); close()
        }, colors.meadow)

        // The widening ribbon suggests a journey, without pretending to be navigation.
        drawPath(Path().apply {
            moveTo(w * .12f, h)
            cubicTo(w * .18f, h * .83f, w * .7f, h * .82f, w * .67f, h * .68f)
            cubicTo(w * .65f, h * .59f, w * .45f, h * .55f, w * .58f, h * .45f)
            lineTo(w * .62f, h * .45f)
            cubicTo(w * .53f, h * .54f, w * .78f, h * .57f, w * .8f, h * .69f)
            cubicTo(w * .83f, h * .85f, w * .49f, h * .94f, w * .48f, h)
            close()
        }, colors.road)

        // A quiet roadside tree anchors the scene; no movement or attention effects.
        drawLine(colors.meadow, Offset(w * .19f, h * .54f), Offset(w * .19f, h * .66f), 3.dp.toPx(), StrokeCap.Round)
        drawOval(colors.meadow, Offset(w * .14f, h * .43f), Size(w * .10f, h * .17f))
    }
}
