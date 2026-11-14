package mihon.feature.readingpace

import tachiyomi.data.readingpace.PaceSample
import kotlin.math.roundToInt

/**
 * The user's own pace in seconds a page: the median over their most recent chapters, so a chapter left open or
 * skipped through barely moves it.
 */
@JvmInline
value class ReadingPace(val secondsPerPage: Double) {

    /** Whole minutes to read [pages] more pages. */
    fun minutesFor(pages: Int): Int = (pages.coerceAtLeast(0) * secondsPerPage / 60).roundToInt()

    companion object {
        const val RECENT_CHAPTERS = 30
        private const val MIN_SAMPLES = 3
        private val PLAUSIBLE_PER_PAGE = 0.5..120.0

        /**
         * The pace from [samples], newest first, or null while there are too few; a chapter counts up to the page it
         * was left at.
         */
        fun of(samples: List<PaceSample>): ReadingPace? {
            val rates = samples.take(RECENT_CHAPTERS).mapNotNull { sample ->
                if (sample.lastPageRead < 1 || sample.readDurationMs <= 0) return@mapNotNull null
                (sample.readDurationMs / 1000.0 / (sample.lastPageRead + 1)).takeIf { it in PLAUSIBLE_PER_PAGE }
            }
            if (rates.size < MIN_SAMPLES) return null
            return ReadingPace(median(rates))
        }

        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
        }
    }
}
