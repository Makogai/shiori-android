package mihon.feature.friends.domain

import java.time.LocalDate

/** Chapters and seconds one member read on one league-timezone day. */
data class DayTotal(val userId: String, val day: LocalDate, val chapters: Long, val seconds: Long)

enum class PeriodType { DAY, WEEK, MONTH }

enum class Medal(val emoji: String) { GOLD("🥇"), SILVER("🥈"), BRONZE("🥉") }

data class MedalTally(val gold: Int = 0, val silver: Int = 0, val bronze: Int = 0) {
    val total: Int get() = gold + silver + bronze

    operator fun plus(medal: Medal) = when (medal) {
        Medal.GOLD -> copy(gold = gold + 1)
        Medal.SILVER -> copy(silver = silver + 1)
        Medal.BRONZE -> copy(bronze = bronze + 1)
    }

    operator fun plus(other: MedalTally) =
        MedalTally(gold + other.gold, silver + other.silver, bronze + other.bronze)
}

data class MedalCabinet(
    val day: MedalTally = MedalTally(),
    val week: MedalTally = MedalTally(),
    val month: MedalTally = MedalTally(),
) {
    val total: MedalTally get() = day + week + month

    fun with(type: PeriodType, medal: Medal) = when (type) {
        PeriodType.DAY -> copy(day = day + medal)
        PeriodType.WEEK -> copy(week = week + medal)
        PeriodType.MONTH -> copy(month = month + medal)
    }
}

object Medals {

    /**
     * Medals for every completed day, week and month that ends after [leagueCreated]. Places use competition
     * ranking on chapters then seconds (tied members share a place and the next place is skipped); members who read
     * nothing still place, a period where nobody read awards nothing, and bronze needs at least three members.
     */
    fun cabinets(
        daily: List<DayTotal>,
        memberIds: Collection<String>,
        leagueCreated: LocalDate,
        today: LocalDate,
    ): Map<String, MedalCabinet> {
        val members = memberIds.toSet()
        val result = members.associateWith { MedalCabinet() }.toMutableMap()
        val rows = daily.filter { it.userId in members }
        for (type in PeriodType.entries) {
            rows.groupBy { periodStart(type, it.day) }
                .filterKeys { start -> isCompleted(type, start, today) && periodEnd(type, start) > leagueCreated }
                .forEach { (_, periodRows) ->
                    placings(periodRows, members).forEach { (userId, medal) ->
                        result[userId] = result.getValue(userId).with(type, medal)
                    }
                }
        }
        return result
    }

    /** Medal per member for one period's rows (any number of days per member; missing members read nothing). */
    fun placings(rows: List<DayTotal>, memberIds: Collection<String>): Map<String, Medal> {
        val byUser = rows.groupBy { it.userId }
        val totals = memberIds.toSet().associateWith { id ->
            val list = byUser[id].orEmpty()
            list.sumOf { it.chapters } to list.sumOf { it.seconds }
        }
        if (totals.values.all { it.first == 0L }) return emptyMap()
        val memberCount = totals.size
        return buildMap {
            totals.forEach { (userId, score) ->
                val place = 1 + totals.values.count { other -> compareScores(other, score) > 0 }
                val medal = when {
                    place == 1 -> Medal.GOLD
                    place == 2 -> Medal.SILVER
                    place == 3 && memberCount >= 3 -> Medal.BRONZE
                    else -> null
                }
                if (medal != null) put(userId, medal)
            }
        }
    }

    fun periodStart(type: PeriodType, day: LocalDate): LocalDate = when (type) {
        PeriodType.DAY -> day
        PeriodType.WEEK -> LeaguePeriods.weekStart(day)
        PeriodType.MONTH -> LeaguePeriods.monthStart(day)
    }

    fun periodEnd(type: PeriodType, start: LocalDate): LocalDate = when (type) {
        PeriodType.DAY -> start.plusDays(1)
        PeriodType.WEEK -> start.plusWeeks(1)
        PeriodType.MONTH -> start.plusMonths(1)
    }

    fun isCompleted(type: PeriodType, start: LocalDate, today: LocalDate): Boolean = periodEnd(type, start) <= today

    private fun compareScores(a: Pair<Long, Long>, b: Pair<Long, Long>): Int =
        compareValuesBy(a, b, { it.first }, { it.second })
}

object Streaks {

    /** Consecutive days with a read, ending today or yesterday. */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var day = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun best(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue
            var length = 0
            var cursor = day
            while (cursor in days) {
                length++
                cursor = cursor.plusDays(1)
            }
            best = maxOf(best, length)
        }
        return best
    }
}

enum class LeagueTitle(val emoji: String, val label: String) {
    CHAMPION("👑", "League Champion"),
    STREAK_MASTER("🔥", "Streak Master"),
    BINGE_LORD("📚", "Binge Lord"),
    NIGHT_OWL("🦉", "Night Owl"),
    EARLY_BIRD("🌅", "Early Bird"),
    MARATHONER("⏱️", "Marathoner"),
    SPEED_READER("⚡", "Speed Reader"),
    EXPLORER("🧭", "Explorer"),
    GENRE_KING("🎭", "Genre King"),
}

data class TitleAward(val title: LeagueTitle, val holder: String, val genre: String? = null) {
    /** The text stored in `profiles.title` and shown under names; identical on both apps. */
    val text: String
        get() = buildString {
            append(title.emoji).append(' ').append(title.label)
            if (genre != null) append(" · ").append(genre)
        }
}

/** One member's numbers for the current month. */
data class MonthStats(
    val userId: String,
    val chapters: Long,
    val seconds: Long,
    val series: Long,
    val genres: Map<String, Long>,
    val bestDay: Long,
    val nightChapters: Long,
    val morningChapters: Long,
    val currentStreak: Int,
)

object Titles {

    const val STREAK_MIN = 3
    const val SPEED_READER_MIN_CHAPTERS = 20
    const val GENRE_KING_MIN = 5
    const val GENRE_KINGS = 3

    val NIGHT_HOURS = 0..4
    val MORNING_HOURS = 5..8

    fun compute(stats: List<MonthStats>): List<TitleAward> {
        val eligible = stats.filter { it.chapters >= 1 }
        return buildList {
            fun award(title: LeagueTitle, holder: MonthStats?, genre: String? = null) {
                if (holder != null) add(TitleAward(title, holder.userId, genre))
            }

            award(
                LeagueTitle.CHAMPION,
                uniqueBest(eligible, compareBy<MonthStats>({ it.chapters }, { it.seconds })),
            )
            award(
                LeagueTitle.STREAK_MASTER,
                uniqueMax(eligible.filter { it.currentStreak >= STREAK_MIN }) { it.currentStreak.toLong() },
            )
            award(LeagueTitle.BINGE_LORD, uniqueMax(eligible) { it.bestDay })
            award(LeagueTitle.NIGHT_OWL, uniqueMax(eligible) { it.nightChapters })
            award(LeagueTitle.EARLY_BIRD, uniqueMax(eligible) { it.morningChapters })
            award(LeagueTitle.MARATHONER, uniqueMax(eligible) { it.seconds })
            award(
                LeagueTitle.SPEED_READER,
                uniqueBest(
                    eligible.filter { it.chapters >= SPEED_READER_MIN_CHAPTERS && it.seconds > 0 },
                    compareByDescending { it.seconds.toDouble() / it.chapters },
                ),
            )
            award(LeagueTitle.EXPLORER, uniqueMax(eligible) { it.series })
            topGenres(eligible).forEach { genre ->
                val candidates = eligible.filter { (it.genres[genre] ?: 0) >= GENRE_KING_MIN }
                award(LeagueTitle.GENRE_KING, uniqueMax(candidates) { it.genres[genre] ?: 0 }, genre)
            }
        }
    }

    /** The league's most-read genres this month, most chapters first, then by name. */
    fun topGenres(stats: List<MonthStats>): List<String> =
        stats.flatMap { it.genres.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(GENRE_KINGS)
            .map { it.key }

    private fun uniqueMax(stats: List<MonthStats>, selector: (MonthStats) -> Long): MonthStats? =
        uniqueBest(stats.filter { selector(it) > 0 }, compareBy(selector))

    /** The single greatest element by [comparator], or null when it's empty or the top is tied. */
    private fun uniqueBest(stats: List<MonthStats>, comparator: Comparator<MonthStats>): MonthStats? {
        val sorted = stats.sortedWith(comparator.reversed())
        val best = sorted.firstOrNull() ?: return null
        if (sorted.size > 1 && comparator.compare(sorted[1], best) == 0) return null
        return best
    }
}

object HeadToHead {

    /**
     * Days of the month starting at [monthStart] before [today] won by each side: more chapters, then more seconds.
     * Days where neither read or both tied count for nobody.
     */
    fun monthlyWins(
        daily: List<DayTotal>,
        me: String,
        rival: String,
        monthStart: LocalDate,
        today: LocalDate,
    ): Pair<Int, Int> {
        val monthEnd = monthStart.plusMonths(1)
        val byDay = daily.filter { it.day >= monthStart && it.day < monthEnd && it.day < today }
            .groupBy { it.day }
        var mine = 0
        var theirs = 0
        byDay.values.forEach { rows ->
            val a = rows.firstOrNull { it.userId == me }
            val b = rows.firstOrNull { it.userId == rival }
            val cmp = compareValuesBy(a, b, { it?.chapters ?: 0L }, { it?.seconds ?: 0L })
            if (cmp > 0) {
                mine++
            } else if (cmp < 0) {
                theirs++
            }
        }
        return mine to theirs
    }
}
