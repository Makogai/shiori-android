package mihon.feature.friends.cards

import kotlin.math.abs
import kotlin.math.max

/**
 * Shiori Cards v1.2 geometry (BRIEF §2–4, §10): one renderer-independent table shared with iOS. Master pixels on the
 * 1500 × 2100 trim (5:7); every output scales them uniformly by outputWidth / 1500. Rectangles are top-left based.
 */
object CardSpec {

    const val WIDTH = 1500f
    const val HEIGHT = 2100f
    const val ASPECT = 5f / 7f

    /** The digital preview's corner radius: 36 px at master size, 2.4 % of the width. Same mask for every rank. */
    const val PREVIEW_RADIUS = 36f
    const val RADIUS_SHARE = PREVIEW_RADIUS / WIDTH

    /** The print fraction ("01/10"). Left empty for now (DECISIONS): its region stays clear. */
    val PRINT = CardBox(75f, 84f, 315f, 105f)
    const val PRINT_BASELINE = 161f

    /** Where the card number used to be ("No. 0003"); the front no longer draws it (the back does). */
    val CARD_ID = CardBox(75f, 1953f, 420f, 84f)
    const val CARD_ID_BASELINE = 2012f

    /** Ornaments, shine and holo keep out of these. */
    val PRINT_EXCLUSION = CardBox(60f, 63f, 360f, 147f)
    val CARD_ID_EXCLUSION = CardBox(60f, 1932f, 465f, 126f)
    val EXCLUSIONS: List<CardBox> get() = listOf(PRINT_EXCLUSION, CARD_ID_EXCLUSION)

    /** An image within 1 % of 5:7 is Full Art and fills the card; anything else is a legacy image. */
    const val ASPECT_TOLERANCE = 0.01f

    /** The bundled support font (SIL OFL, licence beside it): the card back's small lines. */
    const val SUPPORT_FONT = "fonts/Inter-Regular.ttf"

    fun scale(outputWidth: Float): Float = outputWidth / WIDTH

    /** 5:7 height for [width]; whole when [width] is a multiple of 5. */
    fun heightFor(width: Int): Int = Math.round(width * 7f / 5f)

    fun cornerRadius(width: Float): Float = width * RADIUS_SHARE

    /** "0042": at least four digits, more as they are; null without a serial (nothing is drawn). */
    fun serialDigits(serial: Long?): String? = serial?.takeIf { it > 0 }?.toString()?.padStart(4, '0')

    /** "No. 0042", null without a serial. */
    fun cardIdText(serial: Long?): String? = serialDigits(serial)?.let { "No. $it" }

    /**
     * An edition as "01/10", "001/100": both padded to max(2, digits of the total). Null when either is missing or
     * the pair is invalid (1 ≤ index ≤ total).
     */
    fun printText(index: Int?, total: Int?): String? {
        if (index == null || total == null || index < 1 || index > total) return null
        val width = max(2, total.toString().length)
        return index.toString().padStart(width, '0') + "/" + total.toString().padStart(width, '0')
    }

    /** An image is Full Art (5:7) within [ASPECT_TOLERANCE]. */
    fun isFullArt(imageWidth: Float, imageHeight: Float): Boolean {
        if (imageWidth <= 0f || imageHeight <= 0f) return false
        return abs(imageWidth / imageHeight - ASPECT) <= ASPECT * ASPECT_TOLERANCE
    }

    /**
     * Where a card image goes on the canvas: Full Art fills it; a legacy image (usually 2:3) is aspect-fitted and
     * centred, never stretched (the caller fills the rest with a blurred, darkened copy).
     */
    fun artRect(
        imageWidth: Float,
        imageHeight: Float,
        canvasWidth: Float = WIDTH,
        canvasHeight: Float = HEIGHT,
    ): CardBox {
        if (imageWidth <= 0f || imageHeight <= 0f || isFullArt(imageWidth, imageHeight)) {
            return CardBox(0f, 0f, canvasWidth, canvasHeight)
        }
        val k = minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
        val w = imageWidth * k
        val h = imageHeight * k
        return CardBox((canvasWidth - w) / 2, (canvasHeight - h) / 2, w, h)
    }

    /** A legacy image needs the blurred backdrop: it doesn't cover the card. */
    fun needsBackdrop(imageWidth: Float, imageHeight: Float): Boolean = !isFullArt(imageWidth, imageHeight)
}
