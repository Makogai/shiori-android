package mihon.feature.friends.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReadingStreakStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val thisWeekChapters: Int,
    val thisWeekSeconds: Long,
    val lastWeekChapters: Int,
    val lastWeekSeconds: Long,
    /** Chapters per day for the last seven days, oldest first, ending today. */
    val lastSevenDays: List<Int>,
) {
    companion object {

        /** [reads] holds each history entry's last-read time and total read duration in milliseconds. */
        fun compute(reads: List<Pair<Instant, Long>>, now: Instant, zone: ZoneId): ReadingStreakStats {
            val today = LeaguePeriods.localDate(now, zone)
            val byDay = reads
                .filter { it.first.toEpochMilli() > 0 }
                .groupBy({ LeaguePeriods.localDate(it.first, zone) }, { it.second })
            val days = byDay.keys
            val thisWeek = LeaguePeriods.weekStart(today)
            val lastWeek = thisWeek.minusWeeks(1)

            fun inRange(start: LocalDate, end: LocalDate) = byDay.filterKeys { it >= start && it < end }.values

            val thisWeekReads = inRange(thisWeek, thisWeek.plusWeeks(1))
            val lastWeekReads = inRange(lastWeek, thisWeek)
            return ReadingStreakStats(
                currentStreak = Streaks.current(days, today),
                bestStreak = Streaks.best(days),
                thisWeekChapters = thisWeekReads.sumOf { it.size },
                thisWeekSeconds = thisWeekReads.sumOf { list -> list.sumOf { it / 1000 } },
                lastWeekChapters = lastWeekReads.sumOf { it.size },
                lastWeekSeconds = lastWeekReads.sumOf { list -> list.sumOf { it / 1000 } },
                lastSevenDays = (6 downTo 0).map { byDay[today.minusDays(it.toLong())]?.size ?: 0 },
            )
        }
    }
}
