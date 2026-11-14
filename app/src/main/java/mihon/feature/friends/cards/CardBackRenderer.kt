package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.roundToInt
import mihon.feature.friends.cards.CardBackLayout as L

/** What the back shows of a card: its grade (colours and rank), series and serial. No character name. */
data class CardBackFace(val grade: CardGrade, val series: String, val serial: Long?) {
    constructor(card: ShioriCard) : this(card.grade, card.seriesTitle, card.serial)
}

/** The back's faces: [sans] for the small lines (bundled Inter), [logo] a serif family that has 栞 (Noto Serif CJK). */
class CardBackFonts(val sans: Typeface, val logo: Typeface = systemSerifLogo()) {
    companion object {
        /** The system serif at weight 800: on Android its CJK fallback (Noto Serif CJK) draws 栞. */
        fun systemSerifLogo(): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SERIF, 800, false)
        } else {
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
    }
}

/**
 * Draws the v1.2 card back (pattern "Pull back" + layout "Halo", ported from the approved card-back board's SVG) with
 * android.graphics in the 750 × 1050 design space ([CardBackLayout]). The live view calls [draw] from a Compose
 * Canvas; exports call [render].
 */
object CardBackRenderer {

    /** The back [width] pixels wide (1500 for exports), 5:7, transparent outside the rounded card. */
    fun render(face: CardBackFace, fonts: CardBackFonts, width: Int = CardSpec.WIDTH.toInt()): Bitmap {
        val k = width / L.WIDTH
        val bitmap = Bitmap.createBitmap(width, CardSpec.heightFor(width), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(k, k)
        draw(canvas, face, fonts)
        return bitmap
    }

    /** The back onto [canvas], which is in design pixels (750 × 1050). */
    fun draw(canvas: Canvas, face: CardBackFace, fonts: CardBackFonts) {
        val g = L.palette(face.grade)
        val full = RectF(0f, 0f, L.WIDTH, L.HEIGHT)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(full, L.CORNER, L.CORNER, Path.Direction.CW) })

        // Background: bg2 at the centre to bg1 at the edge
        canvas.drawRect(
            full,
            fillPaint(
                ellipticalGradient(
                    L.WIDTH * L.BG_CX,
                    L.HEIGHT * L.BG_CY,
                    L.WIDTH * L.BG_R,
                    L.HEIGHT * L.BG_R,
                    intArrayOf(g.bg2.toInt(), g.bg1.toInt()),
                    null,
                ),
            ),
        )

        extras(canvas, g)

        // The pattern at the grade's strength
        val save = canvas.saveLayerAlpha(full, (L.patternAlpha(face.grade) * 255).roundToInt())
        pullBack(canvas, g)
        canvas.restoreToCount(save)

        halo(canvas, face, g, fonts)
        canvas.restore()
    }

    /** Mythic's aurora, Shiori's nebula and stars. */
    private fun extras(canvas: Canvas, g: CardBackPalette) {
        if (g.aurora) L.AURORA.forEach { glow(canvas, it) }
        if (g.stars) {
            glow(canvas, L.NEBULA)
            val star = Paint(Paint.ANTI_ALIAS_FLAG)
            L.STAR_FIELD.forEach {
                star.color = alpha(WHITE, it.alpha)
                canvas.drawCircle(it.x, it.y, it.radius, star)
            }
        }
    }

    private fun glow(canvas: Canvas, glow: L.Glow) {
        val color = glow.color.toInt()
        val shader = ellipticalGradient(
            glow.cx,
            glow.cy,
            glow.rx,
            glow.ry,
            intArrayOf(alpha(color, glow.alpha), alpha(color, 0f)),
            null,
        )
        canvas.drawOval(
            RectF(glow.cx - glow.rx, glow.cy - glow.ry, glow.cx + glow.rx, glow.cy + glow.ry),
            fillPaint(shader),
        )
    }

    /** P1 "Pull back": lattice, vignette, double frame, corner arcs with diamonds, ✦ marks, the dot and tick ring. */
    private fun pullBack(canvas: Canvas, g: CardBackPalette) {
        val a = g.a.toInt()
        canvas.drawPath(lattice, stroke(alpha(a, L.LATTICE_ALPHA), 1f))
        canvas.drawRect(
            0f,
            0f,
            L.WIDTH,
            L.HEIGHT,
            fillPaint(
                ellipticalGradient(
                    L.WIDTH * L.VIGNETTE_CX,
                    L.HEIGHT * L.VIGNETTE_CY,
                    L.WIDTH * L.VIGNETTE_R,
                    L.HEIGHT * L.VIGNETTE_R,
                    intArrayOf(alpha(BLACK, L.VIGNETTE_ALPHA), alpha(BLACK, 0f)),
                    floatArrayOf(0f, L.VIGNETTE_STOP),
                ),
            ),
        )

        val outer = L.OUTER
        canvas.drawRoundRect(
            outer.toRectF(),
            L.OUTER_RADIUS,
            L.OUTER_RADIUS,
            stroke(WHITE, L.OUTER_STROKE).apply { shader = metal(g, outer) },
        )
        canvas.drawRoundRect(
            L.INNER.toRectF(),
            L.INNER_RADIUS,
            L.INNER_RADIUS,
            stroke(alpha(a, L.INNER_ALPHA), L.INNER_STROKE),
        )

        val arc = stroke(alpha(a, L.ARC_ALPHA), L.ARC_STROKE)
        val diamond = stroke(a, L.DIAMOND_STROKE)
        L.CORNERS.forEach { c ->
            L.ARC_RADII.forEach { r ->
                // A quarter circle round the corner anchor, from its vertical edge to its horizontal one
                val oval = RectF(c.x - r, c.y - r, c.x + r, c.y + r)
                val start = when {
                    c.sx > 0 && c.sy > 0 -> 0f
                    c.sx < 0 && c.sy > 0 -> 90f
                    c.sx < 0 && c.sy < 0 -> 180f
                    else -> 270f
                }
                canvas.drawArc(oval, start, 90f, false, arc)
            }
            val dx = c.x + c.sx * L.DIAMOND_OFFSET
            val dy = c.y + c.sy * L.DIAMOND_OFFSET
            canvas.save()
            canvas.rotate(45f, dx, dy)
            canvas.drawRect(dx - L.DIAMOND / 2, dy - L.DIAMOND / 2, dx + L.DIAMOND / 2, dy + L.DIAMOND / 2, diamond)
            canvas.restore()
        }

        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = L.MARK_SIZE
            textAlign = Paint.Align.CENTER
            color = a
        }
        L.MARKS.forEach { (x, y) -> canvas.drawText(L.MARK, x, y + L.MARK_DY, mark) }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(L.DOTS) { i ->
            val d = L.dot(i)
            dot.color = alpha(a, d.alpha)
            canvas.drawCircle(d.x, d.y, d.radius, dot)
        }
        val tick = stroke(alpha(a, L.TICK_ALPHA), L.TICK_STROKE)
        repeat(L.TICKS) { i ->
            val (x1, y1, x2, y2) = L.tick(i)
            canvas.drawLine(x1, y1, x2, y2, tick)
        }
    }

    /** L25 "Halo": the grade-colour glow, 栞 in the grade's metal, the series and "№ 0013 · A". */
    private fun halo(canvas: Canvas, face: CardBackFace, g: CardBackPalette, fonts: CardBackFonts) {
        val a = g.a.toInt()
        canvas.drawCircle(
            L.HALO_X,
            L.HALO_Y,
            L.HALO_RADIUS,
            fillPaint(
                RadialGradient(
                    L.HALO_X,
                    L.HALO_Y,
                    L.HALO_RADIUS,
                    L.HALO_STOPS.map { alpha(a, it.second) }.toIntArray(),
                    L.HALO_STOPS.map { it.first }.toFloatArray(),
                    Shader.TileMode.CLAMP,
                ),
            ),
        )

        // 栞, centred on the halo (dominant-baseline central), in the grade's metal gradient
        val logo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.logo
            textSize = L.LOGO_SIZE
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        logo.getTextBounds(L.LOGO, 0, L.LOGO.length, bounds)
        val baseline = L.HALO_Y - bounds.exactCenterY()
        // Bounds are from a left-aligned origin; centred text starts half its advance to the left
        val box = CardBox(
            L.HALO_X - logo.measureText(L.LOGO) / 2 + bounds.left,
            baseline + bounds.top,
            bounds.width().toFloat(),
            bounds.height().toFloat(),
        )
        logo.shader = metal(g, box)
        logo.alpha = (L.LOGO_ALPHA * 255).roundToInt()
        canvas.drawText(L.LOGO, L.HALO_X, baseline, logo)

        val ink = g.ink.toInt()
        val series = L.seriesText(face.series)
        if (series.isNotEmpty()) {
            val paint = small(fonts, ink, L.SERIES_ALPHA)
            paint.textSize = L.fit(L.SERIES_SIZE, L.SERIES_MIN_SIZE, L.SERIES_MAX_WIDTH) { size ->
                paint.textSize = size
                paint.letterSpacing = L.SERIES_TRACKING / size
                paint.measureText(series)
            }
            paint.letterSpacing = L.SERIES_TRACKING / paint.textSize
            val shown = TextUtils.ellipsize(series, paint, L.SERIES_MAX_WIDTH, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, L.HALO_X, L.SERIES_BASELINE, paint)
        }

        val number = small(fonts, ink, L.NUMBER_ALPHA).apply {
            textSize = L.NUMBER_SIZE
            letterSpacing = L.NUMBER_TRACKING / L.NUMBER_SIZE
            fontFeatureSettings = "tnum"
        }
        canvas.drawText(L.numberText(face.serial, face.grade), L.HALO_X, L.NUMBER_BASELINE, number)
    }

    /** The lattice's diamonds over the whole card (one path, built once). */
    private val lattice: Path by lazy {
        Path().apply {
            val s = L.LATTICE
            val h = s / 2
            var y = 0f
            while (y < L.HEIGHT) {
                var x = 0f
                while (x < L.WIDTH) {
                    moveTo(x, y + h)
                    lineTo(x + h, y)
                    lineTo(x + s, y + h)
                    lineTo(x + h, y + s)
                    close()
                    x += s
                }
                y += s
            }
        }
    }

    /** The grade's metal running top-left → bottom-right across [box] (SVG objectBoundingBox 0,0 → 1,1). */
    private fun metal(g: CardBackPalette, box: CardBox): Shader {
        val colors = g.metal.map { it.toInt() }.toIntArray()
        val stops = FloatArray(colors.size) { it / (colors.size - 1f) }
        return LinearGradient(box.left, box.top, box.right, box.bottom, colors, stops, Shader.TileMode.CLAMP)
    }

    /** A radial gradient stretched into an ellipse (an SVG objectBoundingBox gradient on a non-square box). */
    private fun ellipticalGradient(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        colors: IntArray,
        stops: FloatArray?,
    ): Shader =
        RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(rx, ry)
                    postTranslate(cx, cy)
                },
            )
        }

    private fun small(fonts: CardBackFonts, color: Int, opacity: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.sans
        textAlign = Paint.Align.CENTER
        this.color = alpha(color, opacity)
    }

    private fun fillPaint(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private fun alpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * ((color ushr 24) and 0xFF)).roundToInt() shl 24)

    private const val WHITE = -0x1
    private const val BLACK = 0xFF000000.toInt()
}
