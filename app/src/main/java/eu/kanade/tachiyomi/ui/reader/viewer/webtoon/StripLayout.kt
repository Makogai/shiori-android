package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/** The long strip's page gap and its side padding when shown wide. */
object StripLayout {

    /** The page gap's choices, in dp. */
    val gaps = listOf(0, 2, 4, 8, 16, 24)

    /** The wide side padding's choices, in percent; [SAME_AS_SIDE_PADDING] follows the side padding. */
    val widePaddings = listOf(-1, 0, 5, 10, 15, 20, 25, 30, 35)

    const val SAME_AS_SIDE_PADDING = -1

    /** The side padding in percent: the wide one when set and the strip [isWide] (a tablet or landscape). */
    fun sidePadding(sidePadding: Int, widePadding: Int, isWide: Boolean): Int =
        if (widePadding >= 0 && isWide) widePadding else sidePadding
}
