package mihon.feature.friends.cards

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.time.ZoneId
import kotlin.math.roundToInt

/** What [CardRenderer.live] keeps between frames. */
class CardLiveLayers(
    val below: Bitmap?,
    val top: Bitmap?,
    val belowEffects: List<String>,
    val topEffects: List<String>,
    val sample: Int,
    /** An animated card edit's GIF, played as the whole card (null: none, or a still). */
    val animation: CardFrameAnimation? = null,
    /** The AI card's rect (master pixels) when this is a still edit: the effects draw only there. */
    val editBox: CardBox? = null,
)

/** What a card's front shows, ready to draw. */
data class CardFace(
    val name: String,
    val seriesLine: String,
    val serial: String,
    /** "S1·2026": season and year (the rank is on the badge). */
    val season: String,
    val grade: CardGrade,
    val theme: CardTheme,
    val effects: List<String>,
    val accentHex: String?,
    val frameStyle: CardFrameStyle = CardFrameStyle.BORDER,
    /** The card number drawn over an AI card ("No. 0003"); null draws nothing. */
    val cardId: String? = null,
) {
    constructor(card: ShioriCard, zone: ZoneId) : this(
        name = card.cardName,
        seriesLine = card.seriesLine,
        serial = card.serialText,
        season = card.seasonText(zone),
        grade = card.grade,
        theme = card.theme,
        effects = card.effects,
        accentHex = card.accentHex,
        frameStyle = card.frameStyle,
        cardId = CardSpec.cardIdText(card.serial),
    )
}

/**
 * Draws cards from the pack in the spec's layer order (cards/ANDROID_CARDS.md §3–§6): the 1500 × 2100 PNG export
 * (animations at rest, holo at a 30° tilt) and the live view's static parts and effects.
 */
class CardRenderer(private val pack: CardPack) {

    /**
     * The card [width] pixels wide, drawing the layers [include] accepts; transparent outside the rounded card.
     * Null without a pack.
     */
    fun render(
        face: CardFace,
        art: Bitmap?,
        customFrame: Bitmap?,
        width: Int,
        tilt: CardTilt = CardTilt.EXPORT,
        animationFrame: Bitmap? = null,
        include: (CardLayer) -> Boolean = { true },
    ): Bitmap? {
        val manifest = pack.manifest ?: return null
        val scale = width / manifest.canvas.width
        val height = (manifest.canvas.height * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        clipCard(canvas, manifest)
        val drawer = Drawer(manifest, face, art, customFrame, CardGeometry.sampleSize(scale), animationFrame, scale)
        val edit = CardLayerPlan.isEdit(face, customFrame != null)
        plan(face, manifest, customFrame != null, animationFrame != null)
            .filter(include)
            .forEach { layer ->
                if (layer is CardLayer.Effect) {
                    val pose = restingPose(manifest, layer.id, tilt)
                    val dim = if (edit) CardLayerPlan.editEffectOpacity(face.grade, layer.id) else 1f
                    drawer.effect(canvas, layer.id, pose.copy(opacity = pose.opacity * dim))
                } else {
                    drawer.draw(canvas, layer)
                }
            }
        return bitmap
    }

    /**
     * The live view's cached parts at [width] pixels: below the frame without animated effects, and the frame with
     * what sits on it; the effects in between and on top are drawn every frame.
     */
    fun live(
        face: CardFace,
        art: Bitmap?,
        customFrame: Bitmap?,
        width: Int,
        animation: CardFrameAnimation? = null,
    ): CardLiveLayers? {
        val manifest = pack.manifest ?: return null
        val playing = animation?.takeIf { CardLayerPlan.isAnimated(face, true) }
        val first = playing?.firstFrame
        fun animated(layer: CardLayer) =
            layer is CardLayer.Effect && manifest.effects[layer.id]?.motion != CardAnimation.NONE
        // An animated edit's GIF plays as its own drawable
        val below = render(face, art, customFrame, width, animationFrame = first) { layer ->
            layer != CardLayer.AnimatedFrame && CardLayerPlan.isBelowFrame(layer, manifest) && !animated(layer)
        }
        // The card number is drawn by the view over the shine ([CardNumberOverlay])
        val top = render(face, art, customFrame, width, animationFrame = first) { layer ->
            layer !is CardLayer.Effect && layer != CardLayer.Metadata && !CardLayerPlan.isBelowFrame(layer, manifest)
        }
        val effects = plan(face, manifest, customFrame != null, playing != null).filterIsInstance<CardLayer.Effect>()
        return CardLiveLayers(
            below = below,
            top = top,
            belowEffects = effects.filter { CardLayerPlan.isBelowFrame(it, manifest) && animated(it) }.map { it.id },
            topEffects = effects.filterNot { CardLayerPlan.isBelowFrame(it, manifest) }.map { it.id },
            sample = CardGeometry.sampleSize(width / manifest.canvas.width),
            animation = playing,
            editBox = customFrame?.takeIf { playing == null && CardLayerPlan.isEdit(face, true) }?.let {
                CardGeometry.editRect(
                    it.width.toFloat(),
                    it.height.toFloat(),
                    manifest.canvas.width,
                    manifest.canvas.height,
                )
            },
        )
    }

    /** The 1500 × 2100 export. */
    fun export(face: CardFace, art: Bitmap?, customFrame: Bitmap?): Bitmap? =
        render(face, art, customFrame, pack.manifest?.canvas?.width?.roundToInt() ?: EXPORT_WIDTH)

    /** One effect at [pose] onto [canvas], which is in master pixels; [sample] picks the decode size. */
    fun drawEffect(
        canvas: Canvas,
        face: CardFace,
        id: String,
        pose: CardEffectPose,
        sample: Int,
        editBox: CardBox? = null,
    ) {
        val manifest = pack.manifest ?: return
        Drawer(manifest, face, null, null, sample).effect(canvas, id, pose, editBox)
    }

    /** The card's rounded rectangle (2.4 % of the width, v1.2): the art and every overlay are clipped to it. */
    fun clipCard(canvas: Canvas, manifest: CardManifest) {
        val c = manifest.canvas
        val radius = CardSpec.cornerRadius(c.width)
        canvas.clipPath(
            Path().apply { addRoundRect(RectF(0f, 0f, c.width, c.height), radius, radius, Path.Direction.CW) },
        )
    }

    /** Static effects at their opacity; `tilt` ones also at the given tilt. */
    private fun restingPose(manifest: CardManifest, id: String, tilt: CardTilt): CardEffectPose {
        val effect = manifest.effects.getValue(id)
        val animation = if (effect.motion == CardAnimation.TILT) CardAnimation.TILT else CardAnimation.NONE
        return CardMotion.pose(animation, effect.opacity, 0.0, tilt, CardTilt.travel(id))
    }

    private inner class Drawer(
        private val manifest: CardManifest,
        private val face: CardFace,
        private val art: Bitmap?,
        private val customFrame: Bitmap?,
        private val sample: Int,
        /** Frame 0 of an animated card edit (the live view's Reduce Motion still). */
        private val animationFrame: Bitmap? = null,
        /** Output pixels per master pixel (sizes the backdrop). */
        private val outputScale: Float = 1f,
    ) {
        private val full = RectF(0f, 0f, manifest.canvas.width, manifest.canvas.height)
        private val grade = manifest.grade(face.grade)
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val stillEditBox = customFrame?.takeIf {
            animationFrame == null && CardLayerPlan.isEdit(face, true)
        }?.let {
            CardGeometry.editRect(it.width.toFloat(), it.height.toFloat(), full.width(), full.height())
        }

        fun draw(canvas: Canvas, layer: CardLayer) {
            when (layer) {
                CardLayer.Base -> image(canvas, grade?.base, full)
                CardLayer.Backdrop -> (animationFrame ?: customFrame)?.let { backdrop(canvas, it) }
                CardLayer.Metadata -> drawMetadata(canvas)
                CardLayer.Art -> drawArt(canvas)
                CardLayer.CustomFrame -> customFrame?.let {
                    if (CardLayerPlan.isEdit(
                            face,
                            true,
                        )
                    ) {
                        editImage(canvas, it)
                    } else {
                        canvas.drawBitmap(it, null, full, bitmapPaint)
                    }
                }
                CardLayer.Frame -> image(canvas, grade?.frame, full)
                CardLayer.Ornament -> image(canvas, manifest.theme(face.theme)?.ornament, full)
                CardLayer.Emblem -> image(
                    canvas,
                    manifest.theme(face.theme)?.emblem,
                    manifest.regions.emblem.rect.toRectF(),
                )
                CardLayer.Badge -> image(canvas, grade?.badge, manifest.regions.badge.rect.toRectF())
                CardLayer.Scrim -> drawScrim(canvas)
                CardLayer.Text -> drawText(canvas)
                CardLayer.Wordmark -> drawWordmark(canvas)
                CardLayer.AnimatedFrame -> animationFrame?.let { editImage(canvas, it) }
                is CardLayer.Effect -> Unit
            }
        }

        private fun image(canvas: Canvas, path: String?, rect: RectF) {
            pack.image(path, sample)?.let { canvas.drawBitmap(it, null, rect, bitmapPaint) }
        }

        private fun mask(canvas: Canvas, path: String, mode: PorterDuff.Mode) {
            val mask = pack.image(path, sample) ?: return
            canvas.drawBitmap(mask, null, full, Paint(bitmapPaint).apply { xfermode = PorterDuffXfermode(mode) })
        }

        private fun applyClip(canvas: Canvas, clip: CardClip) {
            when (clip) {
                CardClip.CARD -> mask(canvas, manifest.masks.card, PorterDuff.Mode.DST_IN)
                CardClip.ART -> mask(canvas, manifest.masks.art, PorterDuff.Mode.DST_IN)
                CardClip.FRAME_ONLY -> {
                    mask(canvas, manifest.masks.card, PorterDuff.Mode.DST_IN)
                    mask(canvas, manifest.masks.art, PorterDuff.Mode.DST_OUT)
                }
            }
        }

        private fun drawArt(canvas: Canvas) {
            val art = art ?: return
            val window = CardBox.of(manifest.regions.art)
            val rect = CardGeometry.coverFit(art.width.toFloat(), art.height.toFloat(), window)
            val save = canvas.saveLayer(full, null)
            canvas.drawBitmap(art, null, rect.toRectF(), bitmapPaint)
            mask(canvas, manifest.masks.art, PorterDuff.Mode.DST_IN)
            canvas.restoreToCount(save)
        }

        /** Over a still AI card ([editBox], default from [customFrame]) the pass covers its image only. */
        fun effect(canvas: Canvas, id: String, pose: CardEffectPose, editBox: CardBox? = stillEditBox) {
            val effect = manifest.effects[id] ?: return
            val image = pack.effectImage(id, face.accentHex, sample) ?: return
            val blend = CardBlend.of(effect.blend, Build.VERSION.SDK_INT)
            val layerPaint = Paint().apply {
                alpha = (pose.opacity * blend.opacityScale * 255).roundToInt().coerceIn(0, 255)
                applyBlend(blend.op)
            }
            val save = canvas.saveLayer(full, layerPaint)
            if (effect.tile) {
                tile(canvas, image, pose)
            } else {
                canvas.withSave {
                    scale(pose.scale, pose.scale, full.centerX(), full.centerY())
                    rotate(pose.rotation, CardMotion.ROTATE_PIVOT_X, CardMotion.ROTATE_PIVOT_Y)
                    translate(pose.offsetX, pose.offsetY)
                    drawBitmap(image, null, full, bitmapPaint)
                    pose.wrap?.let { wrap ->
                        drawBitmap(image, null, RectF(full).apply { offset(0f, wrap) }, bitmapPaint)
                    }
                }
            }
            if (editBox == null) applyClip(canvas, CardLayerPlan.clip(effect.placement))
            // Shine and holo never cover the metadata zones (a soft fade, never a box)
            CardZoneFade.apply(canvas, 1f)
            canvas.restoreToCount(save)
        }

        /** 1:1 canvas pixels (the tile's own size), from the wrapped origin to past the card. */
        private fun tile(canvas: Canvas, image: Bitmap, pose: CardEffectPose) {
            val tileWidth = (image.width * sample).toFloat()
            val tileHeight = (image.height * sample).toFloat()
            var y = CardTilt.wrap(pose.offsetY, tileHeight)
            while (y < full.height()) {
                var x = CardTilt.wrap(pose.offsetX, tileWidth)
                while (x < full.width()) {
                    canvas.drawBitmap(image, null, RectF(x, y, x + tileWidth, y + tileHeight), bitmapPaint)
                    x += tileWidth
                }
                y += tileHeight
            }
        }

        /** The banner's gradient, clear at its top, masked by the art window so it follows its rounded corners. */
        private fun drawScrim(canvas: Canvas) {
            val scrim = grade?.bannerScrim ?: CardManifest.Scrim()
            val banner = manifest.regions.banner
            val ink = CardColor.argb(scrim.color, INK)
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    banner.y,
                    0f,
                    banner.bottom,
                    ColorUtils.setAlphaComponent(ink, 0),
                    ColorUtils.setAlphaComponent(ink, (scrim.opacity * 255).roundToInt().coerceIn(0, 255)),
                    Shader.TileMode.CLAMP,
                )
            }
            val save = canvas.saveLayer(full, null)
            canvas.drawRect(banner.x, banner.y, banner.right, banner.bottom, paint)
            mask(canvas, manifest.masks.art, PorterDuff.Mode.DST_IN)
            canvas.restoreToCount(save)
        }

        private fun drawText(canvas: Canvas) {
            val colors = grade?.text
            val muted = CardColor.argb(colors?.muted, MUTED)
            val available = CardTextLayout.RIGHT - CardTextLayout.LEFT

            val name = textPaint(pack.nameTypeface, CardColor.argb(colors?.name, WHITE))
            val nameText = face.name.take(CardRules.NAME_LIMIT)
            name.textSize =
                CardTextLayout.fittingSize(CardTextLayout.NAME_SIZE, CardTextLayout.NAME_MIN_SIZE, available) {
                    name.textSize = it
                    name.measureText(nameText)
                }
            line(canvas, nameText, name, CardTextLayout.LEFT, CardTextLayout.NAME_BASELINE, available)

            val series = textPaint(semibold, muted).apply { textSize = CardTextLayout.SERIES_SIZE }
            line(canvas, face.seriesLine, series, CardTextLayout.LEFT, CardTextLayout.SERIES_BASELINE, available)

            val footer = textPaint(semibold, muted).apply {
                textSize = CardTextLayout.FOOTER_SIZE
                fontFeatureSettings = "tnum"
                letterSpacing = 1f / CardTextLayout.FOOTER_SIZE
            }
            // The card number is the Metadata layer; the footer keeps the season on the right
            val third = CardTextLayout.footerThird
            footer.textAlign = Paint.Align.RIGHT
            line(canvas, face.season, footer, CardTextLayout.RIGHT, CardTextLayout.FOOTER_BASELINE, third)
        }

        /**
         * An AI card (v1.2): a 5:7 image fills the card; a legacy one is aspect-fitted and centred over its
         * [backdrop]. Both are clipped by the card's rounded rectangle ([clipCard]).
         */
        private fun editImage(canvas: Canvas, image: Bitmap) {
            val box = CardGeometry.editRect(image.width.toFloat(), image.height.toFloat(), full.width(), full.height())
            canvas.drawBitmap(image, null, box.toRectF(), bitmapPaint)
        }

        /** Under a legacy image: a blurred (API 31+), darkened copy covering the card. */
        private fun backdrop(canvas: Canvas, image: Bitmap) {
            if (!CardSpec.needsBackdrop(image.width.toFloat(), image.height.toFloat())) return
            val k = outputScale.coerceAtLeast(0.05f)
            val back = CardBackdrop.of(image, (full.width() * k).roundToInt(), (full.height() * k).roundToInt())
                ?: return
            canvas.drawBitmap(back, null, full, bitmapPaint)
        }

        private fun drawMetadata(canvas: Canvas) {
            face.cardId?.let { CardNumber.draw(canvas, it, pack.metadataTypeface) }
        }

        private fun line(canvas: Canvas, text: String, paint: TextPaint, x: Float, baseline: Float, width: Float) {
            val shown = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, x, baseline, paint)
        }

        private fun textPaint(typeface: Typeface, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = color
            setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, CardTextLayout.SHADOW_COLOR)
        }

        /** The wordmark, multiplied by the grade's tint with the wordmark's alpha. */
        private fun drawWordmark(canvas: Canvas) {
            val grade = grade ?: return
            val image = pack.image(grade.wordmark, sample) ?: return
            val box = CardTextLayout.wordmarkRect(
                (image.width * sample).toFloat(),
                (image.height * sample).toFloat(),
                full.width(),
            ).toRectF()
            val save = canvas.saveLayer(box, null)
            canvas.drawBitmap(image, null, box, bitmapPaint)
            grade.wordmarkTint?.let { tint ->
                canvas.drawRect(
                    box,
                    Paint().apply {
                        color = CardColor.argb(tint, WHITE)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    },
                )
                canvas.drawBitmap(
                    image,
                    null,
                    box,
                    Paint(bitmapPaint).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
                )
            }
            canvas.restoreToCount(save)
        }
    }

    companion object {
        const val EXPORT_WIDTH = 1500

        /** The layers a card is drawn with (export, live view, Discord render upload alike). */
        fun plan(face: CardFace, manifest: CardManifest, customFrame: Boolean, animation: Boolean = false) =
            CardLayerPlan.layers(face, manifest, customFrame, animation)
        private const val INK = 0xFF0F0E17.toInt()
        private const val WHITE = -0x1
        private const val MUTED = 0xFFA8A29E.toInt()
        private const val SHADOW_RADIUS = 6f
        private const val SHADOW_DY = 3f

        private val semibold: Typeface by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.DEFAULT, 600, false)
            } else {
                Typeface.DEFAULT_BOLD
            }
        }
    }
}

fun CardBox.toRectF() = RectF(left, top, right, bottom)

/** The card number ("No. 0003", CardSpec): Inter Medium, tabular, #F2EEE8 over a faint dark outline, no box. */
object CardNumber {
    /** [text] onto [canvas], which is in master pixels; nothing (a logged layout error) when it can't fit. */
    fun draw(canvas: Canvas, text: String, typeface: Typeface) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            fontFeatureSettings = "tnum"
        }
        val size = when (
            val fit = CardSpec.fitCardId { size ->
                paint.textSize = size
                paint.measureText(text)
            }
        ) {
            is CardSpec.Fit.Fits -> fit.size
            is CardSpec.Fit.TooWide -> {
                logcat(LogPriority.WARN) { "Card number \"$text\" doesn't fit its region (${fit.widthAtMinimum} px)" }
                return
            }
        }
        paint.textSize = size
        // The outline first (centred on the glyph edge, so half shows), then the fill
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = CardSpec.STROKE_WIDTH
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = CardSpec.STROKE_COLOR.toInt()
        canvas.drawText(text, CardSpec.CARD_ID.left, CardSpec.CARD_ID_BASELINE, paint)
        paint.style = Paint.Style.FILL
        paint.color = CardSpec.FILL.toInt()
        canvas.drawText(text, CardSpec.CARD_ID.left, CardSpec.CARD_ID_BASELINE, paint)
    }
}

fun CardManifest.Rect.toRectF() = RectF(x, y, right, bottom)

/** The manifest blend on a layer paint: [BlendMode] from API 29, the PorterDuff equivalent before. */
fun Paint.applyBlend(op: CardBlend.Op) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        blendMode = when (op) {
            CardBlend.Op.SRC_OVER -> BlendMode.SRC_OVER
            CardBlend.Op.SCREEN -> BlendMode.SCREEN
            CardBlend.Op.MULTIPLY -> BlendMode.MULTIPLY
            CardBlend.Op.OVERLAY -> BlendMode.OVERLAY
            CardBlend.Op.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
            CardBlend.Op.PLUS -> BlendMode.PLUS
        }
    } else {
        xfermode = PorterDuffXfermode(
            when (op) {
                CardBlend.Op.SRC_OVER -> PorterDuff.Mode.SRC_OVER
                CardBlend.Op.SCREEN -> PorterDuff.Mode.SCREEN
                CardBlend.Op.MULTIPLY -> PorterDuff.Mode.MULTIPLY
                CardBlend.Op.OVERLAY, CardBlend.Op.SOFT_LIGHT -> PorterDuff.Mode.OVERLAY
                CardBlend.Op.PLUS -> PorterDuff.Mode.ADD
            },
        )
    }
}
