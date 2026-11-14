package mihon.feature.friends.ui

import androidx.compose.runtime.Immutable
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.TopSeries
import mihon.feature.friends.domain.DayTotal
import mihon.feature.friends.domain.HeadToHeadRecord
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.LeaguePeriods
import mihon.feature.friends.domain.MedalCabinet
import mihon.feature.friends.domain.RivalryRules
import mihon.feature.friends.domain.SettledBet
import mihon.feature.friends.domain.Streaks
import mihon.feature.friends.domain.TitleAward
import mihon.feature.friends.domain.Xp
import java.time.LocalDate

enum class GenrePeriod(val period: LeaguePeriod) {
    WEEK(
        LeaguePeriod.WEEK,
    ),
    SEASON(LeaguePeriod.MONTH),
    ALL(LeaguePeriod.ALL),
}

@Immutable
data class GenreTerritory(
    val genre: String,
    val total: Long,
    /** Chapters per player, most first; players who read none are left out. */
    val scores: List<Pair<String, Long>>,
    /** The single player with the most chapters in it; null when unread or tied at the top. */
    val owner: String?,
    /** A running challenge for this genre, if any. */
    val challenge: ChallengeCard?,
) {
    val contested: Boolean get() = owner == null && scores.size > 1 && scores[0].second == scores[1].second
}

@Immutable
data class GenreDuel(val genre: String, val mine: Long, val theirs: Long)

@Immutable
data class GenreWarsModel(
    val period: GenrePeriod,
    /** Most-read genres first; genres with a running challenge are always included. */
    val territories: List<GenreTerritory>,
    /** Genres owned per player, most first. */
    val crowns: List<Pair<String, Int>>,
    val myTop: List<Pair<String, Long>>,
    val rivalTop: List<Pair<String, Long>>,
    /** Me against the rival in the genres the two of us read most. */
    val duels: List<GenreDuel>,
    val challenges: List<ChallengeCard>,
) {
    companion object {
        private const val TOP = 5
        private const val DUELS = 6

        fun build(snapshot: ArenaSnapshot, arena: ArenaUiModel, period: GenrePeriod): GenreWarsModel {
            val board = snapshot.boards[period.period].orEmpty().filter { it.userId in arena.players }
            val challenges = arena.activeChallenges.filter {
                it.challenge.metric == "genre" &&
                    it.challenge.genre != null
            }
            val byGenre = mutableMapOf<String, MutableMap<String, Long>>()
            board.forEach { row ->
                row.genres.forEach { (genre, chapters) ->
                    if (chapters > 0) byGenre.getOrPut(genre) { mutableMapOf() }[row.userId] = chapters
                }
            }
            challenges.forEach { card ->
                val genre = card.challenge.genre!!
                if (byGenre.keys.none { it.equals(genre, ignoreCase = true) }) byGenre[genre] = mutableMapOf()
            }
            val territories = byGenre.map { (genre, scores) ->
                val sorted = scores.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { arena.name(it.key) })
                    .map { it.key to it.value }
                val owner = sorted.firstOrNull()?.takeIf { top -> sorted.getOrNull(1)?.second != top.second }?.first
                GenreTerritory(
                    genre = genre,
                    total = sorted.sumOf { it.second },
                    scores = sorted,
                    owner = owner,
                    challenge = challenges.firstOrNull { it.challenge.genre.equals(genre, ignoreCase = true) },
                )
            }.sortedWith(
                compareByDescending<GenreTerritory> { it.challenge != null }
                    .thenByDescending { it.total }
                    .thenBy { it.genre },
            )
            val crowns = territories.mapNotNull { it.owner }.groupingBy { it }.eachCount()
                .entries.sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.value
                    }.thenBy { arena.name(it.key) },
                )
                .map { it.key to it.value }

            fun top(userId: String?) = board.firstOrNull { it.userId == userId }?.genres.orEmpty()
                .filterValues { it > 0 }
                .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .take(TOP)
                .map { it.key to it.value }

            val mine = board.firstOrNull { it.userId == arena.myId }?.genres.orEmpty()
            val theirs = board.firstOrNull { it.userId == arena.rivalId }?.genres.orEmpty()
            val duels = if (arena.rivalId == null) {
                emptyList()
            } else {
                (mine.keys + theirs.keys)
                    .map { GenreDuel(it, mine[it] ?: 0, theirs[it] ?: 0) }
                    .filter { it.mine + it.theirs > 0 }
                    .sortedWith(compareByDescending<GenreDuel> { it.mine + it.theirs }.thenBy { it.genre })
                    .take(DUELS)
            }
            return GenreWarsModel(
                period = period,
                territories = territories,
                crowns = crowns,
                myTop = top(arena.myId),
                rivalTop = top(arena.rivalId),
                duels = duels,
                challenges = challenges,
            )
        }
    }
}

@Immutable
data class Totals(val chapters: Long, val seconds: Long)

@Immutable
data class PlayerProfileModel(
    val player: ArenaPlayer,
    val isMe: Boolean,
    val seasonPlace: Int?,
    val allTime: Totals,
    val season: Totals,
    val week: Totals,
    val series: Long,
    /** Average minutes per chapter over all counted reads, or null before the first chapter. */
    val minutesPerChapter: Double?,
    val currentStreak: Int,
    val longestStreak: Int,
    val bestDay: DayTotal?,
    /** Chapters per hour of the day (Arena time), 24 entries. */
    val hours: List<Long>,
    /** Chapters per genre, most first. */
    val genres: List<Pair<String, Long>>,
    val topSeries: List<TopSeries>,
    /** Lifetime XP at the end of each day XP was earned, oldest first, starting at 0. */
    val xpHistory: List<Pair<LocalDate, Long>>,
    /** Settled bets with their challenge's emoji and title, newest first. */
    val settledBets: List<Pair<String, SettledBet>>,
    val seasonHistory: List<SeasonResult>,
    val medals: MedalCabinet,
    val titles: List<TitleAward>,
    val records: List<Pair<ArenaPlayer, HeadToHeadRecord>>,
) {
    companion object {

        fun build(snapshot: ArenaSnapshot, myId: String, userId: String): PlayerProfileModel? {
            val arena = ArenaUiModel.build(snapshot, myId)
            val player = arena.players[userId] ?: return null
            val month = LeagueUiModel.build(snapshot, myId, LeaguePeriod.MONTH)
            fun totals(period: LeaguePeriod) = snapshot.boards[period].orEmpty().firstOrNull { it.userId == userId }
                .let { Totals(it?.chapters ?: 0, it?.seconds ?: 0) }
            val all = snapshot.boards[LeaguePeriod.ALL].orEmpty().firstOrNull { it.userId == userId }
            val days = snapshot.daily.filter { it.userId == userId && it.chapters > 0 }
            val daySet = days.mapTo(HashSet()) { it.day }
            val hours = snapshot.lifetimeHours.filter { it.userId == userId }.groupBy { it.hour }
            val allTime = totals(LeaguePeriod.ALL)

            return PlayerProfileModel(
                player = player,
                isMe = userId == myId,
                seasonPlace = arena.seasonPlaces[userId]?.takeIf { player.seasonXp > 0 },
                allTime = allTime,
                season = totals(LeaguePeriod.MONTH),
                week = totals(LeaguePeriod.WEEK),
                series = all?.series ?: 0,
                minutesPerChapter = if (allTime.chapters > 0) allTime.seconds / 60.0 / allTime.chapters else null,
                currentStreak = Streaks.current(daySet, arena.today),
                longestStreak = Streaks.best(daySet),
                bestDay = days.maxWithOrNull(compareBy<DayTotal>({ it.chapters }, { it.seconds })),
                hours = (0..23).map { hour -> hours[hour].orEmpty().sumOf { it.chapters } },
                genres = all?.genres.orEmpty().filterValues { it > 0 }
                    .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                    .map { it.key to it.value },
                topSeries = all?.topSeries.orEmpty(),
                xpHistory = xpHistory(days, player, snapshot),
                settledBets = player.settledBets.map { bet ->
                    val challenge = snapshot.challenges.firstOrNull { it.id == bet.challengeId }
                    (challenge?.let { "${it.emoji} ${it.title}" } ?: "#${bet.challengeId}") to bet
                },
                seasonHistory = arena.seasonHistories[userId].orEmpty(),
                medals = month.cabinets[userId] ?: MedalCabinet(),
                titles = month.titlesOf(userId),
                records = arena.players.values.filter { it.userId != userId }
                    .sortedBy { arena.seasonRanking.indexOf(it.userId) }
                    .map { other -> other to RivalryRules.record(snapshot.daily, userId, other.userId, arena.today) },
            )
        }

        /**
         * Running XP by day: each day's reads, quests, challenges won and bets settled that ended that day; plotted
         * clamped at 0 and ending at the lifetime XP shown.
         */
        private fun xpHistory(
            days: List<DayTotal>,
            player: ArenaPlayer,
            snapshot: ArenaSnapshot,
        ): List<Pair<LocalDate, Long>> {
            val byDay = HashMap<LocalDate, Long>()
            fun add(day: LocalDate, xp: Long) = byDay.merge(day, xp, Long::plus)
            days.forEach { add(it.day, Xp.reads(it.chapters, it.seconds)) }
            player.wins.forEach { add(LeaguePeriods.localDate(it.end, snapshot.zone), Xp.PER_CHALLENGE_WIN) }
            player.questDays.forEach { (day, quests) -> add(day, Xp.PER_QUEST * quests) }
            player.settledBets.forEach { add(LeaguePeriods.localDate(it.end, snapshot.zone), it.result) }
            return Xp.history(byDay)
        }
    }
}
