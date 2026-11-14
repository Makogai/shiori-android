package mihon.feature.habits

import mihon.feature.friends.domain.Streaks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Finished chapters by local calendar day. A chapter has one history entry, so it counts once: on the day it
 * was last read, which the reader sets when its last page is reached.
 */
object ReadingDays {

    fun countByDay(readTimes: List<Instant>, zone: ZoneId): Map<LocalDate, Int> =
        readTimes.groupingBy { it.atZone(zone).toLocalDate() }.eachCount()

    fun countOn(readTimes: List<Instant>, day: LocalDate, zone: ZoneId): Int =
        readTimes.count { it.atZone(zone).toLocalDate() == day }

    /** Consecutive days with at least one chapter, ending today, or yesterday while today has none yet. */
    fun streak(counts: Map<LocalDate, Int>, today: LocalDate): Int = Streaks.current(counts.keys, today)

    /** Consecutive days that met [goal], ending today, or yesterday while today's goal is still open. */
    fun goalStreak(counts: Map<LocalDate, Int>, goal: Int, today: LocalDate): Int =
        Streaks.current(counts.filterValues { it >= goal }.keys, today)

    fun longestStreak(days: Set<LocalDate>): Int = Streaks.best(days)

    /** Chapters still needed today to reach [goal], or null without a goal. */
    fun goalLeft(today: Int, goal: Int?): Int? = goal?.let { (it - today).coerceAtLeast(0) }
}

/** Active reader time per day, kept as "2026-11-14=1234;2026-11-13=50" (seconds) for the last [KEEP_DAYS]. */
object ReadingSeconds {

    const val KEEP_DAYS = 400L

    fun decode(value: String): Map<LocalDate, Long> = value.split(';').mapNotNull { entry ->
        val (day, seconds) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { LocalDate.parse(day) to seconds.toLong() }.getOrNull()
    }
        .toMap()

    fun add(value: String, day: LocalDate, seconds: Long): String {
        val days = decode(value).toMutableMap()
        days[day] = (days[day] ?: 0L) + seconds
        val oldest = day.minusDays(KEEP_DAYS)
        return days.filterKeys { it > oldest }.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}

/**
 * Active time from interaction pings: the gap since the previous ping counts up to [idleLimitMillis], so a
 * reader left open stops counting after about two minutes.
 */
class ActiveTimeCounter(private val idleLimitMillis: Long = 120_000L) {

    private var last: Long? = null

    /** The active milliseconds since the previous ping, 0 for the first one. */
    fun ping(nowMillis: Long): Long {
        val previous = last
        last = nowMillis
        return if (previous == null) 0 else (nowMillis - previous).coerceIn(0, idleLimitMillis)
    }

    /** Ends the session (the reader went to the background). */
    fun stop(nowMillis: Long): Long = ping(nowMillis).also { last = null }
}
