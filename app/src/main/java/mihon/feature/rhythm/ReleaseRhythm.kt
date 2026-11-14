package mihon.feature.rhythm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** How often a series gets chapters, when that is clear from its release dates. As the iOS app's `ReleaseRhythm`. */
sealed interface ReleaseRhythm {
    data class Weekly(val day: DayOfWeek) : ReleaseRhythm

    data class EveryNDays(val days: Int) : ReleaseRhythm
}

object ReleaseRhythmLogic {
    /** Fewer distinct release days than this tell nothing. */
    const val MINIMUM_RELEASES = 4

    /** Only the latest releases count: a series' pace changes over the years. */
    const val WINDOW = 12

    /** Share of gaps that must be close to the usual one. */
    const val STEADY_SHARE = 0.6

    /**
     * The rhythm of releases on [dates] (epoch millis: upload dates, or fetch dates when a source gives none),
     * or null when there are too few, they are irregular or the series looks to be on hiatus at [now].
     */
    fun infer(dates: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReleaseRhythm? {
        // Chapters out on the same day are one release
        val days = dates.filter { it > 0 }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()
            .takeLast(WINDOW)
        if (days.size < MINIMUM_RELEASES) return null
        val intervals = days.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later).toDouble() }
        val usual = median(intervals)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val since = ChronoUnit.DAYS.between(days.last(), today)
        if (since > 3 * usual.coerceAtLeast(1.0) && since > 7) return null
        val tolerance = (usual * 0.25).coerceAtLeast(1.0)
        val steady = intervals.count { abs(it - usual) <= tolerance }.toDouble() / intervals.size
        if (steady < STEADY_SHARE) return null
        if (usual in 6.0..8.0) {
            weeklyDay(days)?.let { return ReleaseRhythm.Weekly(it) }
        }
        return ReleaseRhythm.EveryNDays(usual.roundToInt().coerceAtLeast(1))
    }

    private fun weeklyDay(days: List<LocalDate>): DayOfWeek? {
        val (day, count) = days.groupingBy { it.dayOfWeek }.eachCount().maxBy { it.value }
        return day.takeIf { count.toDouble() / days.size >= STEADY_SHARE }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}
