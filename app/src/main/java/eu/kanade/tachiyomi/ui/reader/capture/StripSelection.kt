package eu.kanade.tachiyomi.ui.reader.capture

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A point on the strip: a page, and how far down it (0 is its top, 1 its bottom). Unlike a y offset it survives
 * pages changing height as their images load, and carries over between layouts of different widths.
 */
data class StripAnchor(val page: Int, val fraction: Float) : Comparable<StripAnchor> {
    override fun compareTo(other: StripAnchor): Int =
        if (page == other.page) fraction.compareTo(other.fraction) else page.compareTo(other.page)
}

/**
 * The vertical stretch of the strip a long screenshot covers.
 */
data class StripSelection(val top: StripAnchor, val bottom: StripAnchor) {

    /** The pages it touches, top to bottom. A bottom right at a page's top does not take that page. */
    val pages: IntRange
        get() {
            val last = if (bottom.fraction <= 0f && bottom.page > top.page) bottom.page - 1 else bottom.page
            return top.page..max(top.page, last)
        }

    /** The part of [page] it covers, as fractions of the page's height. */
    fun span(page: Int): ClosedFloatingPointRange<Float> {
        val from = if (page == top.page) top.fraction else 0f
        val to = if (page == bottom.page) bottom.fraction else 1f
        return min(from, to)..max(from, to)
    }

    companion object {
        /** All of [page]. */
        fun whole(page: Int) = StripSelection(StripAnchor(page, 0f), StripAnchor(page, 1f))
    }
}

/**
 * Pages stacked top to bottom with [gap] between them: converts y offsets to anchors and back.
 */
data class StripLayout(val heights: List<Float>, val gap: Float = 0f) {

    val totalHeight: Float
        get() = heights.sum() + gap * max(heights.size - 1, 0)

    /** Where [page] starts. */
    fun top(page: Int): Float {
        val count = page.coerceIn(0, heights.size)
        return heights.subList(0, count).sum() + gap * count
    }

    fun y(anchor: StripAnchor): Float {
        if (anchor.page !in heights.indices) return if (anchor.page < 0) 0f else totalHeight
        return top(anchor.page) + heights[anchor.page] * anchor.fraction.coerceIn(0f, 1f)
    }

    /** The anchor at [y], clamped to the strip; a y in a gap belongs to the next page's top. */
    fun anchor(y: Float): StripAnchor {
        if (heights.isEmpty()) return StripAnchor(0, 0f)
        var start = 0f
        heights.forEachIndexed { page, height ->
            if (y < start + height || page == heights.lastIndex) {
                val fraction = if (height > 0) (y - start) / height else 0f
                return StripAnchor(page, fraction.coerceIn(0f, 1f))
            }
            start += height
            if (y < start + gap) return StripAnchor(page + 1, 0f)
            start += gap
        }
        return StripAnchor(heights.lastIndex, 1f)
    }

    /** [selection]'s top and bottom edges. */
    fun range(selection: StripSelection): ClosedFloatingPointRange<Float> {
        val top = y(selection.top)
        return top..max(top, y(selection.bottom))
    }

    /** The selection from [top] to [bottom] (clamped and ordered), at least [minHeight] tall when the strip allows. */
    fun selection(top: Float, bottom: Float, minHeight: Float = 0f): StripSelection {
        val total = totalHeight
        var upper = min(top, bottom).coerceIn(0f, total)
        var lower = max(top, bottom).coerceIn(0f, total)
        if (lower - upper < minHeight) {
            lower = min(upper + minHeight, total)
            upper = max(lower - minHeight, 0f)
        }
        return StripSelection(anchor(upper), anchor(lower))
    }
}

/** A size in pixels. */
data class PixelSize(val width: Int, val height: Int)

/** A rectangle in pixels. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val bottom: Int
        get() = top + height
}

/**
 * How the selected parts of the page images go into one tall image: each page's crop, in its own pixels, scaled
 * to a common width. The width is the widest page's, at most [MAX_WIDTH]; past the maximum height the image stops,
 * which [truncatedAt] marks so the screen can say so.
 */
data class StitchPlan(
    val width: Int,
    val height: Int,
    val slices: List<Slice>,
    val truncatedAt: StripAnchor?,
) {
    /** [crop] is in the page image's pixels, [destination] in the stitched image's. */
    data class Slice(val page: Int, val crop: PixelRect, val destination: PixelRect)

    val truncated: Boolean
        get() = truncatedAt != null

    companion object {
        const val MAX_WIDTH = 1440

        /** About 80 MB of pixels at the full width: enough for a long scene, safe for memory. */
        const val MAX_HEIGHT = 14400

        /**
         * The plan for [selection] over pages of the given pixel sizes (null for a page whose size is unknown yet,
         * which is left out). Null when nothing would be drawn.
         */
        fun make(
            selection: StripSelection,
            pixelSizes: List<PixelSize?>,
            maxWidth: Int = MAX_WIDTH,
            maxHeight: Int = MAX_HEIGHT,
        ): StitchPlan? {
            val sizes = selection.pages
                .mapNotNull { page -> pixelSizes.getOrNull(page)?.let { page to it } }
                .filter { (_, size) -> size.width > 0 && size.height > 0 }
            val widest = sizes.maxOfOrNull { it.second.width } ?: return null
            val width = min(widest, maxWidth)
            val slices = mutableListOf<Slice>()
            var bottom = 0f
            var truncatedAt: StripAnchor? = null
            for ((page, size) in sizes) {
                val span = selection.span(page)
                var cropHeight = (span.endInclusive - span.start) * size.height
                if (cropHeight < 0.5f) continue
                val scale = width.toFloat() / size.width
                var drawn = cropHeight * scale
                if (bottom + drawn > maxHeight) {
                    drawn = maxHeight - bottom
                    cropHeight = drawn / scale
                    truncatedAt = StripAnchor(page, span.start + cropHeight / size.height)
                }
                val top = bottom.roundToInt()
                bottom += drawn
                val cropTop = (span.start * size.height).roundToInt()
                slices += Slice(
                    page = page,
                    crop = PixelRect(
                        left = 0,
                        top = cropTop,
                        width = size.width,
                        height = cropHeight.roundToInt().coerceIn(1, size.height - cropTop),
                    ),
                    destination = PixelRect(0, top, width, bottom.roundToInt() - top),
                )
                if (truncatedAt != null) break
            }
            val height = bottom.roundToInt()
            if (height < 1 || slices.isEmpty()) return null
            return StitchPlan(width, height, slices, truncatedAt)
        }
    }
}

/**
 * The part of the strip on screen: the screen's top is [frameTop] above the current page's top (negative when the
 * page starts above the screen), and the screen is [viewport] tall.
 */
fun StripLayout.visibleSelection(current: Int, frameTop: Float, viewport: Float): StripSelection {
    val top = top(current) - frameTop
    return selection(top, top + viewport)
}

/**
 * Pixels per frame the long screenshot scrolls while a handle is held: up to [max] within the top fifth or bottom
 * quarter of the screen (under the bars), faster closer to the edge.
 */
fun autoscrollSpeed(fingerY: Float, viewport: Float, max: Float): Float {
    if (viewport <= 0f) return 0f
    val top = viewport * 0.2f
    val bottom = viewport * 0.75f
    return when {
        fingerY < top -> -max * min((top - fingerY) / top, 1f)
        fingerY > bottom -> max * min((fingerY - bottom) / (viewport - bottom), 1f)
        else -> 0f
    }
}

/**
 * The largest power of two that shrinks a crop [cropWidth] wide to no narrower than [targetWidth]: decoding at it saves memory without
 * losing pixels the stitched image keeps.
 */
fun sampleSizeFor(cropWidth: Int, targetWidth: Int): Int {
    var sample = 1
    while (cropWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
