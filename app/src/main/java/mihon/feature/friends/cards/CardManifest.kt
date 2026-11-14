package mihon.feature.friends.cards

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `shiori_cards/manifest.json` (cards/CODEX_ASSET_PROMPT.md). Every path, blend mode, opacity, colour and font comes
 * from it, so a new pack needs no code change.
 */
@Serializable
data class CardManifest(
    val version: Int = 1,
    val style: String? = null,
    val canvas: Canvas = Canvas(),
    val regions: Regions,
    val fonts: Fonts? = null,
    val grades: Map<String, Grade>,
    val themes: Map<String, Theme>,
    val effects: Map<String, Effect>,
    val back: String,
    val masks: Masks,
) {
    @Serializable
    data class Canvas(val width: Float = 1500f, val height: Float = 2100f, val cornerRadius: Float = 60f)

    @Serializable
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val cornerRadius: Float? = null) {
        val right: Float get() = x + w
        val bottom: Float get() = y + h
    }

    @Serializable
    data class Circle(val cx: Float, val cy: Float, val size: Float) {
        val rect: Rect get() = Rect(cx - size / 2, cy - size / 2, size, size)
    }

    @Serializable
    data class Regions(val art: Rect, val badge: Circle, val emblem: Circle, val banner: Rect)

    @Serializable
    data class Fonts(val name: String? = null)

    @Serializable
    data class TextColors(val name: String, val body: String, val muted: String)

    @Serializable
    data class Scrim(val color: String = "#0F0E17", val opacity: Float = 0.78f)

    @Serializable
    data class Grade(
        val base: String,
        val frame: String,
        val badge: String,
        val glow: String,
        val text: TextColors,
        val bannerScrim: Scrim? = null,
        val wordmark: String,
        val wordmarkTint: String? = null,
        val accent: String,
    )

    @Serializable
    data class Theme(
        /** The theme's frame ornament; packs without ornaments leave it out (the layer is skipped). */
        val ornament: String? = null,
        val emblem: String,
        val accent: String,
        val preferredEffects: List<String>? = null,
    )

    @Serializable
    data class Effect(
        val file: String,
        val tile: Boolean = false,
        val layer: String = LAYER_OVER,
        val blend: String = "normal",
        val opacity: Float = 1f,
        val animation: String = "none",
        val frames: List<String>? = null,
        val tintWithAccent: Boolean? = null,
    ) {
        val placement: EffectLayer get() = EffectLayer.of(layer)

        val motion: CardAnimation get() = CardAnimation.of(animation)
    }

    @Serializable
    data class Masks(val art: String, val card: String)

    fun grade(grade: CardGrade): Grade? = grades[grade.code]

    /** Unknown themes draw as neutral. */
    fun theme(theme: CardTheme): Theme? = themes[theme.code] ?: themes[CardTheme.NEUTRAL.code]

    companion object {
        const val LAYER_OVER = "over"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(text: String): CardManifest = json.decodeFromString(serializer(), text)
    }
}

/** Where an effect draws (manifest `layer`). */
enum class EffectLayer(val code: String) {
    /** Above the art, below the frame, clipped to the card. */
    UNDER("under"),

    /** Above the art, clipped to the art window. */
    ART("art"),

    /** On the frame only: the card minus the art window. */
    FRAME("frame"),

    /** The whole card, on top. */
    OVER("over"),
    ;

    companion object {
        fun of(code: String): EffectLayer = entries.firstOrNull { it.code == code } ?: OVER
    }
}

enum class CardAnimation(val code: String) {
    NONE("none"),
    TILT("tilt"),
    TWINKLE("twinkle"),
    DRIFT("drift"),
    RISE("rise"),
    FALL("fall"),
    FLICKER("flicker"),
    PULSE("pulse"),
    ROTATE("rotate"),
    JITTER("jitter"),
    ;

    companion object {
        fun of(code: String): CardAnimation = entries.firstOrNull { it.code == code } ?: NONE
    }
}
