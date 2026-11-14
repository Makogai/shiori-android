package eu.kanade.tachiyomi.ui.reader.capture

import android.graphics.Bitmap
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * What a reader screenshot is labelled with. [pages] is ready to show, like "Pages 5–7 of 40".
 */
data class ShotInfo(
    val mangaId: Long?,
    val chapterId: Long?,
    val title: String,
    val chapter: String,
    val pages: String,
    val source: String,
)

/**
 * How the label sits on a styled screenshot.
 */
enum class ShotLayout {
    FOOTER,
    CHIP,
    NONE,
}

/**
 * Takes a finished screenshot further, as a "Make a card" action on the screenshot screens. The screens show the
 * action only when the reader is given one.
 */
fun interface ScreenshotCardMaker {
    fun onMakeCard(bitmap: Bitmap, context: ShotInfo)
}

/**
 * What the long screenshot screen opens with: the chapter's pages (or the one page in paged modes), the stretch
 * selected, and the label for what comes out.
 */
data class LongCaptureRequest(
    val pages: List<ReaderPage>,
    val selection: StripSelection,
    val info: ShotInfo,
)

/**
 * A stitched screenshot waiting to be styled.
 */
class StyledShotRequest(val image: Bitmap, val info: ShotInfo)

/**
 * Up to this many pixels a screenshot is a PNG (crisp text); past it, a JPEG keeps tall screenshots to a size
 * messaging apps accept.
 */
const val PNG_PIXEL_LIMIT = 8_000_000L

fun encodesAsPng(width: Int, height: Int): Boolean = width.toLong() * height <= PNG_PIXEL_LIMIT

/**
 * The first and last page numbers the [slices] of a plan show, given each strip index's [number].
 */
fun shownPages(slices: List<StitchPlan.Slice>, number: (Int) -> Int?): IntRange? {
    val numbers = slices.mapNotNull { number(it.page) }
    val first = numbers.minOrNull() ?: return null
    return first..(numbers.maxOrNull() ?: first)
}

/**
 * Where a stitched screenshot goes from the long screenshot screen.
 */
enum class CaptureOutput {
    SHARE,
    SAVE,
    STYLE,
    CARD,
}
