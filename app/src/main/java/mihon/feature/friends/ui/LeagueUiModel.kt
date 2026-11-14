package mihon.feature.friends.ui

import androidx.compose.runtime.Immutable
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.FeedRow
import mihon.feature.friends.data.TopSeries
import mihon.feature.friends.domain.DayTotal
import mihon.feature.friends.domain.HeadToHead
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.LeaguePeriods
import mihon.feature.friends.domain.Medal
import mihon.feature.friends.domain.MedalCabinet
import mihon.feature.friends.domain.Medals
import mihon.feature.friends.domain.MonthStats
import mihon.feature.friends.domain.Streaks
import mihon.feature.friends.domain.TitleAward
import mihon.feature.friends.domain.Titles
import java.time.LocalDate

@Immutable
data class Standing(
    val rank: Int,
    val row: BoardRow,
    val streak: Int,
)

@Immutable
data class RivalMetric(val kind: Kind, val mine: Long, val theirs: Long) {
    enum class Kind { CHAPTERS, TIME, SERIES, STREAK, BEST_DAY }
}

@Immutable
data class Rivalry(
    val rival: BoardRow,
    val metrics: List<RivalMetric>,
    val myMonthlyWins: Int,
    val theirMonthlyWins: Int,
)

@Immutable
data class LeagueUiModel(
    val period: LeaguePeriod,
    val myId: String,
    val standings: List<Standing>,
    /** Medal each member would get if the selected period ended now. */
    val placings: Map<String, Medal>,
    val titles: List<TitleAward>,
    val cabinets: Map<String, MedalCabinet>,
    val topSeries: List<TopSeries>,
    val chartDays: List<LocalDate>,
    /** Chapters per member for each of [chartDays]. */
    val chart: Map<String, List<Long>>,
    val rivalries: List<Rivalry>,
    val feed: List<FeedRow>,
    val members: Map<String, BoardRow>,
) {
    val me: Standing? get() = standings.firstOrNull { it.row.userId == myId }

    /** Chapters ahead of second place when leading, otherwise chapters behind the leader. */
    val gap: Long
        get() {
            val me = me ?: return 0
            return if (me.rank == 1) {
                me.row.chapters - (standings.getOrNull(1)?.row?.chapters ?: 0)
            } else {
                standings.first().row.chapters - me.row.chapters
            }
        }

    fun titlesOf(userId: String): List<TitleAward> = titles.filter { it.holder == userId }

    companion object {

        private const val TOP_SERIES = 6
        private const val ALL_TIME_CHART_DAYS = 30L
        private const val WEEK_CHART_DAYS = 7L

        fun build(snapshot: ArenaSnapshot, myId: String, period: LeaguePeriod): LeagueUiModel {
            val zone = snapshot.zone
            val today = LeaguePeriods.localDate(snapshot.now, zone)
            val monthStart = LeaguePeriods.monthStart(today)
            val board = snapshot.boards[period].orEmpty()
            val members = (snapshot.boards[LeaguePeriod.ALL].orEmpty() + board).associateBy { it.userId }

            val daysByUser = snapshot.daily.filter { it.chapters > 0 }.groupBy { it.userId }
            val streaks = members.keys.associateWith { id ->
                Streaks.current(daysByUser[id].orEmpty().mapTo(HashSet()) { it.day }, today)
            }

            val standings = board
                .sortedWith(
                    compareByDescending<BoardRow> { it.chapters }
                        .thenByDescending { it.seconds }
                        .thenBy { it.displayName.lowercase() },
                )
                .mapIndexed { index, row -> Standing(index + 1, row, streaks[row.userId] ?: 0) }

            val monthBoard = snapshot.boards[LeaguePeriod.MONTH].orEmpty()
            val monthDaily = snapshot.daily.filter { it.day >= monthStart }
            val monthStats = monthBoard.map { row ->
                val hours = snapshot.hours.filter { it.userId == row.userId }
                MonthStats(
                    userId = row.userId,
                    chapters = row.chapters,
                    seconds = row.seconds,
                    series = row.series,
                    genres = row.genres,
                    bestDay = monthDaily.filter { it.userId == row.userId }.maxOfOrNull { it.chapters } ?: 0,
                    nightChapters = hours.filter { it.hour in Titles.NIGHT_HOURS }.sumOf { it.chapters },
                    morningChapters = hours.filter { it.hour in Titles.MORNING_HOURS }.sumOf { it.chapters },
                    currentStreak = streaks[row.userId] ?: 0,
                )
            }

            val created = snapshot.arena.createdInstant?.let { LeaguePeriods.localDate(it, zone) } ?: today
            val cabinets = Medals.cabinets(snapshot.daily, members.keys, created, today)

            val topSeries = board.flatMap { it.topSeries }
                .groupBy { it.key }
                .map { (key, entries) ->
                    TopSeries(
                        key = key,
                        title = entries.firstNotNullOfOrNull { it.title },
                        cover = entries.firstNotNullOfOrNull { it.cover },
                        chapters = entries.sumOf { it.chapters },
                        seconds = entries.sumOf { it.seconds },
                    )
                }
                .sortedWith(compareByDescending<TopSeries> { it.chapters }.thenByDescending { it.seconds })
                .take(TOP_SERIES)

            val periodStart = when (period) {
                LeaguePeriod.TODAY, LeaguePeriod.WEEK -> today.minusDays(WEEK_CHART_DAYS - 1)
                LeaguePeriod.MONTH -> monthStart
                LeaguePeriod.ALL -> today.minusDays(ALL_TIME_CHART_DAYS - 1)
            }
            val chartDays = generateSequence(periodStart) { it.plusDays(1) }.takeWhile { it <= today }.toList()
            val chart = members.keys.associateWith { id ->
                val byDay = daysByUser[id].orEmpty().associate { it.day to it.chapters }
                chartDays.map { byDay[it] ?: 0 }
            }

            val range = LeaguePeriods.range(period, snapshot.now, zone)
            val rangeStart = LeaguePeriods.localDate(range.from, zone)
            val me = board.firstOrNull { it.userId == myId }
            val rivalries = if (me == null) {
                emptyList()
            } else {
                standings.filter { it.row.userId != myId }.map { rival ->
                    fun bestDay(id: String) = daysByUser[id].orEmpty()
                        .filter { it.day >= rangeStart && it.day <= today }
                        .maxOfOrNull { it.chapters } ?: 0
                    val (mine, theirs) = HeadToHead.monthlyWins(
                        snapshot.daily,
                        myId,
                        rival.row.userId,
                        monthStart,
                        today,
                    )
                    Rivalry(
                        rival = rival.row,
                        metrics = listOf(
                            RivalMetric(RivalMetric.Kind.CHAPTERS, me.chapters, rival.row.chapters),
                            RivalMetric(RivalMetric.Kind.TIME, me.seconds, rival.row.seconds),
                            RivalMetric(RivalMetric.Kind.SERIES, me.series, rival.row.series),
                            RivalMetric(
                                RivalMetric.Kind.STREAK,
                                (streaks[myId] ?: 0).toLong(),
                                rival.streak.toLong(),
                            ),
                            RivalMetric(RivalMetric.Kind.BEST_DAY, bestDay(myId), bestDay(rival.row.userId)),
                        ),
                        myMonthlyWins = mine,
                        theirMonthlyWins = theirs,
                    )
                }
            }

            return LeagueUiModel(
                period = period,
                myId = myId,
                standings = standings,
                placings = Medals.placings(
                    board.map { DayTotal(it.userId, today, it.chapters, it.seconds) },
                    board.map { it.userId },
                ),
                titles = Titles.compute(monthStats),
                cabinets = cabinets,
                topSeries = topSeries,
                chartDays = chartDays,
                chart = chart,
                rivalries = rivalries,
                feed = snapshot.feed,
                members = members,
            )
        }
    }
}
