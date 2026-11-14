package mihon.feature.friends.ui

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import mihon.feature.friends.data.ArenaSnapshot
import mihon.feature.friends.data.BoardRow
import mihon.feature.friends.data.Challenge
import mihon.feature.friends.data.ChallengeScore
import mihon.feature.friends.data.League
import mihon.feature.friends.data.QuestFacts
import mihon.feature.friends.data.RecapBoards
import mihon.feature.friends.domain.Achievement
import mihon.feature.friends.domain.Bet
import mihon.feature.friends.domain.DayTotal
import mihon.feature.friends.domain.HeadToHeadRecord
import mihon.feature.friends.domain.Lead
import mihon.feature.friends.domain.LeaguePeriod
import mihon.feature.friends.domain.OvertakeMetric
import mihon.feature.friends.domain.Quest
import mihon.feature.friends.domain.Tier
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ArenaUiModelTest {

    private val opening = LocalDate.of(2026, 11, 11)

    private fun row(id: String, chapters: Long, seconds: Long) =
        BoardRow(
            userId = id,
            displayName = id.replaceFirstChar {
                it.uppercase()
            },
            chapters = chapters,
            seconds = seconds,
            series = 1,
        )

    private fun challenge(id: Int, start: String, end: String) =
        Challenge(id, "Challenge $id", "🎉", "", "chapters", null, start, end)

    // Tuesday 2026-11-17, 13:00 in the Arena
    private val snapshot = ArenaSnapshot(
        arena = League(id = "arena", name = "The Reading Arena", inviteCode = "ARENA1", timezone = "Europe/Belgrade"),
        zone = ZoneId.of("Europe/Belgrade"),
        now = Instant.parse("2026-11-17T12:00:00Z"),
        boards = mapOf(
            LeaguePeriod.TODAY to listOf(row("me", 0, 0), row("rival", 0, 0)),
            LeaguePeriod.WEEK to listOf(row("me", 0, 0), row("rival", 0, 0)),
            LeaguePeriod.MONTH to listOf(row("me", 3, 1_200), row("rival", 1, 60)),
            LeaguePeriod.ALL to listOf(row("me", 3, 1_200), row("rival", 1, 60)),
        ),
        daily = listOf(DayTotal("me", opening, 3, 1_200), DayTotal("rival", opening, 1, 60)),
        hours = emptyList(),
        lifetimeHours = emptyList(),
        feed = emptyList(),
        challenges = listOf(
            challenge(1, "2026-11-11T08:00:00+00:00", "2026-11-15T23:00:00+00:00"),
            challenge(100, "2026-11-15T23:00:00+00:00", "2026-11-22T23:00:00+00:00"),
            challenge(101, "2026-11-22T23:00:00+00:00", "2026-11-29T23:00:00+00:00"),
        ),
        challengeBoards = mapOf(
            1 to listOf(ChallengeScore("me", 3.0), ChallengeScore("rival", 1.0)),
            100 to listOf(ChallengeScore("me", 0.0), ChallengeScore("rival", 2.0)),
        ),
        seasonBoards = emptyMap(),
        questFacts = mapOf(opening to mapOf("me" to QuestFacts(series = 1), "rival" to QuestFacts(series = 1))),
        localQuests = setOf("2026-11-17:READ_5"),
    )

    @Test
    fun `xp adds reads, challenge wins and quests while season xp only has reads and wins`() {
        val model = ArenaUiModel.build(snapshot, "me")
        val me = model.me!!
        // Opening day: Read 3 chapters, Keep your streak, Read for 20 minutes all done; today Read 5 was done here
        me.questsCompleted shouldBe 4
        me.perfectDays shouldBe 1
        me.xp shouldBe 50 + 250 + 200
        me.level.level shouldBe 2
        me.seasonXp shouldBe 300
        me.tier shouldBe Tier.BRONZE
        me.toNextTier shouldBe 200
        me.wins.map { it.id } shouldContainExactly listOf(1)

        val rival = model.rival!!
        rival.questsCompleted shouldBe 1
        rival.xp shouldBe 11 + 50
        rival.level.level shouldBe 0
        rival.seasonXp shouldBe 11
    }

    @Test
    fun `today's quests, challenges and rivalry`() {
        val model = ArenaUiModel.build(snapshot, "me")
        model.quests.map { it.quest } shouldContainExactly listOf(Quest.SERIES_2, Quest.READ_5, Quest.EARLY)
        model.quests.map { it.done } shouldContainExactly listOf(false, true, false)
        model.perfectToday shouldBe false

        model.activeChallenges.map { it.challenge.id } shouldContainExactly listOf(100)
        model.activeChallenges.single().leaders shouldBe setOf("rival")
        model.upcoming?.id shouldBe 101
        model.pastChallenges.single().leaders shouldBe setOf("me")

        model.seasonRanking shouldContainExactly listOf("me", "rival")
        model.seasonPlaces shouldBe mapOf("me" to 1, "rival" to 2)
        model.rivalId shouldBe "rival"
        model.lead shouldBe Lead.TIED
        model.tugShare shouldBe 0.5f
        model.record shouldBe HeadToHeadRecord(won = 1, lost = 0, tied = 0)
        model.winStreak shouldBe 0

        val unlocked = model.me!!.achievements.filter { it.unlocked }.map { it.achievement }
        unlocked shouldContainExactly listOf(Achievement.FIRST_CHAPTER, Achievement.CHAMPION)
    }

    @Test
    fun `past seasons keep the final tier and place`() {
        val december = snapshot.copy(
            now = Instant.parse("2026-12-02T12:00:00Z"),
            seasonBoards = mapOf(YearMonth.of(2026, 11) to listOf(row("me", 3, 1_200), row("rival", 1, 60))),
        )
        // The rival also won challenge 100, which ended in November
        val history = ArenaUiModel.build(december, "rival").seasonHistory
        history shouldContainExactly listOf(SeasonResult(YearMonth.of(2026, 11), Tier.BRONZE, place = 2, xp = 11 + 250))
        ArenaUiModel.build(december, "me").seasonHistory.single().xp shouldBe 300
    }

    @Test
    fun `celebrations are keyed per event, most important first`() {
        val keys = ArenaViewModel.celebrationKeys(ArenaUiModel.build(snapshot, "me"))
        keys.first() shouldBe ("challenge:1" to Celebration.ChallengeWon("🎉", "Challenge 1"))
        keys shouldContain ("level:2" to Celebration.LevelUp(2, "Novice Reader"))
        // Lower levels reached at the same time are only marked as seen
        keys shouldContain ("level:1" to null)
        keys.map { it.first } shouldContain "achievement:FIRST_CHAPTER"
        keys.map { it.first } shouldContain "quest:2026-11-17:READ_5"
        keys.map { it.first } shouldNotContain "lead:2026-11-17"
        keys.map { it.first }.none { it.startsWith("tier:") } shouldBe true
    }

    @Test
    fun `genre wars crown unique leaders and keep running genre challenges on the map`() {
        fun genres(id: String, values: Map<String, Long>) = row(id, values.values.sum(), 0).copy(genres = values)
        val withGenres = snapshot.copy(
            boards = snapshot.boards + (
                LeaguePeriod.MONTH to listOf(
                    genres("me", mapOf("Action" to 5, "Romance" to 2)),
                    genres("rival", mapOf("Action" to 3, "Romance" to 2, "Isekai" to 4, "Comedy" to 0)),
                )
                ),
            challenges = snapshot.challenges +
                Challenge(
                    102,
                    "Fantasy Week",
                    "🐉",
                    "",
                    "genre",
                    "Fantasy",
                    "2026-11-15T23:00:00+00:00",
                    "2026-11-22T23:00:00+00:00",
                ),
        )
        val arena = ArenaUiModel.build(withGenres, "me")
        val wars = GenreWarsModel.build(withGenres, arena, GenrePeriod.SEASON)

        wars.territories.map { it.genre } shouldContainExactly listOf("Fantasy", "Action", "Isekai", "Romance")
        wars.territories.map { it.owner } shouldContainExactly listOf(null, "me", "rival", null)
        wars.territories.map { it.contested } shouldContainExactly listOf(false, false, false, true)
        wars.territories.first().challenge?.challenge?.id shouldBe 102
        wars.challenges.map { it.challenge.id } shouldContainExactly listOf(102)
        wars.crowns shouldContainExactly listOf("me" to 1, "rival" to 1)
        wars.myTop shouldContainExactly listOf("Action" to 5L, "Romance" to 2L)
        wars.rivalTop shouldContainExactly listOf("Isekai" to 4L, "Action" to 3L, "Romance" to 2L)
        wars.duels shouldContainExactly listOf(
            GenreDuel("Action", 5, 3),
            GenreDuel("Isekai", 0, 4),
            GenreDuel("Romance", 2, 2),
        )
    }

    @Test
    fun `profiles have the same stats for any player`() {
        val rival = PlayerProfileModel.build(snapshot, "me", "rival")!!
        rival.isMe shouldBe false
        rival.allTime shouldBe Totals(1, 60)
        rival.minutesPerChapter shouldBe 1.0
        rival.longestStreak shouldBe 1
        rival.bestDay shouldBe DayTotal("rival", opening, 1, 60)
        rival.hours.size shouldBe 24
        rival.records.map { it.first.userId to it.second } shouldContainExactly
            listOf("me" to HeadToHeadRecord(won = 0, lost = 1, tied = 0))
        rival.xpHistory shouldContainExactly listOf(opening.minusDays(1) to 0L, opening to 61L)

        val me = PlayerProfileModel.build(snapshot, "me", "me")!!
        me.isMe shouldBe true
        // Opening day reads and quests, the Grand Opening win (ended Monday 00:00), today's quest
        me.xpHistory shouldContainExactly listOf(
            opening.minusDays(1) to 0L,
            opening to 200L,
            LocalDate.of(2026, 11, 16) to 450L,
            LocalDate.of(2026, 11, 17) to 500L,
        )
        me.xpHistory.last().second shouldBe me.player.xp
        rival.xpHistory.last().second shouldBe rival.player.xp
        PlayerProfileModel.build(snapshot, "me", "stranger") shouldBe null
    }

    @Test
    fun `settled bets add to lifetime and season xp, shown clamped at zero`() {
        val betting = snapshot.copy(bets = listOf(Bet("me", 1, 50), Bet("rival", 1, 100), Bet("me", 100, 30)))
        val model = ArenaUiModel.build(betting, "me")
        model.me!!.xp shouldBe 500 + 50
        model.me!!.seasonXp shouldBe 300 + 50
        model.me!!.settledBets.map { it.result } shouldContainExactly listOf(50L)
        // 61 − 100 and 11 − 100 are below zero
        model.rival!!.xp shouldBe 0
        model.rival!!.seasonXp shouldBe 0
        model.rival!!.level.level shouldBe 0
        // Challenge 100 is weekly, running and before its halfway point (Thu 19 Nov 11:00 UTC)
        val live = model.activeChallenges.single()
        live.weekly shouldBe true
        live.betable shouldBe true
        live.betOf("me")?.stake shouldBe 30
        model.pastChallenges.single().bets.size shouldBe 2

        val rival = PlayerProfileModel.build(betting, "me", "rival")!!
        rival.xpHistory.last().second shouldBe 0
        rival.settledBets.map { it.first to it.second.result } shouldContainExactly listOf("🎉 Challenge 1" to -100L)
    }

    @Test
    fun `the latest week's recap is fresh for 7 days`() {
        val withRecaps = snapshot.copy(
            recapBoards = mapOf(1 to RecapBoards(window = listOf(row("me", 3, 1_200), row("rival", 1, 60)))),
            bets = listOf(Bet("me", 1, 50)),
        )
        val model = ArenaUiModel.build(withRecaps, "me")
        val recap = model.freshRecap!!
        recap.number shouldBe 46
        recap.winner shouldBe "me"
        recap.challengeWinners shouldBe setOf("me")
        recap.player("me")!!.xp shouldBe 50 + 250 + 50
        model.recaps.map { it.week.challengeId } shouldContainExactly listOf(1)
        ArenaUiModel.build(withRecaps.copy(now = Instant.parse("2026-11-23T12:00:00Z")), "me").freshRecap shouldBe null
    }

    @Test
    fun `overtake boards watch today, active challenges and season xp`() {
        val boards = ArenaSocialModels.overtakeBoards(ArenaUiModel.build(snapshot, "me"))
        boards.map { it.metric to it.periodKey } shouldContainExactly listOf(
            OvertakeMetric.TODAY to "2026-11-17",
            OvertakeMetric.CHALLENGE to "100",
            OvertakeMetric.SEASON to "1",
        )
        boards[1].scores shouldBe mapOf("me" to 0.0, "rival" to 2.0)
        boards[2].scores shouldBe mapOf("me" to 300.0, "rival" to 11.0)
        boards[2].label shouldBe "Season 1"
    }
}
