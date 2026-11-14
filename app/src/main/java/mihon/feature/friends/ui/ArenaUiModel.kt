package mihon.feature.friends.ui

import androidx.compose.runtime.Immutable
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.Challenge
import mihon.feature.friends.data.ChallengeScore
import mihon.feature.friends.data.Suggestion
import mihon.feature.friends.data.SuggestionRules
import mihon.feature.friends.domain.AchievementProgress
import mihon.feature.friends.domain.Achievements
import mihon.feature.friends.domain.Bet
import mihon.feature.friends.domain.Bets
import mihon.feature.friends.domain.ChallengeOutcome
import mihon.feature.friends.domain.ChallengeRules
import mihon.feature.friends.domain.DailyQuests
import mihon.feature.friends.domain.DayScore
import mihon.feature.friends.domain.HeadToHeadRecord
import mihon.feature.friends.domain.Lead
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.LeaguePeriods
import mihon.feature.friends.domain.LevelInfo
import mihon.feature.friends.domain.Levels
import mihon.feature.friends.domain.LifetimeStats
import mihon.feature.friends.domain.LocalToday
import mihon.feature.friends.domain.Places
import mihon.feature.friends.domain.QuestDayStats
import mihon.feature.friends.domain.QuestProgress
import mihon.feature.friends.domain.Race
import mihon.feature.friends.domain.Races
import mihon.feature.friends.domain.RivalryRules
import mihon.feature.friends.domain.Seasons
import mihon.feature.friends.domain.SettledBet
import mihon.feature.friends.domain.Streaks
import mihon.feature.friends.domain.Tier
import mihon.feature.friends.domain.TodayEntry
import mihon.feature.friends.domain.WeeklyRecap
import mihon.feature.friends.domain.WeeklyRecaps
import mihon.feature.friends.domain.Xp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

@Immutable
data class ArenaPlayer(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val title: String?,
    val level: LevelInfo,
    val seasonXp: Long,
    val tier: Tier,
    val todayChapters: Long,
    val todaySeconds: Long,
    val streak: Int,
    /** Challenges won, newest first. */
    val wins: List<Challenge>,
    val questsCompleted: Int,
    val perfectDays: Int,
    val achievements: List<AchievementProgress>,
    /** Quests completed per Arena day. */
    val questDays: Map<LocalDate, Int> = emptyMap(),
    /** This player's suggestions that were added, as far as this device can see. */
    val suggestionsAccepted: Int = 0,
    /** Bets on ended challenges, newest first. */
    val settledBets: List<SettledBet> = emptyList(),
) {
    /** Lifetime XP as shown (never below 0). */
    val xp: Long get() = level.xp

    /** Season XP still needed for the next tier, or null at Legend. */
    val toNextTier: Long? get() = tier.next?.let { it.minXp - seasonXp }

    /** Progress through the current tier towards the next one, 0..1 (1 at Legend). */
    val tierProgress: Float
        get() {
            val next = tier.next ?: return 1f
            return ((seasonXp - tier.minXp).toFloat() / (next.minXp - tier.minXp)).coerceIn(0f, 1f)
        }
}

@Immutable
data class ChallengeStanding(val userId: String, val score: Double, val place: Int)

@Immutable
data class ChallengeCard(
    val challenge: Challenge,
    /** Best first. */
    val standings: List<ChallengeStanding>,
    /** Players with the top score above 0 (the winners once it has ended). */
    val leaders: Set<String>,
    /** Lasts at most 7 days, so it can be bet on (v6). */
    val weekly: Boolean = false,
    /** Bets on this challenge, oldest first. */
    val bets: List<Bet> = emptyList(),
    /** Bets are still open (until halfway). */
    val betable: Boolean = false,
) {
    val closesAt: Instant get() = Bets.closesAt(challenge.start, challenge.end)

    fun betOf(userId: String): Bet? = bets.firstOrNull { it.userId == userId }
}

@Immutable
data class SeasonResult(val season: YearMonth, val tier: Tier, val place: Int, val xp: Long) {
    val number: Int get() = Seasons.number(season)
}

@Immutable
data class ArenaUiModel(
    val myId: String,
    val now: Instant,
    val zone: ZoneId,
    val today: LocalDate,
    val season: YearMonth,
    val players: Map<String, ArenaPlayer>,
    /** Player ids by season XP, best first. */
    val seasonRanking: List<String>,
    val seasonPlaces: Map<String, Int>,
    val rivalId: String?,
    /** My share of today's chapters against the rival, 0..1. */
    val tugShare: Float,
    val lead: Lead,
    val record: HeadToHeadRecord,
    val leadChanges: Int,
    val winStreak: Int,
    val activeChallenges: List<ChallengeCard>,
    val upcoming: Challenge?,
    /** Newest first. */
    val pastChallenges: List<ChallengeCard>,
    /** My quests today. */
    val quests: List<QuestProgress>,
    /** Finished seasons per player, newest first. */
    val seasonHistories: Map<String, List<SeasonResult>>,
    /** Suggestions sent or received. */
    val suggestions: List<Suggestion> = emptyList(),
    /** Same-series races against other players, most recent first (v6). */
    val races: List<Race> = emptyList(),
    /** Every ended week's recap, newest first (v6). */
    val recaps: List<WeeklyRecap> = emptyList(),
    /** The latest recap while it is less than 7 days old. */
    val freshRecap: WeeklyRecap? = null,
    /** Each player's chapters on their own local day, ranked (README v9). */
    val localToday: List<TodayEntry> = emptyList(),
) {
    /** Incoming pending suggestions. */
    val newSuggestions: Int get() = SuggestionRules.badge(suggestions, myId)

    /** My finished seasons, newest first. */
    val seasonHistory: List<SeasonResult> get() = seasonHistories[myId].orEmpty()

    val me: ArenaPlayer? get() = players[myId]
    val rival: ArenaPlayer? get() = rivalId?.let { players[it] }
    val seasonNumber: Int get() = Seasons.number(season)
    val perfectToday: Boolean get() = quests.isNotEmpty() && quests.all { it.done }

    /** Chapters between me and the rival today, always >= 0. */
    val todayGap: Long get() = abs((me?.todayChapters ?: 0) - (rival?.todayChapters ?: 0))

    fun name(userId: String): String = players[userId]?.name.orEmpty()

    companion object {

        fun build(snapshot: ArenaSnapshot, myId: String): ArenaUiModel {
            val zone = snapshot.zone
            val now = snapshot.now
            val today = LeaguePeriods.localDate(now, zone)
            val season = Seasons.of(today)
            val allBoard = snapshot.boards[LeaguePeriod.ALL].orEmpty()
            val allDaysByUser = snapshot.daily.groupBy { it.userId }
            fun readsXp(userId: String, month: YearMonth? = null) = Xp.reads(
                allDaysByUser[userId].orEmpty().filter { month == null || YearMonth.from(it.day) == month },
            )
            val todayBoard = snapshot.boards[LeaguePeriod.TODAY].orEmpty().associateBy { it.userId }
            val rows = allBoard.associateBy { it.userId }
            val ids = rows.keys

            val daysByUser = snapshot.daily.filter { it.chapters > 0 }.groupBy { it.userId }
            val dailyByDay = snapshot.daily.groupBy { it.day }

            // Challenges
            val ended = snapshot.challenges.filter { it.hasEnded(now) && it.id in snapshot.challengeBoards }
                .sortedWith(compareBy<Challenge>({ it.end }, { it.start }, { it.id }))
            val betsByChallenge = snapshot.bets.filter { it.userId in ids }.groupBy { it.challengeId }
            fun card(challenge: Challenge): ChallengeCard {
                val scores = snapshot.challengeBoards[challenge.id].orEmpty().filter { it.userId in ids }
                return challengeCard(challenge, scores, rows).copy(
                    weekly = Bets.isWeekly(challenge.start, challenge.end),
                    bets = betsByChallenge[challenge.id].orEmpty(),
                    betable = Bets.betable(challenge.start, challenge.end, now),
                )
            }
            val endedCards = ended.map(::card)
            val winsByUser = ids.associateWith { id ->
                endedCards.filter { id in it.leaders }.map { it.challenge }
            }
            fun seasonWins(userId: String, month: YearMonth) =
                winsByUser[userId].orEmpty().count { YearMonth.from(it.end.atZone(zone)) == month }

            // Bets settle from the same winners once their challenge has ended
            val outcomes = endedCards.associate {
                it.challenge.id to ChallengeOutcome(it.challenge.id, it.challenge.end, it.leaders)
            }
            val settledByUser = Bets.settle(snapshot.bets.filter { it.userId in ids }, outcomes).groupBy { it.userId }
            fun betXp(userId: String, month: YearMonth? = null) = settledByUser[userId].orEmpty()
                .filter { month == null || YearMonth.from(it.end.atZone(zone)) == month }
                .sumOf { it.result }
            fun seasonXp(userId: String, month: YearMonth) = Xp.display(
                readsXp(userId, month) + Xp.PER_CHALLENGE_WIN * seasonWins(userId, month) + betXp(userId, month),
            )

            // Quests
            fun questStats(userId: String, day: LocalDate): QuestDayStats {
                val dayRows = dailyByDay[day].orEmpty()
                val mine = dayRows.firstOrNull { it.userId == userId }
                val facts = snapshot.questFacts[day]?.get(userId)
                val others = ids - userId
                return QuestDayStats(
                    chapters = mine?.chapters ?: 0,
                    seconds = mine?.seconds ?: 0,
                    series = facts?.series ?: 0,
                    early = facts?.early ?: 0,
                    late = facts?.late ?: 0,
                    newSeries = facts?.newSeries ?: false,
                    othersBest = if (others.isEmpty()) {
                        null
                    } else {
                        dayRows.filter { it.userId in others }.maxOfOrNull { it.chapters } ?: 0
                    },
                )
            }
            val questDays = generateSequence(DailyQuests.EPOCH) { it.plusDays(1) }.takeWhile { it <= today }.toList()
            val completedByUser = ids.associateWith { id ->
                questDays.associateWith { day ->
                    val derived = DailyQuests.forDay(day, questStats(id, day)).filter { it.done }.map { it.quest }
                    val local = if (id == myId) {
                        DailyQuests.forDay(day).filter { "$day:${it.name}" in snapshot.localQuests }
                    } else {
                        emptyList()
                    }
                    (derived + local).toSet()
                }
            }

            val players = ids.associateWith { id ->
                val row = rows.getValue(id)
                val wins = winsByUser[id].orEmpty()
                val completed = completedByUser[id].orEmpty()
                val questCount = completed.values.sumOf { it.size }
                val perfectDays = completed.values.count { it.size == DailyQuests.PER_DAY }
                val seasonXp = seasonXp(id, season)
                val days = daysByUser[id].orEmpty().mapTo(HashSet()) { it.day }
                val hours = snapshot.lifetimeHours.filter { it.userId == id }
                val suggestionsAccepted = SuggestionRules.accepted(snapshot.suggestions, id)
                val lifetime = LifetimeStats(
                    chapters = row.chapters,
                    seconds = row.seconds,
                    series = row.series,
                    genres = row.genres.count { it.value > 0 },
                    bestStreak = Streaks.best(days),
                    night = hours.filter { it.hour in Achievements.NIGHT_HOURS }.sumOf { it.chapters },
                    early = hours.filter { it.hour in Achievements.EARLY_HOURS }.sumOf { it.chapters },
                    challengeWins = wins.size,
                    bestWinRun = ChallengeRules.bestWinRun(endedCards.map { it.leaders }, id),
                    perfectDays = perfectDays,
                    suggestionsAdded = suggestionsAccepted,
                )
                ArenaPlayer(
                    userId = id,
                    name = row.displayName,
                    avatarUrl = row.avatarUrl,
                    title = row.title,
                    level = Levels.of(Xp.display(Xp.total(readsXp(id), wins.size, questCount, betXp(id)))),
                    seasonXp = seasonXp,
                    tier = Tier.of(seasonXp),
                    todayChapters = todayBoard[id]?.chapters ?: 0,
                    todaySeconds = todayBoard[id]?.seconds ?: 0,
                    streak = Streaks.current(days, today),
                    wins = wins.reversed(),
                    questsCompleted = questCount,
                    perfectDays = perfectDays,
                    questDays = completed.mapValues { it.value.size }.filterValues { it > 0 },
                    achievements = Achievements.evaluate(lifetime),
                    suggestionsAccepted = suggestionsAccepted,
                    settledBets = settledByUser[id].orEmpty(),
                )
            }

            val seasonRanking = players.values
                .sortedWith(compareByDescending<ArenaPlayer> { it.seasonXp }.thenBy { it.name.lowercase() })
                .map { it.userId }
            val rivalId = RivalryRules.rivalOf(seasonRanking, myId)
            val me = players[myId]
            val rival = rivalId?.let { players[it] }
            val lead = if (me != null && rival != null) {
                RivalryRules.lead(
                    DayScore(me.todayChapters, me.todaySeconds),
                    DayScore(rival.todayChapters, rival.todaySeconds),
                )
            } else {
                Lead.TIED
            }

            val seasonResults = snapshot.seasonBoards.entries.sortedByDescending { it.key }.flatMap { (month, board) ->
                val xps = board.filter {
                    it.userId in ids
                }.associate { row -> row.userId to seasonXp(row.userId, month) }
                val places = Places.of(xps)
                xps.map { (id, xp) -> id to SeasonResult(month, Tier.of(xp), places.getValue(id), xp) }
            }
            val seasonHistories = seasonResults.groupBy({ it.first }, { it.second })
            val recaps = ArenaSocialModels.recaps(snapshot, myId, rivalId, ids)

            return ArenaUiModel(
                myId = myId,
                now = now,
                zone = zone,
                today = today,
                season = season,
                players = players,
                seasonRanking = seasonRanking,
                seasonPlaces = Places.of(players.mapValues { it.value.seasonXp }),
                rivalId = rivalId,
                tugShare = RivalryRules.tugShare(me?.todayChapters ?: 0, rival?.todayChapters ?: 0),
                lead = lead,
                record = if (rivalId != null) {
                    RivalryRules.record(snapshot.daily, myId, rivalId, today)
                } else {
                    HeadToHeadRecord()
                },
                leadChanges = rivalId?.let {
                    RivalryRules.leadChanges(snapshot.daily, myId, it, LeaguePeriods.weekStart(today), today)
                } ?: 0,
                winStreak = rivalId?.let { RivalryRules.winStreak(snapshot.daily, myId, it, today) } ?: 0,
                activeChallenges = snapshot.challenges.filter { it.isActive(now) }.sortedBy { it.end }.map(::card),
                upcoming = snapshot.challenges.filter { it.start > now }.minByOrNull { it.start },
                pastChallenges = endedCards.reversed(),
                quests = DailyQuests.forDay(today, questStats(myId, today)).map { progress ->
                    val local = "$today:${progress.quest.name}" in snapshot.localQuests
                    if (local && !progress.done) progress.copy(value = progress.quest.target) else progress
                },
                seasonHistories = seasonHistories,
                suggestions = snapshot.suggestions,
                localToday = LocalToday.entries(
                    names = rows.mapValues { (id, row) -> if (id == myId) "You" else row.displayName },
                    zones = snapshot.localZones,
                    boards = snapshot.localTodayBoards,
                ),
                races = Races.build(snapshot.races.filter { it.userId in ids }, myId),
                recaps = recaps,
                freshRecap = recaps.firstOrNull()?.takeIf {
                    WeeklyRecaps.fresh(recaps.map { it.week }, now) == it.week
                },
            )
        }

        fun challengeCard(
            challenge: Challenge,
            scores: List<ChallengeScore>,
            rows: Map<String, BoardRow>,
        ): ChallengeCard {
            val byUser = scores.associate { it.userId to it.score }
            val places = Places.of(byUser)
            val standings = scores
                .sortedWith(
                    compareByDescending<ChallengeScore> { it.score }
                        .thenBy { rows[it.userId]?.displayName?.lowercase().orEmpty() },
                )
                .map { ChallengeStanding(it.userId, it.score, places.getValue(it.userId)) }
            return ChallengeCard(challenge, standings, ChallengeRules.winners(byUser))
        }

        /** Own quests completed today as `<date>:<QUEST>`, for keeping them on the device. */
        fun completedQuestKeys(model: ArenaUiModel): Set<String> =
            model.quests.filter { it.done }.mapTo(HashSet()) { "${model.today}:${it.quest.name}" }
    }
}
