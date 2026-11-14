package mihon.feature.friends.ui

import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.domain.OvertakeBoard
import mihon.feature.friends.domain.OvertakeMetric
import mihon.feature.friends.domain.Overtakes
import mihon.feature.friends.domain.RecapInput
import mihon.feature.friends.domain.RecapTotals
import mihon.feature.friends.domain.RecapWeek
import mihon.feature.friends.domain.TopRead
import mihon.feature.friends.domain.WeeklyRecap
import mihon.feature.friends.domain.WeeklyRecaps

/** Builds the v6 pieces of the Arena (recaps, overtake boards) from a snapshot. */
object ArenaSocialModels {

    /** Every ended week's recap, newest first; weeks whose boards couldn't be loaded are left out. */
    fun recaps(snapshot: ArenaSnapshot, myId: String, rivalId: String?, players: Set<String>): List<WeeklyRecap> {
        val weeks = WeeklyRecaps.weeks(
            snapshot.challenges.map { RecapWeek(it.id, it.title, it.emoji, it.start, it.end) },
            snapshot.now,
        )
        return weeks.mapNotNull { week ->
            val boards = snapshot.recapBoards[week.challengeId] ?: return@mapNotNull null
            WeeklyRecaps.build(
                RecapInput(
                    week = week,
                    zone = snapshot.zone,
                    myId = myId,
                    rivalId = rivalId,
                    players = players,
                    window = boards.window.associate { it.userId to it.totals() },
                    previous = boards.previous.associate { it.userId to it.totals() },
                    daily = snapshot.daily,
                    scores = snapshot.challengeBoards[week.challengeId].orEmpty().associate { it.userId to it.score },
                    stakes = snapshot.bets.filter { it.challengeId == week.challengeId }
                        .associate { it.userId to it.stake },
                ),
            )
        }
    }

    /** The scoreboards overtake alerts watch: today's chapters, each active challenge, season XP (v6 display). */
    fun overtakeBoards(model: ArenaUiModel): List<OvertakeBoard> = buildList {
        add(
            OvertakeBoard(
                metric = OvertakeMetric.TODAY,
                periodKey = model.today.toString(),
                scores = model.players.mapValues { it.value.todayChapters.toDouble() },
            ),
        )
        model.activeChallenges.forEach { card ->
            add(
                OvertakeBoard(
                    metric = OvertakeMetric.CHALLENGE,
                    periodKey = card.challenge.id.toString(),
                    scores = model.players.keys.associateWith { id ->
                        card.standings.firstOrNull { it.userId == id }?.score ?: 0.0
                    },
                    label = card.challenge.title,
                    emoji = card.challenge.emoji,
                ),
            )
        }
        add(
            OvertakeBoard(
                metric = OvertakeMetric.SEASON,
                periodKey = model.seasonNumber.toString(),
                scores = model.players.mapValues { it.value.seasonXp.toDouble() },
                label = Overtakes.seasonLabel(model.seasonNumber),
            ),
        )
    }

    private fun BoardRow.totals() = RecapTotals(
        chapters = chapters,
        seconds = seconds,
        genres = genres,
        topSeries = topSeries.firstOrNull()?.let { TopRead(it.title.orEmpty(), it.chapters) },
    )
}
