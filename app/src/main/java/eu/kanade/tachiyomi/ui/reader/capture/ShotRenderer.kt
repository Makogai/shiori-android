package eu.kanade.tachiyomi.ui.reader.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import eu.kanade.tachiyomi.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a screenshot with its label: the page pixels as they are (at most [MAX_WIDTH] wide), a footer bar under
 * them, or a frosted chip in the bottom corner over a blurred copy of the page. The label is laid out for a
 * [DESIGN_WIDTH] wide screen and scaled to the image.
 */
class ShotRenderer(private val context: Context) {

    private val brandName = context.getString(R.string.launcher_name)
    private val brandMark: Bitmap? by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.ic_shiori) }

    fun render(image: Bitmap, info: ShotInfo, cover: Bitmap?, layout: ShotLayout, @ColorInt accent: Int): Bitmap {
        val width = min(image.width, MAX_WIDTH)
        val height = (image.height.toFloat() * width / image.width).roundToInt()
        val unit = width / DESIGN_WIDTH
        val tint = if (ColorUtils.calculateContrast(accent, LABEL_BACKGROUND) >= 3) accent else Color.WHITE
        return when (layout) {
            ShotLayout.NONE -> if (width < image.width) image.scale(width, height) else image
            ShotLayout.FOOTER -> {
                val footerHeight = (FOOTER_HEIGHT * unit).roundToInt()
                val output = createBitmap(width, height + footerHeight)
                val canvas = Canvas(output)
                canvas.drawBitmap(image, null, Rect(0, 0, width, height), filterPaint)
                canvas.translate(0f, height.toFloat())
                canvas.scale(unit, unit)
                drawFooter(canvas, info, cover, tint)
                output
            }
            ShotLayout.CHIP -> {
                val output = createBitmap(width, height)
                val canvas = Canvas(output)
                canvas.drawBitmap(image, null, Rect(0, 0, width, height), filterPaint)
                val chipWidth = chipWidth(info)
                val frame = RectF(
                    CHIP_MARGIN * unit,
                    max(height - (CHIP_MARGIN + CHIP_HEIGHT) * unit, 0f),
                    (CHIP_MARGIN + chipWidth) * unit,
                    max(height - CHIP_MARGIN * unit, CHIP_HEIGHT * unit),
                )
                frostedGlass(canvas, output, frame, CHIP_RADIUS * unit)
                canvas.translate(frame.left, frame.top)
                canvas.scale(unit, unit)
                drawChip(canvas, info, cover, tint, chipWidth)
                output
            }
        }
    }

    private fun drawFooter(canvas: Canvas, info: ShotInfo, cover: Bitmap?, @ColorInt tint: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            FOOTER_HEIGHT,
            Color.rgb(23, 23, 23),
            Color.rgb(13, 13, 13),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, FOOTER_HEIGHT, paint)
        paint.shader = LinearGradient(
            0f,
            0f,
            DESIGN_WIDTH,
            0f,
            intArrayOf(Color.TRANSPARENT, withAlpha(tint, 0.8f), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, 1f, paint)

        val coverWidth = 34f
        drawCover(canvas, cover, RectF(16f, 12f, 16f + coverWidth, 12f + coverWidth * 1.5f), tint)

        val brandWidth = max(22f, textPaint(9f, BOLD, 0.8f).measureText(brandName))
        val textLeft = 16f + coverWidth + 12f
        val textWidth = DESIGN_WIDTH - 16f - brandWidth - 8f - textLeft
        val title = textPaint(14f, SEMIBOLD, 1f)
        val chapter = textPaint(11.5f, MEDIUM, 0.72f)
        val meta = textPaint(10.5f, REGULAR, 0.5f)
        val lines = listOf(
            info.title to title,
            info.chapter to chapter,
            listOf(info.pages, info.source).filter { it.isNotEmpty() }.joinToString(" · ") to meta,
        ).filter { it.first.isNotEmpty() }
        drawLines(canvas, lines, textLeft, textWidth, FOOTER_HEIGHT / 2, spacing = 2f)

        val brandLeft = DESIGN_WIDTH - 16f - brandWidth
        val brandPaint = textPaint(9f, BOLD, 0.8f)
        val blockHeight = 22f + 4f + lineHeight(brandPaint)
        val top = (FOOTER_HEIGHT - blockHeight) / 2
        drawMark(
            canvas,
            RectF(brandLeft + (brandWidth - 22f) / 2, top, brandLeft + (brandWidth + 22f) / 2, top + 22f),
            tint,
        )
        brandPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(brandName, brandLeft + brandWidth / 2, top + 26f - brandPaint.fontMetrics.ascent, brandPaint)
    }

    private fun chipText(info: ShotInfo): Pair<String, String> =
        info.title to listOf(info.chapter, info.pages).filter { it.isNotEmpty() }.joinToString(" · ")

    private fun chipWidth(info: ShotInfo): Float {
        val (title, subtitle) = chipText(info)
        val textWidth = max(
            textPaint(12f, SEMIBOLD, 1f).measureText(title),
            textPaint(9.5f, MEDIUM, 0.72f).measureText(subtitle),
        ).coerceAtMost(CHIP_TEXT_WIDTH)
        return 6f + 24f + 9f + textWidth + 9f + 0.5f + 9f + 18f + 9f
    }

    private fun drawChip(canvas: Canvas, info: ShotInfo, cover: Bitmap?, @ColorInt tint: Int, width: Float) {
        val shape = RectF(0f, 0f, width, CHIP_HEIGHT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = withAlpha(Color.BLACK, 0.42f)
        canvas.drawRoundRect(shape, CHIP_RADIUS, CHIP_RADIUS, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = withAlpha(Color.WHITE, 0.22f)
        shape.inset(0.3f, 0.3f)
        canvas.drawRoundRect(shape, CHIP_RADIUS, CHIP_RADIUS, paint)

        drawCover(canvas, cover, RectF(6f, 6f, 30f, 42f), tint)
        val (title, subtitle) = chipText(info)
        val textWidth = width - (6f + 24f + 9f) - (9f + 0.5f + 9f + 18f + 9f)
        val lines = listOf(
            title to textPaint(12f, SEMIBOLD, 1f),
            subtitle to textPaint(9.5f, MEDIUM, 0.72f),
        ).filter { it.first.isNotEmpty() }
        drawLines(canvas, lines, 39f, textWidth, CHIP_HEIGHT / 2, spacing = 1f)

        val dividerLeft = 39f + textWidth + 9f
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, 0.2f)
        canvas.drawRect(dividerLeft, (CHIP_HEIGHT - 26f) / 2, dividerLeft + 0.5f, (CHIP_HEIGHT + 26f) / 2, paint)
        val markLeft = dividerLeft + 0.5f + 9f
        drawMark(canvas, RectF(markLeft, (CHIP_HEIGHT - 18f) / 2, markLeft + 18f, (CHIP_HEIGHT + 18f) / 2), tint)
    }

    /** Lines of text one under the other, centred on [centerY], each cut short to [width]. */
    private fun drawLines(
        canvas: Canvas,
        lines: List<Pair<String, TextPaint>>,
        left: Float,
        width: Float,
        centerY: Float,
        spacing: Float,
    ) {
        val total = lines.sumOf { lineHeight(it.second).toDouble() }.toFloat() + spacing * max(lines.size - 1, 0)
        var y = centerY - total / 2
        lines.forEach { (text, paint) ->
            val shown = TextUtils.ellipsize(text, paint, max(width, 0f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, left, y - paint.fontMetrics.ascent, paint)
            y += lineHeight(paint) + spacing
        }
    }

    private fun drawCover(canvas: Canvas, cover: Bitmap?, frame: RectF, @ColorInt tint: Int) {
        val radius = frame.width() / 7
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (cover != null) {
            val path = Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) }
            canvas.withClip(path) {
                // Scaled to fill, cut to the middle
                val scale = max(frame.width() / cover.width, frame.height() / cover.height)
                val sourceWidth = frame.width() / scale
                val sourceHeight = frame.height() / scale
                val left = (cover.width - sourceWidth) / 2
                val top = (cover.height - sourceHeight) / 2
                drawBitmap(
                    cover,
                    Rect(left.toInt(), top.toInt(), (left + sourceWidth).toInt(), (top + sourceHeight).toInt()),
                    frame,
                    paint,
                )
            }
        } else {
            paint.color = withAlpha(tint, 0.6f)
            canvas.drawRoundRect(frame, radius, radius, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        paint.color = withAlpha(Color.WHITE, 0.15f)
        canvas.drawRoundRect(RectF(frame).apply { inset(0.25f, 0.25f) }, radius, radius, paint)
    }

    private fun drawMark(canvas: Canvas, frame: RectF, @ColorInt tint: Int) {
        val mark = brandMark ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(mark, null, frame, paint)
    }

    /** The image under [frame], blurred, clipped to a rounded rectangle: the chip's frosted glass. */
    private fun frostedGlass(canvas: Canvas, image: Bitmap, frame: RectF, radius: Float) {
        val source = Rect()
        frame.roundOut(source)
        if (!source.intersect(0, 0, image.width, image.height) || source.isEmpty) return
        val crop = Bitmap.createBitmap(image, source.left, source.top, source.width(), source.height())
        // Shrinking and growing again with filtering blurs about as much as the chip's 9 point blur
        val shrink = max(1, (frame.height() / CHIP_HEIGHT * BLUR_SHRINK).roundToInt())
        val small = crop.scale(max(1, crop.width / shrink), max(1, crop.height / shrink))
        val path = Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) }
        canvas.withClip(path) {
            drawBitmap(small, null, frame, filterPaint)
        }
        if (small !== crop) small.recycle()
        crop.recycle()
    }

    private fun textPaint(size: Float, weight: Int, alpha: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = withAlpha(Color.WHITE, alpha)
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            if (weight >= SEMIBOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun lineHeight(paint: Paint) = paint.fontMetrics.let { it.descent - it.ascent }

    private fun withAlpha(@ColorInt color: Int, alpha: Float) = ColorUtils.setAlphaComponent(
        color,
        (alpha * 255).roundToInt(),
    )

    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    companion object {
        const val MAX_WIDTH = 1440
        const val DESIGN_WIDTH = 390f

        private const val FOOTER_HEIGHT = 75f
        private const val CHIP_HEIGHT = 48f
        private const val CHIP_MARGIN = 14f
        private const val CHIP_RADIUS = 14f
        private const val CHIP_TEXT_WIDTH = 210f
        private const val BLUR_SHRINK = 12f

        private val LABEL_BACKGROUND = Color.rgb(20, 20, 20)

        private const val REGULAR = 400
        private const val MEDIUM = 500
        private const val SEMIBOLD = 600
        private const val BOLD = 700
    }
}
