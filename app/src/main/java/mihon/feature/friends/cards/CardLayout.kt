package mihon.feature.friends.cards

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One step of drawing a card, bottom to top (CARD_SPEC §5). */
sealed interface CardLayer {
    data object Base : CardLayer
    data object Art : CardLayer

    /** An AI-made frame with the art in it, replacing base…ornament (CARD_SPEC §12). */
    data object CustomFrame : CardLayer
    data class Effect(val id: String) : CardLayer
    data object Frame : CardLayer
    data object Ornament : CardLayer
    data object Emblem : CardLayer
    data object Badge : CardLayer

    /** The name banner's gradient, under the text. */
    data object Scrim : CardLayer
    data object Text : CardLayer
    data object Wordmark : CardLayer

    /** An animated card edit's GIF as the whole card (played by the live view; frame 0 in a Reduce Motion still). */
    data object AnimatedFrame : CardLayer

    /** Under a legacy (not 5:7) AI card: a blurred, darkened copy of it filling the card. */
    data object Backdrop : CardLayer

    /**
     * The app-drawn card number ("No. 0003", CardSpec), last on every card; the print fraction stays empty. On screen
     * it is drawn separately, over the shine ([CardNumberOverlay]).
     */
    data object Metadata : CardLayer
}

/** Where an effect may draw. */
enum class CardClip {
    CARD,
    ART,

    /** The card minus the art window. */
    FRAME_ONLY,
}

/**
 * The order layers are drawn in (cards/ANDROID_CARDS.md §3). Effects keep the card's list order inside their group;
 * unknown effect ids are skipped.
 */
object CardLayerPlan {

    /** [theme]: the card's theme; when given, the ornament layer is left out if it (or neutral) has none. */
    fun layers(
        effects: List<String>,
        manifest: CardManifest,
        customFrame: Boolean = false,
        theme: CardTheme? = null,
    ): List<CardLayer> {
        val known = effects.filter { it in manifest.effects }
        fun group(layer: EffectLayer) =
            known.filter { manifest.effects.getValue(it).placement == layer }.map { CardLayer.Effect(it) }
        val overlays = group(EffectLayer.OVER) + group(EffectLayer.FRAME)
        if (customFrame) {
            return listOf(
                CardLayer.CustomFrame,
                CardLayer.Emblem,
                CardLayer.Badge,
                CardLayer.Scrim,
                CardLayer.Text,
                CardLayer.Wordmark,
            ) + overlays + CardLayer.Metadata
        }
        val ornament = theme == null || hasOrnament(manifest.theme(theme))
        return listOf(CardLayer.Base, CardLayer.Art) + group(EffectLayer.UNDER) + group(EffectLayer.ART) +
            listOfNotNull(
                CardLayer.Frame,
                CardLayer.Ornament.takeIf { ornament },
                CardLayer.Emblem,
                CardLayer.Badge,
                CardLayer.Scrim,
                CardLayer.Text,
                CardLayer.Wordmark,
            ) + overlays + CardLayer.Metadata
    }

    /**
     * The app's effects over a still AI card (clipped to the AI image), richer per grade: Common, Uncommon and Rare
     * none; Epic only the tilt holo (at [EPIC_HOLO_OPACITY], see [editEffectOpacity]); Legendary the holo and foil;
     * Mythic and Secret also their own picked effects (sparkles, star dust, light rays…, as the server chose them
     * for the card) under the holo and foil. Matches iOS.
     */
    fun editEffects(face: CardFace): List<String> {
        val holo = listOf(HOLO, FOIL)
        return when (face.grade) {
            CardGrade.COMMON, CardGrade.UNCOMMON, CardGrade.RARE -> emptyList()
            CardGrade.EPIC -> listOf(HOLO)
            CardGrade.LEGENDARY -> holo
            CardGrade.MYTHIC, CardGrade.SECRET -> face.effects.filter { it !in holo } + holo
        }
    }

    /**
     * How much of its manifest opacity an effect over an AI card draws at: Epic's holo [EPIC_HOLO_OPACITY], the rest
     * in full. The live view, exports, the print sheet and the render upload all apply it (AI card edits only).
     */
    fun editEffectOpacity(grade: CardGrade, effectId: String): Float =
        if (grade == CardGrade.EPIC && effectId == HOLO) EPIC_HOLO_OPACITY else 1f

    const val HOLO = "holo_rainbow"
    const val FOIL = "foil_noise"
    const val EPIC_HOLO_OPACITY = 0.4f

    /**
     * On screen (the live view only) the tilt holo and foil draw a little stronger than their manifest opacity,
     * capped at [LIVE_OPACITY_CAP]: at the export opacity they barely show on a phone. Exports keep the manifest's.
     */
    fun liveEffectOpacity(effectId: String, opacity: Float): Float =
        if (effectId == HOLO || effectId == FOIL) min(LIVE_OPACITY_CAP, opacity * LIVE_HOLO_BOOST) else opacity

    const val LIVE_HOLO_BOOST = 1.3f
    const val LIVE_OPACITY_CAP = 0.6f

    /** The on-screen light effects of [grade] (see [CardShine]), drawn over every kind of card face. */
    fun shine(grade: CardGrade): CardShine = CardShine.of(grade)

    /**
     * Whether a card draws as an AI card edit: style edit and its image in hand (while it is pending, or when it
     * didn't download, the card is drawn with the pack frame).
     */
    fun isEdit(face: CardFace, customFrame: Boolean): Boolean = customFrame && face.frameStyle == CardFrameStyle.EDIT

    /** An animated card edit: style edit and its GIF decoded (else the static edit). */
    fun isAnimated(face: CardFace, animation: Boolean): Boolean = animation && face.frameStyle == CardFrameStyle.EDIT

    /**
     * The plan for a card: an animated or static AI card edit, a custom border or the pack frame. [animation]: a GIF
     * is at hand (only the live view has one; exports, uploads and thumbnails stay static).
     */
    fun layers(
        face: CardFace,
        manifest: CardManifest,
        customFrame: Boolean,
        animation: Boolean = false,
    ): List<CardLayer> =
        if (isAnimated(face, animation)) {
            listOf(CardLayer.Backdrop, CardLayer.AnimatedFrame, CardLayer.Metadata)
        } else if (isEdit(face, customFrame)) {
            // The AI drew the whole card (scene, rank, name); R+ get the app's effects over it, the app the number
            listOf(CardLayer.Backdrop, CardLayer.CustomFrame) +
                editEffects(face).filter { it in manifest.effects }.map(CardLayer::Effect) +
                CardLayer.Metadata
        } else {
            layers(face.effects, manifest, customFrame, face.theme)
        }

    fun hasOrnament(theme: CardManifest.Theme?): Boolean = !theme?.ornament.isNullOrEmpty()

    /** Layers drawn under the frame (the live view's "below" part). */
    fun isBelowFrame(layer: CardLayer, manifest: CardManifest): Boolean = when (layer) {
        CardLayer.Base, CardLayer.Art, CardLayer.CustomFrame, CardLayer.AnimatedFrame, CardLayer.Backdrop -> true
        is CardLayer.Effect -> manifest.effects[layer.id]?.placement.let {
            it == EffectLayer.UNDER ||
                it == EffectLayer.ART
        }
        else -> false
    }

    fun clip(layer: EffectLayer): CardClip = when (layer) {
        EffectLayer.UNDER, EffectLayer.OVER -> CardClip.CARD
        EffectLayer.ART -> CardClip.ART
        EffectLayer.FRAME -> CardClip.FRAME_ONLY
    }
}

/** A rectangle in floats (master-canvas pixels unless said otherwise). */
data class CardBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2

    fun intersect(other: CardBox): CardBox {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = min(right, other.right)
        val b = min(bottom, other.bottom)
        return CardBox(l, t, max(r - l, 0f), max(b - t, 0f))
    }

    companion object {
        fun of(rect: CardManifest.Rect) = CardBox(rect.x, rect.y, rect.w, rect.h)
    }
}

object CardGeometry {

    /** The uniform scale that fits [canvasWidth] × [canvasHeight] into the given size. */
    fun fitScale(canvasWidth: Float, canvasHeight: Float, width: Float, height: Float): Float =
        if (canvasWidth <= 0f || canvasHeight <= 0f) 1f else min(width / canvasWidth, height / canvasHeight)

    /**
     * Where an AI card image goes (v1.2): a 5:7 image fills the card; a legacy one (1024 × 1536 → 1400 × 2100) is
     * aspect-fitted and centred over its blurred backdrop. See [CardSpec.artRect].
     */
    fun editRect(imageWidth: Float, imageHeight: Float, canvasWidth: Float, canvasHeight: Float): CardBox =
        CardSpec.artRect(imageWidth, imageHeight, canvasWidth, canvasHeight)

    /** Where to draw an image so it covers [rect], centred, keeping its aspect ratio (it may overflow). */
    fun coverFit(imageWidth: Float, imageHeight: Float, rect: CardBox): CardBox {
        if (imageWidth <= 0f || imageHeight <= 0f) return rect
        val k = max(rect.width / imageWidth, rect.height / imageHeight)
        val w = imageWidth * k
        val h = imageHeight * k
        return CardBox(rect.centerX - w / 2, rect.centerY - h / 2, w, h)
    }

    /** The decode sample size (a power of two) for drawing the 1500 px canvas at [scale]. */
    fun sampleSize(scale: Float): Int {
        var sample = 1
        while (sample < 8 && scale * sample * 2 <= 1f) sample *= 2
        return sample
    }
}

/**
 * The on-screen light effects per grade, over the card (live view at full strength, album thumbnails at
 * [THUMB_STRENGTH]); procedural (gradients and a few dots, no bitmaps) and clipped to the card's rounded shape.
 * Deliberately light so the art stays readable: every alpha is at most [MAX_ALPHA]. C: none; U: a faint sheen;
 * R: a silver glare band sliding with the tilt; E: the glare and a faint violet foil; L: a gold glare, faint gold
 * foil, a thin edge glow and a few glints; M: aurora foil instead of gold; S: prismatic foil, the strongest glare.
 */
data class CardShine(
    /** A soft white diagonal sheen (screen). */
    val sheen: Float = 0f,
    /** The glare band (screen) following the tilt: its peak alpha, width as a share of the diagonal, colour. */
    val glare: Float = 0f,
    val glareWidth: Float = 0.3f,
    val glareColor: Long = WHITE,
    /** Foil colours (overlay), shifted along the diagonal by the tilt; empty for none. */
    val foil: List<Long> = emptyList(),
    val foilAlpha: Float = 0f,
    /** A few small twinkling glints. */
    val sparkles: Int = 0,
    val sparkleAlpha: Float = 0f,
    val sparkleColor: Long = WHITE,
    /** A thin glow along the card's edge (screen), inside the card. */
    val edgeGlow: Float = 0f,
    val edgeColor: Long = WHITE,
) {
    val isNone: Boolean get() = this == NONE

    /** Every alpha times [strength] (album thumbnails draw at [THUMB_STRENGTH]). */
    fun scaled(strength: Float): CardShine = copy(
        sheen = sheen * strength,
        glare = glare * strength,
        foilAlpha = foilAlpha * strength,
        sparkleAlpha = sparkleAlpha * strength,
        edgeGlow = edgeGlow * strength,
    )

    /** The strongest alpha of any wash over the card (the glints are tiny and not counted). */
    val peak: Float get() = maxOf(sheen, glare, foilAlpha, edgeGlow)

    companion object {
        const val WHITE = 0xFFFFFFFF
        const val MAX_ALPHA = 0.3f

        /** Glints stay at or under this, and at most [MAX_SPARKLES] of them. */
        const val MAX_SPARKLE_ALPHA = 0.5f
        const val MAX_SPARKLES = 4
        const val THUMB_STRENGTH = 0.8f

        val NONE = CardShine()

        private val SILVER = 0xFFE8EEF8
        private val GOLD = 0xFFFFD86B
        private val GOLD_LIGHT = 0xFFFFF1B8
        private val VIOLET_FOIL = listOf(0xFF7C3AED, 0xFFC026D3, 0xFF60A5FA, 0xFFC026D3, 0xFF7C3AED)
        private val GOLD_FOIL = listOf(0xFFB8860B, 0xFFFFE08A, 0xFFF5C542, 0xFFFFF4C2, 0xFFB8860B)
        private val AURORA_FOIL = listOf(0xFF22D3EE, 0xFF8B5CF6, 0xFFEC4899, 0xFF8B5CF6, 0xFF22D3EE)
        private val PRISM_FOIL =
            listOf(0xFFFF4D4D, 0xFFFFB84D, 0xFFFFF44D, 0xFF4DFF88, 0xFF4DD2FF, 0xFF8A4DFF, 0xFFFF4DD8, 0xFFFF4D4D)

        fun of(grade: CardGrade): CardShine = when (grade) {
            CardGrade.COMMON -> NONE
            CardGrade.UNCOMMON -> CardShine(sheen = 0.08f)
            CardGrade.RARE -> CardShine(glare = 0.2f, glareWidth = 0.3f, glareColor = SILVER)
            CardGrade.EPIC -> CardShine(glare = 0.22f, glareWidth = 0.32f, foil = VIOLET_FOIL, foilAlpha = 0.12f)
            CardGrade.LEGENDARY -> CardShine(
                glare = 0.25f,
                glareWidth = 0.34f,
                glareColor = GOLD_LIGHT,
                foil = GOLD_FOIL,
                foilAlpha = 0.12f,
                sparkles = 3,
                sparkleAlpha = 0.45f,
                sparkleColor = GOLD_LIGHT,
                edgeGlow = 0.15f,
                edgeColor = GOLD,
            )
            CardGrade.MYTHIC -> CardShine(
                glare = 0.26f,
                glareWidth = 0.34f,
                foil = AURORA_FOIL,
                foilAlpha = 0.14f,
                sparkles = 3,
                sparkleAlpha = 0.45f,
                edgeGlow = 0.15f,
                edgeColor = 0xFFA78BFA,
            )
            CardGrade.SECRET -> CardShine(
                glare = 0.28f,
                glareWidth = 0.36f,
                foil = PRISM_FOIL,
                foilAlpha = 0.16f,
                sparkles = 4,
                sparkleAlpha = 0.5f,
                edgeGlow = 0.15f,
                edgeColor = 0xFFF9A8D4,
            )
        }

        /**
         * Where the glare band's centre crosses the card's diagonal (0 top-left … 1 bottom-right): resting a little
         * up-left, sliding with the tilt (sideways counts more than forward), kept on the card.
         */
        fun glarePosition(tilt: CardTilt): Float = (0.35f + 0.45f * tilt.x + 0.25f * tilt.y).coerceIn(-0.1f, 1.1f)

        /** The foil's shift along its gradient (a share of the gradient's length): the colours roll with the tilt. */
        fun foilShift(tilt: CardTilt): Float = 0.6f * tilt.x + 0.35f * tilt.y

        /** A glint's place in card fractions, the same every frame (kept well inside the card). */
        fun point(index: Int, salt: Int): Pair<Float, Float> {
            val x = CardMotion.noise(index * 7.13 + salt).toFloat()
            val y = CardMotion.noise(index * 3.71 + salt * 2.3 + 11).toFloat()
            return (0.1f + 0.8f * x) to (0.08f + 0.84f * y)
        }

        /** A glint's brightness 0…1 at [seconds]: each on its own phase and speed, dark most of the time. */
        fun twinkle(index: Int, seconds: Double): Float {
            val speed = 0.5 + 0.5 * CardMotion.noise(index + 0.5)
            val phase = CardMotion.noise(index * 1.9 + 3)
            val wave = kotlin.math.sin(2 * Math.PI * (seconds * speed + phase)).toFloat()
            return (wave * wave * wave).coerceAtLeast(0f)
        }
    }
}

/** Tilt (each axis −1…1) as the holo and the 3-D swing use it. */
data class CardTilt(val x: Float = 0f, val y: Float = 0f) {
    companion object {
        /** 30° of a 45° full swing, sideways: what exports show. */
        val EXPORT = CardTilt(30f / 45f, 0f)
        const val HOLO_TRAVEL = 600f
        const val FOIL_TRAVEL = 240f
        private const val FULL_SWING = (Math.PI / 4).toFloat()

        fun travel(effectId: String): Float = if (effectId == "foil_noise") FOIL_TRAVEL else HOLO_TRAVEL

        /** Roll and pitch (radians, from where the device was) as a tilt. */
        fun fromAttitude(roll: Float, pitch: Float) = CardTilt(
            (roll / FULL_SWING).coerceIn(-1f, 1f),
            (pitch / FULL_SWING).coerceIn(-1f, 1f),
        )

        /** A drag's tilt: the finger's travel over half the card's width or height. */
        fun fromDrag(dx: Float, dy: Float, width: Float, height: Float): CardTilt {
            if (width <= 0f || height <= 0f) return CardTilt()
            return CardTilt((dx / (width / 2)).coerceIn(-1f, 1f), (dy / (height / 2)).coerceIn(-1f, 1f))
        }

        /** Where a tiled texture starts for an offset: wrapped into (−tile, 0]. */
        fun wrap(value: Float, tile: Float): Float {
            if (tile <= 0f) return 0f
            val rest = value % tile
            return if (rest > 0f) rest - tile else rest
        }
    }
}

/** The name banner's text, master pixels; positions are baselines (cards/ANDROID_CARDS.md §6). */
object CardTextLayout {
    const val LEFT = 92f
    const val RIGHT = 1408f

    /** v1.2: name and series sit above the card number's exclusion zone (y ≥ 1932). */
    const val NAME_BASELINE = 1856f
    const val NAME_SIZE = 64f
    const val NAME_MIN_SIZE = 44f
    const val SERIES_BASELINE = 1912f
    const val SERIES_SIZE = 34f
    const val FOOTER_BASELINE = 2035f
    const val FOOTER_SIZE = 24f
    const val WORDMARK_HEIGHT = 28f
    const val WORDMARK_CENTER_Y = 2027f
    const val SHADOW_COLOR = 0x59000000

    /** The footer's left and right parts each get a third of the row. */
    val footerThird: Float get() = (RIGHT - LEFT) / 3

    fun wordmarkRect(imageWidth: Float, imageHeight: Float, canvasWidth: Float): CardBox {
        if (imageHeight <= 0f) return CardBox(0f, 0f, 0f, 0f)
        val width = imageWidth * WORDMARK_HEIGHT / imageHeight
        return CardBox(canvasWidth / 2 - width / 2, WORDMARK_CENTER_Y - WORDMARK_HEIGHT / 2, width, WORDMARK_HEIGHT)
    }

    /** The largest size from [start] down to [minimum] in steps of 2 at which the text fits; else [minimum]. */
    fun fittingSize(start: Float, minimum: Float, available: Float, width: (Float) -> Float): Float {
        var size = start
        while (size > minimum && width(size) > available) size -= 2
        return max(size, minimum)
    }
}

/** The crop screen's maths: a fixed frame in the art window's shape over the capture, panned and zoomed. */
object CardCrop {
    const val ASPECT = CardRules.ART_WIDTH.toFloat() / CardRules.ART_HEIGHT
    const val MAX_ZOOM = 6f

    /** The largest frame of that shape inside [width] × [height] less [inset] on every side, centred. */
    fun frame(width: Float, height: Float, inset: Float): CardBox {
        val w = max(width - 2 * inset, 1f)
        val h = max(height - 2 * inset, 1f)
        val (fw, fh) = if (w / h > ASPECT) h * ASPECT to h else w to w / ASPECT
        return CardBox((width - fw) / 2, (height - fh) / 2, fw, fh)
    }

    /** View pixels per image pixel at zoom 1: the image just covers the frame. */
    fun baseScale(imageWidth: Float, imageHeight: Float, frameWidth: Float, frameHeight: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f) return 1f
        return max(frameWidth / imageWidth, frameHeight / imageHeight)
    }

    /** The image centre's shift from the frame centre, kept so the image still covers the frame. */
    fun clamp(
        offsetX: Float,
        offsetY: Float,
        imageWidth: Float,
        imageHeight: Float,
        frameWidth: Float,
        frameHeight: Float,
        zoom: Float,
    ): Pair<Float, Float> {
        val scale = baseScale(imageWidth, imageHeight, frameWidth, frameHeight) * max(zoom, 1f)
        val slackX = max((imageWidth * scale - frameWidth) / 2, 0f)
        val slackY = max((imageHeight * scale - frameHeight) / 2, 0f)
        return offsetX.coerceIn(-slackX, slackX) to offsetY.coerceIn(-slackY, slackY)
    }

    /** The part of the image, in its pixels, inside the frame. */
    fun cropRect(
        imageWidth: Float,
        imageHeight: Float,
        frameWidth: Float,
        frameHeight: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
    ): CardBox {
        val z = max(zoom, 1f)
        val (ox, oy) = clamp(offsetX, offsetY, imageWidth, imageHeight, frameWidth, frameHeight, z)
        val scale = baseScale(imageWidth, imageHeight, frameWidth, frameHeight) * z
        val w = frameWidth / scale
        val h = frameHeight / scale
        val cx = imageWidth / 2 - ox / scale
        val cy = imageHeight / 2 - oy / scale
        return CardBox(cx - w / 2, cy - h / 2, w, h).intersect(CardBox(0f, 0f, imageWidth, imageHeight))
    }

    /** The upload's size: the crop's pixels scaled down (never up) to fit 1428 × 2028. */
    fun outputSize(cropWidth: Float, cropHeight: Float): Pair<Int, Int> {
        val scale = min(
            1f,
            min(CardRules.ART_WIDTH / max(cropWidth, 1f), CardRules.ART_HEIGHT / max(cropHeight, 1f)),
        )
        return (cropWidth * scale).roundToInt() to (cropHeight * scale).roundToInt()
    }
}

/** How a manifest `blend` composites; [opacityScale] multiplies the effect's opacity. */
data class CardBlend(val op: Op, val opacityScale: Float = 1f) {
    enum class Op { SRC_OVER, SCREEN, MULTIPLY, OVERLAY, SOFT_LIGHT, PLUS }

    companion object {
        /** Soft light has no PorterDuff mode before API 29: overlay at 0.7 × the opacity instead. */
        fun of(blend: String, sdk: Int): CardBlend = when (blend) {
            "screen" -> CardBlend(Op.SCREEN)
            "multiply" -> CardBlend(Op.MULTIPLY)
            "overlay" -> CardBlend(Op.OVERLAY)
            "softLight" -> if (sdk >= 29) CardBlend(Op.SOFT_LIGHT) else CardBlend(Op.OVERLAY, 0.7f)
            "add" -> CardBlend(Op.PLUS)
            else -> CardBlend(Op.SRC_OVER)
        }
    }
}
