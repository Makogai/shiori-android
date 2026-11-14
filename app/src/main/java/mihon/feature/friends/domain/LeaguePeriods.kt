package mihon.feature.friends.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

enum class LeaguePeriod { TODAY, WEEK, MONTH, ALL }

/** Half-open range `[from, to)`. */
data class TimeRange(val from: Instant, val to: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= from && instant < to
}

object LeaguePeriods {

    val ALL_TIME_START: Instant = LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()

    fun zone(timezone: String?): ZoneId =
        timezone?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

    fun range(period: LeaguePeriod, now: Instant, zone: ZoneId): TimeRange {
        val today = now.atZone(zone).toLocalDate()
        return when (period) {
            LeaguePeriod.TODAY -> dayRange(today, zone)
            LeaguePeriod.WEEK -> weekRange(weekStart(today), zone)
            LeaguePeriod.MONTH -> monthRange(monthStart(today), zone)
            LeaguePeriod.ALL -> TimeRange(ALL_TIME_START, now.plusSeconds(86_400))
        }
    }

    fun dayRange(day: LocalDate, zone: ZoneId) = TimeRange(start(day, zone), start(day.plusDays(1), zone))

    fun weekRange(monday: LocalDate, zone: ZoneId) = TimeRange(start(monday, zone), start(monday.plusWeeks(1), zone))

    fun monthRange(first: LocalDate, zone: ZoneId) = TimeRange(start(first, zone), start(first.plusMonths(1), zone))

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun monthStart(day: LocalDate): LocalDate = day.withDayOfMonth(1)

    fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    // atStartOfDay picks the first valid instant when a DST gap swallows midnight.
    private fun start(day: LocalDate, zone: ZoneId): Instant = day.atStartOfDay(zone).toInstant()
}
