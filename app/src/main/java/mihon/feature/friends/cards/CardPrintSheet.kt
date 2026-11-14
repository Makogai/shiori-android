package mihon.feature.friends.cards

import kotlin.math.roundToInt

/**
 * The print sheet (CARD_SPEC §15 "Exports"): front left, back right, each at the poker-card trim size at 300 dpi,
 * with crop marks and a caption, on one white page. Numbers in 300-dpi pixels unless said otherwise.
 */
object CardPrintSheet {

    const val DPI = 300
    const val TRIM_MM_W = 63.0
    const val TRIM_MM_H = 88.0

    /** 63 mm and 88 mm at 300 dpi, rounded: 744 × 1039. */
    val TRIM_W: Int = mmToPx(TRIM_MM_W)
    val TRIM_H: Int = mmToPx(TRIM_MM_H)

    /** 10 mm. */
    const val MARGIN = 118
    const val GUTTER = 118

    val PAGE_W: Int = MARGIN * 2 + TRIM_W * 2 + GUTTER
    val PAGE_H: Int = MARGIN * 2 + TRIM_H

    /** 1 pt = 300 / 72 px. */
    const val PX_PER_PT = DPI / 72.0
    val PAGE_W_PT: Double = PAGE_W / PX_PER_PT
    val PAGE_H_PT: Double = PAGE_H / PX_PER_PT

    val FRONT = CardBox(MARGIN.toFloat(), MARGIN.toFloat(), TRIM_W.toFloat(), TRIM_H.toFloat())
    val BACK = CardBox((MARGIN + TRIM_W + GUTTER).toFloat(), MARGIN.toFloat(), TRIM_W.toFloat(), TRIM_H.toFloat())

    /** Crop marks: 2 px black hairlines, starting 24 px (2 mm) outside the trim, 59 px (5 mm) long. */
    const val MARK_WIDTH = 2f
    const val MARK_OFFSET = 24f
    const val MARK_LENGTH = 59f

    const val CAPTION_SIZE = 24f
    const val CAPTION_BASELINE = 1264f
    const val CAPTION_COLOR = 0xFF6B7280L

    fun mmToPx(mm: Double): Int = (mm / 25.4 * DPI).roundToInt()

    /** A 1500 × 2100 card image's scale into its trim box (x 0.4960, y 0.4948). */
    fun scale(cardWidth: Float = CardSpec.WIDTH, cardHeight: Float = CardSpec.HEIGHT): Pair<Float, Float> =
        TRIM_W / cardWidth to TRIM_H / cardHeight

    /**
     * Where a 5:7 card goes in [trim] (63 × 88 mm is not quite 5:7): scaled uniformly to fit, centred, never
     * stretched (742.1 × 1039 in the 744 × 1039 trim).
     */
    fun fit(trim: CardBox): CardBox {
        val k = minOf(trim.width / CardSpec.WIDTH, trim.height / CardSpec.HEIGHT)
        val w = CardSpec.WIDTH * k
        val h = CardSpec.HEIGHT * k
        return CardBox(trim.centerX - w / 2, trim.centerY - h / 2, w, h)
    }

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /** The eight crop marks of [trim]: two per corner along the extensions of the trim's edges. */
    fun cropMarks(trim: CardBox): List<Line> {
        val near = MARK_OFFSET
        val far = MARK_OFFSET + MARK_LENGTH
        return buildList {
            for (x in listOf(trim.left, trim.right)) {
                for (y in listOf(trim.top, trim.bottom)) {
                    val dx = if (x == trim.left) -1 else 1
                    val dy = if (y == trim.top) -1 else 1
                    // Horizontal: the top/bottom edge extended sideways
                    add(Line(x + dx * near, y, x + dx * far, y))
                    // Vertical: the left/right edge extended up/down
                    add(Line(x, y + dy * near, x, y + dy * far))
                }
            }
        }
    }

    val MARKS: List<Line> get() = cropMarks(FRONT) + cropMarks(BACK)

    /** "<name> · No. 0042 · 63 × 88 mm · print at 100 %". */
    fun caption(name: String, serial: String): String = "$name · $serial · 63 × 88 mm · print at 100 %"

    fun fileName(name: String, serial: String): String = "$name - $serial - print"
}
