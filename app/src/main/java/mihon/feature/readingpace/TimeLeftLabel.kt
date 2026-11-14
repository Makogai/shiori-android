package mihon.feature.readingpace

import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** "~6 min left", "<1 min left", "~1 h 10 min left" for [pages] more pages at [pace]; null without pages. */
@Composable
fun timeLeftLabel(pace: ReadingPace, pages: Int): String? {
    if (pages <= 0) return null
    val minutes = pace.minutesFor(pages)
    return when {
        minutes < 1 -> stringResource(MR.strings.reading_left_under_minute)
        minutes < 60 -> stringResource(MR.strings.reading_left_minutes, minutes)
        minutes % 60 == 0 -> stringResource(MR.strings.reading_left_hours, minutes / 60)
        else -> stringResource(MR.strings.reading_left_hours_minutes, minutes / 60, minutes % 60)
    }
}
