package mihon.feature.autoscroll

/** The paged reader's auto page-turn intervals, in seconds a page. */
object AutoPageInterval {
    val seconds = 2..30
    const val DEFAULT = 8

    /** The series' own interval when it has one, else [default]. */
    fun forSeries(intervals: Map<Long, Int>, mangaId: Long?, default: Int): Int =
        (mangaId?.let { intervals[it] } ?: default).coerceIn(seconds)

    /** Stored like [AutoScrollSpeed.serialize]: "12=8,40=15". */
    fun deserialize(text: String): Map<Long, Int> = AutoScrollSpeed.deserialize(text, seconds)
}
