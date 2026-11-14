package mihon.feature.friends.cards

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws [shine] over the card filling this scope. Every part, the edge glow and glints included, is drawn inside
 * one clip to the card's rounded shape, so nothing reaches past its edge; the foil and the screen-lit parts each draw
 * on their own layer (composited with [BlendMode.Overlay] and [BlendMode.Screen]) which then fades out over the
 * metadata zones ([CardZoneFade]), so nothing covers the card number and no box shows. [seconds]: the glints' clock (0 for a
 * still card). Glare and glints use [BlendMode.Screen], foil [BlendMode.Overlay]; alphas are capped at
 * [CardShine.MAX_ALPHA] (glints at [CardShine.MAX_SPARKLE_ALPHA]) so the art stays readable.
 */
fun DrawScope.drawCardShine(shine: CardShine, tilt: CardTilt, seconds: Double) {
    if (shine.isNone || size.minDimension <= 0f) return
    val w = size.width
    val h = size.height
    val radius = CardSpec.cornerRadius(w)
    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius))) }
    clipPath(outline) {
        val diagonal = hypot(w, h)
        // The card's diagonal direction (top-left → bottom-right) and the bands across it
        val ux = w / diagonal
        val uy = h / diagonal
        fun along(t: Float) = Offset(ux * diagonal * t, uy * diagonal * t)

        if (shine.foilAlpha > 0f && shine.foil.isNotEmpty()) {
            val shift = CardShine.foilShift(tilt)
            fadedLayer(BlendMode.Overlay) {
                drawRect(
                    brush = Brush.linearGradient(
                        shine.foil.map { Color(it) },
                        start = along(shift),
                        end = along(shift + 0.9f),
                        tileMode = TileMode.Mirror,
                    ),
                    alpha = shine.foilAlpha.cap(),
                )
            }
        }
        fadedLayer(BlendMode.Screen) { screenParts(shine, tilt, seconds, along = ::along, ux, uy, diagonal, radius) }
    }
}

/** The screen-lit parts (sheen, glare, edge glow, glints), drawn plainly on a layer composited with Screen. */
private fun DrawScope.screenParts(
    shine: CardShine,
    tilt: CardTilt,
    seconds: Double,
    along: (Float) -> Offset,
    ux: Float,
    uy: Float,
    diagonal: Float,
    radius: Float,
) {
    val w = size.width
    val h = size.height
    run {
        if (shine.sheen > 0f) {
            val at = 0.3f + 0.2f * tilt.x + 0.1f * tilt.y
            band(along(at), ux, uy, diagonal * 0.6f, Color.White, shine.sheen.cap())
        }

        if (shine.glare > 0f) {
            val at = CardShine.glarePosition(tilt)
            band(along(at), ux, uy, diagonal * shine.glareWidth, Color(shine.glareColor), shine.glare.cap())
        }

        if (shine.edgeGlow > 0f) {
            // A thin stroke wholly inside the card (inset by half its width)
            val stroke = w * 0.012f
            val inset = stroke / 2
            drawRoundRect(
                color = Color(shine.edgeColor),
                topLeft = Offset(inset, inset),
                size = Size(w - stroke, h - stroke),
                cornerRadius = CornerRadius(max(0f, radius - inset)),
                style = Stroke(width = stroke),
                alpha = shine.edgeGlow.cap(),
            )
        }

        val sparkles = minOf(shine.sparkles, CardShine.MAX_SPARKLES)
        if (sparkles > 0 && shine.sparkleAlpha > 0f) {
            val c = Color(shine.sparkleColor)
            val alpha = shine.sparkleAlpha.coerceIn(0f, CardShine.MAX_SPARKLE_ALPHA)
            for (i in 0 until sparkles) {
                val (x, y) = CardShine.point(i, salt = 7)
                val light = if (seconds == 0.0) {
                    if (i == 0) 0.6f else 0f
                } else {
                    CardShine.twinkle(i, seconds)
                }
                if (light < 0.05f) continue
                val r = w * (0.012f + 0.008f * light)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White,
                        0.35f to c.copy(alpha = 0.5f),
                        1f to Color.Transparent,
                        center = Offset(w * x, h * y),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(w * x, h * y),
                    alpha = alpha * light,
                )
            }
        }
    }
}

private fun Float.cap() = coerceIn(0f, CardShine.MAX_ALPHA)

/**
 * Runs [block] on an offscreen layer composited onto the card with [mode], then fades that layer out over the metadata
 * zones ([CardZoneFade], scaled from master pixels).
 */
private fun DrawScope.fadedLayer(mode: BlendMode, block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { blendMode = mode })
        block()
        CardZoneFade.apply(canvas.nativeCanvas, CardSpec.scale(size.width))
        canvas.restore()
    }
}

/** A soft band (on the screen layer) across the card, centred at [center], perpendicular to the diagonal ([ux], [uy]). */
private fun DrawScope.band(center: Offset, ux: Float, uy: Float, width: Float, color: Color, alpha: Float) {
    val half = Offset(ux * width / 2, uy * width / 2)
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.4f to color.copy(alpha = 0.6f),
            0.5f to color,
            0.6f to color.copy(alpha = 0.6f),
            1f to Color.Transparent,
            start = center - half,
            end = center + half,
        ),
        alpha = alpha,
    )
}
