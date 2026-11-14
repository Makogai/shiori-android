package mihon.feature.friends.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** README v6, "Weekly recaps". */
class WeeklyRecapsTest {

    private val zone = ZoneId.of("Europe/Belgrade")

    // Grand Opening: Wed 11 Nov 09:00 → Mon 16 Nov 00:00 in the Arena
    private val opening = RecapWeek(
        1,
        "Grand Opening",
        "🎉",
        Instant.parse("2026-11-11T08:00:00Z"),
        Instant.parse("2026-11-15T23:00:00Z"),
    )
    private val day = LocalDate.of(2026, 11, 11)

    private fun input(
        window: Map<String, RecapTotals>,
        daily: List<DayTotal> = emptyList(),
        scores: Map<String, Double> = emptyMap(),
        stakes: Map<String, Int> = emptyMap(),
        previous: Map<String, RecapTotals> = emptyMap(),
    ) = RecapInput(opening, zone, "me", "filip", listOf("me", "filip"), window, previous, daily, scores, stakes)

    @Test
    fun `weeks are ended challenges of at most 7 days, newest first`() {
        val special = RecapWeek(2, "Winter Marathon", "❄️", opening.start, Instant.parse("2026-12-31T23:00:00Z"))
        val next = RecapWeek(3, "Sprint", "🏃", opening.end, Instant.parse("2026-11-22T23:00:00Z"))
        val now = Instant.parse("2026-11-23T10:00:00Z")
        WeeklyRecaps.weeks(listOf(opening, special, next), now) shouldContainExactly listOf(next, opening)
        WeeklyRecaps.weeks(listOf(opening, next), Instant.parse("2026-11-20T10:00:00Z")) shouldContainExactly
            listOf(opening)
        WeeklyRecaps.fresh(listOf(next, opening), now) shouldBe next
        WeeklyRecaps.fresh(listOf(next, opening), Instant.parse("2026-11-29T23:00:00Z")) shouldBe null
    }

    @Test
    fun `the Grand Opening is week 46 from Wednesday to Sunday`() {
        WeeklyRecaps.weekNumber(opening.start, zone) shouldBe 46
        WeeklyRecaps.days(opening, zone) shouldBe (day to LocalDate.of(2026, 11, 15))
    }

    @Test
    fun `a week ending off local midnight keeps its last day`() {
        val utcClose = opening.copy(end = Instant.parse("2026-11-16T00:00:00Z"))
        WeeklyRecaps.days(utcClose, zone) shouldBe (day to LocalDate.of(2026, 11, 16))
        val middayClose = opening.copy(end = Instant.parse("2026-11-15T11:00:00Z"))
        WeeklyRecaps.days(middayClose, zone) shouldBe (day to LocalDate.of(2026, 11, 15))
    }

    @Test
    fun `nobody wins a week nobody read in`() {
        val alone = RecapInput(
            opening, zone, "me", null, listOf("me"), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(),
        )
        WeeklyRecaps.build(alone).winner shouldBe null
        WeeklyRecaps.build(alone.copy(window = mapOf("me" to RecapTotals(1)))).winner shouldBe "me"
    }

    @Test
    fun `totals, xp with win and bet, best day and top series`() {
        val recap = WeeklyRecaps.build(
            input(
                window = mapOf(
                    "me" to RecapTotals(10, 3_000, topSeries = TopRead("Solo Leveling", 6)),
                    "filip" to RecapTotals(8, 9_000),
                ),
                daily = listOf(
                    DayTotal("me", day, 4, 600),
                    DayTotal("me", day.plusDays(2), 6, 2_400),
                    DayTotal("filip", day.plusDays(1), 4, 4_500),
                    DayTotal("filip", day.plusDays(3), 4, 4_500),
                    // Outside the window
                    DayTotal("me", day.plusDays(5), 9, 0),
                ),
                scores = mapOf("me" to 10.0, "filip" to 8.0),
                stakes = mapOf("me" to 50, "filip" to 30),
            ),
        )
        recap.number shouldBe 46
        recap.winner shouldBe "me"
        recap.challengeWinners shouldBe setOf("me")
        val me = recap.player("me")!!
        me.minutes shouldBe 50
        // Day XP: (40 + 10) + (60 + 40), + 250 for the win, + 50 bet
        me.xp shouldBe 150 + 250 + 50
        me.bet shouldBe 50
        me.bestDay shouldBe (day.plusDays(2) to 6L)
        me.topSeries shouldBe TopRead("Solo Leveling", 6)
        val filip = recap.player("filip")!!
        // (40 + 75) twice, − 30 bet
        filip.xp shouldBe 230 - 30
        // Ties go to the earliest day
        filip.bestDay shouldBe (day.plusDays(1) to 4L)
        recap.players.map { it.userId } shouldContainExactly listOf("me", "filip")
    }

    @Test
    fun `equal chapters and seconds is a tied week`() {
        val recap = WeeklyRecaps.build(
            input(window = mapOf("me" to RecapTotals(5, 100), "filip" to RecapTotals(5, 100))),
        )
        recap.winner shouldBe null
        recap.tied shouldBe true
        recap.challengeWinners shouldBe emptySet()
        recap.player("me")!!.bestDay shouldBe null
        val bySeconds = WeeklyRecaps.build(
            input(
                window = mapOf(
                    "me" to RecapTotals(5, 100),
                    "filip" to RecapTotals(5, 101),
                ),
            ),
        )
        bySeconds.winner shouldBe "filip"
    }

    @Test
    fun `genre wars changes against the previous 7 days, at most 5 by name`() {
        val previous = mapOf(
            "me" to RecapTotals(genres = mapOf("Action" to 3L, "Romance" to 2L, "Isekai" to 1L)),
            "filip" to RecapTotals(genres = mapOf("Action" to 1L, "Romance" to 1L)),
        )
        val window = mapOf(
            "me" to RecapTotals(genres = mapOf("Action" to 1L, "Romance" to 2L, "Comedy" to 4L)),
            "filip" to RecapTotals(genres = mapOf("Action" to 5L, "Romance" to 2L)),
        )
        val recap = WeeklyRecaps.build(input(window = window, previous = previous))
        val texts = recap.genreChanges.map { change -> WeeklyRecaps.changeText(change, "me") { "Filip" } }
        texts shouldContainExactly listOf(
            "Action: Filip took it",
            "Comedy: you took it",
            "Isekai: unclaimed",
            "Romance: now contested",
        )

        val many = (1..7).associate { "G$it" to 1L }
        WeeklyRecaps.genreChanges(emptyMap(), mapOf("me" to many)).size shouldBe 5
    }

    @Test
    fun `lead changes over the window's days`() {
        val daily = listOf(
            DayTotal("me", day, 2, 0),
            DayTotal("filip", day.plusDays(1), 5, 0),
            DayTotal("me", day.plusDays(2), 4, 0),
        )
        WeeklyRecaps.build(input(window = emptyMap(), daily = daily)).leadChanges shouldBe 2
        val alone = input(window = emptyMap()).copy(rivalId = null, players = listOf("me"))
        WeeklyRecaps.build(alone).leadChanges shouldBe null
    }
}
