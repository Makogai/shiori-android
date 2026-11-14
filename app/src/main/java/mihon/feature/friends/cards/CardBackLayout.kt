package mihon.feature.friends.cards

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A grade's colours on the back, from the `G` table of the approved card-back board (backs/v4/card-backs.html):
 * [a] the accent, [ink] the text, [bg1]/[bg2] the background's edge and centre, [k] the pattern's strength, [metal]
 * the logo's and outer frame's gradient; Mythic adds the aurora, Shiori (SR) the stars.
 */
data class CardBackPalette(
    val a: Long,
    val b: Long,
    val c: Long,
    val bg1: Long,
    val bg2: Long,
    val ink: Long,
    val k: Float,
    val metal: List<Long>,
    val aurora: Boolean = false,
    val stars: Boolean = false,
)

/** A corner of the pattern: its anchor and which way the arcs open. */
data class CardBackCorner(val x: Float, val y: Float, val sx: Int, val sy: Int)

/** A dot of the ring, a star of the SR sky. */
data class CardBackDot(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/**
 * The v1.2 card back: pattern P1 "Pull back" and layout L25 "Halo", title × 1.25, logo × 1.35, pattern strength
 * normal (DECISIONS "Card back"). Numbers are the board's 750 × 1050 design space, drawn scaled uniformly. Pure
 * numbers so the renderer and the tests read the same table (and iOS the same values).
 */
object CardBackLayout {

    const val WIDTH = 750f
    const val HEIGHT = 1050f

    /** The same 2.4 % mask as the front. */
    const val CORNER = WIDTH * CardSpec.RADIUS_SHARE

    const val TITLE_SCALE = 1.25f
    const val LOGO_SCALE = 1.35f
    const val PATTERN_STRENGTH = 1f

    // Background: radial bg2 → bg1 (objectBoundingBox cx .5, cy .42, r .85)
    const val BG_CX = 0.5f
    const val BG_CY = 0.42f
    const val BG_R = 0.85f

    // Lattice: 56 px diamonds, accent at 16 %
    const val LATTICE = 56f
    const val LATTICE_ALPHA = 0.16f

    // Vignette: black 30 % at the centre fading out by 60 % (cx .5, cy .43, r .7)
    const val VIGNETTE_CX = 0.5f
    const val VIGNETTE_CY = 0.43f
    const val VIGNETTE_R = 0.7f
    const val VIGNETTE_ALPHA = 0.3f
    const val VIGNETTE_STOP = 0.6f

    // Double frame
    val OUTER = CardBox(18f, 18f, 714f, 1014f)
    const val OUTER_RADIUS = 18f
    const val OUTER_STROKE = 2f
    val INNER = CardBox(28f, 28f, 694f, 994f)
    const val INNER_RADIUS = 12f
    const val INNER_ALPHA = 0.55f
    const val INNER_STROKE = 1f

    // Corner arcs with diamonds
    val ARC_RADII = listOf(70f, 52f)
    const val ARC_ALPHA = 0.7f
    const val ARC_STROKE = 1.2f
    const val DIAMOND_OFFSET = 24f
    const val DIAMOND = 10f
    const val DIAMOND_STROKE = 1.2f
    val CORNERS = listOf(
        CardBackCorner(28f, 28f, 1, 1),
        CardBackCorner(722f, 28f, -1, 1),
        CardBackCorner(28f, 1022f, 1, -1),
        CardBackCorner(722f, 1022f, -1, -1),
    )

    // ✦ marks at the edges' midpoints (text baseline = y + 6)
    const val MARK = "✦"
    val MARKS = listOf(375f to 26f, 375f to 1030f, 26f to 525f, 724f to 525f)
    const val MARK_SIZE = 18f
    const val MARK_DY = 6f

    // The ring round the logo: 36 dots and 48 ticks
    const val RING_X = 375f
    const val RING_Y = 440f
    const val DOTS = 36
    const val DOT_RING = 150f
    const val DOT_SMALL = 1.3f
    const val DOT_BIG = 2.4f
    const val DOT_ALPHA = 0.55f
    const val TICKS = 48
    const val TICK_INNER = 118f
    const val TICK_OUTER = 134f
    const val TICK_ALPHA = 0.28f
    const val TICK_STROKE = 1f

    // Halo layout: the grade-colour glow, 栞, the series and the number line
    const val HALO_X = 375f
    const val HALO_Y = 460f
    const val HALO_RADIUS = 260f * LOGO_SCALE
    val HALO_STOPS = listOf(0f to 0.32f, 0.5f to 0.08f, 1f to 0f)
    const val LOGO = "栞"
    const val LOGO_SIZE = 120f * LOGO_SCALE
    const val LOGO_ALPHA = 0.95f
    const val SERIES_BASELINE = 950f
    const val SERIES_SIZE = 9f * TITLE_SCALE
    const val SERIES_MIN_SIZE = 7f
    const val SERIES_TRACKING = 3.4f
    const val SERIES_ALPHA = 0.5f

    /** The series line shrinks only when wider than this (inside the 694 px inner frame). */
    const val SERIES_MAX_WIDTH = 640f
    const val NUMBER_BASELINE = 970f
    const val NUMBER_SIZE = 8f
    const val NUMBER_TRACKING = 3f
    const val NUMBER_ALPHA = 0.3f

    /** A soft elliptical glow (Mythic aurora, Shiori nebula). */
    data class Glow(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val color: Long, val alpha: Float)

    val AURORA = listOf(
        Glow(120f, 180f, 380f, 260f, 0xFF5FFFE0, 0.18f),
        Glow(660f, 880f, 400f, 300f, 0xFFFF6CD0, 0.16f),
        Glow(560f, 300f, 300f, 240f, 0xFF8A7CFF, 0.2f),
    )
    val NEBULA = Glow(560f, 250f, 360f, 280f, 0xFFF2A7C8, 0.2f)
    const val STARS = 70
    const val STAR_SEED = 3L

    /** The grade palettes (`G` in the board). */
    fun palette(grade: CardGrade): CardBackPalette = when (grade) {
        CardGrade.COMMON -> CardBackPalette(
            a = 0xFFA9AFB8,
            b = 0xFF5E646C,
            c = 0xFFE6E9ED,
            bg1 = 0xFF0B0C0E,
            bg2 = 0xFF1A1C20,
            ink = 0xFFE3E5E9,
            k = 0.55f,
            metal = listOf(0xFFE6E9ED, 0xFFA9AFB8, 0xFF5E646C),
        )
        CardGrade.UNCOMMON -> CardBackPalette(
            a = 0xFF62CDB8,
            b = 0xFF1F8574,
            c = 0xFFD7FFF6,
            bg1 = 0xFF03100D,
            bg2 = 0xFF0A231E,
            ink = 0xFFD5F3EC,
            k = 0.72f,
            metal = listOf(0xFFD7FFF6, 0xFF62CDB8, 0xFF1F8574),
        )
        CardGrade.RARE -> CardBackPalette(
            a = 0xFFAFC4E0,
            b = 0xFF5F7898,
            c = 0xFFF4F8FF,
            bg1 = 0xFF070A10,
            bg2 = 0xFF141C2B,
            ink = 0xFFE2EAF6,
            k = 0.85f,
            metal = listOf(0xFFF4F8FF, 0xFFAFC4E0, 0xFF4F6A92),
        )
        CardGrade.EPIC -> CardBackPalette(
            a = 0xFFB88CF2,
            b = 0xFF7A3FB8,
            c = 0xFFF2E4FF,
            bg1 = 0xFF0D0817,
            bg2 = 0xFF21123A,
            ink = 0xFFE9DCFA,
            k = 1f,
            metal = listOf(0xFFF2E4FF, 0xFFB88CF2, 0xFFC04FB0),
        )
        CardGrade.LEGENDARY -> CardBackPalette(
            a = 0xFFE9C46A,
            b = 0xFFA8741E,
            c = 0xFFFFF1C2,
            bg1 = 0xFF120D05,
            bg2 = 0xFF2C1F0A,
            ink = 0xFFF4E7C5,
            k = 1f,
            metal = listOf(0xFFFFF1C2, 0xFFE9C46A, 0xFFA8741E),
        )
        CardGrade.MYTHIC -> CardBackPalette(
            a = 0xFF9BE7E0, b = 0xFF7B6CFF, c = 0xFFFFD1F2, bg1 = 0xFF040406, bg2 = 0xFF0E0E15, ink = 0xFFE8F2F5,
            k = 1f, metal = listOf(0xFF5FFFE0, 0xFF8A7CFF, 0xFFFF6CD0), aurora = true,
        )
        CardGrade.SECRET -> CardBackPalette(
            a = 0xFFF6E7C8, b = 0xFFF2A7C8, c = 0xFFFFFFFF, bg1 = 0xFF070A1E, bg2 = 0xFF1A1A44, ink = 0xFFFBF4EA,
            k = 1f, metal = listOf(0xFFFFFFFF, 0xFFF6E0A8, 0xFFF2A7C8, 0xFF9AA8FF), stars = true,
        )
    }

    /** The pattern group's opacity: the grade's strength × the chosen pattern strength (normal, 1). */
    fun patternAlpha(grade: CardGrade): Float = palette(grade).k * minOf(PATTERN_STRENGTH, 1f)

    /** "THE GREATEST ESTATE DEVELOPER". */
    fun seriesText(series: String): String = series.trim().uppercase()

    /** "№ 0013 · RANK A"; just "RANK A" without a serial. */
    fun numberText(serial: Long?, grade: CardGrade): String {
        val rank = grade.label.uppercase()
        return CardSpec.serialDigits(serial)?.let { "№ $it · $rank" } ?: rank
    }

    /** Ring dot [i] (0 until [DOTS]): every third one big. */
    fun dot(i: Int): CardBackDot {
        val t = i * PI / 18
        return CardBackDot(
            (RING_X + DOT_RING * cos(t)).toFloat(),
            (RING_Y + DOT_RING * sin(t)).toFloat(),
            if (i % 3 != 0) DOT_SMALL else DOT_BIG,
            DOT_ALPHA,
        )
    }

    /** Tick [i] (0 until [TICKS]) as x1, y1, x2, y2. */
    fun tick(i: Int): List<Float> {
        val t = i * PI / 24
        return listOf(
            (RING_X + TICK_INNER * cos(t)).toFloat(),
            (RING_Y + TICK_INNER * sin(t)).toFloat(),
            (RING_X + TICK_OUTER * cos(t)).toFloat(),
            (RING_Y + TICK_OUTER * sin(t)).toFloat(),
        )
    }

    /** The board's seeded generator (Park–Miller): the same sky on every platform. */
    class Rng(private var seed: Long) {
        fun next(): Double {
            seed = seed * 16807 % 2147483647
            return (seed - 1).toDouble() / 2147483646
        }
    }

    /** The SR sky: [STARS] white dots from seed [STAR_SEED]. */
    val STAR_FIELD: List<CardBackDot> by lazy {
        val r = Rng(STAR_SEED)
        List(STARS) {
            val z = r.next()
            val x = r.next() * WIDTH
            val y = r.next() * HEIGHT
            CardBackDot(x.toFloat(), y.toFloat(), (0.5 + z * 1.3).toFloat(), (0.2 + z * 0.6).toFloat())
        }
    }

    /**
     * The largest size from [max] down to [min] (0.25 px steps) at which [width] fits [available]; [min] when none
     * (the caller then ellipsizes).
     */
    fun fit(max: Float, min: Float, available: Float, width: (Float) -> Float): Float {
        var size = max
        while (size > min && width(size) > available) size -= 0.25f
        return size.coerceAtLeast(min)
    }

    /** The album thumbnail's glow (CARD_SPEC §13): opacity and radius by grade. */
    fun thumbGlow(grade: CardGrade): Pair<Float, Float> = when {
        grade >= CardGrade.LEGENDARY -> 0.75f to 12f
        grade >= CardGrade.RARE -> 0.55f to 8f
        else -> 0.25f to 4f
    }
}
