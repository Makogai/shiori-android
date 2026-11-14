package mihon.feature.friends.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

/** A week: an ended challenge lasting at most 7 days, window `[start, end)`. */
data class RecapWeek(
    val challengeId: Int,
    val title: String,
    val emoji: String,
    val start: Instant,
    val end: Instant,
)

/** One player's totals on a board (window or the 7 days before it). */
data class RecapTotals(
    val chapters: Long = 0,
    val seconds: Long = 0,
    val genres: Map<String, Long> = emptyMap(),
    /** First entry of the board's `top_series`. */
    val topSeries: TopRead? = null,
)

data class TopRead(val title: String, val chapters: Long)

/** Everything a recap is computed from. */
data class RecapInput(
    val week: RecapWeek,
    val zone: ZoneId,
    val myId: String,
    val rivalId: String?,
    /** Players shown, in any order. */
    val players: Collection<String>,
    val window: Map<String, RecapTotals>,
    val previous: Map<String, RecapTotals>,
    val daily: List<DayTotal>,
    val scores: Map<String, Double>,
    /** Stakes on this challenge per player. */
    val stakes: Map<String, Int>,
)

sealed interface GenreOwner {
    data class Owned(val userId: String) : GenreOwner
    data object Contested : GenreOwner
    data object Unclaimed : GenreOwner
}

data class GenreChange(val genre: String, val before: GenreOwner, val after: GenreOwner)

data class RecapPlayer(
    val userId: String,
    val chapters: Long,
    val seconds: Long,
    val xp: Long,
    val score: Double,
    /** Best day of the window and its chapters; null when nothing was read. */
    val bestDay: Pair<LocalDate, Long>?,
    val topSeries: TopRead?,
    /** Settled bet (±stake), null without a bet. */
    val bet: Long?,
) {
    val minutes: Long get() = seconds / 60
}

data class WeeklyRecap(
    val week: RecapWeek,
    /** ISO-8601 week number of the start in the Arena time zone. */
    val number: Int,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    /** Most chapters first, then seconds. */
    val players: List<RecapPlayer>,
    /** Null for a tied week. */
    val winner: String?,
    val challengeWinners: Set<String>,
    val genreChanges: List<GenreChange>,
    /** Lead changes between me and my rival over the window; null without a rival. */
    val leadChanges: Int?,
) {
    val tied: Boolean get() = winner == null
    fun player(userId: String): RecapPlayer? = players.firstOrNull { it.userId == userId }
}

object WeeklyRecaps {

    const val MAX_GENRE_CHANGES = 5

    /** Recaps stay on the Arena screen this long after the week ended. */
    val FRESH_FOR: Duration = Duration.ofDays(7)

    /** Data of weeks that ended longer ago than this no longer changes and is cached. */
    val SETTLE_AFTER: Duration = Duration.ofHours(3)

    /** Weeks that have ended by [now], newest first. */
    fun weeks(challenges: List<RecapWeek>, now: Instant): List<RecapWeek> =
        challenges.filter { Bets.isWeekly(it.start, it.end) && it.end <= now }
            .sortedWith(compareByDescending<RecapWeek> { it.end }.thenByDescending { it.challengeId })

    /** The latest week while it ended less than 7 days ago. */
    fun fresh(weeks: List<RecapWeek>, now: Instant): RecapWeek? =
        weeks.maxByOrNull { it.end }?.takeIf { now < it.end.plus(FRESH_FOR) }

    fun weekNumber(start: Instant, zone: ZoneId): Int = start.atZone(zone).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** Window days: from the day of the start to the day before the end. */
    fun days(week: RecapWeek, zone: ZoneId): Pair<LocalDate, LocalDate> {
        val first = LeaguePeriods.localDate(week.start, zone)
        val last = LeaguePeriods.localDate(week.end, zone).minusDays(1)
        return first to maxOf(first, last)
    }

    fun build(input: RecapInput): WeeklyRecap {
        val (first, last) = days(input.week, input.zone)
        val windowDays = input.daily.filter { it.day in first..last }
        val winners = ChallengeRules.winners(input.scores.filterKeys { it in input.players })
        val players = input.players.map { id ->
            val totals = input.window[id] ?: RecapTotals()
            val days = windowDays.filter { it.userId == id }
            val bet = input.stakes[id]?.let { Bets.result(it, id, winners) }
            RecapPlayer(
                userId = id,
                chapters = totals.chapters,
                seconds = totals.seconds,
                xp = Xp.reads(days) + (if (id in winners) Xp.PER_CHALLENGE_WIN else 0) + (bet ?: 0),
                score = input.scores[id] ?: 0.0,
                bestDay = days.filter { it.chapters > 0 }
                    .sortedWith(compareByDescending<DayTotal> { it.chapters }.thenBy { it.day })
                    .firstOrNull()?.let { it.day to it.chapters },
                topSeries = totals.topSeries,
                bet = bet,
            )
        }.sortedWith(
            compareByDescending<RecapPlayer> { it.chapters }.thenByDescending { it.seconds }.thenBy { it.userId },
        )
        val top = players.firstOrNull()
        val runnerUp = players.getOrNull(1)
        val winner = top?.takeIf {
            runnerUp == null || runnerUp.chapters != it.chapters ||
                runnerUp.seconds != it.seconds
        }
        return WeeklyRecap(
            week = input.week,
            number = weekNumber(input.week.start, input.zone),
            firstDay = first,
            lastDay = last,
            players = players,
            winner = winner?.userId,
            challengeWinners = winners,
            genreChanges = genreChanges(
                input.previous.filterKeys { it in input.players }.mapValues { it.value.genres },
                input.window.filterKeys { it in input.players }.mapValues { it.value.genres },
            ),
            leadChanges = input.rivalId?.let { RivalryRules.leadChanges(input.daily, input.myId, it, first, last) },
        )
    }

    /** The single player with the most chapters owns a genre; a tie at the top is contested; nobody → unclaimed. */
    fun owner(genre: String, genres: Map<String, Map<String, Long>>): GenreOwner {
        val scores = genres.mapNotNull { (id, counts) -> counts[genre]?.takeIf { it > 0 }?.let { id to it } }
        val best = scores.maxOfOrNull { it.second } ?: return GenreOwner.Unclaimed
        val leaders = scores.filter { it.second == best }
        return if (leaders.size == 1) GenreOwner.Owned(leaders.single().first) else GenreOwner.Contested
    }

    /** Genres whose owner changed from the previous 7 days to the window, at most 5, by genre name. */
    fun genreChanges(
        previous: Map<String, Map<String, Long>>,
        window: Map<String, Map<String, Long>>,
    ): List<GenreChange> {
        val genres = (previous.values + window.values).flatMap { counts -> counts.filterValues { it > 0 }.keys }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
        return genres.mapNotNull { genre ->
            val before = owner(genre, previous)
            val after = owner(genre, window)
            if (before == after) null else GenreChange(genre, before, after)
        }.take(MAX_GENRE_CHANGES)
    }

    /** "Action: Filip took it" / "Comedy: you took it" / "Romance: now contested" / "Isekai: unclaimed". */
    fun changeText(
        change: GenreChange,
        myId: String,
        name: (String) -> String,
    ): String = when (val after = change.after) {
        is GenreOwner.Owned -> if (after.userId == myId) {
            "${change.genre}: you took it"
        } else {
            "${change.genre}: ${name(after.userId)} took it"
        }
        GenreOwner.Contested -> "${change.genre}: now contested"
        GenreOwner.Unclaimed -> "${change.genre}: unclaimed"
    }
}
