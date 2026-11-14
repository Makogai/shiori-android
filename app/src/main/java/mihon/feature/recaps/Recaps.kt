package mihon.feature.recaps

import androidx.compose.runtime.Immutable
import tachiyomi.data.habits.FinishedRead
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.time.toJavaInstant

enum class RecapKind { Week, Month }

/** An ISO week (Monday to Sunday) or a calendar month, as local days `[start, end)`. */
@Immutable
data class RecapPeriod(val kind: RecapKind, val start: LocalDate) {

    val end: LocalDate
        get() = when (kind) {
            RecapKind.Week -> start.plusWeeks(1)
            RecapKind.Month -> start.plusMonths(1)
        }

    /** Stable across launches: "week-2026-W46", "month-2026-11". */
    val key: String
        get() = when (kind) {
            RecapKind.Week -> "week-%04d-W%02d".format(
                start.get(IsoFields.WEEK_BASED_YEAR),
                start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            )
            RecapKind.Month -> "month-%04d-%02d".format(start.year, start.monthValue)
        }

    operator fun contains(day: LocalDate) = day >= start && day < end

    fun previous() = when (kind) {
        RecapKind.Week -> RecapPeriod(kind, start.minusWeeks(1))
        RecapKind.Month -> RecapPeriod(kind, start.minusMonths(1))
    }

    fun days(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()

    companion object {
        fun containing(kind: RecapKind, day: LocalDate) = RecapPeriod(
            kind,
            when (kind) {
                RecapKind.Week -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                RecapKind.Month -> day.withDayOfMonth(1)
            },
        )

        fun parse(key: String): RecapPeriod? {
            val parts = key.split('-')
            return runCatching {
                when (parts[0]) {
                    "week" -> {
                        val week = parts[2].removePrefix("W").toLong()
                        val day = LocalDate.of(parts[1].toInt(), 6, 1)
                            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                        containing(RecapKind.Week, day)
                    }
                    "month" -> RecapPeriod(RecapKind.Month, LocalDate.of(parts[1].toInt(), parts[2].toInt(), 1))
                    else -> null
                }
            }
                .getOrNull()
        }
    }
}

@Immutable
data class RecapTotals(val chapters: Int = 0, val readMillis: Long = 0, val series: Int = 0)

@Immutable
data class RecapSeries(val mangaId: Long, val title: String, val chapters: Int)

@Immutable
data class Recap(
    val period: RecapPeriod,
    val totals: RecapTotals,
    val previous: RecapTotals,
    /** Most chapters first; at most five. */
    val topSeries: List<RecapSeries>,
    /** Series first read in this period, most chapters first. */
    val newSeries: List<RecapSeries>,
    /** Chapters on every day of the period, first to last. */
    val days: List<Pair<LocalDate, Int>>,
    val longestStreak: Int,
    val inProgress: Boolean,
) {
    /** The day with the most chapters, the earlier on a tie. */
    val busiestDay: Pair<LocalDate, Int>?
        get() = days.filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<LocalDate, Int>> { it.second }.thenByDescending { it.first })

    val chaptersChange: Double? get() = badge(change(totals.chapters.toLong(), previous.chapters.toLong()))

    /** Under a minute either side is noise from history without reading time. */
    val timeChange: Double?
        get() = badge(if (totals.readMillis >= 60_000) change(totals.readMillis, previous.readMillis) else null)

    val seriesChange: Double? get() = badge(change(totals.series.toLong(), previous.series.toLong()))

    /** A period still in progress isn't marked down for not being over yet, so only a rise shows. */
    private fun badge(change: Double?) = change?.takeIf { !inProgress || it > 0 }

    companion object {
        /** The change as a fraction (0.25 is 25% more); null when the previous period had none. */
        fun change(current: Long, previous: Long): Double? =
            if (previous > 0) (current - previous).toDouble() / previous else null

        fun longestStreak(counts: List<Int>): Int {
            var best = 0
            var run = 0
            counts.forEach {
                run = if (it > 0) run + 1 else 0
                best = maxOf(best, run)
            }
            return best
        }
    }
}

/**
 * Reading history indexed for recaps. History keeps one entry per chapter, so a chapter counts in the period it
 * was last read in.
 */
class RecapLibrary(reads: List<FinishedRead>, private val zone: ZoneId) {

    private val entries = reads.map { it to it.readAt.toJavaInstant().atZone(zone).toLocalDate() }
    private val firstRead: Map<Long, LocalDate> = entries.groupBy({ it.first.mangaId }, { it.second })
        .mapValues { (_, days) -> days.min() }

    /** Periods of [kind] with reading, newest first, each with its totals. */
    fun periods(kind: RecapKind): List<Pair<RecapPeriod, RecapTotals>> =
        entries.groupBy { RecapPeriod.containing(kind, it.second) }
            .map { (period, inPeriod) -> period to totals(inPeriod.map { it.first }) }
            .sortedByDescending { it.first.start }

    fun recap(period: RecapPeriod, today: LocalDate): Recap {
        val inPeriod = reads(period)
        val perDay = inPeriod.groupingBy { it.second }.eachCount()
        val days = period.days().map { it to (perDay[it] ?: 0) }
        val top = series(inPeriod.map { it.first })
        return Recap(
            period = period,
            totals = totals(inPeriod.map { it.first }),
            previous = totals(reads(period.previous()).map { it.first }),
            topSeries = top.take(5),
            newSeries = top.filter { firstRead[it.mangaId]?.let { day -> day in period } == true },
            days = days,
            longestStreak = Recap.longestStreak(days.map { it.second }),
            inProgress = today < period.end,
        )
    }

    private fun reads(period: RecapPeriod) = entries.filter { it.second in period }

    private fun totals(reads: List<FinishedRead>) = RecapTotals(
        chapters = reads.size,
        readMillis = reads.sumOf { it.readDurationMs.coerceAtLeast(0) },
        series = reads.distinctBy { it.mangaId }.size,
    )

    private fun series(reads: List<FinishedRead>) = reads.groupBy { it.mangaId }
        .map { (id, list) -> RecapSeries(id, list.first().mangaTitle, list.size) }
        .sortedWith(compareByDescending<RecapSeries> { it.chapters }.thenBy { it.title })
}
